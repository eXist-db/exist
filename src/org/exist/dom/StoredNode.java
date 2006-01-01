
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

import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *  The base class for all persistent DOM nodes in the database.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class StoredNode extends NodeImpl {

    public final static long UNKNOWN_NODE_IMPL_ADDRESS = -1;
	
    //TOUNDERSTAND : what are the semantics of this 0 ? -pb
	protected long gid = 0;
	protected long internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
	protected short nodeType = UNKNOWN_NODE_IMPL_NODE_TYPE;
	protected DocumentImpl ownerDocument = null;

	private StoredNode() {
	}

	public StoredNode(short nodeType) {
		this(nodeType, 0);
	}

	public StoredNode(long gid) {
		this(UNKNOWN_NODE_IMPL_NODE_TYPE, gid);
	}

	public StoredNode(short nodeType, long gid) {
		this.nodeType = nodeType;
		this.gid = gid;
	}

    /**
     * Copy constructor: creates a copy of the other node.
     * 
     * @param other
     */
    public StoredNode(StoredNode other) {
        this.nodeType = other.nodeType;
        this.gid = other.gid;
        this.internalAddress = other.internalAddress;
        this.ownerDocument = other.ownerDocument;
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
	public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc,
	        boolean pooled) {
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
				LOG.debug("Unknown node type: " + type);
				return null;
		}
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
	 * Reset this object to its initial state. Required by the
	 * parser to be able to reuse node objects.
	 */
	public void clear() {
		//TODO : what are the semantics of this 0 ? -pb		
		gid = 0;
		internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
		ownerDocument = null;
	}

	public QName getQName() {
		switch(nodeType) {
			case Node.DOCUMENT_NODE:
			    return QName.DOCUMENT_QNAME;
			case Node.TEXT_NODE:
			    return QName.TEXT_QNAME;
			case Node.COMMENT_NODE:
			    return QName.COMMENT_QNAME;
			case Node.DOCUMENT_TYPE_NODE:
			    return QName.DOCTYPE_QNAME;
		}
		return null;
	}
 
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (!(obj instanceof StoredNode))
			return false;
		if (((StoredNode) obj).gid == gid)
			return true;
		return false;
	}

	/**
	 * Return the broker instance used to create this node.
	 * 
	 * @return
	 */
	public DBBroker getBroker() {
		return (DBBroker) ownerDocument.broker;
	}

	/**
	 *  Get the unique identifier assigned to this node.
	 *
	 *@return
	 */
	public long getGID() {
		return gid;
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
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return nodeType;
	}

	/**
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return ownerDocument;
	}

	/**
	 *  Get the unique node identifier of this node's parent node.
	 *
	 *@return    The parentGID value
	 */
	public long getParentGID() {
	    return XMLUtil.getParentId(ownerDocument, gid);
	}

	/**
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		long pid = getParentGID();
		if (pid == NodeImpl.NODE_IMPL_UNKNOWN_GID)
            return null;
        return ownerDocument.getNode(pid);
	}

	public NodePath getPath() {
		NodePath path = new NodePath();
		if (nodeType != ATTRIBUTE_NODE)
			path.addComponent(getQName());
		NodeImpl parent = (NodeImpl)getParentNode();
		while (parent != null && parent.getNodeType() != DOCUMENT_NODE) {
		    path.addComponentAtStart(parent.getQName());
			parent = (NodeImpl)parent.getParentNode();
		}
		return path;
	}

	/**
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
		int level = ownerDocument.getTreeLevel(gid);
		if (level == 0)
			return ownerDocument.getPreviousSibling(this);
		long pid =
			(gid - ownerDocument.getLevelStartPoint(level))
				/ ownerDocument.getTreeLevelOrder(level)
				+ ownerDocument.getLevelStartPoint(level - 1);
		long firstChildId =
			(pid - ownerDocument.getLevelStartPoint(level - 1))
				* ownerDocument.getTreeLevelOrder(level)
				+ ownerDocument.getLevelStartPoint(level);
		if (gid > firstChildId)
			return ownerDocument.getNode(gid - 1);
		return null;
	}

	/**
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
		int level = ownerDocument.getTreeLevel(gid);
		if (level == 0)
			return ownerDocument.getFollowingSibling(this);
		long pid =
			(gid - ownerDocument.getLevelStartPoint(level))
				/ ownerDocument.getTreeLevelOrder(level)
				+ ownerDocument.getLevelStartPoint(level - 1);
		long firstChildId =
			(pid - ownerDocument.getLevelStartPoint(level - 1))
				* ownerDocument.getTreeLevelOrder(level)
				+ ownerDocument.getLevelStartPoint(level);
		if (gid < firstChildId + ownerDocument.getTreeLevelOrder(level) - 1)
			return ownerDocument.getNode(gid + 1);
		return null;
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
	 *  Set the internal storage address of this node.
	 *
	 *@param  address  The new internalAddress value
	 */
	public void setInternalAddress(long address) {
		internalAddress = address;
	}

	/**
	 *  Set the owner document.
	 *
	 *@param  doc  The new ownerDocument value
	 */
	public void setOwnerDocument(Document doc) {
		ownerDocument = (DocumentImpl) doc;
	}

	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(Long.toString(gid));
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
