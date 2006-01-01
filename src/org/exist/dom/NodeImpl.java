package org.exist.dom;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xquery.Constants;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

public abstract class NodeImpl implements Node, QNameable {

    public final static int NODE_IMPL_UNKNOWN_GID = -1;  
    public final static int NODE_IMPL_DOCUMENT_NODE_GID = -1;  
    public final static int NODE_IMPL_ROOT_NODE_GID = 1;  
    
    public final static short UNKNOWN_NODE_IMPL_NODE_TYPE = -1;
    
    protected final static Logger LOG = Logger.getLogger(NodeImpl.class);
    
	/**
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node child) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented on class " + getClass().getName());
	}

	public void appendChildren(Txn transaction, NodeList nodes, int child) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, 
		        "Cannot append children to a node of type " + getNodeType());
	}

	/**
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		return this;
	}
	
	public long firstChildID() {
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
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
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		return null;
	}

	/**
	 * Return the broker instance used to create this node.
	 * 
	 * @return
	 */
	public abstract DBBroker getBroker();
	
	/**
	 *  Get the unique identifier assigned to this node.
	 *
	 *@return
	 */
	public abstract long getGID();
	
	/**
	 *  Set the unique node identifier of this node.
	 *
	 *@param  gid  The new gID value
	 */
	public abstract void setGID(long gid);
	
	/**
	 *  Set the internal storage address of this node.
	 *
	 *@param  address  The new internalAddress value
	 */
	public abstract void setInternalAddress(long address);
	
	/**
	 *  Get the internal storage address of this node
	 *
	 *@return    The internalAddress value
	 */
	public abstract long getInternalAddress();
	
	/**
	 *  Get the unique node identifier of this node's parent node.
	 *
	 *@return    The parentGID value
	 */
	public abstract long getParentGID();
	
	/**
	 *  Set the owner document.
	 *
	 *@param  doc  The new ownerDocument value
	 */
	public abstract void setOwnerDocument(Document doc);
	
	public abstract QName getQName();
	
	/**
	 * Release all memory resources hold by this node. 
	 */
	public abstract void release();
	
	/**
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
	    QName nodeName = getQName();
		if (nodeName != null)
			return nodeName.getLocalName();
		return "";
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
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return "";
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

	public void insertAfter(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
	}

	public void insertBefore(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
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
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
		return 0;
	}

	/**
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
		return;
	}

	protected StoredNode getLastNode(StoredNode node) {
		final DocumentImpl owner = (DocumentImpl) getOwnerDocument();
		final NodeProxy p = new NodeProxy(owner, node.gid, node.internalAddress);
		Iterator iterator = owner.getBroker().getNodeIterator(p);
		iterator.next();
		return getLastNode(iterator, node);
	}

	protected StoredNode getLastNode(Iterator iterator, StoredNode node) {
		if (node.hasChildNodes()) {
			final long firstChild = node.firstChildID();
			final long lastChild = firstChild + node.getChildCount();
			StoredNode next = null;
			for (long gid = firstChild; gid < lastChild; gid++) {
				next = (StoredNode) iterator.next();
				next.setGID(gid);
				next = getLastNode(iterator, next);
			}
			return next;
		} else
			return node;
	}
	
	public Node removeChild(Node oldChild)
    throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "remove child is not supported. Use XUpdate instead.");
    }

    public Node removeChild(Txn transaction, Node oldChild)
    throws DOMException {
        return null;
    }
    
	/**
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
		return null;
	}

	public Node replaceChild(Txn transaction, Node newChild, Node oldChild) throws DOMException {
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
	
	/**
	 * Update a child node. This method will only update the child node
	 * but not its potential descendant nodes.
	 * 
	 * @param oldChild
	 * @param newChild
	 * @throws DOMException
	 */
	public void updateChild(Txn transaction, Node oldChild, Node newChild) throws DOMException {
		throw new DOMException(
				DOMException.NO_MODIFICATION_ALLOWED_ERR,
		"method not allowed on this node type");
	}
	
	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return Constants.EQUAL;
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}
}
