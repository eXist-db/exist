
/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
import org.exist.xpath.XPathException;
import org.exist.xpath.value.AbstractSequence;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Base class for all node set implementations returned by most
 * xpath expressions. It implements NodeList plus some additional
 * methods needed by the xpath engine.
 *
 * There are three classes extending NodeSet: NodeIDSet, ArraySet
 * and VirtualNodeSet. Depending on the context each of these
 * implementations has its advantages and drawbacks. ArraySet
 * uses a sorted array and binary search, while NodeIDSet is based
 * on a HashSet. VirtualNodeSet is specifically used for steps like
 * descendant::* etc..
 */
public abstract class NodeSet extends AbstractSequence implements NodeList {

	private final static Logger LOG = Logger.getLogger(NodeSet.class);
	
	public final static int ANCESTOR = 0;
	public final static int DESCENDANT = 1;
	public final static int PRECEDING = 2;
	public final static int FOLLOWING = 3;

	public static NodeSet EMPTY_SET = new EmptyNodeSet();

	public abstract Iterator iterator();

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public abstract SequenceIterator iterate();

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.NODE;
	}

	public abstract boolean contains(DocumentImpl doc, long nodeId);
	public abstract boolean contains(NodeProxy proxy);

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
	
	public void add(Item item) throws XPathException {
		if (!Type.subTypeOf(item.getType(), Type.NODE))
			throw new XPathException("item has wrong type");
		add((NodeProxy) item);
	}

	public void addAll(Sequence other) throws XPathException {
		if (other.getItemType() != Type.NODE)
			throw new XPathException("sequence argument is not a node sequence");
		for (SequenceIterator i = other.iterate(); i.hasNext();) {
			add((NodeProxy) i.nextItem());
		}
	}

	public abstract void addAll(NodeSet other);

	public void remove(NodeProxy node) {
		throw new RuntimeException("not implemented");
	}

	public abstract int getLength();
	public abstract Node item(int pos);
	public abstract NodeProxy get(int pos);
	public abstract NodeProxy get(NodeProxy p);
	public abstract NodeProxy get(DocumentImpl doc, long nodeId);

	public NodeProxy nodeHasParent(NodeProxy p, boolean directParent) {
		return nodeHasParent(p.doc, p.gid, directParent, false);
	}

	public NodeProxy nodeHasParent(
		NodeProxy p,
		boolean directParent,
		boolean includeSelf) {
		return nodeHasParent(p.doc, p.gid, directParent, includeSelf);
	}

	public NodeProxy nodeHasParent(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		return nodeHasParent(doc, gid, directParent, includeSelf, -1);
	}

	/**
	 * Check if node has a parent contained in this node set.
	 *
	 * If directParent is true, only immediate ancestors are considered.
	 * Otherwise the method will call itself recursively for the node's
	 * parents.
	 *
	 * If includeSelf is true, the method returns also true if
	 * the node itself is contained in the node set.
	 */
	public NodeProxy nodeHasParent(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf,
		int level) {
		NodeProxy parent;
		if(includeSelf && (parent = get(doc, gid)) != null)
			return parent;
		if (level < 0)
			level = doc.getTreeLevel(gid);
		while(gid > 0) {
			// calculate parent's gid
			gid = XMLUtil.getParentId(doc, gid, level);
			if ((parent = get(doc, gid)) != null)
				return parent;
			else if (directParent)
				return null;
			else {
				--level;
			}
		}
		return null;
	}

	public NodeSet getChildren(NodeSet al, int mode) {
		return getChildren(al, mode, false);
	}

	public NodeSet getChildren(
		NodeSet al,
		int mode,
		boolean rememberContext) {
		NodeProxy n, p;
		long start = System.currentTimeMillis();
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		switch (mode) {
			case DESCENDANT :
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if(lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = getSizeHint(lastDoc);
					}
					if ((p = al.nodeHasParent(n.doc, n.gid, true, false, -1)) != null) {
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
					if(lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = al.getSizeHint(lastDoc);
					}
					p = al.parentWithChild(n.doc, n.gid, true, false, -1);
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
		LOG.debug("getChildren found " + result.getLength() + " in " +
			(System.currentTimeMillis() - start));
		result.sort();
		return result;
	}

	public NodeSet getDescendants(NodeSet al, int mode) {
		return getDescendants(al, mode, false);
	}

	public NodeSet getDescendants(NodeSet al, int mode, boolean includeSelf) {
		return getDescendants(al, mode, includeSelf, false);
	}

	/**
	 *  For a given set of potential ancestor nodes, get the
	 * descendants in this node set
	 *
	 *@param  al    node set containing potential ancestors
	 *@param  mode  determines if either the ancestor or the descendant
	 * nodes should be returned. Possible values are ANCESTOR or DESCENDANT.
	 *@return
	 */
	public NodeSet getDescendants(
		NodeSet al,
		int mode,
		boolean includeSelf,
		boolean rememberContext) {
		NodeProxy n, p;
		long start = System.currentTimeMillis();
		DocumentImpl lastDoc = null;
		int sizeHint = -1;
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		switch (mode) {
			case DESCENDANT :
				for (Iterator i = iterator(); i.hasNext();) {
					n = (NodeProxy) i.next();
					if(lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = getSizeHint(lastDoc);
					}
					if ((p = al.nodeHasParent(n.doc, n.gid, false, false, -1)) != null) {
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
					if(lastDoc == null || n.doc != lastDoc) {
						lastDoc = n.doc;
						sizeHint = al.getSizeHint(lastDoc);
					}
					p = al.parentWithChild(n.doc, n.gid, false);
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
		LOG.debug("getDescendants found " + result.getLength() + " in " +
			(System.currentTimeMillis() - start));
		return result;
	}

	/**
		 *  For a given set of potential ancestor nodes, get the
		 * descendants in this node set
		 *
		 *@param  al    node set containing potential ancestors
		 *@param  mode  determines if either the ancestor or the descendant
		 * nodes should be returned. Possible values are ANCESTOR or DESCENDANT.
		 *@return
		 */
	public NodeSet getAncestors(
		NodeSet al,
		boolean includeSelf,
		boolean rememberContext) {
		NodeProxy n, p, temp;
		ArraySet result = new ArraySet(al.getLength());
		for (Iterator i = iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			p = al.parentWithChild(n.doc, n.gid, false);
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
		return result;
	}

	/**
	 * Select all nodes from the passed node set, which
	 * are preceding or following siblings of the nodes in
	 * this set.
	 * 
	 * @param siblings a node set containing potential siblings
	 * @param mode either FOLLOWING or PRECEDING
	 * @return
	 */
	public NodeSet getSiblings(NodeSet siblings, int mode) {
		if (siblings.getLength() == 0 || getLength() == 0)
			return NodeSet.EMPTY_SET;
		ArraySet result = new ArraySet(getLength());
		Iterator ia = iterator();
		Iterator ib = siblings.iterator();
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
						if (mode == PRECEDING)
							result.add(nb);
						if (ib.hasNext())
							nb = (NodeProxy) ib.next();
					} else if (nb.gid > na.gid) {
						// found a following sibling
						if (mode == FOLLOWING)
							result.add(nb);
						if (ib.hasNext())
							nb = (NodeProxy) ib.next();
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
	 * Search for a node contained in this node set, which is an
	 * ancestor of the argument node.
	 * If directParent is true, only immediate ancestors are considered.
	 */
	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent) {
		return parentWithChild(doc, gid, directParent, false, -1);
	}

	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		return parentWithChild(doc, gid, directParent, includeSelf, -1);
	}

	/**
	 * Search for a node contained in this node set, which is an
	 * ancestor of the argument node.
	 * If directParent is true, only immediate ancestors are considered.
	 * If includeSelf is true, the method returns true even if
	 * the node itself is contained in the node set.
	 */
	protected NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf,
		int level) {
		NodeProxy temp;
		if (level < 0)
			level = doc.getTreeLevel(gid);
		while(gid > 0) {
			if (includeSelf && (temp = get(doc, gid)) != null)
				return temp;
			includeSelf = false;
			// calculate parent's gid
			gid = XMLUtil.getParentId(doc, gid, level);
			if (gid > 0 && (temp = get(doc, gid)) != null)
				return temp;
			else if (directParent)
				return null;
			else
				--level;
		}
		return null;
	}

	public NodeProxy parentWithChild(
		NodeProxy proxy,
		boolean directParent,
		boolean includeSelf) {
		return parentWithChild(
			proxy.doc,
			proxy.gid,
			directParent,
			includeSelf,
			-1);
	}

	public NodeSet getParents() {
		ArraySet parents = new ArraySet(getLength());
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

	public boolean hasIndex() {
		for (Iterator i = iterator(); i.hasNext();) {
			if (!((NodeProxy) i.next()).hasIndex())
				return false;
		}
		return true;
	}

	public NodeSet getRange(DocumentImpl doc, long lower, long upper) {
		NodeProxy p;
		ArraySet result = new ArraySet(5);
		for (Iterator i = iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			if (p.doc.docId == doc.docId && p.gid >= lower && p.gid <= upper)
				result.add(p);
		}
		return result;
	}

	public int getSizeHint(DocumentImpl doc) {
		return -1;
	}
	
	public NodeSet intersection(NodeSet other) {
		long start = System.currentTimeMillis();
		TreeNodeSet r = new TreeNodeSet();
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
					p.addMatches(l.match);
				} else
					r.add(l);
			}
		}
		return r;
	}

	public NodeSet union(NodeSet other) {
		long start = System.currentTimeMillis();
		ArraySet result = new ArraySet(getLength() + other.getLength());
		result.addAll(other);
		NodeProxy p, c;
		for (Iterator i = iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			if (other.contains(p)) {
				c = other.get(p);
				c.addMatches(p.match);
			} else
				result.add(p);
		}
		return result;
	}

	public NodeSet getContextNodes(
		NodeSet contextNodes,
		boolean rememberContext) {
		NodeSet result = new ArraySet(getLength());
		NodeProxy current, context, item;
		ContextItem contextNode;
		for (Iterator i = iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			contextNode = current.getContext();
			while(contextNode != null) {
				item = contextNode.getNode();
				context = contextNodes.get(item);
				if (context != null) {
					if (!result.contains(context)) {
						if (rememberContext) {
							context.addContextNode(context);
						}
						result.add(context);
					}
					context.addMatches(current.match);
				}
				contextNode = contextNode.getNextItem();
			}
		}
		return result;
	}
}
