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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;

import javax.annotation.Nullable;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowCondition {

    private final XQueryContext context;
    private Collator collator;
    private final boolean only;
    private final @Nullable QName currentItem;
    private final @Nullable QName posVar;
    private final @Nullable QName previousItem;
    private final @Nullable QName nextItem;
    private final Expression whenExpression;

    public WindowCondition(final XQueryContext context, final boolean only, @Nullable final QName current,
            @Nullable final QName posVar, @Nullable final QName previous, @Nullable final QName next,
            final Expression whenExpr) {

        this.context = context;
        this.collator = context.getDefaultCollator();
        this.only = only;
        this.currentItem = current;
        this.posVar = posVar;
        this.previousItem = previous;
        this.nextItem = next;
        this.whenExpression = whenExpr;
    }

    public void setCollator(final String collation) throws XPathException {
        this.collator = context.getCollator(collation);
    }

    public Collator getCollator() {
        return this.collator;
    }

    @Override
    public String toString() {
        return this.only ? "only " : ""
                + "current " + this.currentItem  + " at " + this.posVar
                + " previous " + this.previousItem
                + " next " + this.nextItem
                + " when " + this.whenExpression.toString();
    }
}
