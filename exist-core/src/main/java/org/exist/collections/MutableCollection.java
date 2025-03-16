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
package org.exist.collections;

import com.evolvedbinary.j8fu.function.BiConsumer2E;
import com.evolvedbinary.j8fu.function.Consumer2E;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.io.input.CloseShieldReader;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;

import java.io.*;
import java.util.*;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.triggers.*;
import org.exist.indexing.IndexController;
import org.exist.indexing.StreamListener;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.Subject;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderObjectFactory.VALIDATION_SETTING;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;

import static org.exist.storage.lock.Lock.LockMode.*;

/**
 * An implementation of {@link Collection} that allows
 * mutations to be made to the Collection object
 *
 * Locks should be taken appropriately for any mutation
 */
@NotThreadSafe
public class MutableCollection implements Collection {

    //TODO(AR) ultimately remove all locking internally from this class and externalise it to the callers, all methods are then internally lock free, and then finally remove `NonLocking` methods

    private static final Logger LOG = LogManager.getLogger(Collection.class);
    private static final int SHALLOW_SIZE = 550;
    private static final int DOCUMENT_SIZE = 450;

    private final int collectionId;
    private XmldbURI path;
    private final LockManager lockManager;

    /*
     * LinkedHashSet is used to ensure a consistent iteration order of child Documents.
     * The `insertion-order` of a LinkedHashSet means we effectively order by Document creation
     * time, i.e. oldest first.
     * This ordering ensures that adding new Documents does not affect the existing order of Documents,
     * in this manner locks acquired when iterating are always acquired and released in the same order
     * which gives us deadlock avoidance for Document iteration.
     */
    @GuardedBy("LockManager") private final LinkedHashMap<String, DocumentImpl> documents;

    /*
     * LinkedHashSet is used to ensure a consistent iteration order of sub-Collections.
     * The `insertion-order` of a LinkedHashSet means we effectively order by sub-Collection creation
     * time, i.e. oldest first.
     * This ordering ensures that adding new sub-Collections does not affect the existing order of sub-Collections,
     * in this manner locks acquired when iterating are always acquired and released in the same order
     * which gives us deadlock avoidance for sub-Collection iteration.
     */
    @GuardedBy("LockManager") private final LinkedHashSet<XmldbURI> subCollections;

    private long created;
    private volatile boolean isTempCollection;
    private final Permission permissions;
    @Deprecated private CollectionMetadata collectionMetadata = null;

    /**
     * Constructs a Collection Object (not yet persisted)
     *
     * @param broker The database broker
     * @param collectionId a unique numeric id for the collection
     * @param path The path of the Collection
     */
    public MutableCollection(final DBBroker broker, final int collectionId, final XmldbURI path) {
        this(broker, collectionId, path, null, -1, null, null);
    }

    /**
     * Constructs a Collection Object (not yet persisted)
     *
     * @param broker The database broker
     * @param collectionId a unique numeric id for the collection
     * @param path The path of the Collection
     * @param permissions The permissions of the collection, or null for the default
     * @param created The created time of the collection, or -1 for now
     */
    public MutableCollection(final DBBroker broker, final int collectionId,
            @EnsureLocked(mode=LockMode.READ_LOCK, type=LockType.COLLECTION) final XmldbURI path,
            @Nullable final Permission permissions, final long created) {
        this(broker, collectionId, path, permissions, created, null, null);
    }

    /**
     * Constructs a Collection Object (not yet persisted)
     *
     * @param broker The database broker
     * @param collectionId a unique numeric id for the collection
     * @param path The path of the Collection
     * @param permissions The permissions of the collection, or null for the default
     * @param created The created time of the collection, or -1 for now
     * @param subCollections the sub-collections
     * @param documents the documents in the collection
     */
    private MutableCollection(final DBBroker broker, final int collectionId,
            @EnsureLocked(mode=LockMode.READ_LOCK, type=LockType.COLLECTION) final XmldbURI path,
            @Nullable final Permission permissions, final long created,
            @Nullable final LinkedHashSet<XmldbURI> subCollections,
            @Nullable final LinkedHashMap<String, DocumentImpl> documents) {
        setPath(path);
        this.collectionId = collectionId;
        this.permissions = permissions != null ? permissions : PermissionFactory.getDefaultCollectionPermission(broker.getBrokerPool().getSecurityManager());
        this.created = created > 0 ? created : System.currentTimeMillis();
        this.lockManager = broker.getBrokerPool().getLockManager();
        this.subCollections = subCollections != null ? subCollections : new LinkedHashSet<>();
        this.documents = documents != null ? documents : new LinkedHashMap<>();
    }

    /**
     * Deserializes a Collection object
     *
     * Counterpart method to {@link #serialize(VariableByteOutputStream)}
     *
     * @param broker The database broker
     * @param path The path of the Collection
     * @param inputStream The input stream to deserialize the Collection from
     * @throws PermissionDeniedException is user does not have sufficient rights
     * @throws IOException if an I/O error happens
     * @throws LockException in case dbbroker is locked
     *
     * @return The Collection Object
     */
    public static MutableCollection load(final DBBroker broker,
            @EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) final XmldbURI path,
            final VariableByteInput inputStream) throws PermissionDeniedException, IOException, LockException {
        return deserialize(broker, path, inputStream);
    }

    @Override
    public final void setPath(XmldbURI path) {
        setPath(path, false);
    }

    @Override
    public final void setPath(XmldbURI path, final boolean updateChildren) {
        path = path.toCollectionPathURI();
        //TODO : see if the URI resolves against DBBroker.TEMP_COLLECTION
        this.isTempCollection = path.getRawCollectionPath().equals(XmldbURI.TEMP_COLLECTION);
        this.path = path;

        if (updateChildren) {
            for (final Map.Entry<String, DocumentImpl> docEntry : documents.entrySet()) {
                final XmldbURI docUri = path.append(docEntry.getKey());
                try (final ManagedDocumentLock documentLock = lockManager.acquireDocumentWriteLock(docUri)) {
                    final DocumentImpl doc = docEntry.getValue();
                    doc.setCollection(this);  // this will invalidate the cached `uri` in DocumentImpl
                } catch (final LockException e) {
                    LOG.error(e.getMessage(), e);
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    @Override
    public void addCollection(final DBBroker broker, final Collection child)
            throws PermissionDeniedException, LockException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
            }

            final XmldbURI childName = child.getURI().lastSegment();
            if (!subCollections.contains(childName)) {
                subCollections.add(childName);
            }
        }
    }

    private static <T> Iterator<T> stableIterator(final LinkedHashSet<T> set) {
        return new LinkedHashSet<>(set).iterator();
    }

    private static Iterator<DocumentImpl> stableDocumentIterator(final LinkedHashMap<String, DocumentImpl> documents) {
        return new ArrayList<>(documents.values()).iterator();
    }

    private static Iterator<String> stableDocumentNameIterator(final LinkedHashMap<String, DocumentImpl> documents) {
        return new ArrayList<>(documents.keySet()).iterator();
    }

    @Override
    public List<CollectionEntry> getEntries(final DBBroker broker) throws PermissionDeniedException, LockException, IOException {
        final List<CollectionEntry> list = new ArrayList<>();

        final Iterator<XmldbURI> subCollectionIterator;
        final Iterator<DocumentImpl> documentIterator;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            subCollectionIterator = stableIterator(subCollections);
            documentIterator = stableDocumentIterator(documents);
        }

        while(subCollectionIterator.hasNext()) {
            final XmldbURI subCollectionURI = subCollectionIterator.next();
            try(final ManagedCollectionLock subCollectionLock = lockManager.acquireCollectionReadLock(subCollectionURI)) {
                final CollectionEntry entry = new SubCollectionEntry(broker.getBrokerPool().getSecurityManager(),
                        subCollectionURI);
                entry.readMetadata(broker);
                list.add(entry);
            }
        }

        while(documentIterator.hasNext()) {
            final DocumentImpl document = documentIterator.next();
            try(final ManagedDocumentLock documentLock = lockManager.acquireDocumentReadLock(document.getURI())) {
                final DocumentEntry entry = new DocumentEntry(document);
                entry.readMetadata(broker);
                list.add(entry);
            }
        }
        return list;
    }

    @Override
    public CollectionEntry getChildCollectionEntry(final DBBroker broker, final String name)
            throws PermissionDeniedException, LockException, IOException {
        final XmldbURI subCollectionURI = getURI().append(name);
        final CollectionEntry entry;
        try(final ManagedCollectionLock subCollectionLock = lockManager.acquireCollectionReadLock(subCollectionURI)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            entry = new SubCollectionEntry(broker.getBrokerPool().getSecurityManager(),
                    subCollectionURI);
            entry.readMetadata(broker);
        }
        return entry;
    }

    @Override
    public CollectionEntry getResourceEntry(final DBBroker broker, final String name)
            throws PermissionDeniedException, LockException, IOException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        final CollectionEntry entry;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            final DocumentImpl doc = documents.get(name);

            try(final ManagedDocumentLock docLock = lockManager.acquireDocumentReadLock(doc.getURI())) {

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();

                entry = new DocumentEntry(doc);
                entry.readMetadata(broker);
            }
        }

        return entry;
    }

    @Override
    public boolean isTempCollection() {
        return isTempCollection;
    }

    @Override
    public void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc)
            throws PermissionDeniedException, LockException {
        addDocument(transaction, broker, doc, null);
    }
    
    /**
     * @param oldDoc if not null, then this document is replacing another and so WRITE access on the collection is not required,
     * just WRITE access on the old document
     */
    private void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc,
            final DocumentImpl oldDoc) throws PermissionDeniedException, LockException {

        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {

            if (oldDoc == null) {

                /* create */
                if (!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
                }
            } else {
                /* update-replace */
                try (final ManagedDocumentLock oldDocLock = lockManager.acquireDocumentReadLock(oldDoc.getURI())) {
                    if (!oldDoc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {

                        // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                        collectionLock.close();

                        throw new PermissionDeniedException("Permission to write to overwrite document: " + oldDoc.getURI());
                    }
                }
            }

            try (final ManagedDocumentLock docLock = lockManager.acquireDocumentWriteLock(doc.getURI())) {

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();

                documents.put(doc.getFileURI().lastSegmentString(), doc);
            }
        }
    }

    @Override
    public void unlinkDocument(final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException,
            LockException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to remove document from collection: " + path);
            }

            documents.remove(doc.getFileURI().lastSegmentString());
        }
    }

    @Override
    public Iterator<XmldbURI> collectionIterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
            }

            return stableIterator(subCollections);
        }
    }

    @Override
    public Iterator<XmldbURI> collectionIteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }
        return stableIterator(subCollections);
    }

    @Override
    public List<Collection> getDescendants(final DBBroker broker, final Subject user) throws PermissionDeniedException {
        final ArrayList<Collection> collectionList = new ArrayList<>();
        final Iterator<XmldbURI> i;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
            }

            collectionList.ensureCapacity(subCollections.size());
            i = stableIterator(subCollections);
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return Collections.emptyList();
        }

        while(i.hasNext()) {
            final XmldbURI childName = i.next();
            //TODO : resolve URI !
            final Collection child = broker.getCollection(path.append(childName));
            if(getPermissions().validate(user, Permission.READ)) {
                collectionList.add(child);
                if(child.getChildCollectionCount(broker) > 0) {
                    //Recursive call
                    collectionList.addAll(child.getDescendants(broker, user));
                }
            }
        }

        return collectionList;
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive)
            throws PermissionDeniedException, LockException {
        return allDocs(broker, docs, recursive, null);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive,
            final LockedDocumentMap lockMap) throws PermissionDeniedException, LockException {
        XmldbURI[] subColls = null;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if (getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                //Add all docs in this collection to the returned set
                getDocuments(broker, docs);
                //Get a list of sub-collection URIs. We will process them
                //after unlocking this collection. otherwise we may deadlock ourselves
                subColls = subCollections.stream()
                        .map(path::appendInternal)
                        .toArray(XmldbURI[]::new);
            }
        }

        if(recursive && subColls != null) {
            // process the child collections
            for(final XmldbURI subCol : subColls) {
                try(final Collection child = broker.openCollection(subCol, NO_LOCK)) {      // NOTE: the recursive call below to child.addDocs will take a lock
                    //A collection may have been removed in the meantime, so check first
                    if(child != null) {
                        child.allDocs(broker, docs, recursive, lockMap);
                    }
                } catch(final PermissionDeniedException pde) {
                    //SKIP to next collection
                    //TODO create an audit log??!
                }
            }
        }
        return docs;
    }

    @Override
    public DocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive,
            final LockedDocumentMap lockMap, final LockMode lockType) throws LockException, PermissionDeniedException {
        XmldbURI[] uris = null;

        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if (getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                //Add all documents in this collection to the returned set
                getDocuments(broker, docs, lockMap, lockType);
                //Get a list of sub-collection URIs. We will process them
                //after unlocking this collection.
                //otherwise we may deadlock ourselves
                uris = subCollections.stream()
                        .map(path::appendInternal)
                        .toArray(XmldbURI[]::new);
            }
        }

        if(recursive && uris != null) {
            //Process the child collections
            for (final XmldbURI uri : uris) {
                try(final Collection child = broker.openCollection(uri, NO_LOCK)) {     // NOTE: the recursive call below to child.addDocs will take a lock
                    // a collection may have been removed in the meantime, so check first
                    if (child != null) {
                        child.allDocs(broker, docs, recursive, lockMap, lockType);
                    }
                } catch (final PermissionDeniedException pde) {
                    //SKIP to next collection
                    //TODO create an audit log??!
                }
            }
        }
        return docs;
    }

    @Override
    public DocumentSet
    getDocuments(final DBBroker broker, final MutableDocumentSet docs)
            throws PermissionDeniedException, LockException {
        final Iterator<DocumentImpl> documentIterator;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }
            documentIterator = stableDocumentIterator(documents);
            docs.addCollection(this);
        }
        addDocumentsToSet(broker, documentIterator, docs);
        
        return docs;
    }

    @Override
    public DocumentSet getDocumentsNoLock(final DBBroker broker, final MutableDocumentSet docs) {
        final Iterator<DocumentImpl> documentIterator = stableDocumentIterator(documents);
        docs.addCollection(this);
        addDocumentsToSet(broker, documentIterator, docs);
        return docs;
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs,
            final LockedDocumentMap lockMap, final LockMode lockType) throws LockException, PermissionDeniedException {
        final Iterator<DocumentImpl> documentIterator;
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }
            documentIterator = stableDocumentIterator(documents);
            docs.addCollection(this);
        }
        addDocumentsToSet(broker, documentIterator, docs, lockMap, lockType);

        return docs;
    }

    private void addDocumentsToSet(final DBBroker broker, final Iterator<DocumentImpl> documentIterator, final MutableDocumentSet docs, final LockedDocumentMap lockMap, final LockMode lockType) throws LockException {
        final int requiredPermission;
        if(lockType == LockMode.READ_LOCK) {
            requiredPermission = Permission.READ;
        } else {
            requiredPermission = Permission.WRITE;
        }

        while(documentIterator.hasNext()) {
            final DocumentImpl doc = documentIterator.next();
            if(doc.getPermissions().validate(broker.getCurrentSubject(), requiredPermission)) {
                final ManagedDocumentLock documentLock = switch (lockType) {
                    case WRITE_LOCK -> lockManager.acquireDocumentWriteLock(doc.getURI());
                    case READ_LOCK -> lockManager.acquireDocumentReadLock(doc.getURI());
                    default -> ManagedSingleLockDocumentLock.notLocked(doc.getURI());
                };

                docs.add(doc);
                lockMap.add(new LockedDocument(documentLock, doc));
            }
    	}
    }
    
    private void addDocumentsToSet(final DBBroker broker, final Iterator<DocumentImpl> documentIterator, final MutableDocumentSet docs) {
        while (documentIterator.hasNext()) {
            final DocumentImpl doc = documentIterator.next();
            try(final ManagedDocumentLock lockedDoc = lockManager.acquireDocumentReadLock(doc.getURI())) {
                if(doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    docs.add(doc);
                }
            } catch (final LockException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK)
    public int compareTo(@EnsureLocked(mode=READ_LOCK) final Collection other) {
        Objects.requireNonNull(other);

        if(collectionId == other.getId()) {
            return Constants.EQUAL;
        } else if(collectionId < other.getId()) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }

    @Override
    @EnsureContainerLocked(mode=READ_LOCK) public boolean equals(@Nullable @EnsureLocked(mode=READ_LOCK) final Object obj) {
        if(obj == null || !(obj instanceof Collection)) {
            return false;
        }

        return ((Collection) obj).getId() == collectionId;
    }

    @Override
    public int getMemorySize() {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            return SHALLOW_SIZE + (documents.size() * DOCUMENT_SIZE);
        } catch(final LockException e) {
            LOG.error(e);
            return -1;
        }
    }

    @Override
    public int getMemorySizeNoLock() {
        return SHALLOW_SIZE + (documents.size() * DOCUMENT_SIZE);
    }

    @Override
    public int getChildCollectionCount(final DBBroker broker) throws PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            return subCollections.size();
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty(final DBBroker broker) throws PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            return documents.isEmpty() && subCollections.isEmpty();
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public DocumentImpl getDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {

            try(final ManagedDocumentLock docLock = lockManager.acquireDocumentReadLock(getURI().append(name.lastSegment()))) {
                final DocumentImpl doc = documents.get(name.lastSegmentString());

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();

                if (doc != null) {
                    if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                        throw new PermissionDeniedException("Permission denied to read document: " + name);
                    }
                } else {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Document {} not found!", name);
                    }
                }

                return doc;
            }
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public LockedDocument getDocumentWithLock(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
    	return getDocumentWithLock(broker, name, READ_LOCK);
    }

    @Override
    public LockedDocument getDocumentWithLock(final DBBroker broker, final XmldbURI name, final LockMode lockMode) throws LockException, PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {

            // lock the document
            final ManagedDocumentLock documentLock;
            final Runnable unlockFn = switch (lockMode) {
                case WRITE_LOCK -> {
                    documentLock = lockManager.acquireDocumentWriteLock(getURI().append(name.lastSegment()));
                    yield documentLock::close;
                }
                case READ_LOCK -> {
                    documentLock = lockManager.acquireDocumentReadLock(getURI().append(name.lastSegment()));
                    yield documentLock::close;
                }
                default -> {
                    documentLock = ManagedSingleLockDocumentLock.notLocked(getURI().append(name.lastSegment()));
                    yield () -> {
                    };
                }
            };    // we unlock on error, or if there is no Collection


            final DocumentImpl doc = documents.get(name.lastSegmentString());

            // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
            collectionLock.close();

            if(doc == null) {
                unlockFn.run();
                return null;
            } else {
                if(!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    unlockFn.run();
                    throw new PermissionDeniedException("Permission denied to read + document: " + name);
                }

                return new LockedDocument(documentLock, doc);
            }
        }
    }

    @Override
    public DocumentImpl getDocumentNoLock(final DBBroker broker, final String rawPath) throws PermissionDeniedException {
        final DocumentImpl doc = documents.get(rawPath);
        if(doc != null) {
            if(!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read document: " + rawPath);
            }
        }
        return doc;
    }

    @Override
    public int getDocumentCount(final DBBroker broker) throws PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            return documents.size();
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public int getDocumentCountNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        return documents.size();
    }

    @Override
    public int getId() {
        return collectionId;
    }

    @Override
    public XmldbURI getURI() {
        return path;    //TODO(AR) we should have a READ_LOCK on here! but we can't as we need the URI to get the READ_LOCK... urgh!
    }

    /**
     * Returns the parent-collection.
     *
     * @return The parent-collection or null if this is the root collection.
     */
    @Override
    public XmldbURI getParentURI() {
        if(path.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            return null;
        }
        //TODO : resolve URI against ".." !
         return path.removeLastSegment();
    }

    @Override
    final public Permission getPermissions() {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            return permissions;
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return permissions;
        }
    }

    @Override
    public Permission getPermissionsNoLock() {
        return permissions;
    }

    @Deprecated
    @Override
    public CollectionMetadata getMetadata() {
        if (collectionMetadata == null) {
            collectionMetadata = new CollectionMetadata(this);
        }
        return collectionMetadata;
    }

    @Override
    public boolean hasDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            return documents.containsKey(name.lastSegmentString());
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            //TODO : ouch ! Should we return at any price ? Without even logging ? -pb
            return documents.containsKey(name.lastSegmentString());
        }
    }

    @Override
    public boolean hasChildCollection(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            return subCollections.contains(name);
        }
    }

    @Override
    public boolean hasChildCollectionNoLock(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        return subCollections.contains(name);
    }

    @Override
    public Iterator<DocumentImpl> iterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        return getDocuments(broker, new DefaultDocumentSet()).getDocumentIterator();
    }

    @Override
    public Iterator<DocumentImpl> iteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        return getDocumentsNoLock(broker, new DefaultDocumentSet()).getDocumentIterator();
    }

    /**
     * Serializes the Collection to a byte representation
     *
     * Counterpart method to {@link #deserialize(DBBroker, XmldbURI, VariableByteInput)}
     *
     * @param outputStream The output stream to write the collection contents to
     */
    @Override
    public void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException {
        outputStream.writeInt(collectionId);

        final int size;
        final Iterator<XmldbURI> i;

        //TODO(AR) should we READ_LOCK the Collection to stop it being modified concurrently? see NativeBroker#saveCollection line 1801 - already has WRITE_LOCK ;-)
//        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
            size = subCollections.size();
//            i = subCollections.stableIterator();
              i = subCollections.iterator();
//        }

        outputStream.writeInt(size);
        while(i.hasNext()) {
            final XmldbURI childCollectionURI = i.next();
            outputStream.writeUTF(childCollectionURI.toString());
        }
        permissions.write(outputStream);
        outputStream.writeLong(created);
    }

    @Override
    public void close() {
        //no-op
    }

    /**
     * Read collection contents from the stream
     *
     * Counterpart method to {@link #serialize(VariableByteOutputStream)}
     *
     * @param broker The database broker
     * @param path The path of the Collection
     * @param istream The input stream to deserialize the Collection from
     */
    private static MutableCollection deserialize(final DBBroker broker, final XmldbURI path, final VariableByteInput istream)
            throws IOException, PermissionDeniedException, LockException {
        final int collectionId = istream.readInt();
        if (collectionId < 0) {
            throw new IOException("Internal error reading collection: invalid collection id");
        }

        final int collLen = istream.readInt();

        //TODO(AR) should we WRITE_LOCK the Collection to stop it being loaded from disk concurrently? see NativeBroker#openCollection line 1030 - already has READ_LOCK ;-)
//        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path, false)) {
            final LinkedHashSet<XmldbURI> subCollections = new LinkedHashSet<>(Math.max(16, collLen));
            for (int i = 0; i < collLen; i++) {
                subCollections.add(XmldbURI.create(istream.readUTF()));
            }

            final Permission permission = PermissionFactory.getDefaultCollectionPermission(broker.getBrokerPool().getSecurityManager());
            permission.read(istream);

            if (!permission.validate(broker.getCurrentSubject(), Permission.EXECUTE)) {
                throw new PermissionDeniedException("Permission denied to open the Collection " + path);
            }

            final long created = istream.readLong();

            final LinkedHashMap<String, DocumentImpl> documents = new LinkedHashMap<>();

            final MutableCollection collection =
                new MutableCollection(broker, collectionId, path, permission, created,subCollections, documents);

            broker.getCollectionResources(new InternalAccess() {
                @Override
                public void addDocument(final DocumentImpl doc) throws EXistException {
                    doc.setCollection(collection);

                    if (doc.getDocId() == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
                        LOG.error("Document must have ID. [{}]", doc);
                        throw new EXistException("Document must have ID.");
                    }

                    documents.put(doc.getFileURI().lastSegmentString(), doc);
                }

                @Override
                public int getId() {
                    return collectionId;
                }
            });

            return collection;
//        }
    }

    @Override
    public void removeCollection(final DBBroker broker, final XmldbURI name)
            throws LockException, PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to read collection: " + path);
            }

            subCollections.remove(name);
        }
    }

    @Override
    public void removeResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc)
            throws PermissionDeniedException, LockException, IOException, TriggerException {
        if (doc.getCollection() != this) {
            throw new IOException("Document '" + doc.getURI() + "' does not belong to Collection '" + getURI() + "'.");
        }

        if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
            removeBinaryResource(transaction, broker, doc);
        } else {
            removeXMLResource(transaction, broker, doc.getFileURI());
        }
    }

    @Override
    public void removeXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name)
            throws PermissionDeniedException, TriggerException, LockException, IOException {
        final BrokerPool db = broker.getBrokerPool();

        db.getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_XML, name);
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to write collection: " + path);
            }

            try(final ManagedDocumentLock docUpdateLock = lockManager.acquireDocumentWriteLock(path.append(name.lastSegment()))) {

                final DocumentImpl doc = documents.get(name.lastSegmentString());

                if (doc == null) {
                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    collectionLock.close();

                    return; //TODO should throw an exception!!! Otherwise we dont know if the document was removed
                }

                try {
                    boolean useTriggers = broker.isTriggersEnabled();
                    if (CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(name)) {
                        // we remove a collection.xconf configuration file: tell the configuration manager to
                        // reload the configuration.
                        useTriggers = false;
                        final CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
                        if (confMgr != null) {
                            confMgr.invalidate(getURI(), broker.getBrokerPool());
                        }
                    }

                    final DocumentTriggers trigger = new DocumentTriggers(broker, transaction, null, this, useTriggers ? getConfiguration(broker) : null);

                    trigger.beforeDeleteDocument(broker, transaction, doc);

                    broker.removeXMLResource(transaction, doc);
                    documents.remove(name.lastSegmentString());

                    trigger.afterDeleteDocument(broker, transaction, getURI().append(name));

                    broker.getBrokerPool().getNotificationService().notifyUpdate(doc, UpdateListener.REMOVE);

                } finally {
                    broker.getBrokerPool().getProcessMonitor().endJob();
                }

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();
            }
        }
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name)
            throws PermissionDeniedException, LockException, TriggerException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to write collection: " + path);
            }

            try(final ManagedDocumentLock docLock = lockManager.acquireDocumentWriteLock(path.append(name.lastSegment()))) {
                final DocumentImpl doc = getDocument(broker, name);
                removeBinaryResource(transaction, broker, doc);

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();
            }
        }
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc)
            throws PermissionDeniedException, LockException, TriggerException {

        if(doc == null) {
            return;  //TODO should throw an exception!!! Otherwise we dont know if the document was removed
        }

        broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_BINARY, doc.getFileURI());
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to write collection: " + path);
            }

            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new PermissionDeniedException("document " + doc.getFileURI() + " is not a binary object");
            }

            try(final ManagedDocumentLock docUpdateLock = lockManager.acquireDocumentWriteLock(doc.getURI())) {
                try {
                    final DocumentTriggers trigger = new DocumentTriggers(broker, transaction, null, this, broker.isTriggersEnabled() ? getConfiguration(broker) : null);

                    trigger.beforeDeleteDocument(broker, transaction, doc);

                    final IndexController indexController = broker.getIndexController();
                    final StreamListener listener = indexController.getStreamListener(doc, StreamListener.ReindexMode.REMOVE_BINARY);
                    try {
                        indexController.startIndexDocument(transaction, listener);

                        try {
                            broker.removeBinaryResource(transaction, (BinaryDocument) doc);
                        } catch (final IOException ex) {
                            throw new PermissionDeniedException("Cannot delete file: " + doc.getURI().toString() + ": " + ex.getMessage(), ex);
                        }
                        documents.remove(doc.getFileURI().lastSegmentString());
                    } finally {
                        indexController.endIndexDocument(transaction, listener);
                    }

                    trigger.afterDeleteDocument(broker, transaction, doc.getURI());

                } finally {
                    broker.getBrokerPool().getProcessMonitor().endJob();
                }

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collectionLock.close();
            }
        }
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, @Nullable MimeType mimeType) throws EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        storeDocument(transaction, broker, name, source, mimeType, null, null, null, null, null);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, @Nullable MimeType mimeType, final @Nullable Date createdDate, final @Nullable Date lastModifiedDate, final @Nullable Permission permission, final @Nullable DocumentType documentType, @Nullable final XMLReader xmlReader) throws EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        if (mimeType == null) {
            mimeType = MimeType.BINARY_TYPE;
        }

        if (mimeType.isXMLType()) {
            // Store XML Document

            final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> validatorFn = (xmlReader1, validateIndexInfo) -> {
                validateIndexInfo.setReader(xmlReader1, null);
                try {
                      xmlReader1.parse(source);
                } catch(final SAXException e) {
                    throw new SAXException("The XML parser reported a problem: " + e.getMessage(), e);
                } catch(final IOException e) {
                    throw new EXistException(e);
                }
            };

            final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> parserFn = (xmlReader1, storeIndexInfo) -> {
                try {
                    storeIndexInfo.setReader(xmlReader1, null);
                    xmlReader1.parse(source);
                } catch(final IOException e) {
                    throw new EXistException(e);
                }
            };

            storeXmlDocument(transaction, broker, name, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader, validatorFn, parserFn);

        } else {
            // Store Binary Document
            try (final InputStream is = source.getByteStream()) {
                if (is == null) {
                    throw new IOException("storeDocument received a null InputStream when trying to store a Binary Document");
                }
                addBinaryResource(transaction, broker, name, is, mimeType.getName(), -1, createdDate, lastModifiedDate, permission);
            }
        }
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node, @Nullable MimeType mimeType) throws EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        storeDocument(transaction, broker, name, node, mimeType);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node, @Nullable MimeType mimeType, final @Nullable Date createdDate, final @Nullable Date lastModifiedDate, final @Nullable Permission permission, final @Nullable DocumentType documentType, @Nullable final XMLReader xmlReader) throws EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        if (mimeType == null) {
            mimeType = MimeType.BINARY_TYPE;
        }

        if (mimeType.isXMLType()) {
            // Store XML Document
            final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> validatorFn = (xmlReader1, validateIndexInfo) -> {
                validateIndexInfo.setReader(xmlReader1, null);
                validateIndexInfo.setDOMStreamer(new DOMStreamer());
                validateIndexInfo.getDOMStreamer().serialize(node, true);
            };

            final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> parserFn = (xmlReader1, storeIndexInfo) -> {
                storeIndexInfo.setReader(xmlReader1, null);
                storeIndexInfo.getDOMStreamer().serialize(node, true);
            };

            storeXmlDocument(transaction, broker, name, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader, validatorFn, parserFn);

        } else {
            throw new EXistException("Cannot store DOM Node as a Binary Document to URI: " + getURI().append(name));
        }
    }

    private void storeXmlDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final MimeType mimeType, final @Nullable Date createdDate, final @Nullable Date lastModifiedDate, final @Nullable Permission permission, final @Nullable DocumentType documentType, @Nullable final XMLReader xmlReader, final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> validatorFn, final BiConsumer2E<XMLReader, IndexInfo, SAXException, EXistException> parserFn) throws EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);

        // borrow a default XML Reader if needed
        boolean borrowed = false;
        final XMLReader xmlReader1;
        if (xmlReader != null) {
            xmlReader1 = xmlReader;
        } else {
            xmlReader1 = getReader(broker, true, colconf);
            borrowed = true;
        }

        try {
            // Phase 1 of 3 - Validate the Document
            final IndexInfo indexInfo = validateXMLResourceInternal(transaction, broker, name, colconf, validatorIndexInfo -> validatorFn.accept(xmlReader1, validatorIndexInfo));

            // Phase 2 of 3 - Set the metadata for the document
            final DocumentImpl document = indexInfo.getDocument();
            document.setMimeType(mimeType.getName());
            if (createdDate != null) {
                document.setCreated(createdDate.getTime());
                if (lastModifiedDate == null) {
                    document.setLastModified(createdDate.getTime());
                }
            }
            if (lastModifiedDate != null) {
                document.setLastModified(lastModifiedDate.getTime());
            }
            if (permission != null) {
                document.setPermissions(permission);
            }
            if (documentType != null) {
                document.setDocumentType(documentType);
            }

            // Phase 3 of 3 - Store the Document
            storeXMLInternal(transaction, broker, indexInfo, storeIndexInfo -> parserFn.accept(xmlReader1, storeIndexInfo));
        } finally {
            if (borrowed) {
                releaseReader(broker, xmlReader1);
            }
        }
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        final XMLReader reader = getReader(broker, false, info.getCollectionConfig());
        try {
            store(transaction, broker, info, source, reader);
        } finally {
            releaseReader(broker, reader);
        }
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source, final XMLReader reader)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, storeInfo -> {
            try {
                final InputStream is = source.getByteStream();
                if(is != null && is.markSupported()) {
                    is.reset();
                } else {
                    final Reader cs = source.getCharacterStream();
                    if(cs != null && cs.markSupported()) {
                        cs.reset();
                    }
                }
            } catch(final IOException e) {
                // mark is not supported: exception is expected, do nothing
                LOG.debug("InputStream or CharacterStream underlying the InputSource does not support marking and therefore cannot be re-read.");
            }
            storeInfo.setReader(reader, null);
            try {
                reader.parse(source);
            } catch(final IOException e) {
                throw new EXistException(e);
            }
        });
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final String data)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, storeInfo -> {
            final CollectionConfiguration colconf = storeInfo.getDocument().getCollection().getConfiguration(broker);
            final XMLReader reader = getReader(broker, false, colconf);
            try {
                storeInfo.setReader(reader, null);
                reader.parse(new InputSource(new StringReader(data)));
            } catch(final IOException e) {
                throw new EXistException(e);
            } finally {
                releaseReader(broker, reader);
            }
        });
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final Node node)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }

        storeXMLInternal(transaction, broker, info, storeInfo -> storeInfo.getDOMStreamer().serialize(node, true));
    }

    /** 
     * Stores an XML document in the database. {@link #validateXMLResourceInternal(Txn, DBBroker, XmldbURI,
     * CollectionConfiguration, Consumer2E)}should have been called previously in order to acquire a write lock
     * for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param parserFn    A function which parses the XML document
     */
    private void storeXMLInternal(final Txn transaction, final DBBroker broker, final IndexInfo info,
            final Consumer2E<IndexInfo, EXistException, SAXException> parserFn)
            throws EXistException, SAXException, PermissionDeniedException {
        
        final DocumentImpl document = info.getIndexer().getDocument();
        
        final Database db = broker.getBrokerPool();
        
        try {
            /* TODO
             * 
             * These security checks are temporarily disabled because throwing an exception
             * here may cause the database to corrupt.
             * Why would the database corrupt? Because validateXMLInternal that is typically
             * called before this method actually modifies the database and this collection,
             * so throwing an exception here leaves the database in an inconsistent state
             * with data 1/2 added/updated.
             * 
             * The downside of disabling these checks here is that: this collection is not locked
             * between the call to validateXmlInternal and storeXMLInternal, which means that if
             * UserA in ThreadA calls validateXmlInternal and is permitted access to store a resource,
             * and then UserB in ThreadB modifies the permissions of this collection to prevent UserA
             * from storing the document, when UserA reaches here (storeXMLInternal) they will still
             * be allowed to store their document. However the next document that UserA attempts to store
             * will be forbidden by validateXmlInternal and so the security transgression whilst not ideal
             * is short-lived.
             * 
             * To fix the issue we need to refactor validateXMLInternal and move any document/database/collection
             * modification code into storeXMLInternal after the commented out permissions checks below.
             * 
             * Noted by Adam Retter 2012-02-01T19:18
             */
            
            /*
            if(info.isCreating()) {
                // create
                * 
                if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission denied to write collection: " + path);
                }
            } else {
                // update

                final Permission oldDocPermissions = info.getOldDocPermissions();
                if(!((oldDocPermissions.getOwner().getId() != broker.getCurrentSubject().getId()) | (oldDocPermissions.validate(broker.getCurrentSubject(), Permission.WRITE)))) {
                    throw new PermissionDeniedException("A resource with the same name already exists in the target collection '" + path + "', and you do not have write access on that resource.");
                }
            }
            */

            if(LOG.isDebugEnabled()) {
                LOG.debug("storing document {} ...", document.getDocId());
            }

            //Sanity check
            if(!lockManager.isDocumentLockedForWrite(document.getURI())) {
                LOG.warn("document is not locked for write !");
            }
            
            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_DOC, document.getFileURI());
            parserFn.accept(info);
            broker.storeXMLResource(transaction, document);
            broker.flush();
            broker.closeDocument();
            //broker.checkTree(document);
            LOG.debug("document stored.");
        } finally {
            //This lock has been acquired in validateXMLResourceInternal()
            info.getDocumentLock().close();
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
        
        if(info.isCreating()) {
            info.getTriggers().afterCreateDocument(broker, transaction, document);
        } else {
            final StreamListener listener = broker.getIndexController().getStreamListener();
            listener.endReplaceDocument(transaction);

            info.getTriggers().afterUpdateDocument(broker, transaction, document);
        }
        
        db.getNotificationService().notifyUpdate(document, (info.isCreating() ? UpdateListener.ADD : UpdateListener.UPDATE));
        //Is it a collection configuration file ?
        final XmldbURI docName = document.getFileURI();
        //WARNING : there is no reason to lock the collection since setPath() is normally called in a safe way
        //TODO: *resolve* URI against CollectionConfigurationManager.CONFIG_COLLECTION_URI 
        if (getURI().startsWith(XmldbURI.CONFIG_COLLECTION_URI)
                && docName.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
            broker.sync(Sync.MAJOR);
            final CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
            if(manager != null) {
                try {
                    manager.invalidate(getURI(), broker.getBrokerPool());
                    manager.loadConfiguration(broker, this);
                } catch(final PermissionDeniedException | LockException pde) {
                    throw new EXistException(pde.getMessage(), pde);
                } catch(final CollectionConfigurationException e) {
                    // DIZ: should this exception really been thrown? bugid=1807744
                    throw new EXistException("Error while reading new collection configuration: " + e.getMessage(), e);
                }
            }
        }
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return validateXMLResource(transaction, broker, name, new InputSource(new StringReader(data)));
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);
        final XMLReader reader = getReader(broker, true, colconf);
        try {
            return validateXMLResource(transaction, broker, name, colconf, source, reader);
        } finally {
            releaseReader(broker, reader);
        }
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, final XMLReader reader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);
        return validateXMLResource(transaction, broker, name, colconf, source, reader);
    }

    private IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final CollectionConfiguration colconf, final InputSource source, final XMLReader reader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return validateXMLResourceInternal(transaction, broker, name, colconf, (info) -> {
            info.setReader(reader, null);
            try {

                /*
                 * Note - we must close shield the input source,
                 * else it can be closed by the Reader, so subsequently
                 * when we try and read it in storeXmlInternal we will get
                 * an exception.
                 */
                final InputSource closeShieldedInputSource = closeShieldInputSource(source);

                reader.parse(closeShieldedInputSource);
            } catch(final SAXException e) {
                throw new SAXException("The XML parser reported a problem: " + e.getMessage(), e);
            } catch(final IOException e) {
                throw new EXistException(e);
            }
        });
    }

    /**
     * Creates a new InputSource that prevents the streams
     * and readers of the InputSource from being closed.
     *
     * @param source the input source
     *
     * @return a new input source
     */
    private InputSource closeShieldInputSource(final InputSource source) {
        final InputSource protectedInputSource = new InputSource();
        protectedInputSource.setEncoding(source.getEncoding());
        protectedInputSource.setSystemId(source.getSystemId());
        protectedInputSource.setPublicId(source.getPublicId());
        
        if (source.getByteStream() != null) {
            //TODO(AR) consider AutoCloseInputStream
            final InputStream closeShieldByteStream = CloseShieldInputStream.wrap(source.getByteStream());
            protectedInputSource.setByteStream(closeShieldByteStream);
        }
        
        if (source.getCharacterStream() != null) {
            //TODO(AR) consider AutoCloseReader
            final Reader closeShieldReader = CloseShieldReader.wrap(source.getCharacterStream());
            protectedInputSource.setCharacterStream(closeShieldReader);
        }
        
        return protectedInputSource;
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return validateXMLResourceInternal(transaction, broker, name, getConfiguration(broker), (info) -> {
                info.setDOMStreamer(new DOMStreamer());
                info.getDOMStreamer().serialize(node, true);
        });
    }

    /** 
     * Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during
     * the process.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param validator   A function which validates the document of throws an Exception
     * 
     * @return An {@link IndexInfo} with a write lock on the document.
     */
    private IndexInfo validateXMLResourceInternal(final Txn transaction, final DBBroker broker, final XmldbURI name,
            final CollectionConfiguration config, final Consumer2E<IndexInfo, SAXException, EXistException> validator)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException,
            IOException {

        //Make the necessary operations if we process a collection configuration document
        checkConfigurationDocument(transaction, broker, name);
        
        final Database db = broker.getBrokerPool();
        
        if (db.isReadOnly()) {
            throw new IOException("Database is read-only");
        }

        ManagedDocumentLock documentWriteLock = null;
        DocumentImpl oldDoc = null;

        db.getProcessMonitor().startJob(ProcessMonitor.ACTION_VALIDATE_DOC, name);
        try {
            try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {

                // acquire the WRITE_LOCK on the Document, this lock is released in storeXMLInternal via IndexInfo
                documentWriteLock = lockManager.acquireDocumentWriteLock(getURI().append(name.lastSegment()));

                oldDoc = documents.get(name.lastSegmentString());
                checkPermissionsForAddDocument(broker, oldDoc);

                // NOTE: the new `document` object actually gets discarded in favour of the `oldDoc` below if there is an oldDoc and it is XML (so we can use -1 as the docId because it will never be used)
                final int docId = (oldDoc != null && oldDoc.getResourceType() == DocumentImpl.XML_FILE) ? - 1 : broker.getNextResourceId(transaction);
                DocumentImpl document = new DocumentImpl(null, (BrokerPool) db, this, docId, name);

                checkCollectionConflict(name);
                manageDocumentInformation(oldDoc, document);
                final Indexer indexer = new Indexer(broker, transaction);

                final IndexInfo info = new IndexInfo(indexer, config, documentWriteLock);
                info.setCreating(oldDoc == null);
                info.setOldDocPermissions(oldDoc != null ? oldDoc.getPermissions() : null);
                indexer.setDocument(document, config);
                indexer.setValidating(true);

                final DocumentTriggers trigger = new DocumentTriggers(broker, transaction, indexer, this, broker.isTriggersEnabled() ? config : null);
                trigger.setValidating(true);

                info.setTriggers(trigger);

                if (oldDoc == null) {
                    trigger.beforeCreateDocument(broker, transaction, getURI().append(name));
                } else {
                    trigger.beforeUpdateDocument(broker, transaction, oldDoc);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scanning document {}", getURI().append(name));
                }

                validator.accept(info);
                // new document is valid: remove old document
                if (oldDoc != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("removing old document {}", oldDoc.getFileURI());
                    }
                    updateModificationTime(document);

                    /**
                     * Matching {@link StreamListener#endReplaceDocument(Txn)} call is in
                     * {@link #storeXMLInternal(Txn, DBBroker, IndexInfo, Consumer2E)}
                     */
                    final StreamListener listener = broker.getIndexController().getStreamListener(document, StreamListener.ReindexMode.REPLACE_DOCUMENT);
                    listener.startReplaceDocument(transaction);

                    if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE) {
                        //TODO : use a more elaborated method ? No triggers...
                        broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
                        documents.remove(oldDoc.getFileURI().lastSegmentString());

                        addDocument(transaction, broker, document);
                    } else {
                        //TODO : use a more elaborated method ? No triggers...
                        broker.removeXMLResource(transaction, oldDoc, false);
                        oldDoc.copyOf(broker, document, oldDoc);
                        indexer.setDocumentObject(oldDoc);
                        //old has become new at this point
                        document = oldDoc;
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("removed old document {}", oldDoc.getFileURI());
                    }
                } else {
                    addDocument(transaction, broker, document);
                }

                trigger.setValidating(false);

                return info;
            }
        } catch(final EXistException | PermissionDeniedException | SAXException | LockException | IOException e) {
            // if there is an exception and we hold the document WRITE_LOCK we must release it
            if(documentWriteLock != null) {
                documentWriteLock.close();
            }
            throw e;
        } finally {
            db.getProcessMonitor().endJob();
        }
    }

    private void checkConfigurationDocument(final Txn transaction, final DBBroker broker, final XmldbURI docUri) throws EXistException, PermissionDeniedException, LockException {
        //Is it a collection configuration file ?
        //TODO : use XmldbURI.resolve() !
        if (!getURI().startsWith(XmldbURI.CONFIG_COLLECTION_URI)) {
            return;
        }
        if(!docUri.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
            return;
        }
        //Allow just one configuration document per collection
        //TODO : do not throw the exception if a system property allows several ones -pb
        for(final Iterator<DocumentImpl> i = iterator(broker); i.hasNext(); ) {
            final DocumentImpl confDoc = i.next();
            final XmldbURI currentConfDocName = confDoc.getFileURI();
            if(currentConfDocName != null && !currentConfDocName.equals(docUri)) {
                throw new EXistException("Could not store configuration '" + docUri + "': A configuration document with a different name ("
                    + currentConfDocName + ") already exists in this collection (" + getURI() + ")");
            }
        }
        //broker.saveCollection(transaction, this);
        //CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
        //if(confMgr != null)
            //try {
                //confMgr.reload(broker, this);
            // catch (CollectionConfigurationException e) {
                //throw new EXistException("An error occurred while reloading the updated collection configuration: " + e.getMessage(), e);
        //}
    }

    /**
     * If an old document exists, keep information about  the document.
     *
     * @param oldDoc The old document
     * @param document The current/new document
     */
    private void manageDocumentInformation(final DocumentImpl oldDoc, final DocumentImpl document) {
        if (oldDoc != null) {
            document.setCreated(oldDoc.getCreated());
            document.setPermissions(oldDoc.getPermissions());
        } else {
            document.setCreated(System.currentTimeMillis());
        }
    }

     /**
      * Update the modification time of a document
      *
      * @param document The document whose modification time should be updated
      */
    private void updateModificationTime(final DocumentImpl document) {
        document.setLastModified(System.currentTimeMillis());
    }
    
    /**
     * Check Permissions about user and document when a document is added to the database,
     * and throw exceptions if necessary.
     *
     * @param broker The database broker
     * @param oldDoc old Document existing in database prior to adding a new one with same name, or null if none exists
     */
    private void checkPermissionsForAddDocument(final DBBroker broker, final DocumentImpl oldDoc)
            throws LockException, PermissionDeniedException {
        
        // do we have execute permission on the collection?
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Execute permission is not granted on the Collection.");
        }
            
        if(oldDoc != null) {   
            
            /* update document */

            LOG.debug("Found old doc {}", oldDoc.getDocId());
            
            // check if the document is locked by another user
            final Account lockUser = oldDoc.getUserLock();
            if(lockUser != null && !lockUser.equals(broker.getCurrentSubject())) {
                throw new PermissionDeniedException("The document is locked by user '" + lockUser.getName() + "'.");
            }
            
            // do we have write permission on the old document or are we the owner of the old document?
            if (!((oldDoc.getPermissions().getOwner().getId() == broker.getCurrentSubject().getId()) || (oldDoc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)))) {
                throw new PermissionDeniedException("A resource with the same name already exists in the target collection '" + path + "', and you do not have write access on that resource.");
            }
        } else {
            
            /* create document */
            
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Write permission is not granted on the Collection.");
            }
        }
    }
    
    private void checkCollectionConflict(final XmldbURI docUri) throws EXistException, PermissionDeniedException {
        if(subCollections.contains(docUri.lastSegment())) {
            throw new EXistException(
                "The collection '" + getURI() + "' already has a sub-collection named '" + docUri.lastSegment() + "', you cannot create a Document with the same name as an existing collection."
            );
        }
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, data, mimeType, null, null);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, new UnsynchronizedByteArrayInputStream(data), mimeType, data.length, created, modified);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, is, mimeType, size, null, null);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name,
            final InputStream is, final String mimeType, final long size, final Date created, final Date modified)
            throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return addBinaryResource(transaction, broker, name, is, mimeType, size, created, modified, null);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name,
            final InputStream is, final String mimeType, final long size, final Date created, final Date modified,
            @Nullable final Permission permission) throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException {

        final Database db = broker.getBrokerPool();
        if (db.isReadOnly()) {
            throw new IOException("Database is read-only");
        }

        final XmldbURI uri = getURI().append(name.lastSegment());

        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path);
            final ManagedDocumentLock docLock = lockManager.acquireDocumentWriteLock(uri)) {

            final DocumentImpl oldDoc = getDocument(broker, name);

            final int docId = broker.getNextResourceId(transaction);
            final BinaryDocument blob;
            if (oldDoc != null) {
                blob = new BinaryDocument(null, docId, oldDoc);
            } else {
                blob = new BinaryDocument(null, broker.getBrokerPool(), this, docId, name);
            }

            return addBinaryResource(db, transaction, broker, blob, is, mimeType, size, created, modified, permission,
                    DBBroker.PreserveType.DEFAULT, oldDoc, collectionLock);
        }
    }

    @Deprecated
    @Override
    public BinaryDocument validateBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException, IOException {
        try {
            final int docId = broker.getNextResourceId(transaction);
            return new BinaryDocument(null, broker.getBrokerPool(), this, docId, name);
        } catch (final EXistException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return addBinaryResource(transaction, broker, blob, is, mimeType, size, created, modified, DBBroker.PreserveType.DEFAULT);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified, final DBBroker.PreserveType preserve) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        final Database db = broker.getBrokerPool();
        if (db.isReadOnly()) {
            throw new IOException("Database is read-only");
        }

        final XmldbURI docUri = blob.getFileURI();

        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path);
                final ManagedDocumentLock docLock = lockManager.acquireDocumentWriteLock(blob.getURI())) {

            final DocumentImpl oldDoc = getDocument(broker, docUri);

            return addBinaryResource(db, transaction, broker, blob, is, mimeType, size, created, modified, null, preserve,
                    oldDoc, collectionLock);
        }
    }

    private BinaryDocument addBinaryResource(final Database db, final Txn transaction, final DBBroker broker,
            final BinaryDocument blob, final InputStream is, final String mimeType, @Deprecated final long size, final Date created,
            final Date modified, @Nullable final Permission permission, final DBBroker.PreserveType preserve, final DocumentImpl oldDoc,
            final ManagedCollectionLock collectionLock) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {

        final DocumentTriggers trigger = new DocumentTriggers(broker, transaction, null, this, broker.isTriggersEnabled() ? getConfiguration(broker) : null);
        final XmldbURI docUri = blob.getFileURI();
        try {
            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_BINARY, docUri);
            checkPermissionsForAddDocument(broker, oldDoc);
            checkCollectionConflict(docUri);
            //manageDocumentInformation(oldDoc, blob);
            if (!broker.preserveOnCopy(preserve)) {
                blob.copyOf(broker, blob, oldDoc);
            }
            blob.setMimeType(mimeType == null ? MimeType.BINARY_TYPE.getName() : mimeType);
            if (created != null) {
                blob.setCreated(created.getTime());
            }
            if (modified != null) {
                blob.setLastModified(modified.getTime());
            }
//            blob.setContentLength(size);

            if (oldDoc == null) {
                trigger.beforeCreateDocument(broker, transaction, blob.getURI());
            } else {
                trigger.beforeUpdateDocument(broker, transaction, oldDoc);
            }

            if (oldDoc != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removing old document db entry{}", oldDoc.getFileURI());
                }

                if (!broker.preserveOnCopy(preserve)) {
                    updateModificationTime(blob);
                }

                // remove the old document
                broker.removeResource(transaction, oldDoc);
            }

            if (permission != null) {
                blob.setPermissions(permission);
            }

            // store the binary content (create/replace)
            broker.storeBinaryResource(transaction, blob, is);
            addDocument(transaction, broker, blob, oldDoc);

            final IndexController indexController = broker.getIndexController();
            final StreamListener listener = indexController.getStreamListener(blob, StreamListener.ReindexMode.STORE);
            indexController.startIndexDocument(transaction, listener);
            try {
                broker.storeXMLResource(transaction, blob);
            } finally {
                indexController.endIndexDocument(transaction, listener);
            }

            if (oldDoc == null) {
                trigger.afterCreateDocument(broker, transaction, blob);
            } else {
                trigger.afterUpdateDocument(broker, transaction, blob);
            }

            // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
            collectionLock.close();

            return blob;
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
    }

    @Override
    public void setPermissions(final DBBroker broker, final int mode) throws LockException, PermissionDeniedException {
        try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(path)) {
            PermissionFactory.chmod(broker, this, Optional.of(mode), Optional.empty());
        }
    }

    @Override
    public CollectionConfiguration getConfiguration(final DBBroker broker) {
        final CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
        if (manager == null) {
            return null;
        }
        //Attempt to get configuration
        return manager.getConfiguration(this);
    }

    @Override
    public void setCreated(final long ms) {
        created = ms;
    }

    @Override
    public long getCreated() {
        return created;
    }

    /** 
     * Get XML Reader from ReaderPool and setup validation when needed.
     *
     * @param broker The database broker
     * @param validation true if validation should be enabled
     * @param collectionConf The configuration of the Collection
     *
     * @return An XML Reader
     */
    private XMLReader getReader(final DBBroker broker, final boolean validation, final CollectionConfiguration collectionConf) {
        // Get reader from readerpool.
        final XMLReader reader = broker.getBrokerPool().getXmlReaderPool().borrowXMLReader();
        
        // If Collection configuration exists (try to) get validation mode
        // and setup reader with this information.
        if (!validation) {
            XMLReaderObjectFactory.setReaderValidationMode(XMLReaderObjectFactory.VALIDATION_SETTING.DISABLED, reader);
            
        } else if(collectionConf != null) {
            final VALIDATION_SETTING mode = collectionConf.getValidationMode();
            XMLReaderObjectFactory.setReaderValidationMode(mode, reader);
        }
        // Return configured reader.
        return reader;
    }

    /**
     * Reset validation mode of reader and return reader to reader pool.
     *
     * @param broker The database broker
     * @param reader The XML Reader to release
     */    
    private void releaseReader(final DBBroker broker, final XMLReader reader) {
        // Get validation mode from static configuration
        final Configuration config = broker.getConfiguration();
        final String optionValue = (String) config.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE);
        final VALIDATION_SETTING validationMode = XMLReaderObjectFactory.VALIDATION_SETTING.fromOption(optionValue);
        
        // Restore default validation mode
        XMLReaderObjectFactory.setReaderValidationMode(validationMode, reader);
        
        // Return reader
        broker.getBrokerPool().getParserPool().returnXMLReader(reader);
    }

    @Override
    public IndexSpec getIndexConfiguration(final DBBroker broker) {
        final CollectionConfiguration conf = getConfiguration(broker);
        //If the collection has its own config...
        if (conf == null) {
            return broker.getIndexConfiguration();
        }
        //... otherwise return the general config (the broker's one)
        return conf.getIndexConfiguration();
    }

    @Override
    public GeneralRangeIndexSpec getIndexByPathConfiguration(final DBBroker broker, final NodePath nodePath) {
        final IndexSpec idxSpec = getIndexConfiguration(broker);
        return (idxSpec == null) ? null : idxSpec.getIndexByPath(nodePath);
    }

    @Override
    public QNameRangeIndexSpec getIndexByQNameConfiguration(final DBBroker broker, final QName nodeName) {
        final IndexSpec idxSpec = getIndexConfiguration(broker);
        return (idxSpec == null) ? null : idxSpec.getIndexByQName(nodeName);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append( getURI() );
        buf.append("[");

        try {
            final Iterator<String> documentNameIterator;
            try (final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(path)) {
                documentNameIterator = documents.keySet().iterator();
            }

            while (documentNameIterator.hasNext()) {
                buf.append(documentNameIterator.next());
                if (documentNameIterator.hasNext()) {
                    buf.append(", ");
                }
            }
        } catch(final LockException e) {
            LOG.error(e);
            throw new IllegalStateException(e);
        }
        buf.append("]");
        return buf.toString();
    }
}
