/*
 *  eXist Open Source Native XML Database
 * Copyright (C) 2001-04, Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xquery.functions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.IndexSpec;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicExpressionVisitor;
import org.exist.xquery.CachedResult;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.ExpressionVisitor;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocationStep;
import org.exist.xquery.NodeTest;
import org.exist.xquery.Optimizable;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the fulltext operators: &amp;= and |=.
 * 
 * This is internally handled like a special function and thus inherits
 * from {@link org.exist.xquery.Function}.
 * 
 * @author wolf
 */
public class ExtFulltext extends Function implements Optimizable {

	public final static FunctionSignature signature =
		new FunctionSignature(
            //TODO : change this ! -pb
			new QName("contains", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		);

	protected PathExpr path;
	protected Expression searchTerm = null;
	protected int type = Constants.FULLTEXT_AND;
	
	protected CachedResult cached = null;

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected boolean optimizeSelf = false;
    protected NodeSet preselectResult = null;

    public ExtFulltext(XQueryContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	public void addTerm(Expression term) {
		if (term instanceof PathExpr) {
			if (((PathExpr) term).getLength() == 1)
				term = ((PathExpr) term).getExpression(0);
		}
		searchTerm = term;
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        path.analyze(contextInfo);
        searchTerm.analyze(contextInfo);

        LocationStep step = BasicExpressionVisitor.findFirstStep(path);
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

    public boolean canOptimize(Sequence contextSequence, Item contextItem) {
        if (contextQName == null)
            return false;
        return checkForQNameIndex(contextSequence);
    }


    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    public NodeSet preSelect(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
			contextSequence = contextItem.toSequence();
        
        // get the search terms
        String arg = searchTerm.eval(contextSequence).getStringValue();
        String[] terms;
        try {
			terms = getSearchTerms(arg);
		} catch (EXistException e) {
			throw new XPathException(e.getMessage(), e);
		}
        // lookup the terms in the fulltext index. returns one node set for each term
        NodeSet[] hits = getMatches(contextSequence.getDocumentSet(), null, contextQName, terms);
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

        NodeSet result;
		// if the expression does not depend on the current context item,
		// we can evaluate it in one single step
		if (path == null || !Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {          
			boolean canCache = 
                !Dependency.dependsOn(searchTerm, Dependency.CONTEXT_ITEM) &&
                !Dependency.dependsOnVar(searchTerm);
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
                String arg = searchTerm.eval(contextSequence).getStringValue();
                result = evalQuery(arg, nodes).toNodeSet();
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
				arg = searchTerm.eval(current.toSequence()).getStringValue();
				nodes =
					path == null
					? contextSequence.toNodeSet()
							: path
							.eval(current.toSequence()).toNodeSet();
				temp = evalQuery(arg, nodes);
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
            IndexSpec config = collection.getIdxConf(context.getBroker());
            FulltextIndexSpec spec = config.getFulltextIndexSpec();
            hasQNameIndex = spec.hasQNameIndex(contextQName);
            if (!hasQNameIndex) {
                if (LOG.isTraceEnabled())
                    LOG.trace("cannot use index on QName: " + contextQName + ". Collection " + collection.getURI() +
                        " does not define an index");
                break;
            }
        }
        return hasQNameIndex;
    }

    protected Sequence evalQuery(String searchArg, NodeSet nodes) throws XPathException {
        String[] terms;
        try {
			terms = getSearchTerms(searchArg);
		} catch (EXistException e) {
			throw new XPathException(e.getMessage(), e);
		}
		NodeSet hits = processQuery(terms, nodes);
		if (hits == null)
			return NodeSet.EMPTY_SET;
		return hits;
	}

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append(path.toString());
        result.append(" &= ");
        result.append(searchTerm.toString());
        return result.toString();
    }   

	/* (non-Javadoc)
     * @see org.exist.xquery.Function#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        path.dump(dumper);
        dumper.display(" &= ");
        searchTerm.dump(dumper);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return path.getDependencies();
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

    protected NodeSet processQuery(String[] terms, NodeSet contextSet) throws XPathException {
		if (terms == null)
			throw new RuntimeException("no search terms");
		if(terms.length == 0)
			return NodeSet.EMPTY_SET;
        NodeSet[] hits = getMatches(contextSet.getDocumentSet(), contextSet, contextQName, terms);
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

    protected NodeSet[] getMatches(DocumentSet docs, NodeSet contextSet, QName qname, String[] terms)
    throws XPathException {
        NodeSet hits[] = new NodeSet[terms.length];
        for (int k = 0; k < terms.length; k++) {
            hits[k] =
                    context.getBroker().getTextEngine().getNodesContaining(
                            context,
                            docs,
                            contextSet,
                            qname, terms[k],
                            DBBroker.MATCH_EXACT);
        }
        return hits;
    }

    public int returnsType() {
		return Type.NODE;
	}

	public void setPath(PathExpr path) {
		this.path = path;
	}
	
	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		path.setContextDocSet(contextSet);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		path.resetState();
		searchTerm.resetState();
        preselectResult = null;
        cached = null;
	}

    public void accept(ExpressionVisitor visitor) {
        visitor.visitFtExpression(this);
    }
}