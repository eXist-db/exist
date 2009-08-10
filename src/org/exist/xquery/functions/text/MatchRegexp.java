/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.FulltextIndexSpec;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicExpressionVisitor;
import org.exist.xquery.CachedResult;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocationStep;
import org.exist.xquery.NodeTest;
import org.exist.xquery.Optimizable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author wolf
 */
public class MatchRegexp extends Function implements Optimizable {

	protected static final FunctionParameterSequenceType SOURCE_PARAM = new FunctionParameterSequenceType("source", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set that is to be searched for the keyword set");
	protected static final FunctionParameterSequenceType REGEX_PARAM = new FunctionParameterSequenceType("regular-expression", Type.STRING, Cardinality.ONE_OR_MORE, "The regular expressions to be matched against the fulltext index");
	protected static final FunctionParameterSequenceType FLAGS_PARAM = new FunctionParameterSequenceType("flag", Type.STRING, Cardinality.EXACTLY_ONE, "With 'w' specified, the regular expression is matched against the entire keyword, i.e. 'explain.*' will match 'explained' , but not 'unexplained'.");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the sequence of all of the matching nodes");
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("match-all", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $source. Every " +
			"node containing ALL of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern if any substring of the keyword matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 'w'. With 'w' specified, the regular expression " +
            "is matched against the entire keyword, i.e. 'explain.*' will match 'explained' , but not 'unexplained'.",
			new SequenceType[] { SOURCE_PARAM, REGEX_PARAM },
			RETURN_TYPE
		),
        new FunctionSignature(
			new QName("match-all", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $source. Every " +
			"node containing ALL of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern if any substring of the keyword matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 'w'. With 'w' specified, the regular expression " +
            "is matched against the entire keyword, i.e. 'explain.*' will match 'explained' , but not 'unexplained'.",
			new SequenceType[] { SOURCE_PARAM, REGEX_PARAM, FLAGS_PARAM },
			RETURN_TYPE
		),
        new FunctionSignature(
			new QName("match-any", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $source. Every " +
			"node containing ANY of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern if any substring of the keyword matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 'w'. With 'w' specified, the regular expression " +
            "is matched against the entire keyword, i.e. 'explain.*' will match 'explained' , but not 'unexplained'.",
			new SequenceType[] { SOURCE_PARAM, REGEX_PARAM },
			RETURN_TYPE
		),
        new FunctionSignature(
			new QName("match-any", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match each of the regular expression " +
			"strings against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $source. Every " +
			"node containing ANY of the keywords is copied to the result sequence. By default, a keyword " +
            "is considered to match the pattern if any substring of the keyword matches. To change this behaviour, " +
            "use the 3-argument version of the function and specify flag 'w'. With 'w' specified, the regular expression " +
            "is matched against the entire keyword, i.e. 'explain.*' will match 'explained' , but not 'unexplained'.",
			new SequenceType[] { SOURCE_PARAM, REGEX_PARAM, FLAGS_PARAM },
			RETURN_TYPE
		)
    };

    public static final String MATCH_ALL_FLAG = "w";

    protected int type = Constants.FULLTEXT_AND;
	protected CachedResult cached = null;

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    protected boolean optimizeSelf = false;
    protected NodeSet preselectResult = null;

	public MatchRegexp(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        if (isCalledAs("match-any"))
            this.type = Constants.FULLTEXT_OR;
        List steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            LocationStep firstStep = (LocationStep) steps.get(0);
            LocationStep lastStep = (LocationStep) steps.get(steps.size() - 1);
            if (steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr != null && outerExpr instanceof LocationStep) {
                    LocationStep outerStep = (LocationStep) outerExpr;
                    NodeTest test = outerStep.getTest();
                    if (!test.isWildcardTest() && test.getName() != null) {
                        contextQName = new QName(test.getName());
                        if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                            contextQName.setNameType(ElementValue.ATTRIBUTE);
                        contextStep = firstStep;
                        axis = outerStep.getAxis();
                        optimizeSelf = true;
                    }
                }
            } else {
                NodeTest test = lastStep.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {
                    contextQName = new QName(test.getName());
                    if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                        contextQName.setNameType(ElementValue.ATTRIBUTE);
                    contextStep = lastStep;
                    axis = firstStep.getAxis();
                    if (axis == Constants.SELF_AXIS && steps.size() > 1)
                        axis = ((LocationStep) steps.get(1)).getAxis();
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

    public int getOptimizeAxis() {
        return axis;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        // get the search terms
        Expression termsExpr = getArgument(1);
        Expression flagsExpr = (getArgumentCount() == 3) ? getArgument(2) : null;
        boolean matchAll = getMatchFlag(flagsExpr, contextSequence);
        List terms = getSearchTerms(termsExpr, contextSequence);

        // lookup the terms in the fulltext index. returns one node set for each term
        NodeSet[] hits = getMatches(contextSequence.getDocumentSet(),
                useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, contextQName, terms, matchAll);
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
        Expression termsExpr = getArgument(1);
        Expression flagsExpr = (getArgumentCount() == 3) ? getArgument(2) : null;
        boolean matchAll = getMatchFlag(flagsExpr, contextSequence);

        NodeSet result;
		// if the expression does not depend on the current context item,
		// we can evaluate it in one single step
		if (path == null || !Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {
			boolean canCache =
                (getTermDependencies() & Dependency.CONTEXT_ITEM) == Dependency.NO_DEPENDENCY;
            if(	canCache && cached != null && cached.isValid(contextSequence, contextItem)) {
				return cached.getResult();
			}
            // do we optimize this expression?
            if (contextStep == null || preselectResult == null) {
                // no optimization: process the whole expression
                NodeSet nodes =
                    path == null
                        ? contextSequence.toNodeSet()
                        : path.eval(contextSequence).toNodeSet();
                List terms = getSearchTerms(termsExpr, contextSequence);
                result = evalQuery(nodes, terms, matchAll).toNodeSet();
            } else {
                contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
                result = path.eval(contextSequence).toNodeSet();
            }
            if(canCache && contextSequence.isCacheable())
				cached = new CachedResult(contextSequence, contextItem, result);

		// otherwise we have to walk through each item in the context
		} else {
			Item current;
			String arg;
			NodeSet nodes;
			result = new ExtArrayNodeSet();
			Sequence temp;
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
				current = i.nextItem();
				List terms = getSearchTerms(termsExpr, contextSequence);
				nodes =
					path == null ? contextSequence.toNodeSet() : path.eval(current.toSequence()).toNodeSet();
				temp = evalQuery(nodes, terms, matchAll);
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

    protected List getSearchTerms(Expression termsExpr, Sequence contextSequence) throws XPathException {
		List terms = new ArrayList();
		Sequence seq = termsExpr.eval(contextSequence);
        if(seq.hasOne())
            terms.add(translateRegexp(seq.itemAt(0).getStringValue()));
        else {
            for(SequenceIterator it = seq.iterate(); it.hasNext(); ) {
                terms.add(translateRegexp(it.nextItem().getStringValue()));
            }
        }
		return terms;
	}

    private boolean getMatchFlag(Expression flagsExpr, Sequence contextSequence) throws XPathException {
        boolean matchAll = false;
        if (flagsExpr != null) {
            String flagStr = flagsExpr.eval(contextSequence).getStringValue();
            matchAll = flagStr.equals(MATCH_ALL_FLAG);
        }
        return matchAll;
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
	public Sequence evalQuery(NodeSet nodes, List terms, boolean matchAll)
		throws XPathException {
		if(terms == null || terms.size() == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
        NodeSet[] hits = getMatches(nodes.getDocumentSet(), nodes, NodeSet.ANCESTOR, contextQName, terms, matchAll);
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

    protected NodeSet[] getMatches(DocumentSet docs, NodeSet contextSet, int axis, QName qname, List terms, boolean matchAll)
    throws XPathException {
        NodeSet hits[] = new NodeSet[terms.size()];
        for (int k = 0; k < terms.size(); k++) {
            hits[k] =
                    context.getBroker().getTextEngine().getNodesContaining(
                            context,
                            docs,
                            contextSet, axis,
                            qname, (String) terms.get(k),
                            DBBroker.MATCH_REGEXP, matchAll);
            LOG.debug("Matches for " + terms.get(k) + ": " + hits[k].getLength());
        }
        return hits;
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
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
        if (!postOptimization)
            cached = null;
	}

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 *
	 * @param pattern Xpath2 expression
	 * @return Java regexp representation of pattern
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
       try {
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexSyntaxException e) {
			throw new XPathException(this, "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}
}
