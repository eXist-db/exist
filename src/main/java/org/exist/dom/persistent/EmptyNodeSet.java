/* eXist Open Source Native XML Database
 * Copyright (C) 2001-2014,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id$
 */
package org.exist.dom.persistent;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;

public final class EmptyNodeSet extends AbstractNodeSet {

    public static final EmptyNodeSetIterator EMPTY_ITERATOR = new EmptyNodeSetIterator();
    public static final EmptyCollectionIterator EMPTY_COLLECTION_ITERATOR = new EmptyCollectionIterator();

    @Override
    public NodeSetIterator iterator() {
        return EMPTY_ITERATOR;
    }

    @Override
    public SequenceIterator iterate() {
        return SequenceIterator.EMPTY_ITERATOR;
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return SequenceIterator.EMPTY_ITERATOR;
    }

    @Override
    public boolean contains(final NodeProxy proxy) {
        return false;
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
    public void add(final NodeProxy proxy) {
    }

    @Override
    public void addAll(final NodeSet other) {
    }

    @Override
    public int getLength() {
        return 0;
    }

    @Override
    public long getItemCountLong() {
        return 0;
    }

    @Override
    public Node item(final int pos) {
        return null;
    }

    @Override
    public Item itemAt(final int pos) {
        return null;
    }

    @Override
    public NodeProxy get(final int pos) {
        return null;
    }

    public NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        return null;
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl doc, final NodeId nodeId,
            final boolean directParent, final boolean includeSelf) {
        return null;
    }

    @Override
    public NodeProxy get(final NodeProxy proxy) {
        return null;
    }

    @Override
    public NodeSet intersection(final NodeSet other) {
        return this;
    }

    @Override
    public NodeSet deepIntersection(final NodeSet other) {
        return this;
    }

    @Override
    public NodeSet union(final NodeSet other) {
        return other;
    }

    private static final class EmptyNodeSetIterator implements NodeSetIterator {

        @Override
        public final boolean hasNext() {
            return false;
        }

        @Override
        public final NodeProxy next() {
            throw new NoSuchElementException("There are no nodes in the empty set");
        }

        @Override
        public final void remove() {
            throw new IllegalStateException("Cannot remove node from an empty set");
        }

        @Override
        public final NodeProxy peekNode() {
            throw new IllegalStateException("Cannot peek into an empty set");
        }

        public final void setPosition(final NodeProxy proxy) {
            throw new IllegalStateException("Cannot reposition within an empty set");
        }

        @Override
        public final String toString() {
            return "Empty#" + super.toString();
        }

    }

    private static final class EmptyCollectionIterator implements Iterator<Collection> {

        @Override
        public final boolean hasNext() {
            return false;
        }

        @Override
        public final Collection next() {
            throw new NoSuchElementException("There are no collections in the empty set");
        }

        @Override
        public final void remove() {
            throw new IllegalStateException("Cannot remove collection from an empty set");
        }

    }
}
