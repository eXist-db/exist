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

import java.io.EOFException;
import java.io.IOException;

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
    
    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * @param broker a <code>DBBroker</code> value
     */
    public DocumentImpl(DBBroker broker) {
        this(broker, null, null);
    }    
	
    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * @param broker a <code>DBBroker</code> value
     * @param collection a <code>Collection</code> value
     */
    public DocumentImpl(DBBroker broker, Collection collection) {
        this(broker, collection, null);
    }

    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * @param broker a <code>DBBroker</code> value
     * @param fileURI a <code>XmldbURI</code> value
     */
    public DocumentImpl(DBBroker broker, XmldbURI fileURI) {
        this(broker, null, fileURI);       
    }

    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * @param broker a <code>DBBroker</code> value
     * @param collection a <code>Collection</code> value
     * @param fileURI a <code>XmldbURI</code> value
     */
    public DocumentImpl(DBBroker broker, Collection collection, XmldbURI fileURI) {
	this.broker = broker;
        this.collection = collection;
	this.fileURI = fileURI;		
    }

    /**
     * The method <code>getLocalName</code>
     *
     * @return a <code>String</code> value
     */
    public String getLocalName() {		
        return "";
    }
	
    /**
     * The method <code>getNamespaceURI</code>
     *
     * @return a <code>String</code> value
     */
    public String getNamespaceURI() {
        return "";
    }		
    
    /************************************************
     * 
     * Document metadata
     *
     ************************************************/

    /**
     * The method <code>getCollection</code>
     *
     * @return a <code>Collection</code> value
     */
    public Collection getCollection() {
        return collection;
    }

    /**
     * The method <code>setCollection</code>
     *
     * @param parent a <code>Collection</code> value
     */
    public void setCollection(Collection parent) {
        this.collection = parent;
    }

    /**
     * The method <code>getDocId</code>
     *
     * @return an <code>int</code> value
     */
    public int getDocId() {
        return docId;
    }
    
    /**
     * The method <code>setDocId</code>
     *
     * @param docId an <code>int</code> value
     */
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
    
    /**
     * The method <code>getFileURI</code>
     *
     * @return a <code>XmldbURI</code> value
     */
    public XmldbURI getFileURI() {
        //checkAvail();
        return fileURI;
    }
    
    /**
     * The method <code>setFileURI</code>
     *
     * @param fileURI a <code>XmldbURI</code> value
     */
    public void setFileURI(XmldbURI fileURI) {
        this.fileURI = fileURI;
    } 
    
    /**
     * The method <code>getURI</code>
     *
     * @return a <code>XmldbURI</code> value
     */
    public XmldbURI getURI() {
        return collection.getURI().append(fileURI);
    }
    
    /**
     * The method <code>getPermissions</code>
     *
     * @return a <code>Permission</code> value
     */
    public Permission getPermissions() {
        return permissions;
    }
    
    /**
     * The method <code>setPermissions</code>
     *
     * @param mode an <code>int</code> value
     */
    public void setPermissions(int mode) {
        permissions.setPermissions(mode);
    }

    /**
     * The method <code>setPermissions</code>
     *
     * @param mode a <code>String</code> value
     * @exception SyntaxException if an error occurs
     */
    public void setPermissions(String mode) throws SyntaxException {
        permissions.setPermissions(mode);
    }

    /**
     * The method <code>setPermissions</code>
     *
     * @param perm a <code>Permission</code> value
     */
    public void setPermissions(Permission perm) {
        permissions = perm;
    }     
    
    /**
     * The method <code>setMetadata</code>
     *
     * @param meta a <code>DocumentMetadata</code> value
     */
    public void setMetadata(DocumentMetadata meta) {
	this.metadata = meta;
    }
	
    /**
     * The method <code>getMetadata</code>
     *
     * @return a <code>DocumentMetadata</code> value
     */
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
     * @param other a <code>DocumentImpl</code> value
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
	
    /**
     * The method <code>copyChildren</code>
     *
     * @param other a <code>DocumentImpl</code> value
     */
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
            updateLock = new MultiReadReentrantLock(collection.getURI().append(fileURI));
        return updateLock;
    }
    
    /**
     * The method <code>setUserLock</code>
     *
     * @param user an <code>User</code> value
     */
    public void setUserLock(User user) {
	getMetadata().setUserLock(user == null ? 0 : user.getUID());
    }
	
    /**
     * The method <code>getUserLock</code>
     *
     * @return an <code>User</code> value
     */
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
    
    /**
     * The method <code>triggerDefrag</code>
     *
     */
    public void triggerDefrag() {		
        int fragmentationLimit = -1;
        if (broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR) != null)
	    fragmentationLimit = ((Integer)broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR)).intValue();
        if (fragmentationLimit != -1)
	    getMetadata().setSplitCount(fragmentationLimit);
    }

    /**
     * The method <code>getSymbols</code>
     *
     * @return a <code>SymbolTable</code> value
     */
    public SymbolTable getSymbols() {
        return broker.getBrokerPool().getSymbols();
    }
    
    /**
     * The method <code>getNode</code>
     *
     * @param nodeId a <code>NodeId</code> value
     * @return a <code>Node</code> value
     */
    public Node getNode(NodeId nodeId) {
    	if (nodeId.getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(this, nodeId);
    }
    
    /**
     * The method <code>getNode</code>
     *
     * @param p a <code>NodeProxy</code> value
     * @return a <code>Node</code> value
     */
    public Node getNode(NodeProxy p) {
        if(p.getNodeId().getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(p);
    }
    
    /**
     * The method <code>resizeChildList</code>
     *
     */
    private void resizeChildList() {
        long[] newChildList = new long[children];
        if(childAddress != null)
            System.arraycopy(childAddress, 0, newChildList, 0, childAddress.length);
        childAddress = newChildList;
    }
    
    /**
     * The method <code>appendChild</code>
     *
     * @param child a <code>StoredNode</code> value
     * @exception DOMException if an error occurs
     */
    public void appendChild(StoredNode child) throws DOMException {
	++children;
	resizeChildList();
	childAddress[children - 1] = child.getInternalAddress();
    }
    
    /**
     * The method <code>write</code>
     *
     * @param ostream a <code>VariableByteOutputStream</code> value
     * @exception IOException if an error occurs
     */
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
            getMetadata().write(broker, ostream);
	} catch (IOException e) {
	    LOG.warn("io error while writing document data", e);
	}
    }

    /**
     * The method <code>read</code>
     *
     * @param istream a <code>VariableByteInput</code> value
     * @exception IOException if an error occurs
     * @exception EOFException if an error occurs
     */
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
    
    /**
     * The method <code>readDocumentMeta</code>
     *
     * @param istream a <code>VariableByteInput</code> value
     */
    public void readDocumentMeta(VariableByteInput istream) {
        // skip over already known document data
        try {
            istream.skip(1); //docId
            istream.readUTF(); //fileURI.toString()
            istream.skip(2 + 2); //uid, gid
            istream.skip(children * 2);

            metadata = new DocumentMetadata();
            metadata.read(broker, istream);
        } catch (IOException e) {
            LOG.error("IO error while reading document metadata for " + fileURI, e);
        }

    }

    /**
     * The method <code>compareTo</code>
     *
     * @param other an <code>Object</code> value
     * @return an <code>int</code> value
     */
    public final int compareTo(Object other) {
	final long otherId = ((DocumentImpl)other).docId;
	if (otherId == docId)
	    return Constants.EQUAL;
	else if (docId < otherId)
	    return Constants.INFERIOR;
	else
	    return Constants.SUPERIOR;
    } 

    /**
     * The method <code>getBroker</code>
     *
     * @return a <code>DBBroker</code> value
     */
    public DBBroker getBroker() {
        return broker;
    }
    
    /**
     * The method <code>setBroker</code>
     *
     * @param broker a <code>DBBroker</code> value
     */
    public void setBroker(DBBroker broker) {
        this.broker = broker;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeImpl#updateChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public StoredNode updateChild(Txn transaction, Node oldChild, Node newChild) throws DOMException {
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
            broker.endRemove(transaction);
            newNode.setNodeId(oldNode.getNodeId());
            broker.insertNodeAfter(null, previousNode, newNode);
            NodePath path = newNode.getPath();
            broker.indexNode(transaction, newNode, path);
            broker.endElement(newNode, path, null);
            broker.flush();
        } else {
            broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
            broker.endRemove(transaction);
            newNode.setNodeId(oldNode.getNodeId());
            broker.insertNodeAfter(transaction, previousNode, newNode);
        }
        return newNode;
    }

    /*
     * @see org.exist.dom.NodeImpl#insertBefore(org.w3c.dom.NodeList, org.w3c.dom.Node)
     */
    public void insertBefore(NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
    }
    /*
     * @see org.exist.dom.NodeImpl#insertAfter(org.w3c.dom.NodeList, org.w3c.dom.Node)
     */
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
    
    /**
     * The method <code>getFirstChildAddress</code>
     *
     * @return a <code>long</code> value
     */
    public long getFirstChildAddress() {
	if (children == 0)
	    return StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
	return childAddress[0];
    }
    	
    /**
     * The method <code>getChildNodes</code>
     *
     * @return a <code>NodeList</code> value
     */
    public NodeList getChildNodes() {
	NodeListImpl list = new NodeListImpl();		
	for (int i = 0; i < children; i++) {
            Node child = broker.objectWith(
					   new NodeProxy(this,
							 NodeId.DOCUMENT_NODE,
							 childAddress[i])
					   );
	    list.add(child);
	}
	return list;
    }
    
    /**
     * The method <code>getPreviousSibling</code>
     *
     * @param node a <code>StoredNode</code> value
     * @return a <code>Node</code> value
     */
    protected Node getPreviousSibling(StoredNode node) {
	NodeList cl = getChildNodes();		
	for (int i = 0; i < cl.getLength(); i++) {
            StoredNode next = (StoredNode) cl.item(i);
	    if (StorageAddress.equals(node.getInternalAddress(), next.getInternalAddress()))
		return i == 0 ? null : cl.item(i - 1);
	}
	return null;
    }

    /**
     * The method <code>getFollowingSibling</code>
     *
     * @param node a <code>StoredNode</code> value
     * @return a <code>Node</code> value
     */
    protected Node getFollowingSibling(StoredNode node) {
	NodeList cl = getChildNodes();		
	for (int i = 0; i < cl.getLength(); i++) {
            StoredNode next = (StoredNode) cl.item(i);
	    if (StorageAddress.equals(node.getInternalAddress(), next.getInternalAddress()))
		return i == children - 1 ? null : cl.item(i + 1);
	}
	return null;
    }     
    
    /**
     * The method <code>findElementsByTagName</code>
     *
     * @param root a <code>StoredNode</code> value
     * @param qname a <code>QName</code> value
     * @return a <code>NodeList</code> value
     */
    protected NodeList findElementsByTagName(StoredNode root, QName qname) {
        MutableDocumentSet docs = new DefaultDocumentSet();
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
    

    /**
     * The method <code>getDoctype</code>
     *
     * @return a <code>DocumentType</code> value
     */
    public DocumentType getDoctype() {
        return getMetadata().getDocType();
    }     
    
    /**
     * The method <code>setDocumentType</code>
     *
     * @param docType a <code>DocumentType</code> value
     */
    public void setDocumentType(DocumentType docType) {
        getMetadata().setDocType(docType);
    }    
    
    /**
     * The method <code>getOwnerDocument</code>
     *
     * @return a <code>Document</code> value
     */
    public Document getOwnerDocument() {        
        return this;
    }
    
    /**
     * The method <code>setOwnerDocument</code>
     *
     * @param doc a <code>Document</code> value
     */
    public void setOwnerDocument(Document doc) {
        if (doc != this)
            throw new IllegalArgumentException("Can't set owner document");
    }     
    
    /**
     * The method <code>getQName</code>
     *
     * @return a <code>QName</code> value
     */
    public QName getQName() {
        return QName.DOCUMENT_QNAME;
    }    

    /**
     * The method <code>getNodeType</code>
     *
     * @return a <code>short</code> value
     */
    public short getNodeType() {
        return Node.DOCUMENT_NODE;
    }

    /**
     * The method <code>getPreviousSibling</code>
     *
     * @return a <code>Node</code> value
     */
    public Node getPreviousSibling() {
        //Documents don't have siblings
        return null;
    }

    /**
     * The method <code>getNextSibling</code>
     *
     * @return a <code>Node</code> value
     */
    public Node getNextSibling() {
        //Documents don't have siblings
        return null;
    }    
    
    /**
     * The method <code>createAttribute</code>
     *
     * @param name a <code>String</code> value
     * @return an <code>Attr</code> value
     * @exception DOMException if an error occurs
     */
    public Attr createAttribute(String name) throws DOMException {
        AttrImpl attr = new AttrImpl(new QName(name, "", null));
        attr.setOwnerDocument(this);
        return attr;
    }

    /**
     * The method <code>createAttributeNS</code>
     *
     * @param namespaceURI a <code>String</code> value
     * @param qualifiedName a <code>String</code> value
     * @return an <code>Attr</code> value
     * @exception DOMException if an error occurs
     */
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
        AttrImpl attr = new AttrImpl(new QName(name, namespaceURI, prefix));
        attr.setOwnerDocument(this);
        return attr;
    }

    /**
     * The method <code>createElement</code>
     *
     * @param tagName a <code>String</code> value
     * @return an <code>Element</code> value
     * @exception DOMException if an error occurs
     */
    public Element createElement(String tagName) throws DOMException {
        ElementImpl element = new ElementImpl(new QName(tagName, "", null));
        element.setOwnerDocument(this);
        return element;
    }

    /**
     * The method <code>createElementNS</code>
     *
     * @param namespaceURI a <code>String</code> value
     * @param qualifiedName a <code>String</code> value
     * @return an <code>Element</code> value
     * @exception DOMException if an error occurs
     */
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

    /**
     * The method <code>createTextNode</code>
     *
     * @param data a <code>String</code> value
     * @return a <code>Text</code> value
     */
    public Text createTextNode(String data) {
        TextImpl text = new TextImpl(data);
        text.setOwnerDocument(this);
        return text;
    }
    
    /*
     *  W3C Document-Methods
     */

    /**
     * The method <code>getDocumentElement</code>
     *
     * @return an <code>Element</code> value
     */
    public Element getDocumentElement() {
        NodeList cl = getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            if (cl.item(i).getNodeType() == Node.ELEMENT_NODE)
                return (Element) cl.item(i);
        }
        return null;
    }

    /**
     * The method <code>getElementsByTagName</code>
     *
     * @param tagname a <code>String</code> value
     * @return a <code>NodeList</code> value
     */
    public NodeList getElementsByTagName(String tagname) {
        return getElementsByTagNameNS("", tagname);
    }

    /**
     * The method <code>getElementsByTagNameNS</code>
     *
     * @param namespaceURI a <code>String</code> value
     * @param localName a <code>String</code> value
     * @return a <code>NodeList</code> value
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        MutableDocumentSet docs = new DefaultDocumentSet();
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
    
    /**
     * The method <code>getChildCount</code>
     *
     * @return an <code>int</code> value
     */
    public int getChildCount() {
        return children;
    }     

    /**
     * The method <code>setChildCount</code>
     *
     * @param count an <code>int</code> value
     */
    public void setChildCount(int count) {
        children = count;
        if (children == 0)
            childAddress = null;
    }
    
    /**
     * The method <code>getEncoding</code>
     *
     * @return a <code>String</code> value
     */
    public String getEncoding() {
        //TODO : on demand result (e.g. from serializer's settings) ? -pb
        return "UTF-8";
    } 
    
    /**
     * The method <code>setEncoding</code>
     *
     * @param enc a <code>String</code> value
     */
    public void setEncoding(String enc) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setEncoding not implemented on class " + getClass().getName());
    } 
    
    /**
     * The method <code>getVersion</code>
     *
     * @return a <code>String</code> value
     */
    public String getVersion() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getVersion not implemented on class " + getClass().getName());
    }
    
    /**
     * The method <code>setVersion</code>
     *
     * @param version a <code>String</code> value
     */
    public void setVersion(String version) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setVersion not implemented on class " + getClass().getName());
    }     
    
    /**
     * The method <code>getStandalone</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean getStandalone() {
        //TODO : on demand result (e.g. from serializer's settings) ? -pb
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getStandalone not implemented on class " + getClass().getName());
    }    
    
    /**
     * The method <code>setStandalone</code>
     *
     * @param alone a <code>boolean</code> value
     */
    public void setStandalone(boolean alone) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setStandalone not implemented on class " + getClass().getName());
    }    
    
    /**
     * The method <code>createCDATASection</code>
     *
     * @param data a <code>String</code> value
     * @return a <code>CDATASection</code> value
     * @exception DOMException if an error occurs
     */
    public CDATASection createCDATASection(String data) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createCDATASection not implemented on class " + getClass().getName());        
    }

    /**
     * The method <code>createComment</code>
     *
     * @param data a <code>String</code> value
     * @return a <code>Comment</code> value
     */
    public Comment createComment(String data) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createComment not implemented on class " + getClass().getName());
    }

    /**
     * The method <code>createDocumentFragment</code>
     *
     * @return a <code>DocumentFragment</code> value
     * @exception DOMException if an error occurs
     */
    public DocumentFragment createDocumentFragment() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createDocumentFragment not implemented on class " + getClass().getName());
    }
    
    /**
     * The method <code>createEntityReference</code>
     *
     * @param name a <code>String</code> value
     * @return an <code>EntityReference</code> value
     * @exception DOMException if an error occurs
     */
    public EntityReference createEntityReference(String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createEntityReference not implemented on class " + getClass().getName());
    }

    /**
     * The method <code>createProcessingInstruction</code>
     *
     * @param target a <code>String</code> value
     * @param data a <code>String</code> value
     * @return a <code>ProcessingInstruction</code> value
     * @exception DOMException if an error occurs
     */
    public ProcessingInstruction createProcessingInstruction(String target, String data)
	throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "createProcessingInstruction not implemented on class " + getClass().getName());
    }     
    
    /**
     * The method <code>getElementById</code>
     *
     * @param elementId a <code>String</code> value
     * @return an <code>Element</code> value
     */
    public Element getElementById(String elementId) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getElementById not implemented on class " + getClass().getName());
    }    
    
    /**
     * The method <code>getImplementation</code>
     *
     * @return an <code>org.w3c.dom.DOMImplementation</code> value
     */
    public org.w3c.dom.DOMImplementation getImplementation() {
        return new StoredDOMImplementation();
    } 

    /**
     * The method <code>getStrictErrorChecking</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean getStrictErrorChecking() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getStrictErrorChecking not implemented on class " + getClass().getName());
    }
    
    /**
     * The method <code>adoptNode</code>
     *
     * @param node a <code>Node</code> value
     * @return a <code>Node</code> value
     * @exception DOMException if an error occurs
     */
    public Node adoptNode(Node node) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "adoptNode not implemented on class " + getClass().getName());
    }    

    /**
     * The method <code>importNode</code>
     *
     * @param importedNode a <code>Node</code> value
     * @param deep a <code>boolean</code> value
     * @return a <code>Node</code> value
     * @exception DOMException if an error occurs
     */
    public Node importNode(Node importedNode, boolean deep) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "importNode not implemented on class " + getClass().getName());
    }

    /**
     * The method <code>isSupported</code>
     *
     * @param type a <code>String</code> value
     * @param value a <code>String</code> value
     * @return a <code>boolean</code> value
     */
    public boolean isSupported(String type, String value) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isSupported not implemented on class " + getClass().getName());
    }    
    
    /**
     * The method <code>setStrictErrorChecking</code>
     *
     * @param strict a <code>boolean</code> value
     */
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
        try {
            return getURI() + "";
        } catch (Exception e) {
            System.out.println("dom/DocumentImpl::getBaseURI() 2 exception catched: ");
        }
        return XmldbURI.ROOT_COLLECTION_URI + "";
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
    
    /**
     * The method <code>toString</code>
     *
     * @return a <code>String</code> value
     */
    public String toString() {
	return getURI() + " - <" + 
	    ( getDocumentElement() != null ? getDocumentElement().getNodeName() : null ) + ">";	
    }
}
