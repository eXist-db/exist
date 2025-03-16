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

import org.exist.storage.DBBroker;
import org.exist.xquery.functions.array.ArrayConstructor;
import org.exist.xquery.pragmas.Optimize;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;

import javax.annotation.Nullable;
import java.util.*;

import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

/**
 * Analyzes the query and marks optimizable expressions for the query engine.
 * This class just searches for potentially optimizable expressions in the query tree and
 * encloses those expressions with an (#exist:optimize#) pragma. The real optimization
 * work is not done by this class but by the pragma (see {@link org.exist.xquery.pragmas.Optimize}).
 * The pragma may also decide that the optimization is not applicable and just execute
 * the expression without any optimization.
 *
 * Currently, the optimizer is disabled by default. To enable it, set attribute enable-query-rewriting
 * to yes in conf.xml:
 *
 *  &lt;xquery enable-java-binding="no" enable-query-rewriting="yes"&gt;...
 * 
 * To enable/disable the optimizer for a single query, use an option:
 *
 * <pre>declare option exist:optimize "enable=yes|no";</pre>
 *
 */
public class Optimizer extends DefaultExpressionVisitor {

    private static final Logger LOG = LogManager.getLogger(Optimizer.class);

    private final XQueryContext context;
    private final List<QueryRewriter> rewriters;
    private final FindOptimizable findOptimizable = new FindOptimizable();

    private int predicates = 0;

    private boolean hasOptimized = false;

    public Optimizer(final XQueryContext context) {
        this.context = context;
        final DBBroker broker = context.getBroker();
        this.rewriters = broker != null ? broker.getIndexController().getQueryRewriters(context) : Collections.emptyList();
    }

    public boolean hasOptimized() {
        return hasOptimized;
    }

    @Override
    public void visitLocationStep(final LocationStep locationStep) {
        super.visitLocationStep(locationStep);

        // check query rewriters if they want to rewrite the location step
        Pragma optimizePragma = null;
        try {  // Keep try-catch out of loop
            for (final QueryRewriter rewriter : rewriters) {
                optimizePragma = rewriter.rewriteLocationStep(locationStep);
                if (optimizePragma != null) {
                    // expression was rewritten: return
                    hasOptimized = true;
                    break;
                }
            }
        } catch (final XPathException e) {
            LOG.warn("Exception called while rewriting location step: {}", e.getMessage(), e);
        }

        boolean optimize = false;
        // only location steps with predicates can be optimized:
        @Nullable final Predicate[] preds = locationStep.getPredicates();
        if (preds != null) {
            // walk through the predicates attached to the current location step.
            // try to find a predicate containing an expression which is an instance
            // of Optimizable.
            for (final Predicate pred : preds) {
                pred.accept(findOptimizable);
                @Nullable final Optimizable[] list = findOptimizable.getOptimizables();
                if (canOptimize(list)) {
                    optimize = true;
                }
                findOptimizable.reset();
                if (optimize) {
                    break;
                }
            }
        }

        final Expression parent = locationStep.getParentExpression();

        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            if (!(parent instanceof final RewritableExpression path)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parent expression of step is not a PathExpr: {}", parent);
                }
                return;
            }

            hasOptimized = true;

            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                if (optimizePragma != null) {
                    extension.addPragma(optimizePragma);
                }
                extension.addPragma(new Optimize(extension, context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(locationStep);
                
                // Replace the old expression with the pragma
                path.replace(locationStep, extension);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Rewritten expression: {}", ExpressionDumper.dump(parent));
                }
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: {}: {}", locationStep, e.getMessage(), e);
            }
        } else if (optimizePragma != null) {
            final ExtensionExpression extension = new ExtensionExpression(context);
            extension.addPragma(optimizePragma);
            extension.setExpression(locationStep);

            // Replace the old expression with the pragma
            final RewritableExpression path = (RewritableExpression) parent;
            path.replace(locationStep, extension);
        }
    }

    @Override
    public void visitFilteredExpr(final FilteredExpression filtered) {
        super.visitFilteredExpr(filtered);

        // check if filtered expression can be simplified:
        // handles expressions like //foo/(baz)[...]
        if (filtered.getExpression() instanceof final LocationStep step) {
            // single location step: simplify by directly attaching it to the parent path expression
            final Expression parent = filtered.getParent();
            if (parent instanceof final RewritableExpression rewritableParentExpression) {
                final List<Predicate> preds = filtered.getPredicates();
                final boolean optimizable = hasOptimizable(preds);
                if (optimizable) {
                    // copy predicates
                    for (Predicate pred : preds) {
                        step.addPredicate(pred);
                    }
                    rewritableParentExpression.replace(filtered, step);
                    step.setParent(parent);
                    visitLocationStep(step);
                    return;
                }
            }
        }

        // check if there are any predicates which could be optimized
        final List<Predicate> preds = filtered.getPredicates();
        final boolean optimize = hasOptimizable(preds);
        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            final Expression parent = filtered.getParent();
            if (!(parent instanceof final RewritableExpression path)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parent expression: {} of step does not implement RewritableExpression", parent.getClass().getName());
                }
                return;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Rewriting expression: {}", ExpressionDumper.dump(filtered));
            }

            hasOptimized = true;

            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                extension.addPragma(new Optimize(extension, context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(filtered);
                // Replace the old expression with the pragma
                path.replace(filtered, extension);
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: {}: {}", filtered, e.getMessage(), e);
            }
        }
    }

    private boolean hasOptimizable(final List<Predicate> preds) {
        // walk through the predicates attached to the current location step.
        // try to find a predicate containing an expression which is an instance
        // of Optimizable.
        boolean optimizable = false;
        for (final Predicate pred : preds) {
            pred.accept(findOptimizable);
            @Nullable final Optimizable[] list = findOptimizable.getOptimizables();
            if (canOptimize(list)) {
                optimizable = true;
            }
            findOptimizable.reset();
            if (optimizable) {
                break;
            }
        }
        return optimizable;
    }

    @Override
    public void visitAndExpr(final OpAnd and) {
        if (predicates > 0) {
            // inside a filter expression, we can often replace a logical and with
            // a chain of filters, which can then be further optimized
            Expression parent = and.getParent();
            if (!(parent instanceof PathExpr)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parent expression of boolean operator is not a PathExpr: {}", parent);
                }
                return;
            }

            final PathExpr path;
            final Predicate predicate;
            if (parent instanceof Predicate) {
                predicate = (Predicate) parent;
                path = predicate;
            } else {
                path = (PathExpr) parent;
                parent = path.getParent();
                if (!(parent instanceof Predicate) || path.getLength() > 1) {
                    LOG.debug("Boolean operator is not a top-level expression in the predicate: {}", parent == null ? "?" : parent.getClass().getName());
                    return;
                }
                predicate = (Predicate) parent;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Rewriting boolean expression: {}", ExpressionDumper.dump(and));
            }
            hasOptimized = true;
            final LocationStep step = (LocationStep) predicate.getParent();
            final Predicate newPred = new Predicate(context);
            newPred.add(simplifyPath(and.getRight()));
            step.insertPredicate(predicate, newPred);
            path.replace(and, simplifyPath(and.getLeft()));
        } else if (and.isRewritable()) {
        	and.getLeft().accept(this);
			and.getRight().accept(this);
        }
    }

    @Override
    public void visitOrExpr(final OpOr or) {
    	if (or.isRewritable()) {
        	or.getLeft().accept(this);
			or.getRight().accept(this);
        }
	}

    @Override
    public void visitGeneralComparison(final GeneralComparison comparison) {
        // Check if the left operand is a path expression ending in a
        // text() step. This step is unnecessary and makes it hard
        // to further optimize the expression. We thus try to remove
        // the extra text() step automatically.
        // TODO should insert a pragma instead of removing the step
        // we don't know at this point if there's an index to use
//        Expression expr = comparison.getLeft();
//        if (expr instanceof PathExpr) {
//            PathExpr pathExpr = (PathExpr) expr;
//            Expression last = pathExpr.getLastExpression();
//            if (pathExpr.getLength() > 1 && last instanceof Step && ((Step)last).getTest().getType() == Type.TEXT) {
//                pathExpr.remove(last);
//            }
//        }
        comparison.getLeft().accept(this);
        comparison.getRight().accept(this);
    }

    @Override
    public void visitPredicate(final Predicate predicate) {
        ++predicates;
        super.visitPredicate(predicate);
        --predicates;
    }

    /**
     * Check if a global variable can be inlined, usually if it
     * references a literal value or sequence thereof.
     *
     * @param ref the variable reference
     */
    @Override
    public void visitVariableReference(final VariableReference ref) {
        final String ns = ref.getName().getNamespaceURI();
        if (ns != null && !ns.isEmpty()) {

            final Module[] modules = context.getModules(ns);
            if (isNotEmpty(modules)) {
                for (final Module module : modules) {
                    if (module != null && !module.isInternalModule()) {
                        final Collection<VariableDeclaration> vars = ((ExternalModule) module).getVariableDeclarations();
                        for (final VariableDeclaration var: vars) {
                            if (var.getName().equals(ref.getName()) && var.getExpression().isPresent()) {
                                var.getExpression().get().accept(this);
                                final Expression expression = simplifyPath(var.getExpression().get());
                                final InlineableVisitor visitor = new InlineableVisitor();
                                expression.accept(visitor);
                                if (visitor.isInlineable()) {
                                    final Expression parent = ref.getParent();
                                    if (parent instanceof final RewritableExpression parentRewritableExpression) {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("{} line {}: inlining variable {}", ref.getSource().toString(), ref.getLine(), ref.getName());
                                        }
                                        parentRewritableExpression.replace(ref, expression);
                                    }
                                }

                                return;  // exit function!
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean canOptimize(@Nullable final Optimizable[] list) {
        if (list == null || list.length == 0) {
            return false;
        }

        for (final Optimizable optimizable : list) {
            final int axis = optimizable.getOptimizeAxis();
            if (!(axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                    axis == Constants.DESCENDANT_ATTRIBUTE_AXIS || axis == Constants.SELF_AXIS
            )) {
                return false;
            }
        }
        return true;
    }

    private int reverseAxis(final int axis) {
        return switch (axis) {
            case Constants.CHILD_AXIS -> Constants.PARENT_AXIS;
            case Constants.DESCENDANT_AXIS -> Constants.ANCESTOR_AXIS;
            case Constants.DESCENDANT_SELF_AXIS -> Constants.ANCESTOR_SELF_AXIS;
            default -> Constants.UNKNOWN_AXIS;
        };
    }

    private Expression simplifyPath(final Expression expression) {
        if (!(expression instanceof final PathExpr path)) {
            return expression;
        }

        if (path.getLength() != 1) {
            return path;
        }

        return path.getExpression(0);
    }

    /**
     * Try to find an expression object implementing interface Optimizable.
     */
    public static class FindOptimizable extends BasicExpressionVisitor {
        private @Nullable Optimizable[] optimizables = null;

        public @Nullable Optimizable[] getOptimizables() {
            return optimizables;
        }

        @Override
        public void visitPathExpr(final PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                final Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }

        @Override
        public void visitGeneralComparison(final GeneralComparison comparison) {
            addOptimizable(comparison);
        }

        @Override
        public void visitPredicate(final Predicate predicate) {
            predicate.getExpression(0).accept(this);
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            if (function instanceof final Optimizable optimizable) {
                addOptimizable(optimizable);
            }
        }

        private void addOptimizable(final Optimizable optimizable) {
            if (optimizables == null) {
                optimizables = new Optimizable[1];
            } else {
                optimizables = Arrays.copyOf(optimizables, optimizables.length + 1);
            }
            optimizables[optimizables.length - 1] = optimizable;
        }

        /**
         * Reset this visitor for reuse.
         *
         * Clears the known {@link #optimizables}.
         */
        public void reset() {
            this.optimizables = null;
        }
    }

    /**
     * Traverses an expression subtree to check if it could be inlined.
     */
    static class InlineableVisitor extends DefaultExpressionVisitor {

        private boolean inlineable = true;

        public boolean isInlineable() {
            return inlineable;
        }

        @Override
        public void visit(final Expression expr) {
            if (expr instanceof LiteralValue) {
                return;
            }

            if (expr instanceof Atomize ||
                    expr instanceof DynamicCardinalityCheck ||
                    expr instanceof DynamicNameCheck ||
                    expr instanceof DynamicTypeCheck ||
                    expr instanceof UntypedValueCheck ||
                    expr instanceof ConcatExpr ||
                    expr instanceof ArrayConstructor) {
                expr.accept(this);
            } else {
                inlineable = false;
            }
        }

        @Override
        public void visitPathExpr(final PathExpr expr) {
            // continue to check for numeric operators and other simple constructs,
            // abort for all other path expressions with length > 1
            if (expr instanceof OpNumeric ||
                    expr instanceof SequenceConstructor ||
                    expr.getLength() == 1) {
                super.visitPathExpr(expr);
            } else {
                inlineable = false;
            }
        }

        @Override
        public void visitUserFunction(final UserDefinedFunction function) {
            inlineable = false;
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            inlineable = false;
        }

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            inlineable = false;
        }

        @Override
        public void visitForExpression(final ForExpr forExpr) {
            inlineable = false;
        }

        @Override
        public void visitLetExpression(final LetExpr letExpr) {
            inlineable = false;
        }

        @Override
        public void visitOrderByClause(final OrderByClause orderBy) {
            inlineable = false;
        }

        @Override
        public void visitGroupByClause(final GroupByClause groupBy) {
            inlineable = false;
        }

        @Override
        public void visitWhereClause(final WhereClause where) {
            inlineable = false;
        }

        @Override
        public void visitConditional(final ConditionalExpression conditional) {
            inlineable = false;
        }

        @Override
        public void visitLocationStep(final LocationStep locationStep) {
        }

        @Override
        public void visitPredicate(final Predicate predicate) {
            super.visitPredicate(predicate);
        }

        @Override
        public void visitDocumentConstructor(final DocumentConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitElementConstructor(final ElementConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitTextConstructor(final DynamicTextConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(final AttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(final DynamicAttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitUnionExpr(final Union union) {
            inlineable = false;
        }

        @Override
        public void visitIntersectionExpr(final Intersect intersect) {
            inlineable = false;
        }

        @Override
        public void visitVariableDeclaration(final VariableDeclaration decl) {
            inlineable = false;
        }

        @Override
        public void visitTryCatch(final TryCatchExpression tryCatch) {
            inlineable = false;
        }

        @Override
        public void visitCastExpr(final CastExpression expression) {
            inlineable = false;
        }

        @Override
        public void visitGeneralComparison(GeneralComparison comparison) {
            inlineable = false;
        }

        @Override
        public void visitAndExpr(final OpAnd and) {
            inlineable = false;
        }

        @Override
        public void visitOrExpr(final OpOr or) {
            inlineable = false;
        }

        @Override
        public void visitFilteredExpr(final FilteredExpression filtered) {
            inlineable = false;
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            inlineable = false;
        }
    }
}
