/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.dom.persistent;

import org.exist.xquery.Constants;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Removes duplication between NewArrayNodeSet
 * and ExtArrayNodeSet
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public abstract class AbstractArrayNodeSet extends AbstractNodeSet implements DocumentSet {

    protected static final int INITIAL_SIZE = 64;

    protected int size = 0;

    protected boolean isSorted = false;
    protected boolean hasOne = false;

    protected int state = 0;

    private NodeProxy lastAdded = null;

    //  used to keep track of the type of added items.
    protected int itemType = Type.ANY_TYPE;

    /**
     * Reset the ArrayNodeSet so that it
     * may be reused
     */
    public abstract void reset();

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean hasOne() {
        return hasOne;
    }

    @Override
    public void add(final NodeProxy proxy) {
        add(proxy, Constants.NO_SIZE_HINT);
    }

    /**
     * Add a new node to the set. If a new array of nodes has to be allocated
     * for the document, use the sizeHint parameter to determine the size of
     * the newly allocated array. This will overwrite the default array size.
     *
     * If the size hint is correct, no further reallocations will be required.
     */
    @Override
    public void add(final NodeProxy proxy, final int sizeHint) {
        if(size > 0) {
            if(hasOne) {
                if(isSorted) {
                    this.hasOne = get(proxy) != null;
                } else {
                    this.hasOne = lastAdded == null || lastAdded.compareTo(proxy) == 0;
                }
            }
        } else {
            this.hasOne = true;
        }

        addInternal(proxy, sizeHint);

        this.isSorted = false;
        setHasChanged();
        checkItemType(proxy.getType());
        this.lastAdded = proxy;
    }

    /**
     * Just add the node to this set
     * all of the checks have been
     * done in @see AbstractArrayNodeSet#add(NodeProxy, int)
     * @param proxy  node to add
     * @param sizeHint hint about the size
     */
    protected abstract void addInternal(final NodeProxy proxy, final int sizeHint);

    @Override
    public void addAll(final NodeSet other) {
        if(other.isEmpty()) {
            return;
        } else if(other.hasOne()) {
            add((NodeProxy) other.itemAt(0));
        } else {
            for(final NodeProxy node : other) {
                add(node);
            }
        }
    }

    /**
     * The method <code>getItemType</code>
     *
     * @return an <code>int</code> value
     */
    @Override
    public int getItemType() {
        return itemType;
    }

    private void checkItemType(final int type) {
        if(itemType == Type.NODE || itemType == type) {
            return;
        }

        if(itemType == Type.ANY_TYPE) {
            itemType = type;
        } else {
            itemType = Type.NODE;
        }
    }

    private void setHasChanged() {
        this.state = (state == Integer.MAX_VALUE ? 0 : state + 1);
    }

    @Override
    public int getLength() {
        if(!isSorted()) {
            // sort to remove duplicates
            sort();
        }
        return size;
    }

    @Override
    public long getItemCountLong() {
        return getLength();
    }

    @Override
    public Node item(final int pos) {
        sortInDocumentOrder();
        final NodeProxy p = get(pos);
        return p == null ? null : p.getNode();
    }

    @Override
    public Item itemAt(final int pos) {
        sortInDocumentOrder();
        return get(pos);
    }

    @Override
    public NodeSet selectParentChild(final NodeSet al, final int mode,
            final int contextId) {
        sort();
        if(al instanceof VirtualNodeSet) {
            return super.selectParentChild(al, mode, contextId);
        }
        return getDescendantsInSet(al, true, false, mode, contextId, true);
    }


    @Override
    public NodeSet selectAncestorDescendant(final NodeSet al, final int mode,
                                            final boolean includeSelf, int contextId, boolean copyMatches) {
        sort();
        if(al instanceof VirtualNodeSet) {
            return super.selectAncestorDescendant(al, mode, includeSelf,
                contextId, copyMatches);
        }
        return getDescendantsInSet(al, false, includeSelf, mode, contextId, copyMatches);
    }

    @Override
    public NodeSet selectAncestors(final NodeSet al, final boolean includeSelf, final int contextId) {
        sort();
        return super.selectAncestors(al, includeSelf, contextId);
    }

    protected abstract NodeSet getDescendantsInSet(final NodeSet al,
            final boolean childOnly, final boolean includeSelf, final int mode,
            final int contextId, final boolean copyMatches);


    @Override
    public DocumentSet getDocumentSet() {
        return this;
    }

    protected boolean isSorted() {
        return isSorted;
    }

    /**
     * Remove all duplicate nodes, but merge their
     * contexts.
     */
    public void mergeDuplicates() {
        sort(true);
    }

    /**
     * Sorts the nodes in the set
     * into document order.
     *
     * Same as calling @see #sort(false)
     */
    public void sortInDocumentOrder() {
        sort(false);
    }

    /**
     * Sorts the nodes in the set
     * without merging their contexts.
     *
     * Same as calling @see #sort(false)
     */
    public void sort() {
        sort(false);
    }

    public abstract void sort(final boolean mergeContexts);

    @Override
    public boolean hasChanged(final int previousState) {
        return state != previousState;
    }


    @Override
    public int getState() {
        return state;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public String toString() {
        return "ArrayNodeSet#" + super.toString();
    }
}

