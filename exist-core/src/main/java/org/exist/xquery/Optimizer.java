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
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.functions.array.ArrayConstructor;
import org.exist.xquery.pragmas.Optimize;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;

import java.util.*;

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

    private XQueryContext context;

    private int predicates = 0;

    private boolean hasOptimized = false;

    private List<QueryRewriter> rewriters = new ArrayList<QueryRewriter>(5);

    public Optimizer(XQueryContext context) {
        this.context = context;
        this.rewriters = context.getBroker().getIndexController().getQueryRewriters(context);
    }

    public boolean hasOptimized() {
        return hasOptimized;
    }

    public void visitLocationStep(LocationStep locationStep) {
        super.visitLocationStep(locationStep);
        // check query rewriters if they want to rewrite the location step
        Pragma optimizePragma = null;
        for (QueryRewriter rewriter : rewriters) {
            try {
                optimizePragma = rewriter.rewriteLocationStep(locationStep);
                if (optimizePragma != null) {
                    // expression was rewritten: return
                    hasOptimized = true;
                    break;
                }
            } catch (XPathException e) {
                LOG.warn("Exception called while rewriting location step: " + e.getMessage(), e);
            }
        }

        boolean optimize = false;
        // only location steps with predicates can be optimized:
        if (locationStep.hasPredicates()) {
            final List<Predicate> preds = locationStep.getPredicates();
            // walk through the predicates attached to the current location step.
            // try to find a predicate containing an expression which is an instance
            // of Optimizable.
            for (final Predicate pred : preds) {
                final FindOptimizable find = new FindOptimizable();
                pred.accept(find);
                final List<Optimizable> list = find.getOptimizables();
                if (list.size() > 0 && canOptimize(list)) {
                    optimize = true;
                    break;
                }
            }
        }

        final Expression parent = locationStep.getParentExpression();

        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            if (!(parent instanceof RewritableExpression)) {
            	if (LOG.isTraceEnabled())
            		{LOG.trace("Parent expression of step is not a PathExpr: " + parent);}
                return;
            }
            hasOptimized = true;
            final RewritableExpression path = (RewritableExpression) parent;
            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                if (optimizePragma != null) {
                    extension.addPragma(optimizePragma);
                }
                extension.addPragma(new Optimize(context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(locationStep);
                
                // Replace the old expression with the pragma
                path.replace(locationStep, extension);

                if (LOG.isTraceEnabled())
                    {LOG.trace("Rewritten expression: " + ExpressionDumper.dump(parent));}
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: " + locationStep + ": " + e.getMessage(), e);
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

    public void visitFilteredExpr(FilteredExpression filtered) {
        super.visitFilteredExpr(filtered);

        // check if filtered expression can be simplified:
        // handles expressions like //foo/(baz)[...]
        if (filtered.getExpression() instanceof LocationStep) {
            // single location step: simplify by directly attaching it to the parent path expression
            final LocationStep step = (LocationStep) filtered.getExpression();
            final Expression parent = filtered.getParent();
            if (parent instanceof RewritableExpression) {
                final List<Predicate> preds = filtered.getPredicates();
                final boolean optimizable = hasOptimizable(preds);
                if (optimizable) {
                    // copy predicates
                    for (Predicate pred : preds) {
                        step.addPredicate(pred);
                    }
                    ((RewritableExpression) parent).replace(filtered, step);
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
            if (!(parent instanceof RewritableExpression)) {
            	if (LOG.isTraceEnabled())
            		{LOG.trace("Parent expression: " + parent.getClass().getName() + " of step does not implement RewritableExpression");}
                return;
            }
            if (LOG.isTraceEnabled())
                {LOG.trace("Rewriting expression: " + ExpressionDumper.dump(filtered));}
            hasOptimized = true;
            final RewritableExpression path = (RewritableExpression) parent;
            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                extension.addPragma(new Optimize(context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(filtered);
                // Replace the old expression with the pragma
                path.replace(filtered, extension);
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: " + filtered + ": " + e.getMessage(), e);
            }
        }
    }

    private boolean hasOptimizable(List<Predicate> preds) {
        // walk through the predicates attached to the current location step.
        // try to find a predicate containing an expression which is an instance
        // of Optimizable.
        for (final Predicate pred : preds) {
            final FindOptimizable find = new FindOptimizable();
            pred.accept(find);
            final List<Optimizable> list = find.getOptimizables();
            if (list.size() > 0 && canOptimize(list)) {
                return true;
            }
        }
        return false;
    }

    public void visitAndExpr(OpAnd and) {
        if (predicates > 0) {
            // inside a filter expression, we can often replace a logical and with
            // a chain of filters, which can then be further optimized
            Expression parent = and.getParent();
            if (!(parent instanceof PathExpr)) {
            	if (LOG.isTraceEnabled())
            		{LOG.trace("Parent expression of boolean operator is not a PathExpr: " + parent);}
                return;
            }
            PathExpr path;
            Predicate predicate;
            if (parent instanceof Predicate) {
                predicate = (Predicate) parent;
                path = predicate;
            } else {
                path = (PathExpr) parent;
                parent = path.getParent();
                if (!(parent instanceof Predicate) || path.getLength() > 1) {
                    LOG.debug("Boolean operator is not a top-level expression in the predicate: " + (parent == null ? "?" : parent.getClass().getName()));
                    return;
                }
                predicate = (Predicate) parent;
            }
            if (LOG.isTraceEnabled())
                {LOG.trace("Rewriting boolean expression: " + ExpressionDumper.dump(and));}
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

	public void visitOrExpr(OpOr or) {
    	if (or.isRewritable()) {
        	or.getLeft().accept(this);
			or.getRight().accept(this);
        }
	}

    @Override
    public void visitGeneralComparison(GeneralComparison comparison) {
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

    public void visitPredicate(Predicate predicate) {
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
    public void visitVariableReference(VariableReference ref) {
        final String ns = ref.getName().getNamespaceURI();
        if (ns != null && ns.length() > 0) {
            final Module module = context.getModule(ns);
            if (module != null && !module.isInternalModule()) {
                final Collection<VariableDeclaration> vars = ((ExternalModule) module).getVariableDeclarations();
                for (VariableDeclaration var: vars) {
                    if (var.getName().equals(ref.getName()) && var.getExpression().isPresent()) {
                        var.getExpression().get().accept(this);
                        final Expression expression = simplifyPath(var.getExpression().get());
                        final InlineableVisitor visitor = new InlineableVisitor();
                        expression.accept(visitor);
                        if (visitor.isInlineable()) {
                            final Expression parent = ref.getParent();
                            if (parent instanceof RewritableExpression) {
//                                System.out.println(ref.getSource().toString() + " line " + ref.getLine() + ": " +
//                                        "inlining " +
//                                        "variable "+ ref.getName());
                                ((RewritableExpression) parent).replace(ref, expression);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean canOptimize(List<Optimizable> list) {
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

    private int reverseAxis(int axis) {
    	switch (axis) {
    	case Constants.CHILD_AXIS:
    		return Constants.PARENT_AXIS;
    	case Constants.DESCENDANT_AXIS:
    		return Constants.ANCESTOR_AXIS;
    	case Constants.DESCENDANT_SELF_AXIS:
    		return Constants.ANCESTOR_SELF_AXIS;
    	}
    	return Constants.UNKNOWN_AXIS;
    }

    private Expression simplifyPath(Expression expression) {
        if (!(expression instanceof PathExpr)) {
            return expression;
        }
        final PathExpr path = (PathExpr) expression;
        if (path.getLength() != 1) {
            return path;
        }
        return path.getExpression(0);
    }

    /**
     * Try to find an expression object implementing interface Optimizable.
     */
    public static class FindOptimizable extends BasicExpressionVisitor {

        List<Optimizable> optimizables = new ArrayList<Optimizable>();

        public List<Optimizable> getOptimizables() {
            return optimizables;
        }

        public void visitPathExpr(PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                final Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }

        public void visitGeneralComparison(GeneralComparison comparison) {
            optimizables.add(comparison);
        }

        public void visitPredicate(Predicate predicate) {
            predicate.getExpression(0).accept(this);
        }

        public void visitBuiltinFunction(Function function) {
            if (function instanceof Optimizable) {
                optimizables.add((Optimizable) function);
            }
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
        public void visit(Expression expr) {
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
        public void visitPathExpr(PathExpr expr) {
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
        public void visitUserFunction(UserDefinedFunction function) {
            inlineable = false;
        }

        @Override
        public void visitBuiltinFunction(Function function) {
            inlineable = false;
        }

        @Override
        public void visitFunctionCall(FunctionCall call) {
            inlineable = false;
        }

        @Override
        public void visitForExpression(ForExpr forExpr) {
            inlineable = false;
        }

        @Override
        public void visitLetExpression(LetExpr letExpr) {
            inlineable = false;
        }

        @Override
        public void visitOrderByClause(OrderByClause orderBy) {
            inlineable = false;
        }

        @Override
        public void visitGroupByClause(GroupByClause groupBy) {
            inlineable = false;
        }

        @Override
        public void visitWhereClause(WhereClause where) {
            inlineable = false;
        }

        @Override
        public void visitConditional(ConditionalExpression conditional) {
            inlineable = false;
        }

        @Override
        public void visitLocationStep(LocationStep locationStep) {
        }

        @Override
        public void visitPredicate(Predicate predicate) {
            super.visitPredicate(predicate);
        }

        @Override
        public void visitDocumentConstructor(DocumentConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitElementConstructor(ElementConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitTextConstructor(DynamicTextConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(AttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(DynamicAttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitUnionExpr(Union union) {
            inlineable = false;
        }

        @Override
        public void visitIntersectionExpr(Intersect intersect) {
            inlineable = false;
        }

        @Override
        public void visitVariableDeclaration(VariableDeclaration decl) {
            inlineable = false;
        }

        @Override
        public void visitTryCatch(TryCatchExpression tryCatch) {
            inlineable = false;
        }

        @Override
        public void visitCastExpr(CastExpression expression) {
            inlineable = false;
        }

        @Override
        public void visitGeneralComparison(GeneralComparison comparison) {
            inlineable = false;
        }

        @Override
        public void visitAndExpr(OpAnd and) {
            inlineable = false;
        }

        @Override
        public void visitOrExpr(OpOr or) {
            inlineable = false;
        }

        @Override
        public void visitFilteredExpr(FilteredExpression filtered) {
            inlineable = false;
        }

        @Override
        public void visitVariableReference(VariableReference ref) {
            inlineable = false;
        }
    }
}
