
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
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 *  The base class for all persistent DOM nodes in the database.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class NodeImpl implements Node {
	
	protected final static Logger LOG = Logger.getLogger(NodeImpl.class);

	protected long gid;
	protected long internalAddress = -1;
	protected short nodeType = 0;
	protected DocumentImpl ownerDocument = null;

	private NodeImpl() {
	}

	public NodeImpl(short nodeType) {
		this(nodeType, 0);
	}

	public NodeImpl(long gid) {
		this((short) 0, gid);
	}

	public NodeImpl(short nodeType, long gid) {
		this.nodeType = nodeType;
		this.gid = gid;
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
	public static NodeImpl deserialize(byte[] data, int start, int len, DocumentImpl doc,
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
	public static NodeImpl deserialize(byte[] data, int start, int len, DocumentImpl doc) {
		return deserialize(data, start, len, doc, false);
	}

	/**
	 * Reset this object to its initial state. Required by the
	 * parser to be able to reuse node objects.
	 */
	public void clear() {
		gid = 0;
		internalAddress = -1;
		ownerDocument = null;
	}

	/**
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node child) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	public Node appendChildren(NodeList nodes, int child) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	/**
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		return this;
	}
 
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (!(obj instanceof NodeImpl))
			return false;
		if (((NodeImpl) obj).gid == gid)
			return true;
		return false;
	}

	public long firstChildID() {
		return 0;
	}

	/**
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
		return null;
	}

	public short getAttributesCount() {
		return 0;
	}

	/**
	 * Return the broker instance used to create this node.
	 * 
	 * @return
	 */
	public DBBroker getBroker() {
		return (DBBroker) ownerDocument.broker;
	}

	public int getChildCount() {
		return 0;
	}

	public NodeList getChildNodes() {
		return (NodeList) new NodeListImpl();
	}

	/**
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		return null;
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
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		return null;
	}

	/**
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
	    QName nodeName = getQName();
		if (nodeName != null)
			return nodeName.getLocalName();
		return "";
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
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
	    QName nodeName = getQName();
		if (nodeName != null)
			return nodeName.getNamespaceURI();
		return "";
	}

	/**
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
	    QName nodeName = getQName();
	    if(nodeName != null)
	        return nodeName.toString();
	    return "";
	}

	/**
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return nodeType;
	}

	/**
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return "";
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
		int level = ownerDocument.getTreeLevel(gid);
		return (gid - ownerDocument.getLevelStartPoint(level))
			/ ownerDocument.getTreeLevelOrder(level)
			+ ownerDocument.getLevelStartPoint(level - 1);
		//return (gid - 2) / ownerDocument.getOrder() + 1;
	}

	/**
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		long pid = getParentGID();
		return pid < 0 ? ownerDocument : ownerDocument.getNode(pid);
	}

	public NodePath getPath() {
		NodePath path = new NodePath();
		Node parent = getParentNode();
		while (parent.getNodeType() != Node.DOCUMENT_NODE) {
		    path.addComponent(parent.getNodeName());
			parent = parent.getParentNode();
		}
		return path;
	}

	/**
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
	    QName nodeName = getQName();
		if (nodeName != null) {
		    final String prefix = nodeName.getPrefix();
		    return prefix == null ? "" : prefix;
		}
		return "";
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
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
		return false;
	}

	/**
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
		return false;
	}

	/**
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node insertBefore(Node newChild, Node refChild) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	public Node insertAfter(Node newChild, Node refChild) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	public Node insertAfter(NodeList nodes, Node refChild) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	public Node insertBefore(NodeList nodes, Node refChild) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}
	/**
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported(String key, String value) {
		return false;
	}

	/**
	 *  Get the unique node identifier of the last child of this node.
	 *
	 *@return    Description of the Return Value
	 */
	public long lastChildID() {
		return 0;
	}

	/**
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
		return;
	}

	/**
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild(Node node) throws DOMException {
		return null;
	}

	/**
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
		return null;
	}

	public byte[] serialize() {
		return null;
	}

	/**
	 *  Set the attributes that belong to this node.
	 *
	 *@param  attribNum  The new attributes value
	 */
	public void setAttributes(short attribNum) {
	}

	/**
	 *  Set the number of children.
	 *
	 *@param  count  The new childCount value
	 */
	protected void setChildCount(int count) {
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
	 *  Set the node name.
	 *
	 *@param  name  The new nodeName value
	 */
	public void setNodeName(QName name) {
	}

	/**
	 *  Set the node value.
	 *
	 *@param  value             The new nodeValue value
	 *@exception  DOMException  Description of the Exception
	 */
	public void setNodeValue(String value) throws DOMException {
	}

	/**
	 *  Set the owner document.
	 *
	 *@param  doc  The new ownerDocument value
	 */
	public void setOwnerDocument(Document doc) {
		ownerDocument = (DocumentImpl) doc;
	}

	/**
	 *  Sets the prefix attribute of the NodeImpl object
	 *
	 *@param  prefix            The new prefix value
	 *@exception  DOMException  Description of the Exception
	 */
	public void setPrefix(String prefix) throws DOMException {
	    QName nodeName = getQName();
		if (nodeName != null)
			nodeName.setPrefix(prefix);
	}

	/**
	 * Method supports.
	 * @param feature
	 * @param version
	 * @return boolean
	 */
	public boolean supports(String feature, String version) {
		return false;
	}

	public void toSAX(ContentHandler contentHandler, LexicalHandler lexicalHandler, boolean first)
		throws SAXException {
		toSAX(contentHandler, lexicalHandler, first, new TreeSet());
	}

	public void toSAX(
		ContentHandler contentHandler,
		LexicalHandler lexicalHandler,
		boolean first,
		Set namespaces)
		throws SAXException {
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

	protected NodeImpl getLastNode(NodeImpl node) {
		final NodeProxy p = new NodeProxy(ownerDocument, node.gid, node.internalAddress);
		Iterator iterator = ownerDocument.getBroker().getNodeIterator(p);
		iterator.next();
		return getLastNode(iterator, node);
	}

	protected NodeImpl getLastNode(Iterator iterator, NodeImpl node) {
		if (node.hasChildNodes()) {
			final long firstChild = node.firstChildID();
			final long lastChild = firstChild + node.getChildCount();
			NodeImpl next = null;
			for (long gid = firstChild; gid < lastChild; gid++) {
				next = (NodeImpl) iterator.next();
				next.setGID(gid);
				next = getLastNode(iterator, next);
			}
			return next;
		} else
			return node;
	}

	/**
		 * Update a child node. This method will only update the child node
		 * but not its potential descendant nodes.
		 * 
		 * @param oldChild
		 * @param newChild
		 * @throws DOMException
		 */
	public void updateChild(Node oldChild, Node newChild) throws DOMException {
		throw new DOMException(
			DOMException.NO_MODIFICATION_ALLOWED_ERR,
			"method not allowed on this node type");
	}
	
	/**
	 * Release all memory resources hold by this node. 
	 */
	public void release() {
		clear();
		NodeObjectPool.getInstance().returnNode(this);
	}
}
