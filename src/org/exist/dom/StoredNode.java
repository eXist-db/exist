/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2007 The eXist Project
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

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.exist.util.pool.NodePool;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Iterator;

/**
 *  The base class for all persistent DOM nodes in the database.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class StoredNode extends NodeImpl implements Visitable, NodeHandle {
	
    public final static int LENGTH_SIGNATURE_LENGTH = 1; //sizeof byte
    public final static long UNKNOWN_NODE_IMPL_ADDRESS = -1;
	
    protected NodeId nodeId = null;

    protected DocumentImpl ownerDocument = null;
    
    private long internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
    
    private short nodeType = NodeProxy.UNKNOWN_NODE_TYPE;
    
    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     */
    public StoredNode(short nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     * @param nodeId a <code>NodeId</code> value
     */
    public StoredNode(short nodeType, NodeId nodeId) {
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    /**
     * Copy constructor: creates a copy of the other node.
     *
     * @param other a <code>StoredNode</code> value
     */
    public StoredNode(StoredNode other) {
        this.nodeType = other.nodeType;
        this.nodeId = other.nodeId;
        this.internalAddress = other.internalAddress;
        this.ownerDocument = other.ownerDocument;        
    }
    
    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param other a <code>NodeProxy</code> value
     */
    public StoredNode(NodeProxy other) {
    	this.ownerDocument = other.getDocument();
    	this.nodeType = other.getNodeType();
    	this.nodeId = other.getNodeId();    	
    	this.internalAddress = other.getInternalAddress();
    }
    
    /**
     * Reset this object to its initial state. Required by the
     * parser to be able to reuse node objects.
     */
    public void clear() {
        this.nodeId = null;
        this.internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
    } 
    
    public byte[] serialize() {
        throw new DOMException(DOMException.INVALID_ACCESS_ERR, "Can't serialize " + getClass().getName());
    }
    
    /**
     * Read a node from the specified byte array.
     * 
     * This checks the node type and calls the {@link #deserialize(byte[], int, int,DocumentImpl,boolean)}
     * method of the corresponding node class.
     * 
     * @param data
     * @param start
     * @param len
     * @param doc
     */
    public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc) {
        return deserialize(data, start, len, doc, false);
    }    
    
	/**
	 * Read a node from the specified byte array.
	 * 
	 * This checks the node type and calls the {@link #deserialize(byte[], int, int, DocumentImpl, boolean)}
	 * method of the corresponding node class. The node will be allocated in the pool
	 * and should be released once it is no longer needed.
	 * 
	 * @param data
	 * @param start
	 * @param len
	 * @param doc
	 */
	public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc, boolean pooled) {
	    short type = Signatures.getType(data[start]);
		switch (type) {
			case Node.TEXT_NODE :
				return TextImpl.deserialize(data, start, len, doc, pooled);
			case Node.ELEMENT_NODE :
				return ElementImpl.deserialize(data, start, len, doc, pooled);
			case Node.ATTRIBUTE_NODE :
				return AttrImpl.deserialize(data, start, len, doc, pooled);
			case Node.PROCESSING_INSTRUCTION_NODE :
				return ProcessingInstructionImpl.deserialize(data, start, len, doc, pooled);
			case Node.COMMENT_NODE :
				return CommentImpl.deserialize(data, start, len, doc, pooled);
            case Node.CDATA_SECTION_NODE :
                return CDATASectionImpl.deserialize(data, start, len, doc, pooled);
			default :
                LOG.error("Unknown node type: " + type);
				Thread.dumpStack();
				return null;
		}
	}
	
	public QName getQName() {
		switch(getNodeType()) {
			case Node.DOCUMENT_NODE:
			    return QName.DOCUMENT_QNAME;
			case Node.TEXT_NODE:
			    return QName.TEXT_QNAME;
			case Node.COMMENT_NODE:
			    return QName.COMMENT_QNAME;
			case Node.DOCUMENT_TYPE_NODE:
			    return QName.DOCTYPE_QNAME;
            default:
                LOG.error("Unknown node type: " + getNodeType()); 
                return null;
		}		
	}
 
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (!(obj instanceof StoredNode))
			return false;
        return ((StoredNode)obj).nodeId.equals(nodeId);
	}

    /**
     * The method <code>setNodeId</code>
     *
     * @param dln a <code>NodeId</code> value
     */
    public void setNodeId(NodeId dln) {
        this.nodeId = dln;
    }
    
    /**
     * The method <code>getNodeId</code>
     *
     * @return a <code>NodeId</code> value
     */
    public NodeId getNodeId() {
        return nodeId;
    }
    
	/**
	 *  Get the internal storage address of this node
	 *
	 *@return    The internalAddress value
	 */
	public long getInternalAddress() {
		return internalAddress;
	}
    
    /**
     *  Set the internal storage address of this node.
     *
     *@param  internalAddress  The new internalAddress value
     */
    public void setInternalAddress(long internalAddress) {
        this.internalAddress = internalAddress;
    }

    /**
     * Returns true if the node was modified recently and nodes
     * were inserted at the start or in the middle of its children.
     *
     * @return TRUE when node is 'dirty'
     */
    public boolean isDirty() {
        return true;
    }

    public void setDirty(boolean dirty) {
    }

    /**
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return this.nodeType;
	}

	/**
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return ownerDocument;
	}

    public DocumentImpl getDocument() {
        return ownerDocument;
    }
    
    /**
     *  Set the owner document.
     *
     *@param  ownerDocument  The new ownerDocument value
     */
    public void setOwnerDocument(DocumentImpl ownerDocument) {
   		this.ownerDocument = ownerDocument;
    }

    public int getDocId() {
   		return ownerDocument.getDocId();
    }
    
	/**
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		NodeId parentId = nodeId.getParentId();
		if (parentId == NodeId.DOCUMENT_NODE)
            return ownerDocument;
		// Filter out the temporary nodes wrapper element
		if (parentId.getTreeLevel() == 1 && ((DocumentImpl)getOwnerDocument()).getCollection().isTempCollection())
			return ownerDocument;
        return ownerDocument.getNode(parentId);
	}
	
	public StoredNode getParentStoredNode() {
		Node parent = getParentNode();
		return parent instanceof StoredNode ? (StoredNode) parent : null;
	}

	/**
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
        StoredNode parent = getParentStoredNode();
        if (parent == null) return null;
        if (parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                int level = nodeId.getTreeLevel();
                StoredNode last = null;
                while (reader.hasNext()) {
                    int status = reader.next();
                    NodeId currentId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                    if (status != XMLStreamReader.END_ELEMENT && currentId.getTreeLevel() == level) {
                        if (currentId.equals(nodeId))
                            return last;
                        last = reader.getNode();
                    }
                }
            } catch (IOException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } catch (XMLStreamException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } catch (EXistException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
            return null;
        } else {
            NodeId firstChild = parent.getNodeId().newChild();
            if (nodeId.equals(firstChild))
                return null;
            NodeId siblingId = nodeId.precedingSibling();
            return ownerDocument.getNode(siblingId);
        }
//        PreviousSiblingVisitor visitor = new PreviousSiblingVisitor(this);
//        ((StoredNode) parent).accept(visitor);
//        return visitor.last;
	}
    
	/**
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
        if (nodeId.getTreeLevel() == 2 && ((DocumentImpl)getOwnerDocument()).getCollection().isTempCollection())
            return null;
        final StoredNode parent = getParentStoredNode();
        if (parent == null) return null;
        if (parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                final int level = nodeId.getTreeLevel();
                while (reader.hasNext()) {
                    int status = reader.next();
                    NodeId currentId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                    if (status != XMLStreamReader.END_ELEMENT && currentId.getTreeLevel() == level) {
                        if (currentId.compareTo(nodeId) > 0)
                            return reader.getNode();
                    }
                }
            } catch (IOException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } catch (XMLStreamException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } catch (EXistException e) {
                LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
            return null;
        } else {
            NodeId siblingId = nodeId.nextSibling();
            return ownerDocument.getNode(siblingId);
        }

//        Iterator iterator = getBroker().getNodeIterator(this);
//        iterator.next();
//        getLastNode(iterator, this);
//        if (iterator.hasNext()) {
//            StoredNode sibling = (StoredNode) iterator.next();
//            return sibling.nodeId.isSiblingOf(nodeId) ? sibling : null;
//        }
//        return null;
	}
    
    protected StoredNode getLastNode(StoredNode node) {
        if (!node.hasChildNodes())
            return node;
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(node, true);
            while (reader.hasNext()) {
                reader.next();
            }
            return reader.getPreviousNode();
        } catch (IOException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch (XMLStreamException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch (EXistException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
//        final Iterator iterator = getBroker().getNodeIterator(node);
//        //TODO : hasNext() test ? -pb
//        iterator.next();
//        return getLastNode(iterator, node);
    }

    protected StoredNode getLastNode(Iterator iterator, StoredNode node) {
        if (!node.hasChildNodes())
            return node;
        final int children = node.getChildCount();
        StoredNode next = null;
        for (int i = 0; i < children; i++) {
            next = (StoredNode) iterator.next();
            //Recursivity helps taversing...
            next = getLastNode(iterator, next);
        }
        return next;
    }
    
    public NodePath getPath() {
        NodePath path = new NodePath();
        if (getNodeType() == NodeImpl.ELEMENT_NODE)
            path.addComponent(getQName());
        NodeImpl parent = (NodeImpl)getParentNode();
        while (parent != null && parent.getNodeType() != NodeImpl.DOCUMENT_NODE) {
            path.addComponentAtStart(parent.getQName());
            parent = (NodeImpl)parent.getParentNode();
        }
        return path;
    }    

    public NodePath getPath(NodePath parentPath) {
        if (getNodeType() == NodeImpl.ELEMENT_NODE)
            parentPath.addComponent(getQName());
        return parentPath;
    }

    public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(nodeId.toString());
		buf.append('\t');
		buf.append(getQName());
		return buf.toString();
	}

	public String toString(boolean top) {
		return toString();
	}
	
	/**
	 * Release all memory resources hold by this node. 
	 */
	public void release() {
        ownerDocument = null;
		clear();
//		NodeObjectPool.getInstance().returnNode(this);
        NodePool.getInstance().returnNode(this);
    }
    
    public boolean accept(NodeVisitor visitor) {
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final Iterator iterator = broker.getNodeIterator(this);
            iterator.next();
            return accept(iterator, visitor);
        } catch (EXistException e) {
            LOG.warn("Exception while reading node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return false;
    }
    
    public boolean accept(Iterator iterator, NodeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Deprecated
    private final static class PreviousSiblingVisitor implements NodeVisitor {
        
        private StoredNode current;
        private StoredNode last = null;
        
        public PreviousSiblingVisitor(StoredNode current) {
            this.current = current;
        }
        
        public boolean visit(StoredNode node) {
            if (node.nodeId.equals(current.nodeId))
                return false;
            if (node.nodeId.getTreeLevel() == current.nodeId.getTreeLevel())
                last = node;
            return true;
        }
    }
}
