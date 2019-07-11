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

    static final int ANCESTOR = 0;
    static final int DESCENDANT = 1;
    static final int PRECEDING = 2;
    static final int FOLLOWING = 3;

    /**
     * Constant representing an empty node set.
     */
    static final NodeSet EMPTY_SET = new EmptyNodeSet();

    /**
     * Get a copy of this node set which can be modified.
     *
     * @return the copy
     */
    NodeSet copy();

    /**
     * Return an iterator on the nodes in this list. The iterator returns nodes
     * according to the internal ordering of nodes (i.e. level first), not in document-
     * order.
     */
    NodeSetIterator iterator();

    /**
     * Check if this node set contains a node matching the document and
     * node-id of the given NodeProxy object.
     *
     * @param proxy to check against this node set
     * @return true if the node set contains a node matching the document and node-id of the given NodeProxy object.
     */
    boolean contains(NodeProxy proxy);

    /**
     * Returns a DocumentSet containing all documents referenced
     * in this node set.
     * @return DocumentSet containing all documents referenced in this node set
     */
    DocumentSet getDocumentSet();

    /**
     * @return Return an iterator on all collections referenced by documents
     * contained in this node set.
     */
    Iterator<Collection> getCollectionIterator();

    /**
     * Add a new proxy object to the node set. Please note: node set
     * implementations may allow duplicates.
     *
     * @param proxy proxy object to be added
     */
    void add(NodeProxy proxy);

    /**
     * Add a proxy object to the node set. The sizeHint parameter
     * gives a hint about the number of items to be expected for the
     * current document.
     *
     * @param proxy proxy object to be added
     * @param sizeHint hint about the number of items
     */
    void add(NodeProxy proxy, int sizeHint);

    /**
     * Add all nodes from the given node set.
     *
     * @param other node set to be added to this
     */
    void addAll(NodeSet other);

    /**
     * Get the node at position pos within this node set.
     *
     * @param pos position of the node to be returned
     * @return the node at position pos within this node set.
     */
    NodeProxy get(int pos);

    /**
     * Get a node from this node set matching the document and node id of
     * the given NodeProxy.
     *
     * @param p give NodeProxy
     * @return node from this node set matching the document and node id of the given NodeProxy.
     */
    NodeProxy get(NodeProxy p);

    NodeProxy get(DocumentImpl doc, NodeId nodeId);

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
     * @return NodeSet of found nodes
     */
    NodeSet selectParentChild(NodeSet al, int mode);

    /**
     * Check if any child nodes are found within this node set for a given
     * set of potential parent nodes.
     *
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
     * @return NodeSet of found childs
     */

    NodeSet selectParentChild(NodeSet al, int mode, int contextId);

    boolean matchParentChild(NodeSet al, int mode, int contextId);

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
     *                    expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                    will be added to each result of the of the selection.
     * @param copyMatches create copy of matches
     * @return NodeSet of matching nodes
     */
    NodeSet selectAncestorDescendant(NodeSet al, int mode, boolean includeSelf,
                                            int contextId, boolean copyMatches);

    boolean matchAncestorDescendant(NodeSet al, int mode, boolean includeSelf,
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
     * @return NodeSet of matching nodes
     */
    NodeSet selectAncestors(NodeSet descendants, boolean includeSelf, int contextId);

    /**
     * Select all nodes from the passed node set, which
     * are preceding siblings of the nodes in
     * this set.
     *
     * @param siblings  a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     * @return NodeSet of matching nodes
     */
    NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId);

    /**
     * Select all nodes from the passed node set, which
     * are following siblings of the nodes in
     * this set.
     *
     * @param siblings  a node set containing potential siblings
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     * @return NodeSet of matching nodes
     */
    NodeSet selectFollowingSiblings(NodeSet siblings, int contextId);

    NodeSet selectPreceding(NodeSet preceding, int contextId) throws XPathException;

    NodeSet selectPreceding(NodeSet preceding, int position, int contextId) throws XPathException, UnsupportedOperationException;

    NodeSet selectFollowing(NodeSet following, int contextId) throws XPathException;

    NodeSet selectFollowing(NodeSet following, int position, int contextId) throws XPathException;

    /**
     * Check if the node identified by its node id has an ancestor contained in this node set
     * and return the ancestor found.
     *
     * If directParent is true, only immediate ancestors (parents) are considered.
     * Otherwise the method will call itself recursively for all the node's
     * parents.
     *
     * If includeSelf is true, the method returns also true if
     * the node itself is contained in the node set.
     * @param directParent if true, only immediate ancestors (parents) are considered
     * @param includeSelf if true, the method returns also true if the node itself is contained in the node set.
     * @param doc the doc
     * @param nodeId node identifier
     * @return the ancestor found
     *
     */
    NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf);

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
     * @param proxy given node
     * @param directParent if true, only immediate ancestors (parents) are considered.
     * @param includeSelf is true, the method returns also true if the node itself is contained in the node set
     * @param level node level
     * @return the ancestor found in this node set
     */
    NodeProxy parentWithChild(NodeProxy proxy, boolean directParent, boolean includeSelf, int level);

    /**
     * Return a new node set containing the parent nodes of all nodes in the
     * current set.
     * @param contextId the context id
     * @return a new node set containing the parent nodes of all nodes in the current set.
     */
    NodeSet getParents(int contextId);

    NodeSet getAncestors(int contextId, boolean includeSelf);

    /**
     * Optimized method to select attributes. Use this if the context has just one or
     * two nodes. Attributes will be directly looked up in the persistent DOM store.
     *
     * @param broker the eXistdb DBBRoker
     * @param test      a node test
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     * @return selected attributes
     */
    NodeSet directSelectAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId);

    boolean directMatchAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId);

    /**
     * If all nodes in this set have an index, returns the common
     * supertype used to build the index, e.g. xs:integer or xs:string.
     * If the nodes have different index types or no node has been indexed,
     * returns {@link Type#ITEM}.
     *
     * @see org.exist.xquery.GeneralComparison
     * @see org.exist.xquery.ValueComparison
     * @return  the common supertype used to build the index
     */
    int getIndexType();

    /**
     * Get a hint about how many nodes in this node set belong to the
     * specified document. This is just used for allocating new node sets.
     * The information does not need to be exact. -1 is returned if the
     * size cannot be determined (the default).
     *
     * @param doc specified document
     * @return hint about how many nodes in this node set belong to the document
     */
    int getSizeHint(DocumentImpl doc);

    /**
     * Return a new node set, which represents the intersection of the current
     * node set with the given node set.
     *
     * @param other to intersect with current node set
     * @return intersection of this and the given node set
     */
    NodeSet intersection(NodeSet other);

    /**
     * Return a new node set, containing all nodes in this node set that
     * are contained or have descendants in the other node set.
     *
     * @param other to deep intersect with current node set
     * @return  deep intersection of this and the given node set
     */
    NodeSet deepIntersection(NodeSet other);

    /**
     * Return a new node set which represents the union of the
     * current node set and the given node set.
     *
     * @param other to unify with current node set
     * @return  union of this and the given node set

     */
    NodeSet union(NodeSet other);

    /**
     * Return a new node set containing all nodes from this node set
     * except those nodes which are also contained in the argument node set.
     *
     * @param other nodes to be not added to the returned node set
     * @return node set containing all nodes from this node set except those nodes which are also contained
     *  in the argument node set.
     */
    NodeSet except(NodeSet other);

    /**
     * Create a new node set from this set containing only nodes in documents
     * that are also contained in the argument set.
     *
     * @param otherSet set of nodes to filter for
     * @return node set from this set containing only nodes in documents that are also contained in the argument set.
     */
    NodeSet filterDocuments(NodeSet otherSet);

    void setProcessInReverseOrder(boolean inReverseOrder);

    boolean getProcessInReverseOrder();

    /**
     * Returns all context nodes associated with the nodes in
     * this node set.
     *
     * @param contextId used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link org.exist.xquery.Expression#NO_CONTEXT_ID}, the current context
     *                  will be added to each result of the of the selection.
     * @return all context nodes associated with the nodes in this node set.
     */
    NodeSet getContextNodes(int contextId);

    boolean getTrackMatches();

    void setTrackMatches(boolean track);

}
