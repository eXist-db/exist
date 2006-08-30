package org.exist.xquery;

/**
 * Basic implementation of the {@link ExpressionVisitor} interface.
 * This implementation will traverse a PathExpr object if it wraps
 * around a single other expression. All other methods are empty.
 * 
 * @author wolf
 *
 */
public class BasicExpressionVisitor implements ExpressionVisitor {

	public void visit(Expression expression) {
		processWrappers(expression);
	}

	public void visitCastExpr(CastExpression expression) {
	}

	/**
	 * Default implementation will traverse a PathExpr
	 * if it is just a wrapper around another single
	 * expression object.
	 */
	public void visitPathExpr(PathExpr expression) {
		if (expression.getLength() == 1) {
			Expression next = expression.getExpression(0);
			next.accept(this);
		}
	}
	
	protected void processWrappers(Expression expr) {
		if (expr instanceof Atomize ||
				expr instanceof DynamicCardinalityCheck ||
				expr instanceof DynamicNameCheck ||
				expr instanceof DynamicTypeCheck ||
				expr instanceof UntypedValueCheck)
			expr.accept(this);
	}
}
