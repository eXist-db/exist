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

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                new QName("field-equals", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "",
                new SequenceType[] {
                        new FunctionParameterSequenceType("fields", Type.STRING, Cardinality.ONE_OR_MORE,
                                "The name of the field(s) to search"),
                        new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.ONE_OR_MORE,
                                "The keys to look up for each field.")
                },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                        "all nodes from the field set whose node value is equal to the key."),
                true
            )
    };

    private NodeSet preselectResult = null;

    public FieldLookup(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        path = new DynamicCardinalityCheck(context, Cardinality.ONE_OR_MORE, path,
                new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));
        steps.add(path);

        for (int i = 1; i < arguments.size(); i++) {
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
        Sequence[] keys = new Sequence[getArgumentCount() - 1];
        for (int i = 1; i < getArgumentCount(); i++) {
            keys[i - 1] = getArgument(i).eval(contextSequence);
        }
        DocumentSet docs = contextSequence.getDocumentSet();

        RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);

        try {
            preselectResult = index.queryField(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null, fieldSeq, keys, NodeSet.DESCENDANT);
        } catch (IOException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.info("preselect for " + Arrays.toString(keys) + " on " + contextSequence.getItemCount() + "returned " + preselectResult.getItemCount() +
                " and took " + (System.currentTimeMillis() - start));
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        if (contextSequence != null && !contextSequence.isPersistentSet())
            // in-memory docs won't have an index
            return Sequence.EMPTY_SEQUENCE;

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
            Sequence[] keys = new Sequence[getArgumentCount() - 1];
            for (int i = 1; i < getArgumentCount(); i++) {
                keys[i - 1] = getArgument(i).eval(contextSequence);
            }

            RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);

            try {
                result = index.queryField(getExpressionId(), docs, contextSet, fields, keys, NodeSet.DESCENDANT);
            } catch (IOException e) {
                throw new XPathException(this, e.getMessage());
            }

            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start );
            }
//            LOG.info("eval plain took " + (System.currentTimeMillis() - start));
        } else {
            long start = System.currentTimeMillis();
            result = preselectResult.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.ANCESTOR, true, getExpressionId(), true);
            LOG.info("eval took " + (System.currentTimeMillis() - start));
        }
        return result;
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
