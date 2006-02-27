/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.storage.DBBroker;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.StorageAddress;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;

import org.exist.numbering.NodeId;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Placeholder class for DOM nodes. 
 * 
 * NodeProxy is an internal proxy class, acting as a placeholder for all types of persistent XML nodes
 * during query processing. NodeProxy just stores the node's unique id and the document it belongs to. 
 * Query processing deals with these proxys most of the time. Using a NodeProxy is much cheaper 
 * than loading the actual node from the database. The real DOM node is only loaded,
 * if further information is required for the evaluation of an XPath expression. To obtain 
 * the real node for a proxy, simply call {@link #getNode()}. 
 * 
 * All sets of type NodeSet operate on NodeProxys. A node set is a special type of 
 * sequence, so NodeProxy does also implement {@link org.exist.xquery.value.Item} and
 * can thus be an item in a sequence. Since, according to XPath 2, a single node is also 
 * a sequence, NodeProxy does itself extend NodeSet. It thus represents a node set containing
 * just one, single node.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class NodeProxy implements NodeSet, NodeValue, Comparable {
	
	/* 
	 * Special values for nodes gid :
	 * Chosen in order to facilitate fast arithmetic computations
	 */	
	public static final int DOCUMENT_NODE_GID = -1;	
	public static final int UNKNOWN_NODE_GID = 0;	
	public static final int DOCUMENT_ELEMENT_GID = 1;
	
	public static final int UNKNOWN_NODE_LEVEL = -1;
	public static final short UNKNOWN_NODE_TYPE = -1;	
	public static final int UNKNOWN_NODE_ADDRESS = -1;
	
	/**
	 * The owner document of this node.
	 */
	private DocumentImpl doc = null;

	/**
	 * The unique internal id of this node in the document, if known.
	 * @link #UNKNOWN_NODE_GID
	 * @link #DOCUMENT_NODE_GID
	 * @link #DOCUMENT_ELEMENT_GID
	 */
	private long gid = UNKNOWN_NODE_GID;

    private NodeId nodeId;
    /**
	 * The internal storage address of this node in the
	 * dom.dbx file, if known.
	 * @link #UNKNOWN_NODE_ADDRESS
	 */
	private long internalAddress;
	
	/**
	 * The type of this node (as defined by DOM), if known. 
	 * @link #UNKNOWN_NODE_TYPE
	 */
	private short nodeType;

	/**
	 * The first {@link Match} object associated with this node.
	 * Match objects are used to track fulltext hits throughout query processing.
	 * 
	 * Matches are stored as a linked list.
	 */
	private Match match = null;

	private ContextItem context = null;

	public NodeProxy(DocumentImpl doc, long gid) {
        this(doc, gid, UNKNOWN_NODE_TYPE, UNKNOWN_NODE_ADDRESS);		
	}

	public NodeProxy(DocumentImpl doc, long gid, short nodeType) {
        this(doc, gid, nodeType, UNKNOWN_NODE_ADDRESS);
	}
    
    public NodeProxy(DocumentImpl doc, long gid, long address) {
        this(doc, gid, UNKNOWN_NODE_TYPE, address);
    }    

	public NodeProxy(DocumentImpl doc, long gid, short nodeType, long address) {
		this.doc = doc;
		this.gid = gid;
		this.nodeType = nodeType;
		this.internalAddress = address;
	}

	public NodeProxy(NodeProxy p) {
        this(p.doc, p.gid, p.nodeType, p.internalAddress);
		match = p.match;
        //TODO : what about node's context ?		
	}

	/** create a proxy to a document node */
	public NodeProxy(DocumentImpl doc) {
        this(doc, DOCUMENT_NODE_GID, Node.DOCUMENT_NODE, UNKNOWN_NODE_ADDRESS);
        this.nodeId = doc.getBroker().getBrokerPool().getNodeFactory().documentNodeId();
    }

    public NodeProxy(DocumentImpl doc, NodeId nodeId) {
        this(doc, 1, UNKNOWN_NODE_TYPE, UNKNOWN_NODE_ADDRESS);
        this.nodeId = nodeId;
    }

    public void setNodeId(NodeId id) {
        this.nodeId = id;
    }

    public NodeId getNodeId() {
        return nodeId;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#getImplementation()
	 */
	public int getImplementationType() {
		return NodeValue.PERSISTENT_NODE;
	}
	
	/** Ordering first according to document ID; then if equal 
	 * according to node gid. */
	public int compareTo(NodeProxy other) {
		final int diff = doc.getDocId() - other.doc.getDocId();
		if (diff != 0)
            return diff;
        return (int) (gid - other.gid);
	}
	
	public int compareTo(Object other) {
		if(!(other instanceof NodeProxy))
            //Always superior...
			return Constants.SUPERIOR;
		return compareTo((NodeProxy) other);
	}

	public boolean equals(Object other) {
		if (!(other instanceof NodeProxy))
            //Always different...
            return false;
		NodeProxy otherNode = (NodeProxy) other;
        if (otherNode.doc.getDocId() != doc.getDocId())
            return false;        
        if (otherNode.gid != gid)
            return false;
        return true;
	}

	public boolean equals(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");		
		NodeProxy otherNode = (NodeProxy) other;
        if (otherNode.doc.getDocId() != doc.getDocId())
            return false;
        if(otherNode.gid != gid)
            return false;
        return true;
	}

	public boolean before(NodeValue other) throws XPathException {
		return before(other, true);
	}
	
	protected boolean before(NodeValue other, boolean includeAncestors) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");
		NodeProxy otherNode = (NodeProxy) other;
		if (doc.getDocId() != otherNode.doc.getDocId())
			return false;		
		int la = doc.getTreeLevel(gid);
		int lb = doc.getTreeLevel(otherNode.gid);
		long pa = gid;
        long pb = otherNode.gid;
		if (la > lb) {
			while (la > lb) {
				pa = NodeSetHelper.getParentId(doc, pa, la);
				--la;
			}
			if (pa == pb)
				// a is a descendant of b
				return false;
			else
				return pa < pb;
		} else if (lb > la) {
			while (lb > la) {
				pb = NodeSetHelper.getParentId(otherNode.doc, pb, lb);
				--lb;
			}
			if (pb == pa)
				// a is an ancestor of b
				return includeAncestors ? true : false;
			else
				return pa < pb;
		} else
			return pa < pb;
	}

	public boolean after(NodeValue other) throws XPathException {
		return after(other, true);
	}
	
	protected boolean after(NodeValue other, boolean includeDescendants) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");
		NodeProxy otherNode = (NodeProxy) other;
		if (doc.getDocId() != otherNode.doc.getDocId())
			return false;		
		int la = doc.getTreeLevel(gid);
		int lb = doc.getTreeLevel(otherNode.gid);
		long pa = gid;
        long pb = otherNode.gid;
		if (la > lb) {
			while (la > lb) {
				pa = NodeSetHelper.getParentId(doc, pa, la);
				--la;
			}
			// a is a descendant of b
			if (pa == pb)
				return includeDescendants ? true : false;
			else
				return pa > pb;
		} else if (lb > la) {
			while (lb > la) {
				pb = NodeSetHelper.getParentId(otherNode.doc, pb, lb);
				--lb;
			}
			if (pb == pa)
				return false;
			else
				return pa > pb;
		} else
			return pa > pb;
	}

	public Document getOwnerDocument() {
		return doc;
	}
	
	public final DocumentImpl getDocument()  {
        return doc;
	}
	
    public boolean isDocument() {
        return nodeType == Node.DOCUMENT_NODE;
    }
    
	public long getGID() {
		return gid;
	}

	public Node getNode() {
		if (isDocument())
			return doc;
		else
			return doc.getNode(this);
	}
    
	public short getNodeType() {
		return nodeType;
	}

	/**
	 * Sets the nodeType.
	 * @param nodeType The nodeType to set
	 */
	public void setNodeType(short nodeType) {
        if (this.nodeType != UNKNOWN_NODE_TYPE && this.nodeType != nodeType)
            throw new IllegalArgumentException("Node type already affected");
        this.nodeType = nodeType;
	}
 
	/**
	 * Returns the storage address of this node in dom.dbx.
	 * @return long
	 */
	public long getInternalAddress() {
		return internalAddress;
	}

	/**
	 * Sets the storage address of this node in dom.dbx.
	 * 
	 * @param internalAddress The internalAddress to set
	 */
	public void setInternalAddress(long internalAddress) {
		this.internalAddress = internalAddress;
	}
    
	public void setIndexType(int type) {
	    this.internalAddress = StorageAddress.setIndexType(internalAddress, (short) type); 
	}

	public int getIndexType() {
	    return RangeIndexSpec.indexTypeToXPath(StorageAddress.indexTypeFromPointer(internalAddress));
	}
	
	public boolean hasTextIndex() {
		return RangeIndexSpec.hasFulltextIndex(StorageAddress.indexTypeFromPointer(internalAddress));
	}

	public boolean hasMixedContent() {
	    return RangeIndexSpec.hasMixedContent(StorageAddress.indexTypeFromPointer(internalAddress));
	}
	
	public Match getMatches() {
		return match;
	}
	
	public void setMatches(Match match) {
		this.match = match;
	}
	
	public boolean hasMatch(Match m) {
		if (m == null || match == null)
			return false;
		Match next = match;
		do {
			if (next.equals(m))
				return true;
		} while ((next = next.getNextMatch()) != null);
		return false;
	}

	public void addMatch(Match m) {
		if (match == null) {            
			match = m;
			match.prevMatch = null;
			match.nextMatch = null;
			return;
		}
		Match next = match;
		int cmp;
		while (next != null) {
			cmp = next.compareTo(m);
			if (cmp == 0 && m.getNodeId() == next.getNodeId())
				return;
			else if (cmp < 0) {
				if (next.prevMatch != null)
					next.prevMatch.nextMatch = m;
				else
					match = m;
				m.prevMatch = next.prevMatch;
				next.prevMatch = m;
				m.nextMatch = next;
				return;
			} else if (next.nextMatch == null) {
				next.nextMatch = m;
				m.prevMatch = next;
				m.nextMatch = null;
				return;
			}
			next = next.nextMatch;
		}
	}

	public void addMatches(NodeProxy p) {
		if (p == this)
			return;
		Match m = p.getMatches();
		while (m != null) {
			addMatch(new Match(m));
			m = m.nextMatch;
		}
	}

	/**
	 * Add a node to the list of context nodes for this node.
	 * 
	 * NodeProxy internally stores the context nodes of the XPath context, for which 
	 * this node has been selected during a previous processing step.
	 * 
	 * Since eXist tries to process many expressions in one, single processing step,
	 * the context information is required to resolve predicate expressions. For
	 * example, for an expression like //SCENE[SPEECH/SPEAKER='HAMLET'],
	 * we have to remember the SCENE nodes for which the equality expression
	 * in the predicate was true.  Thus, when evaluating the step SCENE[SPEECH], the
	 * SCENE nodes become context items of the SPEECH nodes and this context
	 * information is preserved through all following steps.
	 * 
	 * To process the predicate expression, {@link org.exist.xquery.Predicate} will take the
	 * context nodes returned by the filter expression and compare them to its context
	 * node set.
	 */
    public void addContextNode(int contextId, NodeValue node) {
        if (node.getImplementationType() != NodeValue.PERSISTENT_NODE)
            return;
        NodeProxy contextNode = (NodeProxy) node;
        if (context == null) {
            context = new ContextItem(contextId, contextNode);
            return;
        }
        ContextItem next = context;
        while (next != null) {
            if (contextId == next.getContextId() &&
                    next.getNode().getNodeId().equals(contextNode.getNodeId())) {
                // Ignore duplicate context nodes
                break;
            }
            if (next.getNextDirect() == null) {
                next.setNextContextItem(new ContextItem(contextId, contextNode));
                break;
            }
            next = next.getNextDirect();
        }
    }
	
    /**
     * Add all context nodes from the other NodeProxy to the
     * context of this NodeProxy.
     * 
     * @param other
     */
    public void addContext(NodeProxy other) {
        ContextItem next = other.context;
        while (next != null) {
            addContextNode(next.getContextId(), next.getNode());
            next = next.getNextDirect();
        }
    }
    
	public void copyContext(NodeProxy node) {
		context = node.getContext();
	}
	
    public void deepCopyContext(NodeProxy node) {
        context = null;
        ContextItem newContext = null;
        ContextItem next = node.context;
        while (next != null) {
            if (newContext == null) {
                newContext = new ContextItem(next.getContextId(), next.getNode());
                context = newContext;
            } else {
                newContext.setNextContextItem(new ContextItem(next.getContextId(), next.getNode()));
                newContext = newContext.getNextDirect();
            }
//          System.out.println("NodeProxy.copyContext: " + next.getNode().debugContext());
            next = next.getNextDirect();
        }
    }
    
    public void deepCopyContext(NodeProxy node, int addContextId) {
        deepCopyContext(node);
        addContextNode(addContextId, node);
    }
    
    public void clearContext(int contextId) {
        context = null;
        return;
//        if (contextId == Expression.IGNORE_CONTEXT) {
//            context = null;
//            return;
//        }
//        ContextItem newContext = null;
//        ContextItem last = null;
//        ContextItem next = context;
//        while (next != null) {
//            if (next.getContextId() != contextId) {
//                if (newContext == null) {
//                    newContext = next;
//                } else {
//                    last.setNextContextItem(next);
//                }
//                last = next;
//                last.setNextContextItem(null);
//            }
//            next = next.getNextDirect();
//        }
//        this.context = newContext;
    }
    
	public ContextItem getContext() {
		return context;
	}
	
	public String debugContext() {
		StringBuffer buf = new StringBuffer();
		buf.append("Context for " + gid + "[ " + toString() + "] : ");
		ContextItem next = context;
		while(next != null) {
			buf.append('[');
			buf.append(next.getNode().getGID());
			buf.append(':');
			buf.append(next.getContextId());
			buf.append("] ");
			next = next.getNextDirect();
		}
		return buf.toString();
	}
	
	//	methods of interface Item

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getType()
	 */
	public int getType() {
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				return Type.ELEMENT;
			case Node.ATTRIBUTE_NODE :
				return Type.ATTRIBUTE;
			case Node.TEXT_NODE :
				return Type.TEXT;
			case Node.PROCESSING_INSTRUCTION_NODE :
				return Type.PROCESSING_INSTRUCTION;
			case Node.COMMENT_NODE :
				return Type.COMMENT;
			case Node.DOCUMENT_NODE:
			    return Type.DOCUMENT;
			//(yet) unknown type : return generic
			default :
				return Type.NODE;
		}
	}
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
        return true;
    } 

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

    public String getNodeValue() {
        if (isDocument()) {         
            NodeProxy root = (NodeProxy) doc.getDocumentElement();
            return doc.getBroker().getNodeValue(new NodeProxy(doc, root.getGID(), root.getInternalAddress()), false);
        } else {
            return doc.getBroker().getNodeValue(this, false);
        }
    }     

    public String getNodeValueSeparated() {
        return doc.getBroker().getNodeValue(this, true);
    }    

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		return new StringValue(getNodeValue()).convertTo(requiredType);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return new UntypedAtomicValue(getNodeValue());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler) throws SAXException {
		Serializer serializer = broker.getSerializer();
		serializer.reset();
		serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
		serializer.setSAXHandlers(handler, null);
		serializer.toSAX(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#copyTo(org.exist.storage.DBBroker, org.exist.memtree.DocumentBuilderReceiver)
	 */
	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
	    if(nodeType == Node.ATTRIBUTE_NODE) {
	        AttrImpl attr = (AttrImpl)getNode();
	        receiver.attribute(attr.getQName(), attr.getValue());
	    } else
	        receiver.addReferenceNode(this);
//	    Serializer serializer = broker.getSerializer();
//	    serializer.toReceiver(this, receiver);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(NodeProxy.class))
			return 0;
		if (javaClass.isAssignableFrom(Node.class))
			return 1;
		if (javaClass == String.class || javaClass == CharSequence.class)
			return 2;
		if (javaClass == Character.class || javaClass == char.class)
			return 2;
		if (javaClass == Double.class || javaClass == double.class)
			return 10;
		if (javaClass == Float.class || javaClass == float.class)
			return 11;
		if (javaClass == Long.class || javaClass == long.class)
			return 12;
		if (javaClass == Integer.class || javaClass == int.class)
			return 13;
		if (javaClass == Short.class || javaClass == short.class)
			return 14;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 15;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 16;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(NodeProxy.class))
			return this;
		else if (target.isAssignableFrom(Node.class))
			return getNode();
		else if (target == Object.class)
			return getNode();
		else {
			StringValue v = new StringValue(getStringValue());
			return v.toJavaObject(target);
		}
	}

	/*
	 * Methods of interface Sequence:
	 */
	
	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    public int getItemType() {
        return getType();
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getCardinality()
     */
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isCached()
     */
    public boolean isCached() {
        return false;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
     */
    public void setIsCached(boolean cached) {
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
     */
    public boolean effectiveBooleanValue() throws XPathException {
        return true;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // single node: no duplicates
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setSelfAsContext()
     */
    public void setSelfAsContext(int contextId) {
        addContextNode(contextId, this);
    }
   
    
	/* -----------------------------------------------*
	 * Methods of class NodeSet
	 * -----------------------------------------------*/

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public NodeSetIterator iterator() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleNodeIterator(this);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
        if (this.gid != nodeId)
            return false;
        if (this.doc.getDocId() != doc.getDocId()) 
            return false;
        return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
        if (this.gid != proxy.gid)
            return false;
		if (this.doc.getDocId() != proxy.doc.getDocId())
            return false;
        return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
	 */
	public void addAll(NodeSet other) {
        throw new RuntimeException("Method not supported");
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
	 */
	public void add(NodeProxy proxy) {
        throw new RuntimeException("Method not supported");
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
     */
    public void add(Item item) throws XPathException {
        throw new RuntimeException("Method not supported");
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy, int)
     */
    public void add(NodeProxy proxy, int sizeHint) {
        throw new RuntimeException("Method not supported");
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
     */
    public void addAll(Sequence other) throws XPathException {
        throw new RuntimeException("Method not supported");
    }
    
	/* (non-Javadoc)
	 * @see org.w3c.dom.NodeList#getLength()
	 */
	public int getLength() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.NodeList#item(int)
	 */
	public Node item(int pos) {
		return pos > 0 ? null : getNode();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos > 0 ? null : this;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		return pos > 0 ? null : this;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		return contains(p) ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl document, long nodeId) {
        if (this.gid != nodeId)
            return null;
	    if(this.doc.getDocId() != document.getDocId())
	        return null;
	    return this;
	}

    public NodeProxy get(DocumentImpl document, NodeId nodeId) {
        if (!this.nodeId.equals(nodeId))
             return null;
        if(this.doc.getDocId() != document.getDocId())
	        return null;
	    return this;
    }

    /* (non-Javadoc)
    * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.NodeProxy, boolean, boolean, int)
    */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
                                     boolean includeSelf, int level) {
        return parentWithChild(proxy.getDocument(), proxy.gid, directParent, includeSelf, level);
    }
	
	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.DocumentImpl, long, boolean, boolean)
     */
    public NodeProxy parentWithChild(DocumentImpl doc, long gid,
            boolean directParent, boolean includeSelf) {
        return parentWithChild(doc, gid, directParent, includeSelf, UNKNOWN_NODE_LEVEL);
    }

    public NodeProxy parentWithChild(DocumentImpl otherDoc, NodeId otherId,
            boolean directParent, boolean includeSelf) {
        if(otherDoc.getDocId() != doc.getDocId())
			return null;
        if(includeSelf && otherId.compareTo(nodeId) == 0)
            return this;
		while (otherId != null) {
			otherId = otherId.getParentId();
			if(otherId.compareTo(nodeId) == 0)
				return this;
			else if (directParent)
				return null;
		}
		return null;
    }

    /* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.DocumentImpl, long, boolean, boolean, int)
	 */
	public NodeProxy parentWithChild(DocumentImpl otherDoc,	long otherId, boolean directParent,
			boolean includeSelf, int level) {
        if(includeSelf && otherId == gid)
            return this;        
		if(otherDoc.getDocId() != doc.getDocId())
			return null;		
		if (level == UNKNOWN_NODE_LEVEL)
			level = doc.getTreeLevel(otherId);
		while (otherId > 0) {
			otherId = NodeSetHelper.getParentId(doc, otherId, level);
			if(otherId == gid)
				return this;
			else if (directParent)
				return null;
			else
				--level;
		}
		return null;
	}
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getContextNodes(boolean)
     */
    public NodeSet getContextNodes(int contextId) {
        ExtArrayNodeSet result = new ExtArrayNodeSet();
        ContextItem contextNode = getContext();
        while (contextNode != null) {
            NodeProxy p = contextNode.getNode();
            p.addMatches(this);
            if (!result.contains(p)) {
                //TODO : why isn't "this" involved here ? -pb
                if (contextId != Expression.NO_CONTEXT_ID)
                    p.addContextNode(contextId, p);
                result.add(p);
            }
            contextNode = contextNode.getNextDirect();
        }
        return result;
    }    

    
    /* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#getRange(org.exist.dom.DocumentImpl, long, long)
	 */
	public void getRange(NodeSet result, DocumentImpl document, long lower, long upper) {
        if (this.gid < lower)
            return;
        if (this.gid > upper)
            return;
        if(this.doc.getDocId() != document.getDocId())
            return;
        result.add(this);
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
	
	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getSizeHint(org.exist.dom.DocumentImpl)
     */
    public int getSizeHint(DocumentImpl document) {
        if(document.getDocId() == doc.getDocId())
            return 1;
        else
            return 0;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        DocumentSet docs = new DocumentSet(1);
        docs.add(doc);
        return docs;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#containsDoc(org.exist.dom.DocumentImpl)
     */
    public boolean containsDoc(DocumentImpl document) {
        return doc.getDocId() == document.getDocId();
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#intersection(org.exist.dom.NodeSet)
     */
    public NodeSet intersection(NodeSet other) {
        if(other.contains(this))
            return this;
        else
            return NodeSet.EMPTY_SET;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#deepIntersection(org.exist.dom.NodeSet)
     */
    public NodeSet deepIntersection(NodeSet other) {
        NodeProxy p = other.parentWithChild(this, false, true, UNKNOWN_NODE_LEVEL);
        if (p == null)
            return NodeSet.EMPTY_SET;
        if (this.gid != p.gid)
			p.addMatches(this);
        return p;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#union(org.exist.dom.NodeSet)
     */
    public NodeSet union(NodeSet other) {
        ExtArrayNodeSet result = new ExtArrayNodeSet();
        result.addAll(other);
        result.add(this);
        return result;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#except(org.exist.dom.NodeSet)
     */
    public NodeSet except(NodeSet other) {
        return other.contains(this) ? NodeSet.EMPTY_SET : this;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getParents(boolean)
     */    
    public NodeSet getParents(int contextId) {        
        long pid = NodeSetHelper.getParentId(doc, gid);
        if (pid == DOCUMENT_NODE_GID) 
            return NodeSet.EMPTY_SET;
        NodeProxy parent = new NodeProxy(doc, pid, Node.ELEMENT_NODE);
        if (contextId != Expression.NO_CONTEXT_ID)
            parent.addContextNode(contextId, this);
        else
            parent.copyContext(this);
        return parent;
    }
    
    public NodeSet getAncestors(int contextId, boolean includeSelf) {        
        NodeSet ancestors = new ExtArrayNodeSet();
        if (includeSelf)
            ancestors.add(this);        
        long pid = NodeSetHelper.getParentId(getDocument(), gid);        
        while(pid > 0) {
            NodeProxy parent = new NodeProxy(getDocument(), pid, Node.ELEMENT_NODE);
            if (contextId != Expression.NO_CONTEXT_ID)
                parent.addContextNode(contextId, this);
            else
                parent.copyContext(this);
            ancestors.add(parent);
            pid = NodeSetHelper.getParentId(getDocument(), pid);    
        }
        return ancestors;       
    }    
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectParentChild(NodeSet al, int mode) {
        return selectParentChild(al, mode, Expression.NO_CONTEXT_ID);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int, boolean)
     */
    public NodeSet selectParentChild(NodeSet al, int mode, int contextId) {
        return NodeSetHelper.selectParentChild(this, al, mode, contextId);
        
        /*
        NodeProxy parent = al.parentWithChild(this, true, false,	UNKNOWN_NODE_LEVEL);
        if(parent == null)
            return NodeSet.EMPTY_SET;  
        switch (mode){
            case NodeSet.DESCENDANT : {
    			if (rememberContext)
    				addContextNode(parent);
    			else
    				copyContext(parent);
    			return this;
            }
            case NodeSet.ANCESTOR : {
                if (rememberContext)
                    parent.addContextNode(this);
    			else
                    parent.copyContext(this);
                return parent;
            }
            default :
                throw new IllegalArgumentException("Invalid axis");
        }
        */
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestors(org.exist.dom.NodeSet, boolean, int)
     */
    public NodeSet selectAncestors(NodeSet al, boolean includeSelf, int contextId) {
        return NodeSetHelper.selectAncestors(this, al, includeSelf, contextId);
    }    
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectPrecedingSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectPrecedingSiblings(this, siblings, contextId);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectFollowingSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectFollowingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectFollowingSiblings(this, siblings, contextId);
    }    
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestorDescendant(org.exist.dom.NodeSet, int, boolean, int)
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode, boolean includeSelf, int contextId) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectFollowing(org.exist.dom.NodeSet)
     */
    public NodeSet selectPreceding(NodeSet preceding) throws XPathException {
        return NodeSetHelper.selectPreceding(this, preceding);
    }
    
    public NodeSet selectFollowing(NodeSet following) throws XPathException {
        return NodeSetHelper.selectFollowing(this, following);
    }    
    
    public NodeSet directSelectAttribute(QName qname, int contextId) {
        if (nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE)
            return NodeSet.EMPTY_SET;
        NodeImpl node = (NodeImpl) getNode();
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return NodeSet.EMPTY_SET;
        AttrImpl attr = (AttrImpl)
            ((ElementImpl)node).getAttributeNodeNS(qname.getNamespaceURI(), qname.getLocalName());
        if (attr == null)
            return NodeSet.EMPTY_SET;
        NodeProxy child = new NodeProxy(doc, attr.getGID(), Node.ATTRIBUTE_NODE, attr.getInternalAddress());
        child.setNodeId(attr.getNodeId());
        if (Expression.NO_CONTEXT_ID != contextId)
            child.addContextNode(contextId, this);
        else
            child.copyContext(this);
        return child;
    }
    
    public String toString() {
        if (doc.getNode(gid) != null)
            return doc.getNode(gid).toString();
        else
            return "Document node for " + doc.getDocId();
        //return ("doc: " + this.getDocument() + " gid:" + this.getGID() + " address :" + 
        //        this.getInternalAddress() + " type :" + this.getNodeType()
        //        );
    }    
    
	private final static class SingleNodeIterator implements NodeSetIterator, SequenceIterator {

		private boolean hasNext = true;
		private NodeProxy node;

		public SingleNodeIterator(NodeProxy node) {
			this.node = node;
		}

		public boolean hasNext() {
			return hasNext;
		}

		public Object next() {
			if (!hasNext) 
                return null;
			hasNext = false;
			return node;
		}

		public void remove() {            
            throw new RuntimeException("Method not supported");
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if (!hasNext)
			    return null;
			hasNext = false;
			return node;			
		}

        public void setPosition(NodeProxy proxy) {
            node = proxy;
            hasNext = true;
        }
	}

}
