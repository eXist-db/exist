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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Check a function parameter type at runtime.
 *
 * @author wolf
 */
public class FunctionTypeCheck extends AbstractExpression {

    final private Expression expression;
    final private FunctionParameterFunctionSequenceType requiredType;

    public FunctionTypeCheck(XQueryContext context, final FunctionParameterFunctionSequenceType requiredType, Expression expr) {
        super(context);
        this.requiredType = requiredType;
        this.expression = expr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval (Sequence contextSequence, Item contextItem) throws XPathException {
        final Sequence seq = expression.eval(contextSequence, contextItem);
        return (requiredType.checkType(seq) ? seq : null);
    }

//    private void check(Sequence result, Item item) throws XPathException {
//        int type = item.getType();
//        if (!(Type.subTypeOf(type, requiredType.getPrimaryType()))) {
//            throw new XPathException(expression, ErrorCodes.XPTY0004,
//                    Type.getTypeName(item.getType()) + "(" + item.getStringValue() +
//                                    ") is not a sub-type of " + Type.getTypeName(requiredType.getPrimaryType()));
//
//        }
//        else {
//            throw new XPathException(expression, ErrorCodes.FOCH0002, "Required type is " +
//                        Type.getTypeName(requiredType) + " but got '" + Type.getTypeName(item.getType()) + "(" +
//                        item.getStringValue() + ")'");}
//        }
//        if (result != null) { result.add(item); }
//    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if (dumper.verbosity() > 1) {
            dumper.display("dynamic-function-type-check");
            dumper.display("[");
            dumper.display(Type.getTypeName(requiredType.getPrimaryType()));
            dumper.display(", ");
        }
        expression.dump(dumper);
        if (dumper.verbosity() > 1) { dumper.display("]"); }
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        return requiredType.getPrimaryType();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        return expression.getDependencies();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }

    public int getLine() {
        return expression.getLine();
    }

    public int getColumn() {
        return expression.getColumn();
    }

    public void accept(ExpressionVisitor visitor) {
        expression.accept(visitor);
    }

    public int getSubExpressionCount() {
        return 1;
    }

    public Expression getSubExpression(int index) {
        if (index == 0) {return expression;}

        throw new IndexOutOfBoundsException("Index: "+index+", Size: "+getSubExpressionCount());
    }

}