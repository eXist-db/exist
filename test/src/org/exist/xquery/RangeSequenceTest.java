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

package org.exist.xquery;

import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.SequenceIterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RangeSequenceTest {

    private final RangeSequence rangeSequence = new RangeSequence(new IntegerValue(1), new IntegerValue(99));

    @Test
    public void iterate_loop() {
        final SequenceIterator it = rangeSequence.iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(99, count);
    }

    @Test
    public void iterate_skip_loop() {
        final SequenceIterator it = rangeSequence.iterate();

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
    public void iterate_loop_skip_loop() {
        final SequenceIterator it = rangeSequence.iterate();

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

    @Test
    public void iterateInReverse_loop() {
        final SequenceIterator it = rangeSequence.iterateInReverse();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(99, count);
    }

    @Test
    public void iterateInReverse_skip_loop() {
        final SequenceIterator it = rangeSequence.iterateInReverse();

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
    public void iterateInReverse_loop_skip_loop() {
        final SequenceIterator it = rangeSequence.iterateInReverse();

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

    @Test
    public void itemAt_last() throws XPathException {
        assertEquals(99, rangeSequence.itemAt(98).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void itemAt_afterEnd() {
        assertNull(rangeSequence.itemAt(99));
    }
}
