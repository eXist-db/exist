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
     * @param expression
     */
    public void visit(Expression expression);

    /** Found a PathExpr */
    public void visitPathExpr(PathExpr expression);

    /** Found a LocationStep */
    public void visitLocationStep(LocationStep locationStep);

    public void visitFilteredExpr(FilteredExpression filtered);

    public void visitPredicate(Predicate predicate);

    public void visitFunctionCall(FunctionCall call);

    public void visitGeneralComparison(GeneralComparison comparison);

    public void visitCastExpr(CastExpression expression);

    public void visitUnionExpr(Union union);

    public void visitIntersectionExpr(Intersect intersect);

    public void visitAndExpr(OpAnd and);

    public void visitOrExpr(OpOr or);

    public void visitForExpression(ForExpr forExpr);

    public void visitLetExpression(LetExpr letExpr);

    public void visitBuiltinFunction(Function function);

    public void visitUserFunction(UserDefinedFunction function);

    public void visitConditional(ConditionalExpression conditional);

    public void visitTryCatch(TryCatchExpression tryCatch);

    public void visitDocumentConstructor(DocumentConstructor constructor);

    public void visitElementConstructor(ElementConstructor constructor);

    public void visitTextConstructor(DynamicTextConstructor constructor);

    public void visitAttribConstructor(AttributeConstructor constructor);

    public void visitAttribConstructor(DynamicAttributeConstructor constructor);

    public void visitVariableReference(VariableReference ref);

    public void visitVariableDeclaration(VariableDeclaration decl);
}
