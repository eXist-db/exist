package org.exist.dom;

import org.apache.log4j.Logger;
import org.exist.storage.txn.Txn;
import org.exist.xquery.Constants;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

public abstract class NodeImpl implements Node, QNameable {
    
    public final static short UNKNOWN_NODE_IMPL_NODE_TYPE = -1;
    
    protected final static Logger LOG = Logger.getLogger(NodeImpl.class);    

	/**
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		return this;
	}
    
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
     * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }

    public Node insertAfter(Node newChild, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }

    public Node removeChild(Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "remove child is not supported. Use XUpdate instead.");
    }
    
    public void insertBefore(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }
    
    public void insertAfter(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }    

    public Node removeChild(Txn transaction, Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }    
 
    public Node replaceChild(Txn transaction, Node newChild, Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
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
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }        
    
    /**
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        return null;
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
     * @see org.w3c.dom.Node#getAttributes()
     */
    public NamedNodeMap getAttributes() {
        return null;
    }

    public short getAttributesCount() {
        return 0;
    } 
    
    /**
     * @see org.w3c.dom.Node#getNodeValue()
     */
    public String getNodeValue() throws DOMException {
        return "";
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
     * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
     */
    public boolean isSupported(String key, String value) {
        return false;
    }

    /**
     * @see org.w3c.dom.Node#normalize()
     */
    public void normalize() {
        return;
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

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}
    
    /** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
     */
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        // maybe TODO - new DOM interfaces - Java 5.0
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
}
