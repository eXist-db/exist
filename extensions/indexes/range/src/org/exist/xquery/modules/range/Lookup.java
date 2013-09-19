/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.xquery.modules.range;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.VirtualNodeSet;
import org.exist.indexing.range.RangeIndex;
import org.exist.indexing.range.RangeIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Lookup extends Function implements Optimizable {

    private final static SequenceType[] PARAMETER_TYPE = new SequenceType[] {
            new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The node set to search using a range index which is defined on those nodes"),
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.ZERO_OR_MORE,
                    "The key to look up.")
    };

    private final static String DESCRIPTION = "Search for nodes matching the given keys in the range " +
        "index. Normally this function will be called by the query optimizer.";

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("eq", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                DESCRIPTION,
                PARAMETER_TYPE,
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("gt", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("lt", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("le", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("ge", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("starts-with", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("ends-with", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is equal to the key.")
        ),
        new FunctionSignature(
            new QName("contains", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set whose node value is equal to the key.")
        )
    };

    public static Lookup create(XQueryContext context, RangeIndex.Operator operator) {
        for (FunctionSignature sig: signatures) {
            if (sig.getName().getLocalName().equals(operator.toString())) {
                return new Lookup(context, sig);
            }
        }
        return null;
    }

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;
    protected Expression fallback = null;

    public Lookup(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void setFallback(Expression expression) {
        this.fallback = expression;
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1).simplify();
        arg = new Atomize(context, arg);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_MORE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        steps.add(arg);
    }

    @Override
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
                    if (test.getName() == null)
                        contextQName = new QName(null, null, null);
                    else if (test.isWildcardTest())
                        contextQName = test.getName();
                    else
                        contextQName = new QName(test.getName());
                    if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                        contextQName.setNameType(ElementValue.ATTRIBUTE);
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
                    contextQName = new QName(test.getName());
                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                    contextQName.setNameType(ElementValue.ATTRIBUTE);
                axis = firstStep.getAxis();
                optimizeChild = steps.size() == 1 &&
                        (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                contextStep = lastStep;
            }
        }
        if (fallback != null) {
            fallback.analyze(new AnalyzeContextInfo(contextInfo));
        }
    }

    @Override
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        if (contextSequence != null && !contextSequence.isPersistentSet())
            // in-memory docs won't have an index
            return NodeSet.EMPTY_SET;

        long start = System.currentTimeMillis();

        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);

        DocumentSet docs = contextSequence.getDocumentSet();
        AtomicValue[] keys = getKeys(contextSequence);
        if (keys.length == 0) {
            return NodeSet.EMPTY_SET;
        }

        List<QName> qnames = null;
        if (contextQName != null) {
            qnames = new ArrayList<QName>(1);
            qnames.add(contextQName);
        }

        final RangeIndex.Operator operator = getOperator();

        try {
            preselectResult = index.query(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null, qnames, keys, operator, NodeSet.DESCENDANT);
        } catch (IOException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        //LOG.info("preselect for " + Arrays.toString(keys) + " on " + contextSequence.getItemCount() + "returned " + preselectResult.getItemCount() +
        //        " and took " + (System.currentTimeMillis() - start));
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "new-range", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
        }
        if (preselectResult == null) {
            preselectResult = NodeSet.EMPTY_SET;
        }
        return preselectResult;
    }

    private RangeIndex.Operator getOperator() {
        final String calledAs = getSignature().getName().getLocalName();
        return RangeIndexModule.OPERATOR_MAP.get(calledAs);
    }

    private AtomicValue[] getKeys(Sequence contextSequence) throws XPathException {
        Sequence keySeq = getArgument(1).eval(contextSequence);
        AtomicValue[] keys = new AtomicValue[keySeq.getItemCount()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = (AtomicValue) keySeq.itemAt(i);
        }
        return keys;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        if (contextSequence != null && !contextSequence.isPersistentSet()) {
            // in-memory docs won't have an index
            if (fallback == null) {
                return Sequence.EMPTY_SEQUENCE;
            } else {
                return fallback.eval(contextSequence, contextItem);
            }
        }
        NodeSet result = NodeSet.EMPTY_SET;
        if (preselectResult == null) {
            long start = System.currentTimeMillis();
            Sequence input = getArgument(0).eval(contextSequence);
            if (!(input instanceof VirtualNodeSet) && input.isEmpty())
                result = NodeSet.EMPTY_SET;
            else {
                RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);
                AtomicValue[] keys = getKeys(contextSequence);
                if (keys.length == 0) {
                    return NodeSet.EMPTY_SET;
                }
                List<QName> qnames = null;
                if (contextQName != null) {
                    qnames = new ArrayList<QName>(1);
                    qnames.add(contextQName);
                }
                final RangeIndex.Operator operator = getOperator();

                try {
                    NodeSet inNodes = input.toNodeSet();
                    DocumentSet docs = inNodes.getDocumentSet();
                    result = index.query(getExpressionId(), docs, inNodes, qnames, keys, operator, NodeSet.ANCESTOR);
                } catch (IOException e) {
                    throw new XPathException(this, e.getMessage());
                }
            }
            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, "new-range", this, PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start );
            }
//            LOG.info("eval plain took " + (System.currentTimeMillis() - start));
        } else {
            long start = System.currentTimeMillis();
            contextStep.setPreloadedData(preselectResult.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
            //LOG.info("eval took " + (System.currentTimeMillis() - start));
        }
        return result;
    }

    @Override
    public boolean canOptimize(Sequence contextSequence) {
        return contextQName != null;
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
        return Constants.DESCENDANT_AXIS;
    }

    @Override
    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (!Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    public int returnsType() {
        return Type.NODE;
    }
}
