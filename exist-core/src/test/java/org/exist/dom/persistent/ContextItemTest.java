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

import org.exist.numbering.DLN;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ContextItemTest {

    private static NodeProxy node(final String id) {
        return new NodeProxy(null, null, new DLN(id), Node.ELEMENT_NODE, -1);
    }

    private static String chain(final ContextItem chain) {
        final StringBuilder buf = new StringBuilder();
        for (ContextItem item = chain; item != null; item = item.getNextDirect()) {
            buf.append(item.getContextId()).append(':').append(item.getNode().getNodeId()).append(' ');
        }
        return buf.toString().trim();
    }

    @Test
    void appendLeavesTheChainAsItWas() {
        final ContextItem one = new ContextItem(1, node("1.1"));
        final ContextItem two = one.append(2, node("1.2"));
        assertEquals("1:1.1", chain(one));
        assertEquals("1:1.1 2:1.2", chain(two));
    }

    @Test
    void appendIgnoresAnItemItHasAlready() {
        final ContextItem chain = new ContextItem(1, node("1.1")).append(2, node("1.2"));
        assertSame(chain, chain.append(2, node("1.2")));
    }

    @Test
    void withoutLeavesTheChainAsItWas() {
        final ContextItem chain = new ContextItem(1, node("1.1")).append(2, node("1.2")).append(1, node("1.3"))
                .append(3, node("1.4"));
        assertEquals("2:1.2 3:1.4", chain(ContextItem.without(chain, 1)));
        assertEquals("1:1.1 2:1.2 1:1.3 3:1.4", chain(chain));
    }

    @Test
    void withoutSharesTheRestAfterTheLastRemovedItem() {
        final ContextItem chain = new ContextItem(1, node("1.1")).append(2, node("1.2")).append(3, node("1.3"));
        assertSame(chain.getNextDirect(), ContextItem.without(chain, 1));
    }

    @Test
    void withoutAnAbsentIdIsTheChainItself() {
        final ContextItem chain = new ContextItem(1, node("1.1")).append(2, node("1.2"));
        assertSame(chain, ContextItem.without(chain, 9));
        assertNull(ContextItem.without(null, 1));
    }

    @Test
    void withoutEveryItemIsNull() {
        final ContextItem chain = new ContextItem(1, node("1.1")).append(1, node("1.2"));
        assertNull(ContextItem.without(chain, 1));
    }

    @Test
    void nodesSharingAChainKeepTheirOwnContext() {
        final NodeProxy a = node("1.1");
        a.addContextNode(1, node("1"));
        final NodeProxy b = node("1.1");
        b.copyContext(a);
        b.addContextNode(2, node("1.2"));
        a.clearContext(1);
        assertNull(a.getContext());
        assertEquals("1:1 2:1.2", chain(b.getContext()));
    }
}
