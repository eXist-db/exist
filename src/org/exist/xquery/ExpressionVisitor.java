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
	
	/** Found a CastExpression */
	public void visitCastExpr(CastExpression expression);
}
