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

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

/**
 * An immutable, lazy sequence representing an integer range (start to end).
 * Stores only the start and end values as primitive longs — no intermediate
 * IntegerValue objects are created until accessed. Operations like count(),
 * isEmpty(), itemAt(), and subsequence() are O(1).
 */
public class RangeSequence extends AbstractSequence {

    private final long start;
    private final long end;
    private final long size;

    public RangeSequence(final IntegerValue start, final IntegerValue end) {
        this(start.getLong(), end.getLong());
    }

    public RangeSequence(final long start, final long end) {
        this.start = start;
        this.end = end;
        if (start <= end) {
            final long diff = end - start;
            // Overflow protection: if diff < 0, the range is too large
            this.size = (diff >= 0) ? diff + 1 : Long.MAX_VALUE;
        } else {
            this.size = 0;
        }
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException(item, "Internal error: adding to an immutable sequence");
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw new XPathException(other, "Internal error: adding to an immutable sequence");
    }

    public int getItemType() {
        return Type.INTEGER;
    }

    @Override
    public SequenceIterator iterate() {
        return new RangeSequenceIterator(start, end);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new RangeSequenceIterator(start, end);
    }

    public SequenceIterator iterateInReverse() {
        return new ReverseRangeSequenceIterator(start, end);
    }

    private static class RangeSequenceIterator implements SequenceIterator {
        private long current;
        private final long end;

        private RangeSequenceIterator(final long start, final long end) {
            this.current = start;
            this.end = end;
        }

        @Override
        public Item nextItem() {
            if (current <= end) {
                return new IntegerValue(current++);
            } else {
                return null;
            }
        }

        @Override
        public boolean hasNext() {
            return current <= end;
        }

        @Override
        public long skippable() {
            return end - current + 1;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, end - current + 1);
            current += skip;
            return skip;
        }
    }

    private static class ReverseRangeSequenceIterator implements SequenceIterator {
        private final long start;
        private long current;

        private ReverseRangeSequenceIterator(final long start, final long end) {
            this.start = start;
            this.current = end;
        }

        @Override
        public Item nextItem() {
            if (current >= start) {
                return new IntegerValue(current--);
            } else {
                return null;
            }
        }

        @Override
        public boolean hasNext() {
            return current >= start;
        }

        @Override
        public long skippable() {
            return current - start + 1;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, current - start + 1);
            current -= skip;
            return skip;
        }
    }

    @Override
    public long getItemCountLong() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean hasOne() {
        return size == 1;
    }

    @Override
    public boolean hasMany() {
        return size > 1;
    }

    @Override
    public Cardinality getCardinality() {
        if (size == 0) {
            return Cardinality.EMPTY_SEQUENCE;
        }
        if (size == 1) {
            return Cardinality.EXACTLY_ONE;
        }
        return Cardinality._MANY;
    }

    @Override
    public Item itemAt(final int pos) {
        if (pos >= 0 && pos < size) {
            return new IntegerValue(start + pos);
        }
        return null;
    }

    @Override
    public boolean contains(final Item item) {
        if (item instanceof IntegerValue) {
            final long val = ((IntegerValue) item).getLong();
            return val >= start && val <= end;
        }
        return false;
    }

    @Override
    public boolean containsReference(final Item item) {
        return false; // primitives don't have reference identity
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        throw new XPathException(this, ErrorCodes.XPTY0019, "Type error: the sequence cannot be converted into" +
                " a node set. Item type is xs:integer");
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        throw new XPathException(this, ErrorCodes.XPTY0019, "Type error: the sequence cannot be converted into" +
                " a memory node set. Item type is xs:integer");
    }

    @Override
    public void removeDuplicates() {
    }

    @Override
    public String toString() {
        return "Range(" + start + " to " + end + ")";
    }
}
