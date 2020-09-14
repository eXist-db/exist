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
package org.exist.dom.persistent;

import org.exist.xquery.Expression;

public class ContextItem {

    private NodeProxy node;

    private ContextItem nextDirect;
    private final int contextId;

    public ContextItem(final NodeProxy node) {
        this(Expression.NO_CONTEXT_ID, node);
    }

    public ContextItem(final int contextId, final NodeProxy node) {
        this.contextId = contextId;
        this.node = node;
    }

    public NodeProxy getNode() {
        return node;
    }

    public int getContextId() {
        return contextId;
    }

    public boolean hasNextDirect() {
        return (nextDirect != null);
    }

    public ContextItem getNextDirect() {
        return nextDirect;
    }

    public void setNextContextItem(final ContextItem next) {
        nextDirect = next;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(node);
        if(nextDirect != null) {
            buf.append("/").append(nextDirect);
        }
        return buf.toString();
    }
}
