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
package org.exist.xquery.modules.range;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.indexing.range.RangeIndex;
import org.exist.indexing.range.RangeIndexConfig;
import org.exist.indexing.range.RangeIndexConfigElement;
import org.exist.indexing.range.RangeIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
            new QName("ne", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the input node set whose node value is not equal to the key.")
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
        ),
        new FunctionSignature(
            new QName("matches", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            DESCRIPTION,
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The node set to search using a range index which is defined on those nodes"),
                new FunctionParameterSequenceType("regex", Type.STRING, Cardinality.ZERO_OR_MORE,
                    "The regular expression.")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set whose node value matches the regular expression. Regular expression " +
                "syntax is limited to what Lucene supports. See http://lucene.apache.org/core/4_5_1/core/org/apache/lucene/util/automaton/RegExp.html")
        )
    };

    public static @Nullable Lookup create(final XQueryContext context, final RangeIndex.Operator operator, final NodePath contextPath) {
        for (final FunctionSignature sig : signatures) {
            if (sig.getName().getLocalPart().equals(operator.toString())) {
                return new Lookup(context, sig, contextPath);
            }
        }
        return null;
    }

    @Nullable private LocationStep contextStep = null;
    @Nullable private QName contextQName = null;
    private int axis = Constants.UNKNOWN_AXIS;
    @Nullable private NodeSet preselectResult = null;
    private boolean canOptimize = false;
    private boolean optimizeSelf = false;
    private boolean optimizeChild = false;
    private boolean usesCollation = false;
    @Nullable private Expression fallback = null;
    @Nullable private final NodePath contextPath;

    /**
     * Constructor called via reflection from {@link Function#createFunction(XQueryContext, XQueryAST, Module, FunctionDef)}.
     *
     * @param context The XQuery Context.
     * @param signature The signature of the Lookup function.
     */
    @SuppressWarnings("unused")
    public Lookup(final XQueryContext context, final FunctionSignature signature) {
        this(context, signature, null);
    }

    /**
     * Constructor called via {@link #create(XQueryContext, RangeIndex.Operator, NodePath)}.
     *
     * @param context The XQuery Context.
     * @param signature The signature of the Lookup function.
     * @param contextPath the node path of the optimization.
     */
    private Lookup(final XQueryContext context, final FunctionSignature signature, @Nullable final NodePath contextPath) {
        super(context, signature);
        this.contextPath = contextPath;
    }

    public void setFallback(@Nullable Expression expression, final int optimizeAxis) {
        if (expression instanceof final InternalFunctionCall fcall) {
            expression = fcall.getFunction();
        }
        this.fallback = expression;
        // we need to know the axis at this point. the optimizer will call
        // getOptimizeAxis before analyze
        this.axis = optimizeAxis;
    }

    public @Nullable Expression getFallback() {
        return fallback;
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1).simplify();
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_MORE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
        steps.add(arg);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));

        List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            final LocationStep firstStep = steps.get(0);
            final LocationStep lastStep = steps.get(steps.size() - 1);
            if (firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr instanceof LocationStep outerStep) {
                    final NodeTest test = outerStep.getTest();
                    if (test.getName() == null) {
                        contextQName = new QName(null, null, null);
                    } else if (test.isWildcardTest()) {
                        contextQName = test.getName();
                    } else {
                        contextQName = new QName(test.getName());
                    }
                    if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                        contextQName = new QName(contextQName.getLocalPart(), contextQName.getNamespaceURI(), contextQName.getPrefix(), ElementValue.ATTRIBUTE);
                    }
                    contextStep = firstStep;
                    axis = outerStep.getAxis();
                    optimizeSelf = true;
                }
            } else if (lastStep != null && firstStep != null) {
                final NodeTest test = lastStep.getTest();
                if(test.getName() == null) {
                    contextQName = new QName(null, null, null);
                } else if(test.isWildcardTest()) {
                    contextQName = test.getName();
                } else {
                    contextQName = new QName(test.getName());
                }
                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                    contextQName = new QName(contextQName.getLocalPart(), contextQName.getNamespaceURI(), contextQName.getPrefix(), ElementValue.ATTRIBUTE);
                }
                axis = firstStep.getAxis();
                optimizeChild = steps.size() == 1 &&
                        (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                contextStep = lastStep;
            }
        }
        if (fallback != null) {
            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            newContextInfo.setStaticType(Type.NODE);
            fallback.analyze(newContextInfo);
        }
    }

    @Override
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        if (!canOptimize) {
            return ((Optimizable)fallback).preSelect(contextSequence, useContext);
        }
        if (contextSequence != null && !contextSequence.isPersistentSet())
            // in-memory docs won't have an index
            return NodeSet.EMPTY_SET;

        // throw an exception if substring match operation is applied to collated index
        final RangeIndex.Operator operator = getOperator();
        if (usesCollation && !operator.supportsCollation()) {
            throw new XPathException(this, RangeIndexModule.EXXQDYFT0001, "Index defines a collation which cannot be " +
                    "used with the '" + operator + "' operation.");
        }

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
            qnames = new ArrayList<>(1);
            qnames.add(contextQName);
        }

        try {
            preselectResult = index.query(getExpressionId(), docs, contextSequence.toNodeSet(), qnames, keys, operator, NodeSet.DESCENDANT);
        } catch (XPathException | IOException e) {
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
        final String calledAs = getSignature().getName().getLocalPart();
        return RangeIndexModule.OPERATOR_MAP.get(calledAs);
    }

    private AtomicValue[] getKeys(Sequence contextSequence) throws XPathException {
        RangeIndexConfigElement config = findConfiguration(contextSequence);
        int targetType = config != null ? config.getType() : Type.ITEM;
        Sequence keySeq = Atomize.atomize(getArgument(1).eval(contextSequence, null));
        AtomicValue[] keys = new AtomicValue[keySeq.getItemCount()];
        for (int i = 0; i < keys.length; i++) {
            if (targetType == Type.ITEM) {
                keys[i] = (AtomicValue) keySeq.itemAt(i);
            } else {
                keys[i] = keySeq.itemAt(i).convertTo(targetType);
            }
        }
        return keys;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (!canOptimize && fallback != null) {
            return fallback.eval(contextSequence, contextItem);
        }
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
        NodeSet result;
        if (preselectResult == null) {
            long start = System.currentTimeMillis();
            Sequence input = getArgument(0).eval(contextSequence, null);
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
                    qnames = new ArrayList<>(1);
                    qnames.add(contextQName);
                }
                final RangeIndex.Operator operator = getOperator();

                // throw an exception if substring match operation is applied to collated index
                if (usesCollation && !operator.supportsCollation()) {
                    throw new XPathException(this, RangeIndexModule.EXXQDYFT0001, "Index defines a collation which cannot be " +
                            "used with the '" + operator + "' operation.");
                }

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
//            long start = System.currentTimeMillis();
            contextStep.setPreloadedData(preselectResult.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence, null).toNodeSet();
            //LOG.info("eval took " + (System.currentTimeMillis() - start));
        }
        return result;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (fallback != null) {
            fallback.resetState(postOptimization);
        }
        if (!postOptimization) {
            preselectResult = null;
            canOptimize = false;
        }
    }

    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        if (contextQName == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        ValueSequence optimizables = null;

        NodePath path = contextPath;
        if (path == null) {
            path = new NodePath(contextQName);
        }

        int usesCollationCount = 0;
        for (int i = 0; i < contextSequence.getItemCount(); i++) {
            final Item item = contextSequence.itemAt(i);
            Collection collection = null;
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                final NodeValue node = (NodeValue) item;
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    final NodeProxy nodeProxy = (NodeProxy) node;
                    collection = nodeProxy.getOwnerDocument().getCollection();
                }
            }

            if (collection == null) {
                continue;
            }

            if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI)) {
                continue;
            }
            final IndexSpec idxConf = collection.getIndexConfiguration(context.getBroker());
            if (idxConf != null) {
                final RangeIndexConfig config = (RangeIndexConfig) idxConf.getCustomIndexSpec(RangeIndex.ID);
                if (config != null) {
                    RangeIndexConfigElement rice = config.find(path);
                    if (rice != null && !rice.isComplex()) {
                        if (optimizables == null) {
                            optimizables = new ValueSequence(contextSequence.getItemCount());
                        }
                        optimizables.add(item);
                        usesCollationCount += rice.usesCollation() ? 1 : 0;
                    }
                }
            }
        }

        canOptimize = optimizables != null && optimizables.getItemCount() == contextSequence.getItemCount();
        usesCollation = usesCollationCount == contextSequence.getItemCount();

        if (!canOptimize && fallback instanceof final Optimizable optimizableFallback) {
            return optimizableFallback.canOptimizeSequence(contextSequence);
        }

        return optimizables != null ? optimizables : Sequence.EMPTY_SEQUENCE;
    }

    private RangeIndexConfigElement findConfiguration(Sequence contextSequence) {
        NodePath path = contextPath;
        if (path == null) {
            if (contextQName == null) {
                return null;
            }
            path = new NodePath(contextQName);
        }
        for (final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI)) {
                continue;
            }
            IndexSpec idxConf = collection.getIndexConfiguration(context.getBroker());
            if (idxConf != null) {
                RangeIndexConfig config = (RangeIndexConfig) idxConf.getCustomIndexSpec(RangeIndex.ID);
                if (config != null) {
                    RangeIndexConfigElement rice = config.find(path);
                    if (rice != null && !rice.isComplex()) {
                        return rice;
                    }
                }
            }
        }
        return null;
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
