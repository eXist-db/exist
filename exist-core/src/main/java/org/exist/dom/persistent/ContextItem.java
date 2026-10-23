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

import javax.annotation.Nullable;

/**
 * One item of a node's predicate context: the node it was selected from in the predicate {@code contextId}, and the
 * rest of the chain. A chain never changes once built, so nodes may share it; a node whose context changes gets a new
 * chain ({@link #append}, {@link #without}).
 */
public final class ContextItem {

    private final NodeProxy node;
    private final @Nullable ContextItem nextDirect;
    private final int contextId;

    public ContextItem(final NodeProxy node) {
        this(Expression.NO_CONTEXT_ID, node);
    }

    public ContextItem(final int contextId, final NodeProxy node) {
        this(contextId, node, null);
    }

    private ContextItem(final int contextId, final NodeProxy node, @Nullable final ContextItem nextDirect) {
        this.contextId = contextId;
        this.node = node;
        this.nextDirect = nextDirect;
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

    public @Nullable ContextItem getNextDirect() {
        return nextDirect;
    }

    /**
     * This chain with {@code (contextId, node)} at its end.
     *
     * @param contextId the predicate's context id
     * @param node the context node
     * @return a new chain, or this chain if it has an item for the same context id and node id already
     */
    public ContextItem append(final int contextId, final NodeProxy node) {
        int length = 0;
        for (ContextItem item = this; item != null; item = item.nextDirect) {
            if (item.contextId == contextId && item.node.getNodeId().equals(node.getNodeId())) {
                return this;
            }
            length++;
        }
        final ContextItem[] items = new ContextItem[length];
        int i = 0;
        for (ContextItem item = this; item != null; item = item.nextDirect) {
            items[i++] = item;
        }
        ContextItem chain = new ContextItem(contextId, node);
        for (i = length - 1; i >= 0; i--) {
            chain = new ContextItem(items[i].contextId, items[i].node, chain);
        }
        return chain;
    }

    /**
     * A chain without the items of {@code contextId}; the part after the last such item is shared.
     *
     * @param chain the chain, may be null
     * @param contextId the predicate's context id
     * @return the chain without those items: {@code chain} itself if it has none, null if nothing is left
     */
    public static @Nullable ContextItem without(@Nullable final ContextItem chain, final int contextId) {
        int last = -1;
        int length = 0;
        for (ContextItem item = chain; item != null; item = item.nextDirect) {
            if (item.contextId == contextId) {
                last = length;
            }
            length++;
        }
        if (last < 0) {
            return chain;
        }
        final ContextItem[] kept = new ContextItem[last];
        int count = 0;
        ContextItem item = chain;
        for (int i = 0; i <= last; i++, item = item.nextDirect) {
            if (item.contextId != contextId) {
                kept[count++] = item;
            }
        }
        // item is now the first item after the last one removed
        ContextItem result = item;
        for (int i = count - 1; i >= 0; i--) {
            result = new ContextItem(kept[i].contextId, kept[i].node, result);
        }
        return result;
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
