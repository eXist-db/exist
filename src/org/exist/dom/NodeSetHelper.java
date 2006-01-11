/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.dom;

import java.util.Iterator;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * Collection of static methods operating on node sets.
 * 
 * @author wolf
 */
public class NodeSetHelper {

    /**
	 * For two given sets of potential parent and child nodes, find
	 * those nodes from the child set that actually have parents in the
	 * parent set, i.e. the parent-child relationship is true.
	 * 
	 * The method returns either the matching descendant or ancestor nodes,
	 * depending on the mode constant.
	 * 
	 * If mode is {@link #DESCENDANT}, the returned node set will contain
	 * all child nodes found in this node set for each parent node. If mode is
	 * {@link #ANCESTOR}, the returned set will contain those parent nodes,
	 * for which children have been found.
	 *  
	 * @param dl a node set containing potential child nodes
	 * @param al a node set containing potential parent nodes
	 * @param mode selection mode
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 * @return
	 */
	public static NodeSet selectParentChild(NodeSet dl, NodeSet al, int mode, boolean rememberContext) {
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;		
		switch (mode) {
			case NodeSet.DESCENDANT :
				for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy child = (NodeProxy) i.next();
					if (lastDoc == null || child.getDocument() != lastDoc) {
						lastDoc = child.getDocument();
						sizeHint = dl.getSizeHint(lastDoc);
					}
                    NodeProxy parent = al.parentWithChild(child, true, false, NodeProxy.UNKNOWN_NODE_LEVEL);
					if (parent != null) {
						if (rememberContext)
                            child.addContextNode(parent);
						else
                            child.copyContext(parent);
						result.add(child, sizeHint);
					}
				}
				break;
			case NodeSet.ANCESTOR :
				for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy child = (NodeProxy) i.next();
					if (lastDoc == null || child.getDocument() != lastDoc) {
						lastDoc = child.getDocument();
						sizeHint = al.getSizeHint(lastDoc);
					}
                    NodeProxy parent = al.parentWithChild(child, true, false, NodeProxy.UNKNOWN_NODE_LEVEL);
					if (parent != null) {
						if (rememberContext)
                            parent.addContextNode(child);
						else
                            parent.copyContext(child);
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
	 * For two given sets of potential ancestor and descendant nodes, find
	 * those nodes from the descendant set that actually have ancestors in the
	 * ancestor set, i.e. the ancestor-descendant relationship is true.
	 * 
	 * The method returns either the matching descendant or ancestor nodes,
	 * depending on the mode constant.
	 * 
	 * If mode is {@link #DESCENDANT}, the returned node set will contain
	 * all descendant nodes found in this node set for each ancestor. If mode is
	 * {@link #ANCESTOR}, the returned set will contain those ancestor nodes,
	 * for which descendants have been found.
	 * 
	 * @param dl a node set containing potential descendant nodes
	 * @param al a node set containing potential ancestor nodes
	 * @param mode selection mode
	 * @param includeSelf if true, check if the ancestor node itself is contained in
	 * the set of descendant nodes (descendant-or-self axis)
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 * 
	 * @return
	 */
	public static NodeSet selectAncestorDescendant(NodeSet dl, NodeSet al, int mode, boolean includeSelf,
	        boolean rememberContext) {		
        ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		switch (mode) {
			case NodeSet.DESCENDANT :
				for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy descendant = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || descendant.getDocument() != lastDoc) {
						lastDoc = descendant.getDocument();
						sizeHint = dl.getSizeHint(lastDoc);
					}
                    NodeProxy ancestor = al.parentWithChild(descendant.getDocument(), descendant.getGID(), false, includeSelf,
                            NodeProxy.UNKNOWN_NODE_LEVEL);
					if (ancestor != null) {
						if (rememberContext)
                            descendant.addContextNode(ancestor);
						else
                            descendant.copyContext(ancestor);
						result.add(descendant, sizeHint);
					}
				}
				break;
			case NodeSet.ANCESTOR :
				for (Iterator i = dl.iterator(); i.hasNext();) {
                    int sizeHint = Constants.NO_SIZE_HINT;
                    NodeProxy descendant = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || descendant.getDocument() != lastDoc) {
						lastDoc = descendant.getDocument();
						sizeHint = al.getSizeHint(lastDoc);
					}
                    NodeProxy ancestor = al.parentWithChild(descendant.getDocument(), descendant.getGID(), false, includeSelf,
							NodeProxy.UNKNOWN_NODE_LEVEL);
					if (ancestor != null) {
						if (rememberContext)
                            ancestor.addContextNode(descendant);
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
	 * @param  al node set containing potential ancestors
	 * @param dl node set containing potential descendants
	 * @param includeSelf if true, check if the ancestor node itself is contained
	 * in this node set (ancestor-or-self axis)
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 *@return
	 */
    public static NodeSet selectAncestors(NodeSet al, NodeSet dl, boolean includeSelf, boolean rememberContext) {		 
		NodeSet result = new ExtArrayNodeSet();		
		for (Iterator i = dl.iterator(); i.hasNext();) {
            NodeProxy descendant = (NodeProxy) i.next();
            NodeSet ancestors = ancestorsForChild(al, descendant, false, includeSelf, NodeProxy.UNKNOWN_NODE_LEVEL);
			for(Iterator j = ancestors.iterator(); j.hasNext(); ) {
                NodeProxy ancestor = (NodeProxy) j.next();
				if (ancestor != null) {
                    NodeProxy temp = result.get(ancestor);
					if (temp == null) {
						if (rememberContext)
                            ancestor.addContextNode(descendant);
						else
                            ancestor.copyContext(descendant);
						result.add(ancestor);
					} else if (rememberContext)
						temp.addContextNode(descendant);
				}
			}
		}
		return result;
	}
    
    /**
	 * Return all nodes contained in the node set that are ancestors of the node p.  
	 */
	private static NodeSet ancestorsForChild(NodeSet ancestors,	NodeProxy child, boolean directParent,
	        boolean includeSelf, int level) {
	    NodeSet result = new ExtArrayNodeSet(5);
        long gid = child.getGID();
		NodeProxy temp = ancestors.get(child.getDocument(), gid);		
		if (includeSelf && temp != null)
			result.add(temp);
		if (level == NodeProxy.UNKNOWN_NODE_LEVEL)
			level = child.getDocument().getTreeLevel(gid);
		while (gid > 0) {
			gid = XMLUtil.getParentId(child.getDocument(), gid, level);
            temp = ancestors.get(child.getDocument(), gid);
			if (temp != null)
				result.add(temp);
			else if (directParent)
				return result;
			--level;
		}
		return result;
	}
	
	/**
	 * Select all nodes from the passed set of potential siblings, which
	 * are preceding or following siblings of the nodes in
	 * the other set. If mode is {@link #FOLLOWING}, only following
	 * nodes are selected. {@link #PRECEDING} selects
	 * preceding nodes.
	 * 
	 * @param set the node set to check
	 * @param siblings a node set containing potential siblings
	 * @param mode either FOLLOWING or PRECEDING
	 * @return
	 */
	public static NodeSet selectSiblings(NodeSet set, NodeSet siblings, int mode) {
		if (siblings.getLength() == 0 || set.getLength() == 0)
			return NodeSet.EMPTY_SET;
		NodeSet result = new ExtArrayNodeSet();
		Iterator ia = siblings.iterator();
		Iterator ib = set.iterator();
        NodeProxy na = (NodeProxy) ia.next();
        NodeProxy nb = (NodeProxy) ib.next();		
		while (true) {
			// first, try to find nodes belonging to the same doc
			if (na.getDocument().getDocId() < nb.getDocument().getDocId()) {
				if (ia.hasNext())
					na = (NodeProxy) ia.next();
				else
					break;
			} else if (na.getDocument().getDocId() > nb.getDocument().getDocId()) {
				if (ib.hasNext())
					nb = (NodeProxy) ib.next();
				else
					break;
			} else {
				// same document: check if the nodes have the same parent
                long pa = XMLUtil.getParentId(na.getDocument(), na.getGID());
                long pb = XMLUtil.getParentId(nb.getDocument(), nb.getGID());
				if (pa < pb) {
					// wrong parent: proceed
					if (ia.hasNext())
						na = (NodeProxy) ia.next();
					else
						break;
				} else if (pa > pb) {
					// wrong parent: proceed
					if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				} else {
					// found two nodes with the same parent
					// now, compare the ids: a node is a following sibling
					// if its id is greater than the id of the other node
					if (nb.getGID() < na.getGID()) {
						// found a preceding sibling
						if (mode == NodeSet.PRECEDING) { 
                            nb.addContextNode(na);                            
                            result.add(nb);
                        }
						if (ib.hasNext())
							nb = (NodeProxy) ib.next();
						else
							break;
					} else if (nb.getGID() > na.getGID()) {
						// found a following sibling						
						if (mode == NodeSet.FOLLOWING) {
                            nb.addContextNode(na);                            
                            result.add(nb);
                        }
						if (ib.hasNext())
							nb = (NodeProxy) ib.next();
						else
							break;
						// equal nodes: proceed with next node
					} else if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				}
			}
		}
		return result;
	}
	
	public static NodeSet selectFollowing(NodeSet set, NodeSet following) throws XPathException {
		if (following.getLength() == 0 || set.getLength() == 0)
			return NodeSet.EMPTY_SET;
		NodeSet result = new ExtArrayNodeSet();
		for (Iterator si = set.iterator(); si.hasNext(); ) {
            NodeProxy sn = (NodeProxy) si.next();
            for (Iterator fi = following.iterator(); fi.hasNext(); ) {
                NodeProxy fn = (NodeProxy) fi.next();
                if (fn.after(sn)) {
                    fn.addContextNode(sn);
                    result.add(fn);
                }
            }
        }
		return result;
	}
    
    public static NodeSet selectPreceding(NodeSet set, NodeSet following) throws XPathException {
        if (following.getLength() == 0 || set.getLength() == 0)
            return NodeSet.EMPTY_SET;
        NodeSet result = new ExtArrayNodeSet();
        for (Iterator si = set.iterator(); si.hasNext(); ) {
            NodeProxy sn = (NodeProxy) si.next();
            for (Iterator fi = following.iterator(); fi.hasNext(); ) {
                NodeProxy fn = (NodeProxy) fi.next();
                if (fn.before(sn)) {
                    fn.addContextNode(sn);
                    result.add(fn);
                }
            }
        }
        return result;
    }
    
    public static NodeSet directSelectAttributes(NodeSet set, QName qname, boolean rememberContext) {
        NodeSet result = new ExtArrayNodeSet();
        for (Iterator i = set.iterator(); i.hasNext(); ) {
            NodeProxy n = (NodeProxy) i.next();
            result.addAll(n.directSelectAttribute(qname, rememberContext));
        }
        return result;
    }
}