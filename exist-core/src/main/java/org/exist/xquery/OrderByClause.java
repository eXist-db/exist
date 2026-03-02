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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents an "order by" clause within a FLWOR expression.
 *
 * <p>The W3C XQuery 3.1 specification requires that after an order by clause, all subsequent
 * clauses in the FLWOR expression see tuples in the sorted order. This implementation achieves
 * that by collecting the full variable state for each tuple during {@link #eval}, sorting the
 * collected tuples in {@link #postEval}, and then replaying each sorted tuple through the
 * downstream clause chain — mirroring the pattern used by {@link GroupByClause}.</p>
 */
public class OrderByClause extends AbstractFLWORClause {

    private final List<OrderSpec> orderSpecs;

    /**
     * The outermost clause in the preceding FLWOR clause chain (e.g. the {@code ForExpr}).
     * Used in {@link #eval} to locate the first runtime variable of the tuple stream, exactly
     * as {@link GroupByClause} does via its own {@code rootClause} field.
     */
    private FLWORClause rootClause = null;

    /*  OrderByClause needs to keep state between calls to eval and postEval. We thus need
        to track state in a stack to avoid overwrites if we're called recursively. */
    private final Deque<OrderByData> stack = new ArrayDeque<>();

    public OrderByClause(final XQueryContext context, final List<OrderSpec> orderSpecs) {
        super(context);
        this.orderSpecs = orderSpecs;
    }

    public List<OrderSpec> getOrderSpecs() {
        return orderSpecs;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.ORDERBY;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;

        // Walk the previousClause chain to find the outermost (root) clause, mirroring the
        // pattern used by GroupByClause.analyze().  rootClause.getStartVariable() gives us the
        // first runtime variable of the tuple stream during eval().
        FLWORClause prevClause = getPreviousClause();
        while (prevClause != null) {
            rootClause = prevClause;
            prevClause = prevClause.getPreviousClause();
        }

        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        if (orderSpecs != null) {
            for (OrderSpec spec : orderSpecs) {
                spec.analyze(newContextInfo);
            }
        }
        returnExpr.analyze(newContextInfo);
    }

    @Override
    public Sequence preEval(final Sequence seq) throws XPathException {
        final OrderByData data = new OrderByData(orderSpecs.size());
        // Snapshot lastVar at preEval time.  data.mark is used in eval() as a reference
        // to rootClause.getStartVariable() (which equals or precedes data.mark in the chain).
        // Its .before link gives us the pre-FLWOR anchor X so that, when GroupByClause
        // replaces the tuple variables (GroupBy+OrderBy), we can recover the first active
        // variable via startVar.before.after (see eval()).
        data.mark = context.markLocalVariables(false);
        stack.push(data);
        return super.preEval(seq);  // chains to returnExpr.preEval()
    }

    /**
     * Captures the current tuple state (all in-scope variable bindings and evaluated sort
     * keys) without evaluating the downstream clause chain. The actual downstream evaluation
     * happens in {@link #postEval} after all tuples have been sorted.
     *
     * @return always {@link Sequence#EMPTY_SEQUENCE}; downstream clauses are not called here.
     */
    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        // The stack may be empty when an inner OrderByClause is used inside an outer
        // OrderByClause's replay loop (e.g. "for $a order by $a for $b order by $b return …").
        // preEval() is called once (from the outermost for's preEval chain), but postEval()
        // is called once per outer sorted tuple by ForExpr[$b].callPostEval().  After the first
        // postEval() pops the frame, subsequent outer iterations arrive here with an empty stack.
        // Push a fresh frame so each outer-loop iteration gets its own collection context.
        if (stack.isEmpty()) {
            stack.push(new OrderByData(orderSpecs.size()));
        }
        final OrderByData data = stack.peek();

        // Capture variable state for this tuple.
        //
        // Use rootClause.getStartVariable() (= the first runtime variable, e.g. $x from ForExpr)
        // as the anchor for the forward walk, the same strategy GroupByClause uses.  This is
        // needed because ForExpr.createVariable() sets firstVariable to $x but may additionally
        // declare a positional variable ($pos) AFTER $x before calling preEval(), which would
        // make data.mark = $pos and cause $x to be missed by a walk starting there.
        //
        // Two distinct situations arise at eval() time:
        //
        //   (a) Simple for+orderby — rootClause.getStartVariable() ($x_runtime) is still in the
        //       active variable chain.  We walk forward from $x_runtime; inner for/let/count
        //       variables appear as $x_runtime.after and are captured automatically.
        //
        //   (b) GroupBy+OrderBy — ForExpr has already finished and popped $x_runtime from the
        //       chain.  GroupByClause.postEval() declared its own template variables ($x_GB,
        //       $key_GB, …) immediately after the same pre-FLWOR anchor X.  Therefore
        //       $x_runtime.before.after == $x_GB (not $x_runtime), so we walk from $x_GB.
        final LocalVariable startVar = (rootClause != null) ? rootClause.getStartVariable() : data.mark;
        final LocalVariable walkStart;
        if (startVar != null && startVar.before != null && startVar.before.after != startVar) {
            // startVar is no longer in the active chain (GroupBy replaced the tuple variables).
            // Walk from the first variable declared after the pre-FLWOR boundary.
            walkStart = startVar.before.after;
        } else if (startVar != null && startVar.before == null) {
            // startVar was declared when lastVar was null (top-level FLWOR), so its .before is
            // null.  In the normal case startVar is still the first active variable; but in a
            // double-OrderBy FLWOR the inner OBC.eval() is called from the outer OBC.postEval()
            // replay, where startVar is the stale original ForExpr variable (no longer in the
            // active chain) while the actual first active variable is the outer OBC's template.
            // Using the context's live first variable handles both cases correctly.
            final LocalVariable ctxFirst = context.getFirstLocalVariable();
            walkStart = (ctxFirst != null) ? ctxFirst : startVar;
        } else {
            walkStart = startVar;
        }

        final Map<QName, Sequence> varValues = new LinkedHashMap<>();
        LocalVariable nextVar = walkStart;
        while (nextVar != null) {
            varValues.put(nextVar.getQName(), nextVar.getValue());
            if (!data.initialized) {
                // First call: create template LocalVariables for reuse in postEval()
                final LocalVariable templateVar = new LocalVariable(nextVar.getQName());
                templateVar.setSequenceType(nextVar.getSequenceType());
                templateVar.setStaticType(nextVar.getStaticType());
                templateVar.setContextDocs(nextVar.getContextDocs());
                data.variableTemplates.put(nextVar.getQName(), templateVar);
            }
            nextVar = nextVar.after;
        }
        data.initialized = true;

        // Evaluate sort keys for this tuple
        final List<AtomicValue> sortKeys = new ArrayList<>(orderSpecs.size());
        for (int i = 0; i < orderSpecs.size(); i++) {
            final OrderSpec spec = orderSpecs.get(i);
            final Sequence keySeq = spec.getSortExpression().eval(contextSequence, null);
            final AtomicValue value;
            if (keySeq.hasOne()) {
                AtomicValue atomized = keySeq.itemAt(0).atomize();
                // Cast xs:untypedAtomic to xs:string per W3C XQuery 3.1 §3.9.4
                if (Type.UNTYPED_ATOMIC == atomized.getType()) {
                    atomized = atomized.convertTo(Type.STRING);
                }
                data.encounteredPrimitiveTypes.get(i).set(Type.primitiveTypeOf(atomized.getType()));
                value = atomized;
            } else if (keySeq.hasMany()) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Order by expression evaluates to more than one item: " +
                        ExpressionDumper.dump(spec.getSortExpression()));
            } else {
                value = AtomicValue.EMPTY_VALUE;
            }
            sortKeys.add(value);
        }

        data.tuples.add(new OrderByTuple(varValues, sortKeys, data.tuples.size()));
        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * Sorts the collected tuples and replays each one through the downstream clause chain
     * (e.g. {@code count}, {@code where}, {@code let}, the {@code return} expression).
     * This ensures all downstream clauses see tuples in the order specified by the order
     * by clause, as required by W3C XQuery 3.1 §3.9.4.
     */
    @Override
    public Sequence postEval(final Sequence seq) throws XPathException {
        // Use a safe pop: the stack may already be empty when this postEval() is called as
        // part of the final cleanup chain (e.g. BindingExpression.postEval() chains through
        // ForExpr → OrderByClause after all per-iteration postEval() calls have already
        // consumed their frames).
        final OrderByData data = stack.isEmpty() ? null : stack.pop();
        if (data == null || data.tuples.isEmpty()) {
            return seq;
        }

        // Apply W3C type coercion rules before sorting
        coerceSortKeys(data);

        // Sort tuples by their sort keys (stable: ties preserve insertion order)
        data.tuples.sort((a, b) -> compareTuples(a, b, orderSpecs));

        Sequence result = new ValueSequence();
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            // Declare all variable templates in context (same pattern as GroupByClause.postEval())
            for (final LocalVariable var : data.variableTemplates.values()) {
                context.declareVariableBinding(var);
            }

            // Replay each sorted tuple through the downstream clause chain
            for (final OrderByTuple tuple : data.tuples) {
                context.proceed(this);

                // Restore variable bindings for this tuple
                for (final Map.Entry<QName, Sequence> entry : tuple.variableValues.entrySet()) {
                    final LocalVariable var = data.variableTemplates.get(entry.getKey());
                    if (var != null) {
                        var.setValue(entry.getValue());
                    }
                }

                // Evaluate downstream clauses (CountClause, WhereClause, LetExpr, return expr)
                // in sorted order — this is where correct post-sort counting and filtering happen.
                result.addAll(returnExpr.eval(null, null));
            }
        } finally {
            context.popLocalVariables(mark, result);
        }

        // Chain downstream postEval for cleanup (resets firstVariable, etc.)
        if (returnExpr instanceof FLWORClause flworClause) {
            result = flworClause.postEval(result);
        }
        return super.postEval(result);
    }

    /**
     * Applies the W3C XQuery 3.1 type coercion rules for order by sort keys:
     * <ul>
     *   <li>xs:string and xs:anyURI mixed → cast all to xs:string</li>
     *   <li>xs:decimal and xs:float mixed → cast all to xs:float</li>
     *   <li>xs:decimal, xs:float, and/or xs:double mixed → cast all to xs:double</li>
     *   <li>Any other mix → XPTY0004 error</li>
     * </ul>
     */
    private void coerceSortKeys(final OrderByData data) throws XPathException {
        for (int t = 0; t < orderSpecs.size(); t++) {
            final BitSet encounteredTypes = data.encounteredPrimitiveTypes.get(t);
            final int typeCardinality = encounteredTypes.cardinality();
            if (typeCardinality <= 1) {
                continue;
            }

            final int coerceToType;
            if (typeCardinality == 2 && countSetBits(encounteredTypes, Type.STRING, Type.ANY_URI) == 2) {
                coerceToType = Type.STRING;
            } else if (typeCardinality == 2 && countSetBits(encounteredTypes, Type.DECIMAL, Type.FLOAT) == 2) {
                coerceToType = Type.FLOAT;
            } else if (typeCardinality <= 3 && countSetBits(encounteredTypes, Type.DECIMAL, Type.FLOAT, Type.DOUBLE) >= 2) {
                coerceToType = Type.DOUBLE;
            } else {
                final StringBuilder message = new StringBuilder(
                        "Order by contains a mix of primitive types which cannot be compared for sorting: [");
                try (final IntStream stream = encounteredTypes.stream()) {
                    message.append(stream.mapToObj(Type::getTypeName).collect(Collectors.joining(", ")));
                }
                message.append(']');
                throw new XPathException(this, ErrorCodes.XPTY0004, message.toString());
            }

            final int slotIndex = t;
            for (final OrderByTuple tuple : data.tuples) {
                final AtomicValue value = tuple.sortKeys.get(slotIndex);
                if (!value.isEmpty()) {
                    tuple.sortKeys.set(slotIndex, value.convertTo(coerceToType));
                }
            }
        }
    }

    private static int countSetBits(final BitSet bitSet, final int... bitIndexes) {
        int count = 0;
        for (final int index : bitIndexes) {
            if (bitSet.get(index)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Compares two tuples by their sort keys, implementing the W3C XQuery 3.1 order by
     * comparison semantics (empty-value / NaN handling, collator, descending modifier).
     * Falls back to original insertion order for a stable sort when all keys are equal.
     */
    private static int compareTuples(final OrderByTuple a, final OrderByTuple b,
                                     final List<OrderSpec> orderSpecs) {
        int cmp = Constants.EQUAL;
        for (int i = 0; i < a.sortKeys.size(); i++) {
            final AtomicValue keyA = a.sortKeys.get(i);
            final AtomicValue keyB = b.sortKeys.get(i);
            final OrderSpec spec = orderSpecs.get(i);

            try {
                final boolean aIsEmpty = keyA.isEmpty()
                        || (Type.subTypeOfUnion(keyA.getType(), Type.NUMERIC)
                            && ((NumericValue) keyA).isNaN());
                final boolean bIsEmpty = keyB.isEmpty()
                        || (Type.subTypeOfUnion(keyB.getType(), Type.NUMERIC)
                            && ((NumericValue) keyB).isNaN());

                if (aIsEmpty && bIsEmpty) {
                    cmp = Constants.EQUAL;
                } else if (aIsEmpty) {
                    cmp = (spec.getModifiers() & OrderSpec.EMPTY_LEAST) != 0
                            ? Constants.INFERIOR : Constants.SUPERIOR;
                } else if (bIsEmpty) {
                    cmp = (spec.getModifiers() & OrderSpec.EMPTY_LEAST) != 0
                            ? Constants.SUPERIOR : Constants.INFERIOR;
                } else {
                    cmp = keyA.compareTo(spec.getCollator(), keyB);
                }
            } catch (final XPathException e) {
                // fall through with cmp unchanged
            }

            if ((spec.getModifiers() & OrderSpec.DESCENDING_ORDER) != 0) {
                cmp = -cmp;
            }
            if (cmp != Constants.EQUAL) {
                break;
            }
        }

        // Stable sort: preserve original insertion order when all keys are equal
        if (cmp == Constants.EQUAL) {
            cmp = Integer.compare(a.originalPos, b.originalPos);
        }
        return cmp;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("order by ");
        for (int i = 0; i < orderSpecs.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            dumper.display(orderSpecs.get(i));
        }
        dumper.nl();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("order by ");
        for (int i = 0; i < orderSpecs.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(orderSpecs.get(i));
        }
        return builder.toString();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitOrderByClause(this);
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        returnExpr.resetState(postOptimization);
        stack.clear();
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> vars = new HashSet<>();

        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            vars.add(startVar.getQName());
        }

        return vars;
    }

    // -------------------------------------------------------------------------
    // Internal state classes
    // -------------------------------------------------------------------------

    /** Per-invocation state collected during {@link #eval} and consumed in {@link #postEval}. */
    private static class OrderByData {
        final List<OrderByTuple> tuples = new ArrayList<>();
        /** One BitSet per OrderSpec slot, tracking which primitive types appear in sort keys. */
        final List<BitSet> encounteredPrimitiveTypes;
        /** Template LocalVariables declared in postEval(); keys = variable QNames. */
        final Map<QName, LocalVariable> variableTemplates = new LinkedHashMap<>();
        /**
         * The {@code lastVar} snapshot taken in {@link #preEval} — the last variable
         * in scope when preEval ran (equals {@code rootClause.getStartVariable()} in the
         * simple case, or the positional variable {@code $pos} if one was declared after
         * {@code $x}).  Its {@code .before} link points to the pre-FLWOR anchor X; this
         * is used in {@link #eval} to recover the first active variable after GroupByClause
         * has replaced the tuple variables (via {@code startVar.before.after}).
         */
        LocalVariable mark = null;
        boolean initialized = false;

        OrderByData(final int numOrderSpecs) {
            this.encounteredPrimitiveTypes = new ArrayList<>(numOrderSpecs);
            for (int i = 0; i < numOrderSpecs; i++) {
                this.encounteredPrimitiveTypes.add(new BitSet(Type.ARRAY_ITEM + 1));
            }
        }
    }

    /** A snapshot of one tuple: all in-scope variable values and pre-evaluated sort keys. */
    private static class OrderByTuple {
        final Map<QName, Sequence> variableValues;
        final List<AtomicValue> sortKeys;
        /** Original insertion position, used to guarantee a stable sort. */
        final int originalPos;

        OrderByTuple(final Map<QName, Sequence> variableValues,
                     final List<AtomicValue> sortKeys,
                     final int originalPos) {
            this.variableValues = variableValues;
            this.sortKeys = sortKeys;
            this.originalPos = originalPos;
        }
    }
}
