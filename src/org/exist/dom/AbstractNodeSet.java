
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
	
	// indicates the type of an optional value index that may have
    // been defined on the nodes in this set.
	private int indexType = Type.ANY_TYPE;
	
	private boolean hasTextIndex = false;
	private boolean hasMixedContent = false;
	
	private boolean isCached = false;
	
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

	public void setIsCached(boolean cached) {
		isCached = cached;
	}
	
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
			ds.add(p.getDocument());
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
	protected NodeSet hasChildrenInSet(
		NodeProxy parent,
		int mode,
		boolean rememberContext) {
		Range range = XMLUtil.getChildRange(parent.getDocument(), parent.gid);
		return getRange(parent.getDocument(), range.getStart(), range.getEnd());
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
	 * set of potential ancestor nodes.
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
		    else
		        return quickSelectParentChild(al, mode, rememberContext);
		}
		return NodeSetHelper.selectParentChild(this, al, mode, rememberContext);
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
	 * 
	 * @return
	 */
	public NodeSet selectAncestorDescendant(
		NodeSet al,
		int mode,
		boolean includeSelf,
		boolean rememberContext) {
		return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, rememberContext);
	}
	
	private NodeSet quickSelectParentChild(NodeSet al, int mode, boolean rememberContext) {
	    final NodeSet result = new ExtArrayNodeSet();
		final Iterator ia = al.iterator();
		final Iterator ib = iterator();
//		final long start = System.currentTimeMillis();
		NodeProxy na = (NodeProxy) ia.next(), nb = (NodeProxy) ib.next();
		
		// check if one of the node sets is empty
		if(na == null || nb == null)
		    return result;
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
			    // same document
			    pa = na.gid;
			    pb = nb.gid;
//			    System.out.println(pa + " -> " + pb);
				pb = XMLUtil.getParentId(nb.getDocument(), pb, nb.getDocument().getTreeLevel(pb));
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
//		LOG.debug("quickSelect took " + (System.currentTimeMillis() - start));
		return result;
	}
	
	/**
	 * For a given set of potential ancestor nodes, return all ancestors
	 * having descendants in this node set.
	 *
	 * @param  al    node set containing potential ancestors
	 * @param includeSelf if true, check if the ancestor node itself is contained
	 * in this node set (ancestor-or-self axis)
	 * @param rememberContext if true, add the matching nodes to the context node
	 * list of each returned node (this is used to track matches for predicate evaluation)
	 * @return
	 */
	public NodeSet selectAncestors(
		NodeSet dl,
		boolean includeSelf,
		boolean rememberContext) {
		return NodeSetHelper.selectAncestors(this, dl, includeSelf, rememberContext);
	}

	public NodeSet selectFollowing(NodeSet following) throws XPathException {
		return NodeSetHelper.selectFollowing(this, following);
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
		return NodeSetHelper.selectSiblings(this, siblings, mode);
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
		return parentWithChild(proxy.getDocument(), proxy.gid, directParent, includeSelf, level);
	}
	
	/**
	 * Return a new node set containing the parent nodes of all nodes in the 
	 * current set.
	 * @return
	 */
	public NodeSet getParents(boolean rememberContext) {
		NodeSet parents = new ExtArrayNodeSet();
		NodeProxy p;
		long pid;
		for (Iterator i = iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			// calculate parent's gid
			pid = XMLUtil.getParentId(p.getDocument(), p.gid);
			if (pid > -1) {
				NodeProxy parent = new NodeProxy(p.getDocument(), pid, Node.ELEMENT_NODE);
				if (rememberContext)
					parent.addContextNode(p);
				else
					parent.copyContext(p);
				parents.add(parent);
			}
		}
		return parents;
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
		//ExtArrayNodeSet r = new ExtArrayNodeSet();
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
					if(lastDoc != null && lastDoc.getDocId() != context.getDocument().getDocId()) {
						lastDoc = context.getDocument();
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
	
	/**
	 * If all nodes in this set have an index, returns the common
	 * supertype used to build the index, e.g. xs:integer or xs:string.
	 * If the nodes have different index types or no node has been indexed,
	 * returns {@link Type#ITEM}.
	 * 
	 * @see org.exist.xquery.GeneralComparison
	 * @see org.exist.xquery.ValueComparison
	 * @return
	 */
	public int getIndexType() {
		if(indexType == Type.ANY_TYPE) {
		    hasTextIndex = true;
		    hasMixedContent = false;
		    
		    int type;
		    NodeProxy p;
			for (Iterator i = iterator(); i.hasNext();) {
			    p = (NodeProxy) i.next();
			    type = p.getIndexType();
				if(indexType == Type.ANY_TYPE)
				    indexType = type;
				else if(indexType != type) {
				    LOG.debug(p);
				    indexType = Type.ITEM;
				}
				if(!p.hasTextIndex()) {
				    hasTextIndex = false;
				}
				if(p.hasMixedContent()) {
				    hasMixedContent = true;
				}
			}
		}
		return indexType;
	}
	
	public boolean hasTextIndex() {
	    if(indexType == Type.ANY_TYPE) {
	        getIndexType();
//		    int type;
//		    NodeProxy p;
//			for (Iterator i = iterator(); i.hasNext();) {
//			    p = (NodeProxy) i.next();
//			    hasTextIndex = p.hasTextIndex();
//			    if(!hasTextIndex)
//			        break;
//			}
		}
	    return hasTextIndex;
	}
	
	public boolean hasMixedContent() {
	    if(indexType == Type.ANY_TYPE) {
	        getIndexType();
	    }
	    return hasMixedContent;
	}
}
