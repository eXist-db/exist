package org.exist.xquery;

import org.exist.xquery.functions.ExtFulltext;

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

    public void visitFtExpression(ExtFulltext fulltext) {
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

    public void visitGeneralComparison(GeneralComparison comparison) {
    }
    
    public void visitUnionExpr(Union union) {
    }

    public void visitLocationStep(LocationStep locationStep) {
    }

    public void visitPredicate(Predicate predicate) {
    }

    protected void processWrappers(Expression expr) {
		if (expr instanceof Atomize ||
				expr instanceof DynamicCardinalityCheck ||
				expr instanceof DynamicNameCheck ||
				expr instanceof DynamicTypeCheck ||
				expr instanceof UntypedValueCheck) {
			expr.accept(this);
        }
    }

    public final static LocationStep findFirstStep(Expression expr) {
        if (expr instanceof LocationStep)
            return (LocationStep) expr;
        FirstStepVisitor visitor = new FirstStepVisitor();
        expr.accept(visitor);
        return visitor.firstStep;
    }


    public void visitForExpression(ForExpr forExpr) {
    }

    public void visitLetExpression(LetExpr letExpr) {
    }

    public void visitFunction(Function function) {
    }

    public static class FirstStepVisitor extends BasicExpressionVisitor {

        private LocationStep firstStep = null;

        public LocationStep getFirstStep() {
            return firstStep;
        }

        public void visitLocationStep(LocationStep locationStep) {
            firstStep = locationStep;
        }
    }
}
