
/* eXist Open Source Native XML Database
 * Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.dom;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.util.Range;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

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
	
	protected AbstractNodeSet() {
	}
	
	/**
	 * Return an iterator on the nodes in this list. The iterator returns nodes
	 * according to the internal ordering of nodes (i.e. level first), not in document-
	 * order.
	 * 
	 * @return
	 */
	public abstract Iterator iterator();

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public abstract SequenceIterator iterate();

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#unorderedIterator()
	 */
	public abstract SequenceIterator unorderedIterator();
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.NODE;
	}

	/**
	 * Check if this node set contains a node matching the given
	 * document and node-id.
	 * 
	 * @param doc
	 * @param nodeId
	 * @return
	 */
	public abstract boolean contains(DocumentImpl doc, long nodeId);

	/**
	 * Check if this node set contains a node matching the document and
	 * node-id of the given NodeProxy object.
	 * 
	 * @param proxy
	 * @return
	 */
	public abstract boolean contains(NodeProxy proxy);

	/**
	 * Check if this node set contains nodes belonging to the given document.
	 * 
	 * @param doc
	 * @return
	 */
	public boolean containsDoc(DocumentImpl doc) {
		return true;
	}

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
	public void add(Item item) throws XPathException {
		if (!Type.subTypeOf(item.getType(), Type.NODE))
			throw new XPathException("item has wrong type");
		add((NodeProxy) item);
	}

	/**
	 * Add all items from the given sequence to the node set. All items
	 * have to be a subtype of node.
	 * 
	 * @param other
	 * @throws XPathException
	 */
	public void addAll(Sequence other) throws XPathException {
		if (!Type.subTypeOf(other.getItemType(), Type.NODE))
			throw new XPathException("sequence argument is not a node sequence");
		for (SequenceIterator i = other.iterate(); i.hasNext();) {
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
	 * Remove a node. By default, this method throws a
	 * RuntimeException.
	 * 
	 * @param node
	 */
	public void remove(NodeProxy node) {
		throw new RuntimeException(
			"remove not implemented for class " + getClass().getName());
	}

	/**
	 * Return the number of nodes contained in this node set.
	 */
	public abstract int getLength();

	public abstract Node item(int pos);

	/**
	 * Get the node at position pos within this node set.
	 * @param pos
	 * @return
	 */
	public abstract NodeProxy get(int pos);

	/**
	 * Get a node from this node set matching the document and node id of
	 * the given NodeProxy.
	 *  
	 * @param p
	 * @return
	 */
	public abstract NodeProxy get(NodeProxy p);

	/**
	 * Get a node from this node set matching the document and node id.
	 * 
	 * @param doc
	 * @param nodeId
	 * @return
	 */
	public abstract NodeProxy get(DocumentImpl doc, long nodeId);

	public DocumentSet getDocumentSet() {
		DocumentSet ds = new DocumentSet();
		NodeProxy p;
		for(Iterator i = iterator(); i.hasNext(); ) {
			p = (NodeProxy)i.next();
			ds.add(p.doc);
		}
		return ds;
	}
	
	/**
	 * Get all children of the given parent node contained in this node set.
	 * If mode is {@link #DESCENDANT}, the returned node set will contain
	 * all children found in this node set. If mode is {@link #ANCESTOR},
	 * the parent itself will be returned if it has child nodes in this set.
	 * 
	 * @param parent
	 * @param mode
	 * @param rememberContext
	 * @return
	 */
	public NodeSet hasChildrenInSet(
		NodeProxy parent,
		int mode,
		boolean rememberContext) {
		Range range = XMLUtil.getChildRange(parent.doc, parent.gid);
		return getRange(parent.doc, range.getStart(), range.getEnd());
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
	 * @return
	 */
	public NodeSet selectParentChild(NodeSet al, int mode) {
		return selectParentChild(al, mode, false);
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
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 * @return
	 */
	public NodeSet selectParentChild(NodeSet al, int mode, boolean rememberContext) {
		if (!(al instanceof VirtualNodeSet)) {
		    if(al.getLength() == 1)
		        return hasChildrenInSet(al.get(0), mode, rememberContext);
//		    else
//		        return quickSelectParentChild(al, mode, rememberContext);
		}
		NodeProxy n, p;
		//		long start = System.currentTimeMillis();
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		switch (mode) {
			case NodeSet.DESCENDANT :
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if (lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = getSizeHint(lastDoc);
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
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if (lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
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
	 * @return
	 */
	public NodeSet selectAncestorDescendant(NodeSet al, int mode) {
		return selectAncestorDescendant(al, mode, false);
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
	 * @return
	 */
	public NodeSet selectAncestorDescendant(NodeSet al, int mode, boolean includeSelf) {
		return selectAncestorDescendant(al, mode, includeSelf, false);
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
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 * @return
	 */
	public NodeSet selectAncestorDescendant(
		NodeSet al,
		int mode,
		boolean includeSelf,
		boolean rememberContext) {
	    if(!(al instanceof VirtualNodeSet))
	        return quickSelectAncestorDescendant(al, mode, rememberContext);
		NodeProxy n, p;
		//		long start = System.currentTimeMillis();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		switch (mode) {
			case DESCENDANT :
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = getSizeHint(lastDoc);
					}
					if ((p = al.parentWithChild(n.doc, n.gid, false, includeSelf, -1))
						!= null) {
						if (rememberContext)
							n.addContextNode(p);
						else
							n.copyContext(p);
						result.add(n, sizeHint);
					}
				}
				break;
			case ANCESTOR :
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					// get a size hint for every new document encountered
					if (lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = al.getSizeHint(lastDoc);
					}
					p = al.parentWithChild(n.doc, n.gid, false, includeSelf, -1);
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
		//result.sort();
		//				LOG.debug(
		//					"getDescendants found "
		//						+ result.getLength()
		//						+ " in "
		//						+ (System.currentTimeMillis() - start));
		return result;
	}

	/**
	 * Fast ancestor descendant join based on two iterators. This method is
	 * selected if the ancestor set is fixed, i.e. the selection step did not contain
	 * any wildcards.
	 * 
	 * @param al
	 * @param mode
	 * @param rememberContext
	 * @return
	 */
	private NodeSet quickSelectAncestorDescendant(NodeSet al, int mode, boolean rememberContext) {
	    final NodeSet result = new ExtArrayNodeSet();
		final Iterator ia = al.iterator();
		final Iterator ib = iterator();
		final long start = System.currentTimeMillis();
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		long pa, pb;
		while (true) {
			// first, try to find nodes belonging to the same doc
			if (na.doc.getDocId() < nb.doc.getDocId()) {
				if (ia.hasNext())
					na = (NodeProxy) ia.next();
				else
					break;
			} else if (na.doc.getDocId() > nb.doc.getDocId()) {
				if (ib.hasNext())
					nb = (NodeProxy) ib.next();
				else
					break;
			} else {
			    // same document
			    pa = na.gid; 
			    pb = nb.gid;
			    int la = na.doc.getTreeLevel(pa);
				int lb = nb.doc.getTreeLevel(pb);
				while (la < lb) {
					pb = XMLUtil.getParentId(nb.doc, pb, lb);
					--lb;
				}
				if (pa < pb) {
					if (ia.hasNext())
						na = (NodeProxy) ia.next();
					else
						break;
				} else if (pa > pb) {
					if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				} else {
				    if(mode == NodeSet.DESCENDANT) {
				        if (rememberContext)
				            nb.addContextNode(na);
				        else
				            nb.copyContext(na);
				        result.add(nb);
				    } else {
				        if (rememberContext)
				            na.addContextNode(nb);
				        else
				            na.copyContext(nb);
				        result.add(na);
				    }
				    if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				}
			}
		}
		LOG.debug("quickSelect took " + (System.currentTimeMillis() - start));
		return result;
	}
	
	private NodeSet quickSelectParentChild(NodeSet al, int mode, boolean rememberContext) {
	    final NodeSet result = new ExtArrayNodeSet();
		final Iterator ia = al.iterator();
		final Iterator ib = iterator();
		final long start = System.currentTimeMillis();
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		long pa, pb;
		while (true) {
			// first, try to find nodes belonging to the same doc
			if (na.doc.getDocId() < nb.doc.getDocId()) {
				if (ia.hasNext())
					na = (NodeProxy) ia.next();
				else
					break;
			} else if (na.doc.getDocId() > nb.doc.getDocId()) {
				if (ib.hasNext())
					nb = (NodeProxy) ib.next();
				else
					break;
			} else {
			    // same document
			    pa = na.gid;
			    pb = nb.gid;
//			    System.out.println(pa + " -> " + pb);
				pb = XMLUtil.getParentId(nb.doc, pb, nb.doc.getTreeLevel(pb));
//				System.out.println("comparing " + pa + " -> " + pb);
				if(pa == pb) {
				    if(mode == NodeSet.DESCENDANT) {
				        if (rememberContext)
				            nb.addContextNode(na);
				        else
				            nb.copyContext(na);
				        result.add(nb);
				    } else {
				        if (rememberContext)
				            na.addContextNode(nb);
				        else
				            na.copyContext(nb);
				        result.add(na);
				    }
				    if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				} else if (pa < pb) {
					if (ia.hasNext())
						na = (NodeProxy) ia.next();
					else
						break;
				} else {
					if (ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				}
			}
		}
		LOG.debug("quickSelect took " + (System.currentTimeMillis() - start));
		return result;
	}
	
	/**
	 * For a given set of potential ancestor nodes, return all ancestors
	 * having descendants in this node set.
	 *
	 *@param  al    node set containing potential ancestors
	 * @param includeSelf if true, check if the ancestor node itself is contained
	 * in this node set (ancestor-or-self axis)
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 *@return
	 */
	public NodeSet selectAncestors(
		NodeSet dl,
		boolean includeSelf,
		boolean rememberContext) {
		NodeProxy n, p, temp;
		NodeSet result = new ExtArrayNodeSet();
		NodeSet ancestors;
		for (Iterator i = dl.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			ancestors = ancestorsForChild(n.doc, n.gid, false, includeSelf, -1);
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

	public NodeSet selectFollowing(NodeSet following) throws XPathException {
		if (following.getLength() == 0 || getLength() == 0)
			return EMPTY_SET;
		NodeSet result = new ExtArrayNodeSet();
		Iterator ia = iterator();
		Iterator ib = following.iterator();
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		while(true) {
			if(na.doc.getDocId() < nb.doc.getDocId()) {
				if(ia.hasNext())
					na = (NodeProxy) ia.next();
				else
					break;
			} else if(na.doc.getDocId() > nb.doc.getDocId()) {
				if(ib.hasNext())
					nb = (NodeProxy) ib.next();
				else
					break;
			} else {
				if(nb.after(na, false)) {
					nb.addContextNode(na);
					result.add(nb);
				} else {
					if(ib.hasNext())
						nb = (NodeProxy) ib.next();
					else
						break;
				}
			}
		}
		return result;
	}
	
	/**
	 * Select all nodes from the passed node set, which
	 * are preceding or following siblings of the nodes in
	 * this set. If mode is {@link #FOLLOWING}, only nodes following
	 * the context node are selected. {@link #PRECEDING} selects
	 * preceding nodes.
	 * 
	 * @param siblings a node set containing potential siblings
	 * @param mode either FOLLOWING or PRECEDING
	 * @return
	 */
	public NodeSet selectSiblings(NodeSet siblings, int mode) {
		if (siblings.getLength() == 0 || getLength() == 0)
			return EMPTY_SET;
		NodeSet result = new ExtArrayNodeSet();
		Iterator ia = siblings.iterator();
		Iterator ib = iterator();
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		long pa, pb;
		while (true) {
			// first, try to find nodes belonging to the same doc
			if (na.doc.getDocId() < nb.doc.getDocId()) {
				if (ia.hasNext())
					na = (NodeProxy) ia.next();
				else
					break;
			} else if (na.doc.getDocId() > nb.doc.getDocId()) {
				if (ib.hasNext())
					nb = (NodeProxy) ib.next();
				else
					break;
			} else {
				// same document: check if the nodes have the same parent
				pa = XMLUtil.getParentId(na.doc, na.gid);
				pb = XMLUtil.getParentId(nb.doc, nb.gid);
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
					if (nb.gid < na.gid) {
						// found a preceding sibling
						if (mode == PRECEDING) {
							nb.addContextNode(na);
							result.add(nb);
						}
						if (ib.hasNext())
							nb = (NodeProxy) ib.next();
						else
							break;
					} else if (nb.gid > na.gid) {
						// found a following sibling						
						if (mode == FOLLOWING) {
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

	/**
	 * Get all the sibling nodes of the specified node in the current set.
	 * 
	 * @param doc the node's owner document
	 * @param gid the node's internal id
	 * @return
	 */
	public NodeSet getSiblings(DocumentImpl doc, long gid) {
		int level = doc.getTreeLevel(gid);
		// get parents id
		long pid =
			(gid - doc.getLevelStartPoint(level)) / doc.getTreeLevelOrder(level)
				+ doc.getLevelStartPoint(level - 1);
		// get first childs id
		long f_gid =
			(pid - doc.getLevelStartPoint(level - 1)) * doc.getTreeLevelOrder(level)
				+ doc.getLevelStartPoint(level);
		// get last childs id
		long e_gid = f_gid + doc.getTreeLevelOrder(level);
		// get all nodes between first and last childs id
		NodeSet set = getRange(doc, f_gid, e_gid);
		return set;
	}

	/**
	 * Check if the node identified by its node id has an ancestor contained in this node set
	 * and return the ancestor found.
	 *
	 * If directParent is true, only immediate ancestors (parents) are considered.
	 * Otherwise the method will call itself recursively for all the node's
	 * parents.
	 *
	 */
	public NodeProxy parentWithChild(DocumentImpl doc, long gid, boolean directParent) {
		return parentWithChild(doc, gid, directParent, false, -1);
	}

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
	 */
	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		return parentWithChild(doc, gid, directParent, includeSelf, -1);
	}

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
	 */
	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf,
		int level) {
		NodeProxy temp;
		if (includeSelf && (temp = get(doc, gid)) != null)
			return temp;
		if (level < 0)
			level = doc.getTreeLevel(gid);
		while (gid > 0) {
			gid = XMLUtil.getParentId(doc, gid, level);
			if ((temp = get(doc, gid)) != null)
				return temp;
			else if (directParent)
				return null;
			else
				--level;
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
	public NodeProxy parentWithChild(
		NodeProxy proxy,
		boolean directParent,
		boolean includeSelf,
		int level) {
		return parentWithChild(proxy.doc, proxy.gid, directParent, includeSelf, level);
	}

	/**
	 * Return all nodes contained in this node set that are ancestors of the node
	 * identified by doc and gid.  
	 */
	public NodeSet ancestorsForChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf,
		int level) {
	    NodeSet result = new ArraySet(5);
		NodeProxy temp;
		if (includeSelf && (temp = get(doc, gid)) != null)
			result.add(temp);
		if (level < 0)
			level = doc.getTreeLevel(gid);
		while (gid > 0) {
			gid = XMLUtil.getParentId(doc, gid, level);
			if ((temp = get(doc, gid)) != null)
				result.add(temp);
			else if (directParent)
				return result;
			--level;
		}
		return result;
	}
	
	/**
	 * Return a new node set containing the parent nodes of all nodes in the 
	 * current set.
	 * @return
	 */
	public NodeSet getParents() {
		NodeSet parents = new ExtArrayNodeSet();
		NodeProxy p;
		long pid;
		for (Iterator i = iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			// calculate parent's gid
			pid = XMLUtil.getParentId(p.doc, p.gid);
			if (pid > -1)
				parents.add(new NodeProxy(p.doc, pid, Node.ELEMENT_NODE));
		}
		return parents;
	}

	/**
	 * Returns true if all nodes in this node set are included in
	 * the fulltext index.
	 * 
	 * @return
	 */
	public boolean hasIndex() {
		for (Iterator i = iterator(); i.hasNext();) {
			if (!((NodeProxy) i.next()).hasIndex())
				return false;
		}
		return true;
	}

	/**
	 * Return a sub-range of this node set containing the range of nodes greater than or including
	 * the lower node and smaller than or including the upper node.
	 * 
	 * @param doc
	 * @param lower
	 * @param upper
	 * @return
	 */
	public NodeSet getRange(DocumentImpl doc, long lower, long upper) {
		throw new RuntimeException(
			"getRange is not valid for class " + getClass().getName());
	}

	/**
	 * Get a hint about how many nodes in this node set belong to the 
	 * specified document. This is just used for allocating new node sets.
	 * The information does not need to be exact. -1 is returned if the
	 * size cannot be determined (the default).
	 * 
	 * @param doc
	 * @return
	 */
	public int getSizeHint(DocumentImpl doc) {
		return -1;
	}

	/**
	 * Return a new node set, which represents the intersection of the current
	 * node set with the given node set.
	 * 
	 * @param other
	 * @return
	 */
	public NodeSet intersection(NodeSet other) {
		AVLTreeNodeSet r = new AVLTreeNodeSet();
		NodeProxy l, p;
		for (Iterator i = iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if (other.contains(l)) {
				r.add(l);
			}
		}
		for (Iterator i = other.iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if (contains(l)) {
				if ((p = r.get(l)) != null) {
					p.addMatches(l);
				} else
					r.add(l);
			}
		}
		return r;
	}

	public NodeSet deepIntersection(NodeSet other) {
//		long start = System.currentTimeMillis();
		AVLTreeNodeSet r = new AVLTreeNodeSet();
		NodeProxy l, p, q;
		for (Iterator i = iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if ((p = other.parentWithChild(l, false, true, -1)) != null) {
				if(p.gid != l.gid)
					p.addMatches(l);
				r.add(p);
			}
		}
		for (Iterator i = other.iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if ((q = parentWithChild(l, false, true, -1)) != null) {
				if ((p = r.get(q)) != null) {
					p.addMatches(l);
				} else
					r.add(l);
			}
		}
//		LOG.debug("deep intersection took " + (System.currentTimeMillis() - start));
		return r;
	}
	
	public NodeSet except(NodeSet other) {
		AVLTreeNodeSet r = new AVLTreeNodeSet();
		NodeProxy l, p;
		for (Iterator i = iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if (!other.contains(l)) {
				r.add(l);
			}
		}
		return r;
	}

	/**
	 * Return a new node set which represents the union of the
	 * current node set and the given node set.
	 * 
	 * @param other
	 * @return
	 */
	public NodeSet union(NodeSet other) {
		ArraySet result = new ArraySet(getLength() + other.getLength());
		result.addAll(other);
		NodeProxy p, c;
		for (Iterator i = iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			if (other.contains(p)) {
				c = other.get(p);
				if(c != null)
					c.addMatches(p);
			} else
				result.add(p);
		}
		return result;
	}

	/**
	 * Return a new node set containing all the context nodes associated
	 * with the nodes in this set.
	 * 
	 * @param contextNodes
	 * @param rememberContext
	 * @return
	 */
	public NodeSet getContextNodes(NodeSet contextNodes, boolean rememberContext) {
		ArraySet result = new ArraySet(getLength());
		NodeProxy current, context, item;
		ContextItem contextNode;
		for (Iterator i = iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			contextNode = current.getContext();
			while (contextNode != null) {
				item = contextNode.getNode();
				context = contextNodes.get(item);
				if (context != null) {
					if (!result.contains(context)) {
						if (rememberContext) {
							context.addContextNode(context);
						}
						result.add(context);
					}
					context.addMatches(current);
				}
				contextNode = contextNode.getNextItem();
			}
		}
		return result;
	}

	public NodeSet getContextNodes(boolean rememberContext) {
		NodeProxy current, context;
		ContextItem contextNode;
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		for (Iterator i = iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			contextNode = current.getContext();
			while (contextNode != null) {
				context = contextNode.getNode();
				context.addMatches(current);
				if (!result.contains(context)) {
					if (rememberContext)
						context.addContextNode(context);
					if(lastDoc != null && lastDoc.getDocId() != context.doc.getDocId()) {
						lastDoc = context.doc;
						result.add(context, getSizeHint(lastDoc));
					} else
						result.add(context);
				}
				contextNode = contextNode.getNextItem();
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
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#getState()
	 */
	public int getState() {
		return 1;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#hasChanged(int)
	 */
	public boolean hasChanged(int previousState) {
		return false;
	}
	
	public String pprint() {
	    StringBuffer buf = new StringBuffer();
	    buf.append('[');
	    buf.append(getClass().getName());
	    buf.append(' ');
	    for(Iterator i = iterator(); i.hasNext(); ) {
	        NodeProxy p = (NodeProxy) i.next();
	        buf.append(p.pprint());
	        buf.append(' ');
	    }
	    buf.append(']');
	    return buf.toString();
	}
}
