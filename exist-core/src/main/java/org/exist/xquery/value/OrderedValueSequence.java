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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private final List<OrderSpec> orderSpecs;
    private final List<BitSet> encounteredPrimitiveTypesForOrderSpecs;
    private Entry[] items;
    private int count = 0;
    private int state = 0;

    // used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;
    private Sequence contextSequence;

    public OrderedValueSequence(final List<OrderSpec> orderSpecs, final int size) {
        this.orderSpecs = orderSpecs;
        this.encounteredPrimitiveTypesForOrderSpecs = new ArrayList<>(orderSpecs.size());
        for (int i = 0; i < orderSpecs.size(); i++) {
            this.encounteredPrimitiveTypesForOrderSpecs.add(new BitSet(Type.ARRAY_ITEM + 1));
        }
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
        items[count] = Entry.create(encounteredPrimitiveTypesForOrderSpecs, orderSpecs, item, count++, contextSequence);
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

    /**
     * Coerce the types as required by <a href="https://www.w3.org/TR/xquery-31/#id-order-by-clause">Order By Clause</a>
     * in the XQuery 3.1 specification before sorting them.
     *
     * @throws XPathException if the types cannot be coerced as according to the spec.
     */
    public void coerceTypesForOrderBy() throws XPathException {
        for (int t = 0; t < encounteredPrimitiveTypesForOrderSpecs.size(); t++) {
            final BitSet encounteredPrimitiveTypesForOrderSpec = encounteredPrimitiveTypesForOrderSpecs.get(t);
            final int valueTypeCardinality = encounteredPrimitiveTypesForOrderSpec.cardinality();
            if (valueTypeCardinality > 1) {

                int coerceToType = -1;

                /*
                 If the resulting sequence contains values that are instances of more than one primitive type
                 */

                if (valueTypeCardinality == 2 && countSetBits(encounteredPrimitiveTypesForOrderSpec, Type.STRING, Type.ANY_URI) == 2) {
                    /*
                     If each value is an instance of one of the types xs:string or xs:anyURI, then all values are cast to type xs:string.
                     */
                    coerceToType = Type.STRING;


                } else if (valueTypeCardinality == 2 && countSetBits(encounteredPrimitiveTypesForOrderSpec, Type.DECIMAL, Type.FLOAT) == 2) {
                    /*
                     If each value is an instance of one of the types xs:decimal or xs:float, then all values are cast to type xs:float.
                     */
                    coerceToType = Type.FLOAT;

                } else if (valueTypeCardinality <= 3 && countSetBits(encounteredPrimitiveTypesForOrderSpec, Type.DECIMAL, Type.FLOAT, Type.DOUBLE) >= 2) {
                    /*
                    If each value is an instance of one of the types xs:decimal, xs:float, or xs:double, then all values are cast to type xs:double.
                     */
                    coerceToType = Type.DOUBLE;

                } else {
                    // Otherwise, a type error is raised [err:XPTY0004].
                    final StringBuilder message = new StringBuilder("OrderSpec contains a mix of primitive types which can not be compared for sorting: [");
                    try (final IntStream encounteredPrimitiveTypesStream = encounteredPrimitiveTypesForOrderSpec.stream()) {
                        message.append(
                                encounteredPrimitiveTypesStream
                                        .mapToObj(Type::getTypeName)
                                        .collect(Collectors.joining(", "))
                        );
                    }
                    message.append(']');
                    final Expression expression = (items != null && items.length >= 1 && items[0] != null && items[0].item != null) ? items[0].item.getExpression() : null;
                    throw new XPathException(expression, ErrorCodes.XPTY0004, message.toString());
                }

                // perform the coercion
                for (int i = 0; i < count; i++) {
                    final Entry item = items[i];
                    final AtomicValue value = item.values.get(t);
                    item.values.set(t, value.convertTo(coerceToType));
                }
                checkItemType(coerceToType);
            }
        }
    }

    private int countSetBits(final BitSet bitSet, final int... bitIndex) {
        int setBitsCount = 0;
        for (int index : bitIndex) {
            if (bitSet.get(index)) {
                setBitsCount++;
            }
        }
        return setBitsCount;
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

    /**
     * Set the Context Sequence.
     * This is useful if it is needed to evaluate the {@link #orderSpecs}.
     *
     * @param contextSequence the context sequence
     */
    public void setContextSequence(@Nullable final Sequence contextSequence) {
        this.contextSequence = contextSequence;
    }

    private static class Entry implements Comparable<Entry> {
        private final List<BitSet> encounteredPrimitiveTypesForOrderSpecs;
        private final List<OrderSpec> orderSpecs;
        private Item item;
        private final int pos;
        @Nullable private List<AtomicValue> values;

        /**
         * Private constructor, use {@link #create(List, List, Item, int, Sequence)} instead.
         *
         * @param encounteredPrimitiveTypesForOrderSpecs a list of bitset which will be populated with the primitive type of each value in the entry of each orderspec
         * @param item the item in the sequence.
         * @param position the original position of the item in the result sequence.
         * @param values the values for the entry.
         */
        private Entry(final List<BitSet> encounteredPrimitiveTypesForOrderSpecs, final List<OrderSpec> orderSpecs, final Item item, final int position, final List<AtomicValue> values) {
            this.encounteredPrimitiveTypesForOrderSpecs = encounteredPrimitiveTypesForOrderSpecs;
            this.orderSpecs = orderSpecs;
            this.item = item;
            this.pos = position;
            this.values = values;
        }

        /**
         * Create an Entry.
         *
         * @param encounteredPrimitiveTypesForOrderSpecs a list of bitset which will be populated with the primitive type of each value in the entry of each orderspec
         * @param orderSpecs the ordering specifications.
         * @param item the item in the sequence.
         * @param position the original position of the item in the result sequence.
         * @param contextSequence the context sequence if required for evaluating the ordering specifications, else null.
         *
         * @throws XPathException thrown if the evaluation of an order spec raises an error.
         */
        public static Entry create(final List<BitSet> encounteredPrimitiveTypesForOrderSpecs, final List<OrderSpec> orderSpecs, final Item item, final int position, @Nullable final Sequence contextSequence) throws XPathException {
            final List<AtomicValue> values = new ArrayList<>(orderSpecs.size());
            for (int i = 0; i < orderSpecs.size(); i++) {
                final OrderSpec orderSpec = orderSpecs.get(i);
                final Expression sortExpression = orderSpec.getSortExpression();
                final Sequence seq = sortExpression.eval(contextSequence, null);
                if (seq.hasOne()) {
                    AtomicValue value = seq.itemAt(0).atomize();
                    int valueType = value.getType();

                    /*
                     If the value of an orderspec has the dynamic type xs:untypedAtomic (such as character data in a
                     schemaless document), it is cast to the type xs:string.
                     */
                    if (Type.UNTYPED_ATOMIC == valueType) {
                        value = value.convertTo(Type.STRING);
                        valueType = Type.STRING;
                    }

                    final BitSet encounteredPrimitiveTypesForOrderSpec = encounteredPrimitiveTypesForOrderSpecs.get(i);
                    encounteredPrimitiveTypesForOrderSpec.set(Type.primitiveTypeOf(valueType));
                    values.add(value);

                } else if (seq.hasMany()) {
                    throw new XPathException(item.getExpression(), ErrorCodes.XPTY0004,
                            "expected a single value for order expression " +
                                    ExpressionDumper.dump(sortExpression) +
                                    " ; found: " + seq.getItemCount());
                } else {
                    values.add(AtomicValue.EMPTY_VALUE);
                }
            }

            return new Entry(encounteredPrimitiveTypesForOrderSpecs, orderSpecs, item, position, values);
        }

        @Override
        public int compareTo(final Entry other) {
            int cmp = 0;
            for (int i = 0; i < values.size(); i++) {
                try {
                    final AtomicValue a = values.get(i);
                    final AtomicValue b = other.values.get(i);

                    final boolean aIsEmpty = (a.isEmpty() || (Type.subTypeOfUnion(a.getType(), Type.NUMERIC) && ((NumericValue) a).isNaN()));
                    final boolean bIsEmpty = (b.isEmpty() || (Type.subTypeOfUnion(b.getType(), Type.NUMERIC) && ((NumericValue) b).isNaN()));
                    if (aIsEmpty) {
                        if (bIsEmpty)
                        // both values are empty
                        {
                            return Constants.EQUAL;
                        } else if ((orderSpecs.get(i).getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.INFERIOR;
                        } else {
                            cmp = Constants.SUPERIOR;
                        }
                    } else if (bIsEmpty) {
                        // we don't need to check for equality since we know a is not empty
                        if ((orderSpecs.get(i).getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.SUPERIOR;
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                    } else if (a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs.get(i).getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.INFERIOR;
                        } else {
                            cmp = Constants.SUPERIOR;
                        }
                    } else if (b == AtomicValue.EMPTY_VALUE && a != AtomicValue.EMPTY_VALUE) {
                        if ((orderSpecs.get(i).getModifiers() & OrderSpec.EMPTY_LEAST) != 0) {
                            cmp = Constants.SUPERIOR;
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                    } else {
                        cmp = a.compareTo(orderSpecs.get(i).getCollator(), b);
                    }
                    if ((orderSpecs.get(i).getModifiers() & OrderSpec.DESCENDING_ORDER) != 0) {
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
            if (values == null) {
                return item.toString();
            } else {
                final StringBuilder builder = new StringBuilder();
                builder.append(item);
                builder.append(" [");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(values.get(i).toString());
                }
                builder.append("]");
                return builder.toString();
            }
        }

        public void clear() {
            for (final BitSet encounteredPrimitiveTypesForOrderSpec : encounteredPrimitiveTypesForOrderSpecs) {
                encounteredPrimitiveTypesForOrderSpec.clear();
            }
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
