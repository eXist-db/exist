package org.exist.xquery;

import org.exist.dom.QName;

/**
 * Abstract base class for clauses in a FLWOR expressions, for/let/group by ...
 */
public abstract class AbstractFLWORClause extends AbstractExpression implements FLWORClause {

    protected LocalVariable firstVar = null;
    private FLWORClause previousClause  = null;
    protected Expression returnExpr;

    public AbstractFLWORClause(XQueryContext context) {
        super(context);
    }

    public LocalVariable createVariable(String name) throws XPathException {
        final LocalVariable var = new LocalVariable(QName.parse(context, name, null));
        if (firstVar == null) {
            firstVar = var;
        }
        return var;
    }

    @Override
    public void setReturnExpression(Expression expr) {
        this.returnExpr = expr;
    }

    @Override
    public Expression getReturnExpression() {
        return returnExpr;
    }

    @Override
    public LocalVariable getStartVariable() {
        return firstVar;
    }

    @Override
    public void setPreviousClause(FLWORClause clause) {
        previousClause = clause;
    }

    @Override
    public FLWORClause getPreviousClause() {
        return previousClause;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        firstVar = null;
    }
}