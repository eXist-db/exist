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
import java.util.Stack;

/**
 * An {@link org.exist.xquery.ExpressionVisitor} which traverses the entire
 * expression tree. Methods may be overwritten by subclasses to filter out the
 * events they need.
 */
public class DefaultExpressionVisitor extends BasicExpressionVisitor {

    private Stack functionStack = new Stack();

    public void visitPathExpr(PathExpr expression) {
        for (int i = 0; i < expression.getLength(); i++) {
            Expression next = expression.getExpression(i);
            next.accept(this);
        }
    }

    public void visitUserFunction(UserDefinedFunction function) {
        if (functionStack.contains(function))
            return;
        functionStack.push(function);
        function.getFunctionBody().accept(this);
        functionStack.pop();
    }

    public void visitBuiltinFunction(Function function) {
        for (int i = 0; i < function.getArgumentCount(); i++) {
            Expression arg = function.getArgument(i);
            arg.accept(this);
        }
    }

    public void visitForExpression(ForExpr forExpr) {
        forExpr.getInputSequence().accept(this);
        Expression where = forExpr.getWhereExpression();
        if (where != null)
            where.accept(this);
        forExpr.getReturnExpression().accept(this);
    }

    public void visitLetExpression(LetExpr letExpr) {
        letExpr.getInputSequence().accept(this);
        Expression where = letExpr.getWhereExpression();
        if (where != null)
            where.accept(this);
        letExpr.getReturnExpression().accept(this);
    }

    public void visitConditional(ConditionalExpression conditional) {
        conditional.getTestExpr().accept(this);
        conditional.getThenExpr().accept(this);
        conditional.getElseExpr().accept(this);
    }


    public void visitLocationStep(LocationStep locationStep) {
        List predicates = locationStep.getPredicates();
        for (int i = 0; i < predicates.size(); i++) {
            Predicate pred = (Predicate) predicates.get(i);
			pred.accept(this);
        }
    }

    public void visitPredicate(Predicate predicate) {
        predicate.getExpression(0).accept(this);
    }

    public void visitElementConstructor(ElementConstructor constructor) {
        constructor.getNameExpr().accept(this);
        if (constructor.getContent() != null)
            constructor.getContent().accept(this);
    }

    public void visitTextConstructor(DynamicTextConstructor constructor) {
        constructor.getContent().accept(this);
    }

    public void visitAttribConstructor(AttributeConstructor constructor) {
        for (Iterator i = constructor.contentIterator(); i.hasNext(); ) {
            Object next = i.next();
            if (next instanceof Expression)
                ((Expression)next).accept(this);
        }
    }

    public void visitAttribConstructor(DynamicAttributeConstructor constructor) {
        constructor.getNameExpr().accept(this);
        if (constructor.getContentExpr() != null)
            constructor.getContentExpr().accept(this);
    }
}
