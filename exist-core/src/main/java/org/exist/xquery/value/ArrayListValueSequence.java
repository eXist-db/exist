/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Alternative to {@link ValueSequence}, this version
 * is much faster, but it does not support sorting!
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ArrayListValueSequence extends AbstractSequence implements MemoryNodeSet {

    private final static Logger LOG = LogManager.getLogger(ArrayListValueSequence.class);

    private final List<Item> values;

    // used to keep track of the type of added items.
    // will be Type.ANY_TYPE if the type is unknown
    // and Type.ITEM if there are items of mixed type.
    private int itemType = Type.ANY_TYPE;

    private int state = 0;

    public ArrayListValueSequence() {
        this.isEmpty = true;
        this.hasOne = false;
        this.values = new ArrayList<>();
    }

    public ArrayListValueSequence(final int initialSize) {
        this.isEmpty = true;
        this.hasOne = false;
        this.values = new ArrayList<>(initialSize);
    }

    public ArrayListValueSequence(final Sequence otherSequence) throws XPathException {
        this.isEmpty = true;
        this.hasOne = false;
        this.values = new ArrayList<>(otherSequence.getItemCount());
        addAll(otherSequence);
    }

    public ArrayListValueSequence(final Item... items) {
        this.isEmpty = true;
        this.hasOne = false;
        this.values = new ArrayList<>(items.length);
        addAll(Arrays.asList(items), null);
    }

    public void clear() {
        if (!isEmpty) {
            setHasChanged();
        }

        values.clear();
        this.itemType = Type.ANY_TYPE;
        this.isEmpty = true;
        this.hasOne = false;
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
    public void add(final Item item) {
        if (hasOne) {
            hasOne = false;
        }
        if (isEmpty) {
            isEmpty = false;
            hasOne = true;
        }

        values.add(item);

        if (itemType == Type.ANY_TYPE) {
            itemType = item.getType();
        } else if (itemType != item.getType()) {
            itemType = Type.getCommonSuperType(item.getType(), itemType);
        }

        setHasChanged();
    }

    private void addAll(final List<Item> items, @Nullable final Integer knownType) {
        if (!items.isEmpty()) {
            if (hasOne) {
                hasOne = false;
            }
            if (isEmpty) {
                isEmpty = false;
                hasOne = true;
            }

            values.addAll(items);

            if (knownType != null) {
                if (itemType == Type.ANY_TYPE) {
                    itemType = knownType;
                } else if (itemType != knownType) {
                    itemType = Type.getCommonSuperType(knownType, itemType);
                }
            } else {
                for (final Item item : items) {
                    if (itemType == Type.ITEM) {
                        // stop, already as loose as possible
                        break;
                    }

                    if (itemType == Type.ANY_TYPE) {
                        itemType = item.getType();
                    } else if (itemType != item.getType()) {
                        itemType = Type.getCommonSuperType(item.getType(), itemType);
                    }
                }
            }
        }
    }

    @Override
    public void addAll(final Sequence otherSequence) throws XPathException {
        if (otherSequence == null || otherSequence.isEmpty()) {
            return;
        }

        if (otherSequence instanceof ArrayListValueSequence other) {
            addAll(other.values, other.itemType);

        } else {
            final SequenceIterator iterator = otherSequence.iterate();
            if (iterator == null) {
                LOG.warn("Iterator == null: {}", otherSequence.getClass().getName());
                return;
            }
            while (iterator.hasNext()) {
                add(iterator.nextItem());
            }
        }

        setHasChanged();
    }

    @Override
    public int getItemType() {
        return itemType == Type.ANY_TYPE ? Type.ITEM : itemType;
    }

    @Override
    public SequenceIterator iterate() {
        return new ArrayListValueSequenceIterator(values.iterator());
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new ArrayListValueSequenceIterator(values.iterator());
    }

    @Override
    public long getItemCountLong() {
        return values.size();
    }

    @Override
    public Item itemAt(final int pos) {
        return values.get(pos);
    }

    @Override
    public boolean isPersistentSet() {
        if (isEmpty) {
            return true;
        }

        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            for (final Item value : values) {
                if (((NodeValue)value).getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean containsReference(final Item item) {
        for (final Item value : values) {
            if (value == item) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(final Item item) {
        return values.contains(item);
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        for (final Item value : values) {
            value.destroy(context, contextSequence);
        }
    }

    @Override
    public void removeDuplicates() {
        final List<Item> newValues = new ArrayList<>(values.size());
        int newType = Type.ANY_TYPE;

        final ItemComparator itemComparator = new ItemComparator();

        for (int i = 0; i < values.size(); i++) {
            final Item value = values.get(i);
            boolean foundDuplicate = false;

            if (Type.subTypeOf(value.getType(), Type.NODE)) {
                // look for a duplicate node
                for (int j = i + 1; j < values.size(); j++) {
                    final Item otherValue = values.get(j);
                    if (Type.subTypeOf(otherValue.getType(), Type.NODE)) {
                        if (itemComparator.compare(value, otherValue) == 0) {
                            foundDuplicate = true;
                            break;  // exit j loop
                        }
                    }
                }
            }

            if (!foundDuplicate) {
                newValues.add(value);
                if (newType == Type.ANY_TYPE) {
                    newType = value.getType();
                } else if (newType != value.getType()) {
                    newType = Type.getCommonSuperType(value.getType(), newType);
                }
            }
        }

        values.clear();
        addAll(newValues, newType);
        setHasChanged();
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        for (final Item value : values) {
            if (Type.subTypeOf(value.getType(), Type.NODE)) {
                ((NodeValue) value).clearContext(contextId);
            }
        }
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 1 : state + 1);
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
    public NodeSet toNodeSet() throws XPathException {
        if (isEmpty) {
            return NodeSet.EMPTY_SET;
        }

        // for this method to work, all items have to be nodes
        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            final NodeSet set = new NewArrayNodeSet();
            for (int i = 0; i <= values.size(); i++) {
                NodeValue v = (NodeValue) values.get(i);
                if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    // found an in-memory document
                    final DocumentImpl doc;
                    if (v.getType() == Type.DOCUMENT) {
                        doc = (DocumentImpl) v;
                    } else {
                        doc = ((NodeImpl) v).getOwnerDocument();
                    }

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
                        NodeId rootId = newDoc.getBrokerPool().getNodeFactory().createInstance();
                        for (int j = i; j <= values.size(); j++) {
                            v = (NodeValue) values.get(j);
                            if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                                NodeImpl node = (NodeImpl) v;
                                final Document nodeOwnerDoc;
                                if (node.getNodeType() == Node.DOCUMENT_NODE) {
                                    nodeOwnerDoc = (Document) node;
                                } else {
                                    nodeOwnerDoc = node.getOwnerDocument();
                                }

                                if (nodeOwnerDoc == doc) {
                                    if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                                        node = expandedDoc.getAttribute(node.getNodeNumber());
                                    } else {
                                        node = expandedDoc.getNode(node.getNodeNumber());
                                    }
                                    NodeId nodeId = node.getNodeId();
                                    if (nodeId == null) {
                                        throw new XPathException((Expression) null, "Internal error: nodeId == null");
                                    }
                                    if (node.getNodeType() == Node.DOCUMENT_NODE) {
                                        nodeId = rootId;
                                    } else {
                                        nodeId = rootId.append(nodeId);
                                    }
                                    final NodeProxy p = new NodeProxy(node.getExpression(), newDoc, nodeId, node.getNodeType());
                                    // replace the node by the NodeProxy
                                    values.set(j, p);

                                    setHasChanged();
                                }
                            }
                        }
                        set.add((NodeProxy) values.get(i));
                    }
                } else {
                    set.add((NodeProxy) v);
                }
            }
//            if (holderVar != null) {
//                holderVar.setValue(set);
//            }
            return set;
        } else {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        if (isEmpty) {
            return MemoryNodeSet.EMPTY;
        }

        if (itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE)) {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
        for (final Item value : values) {
            final NodeValue v = (NodeValue) value;
            if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                        " a MemoryNodeSet. It contains nodes from stored resources.");
            }
        }
        expand();
        return this;
    }

    /**
     * Scan the sequence and check all in-memory documents.
     * They may contains references to nodes stored in the database.
     * Expand those references to get a pure in-memory DOM tree.
     */
    private void expand() {
        final Set<DocumentImpl> docs = new HashSet<>();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            final DocumentImpl ownerDoc = node.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl) node : node.getOwnerDocument();

            if (ownerDoc.hasReferenceNodes()) {
                docs.add(ownerDoc);
            }
        }
        for (final DocumentImpl doc : docs) {
            doc.expand();
        }
    }

    private static class ArrayListValueSequenceIterator implements SequenceIterator {
        private final Iterator<Item> iterator;

        public ArrayListValueSequenceIterator(final Iterator<Item> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Item nextItem() {
            if (!hasNext()) {
                return null;
            }
            return iterator.next();
        }
    }

    // <editor-fold desc="Methods of MemoryNodeSet">
    @Override
    public Sequence getAttributes(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getDescendantAttributes(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectDescendantAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildren(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectChildren(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildrenForParent(final NodeImpl parent) {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if (node.getNodeId().isChildOf(parent.getNodeId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectDescendants(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getAncestors(final boolean includeSelf, final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectAncestors(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getParents(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            final NodeImpl parent = (NodeImpl) node.selectParentNode();
            if (parent != null && test.matches(parent)) {
                nodes.add(parent);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getSelf(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                    test.matches(node)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getPrecedingSiblings(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;

            // if the context node is an attribute or namespace node, the preceding-sibling axis is empty
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                node.selectPrecedingSiblings(test, nodes);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getPreceding(final NodeTest test, final int position) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectPreceding(test, nodes, position);
        }
        return nodes;
    }

    @Override
    public Sequence getFollowingSiblings(final NodeTest test) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            // if the context node is an attribute or namespace node, the following-sibling axis is empty
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                node.selectFollowingSiblings(test, nodes);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getFollowing(final NodeTest test, final int position) throws XPathException {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            node.selectFollowing(test, nodes, position);
        }
        return nodes;
    }

    @Override
    public Sequence selectDescendants(final MemoryNodeSet descendants) {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            for (int j = 0; j < descendants.size(); j++) {
                final NodeImpl descendant = descendants.get(j);
                if (descendant.getNodeId().isDescendantOrSelfOf(node.getNodeId())) {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    @Override
    public Sequence selectChildren(final MemoryNodeSet children) {
        final ArrayListValueSequence nodes = new ArrayListValueSequence();
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            for (int j = 0; j < children.size(); j++) {
                final NodeImpl descendant = children.get(j);
                if (descendant.getNodeId().isChildOf(node.getNodeId())) {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }


    @Override
    public boolean matchSelf(final NodeTest test) {
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                    test.matches(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchChildren(final NodeTest test) throws XPathException {
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if (node.matchChildren(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchAttributes(final NodeTest test) {
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if (node.matchAttributes(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        for (final Item value : values) {
            final NodeImpl node = (NodeImpl) value;
            if (node.matchDescendantAttributes(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public NodeImpl get(final int which) {
        return (NodeImpl) values.get(which);
    }

    // </editor-fold>
}
