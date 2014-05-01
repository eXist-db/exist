/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;
import java.util.List;

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
        final Expression where = forExpr.getWhereExpression();
        if (where != null) {
            where.accept(this);
        }
        for (OrderSpec orderSpec: forExpr.getOrderSpecs()) {
            orderSpec.getSortExpression().accept(this);
        }
        for (GroupSpec groupSpec: forExpr.getGroupSpecs()) {
            groupSpec.getGroupExpression().accept(this);
        }
        forExpr.getReturnExpression().accept(this);
    }

    public void visitLetExpression(LetExpr letExpr) {
        letExpr.getInputSequence().accept(this);
        final Expression where = letExpr.getWhereExpression();
        if (where != null) {
            where.accept(this);
        }
        for (OrderSpec orderSpec: letExpr.getOrderSpecs()) {
            orderSpec.getSortExpression().accept(this);
        }
        for (GroupSpec groupSpec: letExpr.getGroupSpecs()) {
            groupSpec.getGroupExpression().accept(this);
        }
        letExpr.getReturnExpression().accept(this);
    }

    public void visitConditional(ConditionalExpression conditional) {
        conditional.getTestExpr().accept(this);
        conditional.getThenExpr().accept(this);
        conditional.getElseExpr().accept(this);
    }

    public void visitLocationStep(LocationStep locationStep) {
        final List<Predicate> predicates = locationStep.getPredicates();
        for (final Predicate pred : predicates) {
			pred.accept(this);
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

    public void visitIntersectionExpr(Intersection intersect) {
        intersect.left.accept(this);
        intersect.right.accept(this);
    }
    
    @Override
    public void visitVariableDeclaration(VariableDeclaration decl) {
    	decl.getExpression().accept(this);
    }

    @Override
    public void visitTryCatch(TryCatchExpression tryCatch) {
        tryCatch.getTryTargetExpr().accept(this);
        for (TryCatchExpression.CatchClause clause : tryCatch.getCatchClauses()) {
            clause.getCatchExpr().accept(this);
        }
    }
}
