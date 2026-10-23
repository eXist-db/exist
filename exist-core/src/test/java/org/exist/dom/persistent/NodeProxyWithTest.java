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
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The {@code with…} methods leave the node they are called on as it was, and return it when nothing changes.
 */
class NodeProxyWithTest {

    private static final class TestMatch extends Match {
        TestMatch(final NodeId nodeId, final String term) {
            super(0, nodeId, term);
        }

        private TestMatch(final TestMatch match) {
            super(match);
        }

        @Override
        public Match createInstance(final int contextId, final NodeId nodeId, @Nullable final String matchTerm) {
            return new TestMatch(nodeId, matchTerm);
        }

        @Override
        public Match newCopy() {
            return new TestMatch(this);
        }

        @Override
        public String getIndexId() {
            return "test";
        }
    }

    private static NodeProxy node(final String id) {
        return new NodeProxy(null, null, new DLN(id), Node.ELEMENT_NODE, -1);
    }

    @Test
    void withMatchLeavesTheNodeAsItWas() {
        final NodeProxy node = node("1.1");
        final NodeProxy matched = node.withMatch(new TestMatch(new DLN("1.1"), "a"));
        assertNull(node.getMatches());
        assertEquals("a", matched.getMatches().toString());
        assertEquals(node.getNodeId(), matched.getNodeId());
    }

    @Test
    void withMatchesOfANodeWithoutMatchesIsTheNodeItself() {
        final NodeProxy node = node("1.1").withMatch(new TestMatch(new DLN("1.1"), "a"));
        assertSame(node, node.withMatchesOf(node("1.1")));
        assertSame(node, node.withMatchesOf(node));
    }

    @Test
    void withMatchesOfLeavesBothNodesAsTheyWere() {
        final NodeProxy a = node("1.1").withMatch(new TestMatch(new DLN("1.1"), "a"));
        final NodeProxy b = node("1.1").withMatch(new TestMatch(new DLN("1.1"), "b"));
        final NodeProxy both = a.withMatchesOf(b);
        assertNotSame(a, both);
        assertNull(a.getMatches().getNextMatch());
        assertNull(b.getMatches().getNextMatch());
        // a match's toString shows the rest of its chain
        assertEquals("b a", both.getMatches().toString());
    }

    @Test
    void withContextNodeLeavesTheNodeAsItWas() {
        final NodeProxy node = node("1.1");
        final NodeProxy context = node("1");
        final NodeProxy tagged = node.withContextNode(7, context);
        assertNull(node.getContext());
        assertEquals(7, tagged.getContext().getContextId());
        assertSame(context, tagged.getContext().getNode());
        assertSame(tagged, tagged.withContextNode(7, context));
    }

    @Test
    void withoutContextOfAnAbsentPredicateIsTheNodeItself() {
        final NodeProxy tagged = node("1.1").withContextNode(7, node("1"));
        assertSame(tagged, tagged.withoutContext(8));
        assertNull(tagged.withoutContext(7).getContext());
        assertNull(tagged.withoutContext(Expression.IGNORE_CONTEXT).getContext());
        assertEquals(7, tagged.getContext().getContextId());
    }

    @Test
    void withContextOfSharesTheOtherNodesChain() {
        final NodeProxy tagged = node("1.1").withContextNode(7, node("1"));
        final NodeProxy other = node("1.1");
        final NodeProxy copy = other.withContextOf(tagged);
        assertSame(tagged.getContext(), copy.getContext());
        assertNull(other.getContext());
        assertSame(copy, copy.withContextOf(tagged));
    }
}
