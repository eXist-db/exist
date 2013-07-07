package org.exist.xquery;

import org.exist.xquery.functions.fn.ExtFulltext;

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

    public void visitIntersectionExpr(Intersection intersect);

    public void visitAndExpr(OpAnd and);

    public void visitOrExpr(OpOr or);

    public void visitFtExpression(ExtFulltext fulltext);

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
