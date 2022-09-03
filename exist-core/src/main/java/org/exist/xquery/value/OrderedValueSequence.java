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

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.AVLTreeNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.Arrays;

/**
 * A sequence that sorts its entries in the order specified by the order specs of
 * an "order by" clause. Used by {@link org.exist.xquery.ForExpr}.
 *
 * Contrary to class {@link org.exist.xquery.value.PreorderedValueSequence},
 * all order expressions are evaluated once for each item in the sequence
 * <b>while</b> items are added.
 *
 * @author wolf
 */
public class OrderedValueSequence extends AbstractSequence {

    private final OrderSpec[] orderSpecs;
    private Entry[] items;
    private int count = 0;
    private int state = 0;

    // used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;

    public OrderedValueSequence(final OrderSpec orderSpecs[], final int size) {
        this.orderSpecs = orderSpecs;
        this.items = new Entry[size == 0 ? 1 : size];
    }

    @Override
    public SequenceIterator iterate() {
        return new OrderedValueSequenceIterator();
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new OrderedValueSequenceIterator();
    }

    @Override
    public long getItemCountLong() {
        return (items == null) ? 0 : count;
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public boolean hasOne() {
        return hasOne;
    }

    @Override
    public void add(final Item item) throws XPathException {
        if (hasOne) {
            hasOne = false;
        }
        if (isEmpty) {
            hasOne = true;
        }
        isEmpty = false;
        if (count == 0 && items.length == 1) {
            items = new Entry[2];
        } else if (count == items.length) {
            final Entry newItems[] = new Entry[count * 2];
            System.arraycopy(items, 0, newItems, 0, count);
            items = newItems;
        }
        items[count] = new Entry(item, count++);
        checkItemType(item.getType());
        setHasChanged();
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        if (other.hasOne()) {
            add(other.itemAt(0));
        } else if (!other.isEmpty()) {
            for (final SequenceIterator i = other.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (next != null) {
                    add(next);
                }
            }
        }
    }

    public void sort() {
//		FastQSort.sort(items, 0, count - 1);

        Arrays.parallelSort(items, 0, count);
        Arrays.stream(items, 0, count).parallel().forEach(Entry::clear);
    }

    @Override
    public Item itemAt(final int pos) {
        if (items != null && pos > -1 && pos < count) {
            return items[pos].item;
        } else {
            return null;
        }
    }

    private void checkItemType(final int type) {
        if (itemType == type) {
            return;
        } else if (itemType == Type.ANY_TYPE) {
            itemType = type;
        } else {
            itemType = Type.getCommonSuperType(type, itemType);
        }
    }

    @Override
    public int getItemType() {
        return itemType;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        //return early
        if (isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        // for this method to work, all items have to be nodes
        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            //Was ExtArrayNodeset() which orders the nodes in document order
            //The order seems to change between different invocations !!!
            final NodeSet set = new AVLTreeNodeSet();
            //We can't make it from an ExtArrayNodeSet (probably because it is sorted ?)
            //NodeSet set = new ArraySet(100);
            for (int i = 0; i < count; i++) {
                NodeValue v = (NodeValue) items[i].item;
                if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {

                    // found an in-memory document
                    final org.exist.dom.memtree.DocumentImpl doc = v.getType() == Type.DOCUMENT ? (org.exist.dom.memtree.DocumentImpl)v : ((NodeImpl) v).getOwnerDocument();
                    if (doc == null) {
                        continue;
                    }
                    // make this document persistent: doc.makePersistent()
                    // returns a map of all root node ids mapped to the corresponding
                    // persistent node. We scan the current sequence and replace all
                    // in-memory nodes with their new persistent node objects.
                    final DocumentImpl expandedDoc = doc.expandRefs(null);
                    final org.exist.dom.persistent.DocumentImpl newDoc = expandedDoc.makePersistent();
                    if (newDoc != null) {
                        final NodeId rootId = newDoc.getBrokerPool().getNodeFactory().createInstance();
                        for (int j = i; j < count; j++) {
                            v = (NodeValue) items[j].item;
                            if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                                NodeImpl node = (NodeImpl) v;
                                final Document nodeOwnerDoc = node.getNodeType() == Node.DOCUMENT_NODE ? (org.exist.dom.memtree.DocumentImpl)v : ((NodeImpl) v).getOwnerDocument();

                                if (nodeOwnerDoc == doc) {
                                    node = expandedDoc.getNode(node.getNodeNumber());
                                    NodeId nodeId = node.getNodeId();
                                    if (nodeId == null) {
                                        throw new XPathException((Expression) null, "Internal error: nodeId == null");
                                    }
                                    if (node.getNodeType() == Node.DOCUMENT_NODE) {
                                        nodeId = rootId;
                                    } else {
                                        nodeId = rootId.append(nodeId);
                                    }
                                    final NodeProxy p = new NodeProxy(null, newDoc, nodeId, node.getNodeType());
                                    if (p != null) {
                                        // replace the node by the NodeProxy
                                        items[j].item = p;
                                    }
                                }
                            }
                        }
                    }
                    set.add((NodeProxy) items[i].item);
                } else {
                    set.add((NodeProxy) v);
                }
            }
            return set;
        } else {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
    }

    @Override
    public boolean isPersistentSet() {
        if (count == 0) {
            return true;
        }
        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            for (int i = 0; i < count; i++) {
                final NodeValue v = (NodeValue) items[i].item;
                if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        if (count == 0) {
            return MemoryNodeSet.EMPTY;
        }
        if (itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE)) {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
        for (int i = 0; i < count; i++) {
            final NodeValue v = (NodeValue) items[i].item;
            if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                return null;
            }
        }
        return new ValueSequence(this);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(items[i].toString());
        }
        return builder.toString();
    }

    @Override
    public void removeDuplicates() {
        // TODO: is this ever relevant?
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 0 : state + 1);
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return state != previousState;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public boolean containsReference(final Item item) {
        for (final SequenceIterator it = iterate(); it.hasNext(); ) {
            final Item i = it.nextItem();
            if (i == item) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(final Item item) {
        for (final SequenceIterator it = iterate(); it.hasNext(); ) {
            final Item i = it.nextItem();
            if (i.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private class Entry implements Comparable<Entry> {
        Item item;
        AtomicValue values[];
        private final int pos;

        /**
         * @param item     the item in the sequence
         * @param position the original position of the item in the result sequence
         * @throws XPathException
         */
        public Entry(final Item item, final int position) throws XPathException {
            this.item = item;
            this.pos = position;
            values = new AtomicValue[orderSpecs.length];
            for (int i = 0; i < orderSpecs.length; i++) {
                final Sequence seq = orderSpecs[i].getSortExpression().eval(null, null);
                values[i] = AtomicValue.EMPTY_VALUE;
                if (seq.hasOne()) {
                    values[i] = seq.itemAt(0).atomize();
                } else if (seq.hasMany()) {
                    throw new XPathException((values[i] == null) ? null : values[i].getExpression(), ErrorCodes.XPTY0004,
                            "expected a single value for order expression " +
                            ExpressionDumper.dump(orderSpecs[i].getSortExpression()) +
                            " ; found: " + seq.getItemCount());
                }
            }
        }

        @Override
        public int compareTo(final Entry other) {
            int cmp = 0;
            for (int i = 0; i < values.length; i++) {
                try {
                    final AtomicValue a = values[i];
                    final AtomicValue b = other.values[i];
                    final boolean aIsEmpty = (a.isEmpty() || (Type.subTypeOfUnion(a.getType(), Type.NUMERIC) && ((NumericValue) a).isNaN()));
                    final boolean bIsEmpty = (b.isEmpty() || (Type.subTypeOfUnion(b.getType(), Type.NUMERIC) && ((NumericValue) b).isNaN()));
                    if (aIsEmpty) {
                        if (bIsEmpty)
                        // both values are empty
                        {
                            return Constants.EQUAL;
                        } else if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.INFERIOR;
                        } else {
                            cmp = Constants.SUPERIOR;
                        }
                    } else if (bIsEmpty) {
                        // we don't need to check for equality since we know a is not empty
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.SUPERIOR;
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                    } else if (a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.INFERIOR;
                        } else {
                            cmp = Constants.SUPERIOR;
                        }
                    } else if (b == AtomicValue.EMPTY_VALUE && a != AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.SUPERIOR;
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                    } else {
                        cmp = a.compareTo(orderSpecs[i].getCollator(), b);
                    }
                    if ((orderSpecs[i].getModifiers() & OrderSpec.DESCENDING_ORDER) != 0) {
                        cmp = cmp * -1;
                    }
                    if (cmp != Constants.EQUAL) {
                        break;
                    }
                } catch (final XPathException e) {
                }
            }
            // if the sort keys are equal, we need to order by the original position in the result sequence
            if (cmp == Constants.EQUAL) {
                cmp = (pos > other.pos ? Constants.SUPERIOR : (pos == other.pos ? Constants.EQUAL : Constants.INFERIOR));
            }
            return cmp;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append(item);
            builder.append(" [");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(values[i].toString());
            }
            builder.append("]");
            return builder.toString();
        }

        public void clear() {
            values = null;
        }
    }

    private class OrderedValueSequenceIterator implements SequenceIterator {
        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos < count;
        }

        @Override
        public Item nextItem() {
            if (pos < count) {
                return items[pos++].item;
            }
            return null;
        }

        @Override
        public long skippable() {
            if (pos < count) {
                return count - pos;
            }
            return 0;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, pos < count ? count - pos : 0);
            pos += skip;
            return skip;
        }
    }
}
