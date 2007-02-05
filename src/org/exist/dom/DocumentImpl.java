/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.dom;
import java.io.EOFException;
import java.io.IOException;

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.storage.StorageAddress;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.MultiReadReentrantLock;
import org.exist.storage.txn.Txn;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.DescendantSelector;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeSelector;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

/**
 *  Represents a persistent document object in the database;
 *  it can be an XML_FILE , a BINARY_FILE, or Xquery source code.
 *  
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class DocumentImpl extends NodeImpl implements Document, Comparable {

    public final static int UNKNOWN_DOCUMENT_ID = -1;
    
    public final static byte XML_FILE = 0;
	public final static byte BINARY_FILE = 1;
	
	public static int LENGTH_DOCUMENT_ID = 4; //sizeof int 
	public static int LENGTH_DOCUMENT_TYPE = 1; //sizeof byte 
	
	//public final static byte DOCUMENT_NODE_SIGNATURE = 0x0F;

	protected transient DBBroker broker = null;

	/** number of child nodes */
	private int children = 0;

	private long[] childAddress = null;

	/** the collection this document belongs to */
	private transient Collection collection = null;

	/** the document's id */
	private int docId = UNKNOWN_DOCUMENT_ID;

	/** the document's file name */
	private XmldbURI fileURI = null;
    
    //TODO : make private
    protected Permission permissions = PermissionFactory.getPermission(Permission.DEFAULT_PERM);
    
    private transient Lock updateLock = null;
    
    private DocumentMetadata metadata = null;    
    
    public DocumentImpl(DBBroker broker) {
        this(broker, null, null);
    }    
	
    public DocumentImpl(DBBroker broker, Collection collection) {
        this(broker, collection, null);
    }

    public DocumentImpl(DBBroker broker, XmldbURI fileURI) {
        this(broker, null, fileURI);       
    }

    public DocumentImpl(DBBroker broker, Collection collection, XmldbURI fileURI) {
		this.broker = broker;
        this.collection = collection;
		this.fileURI = fileURI;		
	}

	public String getLocalName() {		
        return "";
	}
	
	public String getNamespaceURI() {
        return "";
	}		
    
    /************************************************
     * 
     * Document metadata
     *
     ************************************************/
    
    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection parent) {
        this.collection = parent;
    }

    public int getDocId() {
        return docId;
    }
    
    public void setDocId(int docId) {
        this.docId = docId;
    }     
    
    /**
     * Returns the type of this resource, either  {@link #XML_FILE} or 
     * {@link #BINARY_FILE}.
     * 
     */
    public byte getResourceType() {
        return XML_FILE;
    }
    
    public XmldbURI getFileURI() {
        //checkAvail();
        return fileURI;
    }
    
    public void setFileURI(XmldbURI fileURI) {
        this.fileURI = fileURI;
    } 
    
    public XmldbURI getURI() {
        return collection.getURI().append(fileURI);
    }
    
    public Permission getPermissions() {
        return permissions;
    }
    
    public void setPermissions(int mode) {
        permissions.setPermissions(mode);
    }

    public void setPermissions(String mode) throws SyntaxException {
        permissions.setPermissions(mode);
    }

    public void setPermissions(Permission perm) {
        permissions = perm;
    }     
    
	public void setMetadata(DocumentMetadata meta) {
		this.metadata = meta;
	}
	
	public DocumentMetadata getMetadata() {
		if (metadata == null) {
			broker.getResourceMetadata(this);
		}
		return metadata;
	}
	
    /************************************************
     * 
     * Persistent node methods
     *
     ************************************************/
	
	/**
	 * Copy the relevant internal fields from the specified document object.
	 * This is called by {@link Collection} when replacing a document.
	 * 
	 * @param other
	 */
	public void copyOf(DocumentImpl other) {
	    childAddress = null;
	    children = 0;
	    if (metadata == null)
	    	metadata = new DocumentMetadata();
	    metadata.setLastModified(other.getMetadata().getLastModified());
	    // reset pageCount: will be updated during storage
	    metadata.setPageCount(0);
	}
	
	public void copyChildren(DocumentImpl other) {
		childAddress = other.childAddress;
		children = other.children;
	}
    
	/**
	 * Returns true if the document is currently locked for
	 * write.
	 * 
	 */
	public synchronized boolean isLockedForWrite() {
		return getUpdateLock().isLockedForWrite();
	}

    /**
     * Returns the update lock associated with this
     * resource.
     * 
     */     
    public final synchronized Lock getUpdateLock() {
        if(updateLock == null)
            updateLock = new MultiReadReentrantLock();
        return updateLock;
    }
    
    public void setUserLock(User user) {
		getMetadata().setUserLock(user == null ? 0 : user.getUID());
	}
	
	public User getUserLock() {
		int lockOwnerId = getMetadata().getUserLock();
		if(lockOwnerId == 0)
			return null;
		final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
		return secman.getUser(lockOwnerId);
	}
    
	/**
	 * Returns the estimated size of the data in this document.
	 * 
	 * As an estimation, the number of pages occupied by the document
	 * is multiplied with the current page size.
	 * 
	 */
	public int getContentLength() {
            int length = getMetadata().getPageCount() * broker.getPageSize();
	    return (length<0) ? 0 : length;
	}
    
	public void triggerDefrag() {		
        int fragmentationLimit = -1;
        if (broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR) != null)
        	fragmentationLimit = ((Integer)broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR)).intValue();
        if (fragmentationLimit != -1)
        	getMetadata().setSplitCount(fragmentationLimit);
	}

    public SymbolTable getSymbols() {
        return broker.getSymbols();
    }
    
    public Node getNode(NodeId nodeId) {
    	if (nodeId.getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(this, nodeId);
    }
    
    public Node getNode(NodeProxy p) {
        if(p.getNodeId().getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(p);
    }
    
    private void resizeChildList() {
        long[] newChildList = new long[children];
        if(childAddress != null)
            System.arraycopy(childAddress, 0, newChildList, 0, childAddress.length);
        childAddress = newChildList;
    }
    
	public void appendChild(StoredNode child) throws DOMException {
		++children;
		resizeChildList();
		childAddress[children - 1] = child.getInternalAddress();
	}
    
	public void write(VariableByteOutputStream ostream) throws IOException {
		try {
			if (!getCollection().isTempCollection() && !getUpdateLock().isLockedForWrite()) {
				LOG.warn("document not locked for write !");
			}
            ostream.writeInt(docId);
            ostream.writeUTF(fileURI.toString());
            final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
            if (secman == null) {
                //TODO : explain those 2 values -pb
                ostream.writeInt(1);
                ostream.writeInt(1);
            } else {
                User user = secman.getUser(permissions.getOwner());
                Group group = secman.getGroup(permissions.getOwnerGroup());
                if (group == null)
                    group = secman.getGroup(SecurityManager.GUEST_GROUP);
                ostream.writeInt(user.getUID());
                ostream.writeInt(group.getId());
            }
            ostream.writeInt(permissions.getPermissions());
            ostream.writeInt(children);
            if (children > 0) {
			    for(int i = 0; i < children; i++) {
					ostream.writeInt(StorageAddress.pageFromPointer(childAddress[i]));
					ostream.writeShort(StorageAddress.tidFromPointer(childAddress[i]));
			    }
			}
            metadata.write(broker, ostream);
		} catch (IOException e) {
			LOG.warn("io error while writing document data", e);
		}
	}

	public void read(VariableByteInput istream) throws IOException, EOFException {
		try {
            docId = istream.readInt();
            fileURI = XmldbURI.createInternal(istream.readUTF());
            final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
            final int uid = istream.readInt();
            final int gid = istream.readInt();
            //TODO : Why such a mask ? -pb
            final int perm = (istream.readInt() & 0777);
            if (secman == null) {
                permissions.setOwner(SecurityManager.DBA_USER);
                permissions.setGroup(SecurityManager.DBA_GROUP);
            } else {
                permissions.setOwner(secman.getUser(uid));
                Group group = secman.getGroup(gid);
                if (group != null)
                    permissions.setGroup(group.getName());
            }
            permissions.setPermissions(perm);
            //Should be > 0 ;-)
            children = istream.readInt();            
			childAddress = new long[children];
			for (int i = 0; i < children; i++) { 
				childAddress[i] = StorageAddress.createPointer(istream.readInt(), istream.readShort());
			}
		} catch (IOException e) {
            LOG.error("IO error while reading document data for document " + fileURI, e);
		}
	}

    public void readDocumentMeta(VariableByteInput istream) {
        // skip over already known document data
        try {
            istream.skip(1);
            istream.readUTF();
            istream.skip(4);
            istream.skip(children * 2);

            metadata = new DocumentMetadata();
            metadata.read(broker, istream);
        } catch (IOException e) {
            LOG.error("IO error while reading document metadata for " + fileURI, e);
        }

    }

    public final int compareTo(Object other) {
		final long otherId = ((DocumentImpl)other).docId;
		if (otherId == docId)
			return Constants.EQUAL;
		else if (docId < otherId)
			return Constants.INFERIOR;
		else
			return Constants.SUPERIOR;
	} 

    public DBBroker getBroker() {
        return broker;
    }
    
    public void setBroker(DBBroker broker) {
        this.broker = broker;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeImpl#updateChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public void updateChild(Txn transaction, Node oldChild, Node newChild) throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Node does not belong to this document");
        final StoredNode oldNode = (StoredNode) oldChild;
        final StoredNode newNode = (StoredNode) newChild;
        final StoredNode previousNode = (StoredNode) oldNode.getPreviousSibling();      
        if (previousNode == null)            
            throw new DOMException(DOMException.NOT_FOUND_ERR, "No previous sibling for the old child");
        if (oldChild.getNodeType() == Node.ELEMENT_NODE) {
            // replace the document-element
            //TODO : be more precise in the type test -pb
            if (newChild.getNodeType() != Node.ELEMENT_NODE)
                throw new DOMException(
                    DOMException.INVALID_MODIFICATION_ERR,
                    "A node replacing the document root needs to be an element");
            broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
            broker.endRemove();
            newNode.setNodeId(oldNode.getNodeId());
            broker.insertNodeAfter(null, previousNode, newNode);
            NodePath path = newNode.getPath();
            broker.indexNode(transaction, newNode, path);
            broker.endElement(newNode, path, null);
            broker.flush();
        } else {
            broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
            broker.endRemove();
            newNode.setNodeId(oldNode.getNodeId());
            broker.insertNodeAfter(transaction, previousNode, newNode);
        }
    }

    /*
     * @see org.exist.dom.NodeImpl#insertBefore(org.w3c.dom.NodeList, org.w3c.dom.Node)
     */
    public void insertBefore(NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }

    public void insertAfter(NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		if (children == 0)
		    return null;
		return broker.objectWith(
            new NodeProxy(this, NodeId.DOCUMENT_NODE, childAddress[0])
        );
	}	
    
	public long getFirstChildAddress() {
		if (children == 0)
			return StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
		return childAddress[0];
	}
    	
	public NodeList getChildNodes() {
		NodeListImpl list = new NodeListImpl();		
		for (int i = 0; i < children; i++) {
            Node child = broker.objectWith(
			        new NodeProxy(this, NodeId.DOCUMENT_NODE, childAddress[i])
                );
			list.add(child);
		}
		return list;
	}
    
	protected Node getPreviousSibling(StoredNode node) {
		NodeList cl = getChildNodes();		
		for (int i = 0; i < cl.getLength(); i++) {
            StoredNode next = (StoredNode) cl.item(i);
			if (StorageAddress.equals(node.getInternalAddress(), next.getInternalAddress()))
				return i == 0 ? null : cl.item(i - 1);
		}
		return null;
	}

	protected Node getFollowingSibling(StoredNode node) {
		NodeList cl = getChildNodes();		
		for (int i = 0; i < cl.getLength(); i++) {
            StoredNode next = (StoredNode) cl.item(i);
			if (StorageAddress.equals(node.getInternalAddress(), next.getInternalAddress()))
				return i == children - 1 ? null : cl.item(i + 1);
		}
		return null;
	}     
    
    protected NodeList findElementsByTagName(StoredNode root, QName qname) {
        DocumentSet docs = new DocumentSet();
        docs.add(this);
        NodeProxy p = new NodeProxy(this, root.getNodeId(), root.getInternalAddress());
        NodeSelector selector = new DescendantSelector(p, Expression.NO_CONTEXT_ID);
        return broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, docs, qname, selector);
    } 
    
    /************************************************
     * 
     * NodeImpl methods
     *
     ************************************************/
    
    public DocumentType getDoctype() {
        return getMetadata().getDocType();
    }     
    
    public void setDocumentType(DocumentType docType) {
        getMetadata().setDocType(docType);
    }    
    
    public Document getOwnerDocument() {        
        return this;
    }
    
    public void setOwnerDocument(Document doc) {
        if (doc != this)
            throw new IllegalArgumentException("Can't set owner document");
    }     
    
    public QName getQName() {
        return QName.DOCUMENT_QNAME;
    }    

    public short getNodeType() {
        return Node.DOCUMENT_NODE;
    }

    public Node getPreviousSibling() {
        //Documents don't have siblings
        return null;
    }

    public Node getNextSibling() {
        //Documents don't have siblings
        return null;
    }    
    
    public Attr createAttribute(String name) throws DOMException {
        AttrImpl attr = new AttrImpl(new QName(name, "", null), null);
        attr.setOwnerDocument(this);
        return attr;
    }

    public Attr createAttributeNS(String namespaceURI, String qualifiedName) throws DOMException {
        String name;
        String prefix;        
        int p = qualifiedName.indexOf(':');
        if (p == Constants.STRING_NOT_FOUND) {
            prefix =null; 
            name =  qualifiedName;
        } else {
            prefix = qualifiedName.substring(0, p);
            name = qualifiedName.substring(p); 
        }
        AttrImpl attr = new AttrImpl(new QName(name, namespaceURI, prefix), null);
        attr.setOwnerDocument(this);
        return attr;
    }

    public Element createElement(String tagName) throws DOMException {
        ElementImpl element = new ElementImpl(new QName(tagName, "", null));
        element.setOwnerDocument(this);
        return element;
    }

    public Element createElementNS(String namespaceURI, String qualifiedName) throws DOMException {
        String name;
        String prefix;
        int p = qualifiedName.indexOf(':');        
        if (p == Constants.STRING_NOT_FOUND) {
            prefix =null;
            name = qualifiedName;            
        } else {                 
            prefix = qualifiedName.substring(0, p); 
            name = qualifiedName.substring(p);            
        }          
        ElementImpl element = new ElementImpl(new QName(name, namespaceURI, prefix));
        element.setOwnerDocument(this);
        return element;
    }

    public Text createTextNode(String data) {
        TextImpl text = new TextImpl(data);
        text.setOwnerDocument(this);
        return text;
    }
    
    /*
     *  W3C Document-Methods
     */

    public Element getDocumentElement() {
        NodeList cl = getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            if (cl.item(i).getNodeType() == Node.ELEMENT_NODE)
                return (Element) cl.item(i);
        }
        return null;
    }

    public NodeList getElementsByTagName(String tagname) {
        return getElementsByTagNameNS("", tagname);
    }

    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        DocumentSet docs = new DocumentSet();
        docs.add(this);
        QName qname = new QName(localName, namespaceURI, null);
        return broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, docs, qname, null);
    }
    
    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getParentNode()
     */    
    public Node getParentNode() {
        //Documents d'on have parents
        return null;
    }    
    
    public int getChildCount() {
        return children;
    }     

    public void setChildCount(int count) {
        children = count;
        if (children == 0)
            childAddress = null;
    }
    
    public String getEncoding() {
        //TODO : on demand result (e.g. from serializer's settings) ? -pb
        return "UTF-8";
    } 
    
    public void setEncoding(String enc) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setEncoding not implemented on class " + getClass().getName());
    } 
    
    public String getVersion() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getVersion not implemented on class " + getClass().getName());
    }
    
    public void setVersion(String version) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setVersion not implemented on class " + getClass().getName());
    }     
    
    public boolean getStandalone() {
        //TODO : on demand result (e.g. from serializer's settings) ? -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getStandalone not implemented on class " + getClass().getName());
    }    
    
    public void setStandalone(boolean alone) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setStandalone not implemented on class " + getClass().getName());
    }    
    
    public CDATASection createCDATASection(String data) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createCDATASection not implemented on class " + getClass().getName());        
    }

    public Comment createComment(String data) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createComment not implemented on class " + getClass().getName());
    }

    public DocumentFragment createDocumentFragment() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createDocumentFragment not implemented on class " + getClass().getName());
    }
    
    public EntityReference createEntityReference(String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createEntityReference not implemented on class " + getClass().getName());
    }

    public ProcessingInstruction createProcessingInstruction(String target, String data)
            throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createProcessingInstruction not implemented on class " + getClass().getName());
    }     
    
    public Element getElementById(String elementId) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getElementById not implemented on class " + getClass().getName());
    }    
    
    public org.w3c.dom.DOMImplementation getImplementation() {
        return new StoredDOMImplementation();
    } 

    public boolean getStrictErrorChecking() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getStrictErrorChecking not implemented on class " + getClass().getName());
    }
    
    public Node adoptNode(Node node) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "adoptNode not implemented on class " + getClass().getName());
    }    

    public Node importNode(Node importedNode, boolean deep) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "importNode not implemented on class " + getClass().getName());
    }

    public boolean isSupported(String type, String value) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isSupported not implemented on class " + getClass().getName());
    }    
    
    public void setStrictErrorChecking(boolean strict) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setStrictErrorChecking not implemented on class " + getClass().getName());
    }          

	/** ? @see org.w3c.dom.Document#getInputEncoding()
	 */
	public String getInputEncoding() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "String getInputEncoding not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Document#getXmlEncoding()
	 */
	public String getXmlEncoding() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getXmlEncoding not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Document#getXmlStandalone()
	 */
	public boolean getXmlStandalone() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getXmlStandalone not implemented on class " + getClass().getName());
    }

	/** ? @see org.w3c.dom.Document#setXmlStandalone(boolean)
	 */
	public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setXmlStandalone not implemented on class " + getClass().getName());		
	}

	/** ? @see org.w3c.dom.Document#getXmlVersion()
	 */
	public String getXmlVersion() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getXmlVersion not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Document#setXmlVersion(java.lang.String)
	 */
	public void setXmlVersion(String xmlVersion) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setXmlVersion not implemented on class " + getClass().getName());		
	}

	/** ? @see org.w3c.dom.Document#getDocumentURI()
	 */
	public String getDocumentURI() {
        //TODO : easy to implement once we have stabule base-URIs -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getDocumentURI not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
	 */
	public void setDocumentURI(String documentURI) {
        //TODO : non-writable -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setDocumentURI not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Document#getDomConfig()
	 */
	public DOMConfiguration getDomConfig() {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getDomConfig not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Document#normalizeDocument()
	 */
	public void normalizeDocument() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "normalizeDocument not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Document#renameNode(org.w3c.dom.Node, java.lang.String, java.lang.String)
	 */
	public Node renameNode(Node n, String namespaceURI, String qualifiedName) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "renameNode not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
        //TODO : read it from broker's context -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getBaseURI not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "compareDocumentPosition not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getTextContent not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setTextContent not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
        //TODO : compare node identities ? -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isSameNode not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "lookupPrefix(String namespaceURI) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isDefaultNamespace not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
        //TODO : use broker's context ? -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "lookupNamespaceURI not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isEqualNode not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getFeature not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setUserData not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getUserData not implemented on class " + getClass().getName());
	}
    
	public String toString() {
		return getURI() + " - <" + 
		( getDocumentElement() != null ? getDocumentElement().getNodeName() : null ) + ">";	
	}
}
