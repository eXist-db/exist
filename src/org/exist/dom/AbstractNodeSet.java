/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
package org.exist.dom;

import org.apache.log4j.Logger;
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
 * (e.g. {@link org.exist.dom.ExtArrayNodeSet}) remove duplicates when sorting the set.
 */
public abstract class AbstractNodeSet extends AbstractSequence implements NodeSet {

    protected final static Logger LOG = Logger.getLogger(AbstractNodeSet.class);

    // indicates the type of an optional value index that may have
    // been defined on the nodes in this set.
    protected int indexType = Type.ANY_TYPE;

    private boolean isCached = false;

    private boolean processInReverseOrder = false;

    private boolean trackMatches = true;

    protected AbstractNodeSet() {
        isEmpty = true;
    }

    /**
     * Return an iterator on the nodes in this list. The iterator returns nodes
     * according to the internal ordering of nodes (i.e. level first), not in document-
     * order.
     *
     */
    public abstract NodeSetIterator iterator();

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#iterate()
     */
    @Override
    public abstract SequenceIterator iterate() throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#unorderedIterator()
     */
    @Override
    public abstract SequenceIterator unorderedIterator() throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    @Override
    public int getItemType() {
        return Type.NODE;
    }

    /**
     * Check if this node set contains a node matching the document and
     * node-id of the given NodeProxy object.
     *
     * @param proxy
     */
    public abstract boolean contains(NodeProxy proxy);

    /**
     * Add a new proxy object to the node set. Please note: node set
     * implementations may allow duplicates.
     *
     * @param proxy
     */
    public abstract void add(NodeProxy proxy);

    /**
     * Add a proxy object to the node set. The sizeHint parameter
     * gives a hint about the number of items to be expected for the
     * current document.
     *
     * @param proxy
     * @param sizeHint
     */
    public void add(NodeProxy proxy, int sizeHint) {
        add(proxy);
    }

    /**
     * Add a sequence item to the node set. The item has to be
     * a subtype of node.
     */
    @Override
    public void add(Item item) throws XPathException {
        if (!Type.subTypeOf(item.getType(), Type.NODE))
            {throw new XPathException("item has wrong type");}
        add((NodeProxy) item);
    }

    /**
     * Add all items from the given sequence to the node set. All items
     * have to be a subtype of node.
     *
     * @param other
     * @throws XPathException
     */
    @Override
    public void addAll(Sequence other) throws XPathException {
        if (!other.isEmpty() && !Type.subTypeOf(other.getItemType(), Type.NODE))
            {throw new XPathException("sequence argument is not a node sequence");}
        if (Type.subTypeOf(other.getItemType(), Type.NODE))
        	{addAll((NodeSet) other);}
        for (final SequenceIterator i = other.iterate(); i.hasNext();) {
            add(i.nextItem());
        }
    }

    /**
     * Add all nodes from the given node set.
     *
     * @param other
     */
    public abstract void addAll(NodeSet other);

    /**
     * Return the number of nodes contained in this node set.
     */
    public abstract int getLength();

    public NodeSet copy() {
        final NewArrayNodeSet set = new NewArrayNodeSet(getLength());
        set.addAll(this);
        return set;
    }

	@Override
    public void setIsCached(boolean cached) {
        isCached = cached;
    }

    @Override
    public boolean isCached() {
        return isCached;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // all instances of NodeSet will automatically remove duplicates
        // upon a call to getLength() or iterate()
    }

    public abstract Node item(int pos);

    /**
     * Get the node at position pos within this node set.
     * @param pos
     */
    public abstract NodeProxy get(int pos);

    /**
     * Get a node from this node set matching the document and node id of
     * the given NodeProxy.
     *
     * @param p
     */
    public abstract NodeProxy get(NodeProxy p);

    @Override
    public DocumentSet getDocumentSet() {
        final MutableDocumentSet ds = new DefaultDocumentSet();
        NodeProxy p;
        for(final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            p = i.next();
            ds.add(p.getDocument());
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
     * @param al a node set containing potential parent nodes
     * @param mode selection mode
     */
    public NodeSet selectParentChild(NodeSet al, int mode) {
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
     * @param al a node set containing potential parent nodes
     * @param mode selection mode
     * @param contextId used to track context nodes when evaluating predicate
     * expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     * will be added to each result of the of the selection.
     */
    public NodeSet selectParentChild(NodeSet al, int mode, int contextId) {
        return NodeSetHelper.selectParentChild(this, al, mode, contextId);
    }

    public boolean matchParentChild(NodeSet al, int mode, int contextId) {
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
     * @param al a node set containing potential parent nodes
     * @param mode selection mode
     * @param includeSelf if true, check if the ancestor node itself is contained in
     * the set of descendant nodes (descendant-or-self axis)
     * @param contextId used to track context nodes when evaluating predicate
     * expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     * will be added to each result of the selection. 
     * 
     */
    public NodeSet selectAncestorDescendant(NodeSet al,	int mode, boolean includeSelf,
    int contextId, boolean copyMatches) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    public boolean matchAncestorDescendant(NodeSet al,	int mode, boolean includeSelf,
            int contextId, boolean copyMatches) {
        return NodeSetHelper.matchAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    /**
     * For a given set of potential ancestor nodes, return all ancestors
     * having descendants in this node set.
     *
     * @param  descendants    node set containing potential ancestors
     * @param includeSelf if true, check if the ancestor node itself is contained
     * in this node set (ancestor-or-self axis)
     * @param contextId
     */
    public NodeSet selectAncestors(NodeSet descendants, boolean includeSelf, int contextId) {
        return NodeSetHelper.selectAncestors(this, descendants, includeSelf, contextId);
    }

    public boolean matchAncestors(NodeSet descendants, boolean includeSelf, int contextId) {
        return NodeSetHelper.matchAncestors(this, descendants, includeSelf, contextId);
    }

    public NodeSet selectFollowing(NodeSet fl, int contextId) throws XPathException {
        return NodeSetHelper.selectFollowing(fl, this);
    }

    public NodeSet selectFollowing(NodeSet following, int position, int contextId) throws XPathException {
        throw new UnsupportedOperationException();
    }

    public NodeSet selectPreceding(NodeSet pl, int contextId) throws XPathException {
        return NodeSetHelper.selectPreceding(pl, this);
    }

    public NodeSet selectPreceding(NodeSet preceding, int nth, int contextId) throws XPathException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Select all nodes from the passed node set, which
     * are preceding or following siblings of the nodes in
     * this set. If mode is {@link #FOLLOWING}, only nodes following
     * the context node are selected. {@link #PRECEDING} selects
     * preceding nodes.
     *
     * @param siblings a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     * expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     * will be added to each result of the of the selection.
     */
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectPrecedingSiblings(this, siblings, contextId);
    }

    public NodeSet selectFollowingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectFollowingSiblings(this, siblings, contextId);
    }

    public NodeSet directSelectAttribute(DBBroker broker, org.exist.xquery.NodeTest qname, int contextId) {
        return NodeSetHelper.directSelectAttributes(broker, this, qname, contextId);
    }

    public boolean directMatchAttribute(DBBroker broker, org.exist.xquery.NodeTest qname, int contextId) {
        return NodeSetHelper.directMatchAttributes(broker, this, qname, contextId);
    }

    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
        NodeProxy temp = get(doc, nodeId);
        if (includeSelf && temp != null)
            {return temp;}
        nodeId = nodeId.getParentId();
        while (nodeId != null) {
            temp = get(doc, nodeId);
            if (temp != null)
                {return temp;}
            else if (directParent)
                {return null;}
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
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,	boolean includeSelf, int level) {
        return parentWithChild(proxy.getDocument(), proxy.getNodeId(), directParent, includeSelf);
    }

    /**
     * Return a new node set containing the parent nodes of all nodes in the
     * current set.
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet getParents(int contextId) {
        final NodeSet parents = new NewArrayNodeSet();
        NodeProxy parent = null;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            final NodeProxy current = i.next();
            final NodeId parentID = current.getNodeId().getParentId();
            if (parentID != null && !(parentID.getTreeLevel() == 1 &&
                    current.getDocument().getCollection().isTempCollection())) {
                if (parent == null || parent.getDocument().getDocId() != 
                        current.getDocument().getDocId() || !parent.getNodeId().equals(parentID)) {
                    if (parentID != NodeId.DOCUMENT_NODE) {
                        parent = new NodeProxy(current.getDocument(), parentID, Node.ELEMENT_NODE,
                            StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
                    } else {
                        parent = new NodeProxy(current.getDocument(), parentID, Node.DOCUMENT_NODE,
                            StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
                    }
                }
                if (Expression.NO_CONTEXT_ID != contextId) {
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
     * @param contextId an <code>int</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet getAncestors(int contextId, boolean includeSelf) {
        final ExtArrayNodeSet ancestors = new ExtArrayNodeSet();
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            final NodeProxy current = i.next();
            if (includeSelf) {
                if (Expression.NO_CONTEXT_ID != contextId)
                    {current.addContextNode(contextId, current);}
                ancestors.add(current);
            }
            NodeId parentID = current.getNodeId().getParentId();
            while (parentID != null) {
                //Filter out the temporary nodes wrapper element
                if (parentID != NodeId.DOCUMENT_NODE &&
                    !(parentID.getTreeLevel() == 1  && current.getDocument().getCollection().isTempCollection())) {
                    final NodeProxy parent = new NodeProxy(current.getDocument(), parentID, Node.ELEMENT_NODE);
                    if (Expression.NO_CONTEXT_ID != contextId)
                        {parent.addContextNode(contextId, current);}
                    else
                        {parent.copyContext(current);}
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
     * @param doc
     */
    public int getSizeHint(DocumentImpl doc) {
        return Constants.NO_SIZE_HINT;
    }

    /**
     * Return a new node set, which represents the intersection of the current
     * node set with the given node set.
     *
     * @param other
     */
    public NodeSet intersection(NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        NodeProxy l, p;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            l = i.next();
            if ((p = other.get(l)) != null) {
                l.addMatches(p);
                r.add(l);
            }
        }
        return r;
    }

    public NodeSet deepIntersection(NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        NodeProxy l, p, q;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            l = i.next();
            if ((p = other.parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL)) != null) {
                if (p.getNodeId().equals(l.getNodeId()))
                    {p.addMatches(l);}
                r.add(p);
            }
        }
        for (final Iterator<NodeProxy> i = other.iterator(); i.hasNext();) {
            l = i.next();
            if ((q = parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL)) != null) {
                if ((p = r.get(q)) != null) {
                    p.addMatches(l);
                } else
                    {r.add(l);}
            }
        }
        return r;
    }

    public NodeSet except(NodeSet other) {
        final AVLTreeNodeSet r = new AVLTreeNodeSet();
        NodeProxy l;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            l = i.next();
            if (!other.contains(l)) {
                r.add(l);
            }
        }
        return r;
    }

    public NodeSet filterDocuments(NodeSet otherSet) {
        final DocumentSet docs = otherSet.getDocumentSet();
        final NodeSet newSet = new NewArrayNodeSet();
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            final NodeProxy p = i.next();
            if (docs.contains(p.getDocument().getDocId()))
                {newSet.add(p);}
        }
        return newSet;
    }

    public void setProcessInReverseOrder(boolean inReverseOrder) {
        processInReverseOrder = inReverseOrder;
    }

    public boolean getProcessInReverseOrder() {
        return processInReverseOrder;
    }

    /**
     * Return a new node set which represents the union of the
     * current node set and the given node set.
     *
     * @param other
     */
    public NodeSet union(NodeSet other) {
        if (isEmpty())
            {return other;}
        if (other.isEmpty())
            {return this;}
        final NewArrayNodeSet result = new NewArrayNodeSet();
        result.addAll(other);
        NodeProxy p, c;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            p = i.next();
            if ((c = other.get(p)) != null) {
                c.addMatches(p);
            } else
                {result.add(p);}
        }
        return result;
    }

    /**
     * Returns all context nodes associated with the nodes in
     * this node set.
     *
     * @param contextId used to track context nodes when evaluating predicate
     * expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current context
     * will be added to each result of the of the selection.
     */
    public NodeSet getContextNodes(int contextId) {
        NodeProxy current, context;
        ContextItem contextNode;
        final NewArrayNodeSet result = new NewArrayNodeSet();
        DocumentImpl lastDoc = null;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
            current = i.next();
            contextNode = current.getContext();
            while (contextNode != null) {
                if (contextNode.getContextId() == contextId) {
                    context = contextNode.getNode();
                    context.addMatches(current);
                    if (Expression.NO_CONTEXT_ID != contextId)
                        {context.addContextNode(contextId, context);}
                    if (lastDoc != null && lastDoc.getDocId() != context.getDocument().getDocId()) {
                        lastDoc = context.getDocument();
                        result.add(context, getSizeHint(lastDoc));
                    } else
                        {result.add(context);}
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
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return null;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getState()
     */
    @Override
    public int getState() {
        return 1;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#hasChanged(int)
     */
    @Override
    public boolean hasChanged(int previousState) {
        return false;
    }

    @Override
    public void setTrackMatches(boolean track) {
    	this.trackMatches = track;
    }

    @Override
    public boolean getTrackMatches() {
        return trackMatches;
    }

    /**
     * If all nodes in this set have an index, returns the common
     * supertype used to build the index, e.g. xs:integer or xs:string.
     * If the nodes have different index types or no node has been indexed,
     * returns {@link Type#ITEM}.
     *
     * @see org.exist.xquery.GeneralComparison
     * @see org.exist.xquery.ValueComparison
     */
    public int getIndexType() {
        //Is the index type initialized ?
        if (indexType == Type.ANY_TYPE) {
            for (final Iterator<NodeProxy> i = iterator(); i.hasNext();) {
                final NodeProxy node = i.next();
                if (node.getDocument().getCollection().isTempCollection()) {
                    //Temporary nodes return default values
                    indexType = Type.ITEM;
                    break;
                }
                int nodeIndexType = node.getIndexType();
                //Refine type
                //TODO : use common subtype
                if (indexType == Type.ANY_TYPE) {
                    indexType = nodeIndexType;
                } else {
                    //Broaden type
                    //TODO : use common supertype
                    if (indexType != nodeIndexType)
                        {indexType = Type.ITEM;}
                }
            }
        }
        return indexType;
    }

    @Override
    public void clearContext(int contextId) throws XPathException {
        NodeProxy p;
        for (final Iterator<NodeProxy> i = iterator(); i.hasNext(); ) {
            p = i.next();
            p.clearContext(contextId);
        }
    }

    @Override
    public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
        final NodeProxy p = get((DocumentImpl)newNode.getOwnerDocument(), oldNodeId);
        if (p != null)
            {p.nodeMoved(oldNodeId, newNode);}
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AbstractSequence#isPersistentSet()
     */
    @Override
    public boolean isPersistentSet() {
        // node sets are always persistent
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("NodeSet(");
        for (int i = 0 ; i < getLength() ; i++) {
            if(i > 0)
                {result.append(", ");}
            final NodeProxy p = get(i);
            result.append("[").append(p.getDocument().getDocId()).append(":").append(p.getNodeId()).append("]");
        }
        result.append(")");
        return result.toString();
    }

    private class CollectionIterator implements Iterator<Collection> {

        Collection nextCollection = null;
        NodeSetIterator nodeIterator = iterator();

        CollectionIterator() {
            if (nodeIterator.hasNext()) {
                final NodeProxy p = nodeIterator.next();
                nextCollection = p.getDocument().getCollection();
            }
        }

        public boolean hasNext() {
            return nextCollection != null;
        }

        public Collection next() {
            final Collection oldCollection = nextCollection;
            nextCollection = null;
            while (nodeIterator.hasNext()) {
                final NodeProxy p = nodeIterator.next();
                if (!p.getDocument().getCollection().equals(oldCollection)) {
                    nextCollection = p.getDocument().getCollection();
                    break;
                }
            }
            return oldCollection;
        }

        public void remove() {
            // not needed
            throw new IllegalStateException();
        }
    }
}
