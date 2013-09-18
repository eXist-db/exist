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
import org.exist.xquery.util.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FieldLookup extends Function implements Optimizable {

    private final static SequenceType[] PARAMETER_TYPE = new SequenceType[] {
        new FunctionParameterSequenceType("fields", Type.STRING, Cardinality.ONE_OR_MORE,
                "The name of the field(s) to search"),
        new FunctionParameterSequenceType("keys", Type.ATOMIC, Cardinality.ZERO_OR_MORE,
                "The keys to look up for each field.")
    };

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("field", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "General field lookup function. Normally this will be used by the query optimizer.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("fields", Type.STRING, Cardinality.ONE_OR_MORE,
                            "The name of the field(s) to search"),
                    new FunctionParameterSequenceType("operators", Type.STRING, Cardinality.ONE_OR_MORE,
                            "The operators to use as strings: eq, lt, gt, contains ..."),
                    new FunctionParameterSequenceType("keys", Type.ATOMIC, Cardinality.ZERO_OR_MORE,
                            "The keys to look up for each field.")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
            new QName("field-eq", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "General field lookup function based on equality comparison. Normally this will be used by the query optimizer.",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
            new QName("field-gt", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "General field lookup function based on greater-than comparison. Normally this will be used by the query optimizer.",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
            new QName("field-lt", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "General field lookup function based on less-than comparison. Normally this will be used by the query optimizer.",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
            new QName("field-le", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "General field lookup function based on less-than-equal comparison. Normally this will be used by the query optimizer.",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
            new QName("field-ge", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "General field lookup function based on greater-than-equal comparison. Normally this will be used by the query optimizer.",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        ),
        new FunctionSignature(
                new QName("field-starts-with", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "Used by optimizer to optimize a starts-with() function call",
                PARAMETER_TYPE,
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                        "all nodes from the field set whose node value is equal to the key."),
                true
        ),
        new FunctionSignature(
                new QName("field-ends-with", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "Used by optimizer to optimize a ends-with() function call",
                PARAMETER_TYPE,
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                        "all nodes from the field set whose node value is equal to the key."),
                true
        ),
        new FunctionSignature(
            new QName("field-contains", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "Used by optimizer to optimize a contains() function call",
            PARAMETER_TYPE,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "all nodes from the field set whose node value is equal to the key."),
            true
        )
    };

    private NodeSet preselectResult = null;
    protected Expression fallback = null;

    public FieldLookup(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void setFallback(Expression expression) {
        this.fallback = expression;
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        path = new DynamicCardinalityCheck(context, Cardinality.ONE_OR_MORE, path,
                new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));
        steps.add(path);

        int j = 1;
        if (isCalledAs("field")) {
            Expression fields = arguments.get(1);
            fields = new DynamicCardinalityCheck(context, Cardinality.ONE_OR_MORE, fields,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
            steps.add(fields);
            j++;
        }
        for (int i = j; i < arguments.size(); i++) {
            Expression arg = arguments.get(i).simplify();
            arg = new Atomize(context, arg);
            arg = new DynamicCardinalityCheck(context, Cardinality.ONE_OR_MORE, arg,
                    new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "1", mySignature));
            steps.add(arg);
        }
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));

        this.contextId = contextInfo.getContextId();
    }

    @Override
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        if (contextSequence != null && !contextSequence.isPersistentSet())
            // in-memory docs won't have an index
            return NodeSet.EMPTY_SET;

        long start = System.currentTimeMillis();

        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        Sequence fieldSeq = getArgument(0).eval(contextSequence);
        RangeIndex.Operator[] operators = null;
        int j = 1;
        if (isCalledAs("field")) {
            Sequence operatorSeq = getArgument(1).eval(contextSequence);
            operators = new RangeIndex.Operator[operatorSeq.getItemCount()];
            int i = 0;
            for (SequenceIterator si = operatorSeq.iterate(); si.hasNext(); i++) {
                operators[i] = RangeIndexModule.OPERATOR_MAP.get(si.nextItem().getStringValue());
            }
            j++;
        } else {
            RangeIndex.Operator operator = getOperator();
            operators = new RangeIndex.Operator[fieldSeq.getItemCount()];
            for (int i = 0; i < operators.length; i++) {
                operators[i] = operator;
            }
        }

        Sequence[] keys = new Sequence[getArgumentCount() - j];
        for (int i = j; i < getArgumentCount(); i++) {
            keys[i - j] = getArgument(i).eval(contextSequence);
        }
        DocumentSet docs = contextSequence.getDocumentSet();

        RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);
        try {
            preselectResult = index.queryField(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null, fieldSeq, keys, operators, NodeSet.DESCENDANT);
        } catch (IOException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.info("preselect for " + Arrays.toString(keys) + " on " + contextSequence.getItemCount() + "returned " + preselectResult.getItemCount() +
                " and took " + (System.currentTimeMillis() - start));
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "new-range", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
        }
        //preselectResult.setSelfAsContext(getContextId());
        return preselectResult;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        if (contextSequence != null && !contextSequence.isPersistentSet())
            // in-memory docs won't have an index
            if (fallback == null) {
                return Sequence.EMPTY_SEQUENCE;
            } else {
                return fallback.eval(contextSequence, contextItem);
            }

        NodeSet result;
        if (preselectResult == null) {
            long start = System.currentTimeMillis();

            DocumentSet docs;
            if (contextSequence == null)
                docs = context.getStaticallyKnownDocuments();
            else
                docs = contextSequence.getDocumentSet();
            NodeSet contextSet = null;
            if (contextSequence != null)
                contextSet = contextSequence.toNodeSet();

            Sequence fields = getArgument(0).eval(contextSequence);
            RangeIndex.Operator[] operators = null;
            int j = 1;
            if (isCalledAs("field")) {
                Sequence operatorSeq = getArgument(1).eval(contextSequence);
                operators = new RangeIndex.Operator[operatorSeq.getItemCount()];
                int i = 0;
                for (SequenceIterator si = operatorSeq.iterate(); si.hasNext(); i++) {
                    operators[i] = RangeIndexModule.OPERATOR_MAP.get(si.nextItem().getStringValue());
                }
                j++;
            } else {
                RangeIndex.Operator operator = getOperator();
                operators = new RangeIndex.Operator[fields.getItemCount()];
                for (int i = 0; i < operators.length; i++) {
                    operators[i] = operator;
                }
            }
            Sequence[] keys = new Sequence[getArgumentCount() - j];
            for (int i = j; i < getArgumentCount(); i++) {
                keys[i - j] = getArgument(i).eval(contextSequence);
            }

            RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);

            try {
                result = index.queryField(getExpressionId(), docs, contextSet, fields, keys, operators, NodeSet.DESCENDANT);
                if (contextSet != null) {
                    result = result.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, true, getContextId(), true);
                }
            } catch (IOException e) {
                throw new XPathException(this, e.getMessage());
            }

            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, "new-range", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
            }
//            LOG.info("eval plain took " + (System.currentTimeMillis() - start));
        } else {
            long start = System.currentTimeMillis();
            result = preselectResult.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.DESCENDANT, true, getContextId(), true);
            LOG.info("eval took " + (System.currentTimeMillis() - start));
        }
        return result;
    }

    private RangeIndex.Operator getOperator() {
        final String calledAs = getSignature().getName().getLocalName();
        return RangeIndexModule.OPERATOR_MAP.get(calledAs.substring("field-".length()));
    }

    @Override
    public boolean canOptimize(Sequence contextSequence) {
        return true;
    }

    @Override
    public boolean optimizeOnSelf() {
        return false;
    }

    @Override
    public boolean optimizeOnChild() {
        return true;
    }

    @Override
    public int getOptimizeAxis() {
        return Constants.CHILD_AXIS;
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
