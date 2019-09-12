/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.NotThreadSafe;
import org.exist.EXistException;
import org.exist.Resource;
import org.exist.collections.LockedCollection;
import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.memtree.DocumentFragmentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.storage.StorageAddress;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.EnsureContainerLocked;
import org.exist.storage.lock.EnsureLocked;
import org.exist.storage.txn.Txn;
import org.exist.util.XMLString;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Type;
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

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.dom.QName.Validity.ILLEGAL_FORMAT;
import static org.exist.storage.lock.Lock.LockMode.READ_LOCK;
import static org.exist.storage.lock.Lock.LockMode.WRITE_LOCK;

/**
 * Represents a persistent document object in the database;
 * it can be an XML_FILE , a BINARY_FILE, or Xquery source code.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
@NotThreadSafe
public class DocumentImpl extends NodeImpl<DocumentImpl> implements Resource, Document {

    public static final int UNKNOWN_DOCUMENT_ID = -1;

    public static final byte XML_FILE = 0;
    public static final byte BINARY_FILE = 1;

    public static final int LENGTH_DOCUMENT_ID = 4; //sizeof int
    public static final int LENGTH_DOCUMENT_TYPE = 1; //sizeof byte

    //public static final byte DOCUMENT_NODE_SIGNATURE = 0x0F;

    private final BrokerPool pool;

    /**
     * number of child nodes
     */
    private int children = 0;
    private long[] childAddress = null;

    /**
     * the collection this document belongs to
     */
    private transient Collection collection = null;

    /**
     * the document's id
     */
    private final int docId;

    /**
     * Just the document's file name
     */
    private XmldbURI fileURI = null;

    /**
     * Lazily computed. Needs to be recomputed if {@link #fileURI} or {@link #collection} change
     */
    private XmldbURI uri = null;

    private Permission permissions = null;

    private DocumentMetadata metadata = null;

    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * Package private - for testing!
     *
     * @param pool a <code>BrokerPool</code> instance representing the db
     */
    DocumentImpl(final BrokerPool pool, final int docId) {
        this(pool, null, docId, null, PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager()), 0, null, null);
    }

    /**
     * Creates a new persistent Document instance.
     *
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     */
    public DocumentImpl(final BrokerPool pool, @Nullable final Collection collection, final int docId, @Nullable
            final XmldbURI fileURI) {
        this(pool, collection, docId, fileURI,
                PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager()), 0, null, null);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param docId the id of the document
     * @param prevDoc The previous Document object that we are overwriting
     */
    public DocumentImpl(final int docId, final DocumentImpl prevDoc) {
        this(prevDoc.pool, prevDoc.collection, docId, prevDoc.fileURI, prevDoc.permissions.copy(), 0, null, null);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     * @param permissions the permissions of the document
     * @param children the number of children that the document has
     * @param childAddress the addresses of the child nodes
     * @param metadata the document metadata
     */
    public DocumentImpl(final BrokerPool pool, @Nullable final Collection collection,
            final int docId, final XmldbURI fileURI, final Permission permissions,
            final int children, @Nullable final long[] childAddress,
            final DocumentMetadata metadata) {
        this.pool = pool;

        // NOTE: We must not keep a reference to a LockedCollection in the Document object!
        this.collection = LockedCollection.unwrapLocked(collection);

        this.docId = docId;
        this.fileURI = fileURI;
        this.permissions = permissions;
        this.children = children;
        this.childAddress = childAddress;
        this.metadata = metadata;

        //inherit the group to the resource if current collection is setGid
        if(collection != null && collection.getPermissions().isSetGid()) {
            try {
                this.permissions.setGroupFrom(collection.getPermissions());
            } catch(final PermissionDeniedException pde) {
                throw new IllegalArgumentException(pde); //TODO improve
            }
        }
    }

    //TODO document really should not hold a reference to the brokerpool
    public BrokerPool getBrokerPool() {
        return pool;
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
    @EnsureContainerLocked(mode=READ_LOCK)
    public Collection getCollection() {
        return collection;
    }

    /**
     * Set the Collection for the document
     *
     * @param collection The Collection that the document belongs too
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setCollection(final Collection collection) {
        this.collection = collection;
        this.uri = null;  // reset
    }

    /**
     * The method <code>getDocId</code>
     *
     * @return an <code>int</code> value
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public int getDocId() {
        return docId;
    }

    /**
     * @return the type of this resource, either  {@link #XML_FILE} or
     * {@link #BINARY_FILE}.
     */
    public byte getResourceType() {
        return XML_FILE;
    }

    /**
     * The method <code>getFileURI</code>
     *
     * @return a <code>XmldbURI</code> value
     */
    //@EnsureContainerLocked(mode=READ_LOCK)  // TODO(AR) temporarily we need to allow some unlocked access
    public XmldbURI getFileURI() {
        return fileURI;
    }

    /**
     * The method <code>setFileURI</code>
     *
     * @param fileURI a <code>XmldbURI</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setFileURI(final XmldbURI fileURI) {
        this.fileURI = fileURI;
        this.uri = null;  // reset
    }

    //@EnsureContainerLocked(mode=READ_LOCK)  // TODO(AR) temporarily we need to allow some unlocked access
    @Override
    public XmldbURI getURI() {
        if (uri == null) {
            if (collection == null) {
                uri = (XmldbURI) fileURI.clone();
            } else {
                uri = collection.getURI().append(fileURI);
            }
        }
        return uri;
    }

    @EnsureContainerLocked(mode=READ_LOCK)
    public boolean isCollectionConfig() {
        return fileURI.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI);
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public Permission getPermissions() {
        return permissions;
    }

    /**
     * The method <code>setMode</code>
     *
     * @param perm a <code>Permission</code> value
     * @deprecated This function is considered a security problem
     * and should be removed, move code to copyOf or Constructor
     */
    @Deprecated
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setPermissions(final Permission perm) {
        permissions = perm;
    }

    /**
     * The method <code>setMetadata</code>
     *
     * @param meta a <code>DocumentMetadata</code> value
     * @deprecated This function is considered a security problem
     * and should be removed, move code to copyOf or Constructor
     */
    @Deprecated
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setMetadata(final DocumentMetadata meta) {
        this.metadata = meta;
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public DocumentMetadata getMetadata() {
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
     * @param broker eXist-db DBBroker
     * @param other    a <code>DocumentImpl</code> value
     * @param prev if there was an existing document which we are replacing,
     *     we will copy the mode, ACL, and birth time from the existing document.
     * @throws PermissionDeniedException in case user has not sufficient privileges
     */
    public void copyOf(final DBBroker broker, final DocumentImpl other, @EnsureLocked(mode=READ_LOCK) @Nullable final DocumentImpl prev) throws PermissionDeniedException {
        copyOf(broker, other, prev == null ? null : new Tuple2<>(prev.getPermissions(), prev.getMetadata().getCreated()));
    }

    /**
     * Copy the relevant internal fields from the specified document object.
     * This is called by {@link Collection} when replacing a document.
     * @param broker eXist-db DBBroker
     * @param other a <code>DocumentImpl</code> value
     * @param prev if there was an existing document which we are replacing,
     *     we will copy the mode, ACL, and birth time from the existing document.
     * @throws PermissionDeniedException in case user has not sufficient privileges
     */
    public void copyOf(final DBBroker broker, final DocumentImpl other, @Nullable final Collection.CollectionEntry prev) throws PermissionDeniedException {
        copyOf(broker, other, prev == null ? null : new Tuple2<>(prev.getPermissions(), prev.getCreated()));
    }

    /**
     * Copy the relevant internal fields from the specified document object.
     * This is called by {@link Collection} when replacing a document.
     *
     * @param other    a <code>DocumentImpl</code> value
     * @param prev A tuple, containing the permissions and birth time of any
     *     previous document that we are replacing; We will copy the mode, ACL,
     *     and birth time from the existing document.
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    private void copyOf(final DBBroker broker, @EnsureLocked(mode=READ_LOCK) final DocumentImpl other, @Nullable final Tuple2<Permission, Long> prev) throws PermissionDeniedException {
        childAddress = null;
        children = 0;

        metadata = getMetadata();
        if (metadata == null) {
            metadata = new DocumentMetadata();
        }

        //copy metadata
        metadata.copyOf(other.getMetadata());

        final long timestamp = System.currentTimeMillis();
        if(prev != null) {
            // replaced file should have same owner user:group as prev file
            if (permissions.getGroup().getId() != prev._1.getGroup().getId()) {
                permissions.setGroup(prev.get_1().getGroup());
            }
            if (permissions.getOwner().getId() != prev._1.getOwner().getId()) {
                permissions.setOwner(prev.get_1().getOwner());
            }

            //copy mode and acl from prev file
            copyModeAcl(broker, prev._1, permissions);

            // set birth time to same as prev file
            metadata.setCreated(prev._2);

        } else {
            // copy mode and acl from source file
            copyModeAcl(broker, other.getPermissions(), permissions);

            // set birth time to the current timestamp
            metadata.setCreated(timestamp);
        }

        // always set mtime
        metadata.setLastModified(timestamp);

        // reset pageCount: will be updated during storage
        metadata.setPageCount(0);
    }

    private void copyModeAcl(final DBBroker broker, final Permission srcPermissions, final Permission destPermissions) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, destPermissions, Optional.of(srcPermissions.getMode()), Optional.empty());

        if (srcPermissions instanceof SimpleACLPermission && destPermissions instanceof SimpleACLPermission) {
            final SimpleACLPermission srcAclPermissions = (SimpleACLPermission)srcPermissions;
            final SimpleACLPermission destAclPermissions = (SimpleACLPermission)destPermissions;
            if (!destAclPermissions.equalsAcl(srcAclPermissions)) {
                PermissionFactory.chacl(destAclPermissions, newAcl ->
                    ((SimpleACLPermission)newAcl).copyAclOf(srcAclPermissions)
                );
            }
        }
    }

    /**
     * The method <code>copyChildren</code>
     *
     * @param other a <code>DocumentImpl</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void copyChildren(@EnsureLocked(mode=READ_LOCK) final DocumentImpl other) {
        childAddress = other.childAddress;
        children = other.children;
    }

    /**
     * The method <code>setUserLock</code>
     *
     * @param user an <code>User</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setUserLock(final Account user) {
        getMetadata().setUserLock(user == null ? 0 : user.getId());
    }

    /**
     * The method <code>getUserLock</code>
     *
     * @return an <code>User</code> value
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public Account getUserLock() {
        final int lockOwnerId = getMetadata().getUserLock();
        if(lockOwnerId == 0) {
            return null;
        }
        final SecurityManager secman = pool.getSecurityManager();
        return secman.getAccount(lockOwnerId);
    }

    /**
     * Returns the estimated size of the data in this document.
     *
     * As an estimation, the number of pages occupied by the document
     * is multiplied with the current page size.
     * @return the estimated size of the data in this document.
     *
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public long getContentLength() {
        final long length = getMetadata().getPageCount() * pool.getPageSize();
        return (length < 0) ? 0 : length;
    }

    /**
     * The method <code>triggerDefrag</code>
     */
    public void triggerDefrag() {
        int fragmentationLimit = -1;
        final Object property = pool.getConfiguration().getProperty(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
        if(property != null) {
            fragmentationLimit = ((Integer) property).intValue();
        }
        if(fragmentationLimit != -1) {
            getMetadata().setSplitCount(fragmentationLimit);
        }
    }

    /**
     * The method <code>getNode</code>
     *
     * @param nodeId a <code>NodeId</code> value
     * @return a <code>Node</code> value
     */
    public Node getNode(final NodeId nodeId) {
        try(final DBBroker broker = pool.getBroker()) {
            return broker.objectWith(this, nodeId);
        } catch(final EXistException e) {
            LOG.warn("Error occurred while retrieving node: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * The method <code>getNode</code>
     *
     * @param p a <code>NodeProxy</code> value
     * @return a <code>Node</code> value
     */
    public Node getNode(final NodeProxy p) {
        if(p.getNodeId().getTreeLevel() == 1) {
            return getDocumentElement();
        }
        try(final DBBroker broker = pool.getBroker()) {
            return broker.objectWith(p);
        } catch(final Exception e) {
            LOG.warn("Error occurred while retrieving node: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * The method <code>resizeChildList</code>
     */
    private void resizeChildList() {
        final long[] newChildList = new long[children];
        if(childAddress != null) {
            System.arraycopy(childAddress, 0, newChildList, 0, childAddress.length);
        }
        childAddress = newChildList;
    }

    /**
     * The method <code>appendChild</code>
     *
     * @param child a <code>NodeHandle</code> value
     * @throws DOMException if an error occurs
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void appendChild(final NodeHandle child) throws DOMException {
        ++children;
        resizeChildList();
        childAddress[children - 1] = child.getInternalAddress();
    }

    /**
     * The method <code>write</code>
     *
     * @param ostream a <code>VariableByteOutputStream</code> value
     * @throws IOException if an error occurs
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public void write(final VariableByteOutputStream ostream) throws IOException {
        try {
            ostream.writeInt(docId);
            ostream.writeUTF(fileURI.toString());
            getPermissions().write(ostream);
            ostream.writeInt(children);
            if(children > 0) {
                for(int i = 0; i < children; i++) {
                    ostream.writeInt(StorageAddress.pageFromPointer(childAddress[i]));
                    ostream.writeShort(StorageAddress.tidFromPointer(childAddress[i]));
                }
            }
            getMetadata().write(pool.getSymbols(), ostream);
        } catch(final IOException e) {
            LOG.warn("io error while writing document data", e);
            //TODO : raise exception ?
        }
    }

    /**
     * Deserialize the document object from bytes.
     *
     * @param pool the database
     * @param istream the byte stream to read
     *
     * @return the document object.
     *
     * @throws IOException  if an error occurs
     */
    public static DocumentImpl read(final BrokerPool pool, final VariableByteInput istream) throws IOException {
        final int docId = istream.readInt();
        final XmldbURI fileURI = XmldbURI.createInternal(istream.readUTF());
        final Permission permissions = PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager());
        permissions.read(istream);

        final int children = istream.readInt();
        final long childAddress[] = new long[children];
        for (int i = 0; i < children; i++) {
            childAddress[i] = StorageAddress.createPointer(istream.readInt(), istream.readShort());
        }

        final DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(pool.getSymbols(), istream);

        return new DocumentImpl(pool, null, docId, fileURI, permissions, children, childAddress, metadata);
    }

    /**
     * The method <code>compareTo</code>
     *
     * @param other an <code>DocumentImpl</code> value
     * @return an <code>int</code> value
     */
    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public int compareTo(@EnsureLocked(mode=READ_LOCK) final DocumentImpl other) {
        final long otherId = other.docId;
        if(otherId == docId) {
            return Constants.EQUAL;
        } else if(docId < otherId) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }

    @Override
    public IStoredNode updateChild(final Txn transaction, final Node oldChild, final Node newChild) throws DOMException {
        if(!(oldChild instanceof StoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Node does not belong to this document");
        }
        final IStoredNode<?> oldNode = (IStoredNode<?>) oldChild;
        final IStoredNode<?> newNode = (IStoredNode<?>) newChild;
        final IStoredNode<?> previousNode = (IStoredNode<?>) oldNode.getPreviousSibling();
        if(previousNode == null) {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "No previous sibling for the old child");
        }
        try(final DBBroker broker = pool.getBroker()) {
            if(oldChild.getNodeType() == Node.ELEMENT_NODE) {
                // replace the document-element
                //TODO : be more precise in the type test -pb
                if(newChild.getNodeType() != Node.ELEMENT_NODE) {
                    throw new DOMException(
                        DOMException.INVALID_MODIFICATION_ERR,
                        "A node replacing the document root needs to be an element");
                }
                broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
                broker.endRemove(transaction);
                newNode.setNodeId(oldNode.getNodeId());
                broker.insertNodeAfter(null, previousNode, newNode);
                final NodePath path = newNode.getPath();
                broker.indexNode(transaction, newNode, path);
                broker.endElement(newNode, path, null);
                broker.flush();
            } else {
                broker.removeNode(transaction, oldNode, oldNode.getPath(), null);
                broker.endRemove(transaction);
                newNode.setNodeId(oldNode.getNodeId());
                broker.insertNodeAfter(transaction, previousNode, newNode);
            }
        } catch(final EXistException e) {
            LOG.warn("Exception while updating child node: " + e.getMessage(), e);
            //TODO : thow exception ?
        }
        return newNode;
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public Node getFirstChild() {
        if(children == 0) {
            return null;
        }
        try(final DBBroker broker = pool.getBroker()) {
            return broker.objectWith(new NodeProxy(this, NodeId.DOCUMENT_NODE, childAddress[0]));
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
            //TODO : throw exception ?
        }
        return null;
    }

    @EnsureContainerLocked(mode=READ_LOCK)
    protected NodeProxy getFirstChildProxy() {
        return new NodeProxy(this, NodeId.ROOT_NODE, Node.ELEMENT_NODE, childAddress[0]);
    }

    /**
     * The method <code>getFirstChildAddress</code>
     *
     * @return a <code>long</code> value
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public long getFirstChildAddress() {
        if(children == 0) {
            return StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
        }
        return childAddress[0];
    }


    @Override
    public boolean hasChildNodes() {
        return children > 0;
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public NodeList getChildNodes() {
        final org.exist.dom.NodeListImpl list = new org.exist.dom.NodeListImpl();
        try(final DBBroker broker = pool.getBroker()) {
            for(int i = 0; i < children; i++) {
                final Node child = broker.objectWith(new NodeProxy(this, NodeId.DOCUMENT_NODE, childAddress[i]));
                list.add(child);
            }
        } catch(final EXistException e) {
            LOG.warn("Exception while retrieving child nodes: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * The method <code>getPreviousSibling</code>
     *
     * @param node a <code>NodeHanle</code> value
     * @return a <code>Node</code> value
     */
    protected Node getPreviousSibling(final NodeHandle node) {
        final NodeList cl = getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            final NodeHandle next = (NodeHandle) cl.item(i);
            if(StorageAddress.equals(node.getInternalAddress(), next.getInternalAddress())) {
                return i == 0 ? null : cl.item(i - 1);
            }
        }
        return null;
    }

    /**
     * The method <code>getFollowingSibling</code>
     *
     * @param node a <code>NodeHandle</code> value
     * @return a <code>Node</code> value
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    protected Node getFollowingSibling(final NodeHandle node) {
        final NodeList cl = getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            final NodeHandle next = (NodeHandle) cl.item(i);
            if(StorageAddress.equals(node, next)) {
                return i == children - 1 ? null : cl.item(i + 1);
            }
        }
        return null;
    }

    /**
     * The method <code>findElementsByTagName</code>
     *
     * @param root  a <code>NodeHandle</code> value
     * @param qname a <code>QName</code> value
     * @return a <code>NodeList</code> value
     */
    protected NodeList findElementsByTagName(final NodeHandle root, final QName qname) {
        try(final DBBroker broker = pool.getBroker()) {

            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(this);

            final NewArrayNodeSet contextSet = new NewArrayNodeSet();
            contextSet.add(new NodeProxy(this, root.getNodeId(), root.getInternalAddress()));

            return broker.getStructuralIndex().scanByType(ElementValue.ELEMENT, Constants.DESCENDANT_AXIS,
                    new NameTest(Type.ELEMENT, qname), false, docs, contextSet, Expression.NO_CONTEXT_ID);

        } catch(final Exception e) {
            LOG.warn("Exception while finding elements: " + e.getMessage(), e);
        }
        return NodeSet.EMPTY_SET;
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
    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public DocumentType getDoctype() {
        return getMetadata().getDocType();
    }

    /**
     * The method <code>setDocumentType</code>
     *
     * @param docType a <code>DocumentType</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setDocumentType(final DocumentType docType) {
        getMetadata().setDocType(docType);
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        return null;
    }

    /**
     * The method <code>setOwnerDocument</code>
     *
     * @param doc a <code>Document</code> value
     */
    public void setOwnerDocument(final Document doc) {
        if(doc != this) {
            throw new IllegalArgumentException("Can't set owner document");
        }
    }

    /**
     * The method <code>getQName</code>
     *
     * @return a <code>QName</code> value
     */
    @Override
    public QName getQName() {
        return QName.DOCUMENT_QNAME;
    }

    @Override
    public void setQName(final QName qname) {
        //do nothing
    }

    /**
     * The method <code>getNodeType</code>
     *
     * @return a <code>short</code> value
     */
    @Override
    public short getNodeType() {
        return Node.DOCUMENT_NODE;
    }

    /**
     * The method <code>getPreviousSibling</code>
     *
     * @return a <code>Node</code> value
     */
    @Override
    public Node getPreviousSibling() {
        //Documents don't have siblings
        return null;
    }

    /**
     * The method <code>getNextSibling</code>
     *
     * @return a <code>Node</code> value
     */
    @Override
    public Node getNextSibling() {
        //Documents don't have siblings
        return null;
    }

    /**
     * The method <code>createAttribute</code>
     *
     * @param name a <code>String</code> value
     * @return an <code>Attr</code> value
     * @throws DOMException if an error occurs
     */
    @Override
    public Attr createAttribute(final String name) throws DOMException {
        final QName qname;
        try {
            qname = new QName(name);
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        // check the QName is valid for use
        if(qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        final AttrImpl attr = new AttrImpl(qname, getBrokerPool().getSymbols());
        attr.setOwnerDocument(this);
        return attr;
    }

    /**
     * The method <code>createAttributeNS</code>
     *
     * @param namespaceURI  a <code>String</code> value
     * @param qualifiedName a <code>String</code> value
     * @return an <code>Attr</code> value
     * @throws DOMException if an error occurs
     */
    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        final QName qname;

        try {
            qname = QName.parse(namespaceURI, qualifiedName);
        } catch (final IllegalQNameException e) {
            final short errCode;
            if(e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        final AttrImpl attr = new AttrImpl(qname, getBrokerPool().getSymbols());
        attr.setOwnerDocument(this);
        return attr;
    }

    /**
     * The method <code>createElement</code>
     *
     * @param tagName a <code>String</code> value
     * @return an <code>Element</code> value
     * @throws DOMException if an error occurs
     */
    @Override
    public Element createElement(final String tagName) throws DOMException {
        final QName qname;

        try {
            qname = new QName(tagName);
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        // check the QName is valid for use
        if(qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        final ElementImpl element = new ElementImpl(qname, getBrokerPool().getSymbols());
        element.setOwnerDocument(this);
        return element;
    }

    /**
     * The method <code>createElementNS</code>
     *
     * @param namespaceURI  a <code>String</code> value
     * @param qualifiedName a <code>String</code> value
     * @return an <code>Element</code> value
     * @throws DOMException if an error occurs
     */
    @Override
    public Element createElementNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        final QName qname;
        try {
            qname = QName.parse(namespaceURI, qualifiedName);
        } catch (final IllegalQNameException e) {
            final short errCode;
            if(e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        final ElementImpl element = new ElementImpl(qname, getBrokerPool().getSymbols());
        element.setOwnerDocument(this);
        return element;
    }

    /**
     * The method <code>createTextNode</code>
     *
     * @param data a <code>String</code> value
     * @return a <code>Text</code> value
     */
    @Override
    public Text createTextNode(final String data) {
        final TextImpl text = new TextImpl(data);
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
    @Override
    public Element getDocumentElement() {
        final NodeList cl = getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            if(cl.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return (Element) cl.item(i);
            }
        }
        return null;
    }

    @Override
    public NodeList getElementsByTagName(final String tagname) {
        if(tagname != null && tagname.equals(QName.WILDCARD)) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(XMLConstants.DEFAULT_NS_PREFIX));
        } else {
            try {
                return getElementsByTagName(new QName(tagname));
            } catch (final IllegalQNameException e) {
                throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
            }
        }
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        final boolean wildcardNS = namespaceURI != null && namespaceURI.equals(QName.WILDCARD);
        final boolean wildcardLocalPart = localName != null && localName.equals(QName.WILDCARD);

        if(wildcardNS && wildcardLocalPart) {
            return getElementsByTagName(QName.WildcardQName.getInstance());
        } else if(wildcardNS) {
            return getElementsByTagName(new QName.WildcardNamespaceURIQName(localName));
        } else if(wildcardLocalPart) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(namespaceURI));
        } else {
            return getElementsByTagName(new QName(localName, namespaceURI));
        }
    }

    private NodeList getElementsByTagName(final QName qname) {
        try(final DBBroker broker = pool.getBroker()) {

            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(this);

            final NewArrayNodeSet contextSet = new NewArrayNodeSet();
            final ElementImpl root = ((ElementImpl)getDocumentElement());
            contextSet.add(new NodeProxy(this, root.getNodeId(), root.getInternalAddress()));

            return broker.getStructuralIndex().scanByType(ElementValue.ELEMENT, Constants.DESCENDANT_SELF_AXIS,
                    new NameTest(Type.ELEMENT, qname), false, docs, contextSet, Expression.NO_CONTEXT_ID);
        } catch(final Exception e) {
            LOG.error("Exception while finding elements: " + e.getMessage(), e);
            //TODO : throw exception ?
        }
        return NodeSet.EMPTY_SET;
    }

    @Override
    public Node getParentNode() {
        //Documents don't have parents
        return null;
    }

    /**
     * The method <code>getChildCount</code>
     *
     * @return an <code>int</code> value
     */
    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public int getChildCount() {
        return children;
    }

    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setChildCount(final int count) {
        this.children = count;
        if(children == 0) {
            this.childAddress = null;
        }
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public boolean isSameNode(final Node other) {
        // This function is used by Saxon in some circumstances, and this partial implementation is required for proper Saxon operation.
        if(other instanceof DocumentImpl) {
            return this.docId == ((DocumentImpl) other).getDocId();
        } else {
            return false;
        }
    }

    @Override
    public CDATASection createCDATASection(final String data) throws DOMException {
        final CDATASectionImpl cdataSection = new CDATASectionImpl(new XMLString(data.toCharArray()));
        cdataSection.setOwnerDocument(this);
        return cdataSection;
    }

    @Override
    public Comment createComment(final String data) {
        final CommentImpl comment = new CommentImpl(data);
        comment.setOwnerDocument(this);
        return comment;
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(final String target, final String data)
            throws DOMException {
        final ProcessingInstructionImpl processingInstruction = new ProcessingInstructionImpl(target, data);
        processingInstruction.setOwnerDocument(this);
        return processingInstruction;
    }

    @Override
    public DocumentFragment createDocumentFragment() throws DOMException {
        return new DocumentFragmentImpl();
    }

    @Override
    public EntityReference createEntityReference(final String name) throws DOMException {
        throw unsupported();
    }

    @Override
    public Element getElementById(final String elementId) {
        throw unsupported();
    }

    @Override
    public org.w3c.dom.DOMImplementation getImplementation() {
        return new DOMImplementationImpl();
    }

    @Override
    public boolean getStrictErrorChecking() {
        throw unsupported();
    }

    @Override
    public Node adoptNode(final Node node) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node importNode(final Node importedNode, final boolean deep) throws DOMException {
        throw unsupported();
    }

    @Override
    public void setStrictErrorChecking(final boolean strict) {
        throw unsupported();
    }

    @Override
    public String getInputEncoding() {
        throw unsupported();
    }

    @Override
    public String getXmlEncoding() {
        return UTF_8.name();    //TODO(AR) this should be recorded from the XML document and not hard coded
    }

    @Override
    public boolean getXmlStandalone() {
        return false;   //TODO(AR) this should be recorded from the XML document and not hard coded
    }

    @Override
    public void setXmlStandalone(final boolean xmlStandalone) throws DOMException {
    }

    @Override
    public String getXmlVersion() {
        return "1.0";   //TODO(AR) this should be recorded from the XML document and not hard coded
    }

    @Override
    public void setXmlVersion(final String xmlVersion) throws DOMException {
    }

    @Override
    public String getDocumentURI() {
        return getBaseURI();
    }

    @Override
    public void setDocumentURI(final String documentURI) {
        throw unsupported();
    }

    @Override
    public DOMConfiguration getDomConfig() {
        throw unsupported();
    }

    @Override
    public void normalizeDocument() {
        throw unsupported();
    }

    @Override
    public Node renameNode(final Node n, final String namespaceURI, final String qualifiedName) throws DOMException {
        throw unsupported();
    }

    @Override
    public String getBaseURI() {
        return getURI().toString();
    }

    @Override
    public String toString() {
        return getURI() + " - <" +
            (getDocumentElement() != null ? getDocumentElement().getNodeName() : null) + ">";
    }

    @Override
    public NodeId getNodeId() {
        return null;
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        if(newChild.getNodeType() != Node.DOCUMENT_NODE && newChild.getOwnerDocument() != this) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        if(newChild == this) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "Cannot append a document to itself");
        }

        if(newChild.getNodeType() == DOCUMENT_NODE) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may not be appended to a Document Node");
        }

        if(newChild.getNodeType() == ELEMENT_NODE && getDocumentElement() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document element");
        }

        if(newChild.getNodeType() == DOCUMENT_TYPE_NODE && getDoctype() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document type");
        }

        throw unsupported();
    }
}
