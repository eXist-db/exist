
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
 * 
 * $Id$
 */
package org.exist.dom;

import java.util.Iterator;

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class EmptyNodeSet extends AbstractNodeSet {

    public final static EmptyNodeSetIterator EMPTY_ITERATOR = new EmptyNodeSetIterator();

    public final static EmptyCollectionIterator EMPTY_COLLECTION_ITERATOR = new EmptyCollectionIterator();

    @Override
    public NodeSetIterator iterator() {
        return EMPTY_ITERATOR;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterate()
     */
    @Override
    public SequenceIterator iterate() throws XPathException {
        return SequenceIterator.EMPTY_ITERATOR;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        return SequenceIterator.EMPTY_ITERATOR;
    }

    public boolean contains(DocumentImpl doc, long nodeId) {
        return false;
    }

    @Override
    public boolean contains(NodeProxy proxy) {
        return false;
    }

    public boolean contains(DocumentImpl doc) {
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

    public void add(DocumentImpl doc, long nodeId) {
        //Nothing to do
    }

    public void add(Node node) {
        //Nothing todo
    }

    @Override
    public void add(NodeProxy proxy) {
        //Nothing to do
    }

    public void addAll(NodeList other) {
        //Nothing to do
    }

    @Override
    public void addAll(NodeSet other) {
        //Nothing to do
    }

    public void remove(NodeProxy node) {
        //Nothing to do
    }

    @Override
    public int getLength() {
        return 0;
    }

    @Override
    public int getItemCount() {
        return 0;
    }

    @Override
    public Node item(int pos) {
        return null;
    }

    @Override
    public Item itemAt(int pos) {
        return null;
    }

    @Override
    public NodeProxy get(int pos) {
        return null;
    }

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        return null;
    }

    @Override
    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId,
            boolean directParent, boolean includeSelf) {
        return null;
    }

    @Override
    public NodeProxy get(NodeProxy proxy) {
        return null;
    }

    @Override
    public NodeSet intersection(NodeSet other) {
        return this;
    }

    @Override
    public NodeSet deepIntersection(NodeSet other) {
        return this;
    }

    @Override
    public NodeSet union(NodeSet other) {
        return other;
    }

    private final static class EmptyNodeSetIterator implements NodeSetIterator {

        /* (non-Javadoc)
         * @see java.util.Iterator#remove()
         */
        public void remove() {
            //Nothing to do
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return false;
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#next()
         */
        public NodeProxy next() {
            return null;
        }

        public NodeProxy peekNode() {
            return null;
        }

        public void setPosition(NodeProxy proxy) {
            //Nothing to do
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();
            result.append("Empty#").append(super.toString());
            return result.toString();
        }

    } 

    private final static class EmptyCollectionIterator implements Iterator<Collection> {

        public boolean hasNext() {
            return false;
        }

        public Collection next() {
            return null;
        }

        public void remove() {
            //Nothing to do
        }

    }
}
