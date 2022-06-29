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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import java.math.BigInteger;

public class RangeSequence extends AbstractSequence {

    private final static Logger LOG = LogManager.getLogger(AbstractSequence.class);

    private final IntegerValue start;
    private final IntegerValue end;

    public RangeSequence(final IntegerValue start, final IntegerValue end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException((Expression) null, "Internal error: adding to an immutable sequence");
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw new XPathException((Expression) null, "Internal error: adding to an immutable sequence");
    }

    public int getItemType() {
        return Type.INTEGER;
    }

    @Override
    public SequenceIterator iterate() {
        return new RangeSequenceIterator(start.getLong(), end.getLong());
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new RangeSequenceIterator(start.getLong(), end.getLong());
    }

    public SequenceIterator iterateInReverse() {
        return new ReverseRangeSequenceIterator(start.getLong(), end.getLong());
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
        if (start.compareTo(end) > 0) {
            return 0;
        }
        try {
            return ((IntegerValue) end.minus(start)).getLong() + 1;
        } catch (final XPathException e) {
            LOG.warn("Unexpected exception when processing result of range expression: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return getItemCountLong() == 0;
    }

    @Override
    public boolean hasOne() {
        return getItemCountLong() == 1;
    }

    @Override
    public boolean hasMany() {
        return getItemCountLong() > 1;
    }

    @Override
    public Cardinality getCardinality() {
        final long itemCount = getItemCountLong();
        if (itemCount <= 0) {
            return Cardinality.EMPTY_SEQUENCE;
        }
        if (itemCount == 1) {
            return Cardinality.EXACTLY_ONE;
        }
        return Cardinality._MANY;
    }

    @Override
    public Item itemAt(final int pos) {
        if (pos < getItemCountLong()) {
            return new IntegerValue(start.getLong() + pos);
        }
        return null;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                " a node set. Item type is xs:integer");
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                " a memory node set. Item type is xs:integer");
    }

    @Override
    public void removeDuplicates() {
    }

    @Override
    public boolean containsReference(final Item item) {
        return start == item || end == item;
    }

    @Override
    public boolean contains(final Item item) {
        if (item instanceof IntegerValue) {
            try {
                final BigInteger other = item.toJavaObject(BigInteger.class);
                return other.compareTo(start.toJavaObject(BigInteger.class)) >= 0
                        && other.compareTo(end.toJavaObject(BigInteger.class)) <= 0;
            } catch (final XPathException e) {
                LOG.warn(e.getMessage(), e);
                return false;
            }
        }
        return false;
    }

	/**
	 * Generates a string representation of the Range Sequence.
	 *
	 * Range sequences can potentially be
	 * very large, so we generate a summary here
	 * rather than evaluating to generate a (possibly)
	 * huge sequence of objects.
	 *
	 * @return a string representation of the range sequence.
	 */
	@Override
	public String toString() {
		return "Range(" + start + " to " + end + ")";
	}
}
