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

//TODO replace with extends Iterator<Item>
public interface SequenceIterator {

    SequenceIterator EMPTY_ITERATOR = new EmptySequenceIterator();

    /**
     * Determines if there is a next item in the sequence
     *
     * @return true if there is another item available, false otherwise.
     */
    boolean hasNext();

    /**
     * Retrieves the next item from the Sequence.
     *
     * If you do not care about the actual value and
     * are just trying to advance the iterator, you
     * should consider calling {@link #skip(long)} instead.
     *
     * @return The item, or null if there are no more items
     */
    Item nextItem();

    /**
     * Returns the number of the items in the sequence
     * that may be skipped over from the current position.
     *
     * @return The number of items that may be skipped with {@link #skip(long)},
     *     or -1 if no items may be skipped.
     */
    default long skippable() {
        return -1;
    }

    /**
     * Skip forward over {@code n} items from the current position.
     *
     * @param n number of items to skip
     * @return the number of items actually skipped over, zero
     *     if no items could be skipped, or -1 if this sequence
     *     does not support skipping.
     */
    default long skip(final long n) {
        return -1;
    }
}
