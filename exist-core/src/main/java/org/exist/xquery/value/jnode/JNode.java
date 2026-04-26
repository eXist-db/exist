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
package org.exist.xquery.value.jnode;

import org.exist.collections.Collection;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.Lookup;
import org.exist.xquery.XQueryContext;
import org.xml.sax.SAXException;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * JNode — an XQuery 4.0 JSON node.
 *
 * JNodes provide a node view over JSON data (maps and arrays). Unlike maps and
 * arrays (which are function items), JNodes are true nodes that can be navigated
 * with XPath axes, matched with node tests, and compared with node identity.
 *
 * This follows BaseX's single-class approach: one JNode class whose JSON kind
 * (object, array, string, number, boolean, null, member) is determined dynamically
 * from its value's XDM type.
 *
 * @see <a href="https://qt4cg.org/specifications/xquery-40/Overview.html#id-json-node-constructors">
 *      XQuery 4.0 §3.12 JSON Node Constructors</a>
 */
public class JNode implements GNode, Sequence, Lookup.LookupSupport {

    /** The member key (null for root nodes and array items). */
    private final AtomicValue key;

    /** The underlying value (map, array, string, number, boolean, or null). */
    private final Sequence value;

    /** Parent node (null for root). */
    private final JNode parent;

    /** Position among siblings (1-based). */
    private final int position;

    /**
     * Root node constructor — wraps a map or array as a JNode tree root.
     *
     * @param value the map or array to wrap
     */
    public JNode(final Sequence value) {
        this(null, value, null, 0);
    }

    /**
     * Child node constructor.
     *
     * @param key      member key (null for array items)
     * @param value    the value
     * @param parent   parent node
     * @param position 1-based position among siblings
     */
    public JNode(final AtomicValue key, final Sequence value, final JNode parent, final int position) {
        this.key = key;
        this.value = value;
        this.parent = parent;
        this.position = position;
    }

    // ===================== JSON kind detection =====================

    /**
     * Returns the JSON kind of this node as an eXist type constant.
     */
    public int getJsonKind() {
        if (key != null && isContainer()) {
            // A keyed node whose value is a container is a member node
            return Type.JSON_MEMBER;
        }
        if (value instanceof AbstractMapType) {
            return Type.JSON_OBJECT;
        }
        if (value instanceof ArrayType) {
            return Type.JSON_ARRAY;
        }
        if (value == Sequence.EMPTY_SEQUENCE || (value instanceof Item && ((Item) value).getType() == Type.EMPTY_SEQUENCE)) {
            return Type.JSON_NULL;
        }
        if (value instanceof Item) {
            final int t = ((Item) value).getType();
            if (Type.subTypeOf(t, Type.STRING) || t == Type.UNTYPED_ATOMIC) {
                return Type.JSON_STRING;
            }
            if (Type.subTypeOf(t, Type.NUMERIC)) {
                return Type.JSON_NUMBER;
            }
            if (t == Type.BOOLEAN) {
                return Type.JSON_BOOLEAN;
            }
        }
        // Default: treat as string
        return Type.JSON_STRING;
    }

    /**
     * Returns true if this node's value is a container (map or array)
     * that can have children.
     */
    public boolean isContainer() {
        return value instanceof AbstractMapType || value instanceof ArrayType;
    }

    /**
     * Returns true if this is a root node (no parent).
     */
    public boolean isRoot() {
        return parent == null;
    }

    // ===================== Accessors =====================

    /**
     * Returns the member key, or null for root/array items.
     */
    public AtomicValue getKey() {
        return key;
    }

    /**
     * Returns the underlying value.
     */
    public Sequence getValue() {
        return value;
    }

    /**
     * Returns the parent node, or null for root.
     */
    public JNode getParent() {
        return parent;
    }

    /**
     * Returns the 1-based position among siblings.
     */
    public int getPosition() {
        return position;
    }

    // ===================== Child axis =====================

    /**
     * Returns the children of this node.
     *
     * For object nodes: one child per member (key-value pair).
     * For array nodes: one child per array item.
     * For leaf nodes: empty list.
     */
    public List<JNode> getChildren() throws XPathException {
        final List<JNode> children = new ArrayList<>();
        if (value instanceof AbstractMapType) {
            final AbstractMapType map = (AbstractMapType) value;
            final Sequence keys = map.keys();
            for (final SequenceIterator it = keys.iterate(); it.hasNext(); ) {
                final AtomicValue k = (AtomicValue) it.nextItem();
                final Sequence v = map.get(k);
                // Map member positions are always 1 — JSON keys are unique within an object,
                // so each member is the only node with its key (W3C XQ4 fn:jposition spec).
                children.add(new JNode(k, v, this, 1));
            }
        } else if (value instanceof ArrayType) {
            final ArrayType array = (ArrayType) value;
            for (int i = 0; i < array.getSize(); i++) {
                final Sequence v = array.get(i);
                children.add(new JNode(null, v, this, i + 1));
            }
        }
        return children;
    }

    /**
     * Returns the number of children.
     */
    public int getChildCount() {
        if (value instanceof AbstractMapType) {
            return ((AbstractMapType) value).size();
        }
        if (value instanceof ArrayType) {
            return ((ArrayType) value).getSize();
        }
        return 0;
    }

    /**
     * Returns true if this node has children.
     */
    public boolean hasChildren() {
        return getChildCount() > 0;
    }

    // ===================== Sibling axis =====================

    /**
     * Returns the following siblings of this node.
     */
    public List<JNode> getFollowingSiblings() throws XPathException {
        if (parent == null) {
            return List.of();
        }
        final List<JNode> siblings = parent.getChildren();
        final List<JNode> result = new ArrayList<>();
        boolean found = false;
        for (final JNode sibling : siblings) {
            if (found) {
                result.add(sibling);
            } else if (isSameSibling(sibling)) {
                found = true;
            }
        }
        return result;
    }

    /**
     * Returns the preceding siblings of this node.
     */
    public List<JNode> getPrecedingSiblings() throws XPathException {
        if (parent == null) {
            return List.of();
        }
        final List<JNode> siblings = parent.getChildren();
        final List<JNode> result = new ArrayList<>();
        for (final JNode sibling : siblings) {
            if (isSameSibling(sibling)) {
                break;
            }
            result.add(sibling);
        }
        return result;
    }

    /**
     * Sibling identity check that handles map members (uniquely identified by key)
     * and array items (uniquely identified by position).
     */
    private boolean isSameSibling(final JNode other) throws XPathException {
        if (key != null && other.key != null) {
            return key.compareTo(null, other.key) == 0;
        }
        return position == other.position;
    }

    // ===================== Following / Preceding axis =====================

    /**
     * Returns all nodes following this node in document order (excluding descendants).
     * following = following-siblings and their descendants, then following of parent (recursive).
     */
    public List<JNode> getFollowing() throws XPathException {
        final List<JNode> result = new ArrayList<>();
        collectFollowing(this, result);
        return result;
    }

    private void collectFollowing(final JNode node, final List<JNode> result) throws XPathException {
        // Add following siblings and all their descendants
        for (final JNode sibling : node.getFollowingSiblings()) {
            result.add(sibling);
            collectDescendants(sibling, result);
        }
        // Recurse up to parent's following
        if (node.parent != null) {
            collectFollowing(node.parent, result);
        }
    }

    /**
     * Returns all nodes preceding this node in document order (excluding ancestors).
     * preceding = preceding-siblings and their descendants, then preceding of parent (recursive).
     */
    public List<JNode> getPreceding() throws XPathException {
        final List<JNode> result = new ArrayList<>();
        collectPreceding(this, result);
        return result;
    }

    private void collectPreceding(final JNode node, final List<JNode> result) throws XPathException {
        // Add preceding siblings in reverse and all their descendants
        final List<JNode> precSiblings = node.getPrecedingSiblings();
        for (int i = precSiblings.size() - 1; i >= 0; i--) {
            final JNode sibling = precSiblings.get(i);
            // Add descendants first (reverse document order), then the sibling itself
            final List<JNode> desc = new ArrayList<>();
            collectDescendants(sibling, desc);
            for (int j = desc.size() - 1; j >= 0; j--) {
                result.add(desc.get(j));
            }
            result.add(sibling);
        }
        // Recurse up to parent's preceding
        if (node.parent != null) {
            collectPreceding(node.parent, result);
        }
    }

    // ===================== Descendant axis =====================

    /**
     * Returns all descendants of this node (depth-first).
     */
    public List<JNode> getDescendants() throws XPathException {
        final List<JNode> result = new ArrayList<>();
        collectDescendants(this, result);
        return result;
    }

    private void collectDescendants(final JNode node, final List<JNode> result) throws XPathException {
        for (final JNode child : node.getChildren()) {
            result.add(child);
            collectDescendants(child, result);
        }
    }

    // ===================== Lookup support (XQuery 4.0 ?key and get() step) =====================

    /**
     * Lookup support: return the child JSON node(s) selected by the given key.
     * For object nodes, returns the member node whose key equals the selector.
     * For array nodes, treats the selector as a 1-based integer index.
     * For leaf nodes, returns empty.
     *
     * Per XQuery 4.0 §4.10, the lookup operator on a JSON node returns child JSON
     * nodes (not values), preserving navigation semantics.
     */
    @Override
    public Sequence get(final AtomicValue key) throws XPathException {
        if (key == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (value instanceof AbstractMapType) {
            final AbstractMapType map = (AbstractMapType) value;
            if (!map.contains(key)) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final Sequence v = map.get(key);
            return new JNode(key, v, this, 1);
        }
        if (value instanceof ArrayType) {
            final ArrayType array = (ArrayType) value;
            final int idx = ((IntegerValue) key.convertTo(Type.INTEGER)).getInt();
            if (idx < 1 || idx > array.getSize()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final Sequence v = array.get(idx - 1);
            return new JNode(null, v, this, idx);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * Lookup support: return all child keys (for ?* on JSON nodes).
     * For objects: the member keys. For arrays: 1..size.
     * For leaves: empty.
     */
    @Override
    public Sequence keys() throws XPathException {
        if (value instanceof AbstractMapType) {
            return ((AbstractMapType) value).keys();
        }
        if (value instanceof ArrayType) {
            final ArrayType array = (ArrayType) value;
            final ValueSequence ks = new ValueSequence();
            for (int i = 1; i <= array.getSize(); i++) {
                ks.add(new IntegerValue(i));
            }
            return ks;
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    // ===================== Root =====================

    /**
     * Returns the root of this JNode tree.
     */
    public JNode getRoot() {
        JNode n = this;
        while (n.parent != null) {
            n = n.parent;
        }
        return n;
    }

    // ===================== Node identity =====================

    /**
     * Returns true if this node is the same node as the other node
     * (by identity, not value equality).
     */
    public boolean isSameNode(final JNode other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        // Same node identity: same key/position path from root
        JNode n1 = this;
        JNode n2 = other;
        while (n1 != null && n2 != null) {
            if (n1.key != null || n2.key != null) {
                if (n1.key == null || n2.key == null) {
                    return false;
                }
                try {
                    if (n1.key.compareTo(null, n2.key) != 0) {
                        return false;
                    }
                } catch (final XPathException e) {
                    return false;
                }
            } else if (n1.position != n2.position) {
                return false;
            }
            if (n1.parent == null && n2.parent == null) {
                // Both roots: same identity only if same value object
                return n1.value == n2.value;
            }
            n1 = n1.parent;
            n2 = n2.parent;
        }
        return n1 == null && n2 == null;
    }

    // ===================== GNode interface =====================

    @Override
    public GNode getGNodeParent() {
        return parent;
    }

    @Override
    public List<JNode> getGNodeChildren() throws XPathException {
        return getChildren();
    }

    @Override
    public GNode getGNodeRoot() {
        return getRoot();
    }

    @Override
    public boolean isSameNode(final GNode other) {
        if (other instanceof JNode) {
            return isSameNode((JNode) other);
        }
        return false;
    }

    @Override
    public boolean hasGNodeChildren() {
        return hasChildren();
    }

    @Override
    public int getNodeKind() {
        return getJsonKind();
    }

    @Override
    public Sequence getGNodeValue() {
        return value;
    }

    // ===================== Item interface =====================

    @Override
    public Expression getExpression() {
        return null;
    }

    @Override
    public int getType() {
        return getJsonKind();
    }

    @Override
    public String getStringValue() throws XPathException {
        if (value instanceof Item) {
            return ((Item) value).getStringValue();
        }
        return value.getStringValue();
    }

    @Override
    public Sequence toSequence() {
        return this;
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        if (value instanceof Item) {
            return ((Item) value).convertTo(requiredType);
        }
        throw new XPathException((org.exist.xquery.Expression) null,
                "Cannot convert JNode to " + Type.getTypeName(requiredType));
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        if (value instanceof Item) {
            return ((Item) value).atomize();
        }
        throw new XPathException((org.exist.xquery.Expression) null,
                "Cannot atomize a JNode with sequence value");
    }

    @Override
    public void toSAX(final org.exist.storage.DBBroker broker,
                       final org.xml.sax.ContentHandler handler,
                       final java.util.Properties properties) throws org.xml.sax.SAXException {
        // JNodes don't have SAX representation
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        return Integer.MAX_VALUE;
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (value instanceof Item) {
            return ((Item) value).toJavaObject(target);
        }
        throw new XPathException((org.exist.xquery.Expression) null,
                "Cannot convert JNode to Java object");
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
        // JNodes are in-memory only — no persistent node relocation
    }

    @Override
    public void copyTo(final DBBroker broker, final DocumentBuilderReceiver receiver) throws SAXException {
        // JNodes don't have a SAX/DOM representation to copy
    }

    // ===================== Sequence interface (single-item) =====================

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException((Expression) null, "Cannot add items to a JNode");
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw new XPathException((Expression) null, "Cannot add items to a JNode");
    }

    @Override
    public int getItemType() {
        return Type.JSON_NODE;
    }

    @Override
    public SequenceIterator iterate() {
        return new SingleItemIterator(this);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return iterate();
    }

    @Override
    public long getItemCountLong() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean hasOne() {
        return true;
    }

    @Override
    public boolean hasMany() {
        return false;
    }

    @Override
    public void removeDuplicates() {
        // single item — no duplicates
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public Item itemAt(final int pos) {
        return pos == 0 ? this : null;
    }

    @Override
    public Sequence tail() {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return true;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        throw new XPathException((Expression) null,
                "Cannot convert JNode to NodeSet");
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        throw new XPathException((Expression) null,
                "Cannot convert JNode to MemoryNodeSet");
    }

    @Override
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return java.util.Collections.emptyIterator();
    }

    @Override
    public boolean isPersistentSet() {
        return false;
    }

    @Override
    public boolean isCached() {
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        // no-op
    }

    @Override
    public void clearContext(final int contextId) {
        // no-op
    }

    @Override
    public void setSelfAsContext(final int contextId) {
        // no-op
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false;
    }

    @Override
    public boolean containsReference(final Item item) {
        return this == item;
    }

    @Override
    public boolean contains(final Item item) {
        return this == item;
    }

    @Override
    public void destroy(final XQueryContext context, final Sequence contextSequence) {
        // no-op (overrides both Item and Sequence destroy)
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("jtree(");
        if (key != null) {
            try {
                sb.append(key.getStringValue()).append(": ");
            } catch (final XPathException e) {
                sb.append("?: ");
            }
        }
        sb.append(Type.getTypeName(getJsonKind()));
        sb.append(')');
        return sb.toString();
    }
}
