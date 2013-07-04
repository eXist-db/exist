package org.exist.xquery;

import org.exist.xquery.functions.fn.ExtFulltext;

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

    @Override
    public void visit(Expression expression) {
        processWrappers(expression);
    }

    @Override
    public void visitCastExpr(CastExpression expression) {
        //Nothing to do
    }

    @Override
    public void visitFtExpression(ExtFulltext fulltext) {
        //Nothing to do
    }

    /**
     * Default implementation will traverse a PathExpr
     * if it is just a wrapper around another single
     * expression object.
     */
    @Override
    public void visitPathExpr(PathExpr expression) {
        if (expression.getLength() == 1) {
            final Expression next = expression.getExpression(0);
            next.accept(this);
        }
    }

    @Override
    public void visitFunctionCall(FunctionCall call) {
        // Nothing to do
    }

    @Override
    public void visitGeneralComparison(GeneralComparison comparison) {
        //Nothing to do
    }

    @Override
    public void visitUnionExpr(Union union) {
        //Nothing to do
    }

    @Override
    public void visitIntersectionExpr(Intersection intersect) {
        //Nothing to do
    }

    @Override
    public void visitAndExpr(OpAnd and) {
        //Nothing to do
    }

    @Override
    public void visitOrExpr(OpOr or) {
        //Nothing to do
    }

    @Override
    public void visitLocationStep(LocationStep locationStep) {
        //Nothing to do
    }

    @Override
    public void visitFilteredExpr(FilteredExpression filtered) {
        //Nothing to do
    }

    @Override
    public void visitPredicate(Predicate predicate) {
        //Nothing to do
    }

    @Override
    public void visitVariableReference(VariableReference ref) {
        //Nothing to do
    }

    @Override
    public void visitVariableDeclaration(VariableDeclaration decl) {
    	// Nothing to do
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
            {return (LocationStep) expr;}
        final FirstStepVisitor visitor = new FirstStepVisitor();
        expr.accept(visitor);
        return visitor.firstStep;
    }

    public static List<LocationStep> findLocationSteps(Expression expr) {
        final List<LocationStep> steps = new ArrayList<LocationStep>(5);
        if (expr instanceof LocationStep) {
            steps.add((LocationStep)expr);
            return steps;
        }
        expr.accept(
            new BasicExpressionVisitor() {
                @Override
                public void visitPathExpr(PathExpr expression) {
                    for (int i = 0; i < expression.getLength(); i++) {
                        final Expression next = expression.getExpression(i);
                        next.accept(this);
                        if (steps.size() - 1 != i) {
                        	steps.add(null);
                        }
                    }
                }
                @Override
                public void visitLocationStep(LocationStep locationStep) {
                    steps.add(locationStep);
                }
            }
        );
        return steps;
    }

    public static VariableReference findVariableRef(Expression expr) {
        final VariableRefVisitor visitor = new VariableRefVisitor();
        expr.accept(visitor);
        return visitor.ref;
    }

    @Override
    public void visitForExpression(ForExpr forExpr) {
        //Nothing to do
    }

    @Override
    public void visitLetExpression(LetExpr letExpr) {
        //Nothing to do
    }

    @Override
    public void visitBuiltinFunction(Function function) {
        //Nothing to do
    }

    @Override
    public void visitUserFunction(UserDefinedFunction function) {
        //Nothing to do
    }

    @Override
    public void visitConditional(ConditionalExpression conditional) {
        //Nothing to do
    }

    @Override
    public void visitTryCatch(TryCatchExpression conditional) {
        //Nothing to do
    }

    @Override
    public void visitDocumentConstructor(DocumentConstructor constructor) {
    	// Nothing to do
    }
    
    public void visitElementConstructor(ElementConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitTextConstructor(DynamicTextConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitAttribConstructor(AttributeConstructor constructor) {
        //Nothing to do
    }

    @Override
    public void visitAttribConstructor(DynamicAttributeConstructor constructor) {
        //Nothing to do
    }

    public static class FirstStepVisitor extends BasicExpressionVisitor {

        private LocationStep firstStep = null;

        public LocationStep getFirstStep() {
            return firstStep;
        }

        @Override
        public void visitLocationStep(LocationStep locationStep) {
            firstStep = locationStep;
        }
    }

    public static class VariableRefVisitor extends BasicExpressionVisitor {

        private VariableReference ref = null;

        @Override
        public void visitVariableReference(VariableReference ref) {
            this.ref = ref;
        }

        @Override
        public void visitPathExpr(PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                final Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }
    }
}
