package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for clauses in a FLWOR expressions, for/let/group by ...
 */
public abstract class AbstractFLWORClause extends AbstractExpression implements FLWORClause {

    protected LocalVariable firstVar = null;
    private FLWORClause previousClause  = null;
    protected Expression returnExpr;
    private int actualReturnType = Type.ITEM;

    public AbstractFLWORClause(XQueryContext context) {
        super(context);
    }

    @Override
    public LocalVariable createVariable(final String name) throws XPathException {
        try {
            final LocalVariable var = new LocalVariable(QName.parse(context, name, null));
            firstVar = var;
            return var;
        } catch (final IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + name);
        }
    }

    @Override
    public Sequence preEval(Sequence seq) throws XPathException {
        if (returnExpr instanceof FLWORClause) {
            return ((FLWORClause)returnExpr).preEval(seq);
        }
        // just return the input sequence by default
        return seq;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        // reset variable after evaluation has completed
        firstVar = null;
        return seq;
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

    protected void setActualReturnType(int type) {
        this.actualReturnType = type;
    }

    @Override
    public int returnsType() {
        //Type.ITEM by default : this may change *after* evaluation
        return actualReturnType;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        firstVar = null;
    }

    @Override
    public int getDependencies() {
        return returnExpr.getDependencies();
    }
}