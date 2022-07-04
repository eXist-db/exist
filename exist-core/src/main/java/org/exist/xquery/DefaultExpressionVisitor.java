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

import javax.annotation.Nullable;
import java.util.Iterator;

/**
 * An {@link org.exist.xquery.ExpressionVisitor} which traverses the entire
 * expression tree. Methods may be overwritten by subclasses to filter out the
 * events they need.
 */
public class DefaultExpressionVisitor extends BasicExpressionVisitor {

    public void visitPathExpr(PathExpr expression) {
        for (int i = 0; i < expression.getLength(); i++) {
            final Expression next = expression.getExpression(i);
            next.accept(this);
        }
    }

    public void visitUserFunction(UserDefinedFunction function) {
        function.getFunctionBody().accept(this);
    }

    public void visitBuiltinFunction(Function function) {
        for (int i = 0; i < function.getArgumentCount(); i++) {
            final Expression arg = function.getArgument(i);
            arg.accept(this);
        }
    }

    @Override
    public void visitFunctionCall(FunctionCall call) {
        // forward to the called function
        for(int i = 0; i < call.getArgumentCount(); i++) {
            call.getArgument(i).accept(this);
        }
        call.getFunction().accept(this);
    }

    public void visitForExpression(ForExpr forExpr) {
        forExpr.getInputSequence().accept(this);
        forExpr.getReturnExpression().accept(this);
    }

    public void visitLetExpression(LetExpr letExpr) {
        letExpr.getInputSequence().accept(this);
        letExpr.getReturnExpression().accept(this);
    }

    public void visitWindowExpression(final WindowExpr windowExpr) {
        windowExpr.getInputSequence().accept(this);
        windowExpr.getReturnExpression().accept(this);
    }

    @Override
    public void visitOrderByClause(OrderByClause orderBy) {
        for (OrderSpec spec: orderBy.getOrderSpecs()) {
            spec.getSortExpression().accept(this);
        }
        orderBy.getReturnExpression().accept(this);
    }

    @Override
    public void visitGroupByClause(GroupByClause groupBy) {
        for (GroupSpec spec: groupBy.getGroupSpecs()) {
            spec.getGroupExpression().accept(this);
        }
        groupBy.getReturnExpression().accept(this);
    }

    @Override
    public void visitWhereClause(WhereClause where) {
        where.getWhereExpr().accept(this);
        where.getReturnExpression().accept(this);
    }

    public void visitConditional(ConditionalExpression conditional) {
        conditional.getTestExpr().accept(this);
        conditional.getThenExpr().accept(this);
        conditional.getElseExpr().accept(this);
    }

    @Override
    public void visitLocationStep(final LocationStep locationStep) {
        @Nullable final Predicate[] predicates = locationStep.getPredicates();
        if (predicates != null) {
            for (final Predicate pred : predicates) {
                pred.accept(this);
            }
        }
    }

    public void visitPredicate(Predicate predicate) {
        predicate.getExpression(0).accept(this);
    }

    public void visitDocumentConstructor(DocumentConstructor constructor) {
    	constructor.getContent().accept(this);
    }
    
    public void visitElementConstructor(ElementConstructor constructor) {
        constructor.getNameExpr().accept(this);
        if (constructor.getAttributes() != null) {
            for (AttributeConstructor attrConstr: constructor.getAttributes()) {
                attrConstr.accept(this);
            }
        }
        if (constructor.getContent() != null)
            {constructor.getContent().accept(this);}
    }

    public void visitTextConstructor(DynamicTextConstructor constructor) {
        constructor.getContent().accept(this);
    }

    public void visitAttribConstructor(AttributeConstructor constructor) {
        for (final Iterator<Object> i = constructor.contentIterator(); i.hasNext(); ) {
            final Object next = i.next();
            if (next instanceof Expression)
                {((Expression)next).accept(this);}
        }
    }

    public void visitAttribConstructor(DynamicAttributeConstructor constructor) {
        constructor.getNameExpr().accept(this);
        if (constructor.getContentExpr() != null)
            {constructor.getContentExpr().accept(this);}
    }

    public void visitUnionExpr(Union union) {
        union.left.accept(this);
        union.right.accept(this);
    }

    public void visitIntersectionExpr(Intersect intersect) {
        intersect.left.accept(this);
        intersect.right.accept(this);
    }
    
    @Override
    public void visitVariableDeclaration(VariableDeclaration decl) {
        decl.getExpression().ifPresent(e -> e.accept(this));
    }

    @Override
    public void visitTryCatch(TryCatchExpression tryCatch) {
        tryCatch.getTryTargetExpr().accept(this);
        for (TryCatchExpression.CatchClause clause : tryCatch.getCatchClauses()) {
            clause.getCatchExpr().accept(this);
        }
    }

    @Override
    public void visitSimpleMapOperator(OpSimpleMap simpleMap) {
        simpleMap.getLeft().accept(this);
        simpleMap.getRight().accept(this);
    }
}
