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
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Implements the inline-for-index-pre-select rewrite invoked from
 * {@link LetExpr#optimize(CompileContext)}.
 *
 * <p>Rewrites
 * <pre>
 *   let $v := &lt;persistent path&gt; return $v[Optimizable-pred]
 * </pre>
 * into
 * <pre>
 *   &lt;persistent path&gt;[Optimizable-pred]
 * </pre>
 * by attaching the predicate to the last LocationStep of the input path. The
 * resulting tree is identical in shape to the direct form, so the legacy
 * {@link Optimizer} pass that runs immediately after the {@code optimize(cc)}
 * pass wraps it in the {@code (#exist:optimize#)} pragma and routes the
 * predicate through the index pre-select machinery — closing GH-873.
 *
 * <p>Without this rewrite, the indirect form runs about 167x slower than the
 * direct form because {@code Optimize.eval} computes a pre-selected node set
 * and then {@code FilteredExpression.eval} re-evaluates {@code $v} from the
 * variable stack (ignoring the pre-selected context), so the index hit-set is
 * thrown away and the predicate runs once per node in the full input.
 *
 * <p>The gate predicates are intentionally narrow in v1: each one corresponds
 * to a soundness or scope concern documented in the design doc accompanying
 * the change. See the comments on {@link #tryInline(LetExpr, CompileContext)}
 * for the precise list.
 */
final class LetInliner {

    private LetInliner() {
    }

    /**
     * Attempt to inline the binding. Returns the LetExpr's replacement if all
     * gates pass, or {@code let} unchanged if any gate fails.
     *
     * <p>Gates (all must hold):
     * <ol>
     *   <li>{@code !scoreBinding} and a non-null variable name -- score
     *       bindings (XQFT 3.0 §2.3) bind a synthesised double, not the
     *       input value, so inlining changes semantics;</li>
     *   <li>{@code getPreviousClause() == null} and the body is not itself
     *       another FLWOR clause -- limits v1 to standalone lets;</li>
     *   <li>no static type declared on the binding -- typed declarations
     *       impose a runtime check on the variable's value that inlining
     *       would silently bypass;</li>
     *   <li>the input is a node-typed expression -- only node sequences
     *       benefit from a downstream index pre-select;</li>
     *   <li>the input contains at least one non-wildcard LocationStep --
     *       this is what an index can attach to;</li>
     *   <li>the body is exactly {@code FilteredExpression} (or a
     *       length-1 PathExpr wrapping one) whose source is the bound
     *       variable, with exactly one predicate that contains an
     *       {@link Optimizable}, and the variable is not referenced
     *       anywhere else in the body.</li>
     * </ol>
     */
    static Expression tryInline(final LetExpr let, final CompileContext cc) {
        // Gate 1
        final QName varName = let.getVariable();
        if (varName == null || let.isScoreBinding()) {
            return let;
        }
        // Gate 2
        if (let.getPreviousClause() != null) {
            return let;
        }
        final Expression returnExpr = let.getReturnExpression();
        if (returnExpr == null || returnExpr instanceof FLWORClause) {
            return let;
        }
        // Gate 3 -- BindingExpression.sequenceType is protected; same-package access.
        if (let.sequenceType != null) {
            return let;
        }
        // Gate 4
        final Expression inputSequence = let.getInputSequence();
        if (inputSequence == null
                || !Type.subTypeOf(inputSequence.returnsType(), Type.NODE)) {
            return let;
        }
        // Gate 5: at least one non-wildcard LocationStep in the input. Pick
        // the last one as the predicate-attachment site -- semantically the
        // predicate filters the OUTPUT of the path, which is what the last
        // step yields.
        final LocationStep lastStep = findLastNamedStep(inputSequence);
        if (lastStep == null) {
            return let;
        }

        // Gate 6: body must be a FilteredExpression (or length-1 PathExpr
        // wrapping one) whose source is $varName, with exactly one
        // Optimizable predicate, and $varName must not appear anywhere
        // else in the body.
        final FilteredExpression fe = unwrapFilteredExpression(returnExpr);
        if (fe == null) {
            return let;
        }
        final Expression feSrc = fe.getExpression();
        if (!(feSrc instanceof final VariableReference vr)
                || !varName.equals(vr.getName())) {
            return let;
        }
        final List<Predicate> preds = fe.getPredicates();
        if (preds.size() != 1) {
            return let;
        }
        final Predicate pred = preds.get(0);
        if (!hasOptimizable(pred)) {
            return let;
        }
        final RefCounter counter = new RefCounter(varName);
        returnExpr.accept(counter);
        if (counter.count != 1) {
            // The variable appears outside the FilteredExpression source --
            // a literal substitution would leave dangling references.
            return let;
        }

        // Substitute: attach the predicate to the input's last named step.
        // The legacy Optimizer pass that runs next will detect the
        // Optimizable predicate, wrap the rewritten path in the
        // (#exist:optimize#) pragma, and route through the index pre-select
        // (Optimizer.visitLocationStep / Optimize.before).
        lastStep.addPredicate(pred);
        return cc.replaceWith(let, inputSequence,
                "inline let $" + varName.getLocalPart() + " for index pre-select");
    }

    /**
     * Return the last non-wildcard LocationStep in {@code expr}, or null if
     * none. Order matches document-order traversal of the path.
     */
    private static @Nullable LocationStep findLastNamedStep(final Expression expr) {
        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(expr);
        LocationStep last = null;
        for (final LocationStep s : steps) {
            if (s != null && !s.getTest().isWildcardTest()) {
                last = s;
            }
        }
        return last;
    }

    /**
     * Unwrap {@link DebuggableExpression} and length-1 {@link PathExpr}
     * containers to expose a {@link FilteredExpression}, or return null if
     * the underlying shape isn't one. Mirrors the unwrap rule used
     * elsewhere in the engine to look past parser-introduced wrappers.
     */
    private static @Nullable FilteredExpression unwrapFilteredExpression(final Expression expr) {
        Expression current = expr;
        while (true) {
            if (current instanceof final FilteredExpression filtered) {
                return filtered;
            } else if (current instanceof final DebuggableExpression debug) {
                current = debug.getFirst();
            } else if (current instanceof final PathExpr pathExpr && pathExpr.getLength() == 1) {
                current = pathExpr.getExpression(0);
            } else {
                return null;
            }
        }
    }

    /**
     * Reuses the engine's existing Optimizable-detection visitor so the
     * "is this predicate index-eligible?" question gets the same answer
     * the legacy Optimizer pass would give.
     */
    private static boolean hasOptimizable(final Predicate predicate) {
        final Optimizer.FindOptimizable visitor = new Optimizer.FindOptimizable();
        predicate.accept(visitor);
        final Optimizable[] optimizables = visitor.getOptimizables();
        return optimizables != null && optimizables.length > 0;
    }

    /**
     * Counts {@link VariableReference} nodes referring to a target name
     * across an expression tree. Descends explicitly into
     * {@link FilteredExpression} (BasicExpressionVisitor's default does
     * not), so a $v that sits as the source of a FE is still counted.
     */
    private static final class RefCounter extends DefaultExpressionVisitor {
        private final QName target;
        int count = 0;

        RefCounter(final QName target) {
            this.target = target;
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            if (target.equals(ref.getName())) {
                count++;
            }
        }

        @Override
        public void visitFilteredExpr(final FilteredExpression filtered) {
            filtered.getExpression().accept(this);
            for (final Predicate p : filtered.getPredicates()) {
                p.accept(this);
            }
        }
    }
}
