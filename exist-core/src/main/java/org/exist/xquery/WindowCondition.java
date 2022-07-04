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

/**
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowCondition {
    @SuppressWarnings("unused")
    private final XQueryContext context;
    private Collator collator;
    private final Expression whenExpression;
    private final String posVar;
    private final QName currentItem;
    private final QName previousItem;
    private final QName nextItem;
    private final boolean only;

    public WindowCondition(final XQueryContext context, final Expression whenExpr,
            final QName current, final QName previous, final QName next, final String posVar, final boolean only) {
        this.whenExpression = whenExpr;
        this.context = context;
        this.currentItem = current;
        this.previousItem = previous;
        this.nextItem = next;
        this.posVar = posVar;
        this.only = only;
        this.collator = context.getDefaultCollator();
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
