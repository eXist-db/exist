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
package org.exist.xquery.update;

import org.exist.xquery.AbstractExpression;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Type;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public abstract class ModifyingExpression extends AbstractExpression {

    protected final Expression targetExpr;

    public ModifyingExpression(final XQueryContext context, final Expression target) {
        super(context);
        this.targetExpr = target;
    }

    @Override
    public int returnsType() {
        // placeholder implementation
        return Type.EMPTY;
    }

    public Category getCategory() {
        return Category.UPDATING;
    }

    public Expression getTargetExpr() {
        return targetExpr;
    }
}
