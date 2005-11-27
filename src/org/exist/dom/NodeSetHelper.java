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
		NodeProxy n, p;
		//		long start = System.currentTimeMillis();
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		switch (mode) {
			case NodeSet.DESCENDANT :
				for (Iterator i = dl.iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if (lastDoc == null || n.getDocument() != lastDoc) {
						lastDoc = n.getDocument();
						sizeHint = dl.getSizeHint(lastDoc);
					}
					if ((p = al.parentWithChild(n, true, false, -1))
						!= null) {
						if (rememberContext)
							n.addContextNode(p);
						else
							n.copyContext(p);
						result.add(n, sizeHint);
					}
				}
				break;
			case NodeSet.ANCESTOR :
				for (Iterator i = dl.iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if (lastDoc == null || n.getDocument() != lastDoc) {
						lastDoc = n.getDocument();
						sizeHint = al.getSizeHint(lastDoc);
					}
					if ((p = al.parentWithChild(n, true, false, -1))
						!= null) {
						if (rememberContext)
							p.addContextNode(n);
						else
							p.copyContext(n);
						result.add(p, sizeHint);
					}
				}
				break;
		}
		//				LOG.debug(
		//					"getChildren found "
		//						+ result.getLength()
		//						+ " in "
		//						+ (System.currentTimeMillis() - start));
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
	public static NodeSet selectAncestorDescendant(
	    NodeSet dl,
		NodeSet al,
		int mode,
		boolean includeSelf,
		boolean rememberContext) {
		NodeProxy n, p;
		//		long start = System.currentTimeMillis();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		switch (mode) {
			case NodeSet.DESCENDANT :
				for (Iterator i = dl.iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || n.getDocument() != lastDoc) {
						lastDoc = n.getDocument();
						sizeHint = dl.getSizeHint(lastDoc);
					}
					if ((p = al.parentWithChild(n.getDocument(), n.getGID(), false, includeSelf,
							NodeProxy.UNKNOWN_NODE_LEVEL ))
						!= null) {
						if (rememberContext)
							n.addContextNode(p);
						else
							n.copyContext(p);
						result.add(n, sizeHint);
					}
				}
				break;
			case NodeSet.ANCESTOR :
				for (Iterator i = dl.iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || n.getDocument() != lastDoc) {
						lastDoc = n.getDocument();
						sizeHint = al.getSizeHint(lastDoc);
					}
					p = al.parentWithChild(n.getDocument(), n.getGID(), false, includeSelf,
							NodeProxy.UNKNOWN_NODE_LEVEL );
					if (p != null) {
						if (rememberContext)
							p.addContextNode(n);
						else
							p.copyContext(n);
						result.add(p, sizeHint);
					}
				}
				break;
		}
		//				LOG.debug(
		//					"getDescendants found "
		//						+ result.getLength()
		//						+ " in "
		//						+ (System.currentTimeMillis() - start));
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
    public static NodeSet selectAncestors(
        NodeSet al,
		NodeSet dl,
		boolean includeSelf,
		boolean rememberContext) {
		NodeProxy n, p, temp;
		NodeSet result = new ExtArrayNodeSet();
		NodeSet ancestors;
		for (Iterator i = dl.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			ancestors = ancestorsForChild(al, n, false, includeSelf, -1);
			for(Iterator j = ancestors.iterator(); j.hasNext(); ) {
			    p = (NodeProxy) j.next();
				if (p != null) {
					if ((temp = result.get(p)) == null) {
						if (rememberContext)
							p.addContextNode(n);
						else
							p.copyContext(n);
						result.add(p);
					} else if (rememberContext)
						temp.addContextNode(n);
				}
			}
		}
		return result;
	}
    
    /**
	 * Return all nodes contained in the node set that are ancestors of the node p.  
	 */
	private static NodeSet ancestorsForChild(
	    NodeSet ancestors,
		NodeProxy p,
		boolean directParent,
		boolean includeSelf,
		int level) {
	    NodeSet result = new ArraySet(5);
		NodeProxy temp;
		long gid = p.getGID();
		if (includeSelf && (temp = ancestors.get(p.getDocument(), gid)) != null)
			result.add(temp);
		if (level == NodeProxy.UNKNOWN_NODE_LEVEL)
			level = p.getDocument().getTreeLevel(gid);
		while (gid > 0) {
			gid = XMLUtil.getParentId(p.getDocument(), gid, level);
			if ((temp = ancestors.get(p.getDocument(), gid)) != null)
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
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		long pa, pb;
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
				pa = XMLUtil.getParentId(na.getDocument(), na.getGID());
				pb = XMLUtil.getParentId(nb.getDocument(), nb.getGID());
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
//            System.out.println("Context " + sn.toString());
            for (Iterator fi = following.iterator(); fi.hasNext(); ) {
                NodeProxy fn = (NodeProxy) fi.next();
//                System.out.println("Checking " + fn.toString());
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
//            System.out.println("Context " + sn.toString());
            for (Iterator fi = following.iterator(); fi.hasNext(); ) {
                NodeProxy fn = (NodeProxy) fi.next();
//                System.out.println("Checking " + fn.toString());
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