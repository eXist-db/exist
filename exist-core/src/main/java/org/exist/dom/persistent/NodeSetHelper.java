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

import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.w3c.dom.*;

/**
 * Collection of static methods operating on node sets.
 *
 * @author wolf
 */
public final class NodeSetHelper {

    private NodeSetHelper() {
        //Utility class of static methods
    }

    /**
     * For two given sets of potential parent and child nodes, find those nodes
     * from the child set that actually have parents in the parent set, i.e. the
     * parent-child relationship is true.
     *
     * The method returns either the matching descendant or ancestor nodes,
     * depending on the mode constant.
     *
     * If mode is {@link NodeSet#DESCENDANT}, the returned node set will contain all
     * child nodes found in this node set for each parent node. If mode is
     * {@link NodeSet#ANCESTOR}, the returned set will contain those parent nodes, for
     * which children have been found.
     *
     * @param dl        A node set containing potential child nodes
     * @param al        A node set containing potential parent nodes
     * @param mode      Selection mode
     * @param contextId Used to track context nodes when evaluating predicate
     *                  expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *                  the current context will be added to each result of the selection.
     * @return find those nodes from the child set that actually have parents in the parent set, i.e. the
     * parent-child relationship is true.
     */
    public static NodeSet selectParentChild(final NodeSet dl, final NodeSet al,
            final int mode, final int contextId) {
        final ExtArrayNodeSet result = new ExtArrayNodeSet();
        DocumentImpl lastDoc = null;
        switch(mode) {

            case NodeSet.DESCENDANT:
                for(final NodeProxy child : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    if(lastDoc == null || child.getOwnerDocument() != lastDoc) {
                        lastDoc = child.getOwnerDocument();
                        sizeHint = dl.getSizeHint(lastDoc);
                    }
                    final NodeProxy parent = al.parentWithChild(child, true, false,
                        NodeProxy.UNKNOWN_NODE_LEVEL);
                    if(parent != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            child.deepCopyContext(parent, contextId);
                        } else {
                            child.copyContext(parent);
                        }
                        result.add(child, sizeHint);
                    }
                }
                break;

            case NodeSet.ANCESTOR:
                for(final NodeProxy child : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    if(lastDoc == null || child.getOwnerDocument() != lastDoc) {
                        lastDoc = child.getOwnerDocument();
                        sizeHint = al.getSizeHint(lastDoc);
                    }
                    final NodeProxy parent = al.parentWithChild(child, true, false,
                        NodeProxy.UNKNOWN_NODE_LEVEL);
                    if(parent != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            parent.deepCopyContext(child, contextId);
                        } else {
                            parent.copyContext(child);
                        }
                        parent.addMatches(child);
                        result.add(parent, sizeHint);
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Bad 'mode' argument");
        }
        result.sort();
        return result;
    }

    public static boolean matchParentChild(final NodeSet dl, final NodeSet al,
            final int mode, final int contextId) {
        DocumentImpl lastDoc = null;
        switch(mode) {

            case NodeSet.DESCENDANT:
                for(final NodeProxy child : dl) {
                    if(lastDoc == null || child.getOwnerDocument() != lastDoc) {
                        lastDoc = child.getOwnerDocument();
                    }
                    final NodeProxy parent = al.parentWithChild(child, true, false, NodeProxy.UNKNOWN_NODE_LEVEL);
                    if(parent != null) {
                        return true;
                    }
                }
                break;

            case NodeSet.ANCESTOR:
                for(final NodeProxy child : dl) {
                    if(lastDoc == null || child.getOwnerDocument() != lastDoc) {
                        lastDoc = child.getOwnerDocument();
                    }
                    final NodeProxy parent = al.parentWithChild(child, true, false, NodeProxy.UNKNOWN_NODE_LEVEL);
                    if(parent != null) {
                        return true;
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Bad 'mode' argument");
        }

        return false;
    }

    /**
     * For two given sets of potential ancestor and descendant nodes, find those
     * nodes from the descendant set that actually have ancestors in the
     * ancestor set, i.e. the ancestor-descendant relationship is true.
     *
     * The method returns either the matching descendant or ancestor nodes,
     * depending on the mode constant.
     *
     * If mode is {@link NodeSet#DESCENDANT}, the returned node set will contain all
     * descendant nodes found in this node set for each ancestor. If mode is
     * {@link NodeSet#ANCESTOR}, the returned set will contain those ancestor nodes,
     * for which descendants have been found.
     *
     * @param dl          A node set containing potential descendant nodes
     * @param al          A node set containing potential ancestor nodes
     * @param mode        Selection mode
     * @param includeSelf If true, check if the ancestor node itself is contained
     *                    in the set of descendant nodes (descendant-or-self axis)
     * @param contextId   Used to track context nodes when evaluating predicate
     *                    expressions. If contextId != {@link Expression#NO_CONTEXT_ID}, the current
     *                    context will be added to each result of the selection.
     * @return those nodes from the descendant set that actually have ancestors in the
     * ancestor set, i.e. the ancestor-descendant relationship is true.
     */
    public static NodeSet selectAncestorDescendant(final NodeSet dl,
            final NodeSet al, final int mode, final boolean includeSelf,
            final int contextId) {
        final ExtArrayNodeSet result = new ExtArrayNodeSet();
        DocumentImpl lastDoc = null;
        switch(mode) {

            case NodeSet.DESCENDANT:
                for(final NodeProxy descendant : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    // get a size hint for every new document encountered
                    if(lastDoc == null || descendant.getOwnerDocument() != lastDoc) {
                        lastDoc = descendant.getOwnerDocument();
                        sizeHint = dl.getSizeHint(lastDoc);
                    }
                    final NodeProxy ancestor = al.parentWithChild(descendant.getOwnerDocument(),
                        descendant.getNodeId(), false, includeSelf);
                    if(ancestor != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            descendant.addContextNode(contextId, ancestor);
                        } else {
                            descendant.copyContext(ancestor);
                        }
                        result.add(descendant, sizeHint);
                    }
                }
                break;

            case NodeSet.ANCESTOR:
                for(final NodeProxy descendant : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    // get a size hint for every new document encountered
                    if(lastDoc == null || descendant.getOwnerDocument() != lastDoc) {
                        lastDoc = descendant.getOwnerDocument();
                        sizeHint = al.getSizeHint(lastDoc);
                    }
                    final NodeProxy ancestor = al.parentWithChild(descendant.getOwnerDocument(),
                        descendant.getNodeId(), false, includeSelf);
                    if(ancestor != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            ancestor.addContextNode(contextId, descendant);
                        } else {
                            ancestor.copyContext(descendant);
                        }
                        result.add(ancestor, sizeHint);
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Bad 'mode' argument");
        }
        return result;
    }

    public static boolean matchAncestorDescendant(final NodeSet dl,
            final NodeSet al, final int mode, final boolean includeSelf, final int contextId) {
        final ExtArrayNodeSet result = new ExtArrayNodeSet();
        DocumentImpl lastDoc = null;
        switch(mode) {

            case NodeSet.DESCENDANT:
                for(final NodeProxy descendant : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    // get a size hint for every new document encountered
                    if(lastDoc == null || descendant.getOwnerDocument() != lastDoc) {
                        lastDoc = descendant.getOwnerDocument();
                        sizeHint = dl.getSizeHint(lastDoc);
                    }
                    final NodeProxy ancestor = al.parentWithChild(descendant.getOwnerDocument(),
                        descendant.getNodeId(), false, includeSelf);
                    if(ancestor != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            descendant.addContextNode(contextId, ancestor);
                        } else {
                            descendant.copyContext(ancestor);
                        }
                        result.add(descendant, sizeHint);
                        return true;
                    }
                }
                break;

            case NodeSet.ANCESTOR:
                for(final NodeProxy descendant : dl) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    // get a size hint for every new document encountered
                    if(lastDoc == null || descendant.getOwnerDocument() != lastDoc) {
                        lastDoc = descendant.getOwnerDocument();
                        sizeHint = al.getSizeHint(lastDoc);
                    }
                    final NodeProxy ancestor = al.parentWithChild(descendant.getOwnerDocument(),
                        descendant.getNodeId(), false, includeSelf);
                    if(ancestor != null) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            ancestor.addContextNode(contextId, descendant);
                        } else {
                            ancestor.copyContext(descendant);
                        }
                        result.add(ancestor, sizeHint);
                        return true;
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Bad 'mode' argument");
        }

        return false;
    }

    /**
     * For two sets of potential ancestor and descendant nodes, return all the
     * real ancestors having a descendant in the descendant set.
     *
     * @param al          Node set containing potential ancestors
     * @param dl          Node set containing potential descendants
     * @param includeSelf If true, check if the ancestor node itself
     *                    is contained in this node set (ancestor-or-self axis)
     * @param contextId   Used to track context nodes when evaluating predicate
     *                    expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *                    the current context will be added to each result of the of the
     *                    selection.
     * @return all the real ancestors having a descendant in the descendant set.
     */
    public static NodeSet selectAncestors(final NodeSet al, final NodeSet dl,
            final boolean includeSelf, final int contextId) {
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy descendant : dl) {
            final NodeSet ancestors = ancestorsForChild(al, descendant, false, includeSelf);
            for(final NodeProxy ancestor : ancestors) {
                if(ancestor != null) {
                    final NodeProxy temp = result.get(ancestor);
                    if(temp == null) {
                        if(Expression.IGNORE_CONTEXT != contextId) {
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                ancestor.addContextNode(contextId, descendant);
                            } else {
                                ancestor.copyContext(descendant);
                            }
                        }
                        ancestor.addMatches(descendant);
                        result.add(ancestor);
                    } else if(Expression.NO_CONTEXT_ID != contextId) {
                        temp.addContextNode(contextId, descendant);
                    }
                }
            }
        }
        return result;
    }

    public static boolean matchAncestors(final NodeSet al, final NodeSet dl,
            final boolean includeSelf, final int contextId) {
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy descendant : dl) {
            final NodeSet ancestors = ancestorsForChild(al, descendant, false, includeSelf);
            for(final NodeProxy ancestor : ancestors) {
                if(ancestor != null) {
                    final NodeProxy temp = result.get(ancestor);
                    if(temp == null) {
                        if(Expression.IGNORE_CONTEXT != contextId) {
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                ancestor.addContextNode(contextId, descendant);
                            } else {
                                ancestor.copyContext(descendant);
                            }
                        }
                        ancestor.addMatches(descendant);
                        result.add(ancestor);
                        return true;
                    } else if(Expression.NO_CONTEXT_ID != contextId) {
                        temp.addContextNode(contextId, descendant);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Return all nodes contained in the node set that are ancestors of the node
     * p.
     */
    private static NodeSet ancestorsForChild(final NodeSet ancestors,
            final NodeProxy child, final boolean directParent,
            final boolean includeSelf) {
        final NodeSet result = new NewArrayNodeSet();
        NodeId nodeId = child.getNodeId();
        NodeProxy temp = ancestors.get(child.getOwnerDocument(), nodeId);
        if(includeSelf && temp != null) {
            result.add(temp);
        }
        while(nodeId != null && nodeId != NodeId.DOCUMENT_NODE) {
            nodeId = nodeId.getParentId();
            temp = ancestors.get(child.getOwnerDocument(), nodeId);
            if(temp != null) {
                result.add(temp);
            } else if(directParent) {
                return result;
            }
        }
        return result;
    }

    /**
     * Select all nodes from the passed set of potential siblings, which are
     * preceding siblings of the nodes in the other set.
     *
     * @param candidates The node set to check
     * @param references A node set containing potential siblings
     * @param contextId  Used to track context nodes when evaluating predicate
     *                   expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *                   the current context will be added to each result of the of the selection.
     * @return all nodes from the passed set of potential siblings, which are preceding siblings of
     * the nodes in the other set.
     */
    public static NodeSet selectPrecedingSiblings(final NodeSet candidates,
            final NodeSet references, final int contextId) {
        if(references.isEmpty() || candidates.isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        final NodeSet result = new ExtArrayNodeSet();
        final NodeSetIterator iReferences = references.iterator();
        final NodeSetIterator iCandidates = candidates.iterator();
        NodeProxy reference = iReferences.next();
        NodeProxy candidate = iCandidates.next();
        NodeProxy firstCandidate = null;
        while(true) {
            // first, try to find nodes belonging to the same doc
            if(reference.getOwnerDocument().getDocId() < candidate.getOwnerDocument().getDocId()) {
                firstCandidate = null;
                if(iReferences.hasNext()) {
                    reference = iReferences.next();
                } else {
                    break;
                }
            } else if(reference.getOwnerDocument().getDocId() > candidate.getOwnerDocument().getDocId()) {
                firstCandidate = null;
                if(iCandidates.hasNext()) {
                    candidate =iCandidates.next();
                } else {
                    break;
                }
            } else {
                // same document: check if the nodes have the same parent
                int cmp = candidate.getNodeId().getParentId().compareTo(reference.getNodeId().getParentId());
                if(cmp > 0 && candidate.getNodeId().getTreeLevel() <= reference.getNodeId().getTreeLevel()) {
                    // wrong parent: proceed
                    firstCandidate = null;
                    if(iReferences.hasNext()) {
                        reference = iReferences.next();
                    } else {
                        break;
                    }
                } else if(cmp < 0 || (cmp > 0 &&
                    candidate.getNodeId().getTreeLevel() >= reference.getNodeId().getTreeLevel())) {
                    // wrong parent: proceed
                    firstCandidate = null;
                    if(iCandidates.hasNext()) {
                        candidate = iCandidates.next();
                    } else {
                        break;
                    }
                } else {
                    if(firstCandidate == null) {
                        firstCandidate = candidate;
                    }
                    // found two nodes with the same parent
                    // now, compare the ids: a node is a following sibling
                    // if its id is greater than the id of the other node
                    cmp = candidate.getNodeId().compareTo(reference.getNodeId());
                    if(cmp < 0) {
                        // found a preceding sibling
                        final NodeProxy t = result.get(candidate);
                        if(t == null) {
                            if(Expression.IGNORE_CONTEXT != contextId) {
                                if(Expression.NO_CONTEXT_ID == contextId) {
                                    candidate.copyContext(reference);
                                } else {
                                    candidate.addContextNode(contextId, reference);
                                }
                            }
                            result.add(candidate);
                        } else if(contextId > Expression.NO_CONTEXT_ID) {
                            t.addContextNode(contextId, reference);
                        }
                        if(iCandidates.hasNext()) {
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                    } else if(cmp > 0) {
                        // found a following sibling
                        if(iCandidates.hasNext()) {
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                        // equal nodes: proceed with next node
                    } else {
                        if(iReferences.hasNext()) {
                            reference = iReferences.next();
                            iCandidates.setPosition(firstCandidate);
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Select all nodes from the passed set of potential siblings, which are
     * following siblings of the nodes in the other set.
     *
     * @param candidates The node set to check
     * @param references A node set containing potential siblings
     * @param contextId  Used to track context nodes when evaluating predicate
     *                   expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *                   the current context will be added to each result of the of the selection.
     * @return all nodes from the passed set of potential siblings, which are following siblings of
     * the nodes in the other set.
     */
    public static NodeSet selectFollowingSiblings(final NodeSet candidates,
            final NodeSet references, final int contextId) {
        if(references.isEmpty() || candidates.isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        final NodeSet result = new ExtArrayNodeSet();
        final NodeSetIterator iReferences = references.iterator();
        final NodeSetIterator iCandidates = candidates.iterator();
        NodeProxy reference = iReferences.next();
        NodeProxy candidate = iCandidates.next();
        NodeProxy firstCandidate = null;
        // TODO : review : don't care about preceding siblings
        while(true) {
            // first, try to find nodes belonging to the same doc
            if(reference.getOwnerDocument().getDocId() < candidate.getOwnerDocument().getDocId()) {
                firstCandidate = null;
                if(iReferences.hasNext()) {
                    reference = iReferences.next();
                } else {
                    break;
                }
            } else if(reference.getOwnerDocument().getDocId() > candidate.getOwnerDocument().getDocId()) {
                firstCandidate = null;
                if(iCandidates.hasNext()) {
                    candidate = iCandidates.next();
                } else {
                    break;
                }
            } else {
                // same document: check if the nodes have the same parent
                int cmp = candidate.getNodeId().getParentId().compareTo(reference.getNodeId().getParentId());
                if(cmp > 0 && candidate.getNodeId().getTreeLevel() <= reference.getNodeId().getTreeLevel()) {
                    //Do not proceed to the next "parent" if the candidate is a descendant  
                    // wrong parent: proceed
                    firstCandidate = null;
                    if(iReferences.hasNext()) {
                        reference = iReferences.next();
                    } else {
                        break;
                    }
                } else if(cmp < 0 || (cmp > 0 &&
                    candidate.getNodeId().getTreeLevel() >= reference.getNodeId().getTreeLevel())) {
                    // wrong parent: proceed
                    firstCandidate = null;
                    if(iCandidates.hasNext()) {
                        candidate = iCandidates.next();
                    } else {
                        break;
                    }
                } else {
                    if(firstCandidate == null) {
                        firstCandidate = candidate;
                    }
                    cmp = candidate.getNodeId().compareTo(reference.getNodeId());
                    // found two nodes with the same parent
                    // now, compare the ids: a node is a following sibling
                    // if its id is greater than the id of the other node
                    if(cmp < 0) {
                        // found a preceding sibling
                        if(iCandidates.hasNext()) {
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                    } else if(cmp > 0) {
                        // found a following sibling
                        final NodeProxy t = result.get(candidate);
                        if(t == null) {
                            if(Expression.IGNORE_CONTEXT != contextId) {
                                if(Expression.NO_CONTEXT_ID == contextId) {
                                    candidate.copyContext(reference);
                                } else {
                                    candidate.addContextNode(contextId, reference);
                                }
                            }
                            result.add(candidate);
                        } else {
                            t.addContextNode(contextId, reference);
                        }
                        result.add(candidate);
                        if(iCandidates.hasNext()) {
                            candidate = iCandidates.next();
                        } else if(iReferences.hasNext()) {
                            reference = iReferences.next();
                            iCandidates.setPosition(firstCandidate);
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                        // equal nodes: proceed with next node
                    } else {
                        if(iCandidates.hasNext()) {
                            candidate = iCandidates.next();
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }


     // TODO: doesn't work!!!
    public static NodeSet selectPreceding(final NodeSet references, final NodeSet candidates)
        throws XPathException {
        if(candidates.isEmpty() || references.isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy reference : references) {
            for(final NodeProxy candidate : candidates) {
                if(candidate.before(reference, true)) {
                    // TODO : add transverse context
                    candidate.addContextNode(Expression.NO_CONTEXT_ID, reference);
                    result.add(candidate);
                }
            }
        }
        return result;
    }


    // TODO: doesn't work!!!
    public static NodeSet selectFollowing(final NodeSet references,
            final NodeSet candidates) throws XPathException {
        if(candidates.isEmpty() || references.isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        final NodeSet result = new ExtArrayNodeSet();
        for(final NodeProxy reference : references) {
            for(final NodeProxy candidate : candidates) {
                if(candidate.after(reference, true)) {
                    // TODO : add transverse context
                    candidate.addContextNode(Expression.NO_CONTEXT_ID, reference);
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    public static NodeSet directSelectAttributes(final DBBroker broker,
            final NodeSet candidates, final NodeTest test, final int contextId) {
        if(candidates.isEmpty()) {
            return NodeSet.EMPTY_SET;
        }
        final NodeSet result = new ExtArrayNodeSet();
        for(final NodeProxy candidate : candidates) {
            result.addAll(candidate.directSelectAttribute(broker, test, contextId));
        }
        return result;
    }

    public static boolean directMatchAttributes(final DBBroker broker, final NodeSet candidates,
                                                final NodeTest test, final int contextId) {
        if(candidates.isEmpty()) {
            return false;
        }
        for(final NodeProxy candidate : candidates) {
            if(candidate.directMatchAttribute(broker, test, contextId)) {
                return true;
            }
        }
        return false;
    }

    public static final void copyChildren(final Document newDoc, final Node node, final Node newNode) {
        final NodeList children = node.getChildNodes();
        Node newChild;
        for(int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if(child == null) {
                continue;
            }
            switch(child.getNodeType()) {
                case Node.ELEMENT_NODE: {
                    newChild = copyNode(newDoc, child);
                    newNode.appendChild(newChild);
                    break;
                }
                case Node.TEXT_NODE: {
                    newChild = copyNode(newDoc, child);
                    newNode.appendChild(newChild);
                    break;
                }
                // TODO : error for any other one -pb
            }
        }
    }

    public static final Node copyNode(final Document newDoc, final Node node) {
        final Node newNode;
        switch(node.getNodeType()) {

            case Node.ELEMENT_NODE:
                newNode = newDoc.createElementNS(node.getNamespaceURI(), node.getNodeName());
                final NamedNodeMap attributes = node.getAttributes();
                for(int i = 0; i < attributes.getLength(); i++) {
                    final Attr attr = (Attr)attributes.item(i);
                    final Attr newAttr = newDoc.createAttributeNS(attr.getNamespaceURI(), attr.getNodeName());
                    newAttr.setValue(((Attr) node).getValue());
                    ((Element) newNode).setAttributeNode(newAttr);
                }
                copyChildren(newDoc, node, newNode);
                break;

            case Node.TEXT_NODE:
                newNode = newDoc.createTextNode(((Text) node).getData());
                break;

            default:
                // TODO : error ? -pb
                newNode = null;
        }
        return newNode;
    }
}
