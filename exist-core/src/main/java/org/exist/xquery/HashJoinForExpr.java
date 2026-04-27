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

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hash-join replacement for the {@code for $i in <invariant> where $i/key
 * = $outer/key return ...} pattern.
 *
 * Produced by {@link ForExpr#optimize(CompileContext)} when it recognises an
 * inner FOR whose body is a {@link WhereClause} containing a single
 * equality {@link GeneralComparison} between an inner-scope path
 * (referencing this FOR's variable) and a probe expression that does NOT
 * reference any inner-scope variable. Replaces the linear scan of the input
 * sequence with an O(N+M) build/probe: a hash map keyed by the join value
 * is built once on first eval (or whenever the input {@link Sequence}
 * reference changes — handles fresh function invocations), and probed per
 * outer iteration with the outer key.
 *
 * Intentional restrictions for v1:
 * <ul>
 *   <li>Operator must be {@code =} (general equality). Other operators
 *       require sorted structures, not hash maps.</li>
 *   <li>The body after the where clause must NOT be a FLWOR clause
 *       (no order-by / group-by / for-let chaining), so the per-match
 *       evaluation matches the original semantics.</li>
 *   <li>The FOR must have no positional, score, or {@code allowing empty}
 *       extras — these affect iteration semantics that hash join skips.</li>
 *   <li>Hash keys are normalised to the atomized value's
 *       {@code stringValue()}. This is provably correct when both sides
 *       atomize to {@code xs:string} or {@code xs:untypedAtomic} (the XMark
 *       case). The detection in {@link ForExpr} restricts to this case.</li>
 * </ul>
 *
 * Cache lifetime: the hash is keyed by the {@link Sequence} reference of
 * the input, mirroring BaseX's {@code CmpHashG}/{@code CmpCache} pattern.
 * Multiple per-outer-iteration calls within the same query share the hash;
 * a fresh function invocation produces a new input Sequence (fresh
 * let-bindings), so the cache is rebuilt — sidestepping the cross-call
 * lifetime bug that broke the earlier eval-time-cache attempt
 * (see {@code joe-vault/Claude/exist/query-optimizer-overhaul.md}).
 */
public class HashJoinForExpr extends ForExpr {

    /** Side of the comparison referencing this FOR's variable: 0 = left, 1 = right. */
    private final int innerSide;

    /** Last-seen input {@link Sequence} reference; rebuilds {@link #hashIndex} on change. */
    private Sequence cachedInputRef;

    /** key → matching items (in input order). */
    private Map<String, List<Item>> hashIndex;

    public HashJoinForExpr(final XQueryContext context, final ForExpr original,
                           final int innerSide) {
        super(context, false /* allowingEmpty — gated off by detection */);
        setVariable(original.getVariable());
        // sequenceType is a protected field on BindingExpression; copy directly
        this.sequenceType = original.sequenceType;
        setInputSequence(original.getInputSequence());
        setReturnExpression(original.getReturnExpression());
        this.innerSide = innerSide;
    }

    /** Already in hash-join form — no further structural rewrite. */
    @Override
    public Expression optimize(final CompileContext cc) throws XPathException {
        return this;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                    "OPTIMIZATION", "Hash-join FLWOR");
        }
        context.expressionStart(this);

        final Sequence resultSequence = new ValueSequence(unordered);
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            // Evaluate input — if hoisted to a let, this is a constant-time
            // VariableReference resolution.
            final Sequence in = inputSequence.eval(contextSequence, null);
            clearContext(getExpressionId(), in);
            registerUpdateListener(in);

            // Declare $i — bound transiently while building the hash and
            // again per match while evaluating the return body.
            final LocalVariable var = createVariable(getVariable());
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);
            if (in instanceof NodeSet) {
                var.setContextDocs(in.getDocumentSet());
            } else {
                var.setContextDocs(null);
            }

            // (Re)build hash if input identity changed since last eval.
            if (cachedInputRef != in) {
                buildHash(var, in);
                cachedInputRef = in;
            }

            // Probe: evaluate the outer-side expression in OUTER scope.
            // $i is currently bound; the probeExpr does not reference it
            // (verified in ForExpr.optimize when the rewrite was decided).
            final Expression probeExpr = getProbeExpr();
            final Sequence probeSeq = probeExpr.eval(contextSequence, contextItem);

            if (!probeSeq.isEmpty() && hashIndex != null && !hashIndex.isEmpty()) {
                // LinkedHashSet: dedupe across probe keys, preserve first-encounter order.
                final Set<Item> matches = new LinkedHashSet<>();
                for (final SequenceIterator probeIter = probeSeq.iterate(); probeIter.hasNext();) {
                    final Item probeItem = probeIter.nextItem();
                    final AtomicValue probeKey = probeItem.atomize();
                    final String keyStr = probeKey.getStringValue();
                    final List<Item> bucket = hashIndex.get(keyStr);
                    if (bucket != null) {
                        matches.addAll(bucket);
                    }
                }

                final Expression body = getBodyExpr();
                for (final Item match : matches) {
                    var.setValue(match.toSequence());
                    var.checkType();
                    final Sequence sub = body.eval(null, null);
                    resultSequence.addAll(sub);
                }
            }
        } finally {
            context.popLocalVariables(mark, resultSequence);
        }

        setActualReturnType(resultSequence.getItemType());
        context.expressionEnd(this);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", resultSequence);
        }
        return resultSequence;
    }

    /**
     * Build the hash by binding {@code var} to each input item and
     * evaluating the inner-side expression. Multi-key items (rare in
     * practice — XMark attribute joins are 1-key) are inserted under each
     * key; per-match dedupe in {@link #eval} handles the join semantics.
     */
    private void buildHash(final LocalVariable var, final Sequence in) throws XPathException {
        hashIndex = new HashMap<>();
        final Expression keyExtractor = getKeyExtractor();
        for (final SequenceIterator it = in.iterate(); it.hasNext();) {
            final Item item = it.nextItem();
            var.setValue(item.toSequence());
            final Sequence keySeq = keyExtractor.eval(null, null);
            for (final SequenceIterator ki = keySeq.iterate(); ki.hasNext();) {
                final AtomicValue key = ki.nextItem().atomize();
                final String keyStr = key.getStringValue();
                hashIndex.computeIfAbsent(keyStr, k -> new ArrayList<>()).add(item);
            }
        }
    }

    private Expression getKeyExtractor() {
        final GeneralComparison cmp = getComparison();
        return innerSide == 0 ? cmp.getLeft() : cmp.getRight();
    }

    private Expression getProbeExpr() {
        final GeneralComparison cmp = getComparison();
        return innerSide == 0 ? cmp.getRight() : cmp.getLeft();
    }

    private GeneralComparison getComparison() {
        final WhereClause wc = (WhereClause) getReturnExpression();
        Expression w = wc.getWhereExpr();
        // Unwrap parser-inserted DebuggableExpression / single-step PathExpr.
        while (true) {
            if (w instanceof DebuggableExpression d) {
                w = d.getFirst();
            } else if (w instanceof PathExpr p && p.getLength() == 1) {
                w = p.getExpression(0);
            } else {
                break;
            }
        }
        return (GeneralComparison) w;
    }

    private Expression getBodyExpr() {
        // Body is the WhereClause's return expression — kept as-is (the parser's
        // DebuggableExpression wrapper retains debugger fidelity at runtime).
        return ((WhereClause) getReturnExpression()).getReturnExpression();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            cachedInputRef = null;
            hashIndex = null;
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("(* hash-join *) ", line);
        super.dump(dumper);
    }

    @Override
    public String toString() {
        return "(* hash-join *) " + super.toString();
    }
}
