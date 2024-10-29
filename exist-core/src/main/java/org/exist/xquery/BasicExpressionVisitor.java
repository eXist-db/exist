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

import java.util.ArrayList;
import java.util.List;

/**
 * Basic implementation of the {@link ExpressionVisitor} interface.
 * This implementation will traverse a PathExpr object if it wraps
 * around a single other expression. All other methods are empty.
 *
 * @author wolf
 */
public class BasicExpressionVisitor implements ExpressionVisitor {

    @Override
    public void visit(final Expression expression) {
        processWrappers(expression);
    }

    @Override
    public void visitCastExpr(final CastExpression expression) {
        //Nothing to do
    }

    /**
     * Default implementation will traverse a PathExpr
     * if it is just a wrapper around another single
     * expression object.
     */
    @Override
    public void visitPathExpr(final PathExpr expression) {
        if (expression.getLength() == 1) {
            final Expression next = expression.getExpression(0);
            next.accept(this);
        }
    }

    @Override
    public void visitFunctionCall(final FunctionCall call) {
        // Nothing to do
    }

    @Override
    public void visitGeneralComparison(final GeneralComparison comparison) {
        //Nothing to do
    }

    @Override
    public void visitUnionExpr(final Union union) {
        //Nothing to do
    }

    @Override
    public void visitIntersectionExpr(final Intersect intersect) {
        //Nothing to do
    }

    @Override
    public void visitAndExpr(final OpAnd and) {
        //Nothing to do
    }

    @Override
    public void visitOrExpr(final OpOr or) {
        //Nothing to do
    }

    @Override
    public void visitLocationStep(final LocationStep locationStep) {
        //Nothing to do
    }

    @Override
    public void visitFilteredExpr(final FilteredExpression filtered) {
        //Nothing to do
    }

    @Override
    public void visitPredicate(final Predicate predicate) {
        //Nothing to do
    }

    @Override
    public void visitVariableReference(final VariableReference ref) {
        //Nothing to do
    }

    @Override
    public void visitVariableDeclaration(final VariableDeclaration decl) {
        // Nothing to do
    }

    protected void processWrappers(final Expression expr) {
        if (expr instanceof Atomize ||
                expr instanceof DynamicCardinalityCheck ||
                expr instanceof DynamicNameCheck ||
                expr instanceof DynamicTypeCheck ||
                expr instanceof UntypedValueCheck ||
                expr instanceof PathExpr) {
            expr.accept(this);
        }
    }

    public static LocationStep findFirstStep(final Expression expr) {
        if (expr instanceof LocationStep) {
            return (LocationStep) expr;
        }
        final FirstStepVisitor visitor = new FirstStepVisitor();
        expr.accept(visitor);
        return visitor.firstStep;
    }

    public static List<LocationStep> findLocationSteps(final Expression expr) {
        final List<LocationStep> steps = new ArrayList<>(5);
        if (expr instanceof LocationStep) {
            steps.add((LocationStep) expr);
            return steps;
        }
        expr.accept(
                new BasicExpressionVisitor() {
                    @Override
                    public void visitPathExpr(final PathExpr expression) {
                        for (int i = 0; i < expression.getLength(); i++) {
                            final Expression next = expression.getExpression(i);
                            next.accept(this);
                            if (steps.size() - 1 != i) {
                                steps.add(null);
                            }
                        }
                    }

                    @Override
                    public void visitLocationStep(final LocationStep locationStep) {
                        steps.add(locationStep);
                    }
                }
        );
        return steps;
    }

    public static VariableReference findVariableRef(final Expression expr) {
        final VariableRefVisitor visitor = new VariableRefVisitor();
        expr.accept(visitor);
        return visitor.ref;
    }

    @Override
    public void visitForExpression(final ForExpr forExpr) {
        //Nothing to do
    }

    @Override
    public void visitLetExpression(final LetExpr letExpr) {
        //Nothing to do
    }

    @Override
    public void visitOrderByClause(final OrderByClause orderBy) {
        // Nothing to do
    }

    @Override
    public void visitCountClause(final CountClause count) {
        // Nothing to do
    }

    @Override
    public void visitGroupByClause(final GroupByClause groupBy) {
        // Nothing to do
    }

    @Override
    public void visitWhereClause(final WhereClause where) {
        // Nothing to do
    }

    @Override
    public void visitBuiltinFunction(final Function function) {
        //Nothing to do
    }

    @Override
    public void visitUserFunction(final UserDefinedFunction function) {
        //Nothing to do
    }

    @Override
    public void visitConditional(final ConditionalExpression conditional) {
        //Nothing to do
    }

    @Override
    public void visitTryCatch(final TryCatchExpression conditional) {
        //Nothing to do
    }

    @Override
    public void visitDocumentConstructor(final DocumentConstructor constructor) {
        // Nothing to do
    }

    public void visitElementConstructor(final ElementConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitTextConstructor(final DynamicTextConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitAttribConstructor(final AttributeConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitAttribConstructor(final DynamicAttributeConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitSimpleMapOperator(final OpSimpleMap simpleMap) {
        // Nothing to do
    }

    @Override
    public void visitWindowExpression(final WindowExpr windowExpr) {
        // Nothing to do
    }

    public static class FirstStepVisitor extends BasicExpressionVisitor {

        private LocationStep firstStep = null;

        public LocationStep getFirstStep() {
            return firstStep;
        }

        @Override
        public void visitLocationStep(final LocationStep locationStep) {
            firstStep = locationStep;
        }
    }

    public static class VariableRefVisitor extends BasicExpressionVisitor {

        private VariableReference ref = null;

        @Override
        public void visitVariableReference(final VariableReference ref) {
            this.ref = ref;
        }

        @Override
        public void visitPathExpr(final PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                final Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }
    }
}
