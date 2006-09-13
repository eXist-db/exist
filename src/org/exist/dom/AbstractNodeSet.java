
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
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
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
	protected int indexType = Type.ANY_TYPE;
	
	protected boolean hasTextIndex = false;
	protected boolean hasMixedContent = false;
	
	private boolean isCached = false;
	
    private boolean processInReverseOrder = false; 
	
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
	public abstract SequenceIterator iterate() throws XPathException;

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
	 */
	public abstract boolean contains(NodeProxy proxy);

	/**
	 * Check if this node set contains nodes belonging to the given document.
	 * 
	 * @param doc
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
	 */
	public abstract NodeProxy get(int pos);

	/**
	 * Get a node from this node set matching the document and node id of
	 * the given NodeProxy.
	 *  
	 * @param p
	 */
	public abstract NodeProxy get(NodeProxy p);

	/**
	 * Get a node from this node set matching the document and node id.
	 * 
	 * @param doc
	 * @param nodeId
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
	 */
	protected NodeSet hasChildrenInSet(NodeSet al, int mode, int contextId) {
		NodeSet result = new ExtArrayNodeSet();		
		for (Iterator i = al.iterator(); i.hasNext(); ) {
            NodeProxy node = (NodeProxy) i.next();
			Range range = NodeSetHelper.getChildRange(node.getDocument(), node.getGID());
			getRange(result, node.getDocument(), range.getStart(), range.getEnd());
		}
		 return result;
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
		if (!(al instanceof VirtualNodeSet)) {
		    if(al.getLength() < 10)
		        return hasChildrenInSet(al, mode, contextId);
		    else
		        return quickSelectParentChild(al, mode, contextId);
		}
		return NodeSetHelper.selectParentChild(this, al, mode, contextId);
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
	 * will be added to each result of the of the selection. 
	 * 
	 */
	public NodeSet selectAncestorDescendant(NodeSet al,	int mode, boolean includeSelf, int contextId) {
		return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
	}
	
	private NodeSet quickSelectParentChild(NodeSet al, int mode, int contextId) {
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
			    pa = na.getGID();
			    pb = nb.getGID();
//			    System.out.println(pa + " -> " + pb);
				pb = NodeSetHelper.getParentId(nb.getDocument(), pb, nb.getDocument().getTreeLevel(pb));
//				System.out.println("comparing " + pa + " -> " + pb);
				if(pa == pb) {
				    if(mode == NodeSet.DESCENDANT) {
				        if (Expression.NO_CONTEXT_ID != contextId)
				            nb.deepCopyContext(na, contextId);
				        else
				            nb.copyContext(na);
				        result.add(nb);
				    } else {
				        if (Expression.NO_CONTEXT_ID != contextId)
				            na.deepCopyContext(nb, contextId);
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
	 * @param  dl    node set containing potential ancestors
	 * @param includeSelf if true, check if the ancestor node itself is contained
	 * in this node set (ancestor-or-self axis)
	 * @param contextId
	 */
	public NodeSet selectAncestors(NodeSet dl, boolean includeSelf, int contextId) {
		return NodeSetHelper.selectAncestors(this, dl, includeSelf, contextId);
	}

	public NodeSet selectFollowing(NodeSet fl) throws XPathException {
		return NodeSetHelper.selectFollowing(fl, this);
	}
    
    public NodeSet selectPreceding(NodeSet pl) throws XPathException {
        return NodeSetHelper.selectPreceding(pl, this);
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

    public NodeSet directSelectAttribute(QName qname, int contextId) {
        return NodeSetHelper.directSelectAttributes(this, qname, contextId);
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
		return parentWithChild(doc, gid, directParent, false, NodeProxy.UNKNOWN_NODE_LEVEL );
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
	public NodeProxy parentWithChild(DocumentImpl doc, long gid, boolean directParent, boolean includeSelf) {
		return parentWithChild(doc, gid, directParent, includeSelf,	NodeProxy.UNKNOWN_NODE_LEVEL );
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
	public NodeProxy parentWithChild(DocumentImpl doc, long gid, boolean directParent, boolean includeSelf,
	        int level) {
		NodeProxy temp = get(doc, gid);
		if (includeSelf && temp != null)
			return temp;
		if (level == NodeProxy.UNKNOWN_NODE_LEVEL)
			level = doc.getTreeLevel(gid);
		while (gid != NodeProxy.DOCUMENT_NODE_GID) {
			gid = NodeSetHelper.getParentId(doc, gid, level);
            temp = get(doc, gid);
			if (temp != null)
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
	public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,	boolean includeSelf, int level) {
		return parentWithChild(proxy.getDocument(), proxy.getGID(), directParent, includeSelf, level);
	}
	
	/**
	 * Return a new node set containing the parent nodes of all nodes in the 
	 * current set.
	 */
	public NodeSet getParents(int contextId) {
		NodeSet parents = new ExtArrayNodeSet();		 
		NodeProxy parent = null;
		for (Iterator i = iterator(); i.hasNext();) {
            NodeProxy current = (NodeProxy) i.next();			
            long parentID = NodeSetHelper.getParentId(current.getDocument(), current.getGID()); 
            //Filter out the temporary nodes wrapper element 
            if (parentID != NodeProxy.DOCUMENT_NODE_GID && 
                    !(parentID == NodeProxy.DOCUMENT_ELEMENT_GID && current.getDocument().getCollection().isTempCollection())) {                
				if (parent == null || parent.getDocument().getDocId() != current.getDocument().getDocId() || parentID != parent.getGID())                 
                    parent = new NodeProxy(current.getDocument(), parentID, Node.ELEMENT_NODE);
				if (Expression.NO_CONTEXT_ID != contextId)
					parent.addContextNode(contextId, current);
				else
					parent.copyContext(current);
                parents.add(parent);
			}
		}
		return parents;
	}

	public NodeSet getAncestors(int contextId, boolean includeSelf) {
	    ExtArrayNodeSet ancestors = new ExtArrayNodeSet();
	    for (Iterator i = iterator(); i.hasNext();) {
	        NodeProxy current = (NodeProxy) i.next();
	        if (includeSelf) {
	            if (Expression.NO_CONTEXT_ID != contextId)
	                current.addContextNode(contextId, current);
	            ancestors.add(current);
	        }
	        long parentID = NodeSetHelper.getParentId(current.getDocument(), current.getGID());            
	        while (parentID > 0) {
	            //Filter out the temporary nodes wrapper element 
	            if (parentID != NodeProxy.DOCUMENT_NODE_GID && 
	                    !(parentID == NodeProxy.DOCUMENT_ELEMENT_GID && current.getDocument().getCollection().isTempCollection())) {
	                NodeProxy parent = new NodeProxy(current.getDocument(), parentID, Node.ELEMENT_NODE);
	                if (Expression.NO_CONTEXT_ID != contextId)
	                    parent.addContextNode(contextId, current);
	                else
	                    parent.copyContext(current);
	                ancestors.add(parent);
	            }
	            parentID = NodeSetHelper.getParentId(current.getDocument(), parentID);    
	        }
	    }
        ancestors.mergeDuplicates();
	    return ancestors;
	}
    
	/**
	 * Return a sub-range of this node set containing the range of nodes greater than or including
	 * the lower node and smaller than or including the upper node.
	 * 
	 * @param doc
	 * @param lower
	 * @param upper
	 */
	public void getRange(NodeSet result, DocumentImpl doc, long lower, long upper) {
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
			if ((p = other.parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL)) != null) {
				if(p.getGID() != l.getGID())
					p.addMatches(l);
				r.add(p);
			}
		}
		for (Iterator i = other.iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if ((q = parentWithChild(l, false, true, NodeProxy.UNKNOWN_NODE_LEVEL)) != null) {
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
		NodeProxy l;
		for (Iterator i = iterator(); i.hasNext();) {
			l = (NodeProxy) i.next();
			if (!other.contains(l)) {
				r.add(l);
			}
		}
		return r;
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
        ExtArrayNodeSet result = new ExtArrayNodeSet();
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
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		DocumentImpl lastDoc = null;
		for (Iterator i = iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			contextNode = current.getContext();
			while (contextNode != null) {
				if (contextNode.getContextId() == contextId) {
					context = contextNode.getNode();
					context.addMatches(current);
					//if (!result.contains(context)) {
						if (Expression.NO_CONTEXT_ID != contextId)
							context.addContextNode(contextId, context);
						if(lastDoc != null && lastDoc.getDocId() != context.getDocument().getDocId()) {
							lastDoc = context.getDocument();
							result.add(context, getSizeHint(lastDoc));
						} else
							result.add(context);
					//}
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
	 */
	public int getIndexType() {
		//Is the index type initialized ?
		if (indexType == Type.ANY_TYPE) {		
		    hasTextIndex = true;
		    hasMixedContent = true;
			for (Iterator i = iterator(); i.hasNext();) {
			    NodeProxy node = (NodeProxy) i.next();
	            if (node.getDocument().getCollection().isTempCollection()) {
	            	//Temporary nodes return default values
	                indexType = Type.ITEM;
	                hasTextIndex = false;
	                hasMixedContent = false;
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
			    		indexType = Type.ITEM;
			    }			    
				if(!node.hasTextIndex()) {
				    hasTextIndex = false;
				}
				if(!node.hasMixedContent()) {
				    hasMixedContent = false;
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
    
	public void clearContext(int contextId) {
		NodeProxy p;
		for (Iterator i = iterator(); i.hasNext(); ) {
			p = (NodeProxy) i.next();
			p.clearContext(contextId);
		}
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.value.AbstractSequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
        // node sets are always persistent
        return true;
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("NodeSet(");
        for (int i = 0 ; i < getLength() ; i++) {
            if(i > 0)
                result.append(", ");                
            result.append(get(i).toString());
        }
        result.append(")");
        return result.toString();
    }     
}
