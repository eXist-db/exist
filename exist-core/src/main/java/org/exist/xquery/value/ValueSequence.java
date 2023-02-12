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

import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.*;
import org.exist.numbering.NodeId;
import org.exist.util.FastQSort;
import org.exist.xquery.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A sequence that may contain a mixture of atomic values and nodes.
 *
 * @author wolf
 */
public class ValueSequence extends AbstractSequence implements MemoryNodeSet {

    //Do not change the -1 value since size computation relies on this start value
    private final static int UNSET_SIZE = -1;
    private final static int INITIAL_SIZE = 64;
    private final static Logger LOG = LogManager.getLogger(ValueSequence.class);
    protected Item[] values;
    protected int size = UNSET_SIZE;

    // used to keep track of the type of added items.
    // will be Type.ANY_TYPE if the typlexe is unknown
    // and Type.ITEM if there are items of mixed type.
    protected int itemType = Type.ANY_TYPE;

    private boolean noDuplicates = false;

    private boolean inMemNodeSet = false;

    private boolean isOrdered = false;

    private boolean enforceOrder = false;

    private boolean keepUnOrdered = false;

    private Variable holderVar = null;

    private int state = 0;

    private NodeSet cachedSet = null;

    public ValueSequence() {
        this(false);
    }

    public ValueSequence(final boolean ordered) {
        this.values = new Item[INITIAL_SIZE];
        this.enforceOrder = ordered;
    }

    public ValueSequence(final int initialSize) {
        this.values = new Item[initialSize];
    }

    public ValueSequence(final Sequence otherSequence) throws XPathException {
        this(otherSequence, false);
    }

    public ValueSequence(final Sequence otherSequence, final boolean ordered) throws XPathException {
        this.values = new Item[otherSequence.getItemCount()];
        addAll(otherSequence);
        this.enforceOrder = ordered;
    }

    public ValueSequence(final Item... items) {
        this.values = new Item[items.length];
        for (final Item item : items) {
            add(item);
        }
    }

    public static <T> ValueSequence of(final FunctionE<T, Item, XPathException> mapper, final T... things) throws XPathException {
        final ValueSequence valueSequence = new ValueSequence(things.length);
        for (final T thing : things) {
            valueSequence.add(mapper.apply(thing));
        }
        return valueSequence;
    }

    public void keepUnOrdered(final boolean flag) {
        keepUnOrdered = flag;
    }

    public void clear() {
        Arrays.fill(values, null);
        this.size = UNSET_SIZE;
        this.itemType = Type.ANY_TYPE;
        this.noDuplicates = false;
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
            hasOne = true;
        }
        cachedSet = null;
        isEmpty = false;
        ++size;
        ensureCapacity();
        values[size] = item;
        if (itemType == item.getType()) {
            return;
        } else if (itemType == Type.ANY_TYPE) {
            itemType = item.getType();
        } else {
            itemType = Type.getCommonSuperType(item.getType(), itemType);
        }
        noDuplicates = false;
        isOrdered = false;
        setHasChanged();
    }

//    public void addAll(ValueSequence otherSequence) throws XPathException {
//        if (otherSequence == null)
//			return;
//        enforceOrder = otherSequence.enforceOrder;
//        for (SequenceIterator i = otherSequence.iterate(); i.hasNext(); ) {
//          add(i.nextItem());
//        }
//    }

    @Override
    public void addAll(final Sequence otherSequence) throws XPathException {
        if (otherSequence == null) {
            return;
        }
        final SequenceIterator iterator = otherSequence.iterate();
        if (iterator == null) {
            LOG.warn("Iterator == null: {}", otherSequence.getClass().getName());
            return;
        }
        while (iterator.hasNext()) {
            add(iterator.nextItem());
        }
    }

    @Override
    public int getItemType() {
        return itemType == Type.ANY_TYPE ? Type.ITEM : itemType;
    }

    @Override
    public SequenceIterator iterate() {
        sortInDocumentOrder();
        return new ValueSequenceIterator();
    }

    @Override
    public SequenceIterator unorderedIterator() {
        sortInDocumentOrder();
        return new ValueSequenceIterator();
    }

    public SequenceIterator iterateInReverse() {
        sortInDocumentOrder();
        return new ReverseValueSequenceIterator();
    }

    public boolean isOrdered() {
        return enforceOrder;
    }

    public void setIsOrdered(final boolean ordered) {
        this.enforceOrder = ordered;
    }

    @Override
    public long getItemCountLong() {
        sortInDocumentOrder();
        return size + 1;
    }

    @Override
    public Item itemAt(final int pos) {
        sortInDocumentOrder();
        return values[pos];
    }

    public void setHolderVariable(final Variable var) {
        this.holderVar = var;
    }

    /**
     * Makes all in-memory nodes in this sequence persistent,
     * so they can be handled like other node sets.
     *
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    @Override
    public NodeSet toNodeSet() throws XPathException {
        if (size == UNSET_SIZE) {
            return NodeSet.EMPTY_SET;
        }
        // for this method to work, all items have to be nodes
        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            final NodeSet set = new NewArrayNodeSet();
            for (int i = 0; i <= size; i++) {
                NodeValue v = (NodeValue) values[i];
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
                        for (int j = i; j <= size; j++) {
                            v = (NodeValue) values[j];
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
                                        throw new XPathException(node == null ? null : node.getExpression(), "Internal error: nodeId == null");
                                    }
                                    if (node.getNodeType() == Node.DOCUMENT_NODE) {
                                        nodeId = rootId;
                                    } else {
                                        nodeId = rootId.append(nodeId);
                                    }
                                    final NodeProxy p = new NodeProxy(node == null ? null : node.getExpression(), newDoc, nodeId, node.getNodeType());
                                    // replace the node by the NodeProxy
                                    values[j] = p;
                                }
                            }
                        }
                        set.add((NodeProxy) values[i]);
                    }
                } else {
                    set.add((NodeProxy) v);
                }
            }
            if (holderVar != null) {
                holderVar.setValue(set);
            }
            return set;
        } else {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        if (size == UNSET_SIZE) {
            return MemoryNodeSet.EMPTY;
        }
        if (itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE)) {
            throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                    " a node set. Item type is " + Type.getTypeName(itemType));
        }
        for (int i = 0; i <= size; i++) {
            final NodeValue v = (NodeValue) values[i];
            if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                throw new XPathException((Expression) null, "Type error: the sequence cannot be converted into" +
                        " a MemoryNodeSet. It contains nodes from stored resources.");
            }
        }
        expand();
        inMemNodeSet = true;
        return this;
    }

    public boolean isInMemorySet() {
        if (size == UNSET_SIZE) {
            return true;
        }
        if (itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE)) {
            return false;
        }
        for (int i = 0; i <= size; i++) {
            final NodeValue v = (NodeValue) values[i];
            if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPersistentSet() {
        if (size == UNSET_SIZE) {
            return true;
        }
        if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            for (int i = 0; i <= size; i++) {
                final NodeValue v = (NodeValue) values[i];
                if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Scan the sequence and check all in-memory documents.
     * They may contains references to nodes stored in the database.
     * Expand those references to get a pure in-memory DOM tree.
     */
    private void expand() {
        final Set<DocumentImpl> docs = new HashSet<>();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            final DocumentImpl ownerDoc = node.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl) node : node.getOwnerDocument();

            if (ownerDoc.hasReferenceNodes()) {
                docs.add(ownerDoc);
            }
        }
        for (final DocumentImpl doc : docs) {
            doc.expand();
        }
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        holderVar = null;
        for (int i = 0; i <= size; i++) {
            values[i].destroy(context, contextSequence);
        }
    }

    @Override
    public boolean containsReference(final Item item) {
        for (int i = 0; i <= size; i++) {
            if (values[i] == item) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(final Item item) {
        for (int i = 0; i <= size; i++) {
            if (values[i].equals(item)) {
                return true;
            }
        }
        return false;
    }

    public void sortInDocumentOrder() {
        if (size == UNSET_SIZE) {
            return;
        }
        if (keepUnOrdered) {
            removeDuplicateNodes();
            return;
        }
        if (!enforceOrder || isOrdered) {
            return;
        }
        inMemNodeSet = inMemNodeSet || isInMemorySet();
        if (inMemNodeSet) {
            FastQSort.sort(values, new InMemoryNodeComparator(), 0, size);
        }
        removeDuplicateNodes();
        isOrdered = true;
    }

    @Override
    public void removeDuplicates() {
        enforceOrder = true;
        isOrdered = false;
        sortInDocumentOrder();
    }

    private void ensureCapacity() {
        if (size == values.length) {
            final int newSize = (int) Math.round((size == 0 ? 1 : size * 3) / (double) 2);
            final Item newValues[] = new Item[newSize];
            System.arraycopy(values, 0, newValues, 0, size);
            values = newValues;
        }
    }

    private void removeDuplicateNodes() {
        if (noDuplicates || size < 1) {
            return;
        }
        if (inMemNodeSet) {
            int j = 0;
            for (int i = 1; i <= size; i++) {
                if (!values[i].equals(values[j])) {
                    if (i != ++j) {
                        values[j] = values[i];
                    }
                }
            }
            size = j;
        } else {
            if (itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.ANY_ATOMIC_TYPE)) {
                return;
            }
            // check if the sequence contains nodes
            boolean hasNodes = false;
            for (int i = 0; i <= size; i++) {
                if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                    hasNodes = true;
                }
            }
            if (!hasNodes) {
                return;
            }
            final Map<Item, Item> nodes = new TreeMap<>(new ItemComparator());
            int j = 0;
            for (int i = 0; i <= size; i++) {
                if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                    final Item found = nodes.get(values[i]);
                    if (found == null) {
                        final Item item = values[i];
                        values[j++] = item;
                        nodes.put(item, item);
                    } else {
                        final NodeValue nv = (NodeValue) found;
                        if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                            ((NodeProxy) nv).addMatches((NodeProxy) values[i]);
                        }
                    }
                } else {
                    values[j++] = values[i];
                }
            }
            size = j - 1;
        }
        noDuplicates = true;
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                ((NodeValue) values[i]).clearContext(contextId);
            }
        }
    }

    public void nodeMoved(final NodeId oldNodeId, final StoredNode newNode) {
        for (int i = 0; i <= size; i++) {
            values[i].nodeMoved(oldNodeId, newNode);
        }
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
    public DocumentSet getDocumentSet() {
        if (cachedSet != null) {
            return cachedSet.getDocumentSet();
        }
        try {
            boolean isPersistentSet = true;
            for (int i = 0; i <= size; i++) {
                if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                    final NodeValue node = (NodeValue) values[i];
                    if (node.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                        isPersistentSet = false;
                        break;
                    }
                } else {
                    isPersistentSet = false;
                    break;
                }
            }
            if (isPersistentSet) {
                cachedSet = toNodeSet();
                return cachedSet.getDocumentSet();
            }
        } catch (final XPathException e) {
        }
        return extractDocumentSet();
    }

    private DocumentSet extractDocumentSet() {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                final NodeValue node = (NodeValue) values[i];
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    docs.add((org.exist.dom.persistent.DocumentImpl) node.getOwnerDocument());
                }
            }
        }
        return docs;
    }

    /* Methods of MemoryNodeSet */
    @Override
    public Sequence getAttributes(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getDescendantAttributes(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendantAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildren(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectChildren(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildrenForParent(final NodeImpl parent) {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.getNodeId().isChildOf(parent.getNodeId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendants(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getAncestors(final boolean includeSelf, final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAncestors(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getParents(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            final NodeImpl parent = (NodeImpl) node.selectParentNode();
            if (parent != null && test.matches(parent)) {
                nodes.add(parent);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getSelf(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                    test.matches(node)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getPrecedingSiblings(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];

            // if the context node is an attribute or namespace node, the preceding-sibling axis is empty
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                node.selectPrecedingSiblings(test, nodes);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getPreceding(final NodeTest test, final int position) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectPreceding(test, nodes, position);
        }
        return nodes;
    }

    @Override
    public Sequence getFollowingSiblings(final NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            // if the context node is an attribute or namespace node, the following-sibling axis is empty
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                node.selectFollowingSiblings(test, nodes);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getFollowing(final NodeTest test, final int position) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectFollowing(test, nodes, position);
        }
        return nodes;
    }

    @Override
    public Sequence selectDescendants(final MemoryNodeSet descendants) {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
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
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
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
    public int size() {
        return size + 1;
    }

    @Override
    public NodeImpl get(final int which) {
        return (NodeImpl) values[which];
    }

    /* END methods of MemoryNodeSet */

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return new CollectionIterator();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("(");
        boolean moreThanOne = false;
        for (final SequenceIterator i = iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (moreThanOne) {
                result.append(", ");
            }
            moreThanOne = true;
            result.append(next.toString());
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public boolean matchSelf(final NodeTest test) {
        //UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                    test.matches(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchChildren(final NodeTest test) throws XPathException {
        //UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchChildren(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchAttributes(final NodeTest test) {
        //UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchAttributes(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        //UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchDescendantAttributes(test)) {
                return true;
            }
        }
        return false;
    }

    private static class InMemoryNodeComparator implements Comparator<Item> {

        @Override
        public int compare(final Item o1, final Item o2) {
            final NodeImpl n1 = (NodeImpl) o1;
            final NodeImpl n2 = (NodeImpl) o2;

            final DocumentImpl n1Doc = n1.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl) n1 : n1.getOwnerDocument();
            final DocumentImpl n2Doc = n2.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl) n2 : n2.getOwnerDocument();

            final int docCmp = n1Doc.compareTo(n2Doc);
            if (docCmp == 0) {
                return n1.getNodeNumber() == n2.getNodeNumber() ? Constants.EQUAL :
                        (n1.getNodeNumber() > n2.getNodeNumber() ? Constants.SUPERIOR : Constants.INFERIOR);
            } else {
                return docCmp;
            }
        }
    }

    private class CollectionIterator implements Iterator<Collection> {
        private Collection nextCollection = null;
        private int pos = 0;

        CollectionIterator() {
            next();
        }

        @Override
        public boolean hasNext() {
            return nextCollection != null;
        }

        @Override
        public Collection next() {
            final Collection oldCollection = nextCollection;
            nextCollection = null;
            while (pos <= size) {
                if (Type.subTypeOf(values[pos].getType(), Type.NODE)) {
                    final NodeValue node = (NodeValue) values[pos];
                    if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeProxy p = (NodeProxy) node;
                        if (!p.getOwnerDocument().getCollection().equals(oldCollection)) {
                            nextCollection = p.getOwnerDocument().getCollection();
                            break;
                        }
                    }
                }
                pos++;
            }
            return oldCollection;
        }
    }

    private class ValueSequenceIterator implements SequenceIterator {
        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos <= size;
        }

        @Override
        public Item nextItem() {
            if (pos <= size) {
                return values[pos++];
            }
            return null;
        }

        @Override
        public long skippable() {
            if (pos <= size) {
                return size - pos + 1;
            }
            return 0;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, pos <= size ? size - pos + 1 : 0);
            pos += skip;
            return skip;
        }
    }

    private class ReverseValueSequenceIterator implements SequenceIterator {
        private int pos = size; // size is not the actual size

        @Override
        public boolean hasNext() {
            return pos >= 0;
        }

        @Override
        public Item nextItem() {
            if (pos >= 0) {
                return values[pos--];
            }
            return null;
        }

        @Override
        public long skippable() {
            if (pos >= 0) {
                return size - pos;
            }
            return 0;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, pos >= 0 ? size - pos : 0);
            pos -= skip;
            return skip;
        }
    }
}
