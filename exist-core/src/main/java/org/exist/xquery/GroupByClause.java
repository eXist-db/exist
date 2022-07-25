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
package org.exist.xquery;

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * Implements a "group by" clause inside a FLWOR.
 *
 * @author wolf
 */
public class GroupByClause extends AbstractFLWORClause {

    protected FLWORClause rootClause = null;
    private GroupSpec[] groupSpecs;
    private final Deque<GroupByData> stack = new ArrayDeque<>();

    /**
     * Collect tuples and grouping vars. Because GroupByClause needs to keep
     * state across calls to preEval/eval/postEval, we have to track state data
     * in a separate object and push it to a stack, otherwise recursive calls
     * would overwrite data.
     */
    private static class GroupByData {

        private Map<List<AtomicValue>, Tuple> groupedMap = null;
        private Map<QName, LocalVariable> variables = null;
        private List<LocalVariable> groupingVars = null;

        private boolean initialized = false;

        public GroupByData(final boolean useDefaultCollator, final Comparator<List<AtomicValue>> keyComparator) {
            // check if we can use a hash map
            if (useDefaultCollator) {
                groupedMap = new HashMap<>();
            } else {
                // non-default collation: must use tree map
                groupedMap = new TreeMap<>(keyComparator);
            }
            variables = new HashMap<>();
            groupingVars = new ArrayList<>();
        }
    }

    public GroupByClause(XQueryContext context) {
        super(context);
    }

    @Override
    public ClauseType getType() {
        return ClauseType.GROUPBY;
    }

    @Override
    public Sequence preEval(Sequence seq) throws XPathException {
        stack.push(new GroupByData(usesDefaultCollator(), this::compareKeys));
        return super.preEval(seq);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final GroupByData data = stack.peek();

        // Evaluate group spec to create grouping key sequence
        final List<Sequence> groupingValues = new ArrayList<>();
        final List<AtomicValue> groupingKeys = new ArrayList<>();
        for (GroupSpec spec: groupSpecs) {
            final Sequence groupingSeq = spec.getGroupExpression().eval(null, null);
            if (groupingSeq.getItemCount() > 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Grouping variable " + spec.getKeyVarName() + " " +
                        "evaluates to more than one item");
            }
            final AtomicValue groupingValue = groupingSeq.isEmpty() ? AtomicValue.EMPTY_VALUE : groupingSeq.itemAt(0)
                    .atomize();
            if (!data.initialized) {
                final LocalVariable groupingVar = new LocalVariable(spec.getKeyVarName());
                groupingVar.setSequenceType(new SequenceType(Type.ANY_ATOMIC_TYPE, groupingValue.isEmpty() ? Cardinality.EMPTY_SEQUENCE : Cardinality.EXACTLY_ONE));
                groupingVar.setStaticType(groupingValue.getType());
                data.groupingVars.add(groupingVar);
            }
            groupingValues.add(groupingValue);
            groupingKeys.add(groupingValue);
        }

        // collect the current tuples into the grouping map
        final Tuple tuple = data.groupedMap.computeIfAbsent(groupingKeys, ks -> new Tuple(groupingValues));

        // scan in-scope variables to collect tuples
        LocalVariable nextVar = rootClause.getStartVariable();
        Objects.requireNonNull(nextVar);
        while(nextVar != null) {
            tuple.add(nextVar.getQName(), nextVar.getValue());
            if (!data.initialized) {
                // on first call: initialize non-grouping variable for later use
                final LocalVariable var = new LocalVariable(nextVar.getQName());
                var.setSequenceType(nextVar.getSequenceType());
                var.setStaticType(nextVar.getStaticType());
                var.setContextDocs(nextVar.getContextDocs());
                data.variables.put(var.getQName(), var);
            }
            nextVar = nextVar.after;
        }

        data.initialized = true;
        return contextSequence;
    }

    @Override
    public Sequence postEval(final Sequence seq) throws XPathException {
        if (!stack.isEmpty()) {
            final GroupByData data = stack.peek();
            Sequence result = new ValueSequence();
            final LocalVariable mark = context.markLocalVariables(false);
            try {
                // declare non-grouping variables
                for (LocalVariable var : data.variables.values()) {
                    context.declareVariableBinding(var);
                }
                // declare grouping variables
                for (LocalVariable var : data.groupingVars) {
                    context.declareVariableBinding(var);
                }
                // iterate over each group
                for (Tuple tuple : data.groupedMap.values()) {
                    context.proceed();

                    // set grouping variable values
                    final Iterator<Sequence> siter = tuple.groupingValues.iterator();
                    for (LocalVariable var : data.groupingVars) {
                        if (siter.hasNext()) {
                            Sequence val = siter.next();
                            var.setValue(val);
                        } else {
                            throw new XPathException(this, ErrorCodes.XPTY0004, "Internal error: missing grouping value for variable: $" + var.getQName());
                        }
                    }
                    // set values of non-grouping variables
                    for (Map.Entry<QName, Sequence> entry : tuple.entrySet()) {
                        final LocalVariable var = data.variables.get(entry.getKey());
                        var.setValue(entry.getValue());
                    }
                    final Sequence r = returnExpr.eval(null, null);
                    result.addAll(r);
                }
            } finally {
                stack.pop();
                context.popLocalVariables(mark, result);
            }

            if (returnExpr instanceof FLWORClause flworClause) {
                result = flworClause.postEval(result);
            }
            result = super.postEval(result);
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
                    final LocalVariable groupKeyVar = new LocalVariable(spec.getKeyVarName());
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
    }

    public void setGroupSpecs(final GroupSpec specs[]) {
        final List<GroupSpec> distinctSpecs = new ArrayList<>(specs.length);
        for (int i = 0; i < specs.length; i++) {
            boolean duplicate = false;
            for (int j = i + 1; j < specs.length; j++) {
                if (specs[i].equals(specs[j])) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                distinctSpecs.add(specs[i]);
            }
        }
        this.groupSpecs = distinctSpecs.toArray(new GroupSpec[0]);
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
        stack.clear();
        returnExpr.resetState(postOptimization);
        for (GroupSpec spec: groupSpecs) {
            spec.resetState(postOptimization);
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitGroupByClause(this);
    }

    /**
     * Compare keys using the collator given in the group spec. Used to
     * sort keys into the grouping map.
     */
    private int compareKeys(final List<AtomicValue> s1, final List<AtomicValue> s2) {
        final int c1 = s1.size();
        final int c2 = s2.size();
        if (c1 == c2) {
            try {
                for (int i = 0; i < c1; i++) {
                    final AtomicValue v1 = s1.get(i);
                    final AtomicValue v2 = s2.get(i);
                    final Collator collator = groupSpecs[i].getCollator();
                    final int r = v1.compareTo(collator, v2);
                    if (r != Constants.EQUAL) {
                        return r;
                    }
                }
                return Constants.EQUAL;
            } catch (XPathException e) {
                return Constants.INFERIOR;
            }
        }
        return c1 < c2 ? Constants.INFERIOR : Constants.SUPERIOR;
    }

    private boolean usesDefaultCollator() {
        return Stream.of(groupSpecs).allMatch(spec -> spec.getCollator() == null);
    }

    static class Tuple extends HashMap<QName, Sequence> {

        private final List<Sequence> groupingValues;

        public Tuple(final List<Sequence> groupingValues) {
            super();
            this.groupingValues = groupingValues;
        }

        public void add(final QName name, final Sequence val) throws XPathException {
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
