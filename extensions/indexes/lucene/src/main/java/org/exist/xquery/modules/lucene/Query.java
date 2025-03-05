/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.lucene;

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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.lucene.LuceneModule.functionSignatures;

public class Query extends Function implements Optimizable {

    private static final FunctionParameterSequenceType FS_PARAM_NODES = optManyParam("nodes", Type.NODE, "The node set to search using a Lucene full text index which is defined on those nodes");
    private static final FunctionParameterSequenceType FS_PARAM_QUERY = optParam("query", Type.ITEM, "The query to search for, provided either as a string or text in Lucene's default query syntax or as an XML fragment to bypass Lucene's default query parser");

    final static FunctionSignature[] signatures = functionSignatures(
            "query",
            """
                    Queries a node set using a Lucene full text index; a lucene index
                    must already be defined on the nodes, because if no index is available
                    on a node, nothing will be found. Indexes on descendant nodes are not
                    used. The context of the Lucene query is determined by the given input
                    node set. The query is specified either as a query string based on
                    Lucene's default query syntax or as an XML fragment.
                    See http://exist-db.org/lucene.html#N1029E for complete documentation.""",
            returnsOptMany(Type.NODE, """
                    all nodes from the input node set matching the query. match highlighting information
                    will be available for all returned nodes. Lucene's match score can be retrieved via
                    the ft:score function."""),
            arities(
                    arity(
                        FS_PARAM_NODES,
                        FS_PARAM_QUERY
                    ),
                    arity(
                        FS_PARAM_NODES,
                        FS_PARAM_QUERY,
                        optParam("options", Type.ITEM,
                                """
                                        An XML fragment or XDM Map containing options to be passed to Lucene's query parser. The following options are supported (a description can be found in the docs):
                                        <options>
                                           <default-operator>and|or</default-operator>
                                           <phrase-slop>number</phrase-slop>
                                           <leading-wildcard>yes|no</leading-wildcard>
                                           <filter-rewrite>yes|no</filter-rewrite>
                                           <lowercase-expanded-terms>yes|no</lowercase-expanded-terms>
                                        </options>"""
                        )
                    )
            )
    );

    private LocationStep contextStep = null;
    @Nullable private QName contextQNames[] = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;

    public Query(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void setArguments(final List<Expression> arguments) {
        steps.clear();

        final Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1).simplify();
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
        add(arg);

        if (arguments.size() == 3) {
            arg = arguments.get(2).simplify();
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
            steps.add(arg);
        }
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));

        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            final LocationStep firstStep = steps.getFirst();
            final LocationStep lastStep = steps.getLast();
            if (firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr instanceof final LocationStep outerLocationStep) {
                    analyzeLocationStep(firstStep, outerLocationStep);
                } else if (outerExpr instanceof final FilteredExpression outerStep) {
                    // NOTE(AR) fix for https://github.com/eXist-db/exist/issues/3207
                    if (outerStep.getExpression() instanceof final LocationStep outerLocationStep) {
                        analyzeLocationStep(firstStep, outerLocationStep);
                    } else if (outerStep.getExpression() instanceof final Union union) {
                        analyzeUnion(firstStep, union);
                    }
                }
            } else if (lastStep != null && firstStep != null) {
                final NodeTest test = lastStep.getTest();
                if (test.getName() == null) {
                    contextQNames = new QName[]{ new QName(null, null, null) };
                } else if (test.isWildcardTest()) {
                    contextQNames = new QName[]{ test.getName() };
                } else if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                    contextQNames = new QName[]{ new QName(test.getName(), ElementValue.ATTRIBUTE) };
                } else {
                    contextQNames = new QName[]{ new QName(test.getName()) };
                }
                axis = firstStep.getAxis();
                optimizeChild = steps.size() == 1 &&
                    (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                contextStep = lastStep;
            }
        }
    }

    private void analyzeLocationStep(final LocationStep firstStep, final LocationStep locationStep) {
        contextQNames = new QName[]{ getContextQName(locationStep) };
        contextStep = firstStep;
        axis = locationStep.getAxis();
        optimizeSelf = true;
    }

    private void analyzeUnion(final LocationStep firstStep, final Union union) {
        final PathExpr left = union.getLeft();
        final PathExpr right = union.getRight();

        if (left.getPrimaryAxis() != right.getPrimaryAxis()) {
            return;
        }

        if (left.getSubExpressionCount() == 1 && left.getSubExpression(0) instanceof final LocationStep leftLocationStep
                && right.getSubExpressionCount() == 1 && right.getSubExpression(0) instanceof final LocationStep rightLocationStep) {
            contextQNames = new QName[] { getContextQName(leftLocationStep), getContextQName(rightLocationStep) };
            contextStep = firstStep;
            axis = left.getPrimaryAxis();
            optimizeSelf = true;
        }
    }

    private QName getContextQName(final LocationStep locationStep) {
        final QName contextQName;

        final byte contextQNameType;
        if (locationStep.getAxis() == Constants.ATTRIBUTE_AXIS || locationStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
            contextQNameType = ElementValue.ATTRIBUTE;
        } else {
            contextQNameType = ElementValue.ELEMENT;
        }

        final NodeTest test = locationStep.getTest();
        if (test.getName() == null) {
            contextQName = new QName(null, null, contextQNameType);
        } else {
            contextQName = new QName(test.getName(), contextQNameType);
        }

        return contextQName;
    }

    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        if (contextQNames != null) {
            return contextSequence;
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    @Override
    public boolean optimizeOnChild() {
        return optimizeChild;
    }

    @Override
    public int getOptimizeAxis() {
        return axis;
    }

    @Override
    public NodeSet preSelect(final Sequence contextSequence, final boolean useContext) throws XPathException {
        // guard against an empty contextSequence
    	if (contextSequence == null || !contextSequence.isPersistentSet()) {
    		// in-memory docs won't have an index
    		return NodeSet.EMPTY_SET;
        }

        final long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);

        final DocumentSet docs = contextSequence.getDocumentSet();
        final Item key = getKey(contextSequence, null);
        @Nullable final List<QName> qnames = contextQNames != null ? Arrays.asList(contextQNames) : null;
        final QueryOptions options = parseOptions(this, contextSequence, null, 3);
        try {
            if (key != null && Type.subTypeOf(key.getType(), Type.ELEMENT)) {
                final Element queryXML = (Element) ((NodeValue) key).getNode();
                preselectResult = index.query(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                        qnames, queryXML, NodeSet.DESCENDANT, options);
            } else {
                final String query = key == null ? null : key.getStringValue();
                preselectResult = index.query(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                        qnames, query, NodeSet.DESCENDANT, options);
            }
        } catch (final IOException | org.apache.lucene.queryparser.classic.ParseException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.trace("Lucene query took {}", System.currentTimeMillis() - start);
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.IndexOptimizationLevel.OPTIMIZED, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    @Override
    public Sequence eval(Sequence contextSequence, @Nullable final Item contextItem) throws XPathException {
    	
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        if (contextSequence != null && !contextSequence.isPersistentSet()) {
            // in-memory docs won't have an index
            return Sequence.EMPTY_SEQUENCE;
        }
        
        final NodeSet result;
        if (preselectResult == null) {
            final long start = System.currentTimeMillis();
            final Sequence input = getArgument(0).eval(contextSequence, null);
            if (!(input instanceof VirtualNodeSet) && input.isEmpty()) {
                result = NodeSet.EMPTY_SET;
            } else {
                final NodeSet inNodes = input.toNodeSet();
                final DocumentSet docs = inNodes.getDocumentSet();
                final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
                final Item key = getKey(contextSequence, contextItem);
                @Nullable final List<QName> qnames = contextQNames != null ? Arrays.asList(contextQNames) : null;
                final QueryOptions options = parseOptions(this, contextSequence, contextItem, 3);
                try {
                    if (key != null && Type.subTypeOf(key.getType(), Type.ELEMENT)) {
                        final Element queryXML = (Element) ((NodeValue) key).getNode();
                        result = index.query(getExpressionId(), docs, inNodes, qnames, queryXML, NodeSet.ANCESTOR, options);
                    } else {
                        final String query = key == null ? null : key.getStringValue();
                        result = index.query(getExpressionId(), docs, inNodes, qnames, query, NodeSet.ANCESTOR, options);
                    }
                } catch (final IOException | org.apache.lucene.queryparser.classic.ParseException e) {
                    throw new XPathException(this, e.getMessage());
                }
            }
            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.IndexOptimizationLevel.BASIC, System.currentTimeMillis() - start );
            }
        } else {
            // DW: contextSequence can be null
            contextStep.setPreloadedData(preselectResult.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence, null).toNodeSet();
        }

        return result;
    }

    protected Item getKey(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence keySeq = getArgument(1).eval(contextSequence, contextItem);
        if (keySeq.isEmpty()) {
            return null;
        }

        final Item key = keySeq.itemAt(0);
        if (!(Type.subTypeOf(key.getType(), Type.STRING) || Type.subTypeOf(key.getType(), Type.NODE))) {
            throw new XPathException(this, "Second argument to ft:query should either be a query string or " +
                    "an XML element describing the query. Found: " + Type.getTypeName(key.getType()));
        }

        return key;
    }

    @Override
    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
                !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    @Override
    public int returnsType() {
        return Type.NODE;
    }

    protected static QueryOptions parseOptions(final Function funct, final Sequence contextSequence, final Item contextItem, final int position) throws XPathException {
        if (funct.getArgumentCount() < position) {
            return new QueryOptions();
        }

        final Sequence optSeq = funct.getArgument(position - 1).eval(contextSequence, contextItem);
        if (Type.subTypeOf(optSeq.getItemType(), Type.ELEMENT)) {
            return new QueryOptions(funct.getContext(), (NodeValue) optSeq.itemAt(0));
        } else if (Type.subTypeOf(optSeq.getItemType(), Type.MAP_ITEM)) {
            return new QueryOptions((AbstractMapType) optSeq.itemAt(0));
        } else {
            throw new XPathException(funct, LuceneModule.EXXQDYFT0004, "Argument 3 should be either a map or an XML element");
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }
}

