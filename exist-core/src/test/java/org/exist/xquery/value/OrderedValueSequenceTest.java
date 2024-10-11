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

package org.exist.xquery.value;

import org.exist.xquery.Expression;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;
import org.junit.Test;

import java.util.Arrays;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

public class OrderedValueSequenceTest {

    @Test
    public void iterate_loop() throws XPathException {
        final OrderedValueSequence orderedValueSequence = mockOrderedValueSequence(99);

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
        final OrderedValueSequence orderedValueSequence = mockOrderedValueSequence(99);
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
        final OrderedValueSequence orderedValueSequence = mockOrderedValueSequence(99);
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

    private static OrderedValueSequence mockOrderedValueSequence(final int size) throws XPathException {
        final Expression mockSortExpr = createMock(Expression.class);
        expect(mockSortExpr.eval(null, null)).andReturn(Sequence.EMPTY_SEQUENCE).anyTimes();
        replay(mockSortExpr);

        final OrderedValueSequence orderedValueSequence = new OrderedValueSequence(Arrays.asList(new OrderSpec(null, mockSortExpr)), size);
        for (int i = 0; i < size; i++) {
            final Item item = createMock(Item.class);
            expect(item.getType()).andReturn(Type.ANY_TYPE);
            replay(item);
            orderedValueSequence.add(item);
        }
        return orderedValueSequence;
    }
}
