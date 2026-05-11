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

import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
import org.exist.xquery.value.SequenceIterator;
import org.junit.Test;
import org.w3c.dom.Node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NodeProxyTest {

    @Test
    public void iterateLoop() {
        final NodeProxy mockNodeProxy = new NodeProxy(null, null, null, Node.ELEMENT_NODE, -1);

        final SequenceIterator it = mockNodeProxy.iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(1, count);
    }

    @Test
    public void iterateSkipLoop() {
        final NodeProxy mockNodeProxy = new NodeProxy(null, null, null, Node.ELEMENT_NODE, -1);
        final SequenceIterator it = mockNodeProxy.iterate();

        assertEquals(1, it.skippable());

        assertEquals(1, it.skip(10));

        assertEquals(0, it.skippable());

        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(0, count);
    }

    @Test
    public void iterateLoopSkipLoop() {
        final NodeProxy mockNodeProxy = new NodeProxy(null, null, null, Node.ELEMENT_NODE, -1);
        final SequenceIterator it = mockNodeProxy.iterate();

        int len = 20;
        int count = 0;
        for (int i = 0; it.hasNext() && i < len; i++) {
            it.nextItem();
            count++;
        }
        assertEquals(1, count);

        assertEquals(0, it.skippable());

        assertEquals(0, it.skip(10));

        assertEquals(0, it.skippable());

        count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(0, count);
    }

    @Test
    public void deepCopyContextClearsExistingContextWhenSourceHasNone() {
        final NodeProxy target = new NodeProxy(null, null, NodeId.DOCUMENT_NODE, Node.ELEMENT_NODE, -1);
        final NodeProxy existingContextNode = new NodeProxy(null, null, NodeId.ROOT_NODE, Node.ELEMENT_NODE, -1);
        target.addContextNode(42, existingContextNode);

        final NodeProxy sourceWithoutContext = new NodeProxy(null, null, NodeId.ROOT_NODE, Node.ELEMENT_NODE, -1);
        target.deepCopyContext(sourceWithoutContext);

        assertNull(target.getContext());
    }

    @Test
    public void deepCopyContextWithContextIdAddsContextIdEvenWhenSourceHasNoContext() {
        final NodeProxy target = new NodeProxy(null, null, NodeId.DOCUMENT_NODE, Node.ELEMENT_NODE, -1);
        final NodeProxy existingContextNode = new NodeProxy(null, null, NodeId.ROOT_NODE, Node.ELEMENT_NODE, -1);
        target.addContextNode(7, existingContextNode);

        final NodeProxy sourceWithoutContext = new NodeProxy(null, null, NodeId.END_OF_DOCUMENT, Node.ELEMENT_NODE, -1);
        target.deepCopyContext(sourceWithoutContext, 99);

        final ContextItem context = target.getContext();
        assertEquals(2, countContextItems(context));
        assertNotNull(findContextItem(context, 99));
        assertEquals(NodeId.END_OF_DOCUMENT, findContextItem(context, 99).getNode().getNodeId());
    }

    @Test
    public void propagatePredicateContextFromNoContextIdSkipsWhenSourceHasNoContext() {
        final NodeProxy target = new NodeProxy(null, null, NodeId.DOCUMENT_NODE, Node.ELEMENT_NODE, -1);
        final NodeProxy existingContextNode = new NodeProxy(null, null, NodeId.ROOT_NODE, Node.ELEMENT_NODE, -1);
        target.addContextNode(42, existingContextNode);

        final NodeProxy sourceWithoutContext = new NodeProxy(null, null, NodeId.END_OF_DOCUMENT, Node.ELEMENT_NODE, -1);
        NodeProxy.propagatePredicateContextFrom(target, sourceWithoutContext, Expression.NO_CONTEXT_ID);

        assertEquals(42, target.getContext().getContextId());
    }

    @Test
    public void deepCopyContextSelfCopyIsNoOp() {
        final NodeProxy node = new NodeProxy(null, null, NodeId.DOCUMENT_NODE, Node.ELEMENT_NODE, -1);
        final NodeProxy existingContextNode = new NodeProxy(null, null, NodeId.ROOT_NODE, Node.ELEMENT_NODE, -1);
        node.addContextNode(42, existingContextNode);

        node.deepCopyContext(node);

        final ContextItem context = node.getContext();
        assertEquals(1, countContextItems(context));
        assertEquals(42, context.getContextId());
        assertEquals(NodeId.ROOT_NODE, context.getNode().getNodeId());
    }

    private int countContextItems(final ContextItem contextItem) {
        int count = 0;
        ContextItem current = contextItem;
        while (current != null) {
            count++;
            current = current.getNextDirect();
        }
        return count;
    }

    private ContextItem findContextItem(final ContextItem contextItem, final int contextId) {
        ContextItem current = contextItem;
        while (current != null) {
            if (current.getContextId() == contextId) {
                return current;
            }
            current = current.getNextDirect();
        }
        return null;
    }
}
