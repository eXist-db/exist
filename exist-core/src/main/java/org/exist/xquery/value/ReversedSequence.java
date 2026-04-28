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

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;

/**
 * A lazy reversed view over a sequence that supports random access via
 * {@link Sequence#itemAt(int)}. No items are copied or materialized;
 * {@code itemAt(pos)} delegates to {@code original.itemAt(size - 1 - pos)}.
 *
 * Intended for atomic-value sequences (notably {@link ValueSequence}) where
 * reversal does not need to preserve document order. Node sets that must
 * stay in document order should be reversed by materialization, not by
 * wrapping.
 */
public class ReversedSequence extends AbstractSequence {

    private final Sequence original;
    private final int size;

    public ReversedSequence(final Sequence original) {
        this.original = original;
        this.size = original.getItemCount();
        this.isEmpty = size == 0;
        this.hasOne = size == 1;
    }

    /**
     * @return the original sequence wrapped by this reversed view.
     */
    public Sequence getOriginal() {
        return original;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException((org.exist.xquery.Expression) null,
                "Internal error: adding to an immutable reversed sequence");
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw new XPathException((org.exist.xquery.Expression) null,
                "Internal error: adding to an immutable reversed sequence");
    }

    @Override
    public int getItemType() {
        return original.getItemType();
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        return new ReversedSequenceIterator(original, size);
    }

    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        return iterate();
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
        if (pos < 0 || pos >= size) {
            return null;
        }
        return original.itemAt(size - 1 - pos);
    }

    @Override
    public boolean contains(final Item item) {
        return original.contains(item);
    }

    @Override
    public boolean containsReference(final Item item) {
        return original.containsReference(item);
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        throw new XPathException((org.exist.xquery.Expression) null,
                "Type error: a reversed sequence cannot be converted into a node set "
                        + "(node sets are always in document order).");
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        throw new XPathException((org.exist.xquery.Expression) null,
                "Type error: a reversed sequence cannot be converted into a memory node set "
                        + "(node sets are always in document order).");
    }

    @Override
    public void removeDuplicates() {
        // Reversed wrapper only delegates to underlying random-access reads.
        // Duplicate removal would require materialization; a no-op preserves
        // the wrapper's laziness. Callers that need uniqueness must materialize.
    }

    @Override
    public String toString() {
        return "Reversed(" + original + ")";
    }

    private static final class ReversedSequenceIterator implements SequenceIterator {
        private final Sequence original;
        private final int size;
        private int pos;

        private ReversedSequenceIterator(final Sequence original, final int size) {
            this.original = original;
            this.size = size;
            this.pos = size - 1;
        }

        @Override
        public boolean hasNext() {
            return pos >= 0;
        }

        @Override
        public Item nextItem() {
            if (pos < 0) {
                return null;
            }
            return original.itemAt(pos--);
        }

        @Override
        public long skippable() {
            return pos < 0 ? 0 : pos + 1L;
        }

        @Override
        public long skip(final long n) {
            if (pos < 0 || n <= 0) {
                return 0;
            }
            final long skip = Math.min(n, pos + 1L);
            pos -= (int) skip;
            return skip;
        }
    }
}
