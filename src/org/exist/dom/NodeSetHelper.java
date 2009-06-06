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

import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.util.Iterator;

/**
 * Collection of static methods operating on node sets.
 * 
 * @author wolf
 */
public class NodeSetHelper {

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
     * @param dl
     *            a node set containing potential child nodes
     * @param al
     *            a node set containing potential parent nodes
     * @param mode
     *            selection mode
     * @param contextId
     *            used to track context nodes when evaluating predicate
     *            expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *            the current context will be added to each result of the
     *            selection.
     */
    public static NodeSet selectParentChild(NodeSet dl, NodeSet al, int mode,
                                            int contextId) {
        ExtArrayNodeSet result = new ExtArrayNodeSet();
        DocumentImpl lastDoc = null;
        switch (mode) {
            case NodeSet.DESCENDANT:
                for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy child = (NodeProxy) i.next();
                    if (lastDoc == null || child.getDocument() != lastDoc) {
                        lastDoc = child.getDocument();
                        sizeHint = dl.getSizeHint(lastDoc);
                    }
                    NodeProxy parent = al.parentWithChild(child, true, false,
                                                          NodeProxy.UNKNOWN_NODE_LEVEL);
                    if (parent != null) {
                        if (Expression.NO_CONTEXT_ID != contextId)
                            child.deepCopyContext(parent, contextId);
                        else
                            child.copyContext(parent);
                        result.add(child, sizeHint);
                    }
                }
                break;
            case NodeSet.ANCESTOR:
                for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy child = (NodeProxy) i.next();
                    if (lastDoc == null || child.getDocument() != lastDoc) {
                        lastDoc = child.getDocument();
                        sizeHint = al.getSizeHint(lastDoc);
                    }
                    NodeProxy parent = al.parentWithChild(child, true, false,
                                                          NodeProxy.UNKNOWN_NODE_LEVEL);
                    if (parent != null) {
                        if (Expression.NO_CONTEXT_ID != contextId)
                            parent.deepCopyContext(child, contextId);
                        else
                            parent.copyContext(child);
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
     * @param dl
     *            a node set containing potential descendant nodes
     * @param al
     *            a node set containing potential ancestor nodes
     * @param mode
     *            selection mode
     * @param includeSelf
     *            if true, check if the ancestor node itself is contained in the
     *            set of descendant nodes (descendant-or-self axis)
     * @param contextId
     *            used to track context nodes when evaluating predicate
     *            expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *            the current context will be added to each result of the
     *            selection.
     * 
     */
    public static NodeSet selectAncestorDescendant(NodeSet dl, NodeSet al,
                                                   int mode, boolean includeSelf, int contextId) {
        ExtArrayNodeSet result = new ExtArrayNodeSet();
        DocumentImpl lastDoc = null;
        switch (mode) {
            case NodeSet.DESCENDANT:
                for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy descendant = (NodeProxy) i.next();
                    // get a size hint for every new document encountered
                    if (lastDoc == null || descendant.getDocument() != lastDoc) {
                        lastDoc = descendant.getDocument();
                        sizeHint = dl.getSizeHint(lastDoc);
                    }
                    NodeProxy ancestor = al.parentWithChild(descendant.getDocument(),
                                                            descendant.getNodeId(),
                                                            false, includeSelf);
                    if (ancestor != null) {
                        if (Expression.NO_CONTEXT_ID != contextId)
                            descendant.addContextNode(contextId, ancestor);
                        else
                            descendant.copyContext(ancestor);
                        result.add(descendant, sizeHint);
                    }
                }
                break;
            case NodeSet.ANCESTOR:
                for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy descendant = (NodeProxy) i.next();
                    // get a size hint for every new document encountered
                    if (lastDoc == null || descendant.getDocument() != lastDoc) {
                        lastDoc = descendant.getDocument();
                        sizeHint = al.getSizeHint(lastDoc);
                    }
                    NodeProxy ancestor = al.parentWithChild(descendant.getDocument(),
                                                            descendant.getNodeId(), false, includeSelf);
                    if (ancestor != null) {
                        if (Expression.NO_CONTEXT_ID != contextId)
                            ancestor.addContextNode(contextId, descendant);
                        else
                            ancestor.copyContext(descendant);
                        result.add(ancestor, sizeHint);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Bad 'mode' argument");
        }
        return result;
    }

    /**
     * For two sets of potential ancestor and descendant nodes, return all the
     * real ancestors having a descendant in the descendant set.
     * 
     * @param al
     *            node set containing potential ancestors
     * @param dl
     *            node set containing potential descendants
     * @param includeSelf
     *            if true, check if the ancestor node itself is contained in
     *            this node set (ancestor-or-self axis)
     * @param contextId
     *            used to track context nodes when evaluating predicate
     *            expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *            the current context will be added to each result of the of the
     *            selection.
     */
    public static NodeSet selectAncestors(NodeSet al, NodeSet dl,
                                          boolean includeSelf, int contextId) {
        NodeSet result = new NewArrayNodeSet();
        for (Iterator i = dl.iterator(); i.hasNext();) {
            NodeProxy descendant = (NodeProxy) i.next();
            NodeSet ancestors = ancestorsForChild(al, descendant, false, includeSelf);
            for (Iterator j = ancestors.iterator(); j.hasNext();) {
                NodeProxy ancestor = (NodeProxy) j.next();
                if (ancestor != null) {
                    NodeProxy temp = result.get(ancestor);
                    if (temp == null) {
                        if (Expression.IGNORE_CONTEXT != contextId) {
                            if (Expression.NO_CONTEXT_ID != contextId)
                                ancestor.addContextNode(contextId, descendant);
                            else
                                ancestor.copyContext(descendant);
                        }
                        ancestor.addMatches(descendant);
                        result.add(ancestor);
                    } else if (Expression.NO_CONTEXT_ID != contextId) {
                        temp.addContextNode(contextId, descendant);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Return all nodes contained in the node set that are ancestors of the node
     * p.
     */
    private static NodeSet ancestorsForChild(NodeSet ancestors,
                                             NodeProxy child, boolean directParent, boolean includeSelf) {
        NodeSet result = new NewArrayNodeSet(5);
        NodeId nodeId = child.getNodeId();
        NodeProxy temp = ancestors.get(child.getDocument(), nodeId);
        if (includeSelf && temp != null)
            result.add(temp);
        while (nodeId != null && nodeId != NodeId.DOCUMENT_NODE) {
        	nodeId = nodeId.getParentId();
            temp = ancestors.get(child.getDocument(), nodeId);
            if (temp != null)
                result.add(temp);
            else if (directParent)
                return result;
        }
        return result;
    }

    /**
     * Select all nodes from the passed set of potential siblings, which are
     * preceding siblings of the nodes in the other set.
     * 
     * @param candidates
     *            the node set to check
     * @param references
     *            a node set containing potential siblings
     * @param contextId
     *            used to track context nodes when evaluating predicate
     *            expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *            the current context will be added to each result of the of the
     *            selection.
     */
    public static NodeSet selectPrecedingSiblings(NodeSet candidates,
                                                  NodeSet references, int contextId) {
        if (references.isEmpty() || candidates.isEmpty())
            return NodeSet.EMPTY_SET;
        NodeSet result = new ExtArrayNodeSet();
        NodeSetIterator iReferences = references.iterator();
        NodeSetIterator iCandidates = candidates.iterator();
        NodeProxy reference = (NodeProxy) iReferences.next();
        NodeProxy candidate = (NodeProxy) iCandidates.next();
        NodeProxy firstCandidate = null;
        while (true) {
            // first, try to find nodes belonging to the same doc
            if (reference.getDocument().getDocId() < candidate.getDocument()
                .getDocId()) {
                firstCandidate = null;
                if (iReferences.hasNext())
                    reference = (NodeProxy) iReferences.next();
                else
                    break;
            } else if (reference.getDocument().getDocId() > candidate
                       .getDocument().getDocId()) {
                firstCandidate = null;
                if (iCandidates.hasNext())
                    candidate = (NodeProxy) iCandidates.next();
                else
                    break;
            } else {
                // same document: check if the nodes have the same parent
                int cmp = candidate.getNodeId().getParentId().compareTo(reference.getNodeId().getParentId());
                if (cmp > 0 && candidate.getNodeId().getTreeLevel() <= reference.getNodeId().getTreeLevel()) {
                    // wrong parent: proceed
                    firstCandidate = null;
                    if (iReferences.hasNext())
                        reference = (NodeProxy) iReferences.next();
                    else
                        break;
                } else if (cmp < 0  || (cmp > 0 && candidate.getNodeId().getTreeLevel() >= reference.getNodeId().getTreeLevel())) {
                	//Why did I have to invert the test ? ----------------------------^^^^^
                    // wrong parent: proceed
                    firstCandidate = null;
                    if (iCandidates.hasNext())
                        candidate = (NodeProxy) iCandidates.next();
                    else
                        break;
                } else {
                    if (firstCandidate == null)
                        firstCandidate = candidate;
                    
                    // found two nodes with the same parent
                    // now, compare the ids: a node is a following sibling
                    // if its id is greater than the id of the other node
                    cmp = candidate.getNodeId().compareTo(reference.getNodeId());
                    if (cmp < 0) {
                        // found a preceding sibling
                        NodeProxy t = result.get(candidate);
                        if (t == null) {
                            if (Expression.IGNORE_CONTEXT != contextId) {
                                if (Expression.NO_CONTEXT_ID == contextId) {
                                    candidate.copyContext(reference);
                                } else {
                                    candidate.addContextNode(contextId,
                                                             reference);
                                }
                            }
                            result.add(candidate);
                        } else if (contextId > Expression.NO_CONTEXT_ID){
                            t.addContextNode(contextId, reference);
                        }
                        if (iCandidates.hasNext())
                            candidate = (NodeProxy) iCandidates.next();
                        else
                            break;
                    } else if (cmp > 0) {
                        // found a following sibling
                        if (iCandidates.hasNext())
                            // TODO : break ?
                            candidate = (NodeProxy) iCandidates.next();
                        else
                            break;
                        // equal nodes: proceed with next node
                    } else {
                        if (iReferences.hasNext()) {
                            reference = (NodeProxy) iReferences.next();
                            iCandidates.setPosition(firstCandidate);
                            candidate = (NodeProxy) iCandidates.next();
                        } else
                            break;
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
     * @param candidates
     *            the node set to check
     * @param references
     *            a node set containing potential siblings
     * @param contextId
     *            used to track context nodes when evaluating predicate
     *            expressions. If contextId != {@link Expression#NO_CONTEXT_ID},
     *            the current context will be added to each result of the of the
     *            selection.
     */
    public static NodeSet selectFollowingSiblings(NodeSet candidates,
                                                  NodeSet references, int contextId) {
        if (references.isEmpty() || candidates.isEmpty())
            return NodeSet.EMPTY_SET;
        NodeSet result = new ExtArrayNodeSet();
        NodeSetIterator iReferences = references.iterator();
        NodeSetIterator iCandidates = candidates.iterator();
        NodeProxy reference = (NodeProxy) iReferences.next();
        NodeProxy candidate = (NodeProxy) iCandidates.next();
        NodeProxy firstCandidate = null;
        // TODO : review : don't care about preceding siblings
        while (true) {
            // first, try to find nodes belonging to the same doc
            if (reference.getDocument().getDocId() < candidate.getDocument()
                .getDocId()) {
                firstCandidate = null;
                if (iReferences.hasNext())
                    reference = (NodeProxy) iReferences.next();
                else
                    break;
            } else if (reference.getDocument().getDocId() > candidate
                       .getDocument().getDocId()) {
                firstCandidate = null;
                if (iCandidates.hasNext())
                    candidate = (NodeProxy) iCandidates.next();
                else
                    break;
            } else {
                // same document: check if the nodes have the same parent
                int cmp = candidate.getNodeId().getParentId().compareTo(reference.getNodeId().getParentId());
                if (cmp > 0 && candidate.getNodeId().getTreeLevel() <= reference.getNodeId().getTreeLevel()) {
                    //Do not proceed to the next "parent" if the candidate is a descendant  
                    // wrong parent: proceed
                    firstCandidate = null;
                    if (iReferences.hasNext())
                        reference = (NodeProxy) iReferences.next();
                    else
                        break;
                } else if (cmp < 0  || (cmp > 0 && candidate.getNodeId().getTreeLevel() >= reference.getNodeId().getTreeLevel())) {
                	//Why did I have to invert the test ? ----------------------------^^^^^
                	// wrong parent: proceed
                    firstCandidate = null;
                    if (iCandidates.hasNext())
                        candidate = (NodeProxy) iCandidates.next();
                    else
                        break;
                } else {
                    if (firstCandidate == null)
                        firstCandidate = candidate;
                    
                    cmp = candidate.getNodeId().compareTo(reference.getNodeId());
                    
                    // found two nodes with the same parent
                    // now, compare the ids: a node is a following sibling
                    // if its id is greater than the id of the other node
                    if (cmp < 0) {
                        // found a preceding sibling
                        if (iCandidates.hasNext())
                            candidate = (NodeProxy) iCandidates.next();
                        else
                            break;
                    } else if (cmp > 0) {
                        // found a following sibling
                        NodeProxy t = result.get(candidate);
                        if (t == null) {
                            if (Expression.IGNORE_CONTEXT != contextId) {
                                if (Expression.NO_CONTEXT_ID == contextId) {
                                    candidate.copyContext(reference);
                                } else {
                                    candidate.addContextNode(contextId,
                                                             reference);
                                }
                            }
                            result.add(candidate);
                        } else {
                            t.addContextNode(contextId, reference);
                        }
                        result.add(candidate);
                        if (iCandidates.hasNext())
                            candidate = (NodeProxy) iCandidates.next();
                        else if (iReferences.hasNext()) {
                            reference = (NodeProxy) iReferences.next();
                            iCandidates.setPosition(firstCandidate);
                            candidate = (NodeProxy) iCandidates.next();
                        } 
                        else
                            break;
                        // equal nodes: proceed with next node
                    } else {
                        if (iCandidates.hasNext())
                            candidate = (NodeProxy) iCandidates.next();
                        else
                            break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * TODO: doesn't work!!!
     */
    public static NodeSet selectPreceding(NodeSet references, NodeSet candidates)
        throws XPathException {
        if (candidates.isEmpty() || references.isEmpty())
            return NodeSet.EMPTY_SET;
        NodeSet result = new NewArrayNodeSet();
        for (Iterator iReferences = references.iterator(); iReferences
                 .hasNext();) {
            NodeProxy reference = (NodeProxy) iReferences.next();
            for (Iterator iCandidates = candidates.iterator(); iCandidates
                     .hasNext();) {
                NodeProxy candidate = (NodeProxy) iCandidates.next();
                if (candidate.before(reference, true)) {
                    // TODO : add transverse context
                    candidate.addContextNode(Expression.NO_CONTEXT_ID,
                                             reference);
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /**
     * TODO: doesn't work!!!
     */
    public static NodeSet selectFollowing(NodeSet references, NodeSet candidates)
        throws XPathException {
        if (candidates.isEmpty() || references.isEmpty())
            return NodeSet.EMPTY_SET;
        NodeSet result = new ExtArrayNodeSet();
        for (Iterator iReferences = references.iterator(); iReferences
                 .hasNext();) {
            NodeProxy reference = (NodeProxy) iReferences.next();
            for (Iterator iCandidates = candidates.iterator(); iCandidates
                     .hasNext();) {
                NodeProxy candidate = (NodeProxy) iCandidates.next();
                if (candidate.after(reference, true)) {
                    // TODO : add transverse context
                    candidate.addContextNode(Expression.NO_CONTEXT_ID,
                                             reference);
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    public static NodeSet directSelectAttributes(DBBroker broker, NodeSet candidates,
                                                 org.exist.xquery.NodeTest test, int contextId) {
        if (candidates.isEmpty())
            return NodeSet.EMPTY_SET;
        NodeSet result = new ExtArrayNodeSet();
        for (Iterator iCandidates = candidates.iterator(); iCandidates
                 .hasNext();) {
            NodeProxy candidate = (NodeProxy) iCandidates.next();
            result.addAll(candidate.directSelectAttribute(broker, test, contextId));
        }
        return result;
    }

    public final static void copyChildren(Document new_doc, Node node,
                                          Node new_node) {
        NodeList children = node.getChildNodes();
        Node new_child;
        for (int i = 0; i < children.getLength(); i++) {
        	Node child = children.item(i);
            if (child == null)
                continue;
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE: {
                    new_child = copyNode(new_doc, child);
                    new_node.appendChild(new_child);
                    break;
                }
                case Node.ATTRIBUTE_NODE: {
                    new_child = copyNode(new_doc, child);
                    ((Element) new_node).setAttributeNode((Attr) new_child);
                    break;
                }
                case Node.TEXT_NODE: {
                    new_child = copyNode(new_doc, child);
                    new_node.appendChild(new_child);
                    break;
                }
                    // TODO : error for any other one -pb
            }
        }
    }

    public final static Node copyNode(Document new_doc, Node node) {
        Node new_node;
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                new_node = new_doc.createElementNS(node.getNamespaceURI(), node
                                                   .getNodeName());
                copyChildren(new_doc, node, new_node);
                return new_node;
            case Node.TEXT_NODE:
                new_node = new_doc.createTextNode(((Text) node).getData());
                return new_node;
            case Node.ATTRIBUTE_NODE:
                new_node = new_doc.createAttributeNS(node.getNamespaceURI(), node
                                                     .getNodeName());
                ((Attr) new_node).setValue(((Attr) node).getValue());
                return new_node;
            default:
                // TODO : error ? -pb
                return null;
        }
    }
}
