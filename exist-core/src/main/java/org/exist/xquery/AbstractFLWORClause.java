/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for clauses in a FLWOR expressions, for/let/group by ...
 */
public abstract class AbstractFLWORClause extends AbstractExpression implements FLWORClause {

    protected LocalVariable firstVariable = null;
    private FLWORClause previousClause  = null;
    protected Expression returnExpr;
    private int actualReturnType = Type.ITEM;

    public AbstractFLWORClause(XQueryContext context) {
        super(context);
    }

    @Override
    public LocalVariable createVariable(final QName name) throws XPathException {
        final LocalVariable variable = new LocalVariable(name);
        firstVariable = variable;
        return variable;
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
        firstVariable = null;
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
        return firstVariable;
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
        firstVariable = null;
    }

    @Override
    public int getDependencies() {
        return returnExpr.getDependencies();
    }
}