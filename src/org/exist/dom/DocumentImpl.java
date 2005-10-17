/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.storage.StorageAddress;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.MultiReadReentrantLock;
import org.exist.storage.txn.Txn;
import org.exist.util.SyntaxException;
import org.exist.xquery.DescendantSelector;
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
 *  Represents a persistent document object in the database.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class DocumentImpl extends NodeImpl implements Document, Comparable {

	public final static byte XML_FILE = 0;
	public final static byte BINARY_FILE = 1;
	
	public final static byte DOCUMENT_NODE_SIGNATURE = 0x0F;
	
    public final static byte HAS_DOCTYPE = 1;
    
	private transient NodeIndexListener listener = NullNodeIndexListener.INSTANCE;

	protected transient DBBroker broker = null;

	// number of child nodes
	protected int children = 0;

	protected long[] childList = null;

	// the collection this document belongs to
	protected transient Collection collection = null;

	// the document's id
	protected int docId = -1;

	// document's document type
	protected transient DocumentType docType = null;

	// the document's file name
	protected String fileName = null;

    protected String mimeType = "text/xml";
    
	// the creation time of this document
	protected long created = 0;
	
	// time of the last modification
	protected long lastModified = 0;
	
	// the number of data pages occupied by this document
	protected int pageCount = 0;
	
	protected transient int splitCount = 0;
	
	// number of levels in this DOM tree
	protected int maxDepth = 0;

	// if set to > -1, the document needs to be partially reindexed
	// - beginning at the tree-level defined by reindex
	protected transient int reindex = -1;

	protected Permission permissions = new Permission(0754);

	// arity of the tree at every level
	protected int treeLevelOrder[] = new int[15];

	protected transient long treeLevelStartPoints[] = new long[15];
	
	//private transient User lockOwner = null;
	private transient int lockOwnerId = 0;
	
	private transient Lock updateLock = null;
	
	public DocumentImpl(DBBroker broker, Collection collection) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = broker;
		this.collection = collection;
		this.ownerDocument = this;
		treeLevelOrder[0] = 1;
	}

	public DocumentImpl(DBBroker broker) {
		this(broker, null, null);
	}

	public DocumentImpl(DBBroker broker, String fileName) {
		this(broker, null, null);
		this.fileName = fileName;
	}

	public DocumentImpl(DBBroker broker, String fileName, Collection collection) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = broker;
		this.fileName = fileName;
		this.ownerDocument = this;
		this.collection = collection;
		treeLevelOrder[0] = 1;
	}

	public DocumentImpl(DocumentImpl old) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = old.broker;
		this.fileName = old.fileName;
		this.ownerDocument = this;
		this.collection = old.collection;
		if(old.collection == null)
			throw new RuntimeException("Collection == null");
		this.children = old.children;
		this.maxDepth = old.maxDepth;
		this.docId = old.docId;
		this.childList = old.childList;
		this.docType = old.docType;
		this.permissions = old.permissions;
		treeLevelOrder = new int[old.treeLevelOrder.length];
		for (int i = 0; i < treeLevelOrder.length; i++)
			treeLevelOrder[i] = old.treeLevelOrder[i];
		treeLevelStartPoints = new long[old.treeLevelStartPoints.length];
		for (int i = 0; i < treeLevelStartPoints.length; i++)
			treeLevelStartPoints[i] = old.treeLevelStartPoints[i];
	}

	/**
	 * Copy the relevant internal fields from the specified document object.
	 * This is called by {@link Collection} when replacing a document.
	 * 
	 * @param other
	 */
	public void copyOf(DocumentImpl other) {
	    maxDepth = other.maxDepth;
	    childList = null;
	    children = 0;
	    docType = other.getDoctype();
	    treeLevelOrder = other.treeLevelOrder;
	    treeLevelStartPoints = other.treeLevelStartPoints;
	    internalAddress = -1;
	    lastModified = other.getLastModified();
	    // reset pageCount: will be updated during storage
	    pageCount = 0;
	}
	
	public void copyChildren(DocumentImpl other) {
		childList = other.childList;
		children = other.children;
	}
	
	/**
	 * Returns the type of this resource, either  {@link #XML_FILE} or 
	 * {@link #BINARY_FILE}.
	 * 
	 * @return
	 */
	public byte getResourceType() {
		return XML_FILE;
	}
    
    public void setMimeType(String type) {
        if (type != null)
            this.mimeType = type;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
	/**
	 * Returns true if the document is currently locked for
	 * write.
	 * 
	 * @return
	 */
	public boolean isLockedForWrite() {
		return getUpdateLock().isLockedForWrite();
	}
	
	public void setCollection(Collection parent) {
	    this.collection = parent;
	}
	
	protected static NodeImpl createNode(long gid, short type) {
		NodeImpl node;
		switch (type) {
			case Node.TEXT_NODE :
				node = new TextImpl(gid);
				break;
			case Node.ELEMENT_NODE :
				node = new ElementImpl(gid);
				break;
			case Node.ATTRIBUTE_NODE :
				node = new AttrImpl(gid);
				break;
			default :
				LOG.debug("unknown node type");
				node = null;
		}
		return node;
	}

	public Node adoptNode(Node node) throws DOMException {
		return node;
	}

	public void appendChild(NodeImpl child) throws DOMException {
		++children;
		resizeChildList();
		childList[children - 1] = child.internalAddress;
	}
	
	public void calculateTreeLevelStartPoints() throws EXistException {
		calculateTreeLevelStartPoints(true);
	}
	
	public void calculateTreeLevelStartPoints(boolean failOnError) throws EXistException {
		treeLevelStartPoints = new long[maxDepth + 1];
		// we know the start point of the root element (which is always 1)
		// and the start point of the first non-root node (children + 1)
		treeLevelStartPoints[0] = 1;
		treeLevelStartPoints[1] = 2;
		for (int i = 1; i < maxDepth; i++) {
			treeLevelStartPoints[i + 1] =
				(treeLevelStartPoints[i] - treeLevelStartPoints[i - 1]) * treeLevelOrder[i]
					+ treeLevelStartPoints[i];
			if(treeLevelStartPoints[i + 1] > 0x6fffffffffffffffL ||
				treeLevelStartPoints[i + 1] < 0) {
				throw new EXistException("The document is too complex/irregularily structured " +
					"to be mapped into eXist's numbering scheme. Number of children per level of the " +
					"tree: " + printTreeLevelOrder());
			}
		}
	}

	public String printTreeLevelOrder() {
		StringBuffer buf = new StringBuffer();
		buf.append("[ ");
		for(int i = 0; i < maxDepth; i++) {
			if(i > 0)
				buf.append(", ");
			buf.append(treeLevelOrder[i]);
		}
		buf.append(" ]");
		return buf.toString();
	}
	
	public final int compareTo(Object other) {
		final long otherId = ((DocumentImpl)other).docId;
		if (otherId == docId)
			return 0;
		else if (docId < otherId)
			return -1;
		else
			return 1;
	}

	public Attr createAttribute(String name) throws DOMException {
		AttrImpl attr = new AttrImpl(new QName(name, "", null), null);
		attr.setOwnerDocument(this);
		return attr;
	}

	public Attr createAttributeNS(String namespaceURI, String qualifiedName) throws DOMException {
		int p = qualifiedName.indexOf(':');
		String name = p > -1 ? qualifiedName.substring(p) : qualifiedName;
		String prefix = p > -1 ? qualifiedName.substring(0, p) : null; 
		AttrImpl attr = new AttrImpl(new QName(name, namespaceURI, prefix), null);
		attr.setOwnerDocument(this);
		return attr;
	}

	public CDATASection createCDATASection(String data) throws DOMException {
		return null;
	}

	public Comment createComment(String data) {
		return null;
	}

	public DocumentFragment createDocumentFragment() throws DOMException {
		return null;
	}

	public Element createElement(String tagName) throws DOMException {
		ElementImpl element = new ElementImpl(new QName(tagName, "", null));
		element.setOwnerDocument(this);
		return element;
	}

	public Element createElementNS(String namespaceURI, String qualifiedName) throws DOMException {
		int p = qualifiedName.indexOf(':');
				String name = p > -1 ? qualifiedName.substring(p) : qualifiedName;
				String prefix = p > -1 ? qualifiedName.substring(0, p) : null;
		ElementImpl element = new ElementImpl(new QName(name, namespaceURI, prefix));
		element.setOwnerDocument(this);
		return element;
	}

	public EntityReference createEntityReference(String name) throws DOMException {
		return null;
	}

	public ProcessingInstruction createProcessingInstruction(String target, String data)
		throws DOMException {
		return null;
	}

	public Text createTextNode(String data) {
		TextImpl text = new TextImpl(data);
		text.setOwnerDocument(this);
		return text;
	}

	protected NodeList findElementsByTagName(NodeImpl root, QName qname) {
		DocumentSet docs = new DocumentSet();
		docs.add(this);
		NodeProxy p = new NodeProxy(root.ownerDocument, root.gid, root.internalAddress);
		NodeSelector selector = new DescendantSelector(p, false);
		return (NodeSet) broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, docs, qname, selector);
	}

	public int getChildCount() {
		return children;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		if(children == 0)
		    return null;
		long address = childList[0];
		return broker.objectWith(new NodeProxy(this,
				// 1,
				NodeProxy.DOCUMENT_ELEMENT_GID, address));
	}
	
	public long getFirstChildAddress() {
		if(children == 0)
			return -1;
		return childList[0];
	}
	
	public NodeList getChildNodes() {
		NodeListImpl list = new NodeListImpl();
		Node child;
		for (int i = 0; i < children; i++) {
			child = broker.objectWith(new NodeProxy(this,
					// 1,
					NodeProxy.DOCUMENT_ELEMENT_GID, childList[i]));
			list.add(child);
		}
		return list;
	}
    
	protected Node getPreviousSibling(NodeImpl node) {
		NodeList cl = getChildNodes();
		NodeImpl next;
		for (int i = 0; i < cl.getLength(); i++) {
			next = (NodeImpl) cl.item(i);
			if (StorageAddress.equals(node.internalAddress, next.internalAddress))
				return i == 0 ? null : cl.item(i - 1);
		}
		return null;
	}

	protected Node getFollowingSibling(NodeImpl node) {
		NodeList cl = getChildNodes();
		NodeImpl next;
		for (int i = 0; i < cl.getLength(); i++) {
			next = (NodeImpl) cl.item(i);
			if (StorageAddress.equals(node.internalAddress, next.internalAddress))
				return i == children - 1 ? null : cl.item(i + 1);
		}
		return null;
	}
	
	public Collection getCollection() {
		return collection;
	}

	public int getDocId() {
		return docId;
	}

	public DocumentType getDoctype() {
		return docType;
	}

	/*
	 *  W3C Document-Methods
	 */

	public Element getDocumentElement() {
		NodeList cl = getChildNodes();
		for (int i = 0; i < cl.getLength(); i++)
			if (cl.item(i).getNodeType() == Node.ELEMENT_NODE)
				return (Element) cl.item(i);
		return null;
	}

	public Element getElementById(String elementId) {
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

	public String getEncoding() {
		return "UTF-8";
	}

	public String getFileName() {
		//checkAvail();
		return fileName;
	}

	public String getName() {
		return collection.getName() + '/' + fileName;
	}

	public org.w3c.dom.DOMImplementation getImplementation() {
		return null;
	}

	public long getLevelStartPoint(int level) {
		if (level > maxDepth || level < 0)
			return -1;
		return treeLevelStartPoints[level];
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	public Node getNode(long gid) {
		if (gid == 1)
			return getDocumentElement();
		return broker.objectWith(this, gid);
	}

	public Node getNode(NodeProxy p) {
        if(p.getGID() < 0)
            return getDocumentElement();
		return broker.objectWith(p);
	}

	public Permission getPermissions() {
		return permissions;
	}

	public NodeList getRange(long first, long last) {
		return broker.getRange(this, first, last);
	}

	public boolean getStandalone() {
		return true;
	}

	public boolean getStrictErrorChecking() {
		return false;
	}

	public SymbolTable getSymbols() {
		return broker.getSymbols();
	}

	public int getTreeLevel(long gid) {
		for (int i = 0; i < maxDepth; i++) {
			if ((gid >= treeLevelStartPoints[i])
				&& (i + 1 == maxDepth || gid < treeLevelStartPoints[i + 1]))
				return i;
		}
		return -1;
	}

	public int getTreeLevelOrder(int level) {
		if (level > maxDepth) {
			LOG.fatal("tree level " + level + " does not exist");
			return -1;
		}
		return treeLevelOrder[level];
	}

	public int getTreeLevelOrder(long gid) {
		int order = 0;
		for (int i = 0; i < maxDepth; i++)
			if (gid >= treeLevelStartPoints[i] && gid < treeLevelStartPoints[i + 1]) {
				order = treeLevelOrder[i];
				break;
			}
		return order;
	}

	/* (non-Javadoc)
     * @see org.w3c.dom.Node#getParentNode()
     */
	public Node getParentNode() {
		return null;
	}
    
	public String getVersion() {
		return "";
	}

	public Node importNode(Node importedNode, boolean deep) throws DOMException {
		return null;
	}

	public boolean isSupported(String type, String value) {
		return false;
	}

	public void setBroker(DBBroker broker) {
		this.broker = broker;
	}

	public void setChildCount(int count) {
		children = count;
		if (children == 0)
			childList = null;
	}

	public void setDocId(int docId) {
		this.docId = docId;
	}

	public void setDocumentType(DocumentType docType) {
		this.docType = docType;
	}

	public void setEncoding(String enc) {
		// allways  "UTF-8"  !?
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setMaxDepth(int depth) {
		maxDepth = depth;
		if (treeLevelOrder.length <= maxDepth) {
			int temp[] = new int[maxDepth + 1];
			System.arraycopy(treeLevelOrder, 0, temp, 0, treeLevelOrder.length);
			temp[maxDepth] = 0;
			treeLevelOrder = temp;
		}
	}

	public void incMaxDepth() {
		++maxDepth;
		if (treeLevelOrder.length < maxDepth) {
			int temp[] = new int[maxDepth];
			System.arraycopy(treeLevelOrder, 0, temp, 0, maxDepth - 1);
			treeLevelOrder = temp;
			treeLevelOrder[maxDepth - 1] = 0;
		}
	}

	public void setUserLock(User user) {
		lockOwnerId = (user == null ? 0 : user.getUID());
	}
	
	public User getUserLock() {
		if(lockOwnerId < 1)
			return null;
		final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
		return secman.getUser(lockOwnerId);
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

	public void setStandalone(boolean alone) {
	}

	public void setStrictErrorChecking(boolean strict) {
	}

	public void setTreeLevelOrder(int level, int order) {
		treeLevelOrder[level] = order;
	}

	public void setVersion(String version) {
	}

	public int reindexRequired() {
		return reindex;
	}

    public void write(VariableByteOutputStream ostream) throws IOException {
		try {
            ostream.writeInt(docId);
            ostream.writeUTF(fileName);
            ostream.writeInt(StorageAddress.pageFromPointer(internalAddress));
            ostream.writeShort(StorageAddress.tidFromPointer(internalAddress));
            SecurityManager secman = broker.getBrokerPool().getSecurityManager();
            if (secman == null) {
                ostream.writeInt(1);
                ostream.writeInt(1);
            } else {
                User user = secman.getUser(permissions.getOwner());
                Group group = secman.getGroup(permissions.getOwnerGroup());
                ostream.writeInt(user.getUID());
                ostream.writeInt(group.getId());
            }
            ostream.writeInt(permissions.getPermissions());
            if(lockOwnerId > 0)
                ostream.writeInt(lockOwnerId);
            else
                ostream.writeInt(0);
            
            ostream.writeInt(maxDepth);
            for (int i = 0; i < maxDepth; i++) {
                //System.out.println("k[" + i + "] = " + treeLevelOrder[i]);
                ostream.writeInt(treeLevelOrder[i]);
            }
            ostream.writeInt(children);
			if(children > 0) {
			    for(int i = 0; i < children; i++) {
					ostream.writeInt(StorageAddress.pageFromPointer(childList[i]));
					ostream.writeShort(StorageAddress.tidFromPointer(childList[i]));
			    }
			}
            if (docType != null) {
                ostream.writeByte(HAS_DOCTYPE);
                ((DocumentTypeImpl) docType).write(ostream);
            } else
                ostream.writeByte((byte) 0);
            
			ostream.writeLong(created);
			ostream.writeLong(lastModified);
            ostream.writeUTF(mimeType);
			ostream.writeInt(pageCount);
		} catch (IOException e) {
			LOG.warn("io error while writing document data", e);
		}
	}

    public void read(VariableByteInput istream) throws IOException, EOFException {
		try {
            docId = istream.readInt();
            fileName = istream.readUTF();
            internalAddress = StorageAddress.createPointer(istream.readInt(), istream.readShort());

            final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
            final int uid = istream.readInt();
            final int gid = istream.readInt();
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
            lockOwnerId = istream.readInt();
            
            maxDepth = istream.readInt();
            treeLevelOrder = new int[maxDepth + 1];
            for (int i = 0; i < maxDepth; i++) {
                treeLevelOrder[i] = istream.readInt();
            }
            children = istream.readInt();
			childList = new long[children];
			for (int i = 0; i < children; i++) { 
				childList[i] = StorageAddress.createPointer(istream.readInt(), istream.readShort());
			}
            
            if (istream.readByte() == HAS_DOCTYPE) { 
                docType = new DocumentTypeImpl();
			    ((DocumentTypeImpl) docType).read(istream);
            } else
                docType = null;
            
			created = istream.readLong();
			lastModified = istream.readLong();
            mimeType = istream.readUTF();
			if(istream.available() > 0)
			    pageCount = istream.readInt();
		} catch (IOException e) {
			LOG.warn("IO error while reading document data for document " + fileName, e);
			LOG.warn("Document address is " + StorageAddress.toString(getAddress()));
		}
        
        try {
            calculateTreeLevelStartPoints();
        } catch (EXistException e) {
        }
	}

	public void setReindexRequired(int level) {
		this.reindex = level;
	}

	public void setIndexListener(NodeIndexListener listener) {
		this.listener = listener;
	}

	public NodeIndexListener getIndexListener() {
		return listener;
	}

	public void clearIndexListener() {
		listener = NullNodeIndexListener.INSTANCE;
	}

	public void setAddress(long address) {
		this.internalAddress = address;
	}

	public long getAddress() {
		return internalAddress;
	}

    /* (non-Javadoc)
     * @see org.exist.dom.NodeImpl#setInternalAddress(long)
     */
    public void setInternalAddress(long address) {
    }

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeImpl#updateChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public void updateChild(Txn transaction, Node oldChild, Node newChild) throws DOMException {
		if (!(oldChild instanceof NodeImpl))
			throw new DOMException(
				DOMException.WRONG_DOCUMENT_ERR,
				"node does not belong to this document");
		NodeImpl old = (NodeImpl) oldChild;
		NodeImpl newNode = (NodeImpl) newChild;
		NodeImpl previous = (NodeImpl) old.getPreviousSibling();
		if (previous == null)
			previous = this;
		if (oldChild.getNodeType() == Node.ELEMENT_NODE) {
			// replace the document-element
			if (newChild.getNodeType() != Node.ELEMENT_NODE)
				throw new DOMException(
					DOMException.INVALID_MODIFICATION_ERR,
					"a node replacing the document root needs to be an element");
			broker.removeNode(transaction, old, old.getPath(), null);
			broker.endRemove();
			newNode.gid = old.gid;
			broker.insertAfter(null, previous, newNode);
			NodePath path = newNode.getPath();
			broker.index(transaction, newNode, path);
			broker.endElement(newNode, path, null);
			broker.flush();
		} else {
			broker.removeNode(transaction, old, old.getPath(), null);
			broker.endRemove();
			newNode.gid = 0;
			broker.insertAfter(transaction, previous, newNode);
		}
	}

	/*
	 * @see org.exist.dom.NodeImpl#insertBefore(org.w3c.dom.NodeList, org.w3c.dom.Node)
	 */
	public void insertBefore(NodeList nodes, Node refChild) throws DOMException {
		if (!(refChild instanceof NodeImpl))
			throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
		NodeImpl ref = (NodeImpl) refChild;
		long next, last = -1;
		int idx = -1;
		for(int i = children - 1; i > -1; i--) {
		    next = childList[i];
			if (StorageAddress.equals(ref.internalAddress, next)) {
				idx = i - 1;
				break;
			}
		}
		if (idx < 0)
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "reference node not found");
		last = childList[idx];
		NodeImpl prev = (NodeImpl) broker.objectWith(
				new NodeProxy(this, NodeProxy.UNKNOWN_NODE_GID, last));
		for (int i = 0; i < nodes.getLength(); i++) {
			prev = (NodeImpl) appendChild(null, prev, nodes.item(i));
			++children;
			resizeChildList();
			childList[++idx] = prev.internalAddress;
		}
		broker.storeDocument(null, this);
	}

	public void insertAfter(NodeList nodes, Node refChild) throws DOMException {
		if (!(refChild instanceof NodeImpl))
			throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
		NodeImpl ref = (NodeImpl) refChild;
		long next, last = -1;
		int idx = -1;
		for(int i = 0; i < children; i++) {
			next = childList[i];
			if (StorageAddress.equals(ref.internalAddress, next)) {
				last = next;
				idx = i + 1;
				break;
			}
		}
		if (last < 0)
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "reference node not found");
		NodeImpl prev = getLastNode( (NodeImpl) broker.objectWith(
				new NodeProxy(this, NodeProxy.UNKNOWN_NODE_GID, last)) );
		for (int i = 0; i < nodes.getLength(); i++) {
			prev = (NodeImpl) appendChild(null, prev, nodes.item(i));
			++children;
			resizeChildList();
			childList[idx] = prev.internalAddress;
		}
		broker.storeDocument(null, this);
	}

	private Node appendChild(Txn transaction, NodeImpl last, Node child) throws DOMException {
		// String ns, prefix;
		// Attr attr;
		switch (child.getNodeType()) {
			case Node.PROCESSING_INSTRUCTION_NODE :
				final ProcessingInstructionImpl pi = new ProcessingInstructionImpl(0);
				pi.setTarget(((ProcessingInstruction) child).getTarget());
				pi.setData(((ProcessingInstruction) child).getData());
				pi.setOwnerDocument(this);
				//insert the node
				broker.insertAfter(transaction, last, pi);
				return pi;
			case Node.COMMENT_NODE :
				final CommentImpl comment = new CommentImpl(0, ((Comment) child).getData());
				comment.setOwnerDocument(this);
				broker.insertAfter(transaction, last, comment);
				return comment;
			default :
				throw new DOMException(
					DOMException.INVALID_MODIFICATION_ERR,
					"you cannot append a node of this type");
		}
	}

	/**
	 * @return
	 */
	public long getCreated() {
		return created;
	}

	/**
	 * @return
	 */
	public long getLastModified() {
		return lastModified;
	}

	/**
	 * @param l
	 */
	public void setCreated(long l) {
		created = l;
		if(lastModified == 0)
			lastModified = l;
	}

	/**
	 * @param l
	 */
	public void setLastModified(long l) {
		lastModified = l;
	}
	
	/**
	 * Returns the update lock associated with this
	 * resource.
	 * 
	 * @return
	 */
	public final synchronized Lock getUpdateLock() {
	    if(updateLock == null)
	        updateLock = new MultiReadReentrantLock();
	    return updateLock;
	}
	
	public void incPageCount() {
	    ++pageCount;
	}
	
	public void decPageCount() {
	    --pageCount;
	}
	
	/**
	 * Returns the estimated size of the data in this document.
	 * 
	 * As an estimation, the number of pages occupied by the document
	 * is multiplied with the current page size.
	 * 
	 * @return
	 */
	public int getContentLength() {
	    return pageCount * broker.getPageSize();
	}
	
	/**
	 * Returns the number of pages currently occupied by this document.
	 * 
	 * @return
	 */
	public int getPageCount() {
	    return pageCount;
	}
	
	/**
	 * Set the number of pages currently occupied by this document.
	 * @param count
	 */
	public void setPageCount(int count) {
	    pageCount = count;
	}
	
	private void resizeChildList() {
	    long[] newChildList = new long[children];
	    if(childList != null)
	        System.arraycopy(childList, 0, newChildList, 0, childList.length);
	    childList = newChildList;
	}
	
	/**
	 * Increase the page split count of this document. The number
	 * of pages that have been split during inserts serves as an
	 * indicator for the 
	 *
	 */
	public void incSplitCount() {
		splitCount++;
	}
	
	public int getSplitCount() {
		return splitCount;
	}
	
	public void setSplitCount(int count) {
		splitCount = count;
	}
	
	public void triggerDefrag() {
		splitCount = broker.getFragmentationLimit();
	}

	/** ? @see org.w3c.dom.Document#getInputEncoding()
	 */
	public String getInputEncoding() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#getXmlEncoding()
	 */
	public String getXmlEncoding() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#getXmlStandalone()
	 */
	public boolean getXmlStandalone() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Document#setXmlStandalone(boolean)
	 */
	public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getXmlVersion()
	 */
	public String getXmlVersion() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#setXmlVersion(java.lang.String)
	 */
	public void setXmlVersion(String xmlVersion) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getDocumentURI()
	 */
	public String getDocumentURI() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
	 */
	public void setDocumentURI(String documentURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getDomConfig()
	 */
	public DOMConfiguration getDomConfig() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#normalizeDocument()
	 */
	public void normalizeDocument() {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#renameNode(org.w3c.dom.Node, java.lang.String, java.lang.String)
	 */
	public Node renameNode(Node n, String namespaceURI, String qualifiedName) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
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
		return 0;
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
