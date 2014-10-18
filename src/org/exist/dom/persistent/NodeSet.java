/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.NodeList;

import java.util.Iterator;

/**
 * @author wolf
 */
public interface NodeSet extends Sequence, NodeList, Iterable<NodeProxy> {

    public static final int ANCESTOR = 0;
    public static final int DESCENDANT = 1;
    public static final int PRECEDING = 2;
    public static final int FOLLOWING = 3;

    /**
     * Constant representing an empty node set.
     */
    public static final NodeSet EMPTY_SET = new EmptyNodeSet();

    /**
     * Get a copy of this node set which can be modified.
     *
     * @return the copy
     */
    public NodeSet copy();

    /**
     * Return an iterator on the nodes in this list. The iterator returns nodes
     * according to the internal ordering of nodes (i.e. level first), not in document-
     * order.
     */
    public NodeSetIterator iterator();

    /**
     * Check if this node set contains a node matching the document and
     * node-id of the given NodeProxy object.
     *
     * @param proxy
     */
    public boolean contains(NodeProxy proxy);

    /**
     * Returns a DocumentSet containing all documents referenced
     * in this node set.
     */
    public DocumentSet getDocumentSet();

    /**
     * Return an iterator on all collections referenced by documents
     * contained in this node set.
     */
    public Iterator<Collection> getCollectionIterator();

    /**
     * Add a new proxy object to the node set. Please note: node set
     * implementations may allow duplicates.
     *
     * @param proxy
     */
    public void add(NodeProxy proxy);

    /**
     * Add a proxy object to the node set. The sizeHint parameter
     * gives a hint about the number of items to be expected for the
     * current document.
     *
     * @param proxy
     * @param sizeHint
     */
    public void add(NodeProxy proxy, int sizeHint);

    /**
     * Add all nodes from the given node set.
     *
     * @param other
     */
    public void addAll(NodeSet other);

    /**
     * Get the node at position pos within this node set.
     *
     * @param pos
     */
    public NodeProxy get(int pos);

    /**
     * Get a node from this node set matching the document and node id of
     * the given NodeProxy.
     *
     * @param p
     */
    public NodeProxy get(NodeProxy p);

    public NodeProxy get(DocumentImpl doc, NodeId nodeId);

    /**
     * Check if any child nodes are found within this node set for a given
     * set of potential parent nodes.
     * <p/>
     * If mode is {@link #DESCENDANT}, the returned node set will contain
     * all child nodes found in this node set for each parent node. If mode is
     * {@link #ANCESTOR}, the returned set will contain those parent nodes,
     * for which children have been found.
     *
     * @param al   a node set containing potential parent nodes
     * @param mode selection mode
     */
    public NodeSet selectParentChild(NodeSet al, int mode);

    /**
     * Check if any child nodes are found within this node set for a given
     * set of potential parent nodes.
     * <p/>
     * If mode is {@link #DESCENDANT}, the returned node set will contain
     * all child nodes found in this node set for each parent node. If mode is
     * {@link #ANCESTOR}, the returned set will contain those parent nodes,
     * for which children have been found.
     *
     * @param al        a node set containing potential parent nodes
     * @param mode      selection mode
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */

    public NodeSet selectParentChild(NodeSet al, int mode, int contextId);

    public boolean matchParentChild(NodeSet al, int mode, int contextId);

    /**
     * Check if any descendant nodes are found within this node set for a given
     * set of potential ancestor nodes.
     * <p/>
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
     *                    expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                    will be added to each result of the of the selection.
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode, boolean includeSelf,
                                            int contextId, boolean copyMatches);

    public boolean matchAncestorDescendant(NodeSet al, int mode, boolean includeSelf,
                                           int contextId, boolean copyMatches);

    /**
     * For a given set of potential ancestor nodes, return all ancestors
     * having descendants in this node set.
     *
     * @param descendants node set containing potential ancestors
     * @param includeSelf if true, check if the ancestor node itself is contained
     *                    in this node set (ancestor-or-self axis)
     * @param contextId   used to track context nodes when evaluating predicate
     *                    expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                    will be added to each result of the of the selection.
     */
    public NodeSet selectAncestors(NodeSet descendants, boolean includeSelf, int contextId);

    /**
     * Select all nodes from the passed node set, which
     * are preceding siblings of the nodes in
     * this set.
     *
     * @param siblings  a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId);

    /**
     * Select all nodes from the passed node set, which
     * are following siblings of the nodes in
     * this set.
     *
     * @param siblings  a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    public NodeSet selectFollowingSiblings(NodeSet siblings, int contextId);

    public NodeSet selectPreceding(NodeSet preceding, int contextId) throws XPathException;

    public NodeSet selectPreceding(NodeSet preceding, int position, int contextId) throws XPathException, UnsupportedOperationException;

    public NodeSet selectFollowing(NodeSet following, int contextId) throws XPathException;

    public NodeSet selectFollowing(NodeSet following, int position, int contextId) throws XPathException;

    /**
     * Check if the node identified by its node id has an ancestor contained in this node set
     * and return the ancestor found.
     * <p/>
     * If directParent is true, only immediate ancestors (parents) are considered.
     * Otherwise the method will call itself recursively for all the node's
     * parents.
     * <p/>
     * If includeSelf is true, the method returns also true if
     * the node itself is contained in the node set.
     */
    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf);

    /**
     * Check if the given node has an ancestor contained in this node set
     * and return the ancestor found.
     * <p/>
     * If directParent is true, only immediate ancestors (parents) are considered.
     * Otherwise the method will call itself recursively for all the node's
     * parents.
     * <p/>
     * If includeSelf is true, the method returns also true if
     * the node itself is contained in the node set.
     */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent, boolean includeSelf, int level);

    /**
     * Return a new node set containing the parent nodes of all nodes in the
     * current set.
     */
    public NodeSet getParents(int contextId);

    public NodeSet getAncestors(int contextId, boolean includeSelf);

    /**
     * Optimized method to select attributes. Use this if the context has just one or
     * two nodes. Attributes will be directly looked up in the persistent DOM store.
     *
     * @param test      a node test
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    public NodeSet directSelectAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId);

    public boolean directMatchAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId);

    /**
     * If all nodes in this set have an index, returns the common
     * supertype used to build the index, e.g. xs:integer or xs:string.
     * If the nodes have different index types or no node has been indexed,
     * returns {@link Type#ITEM}.
     *
     * @see org.exist.xquery.GeneralComparison
     * @see org.exist.xquery.ValueComparison
     */
    public int getIndexType();

    /**
     * Get a hint about how many nodes in this node set belong to the
     * specified document. This is just used for allocating new node sets.
     * The information does not need to be exact. -1 is returned if the
     * size cannot be determined (the default).
     *
     * @param doc
     */
    public int getSizeHint(DocumentImpl doc);

    /**
     * Return a new node set, which represents the intersection of the current
     * node set with the given node set.
     *
     * @param other
     */
    public NodeSet intersection(NodeSet other);

    /**
     * Return a new node set, containing all nodes in this node set that
     * are contained or have descendants in the other node set.
     *
     * @param other
     */
    public NodeSet deepIntersection(NodeSet other);

    /**
     * Return a new node set which represents the union of the
     * current node set and the given node set.
     *
     * @param other
     */
    public NodeSet union(NodeSet other);

    /**
     * Return a new node set containing all nodes from this node set
     * except those nodes which are also contained in the argument node set.
     *
     * @param other
     */
    public NodeSet except(NodeSet other);

    /**
     * Create a new node set from this set containing only nodes in documents
     * that are also contained in the argument set.
     *
     * @param otherSet
     */
    public NodeSet filterDocuments(NodeSet otherSet);

    public void setProcessInReverseOrder(boolean inReverseOrder);

    public boolean getProcessInReverseOrder();

    /**
     * Returns all context nodes associated with the nodes in
     * this node set.
     *
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     */
    public NodeSet getContextNodes(int contextId);

    public boolean getTrackMatches();

    public void setTrackMatches(boolean track);

}
