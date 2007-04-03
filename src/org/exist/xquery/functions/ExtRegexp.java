/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.DocumentSet;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xquery.*;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.collections.Collection;

/**
 * @author wolf
 */
public class ExtRegexp extends Function implements Optimizable {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("match-all", Function.BUILTIN_FUNCTION_NS),
			"eXist-specific extension function. Tries to match each of the regular expression " +
			"strings passed in $b and all following parameters against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing all of the keywords is copied to the result sequence.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true,
            "This function is eXist-specific and should not be in the standard functions namespace. Please " +
            "use text:match-all instead."
        );

	protected int type = Constants.FULLTEXT_AND;
	protected CachedResult cached = null;

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected boolean optimizeSelf = false;
    protected NodeSet preselectResult = null;

    public ExtRegexp(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @param type
	 */
	public ExtRegexp(XQueryContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	public ExtRegexp(XQueryContext context, int type, FunctionSignature signature) {
		super(context, signature);
		this.type = type;
	}

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        LocationStep step = BasicExpressionVisitor.findFirstStep(getArgument(0));
        if (step != null) {
            if (step.getAxis() == Constants.SELF_AXIS) {
                Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr != null && outerExpr instanceof LocationStep) {
                    LocationStep outerStep = (LocationStep) outerExpr;
                    NodeTest test = outerStep.getTest();
                    if (!test.isWildcardTest() && test.getName() != null) {
                        contextQName = new QName(test.getName());
                        if (step.getAxis() == Constants.ATTRIBUTE_AXIS || step.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                            contextQName.setNameType(ElementValue.ATTRIBUTE);
                        contextStep = step;
                        optimizeSelf = true;
                    }
                }
            } else {
                NodeTest test = step.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {
                    contextQName = new QName(test.getName());
                    if (step.getAxis() == Constants.ATTRIBUTE_AXIS || step.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                        contextQName.setNameType(ElementValue.ATTRIBUTE);
                    contextStep = step;
                }
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null)
            return false;
        return checkForQNameIndex(contextSequence);
    }

    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        // get the search terms
        List terms = getSearchTerms(contextSequence);

        // lookup the terms in the fulltext index. returns one node set for each term
        NodeSet[] hits = getMatches(contextSequence.getDocumentSet(),
                useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, contextQName, terms);
        // walk through the matches and compute the combined node set
        preselectResult = hits[0];
        if (preselectResult != null) {
            for(int k = 1; k < hits.length; k++) {
                if(hits[k] != null) {
                    preselectResult = (type == Constants.FULLTEXT_AND ? preselectResult.deepIntersection(hits[k])
                            : preselectResult.union(hits[k]));
                }
            }
        } else {
            preselectResult = NodeSet.EMPTY_SET;
        }
        return preselectResult;
    }

    public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
        // if we were optimizing and the preselect did not return anything,
        // we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        if (contextItem != null)
			contextSequence = contextItem.toSequence();

        if (preselectResult == null && !checkForQNameIndex(contextSequence))
            contextQName = null;

        Expression path = getArgument(0);
        NodeSet result;
		// if the expression does not depend on the current context item,
		// we can evaluate it in one single step
		if (path == null || !Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {
			boolean canCache =
                (getTermDependencies() & Dependency.CONTEXT_ITEM) == Dependency.NO_DEPENDENCY;
            if(	canCache && cached != null && cached.isValid(contextSequence)) {
				return cached.getResult();
			}
            // do we optimize this expression?
            if (contextStep == null || preselectResult == null) {
                // no optimization: process the whole expression
                NodeSet nodes =
                    path == null
                        ? contextSequence.toNodeSet()
                        : path.eval(contextSequence).toNodeSet();
                List terms = getSearchTerms(contextSequence);
                result = evalQuery(nodes, terms).toNodeSet();
            } else {
                contextStep.setPreloadNodeSets(true);
                contextStep.setPreloadedData(preselectResult.getDocumentSet(), preselectResult);

                result = path.eval(contextSequence).toNodeSet();
            }
            if(canCache && contextSequence instanceof NodeSet)
				cached = new CachedResult((NodeSet)contextSequence, result);

		// otherwise we have to walk through each item in the context
		} else {
			Item current;
			String arg;
			NodeSet nodes;
			result = new ExtArrayNodeSet();
			Sequence temp;
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
				current = i.nextItem();
				List terms = getSearchTerms(contextSequence);
				nodes =
					path == null
					? contextSequence.toNodeSet()
							: path
							.eval(current.toSequence()).toNodeSet();
				temp = evalQuery(nodes, terms);
				result.addAll(temp);
			}
		}
        preselectResult = null;
        return result;
	}

    private boolean checkForQNameIndex(Sequence contextSequence) {
        if (contextSequence == null || contextQName == null)
            return false;
        boolean hasQNameIndex = true;
        for (Iterator i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = (Collection) i.next();
            if (collection.getURI().equals(XmldbURI.SYSTEM_COLLECTION_URI))
                continue;
            FulltextIndexSpec config = collection.getFulltextIndexConfiguration(context.getBroker());
            //We have a fulltext index
            if (config != null) {
            	hasQNameIndex = config.hasQNameIndex(contextQName);
            }
            if (!hasQNameIndex) {
                if (LOG.isTraceEnabled())
                    LOG.trace("cannot use index on QName: " + contextQName + ". Collection " + collection.getURI() +
                        " does not define an index");
                break;
            }
        }
        return hasQNameIndex;
    }

    protected String[] getSearchTerms(String searchString)
		throws EXistException {
		List tokens = new ArrayList();
		Tokenizer tokenizer = context.getBroker().getTextEngine().getTokenizer();
		tokenizer.setText(searchString);
		org.exist.storage.analysis.TextToken token;
		String word;
		while (null != (token = tokenizer.nextToken(true))) {
			word = token.getText();
			tokens.add(word);
		}
		String[] terms = new String[tokens.size()];
		return (String[]) tokens.toArray(terms);
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = 0;
		for(int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.ExtFulltext#evalQuery(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, java.lang.String, org.exist.dom.NodeSet)
	 */
	public Sequence evalQuery(
		NodeSet nodes,
		List terms)
		throws XPathException {
		if(terms == null || terms.size() == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
        NodeSet[] hits = getMatches(nodes.getDocumentSet(), nodes, NodeSet.ANCESTOR, contextQName, terms);
		NodeSet result = hits[0];
		if(result != null) {
			for(int k = 1; k < hits.length; k++) {
				if(hits[k] != null)
					result = (type == Constants.FULLTEXT_AND ?
							result.deepIntersection(hits[k]) : result.union(hits[k]));
			}
			return result;
		} else
			return NodeSet.EMPTY_SET;
	}

    protected NodeSet[] getMatches(DocumentSet docs, NodeSet contextSet, int axis, QName qname, List terms)
    throws XPathException {
        NodeSet hits[] = new NodeSet[terms.size()];
        for (int k = 0; k < terms.size(); k++) {
            hits[k] =
                    context.getBroker().getTextEngine().getNodesContaining(
                            context,
                            docs,
                            contextSet, axis,
                            qname, (String) terms.get(k),
                            DBBroker.MATCH_REGEXP);
            LOG.debug("Matches for " + terms.get(k) + ": " + hits[k].getLength());
        }
        return hits;
    }

    protected List getSearchTerms(Sequence contextSequence) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least 2 arguments");
		List terms = new ArrayList();
		Expression next;
		Sequence seq;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			seq = next.eval(contextSequence);
			if(seq.hasOne())
			    terms.add(translateRegexp(seq.itemAt(0).getStringValue()));
			else {
				for(SequenceIterator it = seq.iterate(); it.hasNext(); ) {
				    terms.add(translateRegexp(it.nextItem().getStringValue()));
				}
			}
		}
		return terms;
	}

	protected int getTermDependencies() throws XPathException {
		int deps = 0;
		for(int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		cached = null;
	}

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 *
	 * @param pattern
	 * @return
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
       try {
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexSyntaxException e) {
			throw new XPathException(getASTNode(), "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}
}
