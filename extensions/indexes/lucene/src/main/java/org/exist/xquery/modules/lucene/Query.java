/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.lucene;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Query extends Function implements Optimizable {
	
	protected static final Logger logger = LogManager.getLogger(Query.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("query", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Queries a node set using a Lucene full text index; a lucene index " +
            "must already be defined on the nodes, because if no index is available " +
            "on a node, nothing will be found. Indexes on descendant nodes are not " +
            "used. The context of the Lucene query is determined by the given input " +
            "node set. The query is specified either as a query string based on " +
            "Lucene's default query syntax or as an XML fragment. " +
            "See http://exist-db.org/lucene.html#N1029E for complete documentation.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, 
                		"The node set to search using a Lucene full text index which is defined on those nodes"),
                new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.ZERO_OR_ONE,
                		"The query to search for, provided either as a string or text in Lucene's default query " +
                		"syntax or as an XML fragment to bypass Lucene's default query parser")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set matching the query. match highlighting information " +
                "will be available for all returned nodes. Lucene's match score can be retrieved via " +
                "the ft:score function.")
        ),
        new FunctionSignature(
            new QName("query", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Queries a node set using a Lucene full text index; a lucene index " +
            "must already be defined on the nodes, because if no index is available " +
            "on a node, nothing will be found. Indexes on descendant nodes are not " +
            "used. The context of the Lucene query is determined by the given input " +
            "node set. The query is specified either as a query string based on " +
            "Lucene's default query syntax or as an XML fragment. " +
            "See http://exist-db.org/lucene.html#N1029E for complete documentation.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                		"The node set to search using a Lucene full text index which is defined on those nodes"),
                new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.ZERO_OR_ONE,
                		"The query to search for, provided either as a string or text in Lucene's default query " +
                		"syntax or as an XML fragment to bypass Lucene's default query parser"),
                new FunctionParameterSequenceType("options", Type.ITEM, Cardinality.ZERO_OR_ONE,
                		"An XML fragment containing options to be passed to Lucene's query parser. The following " +
                        "options are supported (a description can be found in the docs):\n" +
                        "<options>\n" +
                        "   <default-operator>and|or</default-operator>\n" +
                        "   <phrase-slop>number</phrase-slop>\n" +
                        "   <leading-wildcard>yes|no</leading-wildcard>\n" +
                        "   <filter-rewrite>yes|no</filter-rewrite>\n" +
                        "</options>")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set matching the query. match highlighting information " +
                "will be available for all returned nodes. Lucene's match score can be retrieved via " +
                "the ft:score function.")
        )
    };

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;

    public Query(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1).simplify();
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        add(arg);

        if (arguments.size() == 3) {
            arg = arguments.get(2).simplify();
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
            steps.add(arg);
        }
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));

        List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            LocationStep firstStep = steps.get(0);
            LocationStep lastStep = steps.get(steps.size() - 1);
            if (firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr != null && outerExpr instanceof LocationStep) {
                    LocationStep outerStep = (LocationStep) outerExpr;
                    NodeTest test = outerStep.getTest();

                    final byte contextQNameType;
                    if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                        contextQNameType = ElementValue.ATTRIBUTE;
                    } else {
                        contextQNameType = ElementValue.ELEMENT;
                    }

                    if (test.getName() == null) {
                        contextQName = new QName(null, null, contextQNameType);
                    } else {
                        contextQName = new QName(test.getName(), contextQNameType);
                    }

                    contextStep = firstStep;
                    axis = outerStep.getAxis();
                    optimizeSelf = true;
                }
            } else if (lastStep != null && firstStep != null) {
                NodeTest test = lastStep.getTest();
                if (test.getName() == null)
                    contextQName = new QName(null, null, null);
                else if (test.isWildcardTest())
                    contextQName = test.getName();
                else

                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                    contextQName = new QName(test.getName(), ElementValue.ATTRIBUTE);
                } else {
                    contextQName = new QName(test.getName());
                }
                axis = firstStep.getAxis();
                optimizeChild = steps.size() == 1 &&
                    (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                contextStep = lastStep;
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        return contextQName != null;
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
    	if (contextSequence != null && !contextSequence.isPersistentSet())
    		// in-memory docs won't have an index
    		return NodeSet.EMPTY_SET;
    	
        long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);

        // DW: contextSequence can be null
        DocumentSet docs = contextSequence.getDocumentSet();
        Item key = getKey(contextSequence, null);
        List<QName> qnames = new ArrayList<>(1);
        qnames.add(contextQName);
        QueryOptions options = parseOptions(this, contextSequence, null, 3);
        try {
            if (key != null && Type.subTypeOf(key.getType(), Type.ELEMENT)) {
                final Element queryXML = key == null ? null : (Element) ((NodeValue) key).getNode();
                preselectResult = index.query(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                        qnames, queryXML, NodeSet.DESCENDANT, options);
            } else {
                final String query = key == null ? null : key.getStringValue();
                preselectResult = index.query(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                        qnames, query, NodeSet.DESCENDANT, options);
            }
        } catch (IOException | org.apache.lucene.queryparser.classic.ParseException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.trace("Lucene query took " + (System.currentTimeMillis() - start));
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        if (contextSequence != null && !contextSequence.isPersistentSet())
    		// in-memory docs won't have an index
    		return Sequence.EMPTY_SEQUENCE;
        
        NodeSet result;
        if (preselectResult == null) {
            long start = System.currentTimeMillis();
            Sequence input = getArgument(0).eval(contextSequence);
            if (!(input instanceof VirtualNodeSet) && input.isEmpty())
                result = NodeSet.EMPTY_SET;
            else {
                NodeSet inNodes = input.toNodeSet();
                DocumentSet docs = inNodes.getDocumentSet();
                LuceneIndexWorker index = (LuceneIndexWorker)
                        context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
                Item key = getKey(contextSequence, contextItem);
                List<QName> qnames = null;
                if (contextQName != null) {
                    qnames = new ArrayList<>(1);
                    qnames.add(contextQName);
                }
                QueryOptions options = parseOptions(this, contextSequence, contextItem, 3);
                try {
                    if (key != null && Type.subTypeOf(key.getType(), Type.ELEMENT)) {
                        final Element queryXML = (Element) ((NodeValue) key).getNode();
                        result = index.query(getExpressionId(), docs, inNodes, qnames,
                                queryXML, NodeSet.ANCESTOR, options);
                    } else {
                        final String query = key == null ? null : key.getStringValue();
                        result = index.query(getExpressionId(), docs, inNodes, qnames,
                                query, NodeSet.ANCESTOR, options);
                    }
                } catch (IOException | org.apache.lucene.queryparser.classic.ParseException e) {
                    throw new XPathException(this, e.getMessage());
                }
            }
            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start );
            }
        } else {
            // DW: contextSequence can be null
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
        }
        return result;
    }

    protected Item getKey(Sequence contextSequence, Item contextItem) throws XPathException {
        Sequence keySeq = getArgument(1).eval(contextSequence, contextItem);
        if (keySeq.isEmpty()) {
            return null;
        }
        Item key = keySeq.itemAt(0);
        if (!(Type.subTypeOf(key.getType(), Type.STRING) || Type.subTypeOf(key.getType(), Type.NODE)))
            throw new XPathException(this, "Second argument to ft:query should either be a query string or " +
                    "an XML element describing the query. Found: " + Type.getTypeName(key.getType()));
        return key;
    }

    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
            !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    public int returnsType() {
        return Type.NODE;
    }

    protected static QueryOptions parseOptions(Function funct, Sequence contextSequence, Item contextItem, int position) throws XPathException {
        if (funct.getArgumentCount() < position)
            return new QueryOptions();
        Sequence optSeq = funct.getArgument(position - 1).eval(contextSequence, contextItem);
        if (Type.subTypeOf(optSeq.getItemType(), Type.ELEMENT)) {
            return new QueryOptions(funct.getContext(), (NodeValue) optSeq.itemAt(0));
        } else if (Type.subTypeOf(optSeq.getItemType(), Type.MAP)) {
            return new QueryOptions((AbstractMapType) optSeq.itemAt(0));
        } else {
            throw new XPathException(funct, LuceneModule.EXXQDYFT0004, "Argument 3 should be either a map or an XML element");
        }
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }
}

