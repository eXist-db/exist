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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.security.Group;
import org.exist.security.Permission;
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
	
	//public final static byte DOCUMENT_NODE_SIGNATURE = 0x0F;

	private transient DBBroker broker = null;

	/** number of child nodes */
	private int children = 0;

	private long[] childList = null;

	/** the collection this document belongs to */
	private transient Collection collection = null;

	/** the document's id */
	private int docId = UNKNOWN_DOCUMENT_ID;

	/** the document's file name */
	private String fileName = null;
    
    //TODO : make private
    protected Permission permissions = new Permission(Permission.DEFAULT_PERM);
    
    private transient Lock updateLock = null;
    
    private DocumentMetadata metadata = null;
    
    private transient long metadataLocation = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;    

    /** number of levels in this DOM tree */
    private int maxDepth = 0;
    
    /** arity of the tree at every level */    
    private int treeLevelOrder[] = new int[15];

    /** Id's of first node at each depth level */
    private transient long treeLevelStartPoints[] = new long[15];
    
    public DocumentImpl(DBBroker broker) {
        this(broker, null, null);
    }    
	
    public DocumentImpl(DBBroker broker, Collection collection) {
        this(broker, collection, null);
    }

    public DocumentImpl(DBBroker broker, String fileName) {
        this(broker, null, fileName);       
    }

	public DocumentImpl(DBBroker broker, Collection collection, String fileName) {
		this.broker = broker;
        this.collection = collection;
		this.fileName = fileName;		
		treeLevelOrder[0] = 1;
	}

//	public DocumentImpl(DocumentImpl old) {
//		this.broker = old.broker;
//		this.fileName = old.fileName;
//		this.collection = old.collection;
//		if(old.collection == null)
//			throw new RuntimeException("Collection == null");
//		this.children = old.children;
//		this.maxDepth = old.maxDepth;
//		this.docId = old.docId;
//		this.childList = old.childList;
//		this.docType = old.docType;
//		this.permissions = old.permissions;
//		treeLevelOrder = new int[old.treeLevelOrder.length];
//		for (int i = 0; i < treeLevelOrder.length; i++)
//			treeLevelOrder[i] = old.treeLevelOrder[i];
//		treeLevelStartPoints = new long[old.treeLevelStartPoints.length];
//		for (int i = 0; i < treeLevelStartPoints.length; i++)
//			treeLevelStartPoints[i] = old.treeLevelStartPoints[i];
//	}
    
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
     * @return
     */
    public byte getResourceType() {
        return XML_FILE;
    }

    public String getFileName() {
        //checkAvail();
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    } 
    
    public String getName() {
        //TODO : use dedicated function in XmldbURI
        return collection.getName() + "/" + fileName;
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
	
	public void setMetadataLocation(long pointer) {
		this.metadataLocation = pointer;
	}
	
	public long getMetadataLocation() {
		return metadataLocation;
	}
	
    /************************************************
     * 
     * Persistent node methods
     *
     ************************************************/ 

    public int getMaxDepth() {
        return maxDepth;
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
	    treeLevelOrder = other.treeLevelOrder;
	    treeLevelStartPoints = other.treeLevelStartPoints;
	    if (metadata == null)
	    	metadata = new DocumentMetadata();
	    metadata.setLastModified(other.getMetadata().getLastModified());
	    // reset pageCount: will be updated during storage
	    metadata.setPageCount(0);
	}
	
	public void copyChildren(DocumentImpl other) {
		childList = other.childList;
		children = other.children;
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
	 * @return
	 */
	public int getContentLength() {
	    return getMetadata().getPageCount() * broker.getPageSize();
	}
    
	public void triggerDefrag() {
		getMetadata().setSplitCount(broker.getFragmentationLimit());
	}

    public SymbolTable getSymbols() {
        return broker.getSymbols();
    }
    
    public Node getNode(NodeId nodeId) {
    	if (nodeId.getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(this, nodeId);
    }

    public Node getNode(long gid) {
    	throw new RuntimeException("Method is deprecated");
    }
    
    public Node getNode(NodeProxy p) {
        if(p.getNodeId().getTreeLevel() == 1)
            return getDocumentElement();
        return broker.objectWith(p);
    }
    
    private void resizeChildList() {
        long[] newChildList = new long[children];
        if(childList != null)
            System.arraycopy(childList, 0, newChildList, 0, childList.length);
        childList = newChildList;
    }    
    
	public void appendChild(StoredNode child) throws DOMException {
		++children;
		resizeChildList();
		childList[children - 1] = child.getInternalAddress();
	}
    
	public void write(VariableByteOutputStream ostream) throws IOException {
		try {
            ostream.writeInt(docId);
            ostream.writeUTF(fileName);
            final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
            if (secman == null) {
                //TODO : explain those 2 values -pb
                ostream.writeInt(1);
                ostream.writeInt(1);
            } else {
                User user = secman.getUser(permissions.getOwner());
                Group group = secman.getGroup(permissions.getOwnerGroup());
                ostream.writeInt(user.getUID());
                ostream.writeInt(group.getId());
            }
            ostream.writeInt(permissions.getPermissions());            
            ostream.writeInt(maxDepth);
            for (int i = 0; i < maxDepth; i++) {                
                ostream.writeInt(treeLevelOrder[i]);
            }
            ostream.writeInt(children);
            if (children > 0) {
			    for(int i = 0; i < children; i++) {
					ostream.writeInt(StorageAddress.pageFromPointer(childList[i]));
					ostream.writeShort(StorageAddress.tidFromPointer(childList[i]));
			    }
			}            
            StorageAddress.write(metadataLocation, ostream);
		} catch (IOException e) {
			LOG.warn("io error while writing document data", e);
		}
	}

	public void read(VariableByteInput istream) throws IOException, EOFException {
		try {
            docId = istream.readInt();
            fileName = istream.readUTF();
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
            maxDepth = istream.readInt();
            treeLevelOrder = new int[maxDepth + 1];
            for (int i = 0; i < maxDepth; i++) {
                treeLevelOrder[i] = istream.readInt();
            }
            //Should be > 0 ;-)
            children = istream.readInt();            
			childList = new long[children];
			for (int i = 0; i < children; i++) { 
				childList[i] = StorageAddress.createPointer(istream.readInt(), istream.readShort());
			}
            metadataLocation = StorageAddress.read(istream);
		} catch (IOException e) {
            LOG.error("IO error while reading document data for document " + fileName, e);
		}        
        try {
            calculateTreeLevelStartPoints();
        } catch (EXistException e) {
            LOG.error(e.getMessage(), e);
            //TODO :rethrow ? -pb
        }
	}
    
    public int getTreeLevel(long gid) {
        for (int i = 0; i < maxDepth; i++) {
            if (gid < treeLevelStartPoints[i])
                continue;
            if (i + 1 == maxDepth || gid < treeLevelStartPoints[i + 1])
                return i;
        }
        return -1;
    }    
    
    public long getLevelStartPoint(int level) {
       //TODO : check *before* calling ? -pb
       if (level < 0) {           
           return -1;
       }
       if (level > maxDepth) {           
           LOG.error("tree level " + level + " does not exist (maximum " + maxDepth + ")");
           //throw an exception ? -pb
           return -1;
       }       
        return treeLevelStartPoints[level];
    }

    public int getTreeLevelOrder(int level) {        
        if (level > maxDepth) {
            //throw an exception ? -pb
            LOG.error("tree level " + level + " does not exist (maximum " + maxDepth + ")");
            return -1;
        }
        return treeLevelOrder[level];
    }

    public int getTreeLevelOrder(long gid) {
        int order = 0;
        for (int i = 0; i < maxDepth; i++) {
            if (gid < treeLevelStartPoints[i])
                continue;
            if (gid < treeLevelStartPoints[i + 1]) {
                order = treeLevelOrder[i];
                break;
            }
        }
        return order;
    } 
    
    public void setTreeLevelOrder(int level, int order) {
        //TODO : range check ? -pb
        treeLevelOrder[level] = order;
    }    

    public void setMaxDepth(int depth) {        
        maxDepth = depth;
        //Expand the array if maxDepth is now too big
        if (treeLevelOrder.length <= maxDepth) {
            int temp[] = new int[maxDepth + 1];
            System.arraycopy(treeLevelOrder, 0, temp, 0, treeLevelOrder.length);
            temp[maxDepth] = 0;
            treeLevelOrder = temp;
        }
    }

    public void incMaxDepth() {
        ++maxDepth; 
        //Expand the array if maxDepth is now too big
        if (treeLevelOrder.length < maxDepth) {
            int temp[] = new int[maxDepth];
            System.arraycopy(treeLevelOrder, 0, temp, 0, maxDepth - 1);            
            treeLevelOrder = temp;
            treeLevelOrder[maxDepth - 1] = 0;
        }
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
			if(treeLevelStartPoints[i + 1] < 0 ||
                    treeLevelStartPoints[i + 1] > 0x6fffffffffffffffL) {
				treeLevelStartPoints[i + 1] = 1;
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

    /*
    public long getGID() {
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
        return 0;
    }

    public void setGID(long gid) {
    }

    public long getInternalAddress() {
        return StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
    }

    public void setInternalAddress(long address) {
    }    

    public long getParentGID() {
        return StoredNode.NODE_IMPL_UNKNOWN_GID;
    }
    */

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
            newNode.setGID(oldNode.getGID());
            broker.insertNodeAfter(null, previousNode, newNode);
            NodePath path = newNode.getPath();
            broker.indexNode(transaction, newNode, path);
            broker.endElement(newNode, path, null);
            broker.flush();
        } else {
            broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
            broker.endRemove();
            //TOUNDERSTAND : what are the semantics of this 0 ? -pb            
            newNode.setGID(0);
            broker.insertNodeAfter(transaction, previousNode, newNode);
        }
    }

    /*
     * @see org.exist.dom.NodeImpl#insertBefore(org.w3c.dom.NodeList, org.w3c.dom.Node)
     */
    public void insertBefore(NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
        /*
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode ref = (StoredNode) refChild;
        long next, last = -1;
        int idx = -1;
        for(int i = children - 1; i >= 0; i--) {
            next = childList[i];
            if (StorageAddress.equals(ref.internalAddress, next)) {
                idx = i - 1;
                break;
            }
        }
        if (idx < 0)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "reference node not found");
        last = childList[idx];
        StoredNode prev = (StoredNode) broker.objectWith(
                new NodeProxy(this, NodeProxy.UNKNOWN_NODE_GID, last));
        for (int i = 0; i < nodes.getLength(); i++) {
            prev = (StoredNode) newChild(null, prev, nodes.item(i));
            ++children;
            resizeChildList();
            childList[++idx] = prev.internalAddress;
        }
        broker.storeDocument(null, this);
        */
    }

    public void insertAfter(NodeList nodes, Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented");
        /*
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode ref = (StoredNode) refChild;
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
        StoredNode prev = getLastNode( (StoredNode) broker.objectWith(
                new NodeProxy(this, NodeProxy.UNKNOWN_NODE_GID, last)) );
        for (int i = 0; i < nodes.getLength(); i++) {
            prev = (StoredNode) newChild(null, prev, nodes.item(i));
            ++children;
            resizeChildList();
            childList[idx] = prev.internalAddress;
        }
        broker.storeDocument(null, this);
        */
    }

    // Never used locally !
//    private Node appendChild(Txn transaction, StoredNode last, Node child) throws DOMException {
//        switch (child.getNodeType()) {
//            case Node.PROCESSING_INSTRUCTION_NODE :
//                final ProcessingInstructionImpl pi = new ProcessingInstructionImpl(0);
//                pi.setTarget(((ProcessingInstruction) child).getTarget());
//                pi.setData(((ProcessingInstruction) child).getData());
//                pi.setOwnerDocument(this);              
//                broker.insertNodeAfter(transaction, last, pi);
//                return pi;
//            case Node.COMMENT_NODE :
//                final CommentImpl comment = new CommentImpl(0, ((Comment) child).getData());
//                comment.setOwnerDocument(this);
//                broker.insertNodeAfter(transaction, last, comment);
//                return comment;
//            default :
//                throw new DOMException(
//                    DOMException.INVALID_MODIFICATION_ERR,
//                    "you cannot append a node of this type");
//        }
//    }    

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		if (children == 0)
		    return null;
		long address = childList[0];
		return broker.objectWith(
            new NodeProxy(this, NodeProxy.DOCUMENT_ELEMENT_GID, address)
        );
	}	
    
	public long getFirstChildAddress() {
		if (children == 0)
			return NodeProxy.UNKNOWN_NODE_ADDRESS;
		return childList[0];
	}
    	
	public NodeList getChildNodes() {
		NodeListImpl list = new NodeListImpl();		
		for (int i = 0; i < children; i++) {
            Node child = broker.objectWith(
			        new NodeProxy(this,	NodeProxy.DOCUMENT_ELEMENT_GID, childList[i])
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
        NodeProxy p = new NodeProxy(this, root.getGID(), root.getInternalAddress());
        NodeSelector selector = new DescendantSelector(p, Expression.NO_CONTEXT_ID);
        return (NodeSet) broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, docs, qname, selector);
    } 
    
    /************************************************
     * 
     * NodeImpl methods
     *
     ************************************************/
    
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
                LOG.error("unknown node type");
                node = null;
        }
        return node;
    }
    
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
            childList = null;
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
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getImplementation not implemented on class " + getClass().getName());
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
		return getName() + " - <" + 
		( getDocumentElement() != null ? getDocumentElement().getNodeName() : null ) + ">";	
	}
}
