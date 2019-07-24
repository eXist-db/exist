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

/**
 * Defines a visitor to be used for traversing and analyzing the
 * expression tree.
 * 
 * @author wolf
 *
 */
public interface ExpressionVisitor {

    /**
     * Default fallback method if no other method matches
     * the object's type.
     * 
     * @param expression the expression to visit
     */
    void visit(Expression expression);

    /**
     * Found a PathExpr
     *
     * @param expression the expression found
     */
    void visitPathExpr(PathExpr expression);

    /**
     * Found a LocationStep
     *
     * @param locationStep the expression to visit
     */
    void visitLocationStep(LocationStep locationStep);

    void visitFilteredExpr(FilteredExpression filtered);

    void visitPredicate(Predicate predicate);

    void visitFunctionCall(FunctionCall call);

    void visitGeneralComparison(GeneralComparison comparison);

    void visitCastExpr(CastExpression expression);

    void visitUnionExpr(Union union);

    void visitIntersectionExpr(Intersect intersect);

    void visitAndExpr(OpAnd and);

    void visitOrExpr(OpOr or);

    void visitForExpression(ForExpr forExpr);

    void visitLetExpression(LetExpr letExpr);

    void visitOrderByClause(OrderByClause orderBy);

    void visitGroupByClause(GroupByClause groupBy);

    void visitWhereClause(WhereClause where);

    void visitBuiltinFunction(Function function);

    void visitUserFunction(UserDefinedFunction function);

    void visitConditional(ConditionalExpression conditional);

    void visitTryCatch(TryCatchExpression tryCatch);

    void visitDocumentConstructor(DocumentConstructor constructor);

    void visitElementConstructor(ElementConstructor constructor);

    void visitTextConstructor(DynamicTextConstructor constructor);

    void visitAttribConstructor(AttributeConstructor constructor);

    void visitAttribConstructor(DynamicAttributeConstructor constructor);

    void visitVariableReference(VariableReference ref);

    void visitVariableDeclaration(VariableDeclaration decl);

    void visitSimpleMapOperator(OpSimpleMap simpleMap);
}
