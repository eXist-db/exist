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

import org.exist.xquery.value.SequenceIterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RangeSequenceTest {

    private final RangeSequence rangeSequence = new RangeSequence(1L, 99L);

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

    @Test
    public void reverse_size_unchanged() {
        final RangeSequence reversed = rangeSequence.reverse();
        assertEquals(99L, reversed.getItemCountLong());
    }

    @Test
    public void reverse_first_item_is_old_last() throws XPathException {
        final RangeSequence reversed = rangeSequence.reverse();
        assertEquals(99, reversed.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void reverse_last_item_is_old_first() throws XPathException {
        final RangeSequence reversed = rangeSequence.reverse();
        assertEquals(1, reversed.itemAt(98).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void reverse_iterate_descends() throws XPathException {
        final RangeSequence reversed = rangeSequence.reverse();
        final SequenceIterator it = reversed.iterate();
        assertEquals(99, it.nextItem().toJavaObject(Integer.class).intValue());
        assertEquals(98, it.nextItem().toJavaObject(Integer.class).intValue());
    }

    @Test
    public void reverse_reverse_returns_ascending() throws XPathException {
        final RangeSequence reversed = rangeSequence.reverse().reverse();
        assertEquals(1, reversed.itemAt(0).toJavaObject(Integer.class).intValue());
        assertEquals(99, reversed.itemAt(98).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void reverse_huge_range_no_oom() throws XPathException {
        final RangeSequence huge = new RangeSequence(1L, 10_000_000_000L);
        final RangeSequence reversed = huge.reverse();
        // Should be O(1) — itemAt(0) of the reversed view is the original end.
        assertEquals(10_000_000_000L, ((org.exist.xquery.value.IntegerValue) reversed.itemAt(0)).getLong());
        // Verify the reverse iterator is also lazy: skip almost the whole range.
        final SequenceIterator it = reversed.iterate();
        assertEquals(10_000_000_000L, it.skippable());
    }

    @Test
    public void reverse_single_item_returns_self() {
        final RangeSequence single = new RangeSequence(5L, 5L);
        assertEquals(single, single.reverse());
    }

    @Test
    public void reverse_empty_returns_self() {
        final RangeSequence empty = new RangeSequence(10L, 1L);
        assertEquals(empty, empty.reverse());
    }
}
