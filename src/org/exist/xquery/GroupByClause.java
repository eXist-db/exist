package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.*;

public class GroupByClause extends AbstractFLWORClause {

    private final static Logger LOG = LogManager.getLogger(GroupByClause.class);

    protected FLWORClause rootClause = null;
    private GroupSpec[] groupSpecs;
    private Map<String, Tuple> groupedMap = null;
    private Map<QName, LocalVariable> variables = null;
    private List<LocalVariable> groupingVars = null;

    public GroupByClause(XQueryContext context) {
        super(context);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final boolean collectVars = variables == null;
        if (collectVars) {
            variables = new HashMap<>();
            groupingVars = new ArrayList<>();
        }

        final List<Sequence> groupingValues = new ArrayList<>();
        final ValueSequence keySequence = new ValueSequence();
        for (GroupSpec spec: groupSpecs) {
            final Sequence groupingSeq = spec.getGroupExpression().eval(null);
            if (groupingSeq.getItemCount() > 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Grouping variable " + spec.getKeyVarName() + " " +
                        "evaluates to more than one item");
            }
            final AtomicValue groupingValue = groupingSeq.isEmpty() ? AtomicValue.EMPTY_VALUE : groupingSeq.itemAt(0)
                    .atomize();
            if (collectVars) {
                final LocalVariable groupingVar = new LocalVariable(QName.parse(context, spec.getKeyVarName(), null));
                groupingVar.setSequenceType(new SequenceType(Type.ATOMIC, groupingValue.isEmpty() ? Cardinality
                        .EMPTY : Cardinality.EXACTLY_ONE));
                groupingVar.setStaticType(groupingValue.getType());
                groupingVars.add(groupingVar);
            }
            groupingValues.add(groupingValue);
            keySequence.add(groupingValue);
        }
        final String hashKey = keySequence.getHashKey();
        LOG.debug("hash key: {}", hashKey);

        if (groupedMap == null) {
            groupedMap = new HashMap<>();
        }

        final Tuple tuple;
        if (groupedMap.containsKey(hashKey)) {
            tuple = groupedMap.get(hashKey);
        } else {
            tuple = new Tuple(groupingValues);
            groupedMap.put(hashKey, tuple);
        }
        LocalVariable nextVar = rootClause.getStartVariable();
        Objects.requireNonNull(nextVar);
        while(nextVar != null) {
            LOG.debug("next var: {}: {}", nextVar.getQName().toString(), nextVar.getValue().getItemCount());
            tuple.add(nextVar.getQName(), nextVar.getValue());
            if (collectVars) {
                final LocalVariable var = new LocalVariable(nextVar.getQName());
                var.setSequenceType(nextVar.getSequenceType());
                var.setStaticType(nextVar.getStaticType());
                var.setContextDocs(nextVar.getContextDocs());
                variables.put(var.getQName(), var);
            }
            nextVar = nextVar.after;
        }

        return contextSequence;
    }

    @Override
    public Sequence postEval(final Sequence seq) throws XPathException {
        LOG.debug("group by post eval: {}", seq.getItemCount());
        if (groupedMap != null) {
            final ValueSequence result = new ValueSequence();
            final LocalVariable mark = context.markLocalVariables(false);
            try {
                for (LocalVariable var: variables.values()) {
                    context.declareVariableBinding(var);
                }
                for (LocalVariable var: groupingVars) {
                    context.declareVariableBinding(var);
                }
                for (Tuple tuple: groupedMap.values()) {
                    context.proceed();

                    // set grouping variables
                    final Iterator<Sequence> siter = tuple.groupingValues.iterator();
                    for (LocalVariable var : groupingVars) {
                        if (siter.hasNext()) {
                            Sequence val = siter.next();
                            var.setValue(val);
                        }
                    }
                    // set values of non-grouping variables
                    for (Map.Entry<QName, Sequence> entry : tuple.entrySet()) {
                        final LocalVariable var = variables.get(entry.getKey());
                        var.setValue(entry.getValue());
                    }
                    result.addAll(returnExpr.eval(null));
                }
            } finally {
                context.popLocalVariables(mark, result);
            }
            return result;
        }
        return seq;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            if (groupSpecs != null) {
                for (final GroupSpec spec : groupSpecs) {
                    final LocalVariable groupKeyVar = new LocalVariable(QName.parse(context, spec.getKeyVarName()));
                    context.declareVariableBinding(groupKeyVar);
                }
            }

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
            for (final GroupSpec spec : groupSpecs) {
                spec.analyze(newContextInfo);
            }

            returnExpr.analyze(newContextInfo);
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }

        FLWORClause prevClause = getPreviousClause();
        while (prevClause != null) {
            rootClause = prevClause;
            prevClause = prevClause.getPreviousClause();
        }
        LOG.debug("group by root expr: {}", ExpressionDumper.dump(rootClause));
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    public void setGroupSpecs(GroupSpec specs[]) {
        this.groupSpecs = specs;
    }

    public GroupSpec[] getGroupSpecs() {
        return groupSpecs == null ? new GroupSpec[0] : groupSpecs;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        if (groupSpecs != null) {
            dumper.display("group by ");
            for (int i = 0; i < groupSpecs.length; i++) {
                if (i > 0)
                {dumper.display(", ");}
                dumper.display(groupSpecs[i].getGroupExpression().toString());
                dumper.display(" as ");
                dumper.display("$").display(groupSpecs[i].getKeyVarName());
            }
            dumper.nl();
        }
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        groupedMap = null;
        returnExpr.resetState(postOptimization);
        for (GroupSpec spec: groupSpecs) {
            spec.resetState(postOptimization);
        }
    }

    static class Tuple extends HashMap<QName, Sequence> {

        private final List<Sequence> groupingValues;

        public Tuple(final List<Sequence> groupingValues) {
            super();
            this.groupingValues = groupingValues;
        }

        public void add(QName name, Sequence val) throws XPathException {
            Sequence seq = get(name);
            if (seq == null) {
                final ValueSequence temp = new ValueSequence(val.getItemCount());
                temp.addAll(val);
                put(name, temp);
            } else {
                seq.addAll(val);
            }
        }
    }
}
