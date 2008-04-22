package org.exist.xquery;

import org.exist.xquery.functions.ExtFulltext;

import java.util.ArrayList;
import java.util.List;

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


    public void visitAndExpr(OpAnd and) {
    }

    public void visitOrExpr(OpOr or) {
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

    public static LocationStep findFirstStep(Expression expr) {
        if (expr instanceof LocationStep)
            return (LocationStep) expr;
        FirstStepVisitor visitor = new FirstStepVisitor();
        expr.accept(visitor);
        return visitor.firstStep;
    }

    public static List findLocationSteps(Expression expr) {
        final List steps = new ArrayList(5);
        if (expr instanceof LocationStep) {
            steps.add(expr);
            return steps;
        }
        expr.accept(new BasicExpressionVisitor() {
            
            public void visitPathExpr(PathExpr expression) {
                for (int i = 0; i < expression.getLength(); i++) {
                    Expression next = expression.getExpression(i);
                    next.accept(this);
                }
            }

            public void visitLocationStep(LocationStep locationStep) {
                steps.add(locationStep);
            }
        });
        return steps;
    }

    public void visitForExpression(ForExpr forExpr) {
    }

    public void visitLetExpression(LetExpr letExpr) {
    }

    public void visitBuiltinFunction(Function function) {
    }

    public void visitUserFunction(UserDefinedFunction function) {
    }

    public void visitConditional(ConditionalExpression conditional) {
    }

    public void visitElementConstructor(ElementConstructor constructor) {
    }

    public void visitTextConstructor(DynamicTextConstructor constructor) {
    }

    public void visitAttribConstructor(AttributeConstructor constructor) {
    }

    public void visitAttribConstructor(DynamicAttributeConstructor constructor) {
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
