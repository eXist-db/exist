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

import org.exist.xquery.XPathException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReversedSequenceTest {

    private static ValueSequence buildSequence(final int... values) {
        final ValueSequence seq = new ValueSequence(values.length);
        for (final int v : values) {
            seq.add(new IntegerValue(v));
        }
        return seq;
    }

    @Test
    public void itemAt_returns_items_in_reverse() throws XPathException {
        final ValueSequence original = buildSequence(10, 20, 30, 40);
        final ReversedSequence reversed = new ReversedSequence(original);

        assertEquals(40, ((IntegerValue) reversed.itemAt(0)).getLong());
        assertEquals(30, ((IntegerValue) reversed.itemAt(1)).getLong());
        assertEquals(20, ((IntegerValue) reversed.itemAt(2)).getLong());
        assertEquals(10, ((IntegerValue) reversed.itemAt(3)).getLong());
    }

    @Test
    public void itemAt_out_of_range_returns_null() {
        final ReversedSequence reversed = new ReversedSequence(buildSequence(1, 2, 3));
        assertNull(reversed.itemAt(-1));
        assertNull(reversed.itemAt(3));
    }

    @Test
    public void iterate_descends_through_items() throws XPathException {
        final ReversedSequence reversed = new ReversedSequence(buildSequence(1, 2, 3, 4, 5));
        final SequenceIterator it = reversed.iterate();

        assertEquals(5, ((IntegerValue) it.nextItem()).getLong());
        assertEquals(4, ((IntegerValue) it.nextItem()).getLong());
        assertEquals(3, ((IntegerValue) it.nextItem()).getLong());
        assertEquals(2, ((IntegerValue) it.nextItem()).getLong());
        assertEquals(1, ((IntegerValue) it.nextItem()).getLong());
        assertFalse(it.hasNext());
        assertNull(it.nextItem());
    }

    @Test
    public void iterate_skip_advances_correctly() throws XPathException {
        final ReversedSequence reversed = new ReversedSequence(buildSequence(1, 2, 3, 4, 5));
        final SequenceIterator it = reversed.iterate();

        assertEquals(5L, it.skippable());
        assertEquals(2L, it.skip(2));
        assertEquals(3L, it.skippable());
        assertEquals(3, ((IntegerValue) it.nextItem()).getLong());
    }

    @Test
    public void cardinality_reflects_size() {
        assertTrue(new ReversedSequence(buildSequence()).isEmpty());
        assertTrue(new ReversedSequence(buildSequence(7)).hasOne());
        assertTrue(new ReversedSequence(buildSequence(1, 2)).hasMany());
    }

    @Test
    public void item_count_matches_original() {
        final ValueSequence original = buildSequence(1, 2, 3, 4);
        assertEquals(original.getItemCount(), new ReversedSequence(original).getItemCount());
    }

    @Test
    public void getOriginal_returns_underlying_sequence() {
        final ValueSequence original = buildSequence(1, 2, 3);
        final ReversedSequence reversed = new ReversedSequence(original);
        assertSame(original, reversed.getOriginal());
    }
}
