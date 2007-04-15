/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
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

import java.util.Iterator;

import org.exist.numbering.NodeId;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This node set is called virtual because it is just a placeholder for
 * the set of relevant nodes. For XPath expressions like //* or //node(), 
 * it would be totally unefficient to actually retrieve all descendant nodes.
 * In many cases, the expression can be resolved at a later point in time
 * without retrieving the whole node set. 
 *
 * VirtualNodeSet basically provides method getFirstParent to retrieve the first
 * matching descendant of its context according to the primary type axis.
 *
 * Class LocationStep will always return an instance of VirtualNodeSet
 * if it finds something like descendant::* etc..
 *
 * @author Wolfgang Meier
 * @author Timo Boehme
 */
public class VirtualNodeSet extends AbstractNodeSet {

    protected int axis = Constants.UNKNOWN_AXIS;
    protected NodeTest test;
    protected NodeSet context;
    protected NodeSet realSet = null;
    protected boolean realSetIsComplete = false;
    protected boolean inPredicate = false;
    protected boolean useSelfAsContext = false;
    protected int contextId = Expression.NO_CONTEXT_ID;
    private static final int MAX_CHILD_COUNT_FOR_OPTIMIZE = 5;
    
    private boolean knownIsEmptyCardinality = false;
    private boolean knownHasOneCardinality = false;
    private boolean knownHasManyCardinality = false;
    private boolean isEmpty = true;
    private boolean hasOne = false;    
    private boolean hasMany = false;    

    /**
     * Creates a new <code>VirtualNodeSet</code> instance.
     *
     * @param axis an <code>int</code> value
     * @param test a <code>NodeTest</code> value
     * @param contextId an <code>int</code> value
     * @param context a <code>NodeSet</code> value
     */
    public VirtualNodeSet(int axis, NodeTest test, int contextId, NodeSet context) {
	this.axis = axis;
	this.test = test;
	this.context = context;
        this.contextId = contextId;
    }

    /**
     * The method <code>contains</code>
     *
     * @param p a <code>NodeProxy</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains(NodeProxy p) {
	NodeProxy first = getFirstParent(p, null, (axis == Constants.SELF_AXIS), 0);
	// Timo Boehme: getFirstParent returns now only real parents
	//              therefore test if node is child of context
        if (first != null) {
            return true;
	}
	if (context.get(p.getDocument(), p.getNodeId().getParentId()) != null) {
            return true;
	}
        return false;           
    }

    /**
     * The method <code>setInPredicate</code>
     *
     * @param predicate a <code>boolean</code> value
     */
    public void setInPredicate(boolean predicate) {
	inPredicate = predicate;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
	return context.getDocumentSet();
    }

    /**
     * The method <code>getCollectionIterator</code>
     *
     * @return an <code>Iterator</code> value
     */
    public Iterator getCollectionIterator() {
        return context.getCollectionIterator();
    }

    /**
     * The method <code>getFirstParent</code>
     *
     * @param node a <code>NodeProxy</code> value
     * @param first a <code>NodeProxy</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param recursions an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy getFirstParent(NodeProxy node, NodeProxy first,
				     boolean includeSelf, int recursions) {
        return getFirstParent(node, first, includeSelf, true, recursions);
    }

    /**
     * The method <code>getFirstParent</code>
     *
     * @param node a <code>NodeProxy</code> value
     * @param first a <code>NodeProxy</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param directParent a <code>boolean</code> value
     * @param recursions an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy getFirstParent(NodeProxy node, NodeProxy first,
				     boolean includeSelf, boolean directParent, int recursions) {
        
        NodeId pid = node.getNodeId().getParentId();
        
        /* if the node has no parent, i.e. pid == -1, we still need to 
	 * complete this method to check if we have found a potential parent
	 * in one of the iterations before.
         */
	NodeProxy parent;
	// check if the start-node should be included, e.g. to process an
	// expression like *[. = 'xxx']
	if (recursions == 0 && includeSelf && test.matches(node)) {
	    if (axis == Constants.CHILD_AXIS) {
		// if we're on the child axis, test if
		// the node is a direct child of the context node
                parent = context.get(node.getDocument(), pid);
		if (parent != null) {
		    node.copyContext(parent);
		    if (useSelfAsContext && inPredicate) {
			node.addContextNode(contextId, node);
		    } else if (inPredicate)
			node.addContextNode(contextId, parent);
		    return node;
		}
	    } else {
		// descendant axis: remember the node and continue 
		first = node;
	    }
	}
        
	// if this is the first call to this method, remember the first 
	// parent node and re-evaluate the method. We can't just return 
	// the first parent as we need a parent that is actually contained 
	// in the context set. We thus call the method again to complete.
        if (first == null) {
            if (pid == NodeId.DOCUMENT_NODE) {
                // given node was already document element -> no parent
                return null;
            }
            first = new NodeProxy(node.getDocument(), pid, Node.ELEMENT_NODE, 
				  StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
            // if we are on the self axis, check if the first parent can be selected
            if (axis == Constants.DESCENDANT_SELF_AXIS) {
		    parent = context.get(first.getDocument(), pid);
		    if (parent != null && test.matches(parent)) {
			first.copyContext(parent);
			if (useSelfAsContext && inPredicate) {
			    first.addContextNode(contextId, first);
			} else if (inPredicate)
			    first.addContextNode(contextId, parent);
			return first;
		    }
		}
            // Timo Boehme: we need a real parent (child from context)
            return getFirstParent(first, first, false, directParent, recursions + 1);
        }
        
	// is pid member of the context set?
	parent = context.get(node.getDocument(), pid);

	if (parent != null && test.matches(node)) {
	    if (axis != Constants.CHILD_AXIS) {
		// if we are on the descendant-axis, we return the first node 
		// we found while walking bottom-up.
		// Otherwise, we return the last one (which is node)
		node = first;
	    }
	    node.copyContext(parent);
	    if (useSelfAsContext && inPredicate) {
		node.addContextNode(contextId, node);
	    } else if (inPredicate) {
		node.addContextNode(contextId, parent);
	    }
	    // Timo Boehme: we return the ancestor which is child of context			
	    return node;
        } else if (pid == NodeId.DOCUMENT_NODE) {
            // no matching node has been found in the context
            return null;
	} else if (directParent && axis == Constants.CHILD_AXIS && recursions == 1) {
	    // break here if the expression is like /*/n          
	    return null;        
	} else {
	    // continue for expressions like //*/n or /*//n
	    parent = new NodeProxy(node.getDocument(), pid, Node.ELEMENT_NODE, 
				   StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
            return getFirstParent(parent, first, false, false, recursions + 1);
	}
    }

    private void addInternal(NodeProxy p) {
	if (realSet == null)
	    realSet = new ExtArrayNodeSet(256);
	knownIsEmptyCardinality = true;
	if (isEmpty) {
	    isEmpty = false;
	    knownHasOneCardinality = true;
	    hasOne= true;
	} else if (hasOne) {
	    hasOne = false;
	    knownHasManyCardinality = true;
	    hasMany = true;	
	}
	realSet.add(p);
	realSetIsComplete = false;
    }

    /**
     * The method <code>parentWithChild</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     * @param directParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param level an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent, boolean includeSelf,
				     int level) {
	NodeProxy first = getFirstParent(proxy, null, includeSelf, directParent, 0);
	if (first != null)
	    //TODO : should we set an empty cardinality here ?
	    addInternal(first);
	return first;
    }

    /**
     * The method <code>parentWithChild</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param directParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
    	NodeProxy first = getFirstParent(new NodeProxy(doc, nodeId), null, includeSelf, directParent, 0);
	if (first != null)
	    //TODO : should we set an empty cardinality here ?
	    addInternal(first);
	return first;
    }

    /**
     * The method <code>getNodes</code>
     *
     * @return a <code>NodeSet</code> value
     */
    private final NodeSet getNodes() {
	ExtArrayNodeSet result = new ExtArrayNodeSet();
	for (Iterator i = context.iterator(); i.hasNext();) {
	    NodeProxy proxy = (NodeProxy) i.next();            
	    if (proxy.getNodeId() == NodeId.DOCUMENT_NODE) {
		if(proxy.getDocument().getResourceType() == DocumentImpl.BINARY_FILE) {
		    // skip binary resources
		    continue;
		}

		NodeList cl = proxy.getDocument().getChildNodes();
		for (int j = 0; j < cl.getLength(); j++) {
		    StoredNode node = (StoredNode) cl.item(j);
		    NodeProxy p = new NodeProxy(node);
		    if (test.matches(p)) {
			// fixme! check for unwanted 
			// side effects. /ljo
			//p.deepCopyContext(proxy);

			if (useSelfAsContext && inPredicate) {
			    p.addContextNode(contextId, p);
			}
			result.add(p);
		    }
		    if (node.getNodeType() == Node.ELEMENT_NODE &&
			(axis == Constants.DESCENDANT_AXIS ||
			 axis == Constants.DESCENDANT_SELF_AXIS ||
			 axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
			// note: we create a copy of the docElemProxy here to be used
			// as context when traversing the tree.
			NodeProxy contextNode = new NodeProxy(p);
			contextNode.deepCopyContext(proxy);
			//TODO : is this StoredNode construction necessary ?
			Iterator domIter = contextNode.getDocument().getBroker().getNodeIterator(new StoredNode(contextNode));
			domIter.next();
			contextNode.setMatches(proxy.getMatches());
			addChildren(contextNode, result, node, domIter, 0);
		    }
		}
		continue;
	    } else {
		if(test.matches(proxy)) {
		    if (axis == Constants.SELF_AXIS ||
			axis == Constants.ANCESTOR_SELF_AXIS || 
			axis == Constants.DESCENDANT_SELF_AXIS) {
			if(useSelfAsContext && inPredicate) {
			    proxy.addContextNode(contextId, proxy);
			}
			result.add(proxy);
		    }
		} 
		if (axis != Constants.SELF_AXIS) {
		    //TODO : is this StroredNode construction necessary ?
		    Iterator domIter = proxy.getDocument().getBroker().getNodeIterator(new StoredNode(proxy));
		    StoredNode node = (StoredNode) domIter.next();
		    node.setOwnerDocument(proxy.getDocument());
		    node.setNodeId(proxy.getNodeId());
		    addChildren(proxy, result, node, domIter, 0);
		}
	    }
	}
	return result;
    }
	
    /**
     * recursively adds child nodes
     * @param contextNode a <code>NodeProxy</code> value
     * @param result a <code>NodeSet</code> value
     * @param node a <code>StoredNode</code> value
     * @param iter an <code>Iterator</code> value
     * @param recursions an <code>int</code> value
     */
    private final void addChildren(NodeProxy contextNode, NodeSet result,
				   StoredNode node, Iterator iter,
				   int recursions) {
	if (node.hasChildNodes()) {
	    for (int i = 0; i < node.getChildCount(); i++) {
		StoredNode child = (StoredNode) iter.next();
		if(child == null)
		    LOG.debug("CHILD == NULL; doc = " + 
			      ((DocumentImpl)node.getOwnerDocument()).getURI());
		if(node.getOwnerDocument() == null)
		    LOG.debug("DOC == NULL");
		child.setOwnerDocument((DocumentImpl)node.getOwnerDocument());
		NodeProxy p = new NodeProxy(child);
		p.setMatches(contextNode.getMatches());
		if (test.matches(child)) {
		    if (((axis == Constants.CHILD_AXIS
			  || axis == Constants.ATTRIBUTE_AXIS)
			 && recursions == 0) ||
			(axis == Constants.DESCENDANT_AXIS
			 || axis == Constants.DESCENDANT_SELF_AXIS
			 || axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
			p.deepCopyContext(contextNode);
			if (useSelfAsContext && inPredicate) {
			    p.addContextNode(contextId, p);
			} else if (inPredicate) {
			    p.addContextNode(contextId, contextNode);
			}
                        result.add(p);
		    }
		}
		addChildren(contextNode, result, child, iter, recursions + 1);
	    }
	}
    }

    /**
     * The method <code>realize</code>
     *
     */
    public final void realize() {
	if (realSet != null && realSetIsComplete)
	    return;
        realSet = getNodes();
	knownIsEmptyCardinality = true;
	knownHasOneCardinality = true;
	knownHasManyCardinality = true;
	isEmpty = realSet.isEmpty();
	hasOne = realSet.hasOne();
	hasMany = realSet.hasMany();        
	realSetIsComplete = true;
    }

    /**
     * The method <code>preferTreeTraversal</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean preferTreeTraversal() {
        if (realSet != null && realSetIsComplete)
            return true;
        if (axis != Constants.CHILD_AXIS)
            return false;
        int contextLen = context.getLength();
        int docs = context.getDocumentSet().getLength();
        if (contextLen > docs * MAX_CHILD_COUNT_FOR_OPTIMIZE)
            return false;   // more than 5 nodes per document
        for (Iterator i = context.iterator(); i.hasNext();) {
            NodeProxy p = (NodeProxy) i.next();
            if (p.getNodeId() == NodeId.DOCUMENT_NODE)
                return false;
            NodeImpl n = (NodeImpl) p.getNode();
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getChildCount() > MAX_CHILD_COUNT_FOR_OPTIMIZE)
                return false;
        }
        return true;
    }

    /**
     * The method <code>setSelfIsContext</code>
     *
     */
    public void setSelfIsContext() {
	useSelfAsContext = true;
    }

    /**
     * The method <code>setContextId</code>
     *
     * @param contextId an <code>int</code> value
     */
    public void setContextId(int contextId) {
	this.contextId = contextId;
    }
	
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#hasIndex()
     */
    public boolean hasIndex() {
	// Always return false: there's no index
	return false;
    }

    /* the following methods are normally never called in this context,
     * we just provide them because they are declared abstract
     * in the super class
     */	

    /**
     * The method <code>isEmpty</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isEmpty() {		
	if (knownIsEmptyCardinality)
	    return isEmpty;
	return getLength() == 0;
    }

    /**
     * The method <code>hasOne</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasOne() {
	if (knownHasOneCardinality)
	    return hasOne;
	return getLength() == 1;
    }

    /**
     * The method <code>hasMany</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasMany() {
	if (knownHasManyCardinality)
	    return hasMany;
	return getLength() > 1;
    }    

    /**
     * The method <code>add</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>long</code> value
     */
    public void add(DocumentImpl doc, long nodeId) {
    }

    /**
     * The method <code>add</code>
     *
     * @param node a <code>Node</code> value
     */
    public void add(Node node) {
    }

    /**
     * The method <code>add</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     */
    public void add(NodeProxy proxy) {
    }

    /**
     * The method <code>addAll</code>
     *
     * @param other a <code>NodeList</code> value
     */
    public void addAll(NodeList other) {
    }

    /**
     * The method <code>addAll</code>
     *
     * @param other a <code>NodeSet</code> value
     */
    public void addAll(NodeSet other) {
    }

    /**
     * The method <code>set</code>
     *
     * @param position an <code>int</code> value
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>long</code> value
     */
    public void set(int position, DocumentImpl doc, long nodeId) {
    }

    /**
     * The method <code>remove</code>
     *
     * @param node a <code>NodeProxy</code> value
     */
    public void remove(NodeProxy node) {
    }

    /**
     * The method <code>getLength</code>
     *
     * @return an <code>int</code> value
     */
    public int getLength() {
	realize();
	return realSet.getLength();
    }
	
    
    /**
     * The method <code>getItemCount</code>
     *
     * @return an <code>int</code> value
     */
    public int getItemCount() {
	//TODO : evaluate both semantics
	realize();
	return realSet.getItemCount();
    }

    /**
     * The method <code>item</code>
     *
     * @param pos an <code>int</code> value
     * @return a <code>Node</code> value
     */
    public Node item(int pos) {
	realize();
	return realSet.item(pos);
    }

    /**
     * The method <code>get</code>
     *
     * @param pos an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(int pos) {
	realize();
	return realSet.get(pos);
    }

    /**
     * The method <code>itemAt</code>
     *
     * @param pos an <code>int</code> value
     * @return an <code>Item</code> value
     */
    public Item itemAt(int pos) {
	realize();
	return realSet.itemAt(pos);
    }

    /**
     * The method <code>get</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        realize();
        return realSet.get(doc, nodeId);
    }

    /**
     * The method <code>get</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(NodeProxy proxy) {
        realize();
        return realSet.get(proxy);
    }

    /**
     * The method <code>iterator</code>
     *
     * @return a <code>NodeSetIterator</code> value
     */
    public NodeSetIterator iterator() {
	realize();
	return realSet.iterator();
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterate()
     */
    public SequenceIterator iterate() throws XPathException {
	realize();
	return realSet.iterate();
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    public SequenceIterator unorderedIterator() {
	realize();
	return realSet.unorderedIterator();
    }
	
    /**
     * The method <code>intersection</code>
     *
     * @param other a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet intersection(NodeSet other) {
	realize();
	return realSet.intersection(other);
    }

    /**
     * The method <code>union</code>
     *
     * @param other a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet union(NodeSet other) {
	realize();
	return realSet.union(other);
    }

    /**
     * The method <code>filterDocuments</code>
     *
     * @param otherSet a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet filterDocuments(NodeSet otherSet) {
        return this;
    }

    /**
     * The method <code>clearContext</code>
     *
     */
    public void clearContext() {
		// ignored for a virtual set
	}
    
    /**
     * The method <code>toString</code>
     *
     * @return a <code>String</code> value
     */
    public String toString() {
        if (realSet == null)
            return "Virtual#unknown";
        StringBuffer result = new StringBuffer();
        result.append("Virtual#").append(super.toString());
        return result.toString();
    }      
}
