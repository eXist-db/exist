/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.NotThreadSafe;
import org.exist.EXistException;
import org.exist.Resource;
import org.exist.collections.Collection;
import org.exist.collections.LockedCollection;
import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.dom.memtree.DocumentFragmentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.SecurityManager;
import org.exist.security.*;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.EnsureContainerLocked;
import org.exist.storage.lock.EnsureLocked;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.XMLString;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NameTest;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;

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

    public static final byte NO_XMLDECL = 0;
    public static final byte HAS_XMLDECL = 1;

    public static final byte NO_DOCTYPE = 0;
    public static final byte HAS_DOCTYPE = 1;

    public static final byte NO_LOCKTOKEN = 0;
    public static final byte HAS_LOCKTOKEN = 2;

    protected final BrokerPool pool;

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
     * The mimeType of the document
     */
    protected String mimeType = MimeType.XML_TYPE.getName();

    /**
     * The creation time of this document
     */
    protected long created = 0;

    /**
     * Time of the last modification
     */
    protected long lastModified = 0;

    /**
     * The number of data pages occupied by this document
     */
    protected int pageCount = 0;

    /**
     * Contains the user id if a user lock is held on this resource
     */
    protected int userLock = 0;

    /**
     * The document's XML Declaration - if specified.
     */
    @Nullable private XMLDeclarationImpl xmlDecl = null;

    /**
     * The document's doctype declaration - if specified.
     */
    protected DocumentType docType = null;

    /**
     * Associated lock token - if available
     */
    protected LockToken lockToken = null;

    protected transient int splitCount = 0;

    private boolean isReferenced = false;

    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * Package private - for testing!
     *
     * @param pool a <code>BrokerPool</code> instance representing the db
     */
    DocumentImpl(final BrokerPool pool, final int docId) {
        this(null, pool, docId);
    }

    /**
     * Creates a new <code>DocumentImpl</code> instance.
     *
     * Package private - for testing!
     *
     * @param expression the expression from which this document derives
     * @param pool a <code>BrokerPool</code> instance representing the db
     */
    DocumentImpl(final Expression expression, final BrokerPool pool, final int docId) {
        this(expression, pool, null, docId, null);
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
        this(null, pool, collection, docId, fileURI);
    }

    /**
     * Creates a new persistent Document instance.
     *
     * @param expression the expression from which this document derives
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     */
    public DocumentImpl(final Expression expression, final BrokerPool pool, @Nullable final Collection collection, final int docId, @Nullable
            final XmldbURI fileURI) {
        this(expression, pool, collection, docId, fileURI,
                PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager()), 0, null,
                System.currentTimeMillis(), null, null, null, null);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param docId the id of the document
     * @param prevDoc The previous Document object that we are overwriting
     */
    public DocumentImpl(final int docId, final DocumentImpl prevDoc) {
        this(null, docId, prevDoc);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param expression the expression from which the Document object derives
     * @param docId the id of the document
     * @param prevDoc The previous Document object that we are overwriting
     */
    public DocumentImpl(final Expression expression, final int docId, final DocumentImpl prevDoc) {
        this(expression, prevDoc.pool, prevDoc.collection, docId, prevDoc.fileURI, prevDoc.permissions.copy(), 0, null,
                System.currentTimeMillis(), null, null, null, null);
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
     * @param created the created time of the document
     * @param lastModified the last modified time of the document, or null to use the {@code created} time
     * @param mimeType the media type of the document, or null for application/xml
     * @param docType the document type, or null
     *
     * @deprecated Use {@link DocumentImpl(BrokerPool, Collection, int, XmldbURI, Permission, int, long[], long, Long, String, XMLDeclarationImpl, DocumentType)}
     */
    @Deprecated
    public DocumentImpl(final BrokerPool pool, @Nullable final Collection collection,
                        final int docId, final XmldbURI fileURI, final Permission permissions,
                        final int children, @Nullable final long[] childAddress,
                        final long created, @Nullable final Long lastModified, @Nullable final String mimeType,
                        @Nullable final DocumentType docType) {
        this(null, pool, collection, docId, fileURI, permissions, children, childAddress, created, lastModified, mimeType, null, docType);
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
     * @param created the created time of the document
     * @param lastModified the last modified time of the document, or null to use the {@code created} time
     * @param mimeType the media type of the document, or null for application/xml
     * @param xmlDecl the XML Declaration, or null
     * @param docType the document type, or null
     */
    public DocumentImpl(final BrokerPool pool, @Nullable final Collection collection,
            final int docId, final XmldbURI fileURI, final Permission permissions,
            final int children, @Nullable final long[] childAddress,
            final long created, @Nullable final Long lastModified, @Nullable final String mimeType,
            @Nullable final XMLDeclarationImpl xmlDecl, @Nullable final DocumentType docType) {
        this(null, pool, collection, docId, fileURI, permissions, children, childAddress, created, lastModified, mimeType, xmlDecl, docType);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param expression the expression from which the document instance derives
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     * @param permissions the permissions of the document
     * @param children the number of children that the document has
     * @param childAddress the addresses of the child nodes
     * @param created the created time of the document
     * @param lastModified the last modified time of the document, or null to use the {@code created} time
     * @param mimeType the media type of the document, or null for application/xml
     * @param docType the document type, or null
     *
     * @deprecated Use {@link DocumentImpl(Expression, BrokerPool, Collection, int ,XmldbURI, Permission, int, long[], long, Long, String, XMLDeclarationImpl, DocumentType)}
     */
    @Deprecated
    public DocumentImpl(final Expression expression, final BrokerPool pool, @Nullable final Collection collection,
            final int docId, final XmldbURI fileURI, final Permission permissions,
            final int children, @Nullable final long[] childAddress,
            final long created, @Nullable final Long lastModified, @Nullable final String mimeType,
            @Nullable final DocumentType docType) {
        this(expression, pool, collection, docId, fileURI, permissions, children, childAddress, created, lastModified, mimeType, null, docType);
    }

    /**
     * Creates a new persistent Document instance to replace an existing document instance.
     *
     * @param expression the expression from which the document instance derives
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     * @param permissions the permissions of the document
     * @param children the number of children that the document has
     * @param childAddress the addresses of the child nodes
     * @param created the created time of the document
     * @param lastModified the last modified time of the document, or null to use the {@code created} time
     * @param mimeType the media type of the document, or null for application/xml
     * @param xmlDecl the XML Declaration, or null
     * @param docType the document type, or null
     */
    public DocumentImpl(final Expression expression, final BrokerPool pool, @Nullable final Collection collection,
            final int docId, final XmldbURI fileURI, final Permission permissions,
            final int children, @Nullable final long[] childAddress,
            final long created, @Nullable final Long lastModified, @Nullable final String mimeType,
            @Nullable final XMLDeclarationImpl xmlDecl, @Nullable final DocumentType docType) {
        super(expression);
        this.pool = pool;

        // NOTE: We must not keep a reference to a LockedCollection in the Document object!
        this.collection = LockedCollection.unwrapLocked(collection);

        this.docId = docId;
        this.fileURI = fileURI;
        this.permissions = permissions;
        this.children = children;
        this.childAddress = childAddress;
        this.created = created;
        this.lastModified = lastModified == null ? created : lastModified;
        this.mimeType = mimeType == null ?  MimeType.XML_TYPE.getName() : mimeType;
        this.xmlDecl = xmlDecl;
        this.docType = docType;

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

    public long getCreated() {
        return created;
    }

    public void setCreated(final long created) {
        this.created = created;
        if (lastModified == 0) {
            this.lastModified = created;
        }
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(final long lastModified) {
        this.lastModified = lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Get the number of pages occupied by this document.
     *
     * @return the number of pages currently occupied by this document.
     */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Set the number of pages currently occupied by this document.
     *
     * @param pageCount number of pages currently occupied by this document
     *
     */
    public void setPageCount(final int pageCount) {
        this.pageCount = pageCount;
    }

    public void incPageCount() {
        ++pageCount;
    }

    public void decPageCount() {
        --pageCount;
    }

    public void setUserLock(final int userLock) {
        this.userLock = userLock;
    }

    /**
     * @deprecated Will be removed when org.exist.dom.persistent.DocumentMetadata is removed
     * @return the internal user lock number
     */
    @Deprecated
    int getUserLockInternal() {
        return userLock;
    }

    public LockToken getLockToken() {
        return lockToken;
    }

    public void setLockToken(final LockToken token) {
        lockToken = token;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public void setDocType(final DocumentType docType) {
        this.docType = docType;
    }

    /**
     * Increase the page split count of this document. The number
     * of pages that have been split during inserts serves as an
     * indicator for the fragmentation
     */
    public void incSplitCount() {
        splitCount++;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public void setSplitCount(final int count) {
        splitCount = count;
    }

    public boolean isReferenced() {
        return isReferenced;
    }

    public void setReferenced(final boolean referenced) {
        isReferenced = referenced;
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
        throw new UnsupportedOperationException();
    }

    /**
     * Get the metadata of the Document
     *
     * @return The Document metadata
     *
     * @deprecated Will be removed in eXist-db 6.0.0. Instead use the direct methods on this class.
     */
    @Deprecated
    @EnsureContainerLocked(mode=READ_LOCK)
    public DocumentMetadata getMetadata() {
        if (metadata == null) {
            metadata = new DocumentMetadata(this);
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
     * @param broker eXist-db DBBroker
     * @param other    a <code>DocumentImpl</code> value
     * @param prev if there was an existing document which we are replacing,
     *     we will copy the mode, ACL, and birth time from the existing document.
     * @throws PermissionDeniedException in case user has not sufficient privileges
     */
    public void copyOf(final DBBroker broker, final DocumentImpl other, @EnsureLocked(mode=READ_LOCK) @Nullable final DocumentImpl prev) throws PermissionDeniedException {
        copyOf(broker, other, prev == null ? null : new Tuple2<>(prev.getPermissions(), prev.getCreated()));
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
        this.childAddress = null;
        this.children = 0;

        this.created = other.created;
        this.lastModified = other.lastModified;
        this.mimeType = other.mimeType;
        this.docType = other.docType;

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
            this.created = prev._2;

        } else {
            // copy mode and acl from source file
            copyModeAcl(broker, other.getPermissions(), permissions);

            // set birth time to the current timestamp
            this.created = timestamp;
        }

        // always set mtime
        this.lastModified = timestamp;

        // reset pageCount: will be updated during storage
        this.pageCount = 0;
    }

    private void copyModeAcl(final DBBroker broker, final Permission srcPermissions, final Permission destPermissions) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, destPermissions, Optional.of(srcPermissions.getMode()), Optional.empty());

        if (srcPermissions instanceof SimpleACLPermission srcAclPermissions && destPermissions instanceof SimpleACLPermission destAclPermissions) {
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
        this.userLock = (user == null ? 0 : user.getId());
    }

    /**
     * The method <code>getUserLock</code>
     *
     * @return an <code>User</code> value
     */
    @EnsureContainerLocked(mode=READ_LOCK)
    public Account getUserLock() {
        if (userLock == 0) {
            return null;
        }
        final SecurityManager secman = pool.getSecurityManager();
        return secman.getAccount(userLock);
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
        final long length = pageCount * pool.getPageSize();
        return (length < 0) ? 0 : length;
    }

    /**
     * The method <code>triggerDefrag</code>
     */
    public void triggerDefrag() {
        int fragmentationLimit = -1;
        final Object property = pool.getConfiguration().getProperty(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
        if(property != null) {
            fragmentationLimit = (Integer) property;
        }
        if(fragmentationLimit != -1) {
            this.splitCount = fragmentationLimit;
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
            LOG.warn("Error occurred while retrieving node: {}", e.getMessage(), e);
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
            LOG.warn("Error occurred while retrieving node: {}", e.getMessage(), e);
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

            // document attributes
            writeDocumentAttributes(pool.getSymbols(), ostream);

        } catch(final IOException e) {
            LOG.warn("io error while writing document data", e);
            //TODO : raise exception ?
        }
    }

    void writeDocumentAttributes(final SymbolTable symbolTable, final VariableByteOutputStream ostream) throws IOException {
        ostream.writeLong(created);
        ostream.writeLong(lastModified);
        ostream.writeInt(symbolTable.getMimeTypeId(mimeType));
        ostream.writeInt(pageCount);
        ostream.writeInt(userLock);
        if (xmlDecl != null) {
            ostream.writeByte(HAS_XMLDECL);
            xmlDecl.write(ostream);
        } else {
            ostream.writeByte(NO_XMLDECL);
        }
        if (docType != null) {
            ostream.writeByte(HAS_DOCTYPE);
            ((DocumentTypeImpl) docType).write(ostream);
        } else {
            ostream.writeByte(NO_DOCTYPE);
        }
        if (lockToken != null) {
            ostream.writeByte(HAS_LOCKTOKEN);
            lockToken.write(ostream);
        } else {
            ostream.writeByte(NO_LOCKTOKEN);
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

        // load document attributes
        final long created = istream.readLong();
        final long lastModified = istream.readLong();
        final int mimeTypeSymbolsIndex = istream.readInt();
        final String mimeType = pool.getSymbols().getMimeType(mimeTypeSymbolsIndex);
        final int pageCount = istream.readInt();
        final int userLock = istream.readInt();
        final XMLDeclarationImpl xmlDecl;
        if (istream.readByte() == HAS_XMLDECL) {
            xmlDecl = XMLDeclarationImpl.read(istream);
        } else {
            xmlDecl = null;
        }
        final DocumentTypeImpl docType;
        if (istream.readByte() == HAS_DOCTYPE) {
            docType = DocumentTypeImpl.read(istream);
        } else {
            docType = null;
        }
        final LockToken lockToken;
        if (istream.readByte() == HAS_LOCKTOKEN) {
            lockToken = LockToken.read(istream);
        } else {
            lockToken = null;
        }

        final DocumentImpl doc = new DocumentImpl(null, pool, null, docId, fileURI, permissions, children, childAddress, created, lastModified, mimeType, xmlDecl, docType);
        doc.pageCount = pageCount;
        doc.userLock = userLock;
        doc.lockToken = lockToken;
        return doc;
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
        if(!(oldChild instanceof IStoredNode<?> oldNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Node does not belong to this document");
        }
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
            LOG.warn("Exception while updating child node: {}", e.getMessage(), e);
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
            return broker.objectWith(new NodeProxy(getExpression(), this, NodeId.DOCUMENT_NODE, childAddress[0]));
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: {}", e.getMessage(), e);
            //TODO : throw exception ?
        }
        return null;
    }

    @EnsureContainerLocked(mode=READ_LOCK)
    protected NodeProxy getFirstChildProxy() {
        return new NodeProxy(getExpression(), this, NodeId.ROOT_NODE, Node.ELEMENT_NODE, childAddress[0]);
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
                final Node child = broker.objectWith(new NodeProxy(getExpression(), this, NodeId.DOCUMENT_NODE, childAddress[i]));
                list.add(child);
            }
        } catch(final EXistException e) {
            LOG.warn("Exception while retrieving child nodes: {}", e.getMessage(), e);
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
            contextSet.add(new NodeProxy(getExpression(), this, root.getNodeId(), root.getInternalAddress()));

            return broker.getStructuralIndex().scanByType(ElementValue.ELEMENT, Constants.DESCENDANT_AXIS,
                    new NameTest(Type.ELEMENT, qname), false, docs, contextSet, Expression.NO_CONTEXT_ID);

        } catch(final Exception e) {
            LOG.warn("Exception while finding elements: {}", e.getMessage(), e);
        }
        return NodeSet.EMPTY_SET;
    }

    /**
     * The method <code>setXmlDeclaration</code>
     *
     * @param xmlDecl a <code>XMLDeclarationImpl</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setXmlDeclaration(final XMLDeclarationImpl xmlDecl) {
        this.xmlDecl = xmlDecl;
    }

    @EnsureContainerLocked(mode=READ_LOCK)
    public @Nullable XMLDeclarationImpl getXmlDeclaration() {
        return xmlDecl;
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
        return docType;
    }

    /**
     * The method <code>setDocumentType</code>
     *
     * @param docType a <code>DocumentType</code> value
     */
    @EnsureContainerLocked(mode=WRITE_LOCK)
    public void setDocumentType(final DocumentType docType) {
        this.docType = docType;
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

        final AttrImpl attr = new AttrImpl(getExpression(), qname, getBrokerPool().getSymbols());
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

        final AttrImpl attr = new AttrImpl(getExpression(), qname, getBrokerPool().getSymbols());
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

        final ElementImpl element = new ElementImpl(getExpression(), qname, getBrokerPool().getSymbols());
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

        final ElementImpl element = new ElementImpl(getExpression(), qname, getBrokerPool().getSymbols());
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
        final TextImpl text = new TextImpl(getExpression(), data);
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
            contextSet.add(new NodeProxy(getExpression(), this, root.getNodeId(), root.getInternalAddress()));

            return broker.getStructuralIndex().scanByType(ElementValue.ELEMENT, Constants.DESCENDANT_SELF_AXIS,
                    new NameTest(Type.ELEMENT, qname), false, docs, contextSet, Expression.NO_CONTEXT_ID);
        } catch(final Exception e) {
            LOG.error("Exception while finding elements: {}", e.getMessage(), e);
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
        final CDATASectionImpl cdataSection = new CDATASectionImpl(getExpression(), new XMLString(data.toCharArray()));
        cdataSection.setOwnerDocument(this);
        return cdataSection;
    }

    @Override
    public Comment createComment(final String data) {
        final CommentImpl comment = new CommentImpl(getExpression(), data);
        comment.setOwnerDocument(this);
        return comment;
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(final String target, final String data)
            throws DOMException {
        final ProcessingInstructionImpl processingInstruction = new ProcessingInstructionImpl((Expression) null, target, data);
        processingInstruction.setOwnerDocument(this);
        return processingInstruction;
    }

    @Override
    public DocumentFragment createDocumentFragment() throws DOMException {
        return new DocumentFragmentImpl(getExpression());
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
