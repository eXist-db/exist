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

/**
 * Base class for the runtime function-argument check wrappers (
 * {@link DynamicCardinalityCheck}, {@link DynamicNameCheck},
 * {@link DynamicTypeCheck}, {@link FunctionTypeCheck},
 * {@link UntypedValueCheck}) that hold exactly one wrapped expression and
 * implement {@link RewritableExpression} so the optimizer can rewrite that
 * wrapped expression in place -- e.g. to attach an {@code (#exist:optimize#)}
 * pragma to a function argument -- without dropping the check. See GH-873.
 */
public abstract class AbstractRewritableExpression extends AbstractExpression implements RewritableExpression {

    protected Expression expression;

    protected AbstractRewritableExpression(final XQueryContext context, final Expression expression) {
        super(context);
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public int getSubExpressionCount() {
        return 1;
    }

    @Override
    public Expression getSubExpression(final int index) {
        if (index == 0) {
            return expression;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + getSubExpressionCount());
    }

    @Override
    public void replace(final Expression oldExpr, final Expression newExpr) {
        if (expression == oldExpr) {
            expression = newExpr;
        }
    }

    @Override
    public void remove(final Expression oldExpr) throws XPathException {
        throw new XPathException(this, "Method remove is not supported");
    }

    @Override
    public Expression getPrevious(final Expression current) {
        return null;
    }

    @Override
    public Expression getFirst() {
        return expression;
    }
}
