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
 *
 *  $Id$
 */
package org.exist.xquery.modules.range;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.indexing.QueryableRangeIndex;
import org.exist.indexing.range.*;
import org.exist.indexing.range.config.ComplexGeneralRangeIndexConfigElement;
import org.exist.indexing.range.config.RangeIndexConfig;
import org.exist.indexing.range.config.RangeIndexConfigElement;
import org.exist.storage.ElementValue;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.regex.JDK15RegexTranslator;
import org.exist.xquery.regex.RegexSyntaxException;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
                "all nodes from the input node set whose node value matches the regular expression.")
        ),
        new FunctionSignature(
                new QName("matches", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                DESCRIPTION,
                new SequenceType[] {
                        new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                                "The node set to search using a range index which is defined on those nodes"),
                        new FunctionParameterSequenceType("regex", Type.STRING, Cardinality.ZERO_OR_MORE,
                                "The regular expression."),
                        new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.ZERO_OR_MORE,
                                "The regular expression.")
                },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                        "all nodes from the input node set whose node value matches the regular expression")
        )
    };

    public static Lookup create(final XQueryContext context, final QueryableRangeIndex.Operator operator, final NodePath contextPath) {
        for (final FunctionSignature sig: signatures) {
            if (sig.getName().getLocalPart().equals(operator.toString())) {
                return new Lookup(context, sig, contextPath);
            }
        }
        return null;
    }

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean canOptimize = false;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;
    protected Expression fallback = null;
    protected NodePath contextPath = null;

    public Lookup(XQueryContext context, FunctionSignature signature) {
        this(context, signature, null);
    }

    public Lookup(XQueryContext context, FunctionSignature signature, NodePath contextPath) {
        super(context, signature);
        this.contextPath = contextPath;
    }

    public void setFallback(Expression expression, int optimizeAxis) {
        if (expression instanceof InternalFunctionCall) {
            expression = ((InternalFunctionCall)expression).getFunction();
        }
        this.fallback = expression;
        // we need to know the axis at this point. the optimizer will call
        // getOptimizeAxis before analyze
        this.axis = optimizeAxis;
    }

    public Expression getFallback() {
        return fallback;
    }

    @Override
    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1).simplify();
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
                NodeTest test = lastStep.getTest();
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
            AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
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

        final QueryableRangeIndex.Operator operator = getOperator(contextSequence);

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

    private QueryableRangeIndex.Operator getOperator(Sequence contextSequence) throws XPathException {
        final String calledAs = getSignature().getName().getLocalPart();
        if(calledAs.equals("matches") && getArgumentCount() == 3) {
            final String flags = getArgument(2).eval(contextSequence).itemAt(0).getStringValue();
            return QueryableRangeIndex.OperatorFactory.match(flags);
        } else {
            return QueryableRangeIndex.OperatorFactory.fromName(calledAs);
        }
    }

    private AtomicValue[] getKeys(final Sequence contextSequence) throws XPathException {
        final RangeIndexConfigElement config = findConfiguration(contextSequence);
        final int targetType = config != null ? config.getType() : Type.ITEM;
        final Sequence keySeq = Atomize.atomize(getArgument(1).eval(contextSequence));

        final AtomicValue[] keys = new AtomicValue[keySeq.getItemCount()];
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
                    qnames = new ArrayList<>(1);
                    qnames.add(contextQName);
                }
                final QueryableRangeIndex.Operator operator = getOperator(contextSequence);

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
            result = getArgument(0).eval(contextSequence).toNodeSet();
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
    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null) {
            return false;
        }
        RangeIndexConfigElement rice = findConfiguration(contextSequence);
        if (rice == null) {
            canOptimize = false;
            if (fallback instanceof Optimizable) {
                return ((Optimizable)fallback).canOptimize(contextSequence);
            }
            return false;
        }
        canOptimize = true;
        return canOptimize;
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
                    if (rice != null && !(rice instanceof ComplexGeneralRangeIndexConfigElement)) {
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

    @Override
    public int returnsType() {
        return Type.NODE;
    }
}
