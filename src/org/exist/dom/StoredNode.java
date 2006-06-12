
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import java.util.Iterator;

import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *  The base class for all persistent DOM nodes in the database.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class StoredNode extends NodeImpl {
    
    public final static long UNKNOWN_NODE_IMPL_ADDRESS = -1;
	
    private DocumentImpl ownerDocument = null;
	private long gid = NodeProxy.UNKNOWN_NODE_GID;
	private long internalAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
	private short nodeType = NodeProxy.UNKNOWN_NODE_TYPE;
    
	protected StoredNode(short nodeType) {
		this.nodeType = nodeType;
	}
    
    protected StoredNode(short nodeType, long gid) {
    	this.nodeType = nodeType;
    	this.gid = gid;
    }

    public StoredNode(NodeProxy other) {
    	this.ownerDocument = other.getDocument();
    	this.nodeType = other.getNodeType();
    	this.gid = other.getGID();
    	this.internalAddress = other.getInternalAddress();           
    } 
    
    public StoredNode(DocumentImpl doc, long gid, short nodeType, long address) {
    	this.ownerDocument = doc;
    	this.nodeType = nodeType;
    	this.gid = gid;
		this.internalAddress = address;
	}    

    /**
     * Copy constructor: creates a copy of the other node.
     * 
     * @param other
     */
    public StoredNode(StoredNode other) {
    	this.ownerDocument = (DocumentImpl)other.getOwnerDocument();
    	this.nodeType = other.getNodeType();
    	this.gid = other.getGID();
    	this.internalAddress = other.getInternalAddress();           
    }
    
    /**
     * Reset this object to its initial state. Required by the
     * parser to be able to reuse node objects.
     */
    public void clear() {
    	this.ownerDocument = null;
    	//nodeType is *immutable*
    	this.gid = NodeProxy.UNKNOWN_NODE_GID;
        this.internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
    } 
    
    public byte[] serialize() {
        throw new DOMException(DOMException.INVALID_ACCESS_ERR, "Can't serialize " + getClass().getName());
    }
    
    /**
     * Read a node from the specified byte array.
     * 
     * This checks the node type and calls the {@link #deserialize(byte[], int, int)}
     * method of the corresponding node class.
     * 
     * @param data
     * @param start
     * @param len
     * @param doc
     * @return
     */
    public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc) {
        return deserialize(data, start, len, doc, false);
    }    
    
	/**
	 * Read a node from the specified byte array.
	 * 
	 * This checks the node type and calls the {@link #deserialize(byte[], int, int)}
	 * method of the corresponding node class. The node will be allocated in the pool
	 * and should be released once it is no longer needed.
	 * 
	 * @param data
	 * @param start
	 * @param len
	 * @param doc
	 * @return
	 */
	public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc, boolean pooled) {
	    short type = Signatures.getType(data[start]);
		switch (type) {
			case Node.TEXT_NODE :
				return TextImpl.deserialize(data, start, len, pooled);
			case Node.ELEMENT_NODE :
				return ElementImpl.deserialize(data, start, len, doc, pooled);
			case Node.ATTRIBUTE_NODE :
				return AttrImpl.deserialize(data, start, len, doc, pooled);
			case Node.PROCESSING_INSTRUCTION_NODE :
				return ProcessingInstructionImpl.deserialize(data, start, len, pooled);
			case Node.COMMENT_NODE :
				return CommentImpl.deserialize(data, start, len, pooled);
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
		if (((StoredNode) obj).getGID() == getGID())
			return true;
		return false;
	}

	/**
	 * Return the broker instance used to create this node.
	 * 
	 * @return
	 */
	public DBBroker getBroker() {
		return ownerDocument.getBroker();
	}

	/**
	 *  Get the unique identifier assigned to this node.
	 *
	 *@return
	 */
	public long getGID() {
		return this.gid;
	}
    
    /**
     *  Set the unique node identifier of this node.
     *
     *@param  gid  The new gID value
     */
    public void setGID(long gid) {
    	this.gid = gid;    	
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
     *@param  address  The new internalAddress value
     */
    public void setInternalAddress(long internalAddress) {
        this.internalAddress = internalAddress;
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

    /**
     *  Set the owner document.
     *
     *@param  doc  The new ownerDocument value
     */
    public void setOwnerDocument(DocumentImpl ownerDocument) {
   		this.ownerDocument = ownerDocument;
    }
    
    public int getDocId() {
   		return ownerDocument.getDocId();
    }    
    
	/**
	 *  Get the unique node identifier of this node's parent node.
	 *
	 *@return    The parentGID value
	 */
	public long getParentGID() {
        return NodeSetHelper.getParentId(ownerDocument, getGID());
	}
    
    public long firstChildID(){
        return NodeSetHelper.getFirstChildId(ownerDocument, getGID());
    }

	/**
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		long parentID = getParentGID();       
		if (parentID == NodeProxy.UNKNOWN_NODE_GID)
            return null;         
        if (parentID == NodeProxy.DOCUMENT_NODE_GID ||
	        	//Filter out the temporary nodes wrapper element
	        	parentID == NodeProxy.DOCUMENT_ELEMENT_GID && 
	        	(ownerDocument.getCollection().isTempCollection())) {
            return null;    
        }
        return ownerDocument.getNode(parentID);
	}      

	/**
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
        int level = ownerDocument.getTreeLevel(getGID());
		if (level == 0)
			return ownerDocument.getPreviousSibling(this);
        
        long parentID = NodeSetHelper.getParentId(ownerDocument, getGID(), level);
        
        //Filter out the temporary nodes wrapper element         
        //TODO : use level == 1 ?
        if (parentID == NodeProxy.DOCUMENT_NODE_GID || 
                parentID == NodeProxy.DOCUMENT_ELEMENT_GID && ownerDocument.getCollection().isTempCollection())
            return null;       
        
        long firstChildId = NodeSetHelper.getFirstChildId(ownerDocument, parentID, level - 1);
		if (getGID() > firstChildId)
			return ownerDocument.getNode(getGID() - 1);
		return null;
	}

	/**
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
		int level = ownerDocument.getTreeLevel(getGID());
		if (level == 0)
			return ownerDocument.getFollowingSibling(this);
        
        long parentID = NodeSetHelper.getParentId(ownerDocument, getGID(), level);
        
        //Filter out the temporary nodes wrapper element 
        //TODO : use level == 1 ?
        if (parentID == NodeProxy.DOCUMENT_NODE_GID || 
                (parentID == NodeProxy.DOCUMENT_ELEMENT_GID && 
                 (ownerDocument.getCollection().isTempCollection())))
            return null;

        long firstChildId = NodeSetHelper.getFirstChildId(ownerDocument, parentID, level - 1);
        
        //TODO : avoid using getTreeLevelOrder
		if (getGID() < firstChildId + ownerDocument.getTreeLevelOrder(level) - 1)
			return ownerDocument.getNode(getGID() + 1);
		return null;
	}
    
    protected StoredNode getLastNode(StoredNode node) { 
        final Iterator iterator = getBroker().getNodeIterator(node);
        //TODO : hasNext() test ? -pb
        iterator.next();
        return getLastNode(iterator, node);
    }

    protected StoredNode getLastNode(Iterator iterator, StoredNode node) {
        if (!node.hasChildNodes())
            return node;
        final long firstChild = node.firstChildID();
        final long lastChild = firstChild + node.getChildCount();
        StoredNode next = null;
        for (long gid = firstChild; gid < lastChild; gid++) {
            next = (StoredNode) iterator.next();            
            next.setGID(gid);
            //Recursivity helps taversing...
            next = getLastNode(iterator, next);
        }
        return next;
    }     
    
    public NodePath getPath() {
        NodePath path = new NodePath();
        if (getNodeType() != NodeImpl.ATTRIBUTE_NODE)
            path.addComponent(getQName());
        NodeImpl parent = (NodeImpl)getParentNode();
        while (parent != null && parent.getNodeType() != NodeImpl.DOCUMENT_NODE) {
            path.addComponentAtStart(parent.getQName());
            parent = (NodeImpl)parent.getParentNode();
        }
        return path;
    }    

	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(Long.toString(getGID()));
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
		clear();
		NodeObjectPool.getInstance().returnNode(this);
	}
    
}
