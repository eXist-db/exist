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
import org.exist.xquery.XPathException;

public class EmptySequence extends AbstractSequence {

    @Override
    public int getItemType() {
        return Type.EMPTY;
    }

    @Override
    public SequenceIterator iterate() {
        return EmptySequenceIterator.EMPTY_ITERATOR;
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return EmptySequenceIterator.EMPTY_ITERATOR;
    }

    @Override
    public long getItemCountLong() {
        return 0;
    }

    @Override
    public Item itemAt(int pos) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean hasOne() {
        return false;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException("cannot add an item to an empty sequence");
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.BOOLEAN:
                return new BooleanValue(false);
            case Type.STRING:
                return new StringValue("");
            default:
                throw new XPathException("cannot convert empty sequence to " + requiredType);
        }
    }

    @Override
    public NodeSet toNodeSet() {
        return NodeSet.EMPTY_SET;
    }

    @Override
    public MemoryNodeSet toMemNodeSet() {
        return MemoryNodeSet.EMPTY;
    }

    @Override
    public void removeDuplicates() {
        // nothing to do
    }

    @Override
    public String toString() {
        return "()";
    }
}
