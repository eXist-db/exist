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
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compilation context passed to {@link Expression#optimize(CompileContext)}.
 *
 * Carries the {@link XQueryContext}, captures rewrite decisions for visibility
 * (consumed by {@code util:explain}-style diagnostics), and records whether any
 * rewrite occurred so the driver knows to re-analyze.
 *
 * The {@link #replaceWith(Expression, Expression, String)} helper is the
 * preferred return path: each {@code optimize()} method computes its
 * replacement and returns {@code cc.replaceWith(this, replacement, reason)}.
 */
public final class CompileContext {

    /** Internal namespace for synthesized hoist variables. */
    public static final String OPTIMIZER_NS =
            "http://exist-db.org/xquery/internal/optimizer";

    public final XQueryContext qc;

    private final List<String> log = new ArrayList<>();
    private boolean changed;

    private final Deque<FlworChainScope> flworScopes = new ArrayDeque<>();
    private int hoistedVarCounter;

    public CompileContext(final XQueryContext qc) {
        this.qc = qc;
    }

    /**
     * Records an expression rewrite and returns the replacement.
     *
     * If {@code replacement == original}, this is a no-op and nothing is
     * logged. Callers can therefore unconditionally return
     * {@code cc.replaceWith(this, candidate, reason)} — when the candidate
     * happens to be {@code this}, the log stays clean.
     *
     * @param original    the expression being rewritten
     * @param replacement the replacement (may be {@code original})
     * @param reason      short human-readable cause, e.g. {@code "constant fold"}
     * @return {@code replacement}
     */
    public Expression replaceWith(final Expression original, final Expression replacement,
                                  final String reason) {
        if (replacement != original) {
            changed = true;
            log.add(String.format("REWRITE %s → %s (%s)",
                    abbreviate(original), abbreviate(replacement), reason));
        }
        return replacement;
    }

    /** Free-form info entry for diagnostics. */
    public void info(final String fmt, final Object... args) {
        log.add(args.length == 0 ? fmt : String.format(fmt, args));
    }

    /**
     * Pre-evaluates an expression with no dependencies and returns a literal
     * wrapping the result. Caller is responsible for ensuring
     * {@code expr.getDependencies() == Dependency.NO_DEPENDENCY}.
     *
     * @param expr expression to pre-evaluate
     * @return a {@link LiteralValue} wrapping the result, or {@code expr}
     *         itself if the result is not an {@link AtomicValue}
     * @throws XPathException if evaluation fails
     */
    public Expression preEval(final Expression expr) throws XPathException {
        final Sequence value = expr.eval(null, null);
        if (value.hasOne() && value.itemAt(0) instanceof AtomicValue atom) {
            final LiteralValue literal = new LiteralValue(qc, atom);
            literal.setLocation(expr.getLine(), expr.getColumn());
            return replaceWith(expr, literal, "constant fold");
        }
        return expr;
    }

    /** True if any {@link #replaceWith} actually swapped expressions. */
    public boolean hasOptimized() {
        return changed;
    }

    /** Read-only view of the log. */
    public List<String> log() {
        return Collections.unmodifiableList(log);
    }

    private static String abbreviate(final Expression e) {
        if (e == null) {
            return "<null>";
        }
        final String s = e.toString();
        if (s.length() <= 60) {
            return s;
        }
        return s.substring(0, 57) + "...";
    }

    /* ===== FLWOR chain scope tracking (loop-invariant hoisting) ===== */

    /**
     * Enter a new FLWOR chain scope. Called by a {@link FLWORClause} whose
     * {@link FLWORClause#getPreviousClause()} is null (i.e., the head of a
     * chain). Subsequent clauses in the same chain share the scope.
     *
     * Each pushed scope tracks (a) the QNames of variables visible to
     * sub-expressions evaluated within it and (b) hoist actions to apply
     * when the chain head's {@code optimize()} returns.
     */
    public void enterFlworChain() {
        flworScopes.push(new FlworChainScope());
    }

    /** True if at least one FLWOR chain scope is active. */
    public boolean inFlworChain() {
        return !flworScopes.isEmpty();
    }

    /** Number of nested FLWOR chain scopes currently active. */
    public int flworChainDepth() {
        return flworScopes.size();
    }

    /**
     * Add a variable to the innermost (current) FLWOR chain scope. Vars are
     * routed to one of two buckets based on whether the chain has yet seen a
     * {@code for}-clause (recorded via {@link #recordForClause(FLWORClause)}):
     * <ul>
     *   <li>let-prefix vars (before the first FOR) — once-evaluated; safe
     *       for a hoisted expression to reference;</li>
     *   <li>loop-body vars (the FOR's variable, plus everything after) —
     *       per-iteration; a hoist that references one is unsafe and must be
     *       refused.</li>
     * </ul>
     */
    public void addVisibleFlworVar(final QName name) {
        if (name != null && !flworScopes.isEmpty()) {
            flworScopes.peek().recordVar(name);
        }
    }

    /**
     * Mark this clause as the chain's first {@code for} (or no-op if a prior
     * for was already seen). Determines two things on the current scope:
     * <ol>
     *   <li>subsequent {@link #addVisibleFlworVar} entries flow into the
     *       loop-body bucket;</li>
     *   <li>the FOR clause becomes the hoist insertion point — synthesised
     *       lets are spliced in just before it.</li>
     * </ol>
     */
    public void recordForClause(final FLWORClause forClause) {
        if (forClause != null && !flworScopes.isEmpty()) {
            flworScopes.peek().recordForClause(forClause);
        }
    }

    /**
     * Returns the union of LOOP-BODY variables visible across all OUTER FLWOR
     * scopes (excluding the current). These are the variables a hoist
     * candidate must NOT reference to be safe — they vary per outer iteration.
     * Let-prefix variables (bound before any outer FOR) are intentionally
     * excluded: they are once-evaluated, so a hoisted expression referencing
     * one remains correct after the rewrite.
     */
    public Set<QName> getOuterLoopBodyVars() {
        final Set<QName> result = new HashSet<>();
        if (flworScopes.size() <= 1) {
            return result;
        }
        boolean first = true;
        for (final FlworChainScope scope : flworScopes) {
            if (first) { first = false; continue; }
            result.addAll(scope.loopBodyVars);
        }
        return result;
    }

    /**
     * Generate a unique QName for a hoisted variable. The local part
     * incorporates a per-context counter; the namespace is reserved for the
     * optimizer so synthesized names cannot collide with user code.
     */
    public QName generateHoistedVarName() {
        ++hoistedVarCounter;
        return new QName("__hoisted_" + hoistedVarCounter, OPTIMIZER_NS, "__opt");
    }

    /**
     * Queue a hoist on the OUTERMOST chain scope that actually has a
     * {@code for}-clause to insert before. Pure let-only chains (e.g. an
     * outer {@code let $auction := …}) cannot host a hoist — there's no
     * loop to lift it out of and no insertion point for
     * {@code applyHoistsAndExitChain}. Skip them and let the next inner
     * scope (which presumably contains the loop the hoist is meant to
     * escape) take it.
     */
    public void addPendingHoistToOutermost(final QName varName, final Expression expr) {
        if (flworScopes.isEmpty()) {
            return;
        }
        FlworChainScope target = null;
        // Iterate head→tail (innermost→outermost). Last scope with a FOR wins.
        for (final FlworChainScope scope : flworScopes) {
            if (scope.firstForClause != null) {
                target = scope;
            }
        }
        if (target == null) {
            return;  // no FOR anywhere: nothing to lift over.
        }
        target.pendingHoists.add(new HoistAction(varName, expr));
        changed = true;
    }

    /**
     * Pop the innermost FLWOR scope and, if it carries pending hoists, splice
     * a chain of {@link LetExpr} clauses into the chain just before its
     * outermost {@code for}-clause (the {@code firstForClause} recorded by
     * {@link #recordForClause(FLWORClause)}).
     *
     * Two cases:
     * <ol>
     *   <li>The first FOR is the chain head itself — wrap {@code chainHead}
     *       with the new lets and return the new head; the caller's
     *       {@code returnExpr.optimize(cc)} captures the replacement.</li>
     *   <li>The first FOR is mid-chain (a {@code let}-prefix precedes it) —
     *       splice the new lets between the prefix's last clause and the FOR
     *       in place; the chain head stays the same and is returned
     *       unchanged.</li>
     * </ol>
     *
     * If a scope has hoists but no FOR was recorded, the hoists are silently
     * dropped (no iteration, no benefit).
     */
    public Expression applyHoistsAndExitChain(final Expression chainHead) {
        if (flworScopes.isEmpty()) {
            return chainHead;
        }
        final FlworChainScope scope = flworScopes.pop();
        if (scope.pendingHoists.isEmpty() || scope.firstForClause == null) {
            return chainHead;
        }

        if (scope.firstForClause == chainHead) {
            // Wrap the chain head; new head returned to caller.
            Expression result = chainHead;
            for (final HoistAction h : scope.pendingHoists) {
                final LetExpr letExpr = buildHoistLet(h, result);
                if (result instanceof FLWORClause flwor) {
                    flwor.setPreviousClause(letExpr);
                }
                logHoist(h, "head wrap");
                result = letExpr;
            }
            return result;
        }

        // Mid-chain splice: find predecessor of firstForClause along the
        // chain head's returnExpression links.
        final FLWORClause predecessor = findPredecessor(chainHead, scope.firstForClause);
        if (predecessor == null) {
            // Defensive: the chain isn't shaped how we expect — skip rather
            // than risk a malformed chain.
            return chainHead;
        }

        Expression hoistChain = scope.firstForClause;
        for (final HoistAction h : scope.pendingHoists) {
            final LetExpr letExpr = buildHoistLet(h, hoistChain);
            if (hoistChain instanceof FLWORClause flwor) {
                flwor.setPreviousClause(letExpr);
            }
            logHoist(h, "mid-chain splice");
            hoistChain = letExpr;
        }
        predecessor.setReturnExpression(hoistChain);
        if (hoistChain instanceof FLWORClause flwor) {
            flwor.setPreviousClause(predecessor);
        }
        return chainHead;
    }

    private LetExpr buildHoistLet(final HoistAction h, final Expression body) {
        final LetExpr letExpr = new LetExpr(qc);
        letExpr.setVariable(h.varName);
        letExpr.setInputSequence(h.expr);
        letExpr.setReturnExpression(body);
        return letExpr;
    }

    private void logHoist(final HoistAction h, final String mode) {
        log.add(String.format("HOIST(%s): let $%s := %s",
                mode, h.varName.getLocalPart(), abbreviate(h.expr)));
    }

    private static FLWORClause findPredecessor(final Expression chainHead,
                                               final FLWORClause target) {
        if (!(chainHead instanceof FLWORClause cur)) {
            return null;
        }
        while (cur != null) {
            final Expression next = cur.getReturnExpression();
            if (next == target) {
                return cur;
            }
            if (next instanceof FLWORClause nextClause) {
                cur = nextClause;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * State for one active FLWOR chain scope. Tracks bound variables in two
     * buckets ({@link #letPrefixVars} vs {@link #loopBodyVars}, separated by
     * the first {@code for}-clause encountered) and queued hoists targeting
     * this scope's outermost FOR position.
     */
    private static final class FlworChainScope {
        final Set<QName> letPrefixVars = new HashSet<>();
        final Set<QName> loopBodyVars = new HashSet<>();
        FLWORClause firstForClause = null;
        boolean firstForSeen = false;
        final List<HoistAction> pendingHoists = new ArrayList<>();

        void recordVar(final QName name) {
            if (firstForSeen) {
                loopBodyVars.add(name);
            } else {
                letPrefixVars.add(name);
            }
        }

        void recordForClause(final FLWORClause forClause) {
            if (!firstForSeen) {
                firstForClause = forClause;
                firstForSeen = true;
            }
        }
    }

    /** A pending hoist: bind {@code varName} to {@code expr} via a new let. */
    private static final class HoistAction {
        final QName varName;
        final Expression expr;

        HoistAction(final QName varName, final Expression expr) {
            this.varName = varName;
            this.expr = expr;
        }
    }
}
