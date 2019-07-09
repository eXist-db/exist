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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import java.util.Iterator;

/**
 * Abstract base class for all node set implementations. A node set is a special type of sequence,
 * which contains only nodes. Class NodeSet thus implements the {@link org.exist.xquery.value.Sequence}
 * as well as the DOM {@link org.w3c.dom.NodeList} interfaces.
 *
 * Please note that a node set may or may not contain duplicate nodes. Some implementations
 * (e.g. {@link org.exist.dom.persistent.ExtArrayNodeSet}) remove duplicates when sorting the set.
 */
public abstract class AbstractNodeSet extends AbstractSequence implements NodeSet {

    protected static final Logger LOG = LogManager.getLogger(AbstractNodeSet.class);

    // indicates the type of an optional value index that may have
    // been defined on the nodes in this set.
    protected int indexType = Type.ANY_TYPE;

    private boolean isCached = false;
    private boolean processInReverseOrder = false;
    private boolean trackMatches = true;

    protected AbstractNodeSet() {
        this.isEmpty = true;
    }

    /**
     * Return an iterator on the nodes in this list. The iterator returns nodes
     * according to the internal ordering of nodes (i.e. level first), not in document-
     * order.
     */
    @Override
    public abstract NodeSetIterator iterator();

    @Override
    public int getItemType() {
        return Type.NODE;
    }

    /**
     * Add a sequence item to the node set. The item has to be
     * a subtype of node.
     */
    @Override
    public void add(final Item item) throws XPathException {
        if(!Type.subTypeOf(item.getType(), Type.NODE)) {
            throw new XPathException("item has wrong type");
        }
        add((NodeProxy) item);
    }

    /**
     * Add a proxy object to the node set. The sizeHint parameter
     * gives a hint about the number of items to be expected for the
     * current document.
     *
     * @param proxy the proxy object
     * @param sizeHint hint about the number of items
     */
    @Override
    public void add(final NodeProxy proxy, final int sizeHint) {
        add(proxy);
    }

    /**
     * Add all items from the given sequence to the node set. All items
     * have to be a subtype of node.
     *
     * @param other sequence of items to be added
     * @throws XPathException in case of an XPath error
     */
    @Override
    public void addAll(final Sequence other) throws XPathException {
        if(!other.isEmpty() && !Type.subTypeOf(other.getItemType(), Type.NODE)) {
            throw new XPathException("sequence argument is not a node sequence");
        }
        if(Type.subTypeOf(other.getItemType(), Type.NODE)) {
            addAll((NodeSet) other);
        }
        for(final SequenceIterator i = other.iterate(); i.hasNext(); ) {
            add(i.nextItem());
        }
    }

    @Override
    public NodeSet copy() {
        final NewArrayNodeSet set = new NewArrayNodeSet();
        set.addAll(this);
        return set;
    }

    @Override
    public void setIsCached(final boolean cached) {
        isCached = cached;
    }

    @Override
    public boolean isCached() {
        return isCached;
    }

    @Override
    public void removeDuplicates() {
        // all instances of NodeSet will automatically remove duplicates
        // upon a call to getLength() or iterate()
    }

    @Override
    public DocumentSet getDocumentSet() {
        final MutableDocumentSet ds = new DefaultDocumentSet();
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            ds.add(i.next().getOwnerDocument());
        }
        return ds;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return new CollectionIterator();
    }

    /**
     * Check if any child nodes are found within this node set for a given
     * set of potential parent nodes.
     *
     * If mode is {@link #DESCENDANT}, the returned node set will contain
     * all child nodes found in this node set for each parent node. If mode is
     * {@link #ANCESTOR}, the returned set will contain those parent nodes,
     * for which children have been found.
     *
     * @param al   a node set containing potential parent nodes
     * @param mode selection mode
     */
    @Override
    public NodeSet selectParentChild(final NodeSet al, final int mode) {
        return selectParentChild(al, mode, Expression.NO_CONTEXT_ID);
    }

    /**
     * Check if any child nodes are found within this node set for a given
     * set of potential ancestor nodes.
     *
     * If mode is {@link #DESCENDANT}, the returned node set will contain
     * all child nodes found in this node set for each parent node. If mode is
     * {@link #ANCESTOR}, the returned set will contain those parent nodes,
     * for which children have been found.
     *
     * @param al        a node set containing potential parent nodes
     * @param mode      selection mode
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    @Override
    public NodeSet selectParentChild(final NodeSet al, final int mode, final int contextId) {
        return NodeSetHelper.selectParentChild(this, al, mode, contextId);
    }

    @Override
    public boolean matchParentChild(final NodeSet al, final int mode, final int contextId) {
        return NodeSetHelper.matchParentChild(this, al, mode, contextId);
    }

    /**
     * Check if any descendant nodes are found within this node set for a given
     * set of potential ancestor nodes.
     *
     * If mode is {@link #DESCENDANT}, the returned node set will contain
     * all descendant nodes found in this node set for each ancestor. If mode is
     * {@link #ANCESTOR}, the returned set will contain those ancestor nodes,
     * for which descendants have been found.
     *
     * @param al          a node set containing potential parent nodes
     * @param mode        selection mode
     * @param includeSelf if true, check if the ancestor node itself is contained in
     *                    the set of descendant nodes (descendant-or-self axis)
     * @param contextId   used to track context nodes when evaluating predicate
     *                    expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     *                    will be added to each result of the selection.
     */
    @Override
    public NodeSet selectAncestorDescendant(final NodeSet al, final int mode, final boolean includeSelf,
                                            final int contextId, final boolean copyMatches) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    @Override
    public boolean matchAncestorDescendant(final NodeSet al, final int mode, final boolean includeSelf,
                                           final int contextId, final boolean copyMatches) {
        return NodeSetHelper.matchAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    /**
     * For a given set of potential ancestor nodes, return all ancestors
     * having descendants in this node set.
     *
     * @param descendants node set containing potential ancestors
     * @param includeSelf if true, check if the ancestor node itself is contained
     *                    in this node set (ancestor-or-self axis)
     * @param contextId the context id
     * @return all ancestors having descendants in this node set
     */
    @Override
    public NodeSet selectAncestors(final NodeSet descendants, boolean includeSelf, int contextId) {
        return NodeSetHelper.selectAncestors(this, descendants, includeSelf, contextId);
    }

    public boolean matchAncestors(final NodeSet descendants, final boolean includeSelf, final int contextId) {
        return NodeSetHelper.matchAncestors(this, descendants, includeSelf, contextId);
    }

    @Override
    public NodeSet selectFollowing(final NodeSet fl, final int contextId) throws XPathException {
        return NodeSetHelper.selectFollowing(fl, this);
    }

    @Override
    public NodeSet selectFollowing(final NodeSet following, final int position, final int contextId) throws XPathException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeSet selectPreceding(final NodeSet pl, final int contextId) throws XPathException {
        return NodeSetHelper.selectPreceding(pl, this);
    }

    @Override
    public NodeSet selectPreceding(final NodeSet preceding, final int nth, final int contextId) throws XPathException, UnsupportedOperationException {
        throw new UnsupportedOperationException("selectPreceding is not implemented on AbstractNodeSet");
    }

    /**
     * Select all nodes from the passed node set, which
     * are preceding or following siblings of the nodes in
     * this set. If mode is {@link #FOLLOWING}, only nodes following
     * the context node are selected. {@link #PRECEDING} selects
     * preceding nodes.
     *
     * @param siblings  a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    @Override
    public NodeSet selectPrecedingSiblings(final NodeSet siblings, final int contextId) {
        return NodeSetHelper.selectPrecedingSiblings(this, siblings, contextId);
    }

    @Override
    public NodeSet selectFollowingSiblings(final NodeSet siblings, final int contextId) {
        return NodeSetHelper.selectFollowingSiblings(this, siblings, contextId);
    }

    @Override
    public NodeSet directSelectAttribute(final DBBroker broker, final org.exist.xquery.NodeTest qname, final int contextId) {
        return NodeSetHelper.directSelectAttributes(broker, this, qname, contextId);
    }

    @Override
    public boolean directMatchAttribute(final DBBroker broker, final org.exist.xquery.NodeTest qname, final int contextId) {
        return NodeSetHelper.directMatchAttributes(broker, this, qname, contextId);
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl doc, NodeId nodeId, final boolean directParent, final boolean includeSelf) {
        NodeProxy temp = get(doc, nodeId);
        if(includeSelf && temp != null) {
            return temp;
        }
        nodeId = nodeId.getParentId();
        while(nodeId != null) {
            temp = get(doc, nodeId);
            if(temp != null) {
                return temp;
            } else if(directParent) {
                return null;
            }
            nodeId = nodeId.getParentId();
        }
        return null;
    }

    /**
     * Check if the given node has an ancestor contained in this node set
     * and return the ancestor found.
     *
     * If directParent is true, only immediate ancestors (parents) are considered.
     * Otherwise the method will call itself recursively for all the node's
     * parents.
     *
     * If includeSelf is true, the method returns also true if
     * the node itself is contained in the node set.
     */
    @Override
    public NodeProxy parentWithChild(final NodeProxy proxy, final boolean directParent, final boolean includeSelf, final int level) {
        return parentWithChild(proxy.getOwnerDocument(), proxy.getNodeId(), directParent, includeSelf);
    }

    /**
     * Return a new node set containing the parent nodes of all nodes in the
     * current set.
     *
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    @Override
    public NodeSet getParents(final int contextId) {
        final NodeSet parents = new NewArrayNodeSet();
        NodeProxy parent = null;
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy current = i.next();
            final NodeId parentID = current.getNodeId().getParentId();
            if(parentID != null && !(parentID.getTreeLevel() == 1 &&
                current.getOwnerDocument().getCollection().isTempCollection())) {
                if(parent == null || parent.getOwnerDocument().getDocId() !=
                    current.getOwnerDocument().getDocId() || !parent.getNodeId().equals(parentID)) {
                    if(parentID != NodeId.DOCUMENT_NODE) {
                        parent = new NodeProxy(current.getOwnerDocument(), parentID, Node.ELEMENT_NODE,
                            StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
                    } else {
                        parent = new NodeProxy(current.getOwnerDocument(), parentID, Node.DOCUMENT_NODE,
                            StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
                    }
                }
                if(Expression.NO_CONTEXT_ID != contextId) {
                    parent.addContextNode(contextId, current);
                } else {
                    parent.copyContext(current);
                }
                parent.addMatches(current);
                parents.add(parent);
            }
        }
        return parents;
    }

    /**
     * The method <code>getAncestors</code>
     *
     * @param contextId   an <code>int</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeSet</code> value
     */
    @Override
    public NodeSet getAncestors(final int contextId, final boolean includeSelf) {
        final ExtArrayNodeSet ancestors = new ExtArrayNodeSet();
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy current = i.next();
            if(includeSelf) {
                if(Expression.NO_CONTEXT_ID != contextId) {
                    current.addContextNode(contextId, current);
                }
                ancestors.add(current);
            }
            NodeId parentID = current.getNodeId().getParentId();
            while(parentID != null) {
                //Filter out the temporary nodes wrapper element
                if(parentID != NodeId.DOCUMENT_NODE &&
                    !(parentID.getTreeLevel() == 1 && current.getOwnerDocument().getCollection().isTempCollection())) {
                    final NodeProxy parent = new NodeProxy(current.getOwnerDocument(), parentID, Node.ELEMENT_NODE);
                    if(Expression.NO_CONTEXT_ID != contextId) {
                        parent.addContextNode(contextId, current);
                    } else {
                        parent.copyContext(current);
                    }
                    ancestors.add(parent);
                }
                parentID = parentID.getParentId();
            }
        }
        ancestors.mergeDuplicates();
        return ancestors;
    }

    /**
     * Get a hint about how many nodes in this node set belong to the
     * specified document. This is just used for allocating new node sets.
     * The information does not need to be exact. -1 is returned if the
     * size cannot be determined (the default).
     *
     * @param doc the document to get the hint for
     * @return hint about how many nodes in this node set belong to the specified document
     */
    @Override
    public int getSizeHint(final DocumentImpl doc) {
        return Constants.NO_SIZE_HINT;
    }

    /**
     * Return a new node set, which represents the intersection of the current
     * node set with the given node set.
     *
     * @param other to intersect the current node set with
     * @return new node set, which represents the intersection of the current node set with the given node set.
     */
    @Override
    public NodeSet intersection(final NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy l = i.next();
            final NodeProxy p = other.get(l);
            if(p != null) {
                l.addMatches(p);
                r.add(l);
            }
        }
        return r;
    }

    @Override
    public NodeSet deepIntersection(final NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy l = i.next();
            final NodeProxy p = other.parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
            if (p != null) {
                if (p.getNodeId().equals(l.getNodeId())) {
                    p.addMatches(l);
                }
                r.add(p);
            }
        }
        for (final Iterator<NodeProxy> i = other.iterator(); i.hasNext(); ) {
            final NodeProxy l = i.next();
            final NodeProxy q = parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
            if (q != null) {
                final NodeProxy p = r.get(q);
                if(p != null) {
                    p.addMatches(l);
                } else {
                    r.add(l);
                }
            }
        }
        return r;
    }

    @Override
    public NodeSet except(final NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        NodeProxy l;
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            l = i.next();
            if(!other.contains(l)) {
                r.add(l);
            }
        }
        return r;
    }

    @Override
    public NodeSet filterDocuments(final NodeSet otherSet) {
        final DocumentSet docs = otherSet.getDocumentSet();
        final NodeSet newSet = new NewArrayNodeSet();
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy p = i.next();
            if(docs.contains(p.getOwnerDocument().getDocId())) {
                newSet.add(p);
            }
        }
        return newSet;
    }

    @Override
    public void setProcessInReverseOrder(final boolean inReverseOrder) {
        processInReverseOrder = inReverseOrder;
    }

    @Override
    public boolean getProcessInReverseOrder() {
        return processInReverseOrder;
    }

    /**
     * Return a new node set which represents the union of the
     * current node set and the given node set.
     *
     * @param other NodeSet to unify with current node set
     * @return new node set which represents the union of the current node set and the given node set.
     */
    public NodeSet union(final NodeSet other) {
        if(isEmpty()) {
            return other;
        } else if(other.isEmpty()) {
            return this;
        } else {
            final NewArrayNodeSet result = new NewArrayNodeSet();
            result.addAll(other);
            for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
                final NodeProxy p = i.next();
                final NodeProxy c = other.get(p);
                if(c != null) {
                    c.addMatches(p);
                } else {
                    result.add(p);
                }
            }
            return result;
        }
    }

    /**
     * Returns all context nodes associated with the nodes in
     * this node set.
     *
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    @Override
    public NodeSet getContextNodes(final int contextId) {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        DocumentImpl lastDoc = null;
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            final NodeProxy current = i.next();
            ContextItem contextNode = current.getContext();
            while(contextNode != null) {
                if(contextNode.getContextId() == contextId) {
                    final NodeProxy context = contextNode.getNode();
                    context.addMatches(current);
                    if(Expression.NO_CONTEXT_ID != contextId) {
                        context.addContextNode(contextId, context);
                    }
                    if(lastDoc != null && lastDoc.getDocId() != context.getOwnerDocument().getDocId()) {
                        lastDoc = context.getOwnerDocument();
                        result.add(context, getSizeHint(lastDoc));
                    } else {
                        result.add(context);
                    }
                }
                contextNode = contextNode.getNextDirect();
            }
        }
        return result;
    }

    /**
     * Always returns this.
     *
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    @Override
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return null;
    }

    @Override
    public int getState() {
        return 1;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false;
    }

    @Override
    public void setTrackMatches(final boolean track) {
        this.trackMatches = track;
    }

    @Override
    public boolean getTrackMatches() {
        return trackMatches;
    }

    /**
     * If all nodes in this set have an index, returns the common
     * super type used to build the index, e.g. xs:integer or xs:string.
     * If the nodes have different index types or no node has been indexed,
     * returns {@link Type#ITEM}.
     *
     * @see org.exist.xquery.GeneralComparison
     * @see org.exist.xquery.ValueComparison
     */
    @Override
    public int getIndexType() {
        //Is the index type initialized ?
        if(indexType == Type.ANY_TYPE) {
            for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
                final NodeProxy node = i.next();
                if(node.getOwnerDocument().getCollection().isTempCollection()) {
                    //Temporary nodes return default values
                    indexType = Type.ITEM;
                    break;
                }
                final int nodeIndexType = node.getIndexType();
                //Refine type
                //TODO : use common subtype
                if(indexType == Type.ANY_TYPE) {
                    indexType = nodeIndexType;
                } else {
                    //Broaden type
                    //TODO : use common supertype
                    if(indexType != nodeIndexType) {
                        indexType = Type.ITEM;
                    }
                }
            }
        }
        return indexType;
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        NodeProxy p;
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            p = i.next();
            p.clearContext(contextId);
        }
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
        final NodeProxy p = get(newNode.getOwnerDocument(), oldNodeId);
        if(p != null) {
            p.nodeMoved(oldNodeId, newNode);
        }
    }

    @Override
    public boolean isPersistentSet() {
        // node sets are always persistent
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("NodeSet(");
        for(int i = 0; i < getLength(); i++) {
            if(i > 0) {
                result.append(", ");
            }
            final NodeProxy p = get(i);
            result.append("[").append(p.getOwnerDocument().getDocId()).append(":").append(p.getNodeId()).append("]");
        }
        result.append(")");
        return result.toString();
    }

    private final class CollectionIterator implements Iterator<Collection> {

        private Collection nextCollection = null;
        private final NodeSetIterator nodeIterator = iterator();

        private CollectionIterator() {
            if(nodeIterator.hasNext()) {
                final NodeProxy p = nodeIterator.next();
                nextCollection = p.getOwnerDocument().getCollection();
            }
        }

        @Override
        public final boolean hasNext() {
            return nextCollection != null;
        }

        @Override
        public final Collection next() {
            final Collection oldCollection = nextCollection;
            nextCollection = null;
            while(nodeIterator.hasNext()) {
                final NodeProxy p = nodeIterator.next();
                if(!p.getOwnerDocument().getCollection().equals(oldCollection)) {
                    nextCollection = p.getOwnerDocument().getCollection();
                    break;
                }
            }
            return oldCollection;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException("remove is not implemented for CollectionIterator");
        }
    }
}
