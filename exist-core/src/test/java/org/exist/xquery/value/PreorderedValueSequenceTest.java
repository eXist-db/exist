/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.value;

import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.w3c.dom.Node;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

public class PreorderedValueSequenceTest {

    @Test
    public void iterate_loop() throws XPathException {
        final PreorderedValueSequence orderedValueSequence = mockPreorderedValueSequence(99);

        final SequenceIterator it = orderedValueSequence.iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(99, count);
    }

    @Test
    public void iterate_skip_loop() throws XPathException {
        final PreorderedValueSequence orderedValueSequence = mockPreorderedValueSequence(99);
        final SequenceIterator it = orderedValueSequence.iterate();

        assertEquals(99, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(89, it.skippable());

        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(89, count);
    }

    @Test
    public void iterate_loop_skip_loop() throws XPathException {
        final PreorderedValueSequence orderedValueSequence = mockPreorderedValueSequence(99);
        final SequenceIterator it = orderedValueSequence.iterate();

        int len = 20;
        int count = 0;
        for (int i = 0; it.hasNext() && i < len; i++) {
            it.nextItem();
            count++;
        }
        assertEquals(20, count);

        assertEquals(79, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(69, it.skippable());

        count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(69, count);
    }

    private static PreorderedValueSequence mockPreorderedValueSequence(final int size) throws XPathException {
        final Sequence valueSequence = new ValueSequence();

        for (int i = 0; i < size; i++) {
            final NodeProxy mockNode = createMock(NodeProxy.class);
            expect(mockNode.getType()).andReturn(Type.NODE).times(2);
            expect(mockNode.getOwnerDocument()).andReturn(null);
            expect(mockNode.getNodeId()).andReturn(null);
            expect(mockNode.getNodeType()).andReturn(Node.ELEMENT_NODE);
            expect(mockNode.getInternalAddress()).andReturn(-1l);
            mockNode.addContextNode(anyInt(), anyObject());
            replay(mockNode);
            valueSequence.add(mockNode);
        }

        return new PreorderedValueSequence(new OrderSpec[0], valueSequence, 7);
    }
}
