/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions.fn;

import org.exist.EXistException;
import org.exist.fulltext.FTIndex;
import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
            new QName("Builtin-old-ft-functions-and-operators", Function.BUILTIN_FUNCTION_NS),
            "no-docs-extraction-for-builtin-old-ft-functions-and-operators",
            new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
        );

    protected PathExpr path;
    protected Expression searchTerm = null;
    protected int type = Constants.FULLTEXT_AND;
    protected CachedResult cached = null;
    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;
    protected NodeSet preselectResult = null;

    public ExtFulltext(XQueryContext context, int type) {
        super(context, signature);
        this.type = type;
    }

    public void addTerm(Expression term) {
        if (term instanceof PathExpr) {
            if (((PathExpr) term).getLength() == 1)
                {term = ((PathExpr) term).getExpression(0);}
        }
        searchTerm = term;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        path.analyze(newContextInfo);
        searchTerm.analyze(newContextInfo);
        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(path);
        if (!steps.isEmpty()) {
            LocationStep firstStep = steps.get(0);
            LocationStep lastStep = steps.get(steps.size() - 1);
            if (firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr != null && outerExpr instanceof LocationStep) {
                    final LocationStep outerStep = (LocationStep) outerExpr;
                    final NodeTest test = outerStep.getTest();
                    if (!test.isWildcardTest() && test.getName() != null) {
                        contextQName = new QName(test.getName());
                        if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS ||
                                outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                            {contextQName.setNameType(ElementValue.ATTRIBUTE);}
                        contextStep = firstStep;
                        axis = outerStep.getAxis();
                        optimizeSelf = true;
                    }
                }
            } else if (firstStep != null && lastStep != null) {
                final NodeTest test = lastStep.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {
                    contextQName = new QName(test.getName());
                    if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS ||
                            lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                        {contextQName.setNameType(ElementValue.ATTRIBUTE);}
                    contextStep = lastStep;
                    axis = firstStep.getAxis();

                    if (axis == Constants.SELF_AXIS && steps.size() > 1) {
                    	if (steps.get(1) != null) {
                    		axis = steps.get(1).getAxis();
                    	} else {
                    		contextQName = null;
                    		contextStep = null;
                    		axis = Constants.UNKNOWN_AXIS;
                    		optimizeChild = false;
                    	}
                    }

                    optimizeChild = steps.size() == 1 &&
                            (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                }
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null)
            {return false;}
        return checkForQNameIndex(contextSequence);
    }

    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    public boolean optimizeOnChild() {
        return optimizeChild;
    }

    public int getOptimizeAxis() {
        return axis;
    }
    
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        //The expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        final long start = System.currentTimeMillis();
        //Get the search terms
        final String arg = searchTerm.eval(contextSequence).getStringValue();
        String[] terms;
        try {
            terms = getSearchTerms(arg);
        } catch (final EXistException e) {
            throw new XPathException(e.getMessage());
        }
        //Lookup the terms in the full-text index. returns one node set for each term
        final NodeSet[] hits = getMatches(contextSequence.getDocumentSet(),
                useContext ? contextSequence.toNodeSet() : null,
                NodeSet.DESCENDANT, contextQName, terms);
        //Walk through the matches and compute the combined node set
        if (hits.length > 0)
        	{preselectResult = hits[0];}
        if (preselectResult != null) {
            for(int k = 1; k < hits.length; k++) {
                if(hits[k] != null) {
                    preselectResult = (type == Constants.FULLTEXT_AND ?
                        preselectResult.deepIntersection(hits[k]) :
                        preselectResult.union(hits[k]));
                }
            }
        } else {
            preselectResult = NodeSet.EMPTY_SET;
        }
        if (context.getProfiler().traceFunctions())
            {context.getProfiler().traceIndexUsage(context, FTIndex.ID, this,
                PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start);}
        return preselectResult;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        //If we were optimizing and the preselect did not return anything,
        //we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}
        final long start = System.currentTimeMillis();
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        if (preselectResult == null && !checkForQNameIndex(contextSequence))
            {contextQName = null;}
        NodeSet result;
        //If the expression does not depend on the current context item,
        //we can evaluate it in one single step
        if (path == null || !Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {
            final boolean canCache = 
                !Dependency.dependsOn(searchTerm, Dependency.CONTEXT_ITEM) &&
                !Dependency.dependsOnVar(searchTerm);
            if (canCache && cached != null && cached.isValid(contextSequence, contextItem)) {
                return cached.getResult();
            }
            //Do we optimize this expression?
            if (contextStep == null || preselectResult == null) {
                //No optimization: process the whole expression
                final NodeSet nodes = path == null ?
                    contextSequence.toNodeSet() : path.eval(contextSequence).toNodeSet();
                final String arg = searchTerm.eval(contextSequence).getStringValue();
                result = evalQuery(arg, nodes).toNodeSet();
                if (context.getProfiler().traceFunctions())
                    {context.getProfiler().traceIndexUsage(context, FTIndex.ID, this,
                        PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start);}
            } else {
                contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
                result = path.eval(contextSequence).toNodeSet();
            }
            if(canCache && contextSequence != null && contextSequence.isCacheable())
                {cached = new CachedResult(contextSequence, contextItem, result);}
        //Otherwise we have to walk through each item in the context
        } else {
            Item current;
            String arg;
            NodeSet nodes;
            result = new ExtArrayNodeSet();
            Sequence temp;
            for (final SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
                current = i.nextItem();
                arg = searchTerm.eval(current.toSequence()).getStringValue();
                nodes = path == null ? contextSequence.toNodeSet() :
                    path.eval(current.toSequence()).toNodeSet();
                temp = evalQuery(arg, nodes);
                result.addAll(temp);
            }
            if (context.getProfiler().traceFunctions())
                {context.getProfiler().traceIndexUsage(context, FTIndex.ID, this,
                    PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start);}
        }
        preselectResult = null;
        return result;
    }

    private boolean checkForQNameIndex(Sequence contextSequence) {
        if (contextSequence == null || contextQName == null)
            {return false;}
        boolean hasQNameIndex = true;
        for (final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI))
                {continue;}
            final FulltextIndexSpec config = collection.getFulltextIndexConfiguration(context.getBroker());
            //We have a full-text index
            if (config != null) {
                hasQNameIndex = config.hasQNameIndex(contextQName);
            }
            if (!hasQNameIndex) {
                if (LOG.isTraceEnabled())
                    {LOG.trace("Cannot use index on QName: " + contextQName +
                            ". Collection " + collection.getURI() + " does not define an index");}
                break;
            }
        }
        return hasQNameIndex;
    }

    protected Sequence evalQuery(String searchArg, NodeSet nodes) throws XPathException {
        String[] terms;
        try {
            terms = getSearchTerms(searchArg);
        } catch (final EXistException e) {
            throw new XPathException(e.getMessage());
        }
        final NodeSet hits = processQuery(terms, nodes);
        if (hits == null)
            {return NodeSet.EMPTY_SET;}
        return hits;
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(path.toString());
        if (type == Constants.FULLTEXT_AND)
            {result.append(" &= ");}
        else
            {result.append(" |= ");}
        result.append(searchTerm.toString());
        return result.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        path.dump(dumper);
        if (type == Constants.FULLTEXT_AND)
            {dumper.display(" &= ");}
        else
            {dumper.display(" |= ");}
        searchTerm.dump(dumper);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.functions.Function#getDependencies()
     */
    public int getDependencies() {
        return path.getDependencies();
    }

    protected String[] getSearchTerms(String searchString) throws EXistException {
        final List<String> tokens = new ArrayList<String>();
        final Tokenizer tokenizer = context.getBroker().getTextEngine().getTokenizer();
        tokenizer.setText(searchString);
        org.exist.storage.analysis.TextToken token;
        String word;
        while (null != (token = tokenizer.nextToken(true))) {
            word = token.getText();
            tokens.add(word);
        }
        final String[] terms = new String[tokens.size()];
        return tokens.toArray(terms);
    }

    protected NodeSet processQuery(String[] terms, NodeSet contextSet)
        throws XPathException {
        if (terms == null) {
            throw new RuntimeException("No search terms");
        }
        if (terms.length == 0)
            {return NodeSet.EMPTY_SET;}
        final NodeSet[] hits = getMatches(contextSet.getDocumentSet(), contextSet,
            NodeSet.ANCESTOR, contextQName, terms);
        NodeSet result = hits[0];
        if (result != null) {
            for(int k = 1; k < hits.length; k++) {
                if (hits[k] != null) {
                    result = type == Constants.FULLTEXT_AND ? 
                        result.deepIntersection(hits[k]) : result.union(hits[k]);
                }
            }
            return result;
        } else {
            return NodeSet.EMPTY_SET;
        }
    }

    protected NodeSet[] getMatches(DocumentSet docs, NodeSet contextSet,
            int axis, QName qname, String[] terms) throws XPathException {
        final NodeSet hits[] = new NodeSet[terms.length];
        for (int k = 0; k < terms.length; k++) {
            hits[k] = context.getBroker().getTextEngine().getNodesContaining(
                context, docs, contextSet, axis, qname, terms[k], DBBroker.MATCH_EXACT);
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
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        path.resetState(postOptimization);
        searchTerm.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
            cached = null;
        }
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitFtExpression(this);
    }
}