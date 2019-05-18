/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections;

import com.evolvedbinary.j8fu.function.Consumer2E;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.NotThreadSafe;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

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
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.SyntaxException;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderObjectFactory.VALIDATION_SETTING;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * An implementation of {@link Collection} that allows
 * mutations to be made to the Collection object
 *
 * Locks should be taken appropriately for any mutation
 */
@NotThreadSafe
public class MutableCollection implements Collection {

    private static final Logger LOG = LogManager.getLogger(Collection.class);
    private static final int SHALLOW_SIZE = 550;
    private static final int DOCUMENT_SIZE = 450;
    private static final int POOL_PARSER_THRESHOLD = 500;

    private int collectionId = UNKNOWN_COLLECTION_ID;
    private XmldbURI path;
    private final Lock lock;
    @GuardedBy("lock") private final Map<String, DocumentImpl> documents = new TreeMap<>();
    @GuardedBy("lock") private ObjectSet<XmldbURI> subCollections = new ObjectOpenHashSet<>(19);
    private long address = BFile.UNKNOWN_ADDRESS;  // Storage address of the collection in the BFile
    private long created = 0;
    private boolean triggersEnabled = true;
    private XMLReader userReader;
    private boolean isTempCollection;
    private Permission permissions;
    private final CollectionMetadata collectionMetadata;
    private final ObservaleMutableCollection observable = new ObservaleMutableCollection();

    // fields required by the collections cache
    private int refCount;
    private int timestamp;

    /**
     * Constructs a Collection Object (not yet persisted)
     *
     * @param broker The database broker
     * @param path The path of the Collection
     */
    public MutableCollection(final DBBroker broker, final XmldbURI path) {
        //The permissions assigned to this collection
        permissions = PermissionFactory.getDefaultCollectionPermission(broker.getBrokerPool().getSecurityManager());

        setPath(path);
        lock = new ReentrantReadWriteLock(path);
        this.collectionMetadata = new CollectionMetadata(this);
    }

    /**
     * Deserializes a Collection object
     *
     * Counterpart method to {@link #serialize(VariableByteOutputStream)}
     *
     * @param broker The database broker
     * @param path The path of the Collection
     * @param inputStream The input stream to deserialize the Collection from
     *
     * @return The Collection Object
     */
    public static MutableCollection load(final DBBroker broker, final XmldbURI path, final VariableByteInput inputStream)
            throws PermissionDeniedException, IOException, LockException {
        final MutableCollection collection = new MutableCollection(broker, path);
        collection.deserialize(broker, inputStream);
        return collection;
    }

    @Override
    public boolean isTriggersEnabled() {
        return triggersEnabled;
    }

    @Override
    public final void setPath(XmldbURI path) {
        path = path.toCollectionPathURI();
        //TODO : see if the URI resolves against DBBroker.TEMP_COLLECTION
        isTempCollection = path.getRawCollectionPath().equals(XmldbURI.TEMP_COLLECTION);
        this.path=path;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void addCollection(final DBBroker broker, final Collection child, final boolean isNew)
            throws PermissionDeniedException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
        }
        
        final XmldbURI childName = child.getURI().lastSegment();

        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            if (!subCollections.contains(childName)) {
                subCollections.add(childName);
            }
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }

        if(isNew) {
            child.setCreationTime(System.currentTimeMillis());
        }
    }

    @Override
    public List<CollectionEntry> getEntries(final DBBroker broker) throws PermissionDeniedException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        final List<CollectionEntry> list = new ArrayList<>();

        final Iterator<XmldbURI> subCollectionIterator;
        getLock().acquire(LockMode.READ_LOCK);
        try {
            subCollectionIterator = new ObjectOpenHashSet<>(subCollections).iterator();
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
        while(subCollectionIterator.hasNext()) {
            final XmldbURI subCollectionURI = subCollectionIterator.next();
            final CollectionEntry entry = new SubCollectionEntry(broker.getBrokerPool().getSecurityManager(),
                    subCollectionURI);
            entry.readMetadata(broker);
            list.add(entry);
        }

        for(final DocumentImpl document : copyOfDocs()) {
            final CollectionEntry entry = new DocumentEntry(document);
            entry.readMetadata(broker);
            list.add(entry);
        }
        return list;
    }

    @Override
    public CollectionEntry getChildCollectionEntry(final DBBroker broker, final String name)
            throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        final XmldbURI subCollectionURI = getURI().append(name);
        final CollectionEntry entry = new SubCollectionEntry(broker.getBrokerPool().getSecurityManager(),
                subCollectionURI);
        entry.readMetadata(broker);
        return entry;
    }

    @Override
    public CollectionEntry getResourceEntry(final DBBroker broker, final String name)
            throws PermissionDeniedException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        final CollectionEntry entry;
        getLock().acquire(LockMode.READ_LOCK);
        try {
            entry = new DocumentEntry(documents.get(name));
        }  finally {
            getLock().release(LockMode.READ_LOCK);
        }

        entry.readMetadata(broker);
        return entry;
    }

    @Override
    public boolean isTempCollection() {
        return isTempCollection;
    }

    @Override
    public void release(final LockMode mode) {
        getLock().release(mode);
    }

    @Override
    public void update(final DBBroker broker, final Collection child) throws PermissionDeniedException, LockException {
        final XmldbURI childName = child.getURI().lastSegment();
        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            subCollections.remove(childName);
            subCollections.add(childName);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
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
        if(oldDoc == null) {
            
            /* create */
            if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
            }
        } else {
            
            /* update-replace */
            if(!oldDoc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission to write to overwrite document: " +  oldDoc.getURI());
            }
        }
        
        if (doc.getDocId() == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
            try {
                doc.setDocId(broker.getNextResourceId(transaction, this));
            } catch(final EXistException e) {
                LOG.error("Collection error " + e.getMessage(), e);
                // TODO : re-raise the exception ? -pb
                return;
            }
        }

        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            documents.put(doc.getFileURI().getRawCollectionPath(), doc);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void unlinkDocument(final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException,
            LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to remove document from collection: " + path);
        }

        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            documents.remove(doc.getFileURI().getRawCollectionPath());
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public Iterator<XmldbURI> collectionIterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }

        getLock().acquire(LockMode.READ_LOCK);
        try {
            return new ObjectOpenHashSet<>(subCollections).iterator();
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
    }

    @Override
    public Iterator<XmldbURI> collectionIteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }
        return new ObjectOpenHashSet<>(subCollections).iterator();
    }

    @Override
    public List<Collection> getDescendants(final DBBroker broker, final Subject user) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }

        final ArrayList<Collection> collectionList;
        final Iterator<XmldbURI> i;
        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                collectionList = new ArrayList<>(subCollections.size());
                i = new ObjectOpenHashSet<>(subCollections).iterator();
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return Collections.emptyList();
        }

        while(i.hasNext()) {
            final XmldbURI childName = i.next();
            //TODO : resolve URI !
            final Collection child = broker.getCollection(path.append(childName));
            if(getPermissionsNoLock().validate(user, Permission.READ)) {
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
            throws PermissionDeniedException {
        return allDocs(broker, docs, recursive, null);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive,
            final LockedDocumentMap lockMap) throws PermissionDeniedException {
        List<XmldbURI> subColls = null;
        if(getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            try {
                getLock().acquire(LockMode.READ_LOCK);
                try {
                    //Add all docs in this collection to the returned set
                    getDocuments(broker, docs);
                    //Get a list of sub-collection URIs. We will process them
                    //after unlocking this collection. otherwise we may deadlock ourselves
                    subColls = new ArrayList<>();
                    subColls.addAll(subCollections);
                } finally {
                    getLock().release(LockMode.READ_LOCK);
                }
            } catch(final LockException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        if(recursive && subColls != null) {
            // process the child collections
            for(final XmldbURI childName : subColls) {
                //TODO : resolve URI !
                try {
                    final Collection child = broker.openCollection(path.appendInternal(childName), LockMode.NO_LOCK);
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
            final LockedDocumentMap lockMap, LockMode lockType) throws LockException, PermissionDeniedException {
        
        XmldbURI uris[] = null;
        if(getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                //Add all documents in this collection to the returned set
                getDocuments(broker, docs, lockMap, lockType);
                //Get a list of sub-collection URIs. We will process them
                //after unlocking this collection.
                //otherwise we may deadlock ourselves
                final List<XmldbURI> subColls = new ArrayList<>(subCollections);
                if (subColls != null) {
                    uris = new XmldbURI[subColls.size()];
                    for(int i = 0; i < subColls.size(); i++) {
                        uris[i] = path.appendInternal(subColls.get(i));
                    }
                }
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        }

        if(recursive && uris != null) {
            //Process the child collections
            for (XmldbURI uri : uris) {
                //TODO : resolve URI !
                try {
                    final Collection child = broker.openCollection(uri, LockMode.NO_LOCK);
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
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(LockMode.READ_LOCK);
            docs.addCollection(this);
            addDocumentsToSet(broker, docs);
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
        
        return docs;
    }

    @Override
    public DocumentSet getDocumentsNoLock(final DBBroker broker, final MutableDocumentSet docs) {
        docs.addCollection(this);
        addDocumentsToSet(broker, docs);
        return docs;
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs,
            final LockedDocumentMap lockMap, LockMode lockType) throws LockException, PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(LockMode.READ_LOCK);
            docs.addCollection(this);
            addDocumentsToSet(broker, docs, lockMap, lockType);
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
        return docs;
    }

    /**
     * Gets a stable list of the document objects
     * from {@link #documents}
     *
     * @return A stable list of the document objects
     */
    private List<DocumentImpl> copyOfDocs() throws LockException {
        getLock().acquire(LockMode.READ_LOCK);
        try {
            return new ArrayList<>(documents.values());
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
    }

    /**
     * Gets a stable set of the the document object
     * names from {@link #documents}
     *
     * @return A stable set of the document names
     */
    private Set<String> copyOfDocNames() throws LockException {
        getLock().acquire(LockMode.READ_LOCK);
        try {
            return new TreeSet<>(documents.keySet());
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
    }

    private void addDocumentsToSet(final DBBroker broker, final MutableDocumentSet docs, final LockedDocumentMap lockMap, LockMode lockType) throws LockException {
    	for(final DocumentImpl doc : copyOfDocs()) {
            if(doc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                doc.getUpdateLock().acquire(lockType);

                docs.add(doc);
                lockMap.add(doc);
            }
    	}
    }
    
    private void addDocumentsToSet(final DBBroker broker, final MutableDocumentSet docs) {
    	try {
            for (final DocumentImpl doc : copyOfDocs()) {
                if (doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    docs.add(doc);
                }
            }
        } catch(final LockException e) {
            LOG.error(e);
        }
    }

    @Override
    public boolean allowUnload() {
        if (getURI().startsWith(CollectionConfigurationManager.ROOT_COLLECTION_CONFIG_URI)) {
            return false;
        }

        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                for (final DocumentImpl doc : documents.values()) {
                    if (doc.isLockedForWrite()) {
                        return false;
                    }
                }
                return true;
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.error(e);
            return false;
        }
    }

    @Override
    public int compareTo(final Collection other) {
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
    public boolean equals(final Object obj) {
        if(obj == null || !(obj instanceof Collection)) {
            return false;
        }

        return ((Collection) obj).getId() == collectionId;
    }

    @Override
    public int getMemorySizeNoLock() {
        return SHALLOW_SIZE + (documents.size() * DOCUMENT_SIZE);
    }

    @Override
    public int getChildCollectionCount(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                return subCollections.size();
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                return documents.isEmpty() && subCollections.isEmpty();
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public DocumentImpl getDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                final DocumentImpl doc = documents.get(name.getRawCollectionPath());
                if (doc != null) {
                    if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                        throw new PermissionDeniedException("Permission denied to read document: " + name.toString());
                    }
                } else {
                    LOG.debug("Document " + name + " not found!");
                }

                return doc;
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
    	return getDocumentWithLock(broker, name, LockMode.READ_LOCK);
    }

    @Override
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI name, final LockMode lockMode) throws LockException, PermissionDeniedException {
        getLock().acquire(LockMode.READ_LOCK);
        try {
            final DocumentImpl doc = documents.get(name.getRawCollectionPath());
            if(doc != null) {
                if(!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    throw new PermissionDeniedException("Permission denied to read document: " + name.toString());
                }
            	doc.getUpdateLock().acquire(lockMode);
            }
            return doc;
        } finally {
            getLock().release(LockMode.READ_LOCK);
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
    public void releaseDocument(final DocumentImpl doc) {
        if(doc != null) {
            doc.getUpdateLock().release(LockMode.READ_LOCK);
        }
    }

    @Override
    public void releaseDocument(final DocumentImpl doc, final LockMode mode) {
        if(doc != null) {
            doc.getUpdateLock().release(mode);
        }
    }

    @Override
    public int getDocumentCount(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                return documents.size();
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
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
        return path;
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
        try {
            getLock().acquire(LockMode.READ_LOCK);
            return permissions;
        } catch(final LockException e) {
            LOG.error(e.getMessage(), e);
            return permissions;
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
    }

    @Override
    public Permission getPermissionsNoLock() {
        return permissions;
    }

    @Override
    public CollectionMetadata getMetadata() {
        return collectionMetadata;
    }

    @Override
    public boolean hasDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        try {
            getLock().acquire(LockMode.READ_LOCK);
            try {
                return documents.containsKey(name.getRawCollectionPath());
            } finally {
                getLock().release(LockMode.READ_LOCK);
            }
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            //TODO : ouch ! Should we return at any price ? Xithout even logging ? -pb
            return documents.containsKey(name.getRawCollectionPath());
        }
    }

    @Override
    public boolean hasChildCollection(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        getLock().acquire(LockMode.READ_LOCK);
        try {
            return subCollections.contains(name);
        } finally {
            getLock().release(LockMode.READ_LOCK);
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
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
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
     * Counterpart method to {@link #deserialize(DBBroker, VariableByteInput)}
     *
     * @param outputStream The output stream to write the collection contents to
     */
    @Override
    public void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException {
        outputStream.writeInt(collectionId);

        final int size;
        final Iterator<XmldbURI> i;

        getLock().acquire(LockMode.READ_LOCK);
        try {
            size = subCollections.size();
            i = new ObjectOpenHashSet<>(subCollections).iterator();
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }

        outputStream.writeInt(size);
        while(i.hasNext()) {
            final XmldbURI childCollectionURI = i.next();
            outputStream.writeUTF(childCollectionURI.toString());
        }
        permissions.write(outputStream);
        outputStream.writeLong(created);
    }
    
    /**
     * Read collection contents from the stream
     *
     * Counterpart method to {@link #serialize(VariableByteOutputStream)}
     *
     * @param broker the database broker
     * @param istream The input data
     */
    private void deserialize(final DBBroker broker, final VariableByteInput istream)
            throws IOException, PermissionDeniedException, LockException {
        collectionId = istream.readInt();
        if (collectionId < 0) {
            throw new IOException("Internal error reading collection: invalid collection id");
        }
        final int collLen = istream.readInt();

        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            subCollections = new ObjectOpenHashSet<>(collLen == 0 ? 19 : collLen); //TODO(AR) why is this number 19?
            for (int i = 0; i < collLen; i++) {
                subCollections.add(XmldbURI.create(istream.readUTF()));
            }

            permissions.read(istream);

            created = istream.readLong();

            if (!permissions.validate(broker.getCurrentSubject(), Permission.EXECUTE)) {
                throw new PermissionDeniedException("Permission denied to open the Collection " + path);
            }

            final Collection col = this;

            broker.getCollectionResources(new InternalAccess() {
                @Override
                public void addDocument(final DocumentImpl doc) throws EXistException {
                    doc.setCollection(col);

                    if (doc.getDocId() == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
                        LOG.error("Document must have ID. [" + doc + "]");
                        throw new EXistException("Document must have ID.");
                    }

                    documents.put(doc.getFileURI().getRawCollectionPath(), doc);
                }

                @Override
                public int getId() {
                    return col.getId();
                }
            });
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void removeCollection(final DBBroker broker, final XmldbURI name)
            throws LockException, PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }

        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            subCollections.remove(name);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
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
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        DocumentImpl doc = null;
        
        final BrokerPool db = broker.getBrokerPool();

        db.getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_XML, name);
        getLock().acquire(LockMode.WRITE_LOCK);

        try {
            doc = documents.get(name.getRawCollectionPath());
            
            if (doc == null) {
                return; //TODO should throw an exception!!! Otherwise we dont know if the document was removed
            }
            
            doc.getUpdateLock().acquire(LockMode.WRITE_LOCK);
            
            boolean useTriggers = isTriggersEnabled();
            if (CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(name)) {
                // we remove a collection.xconf configuration file: tell the configuration manager to
                // reload the configuration.
                useTriggers = false;
                final CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
                if (confMgr != null) {
                    confMgr.invalidate(getURI(), broker.getBrokerPool());
                }
            }
            
            DocumentTriggers trigger = new DocumentTriggers(broker, null, this, useTriggers ? getConfiguration(broker) : null);
            
            trigger.beforeDeleteDocument(broker, transaction, doc);
            
            broker.removeXMLResource(transaction, doc);
            documents.remove(name.getRawCollectionPath());
            
            trigger.afterDeleteDocument(broker, transaction, getURI().append(name));
            
            broker.getBrokerPool().getNotificationService().notifyUpdate(doc, UpdateListener.REMOVE);
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
            if(doc != null) {
                doc.getUpdateLock().release(LockMode.WRITE_LOCK);
            }
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name)
            throws PermissionDeniedException, LockException, TriggerException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        try {
            getLock().acquire(LockMode.READ_LOCK);
            final DocumentImpl doc = getDocument(broker, name);
            
            if(doc.isLockedForWrite()) {
                throw new PermissionDeniedException("Document " + doc.getFileURI() + " is locked for write");
            }
            
            removeBinaryResource(transaction, broker, doc);
        } finally {
            getLock().release(LockMode.READ_LOCK);
        }
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc)
            throws PermissionDeniedException, LockException, TriggerException {
        if(!getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        if(doc == null) {
            return;  //TODO should throw an exception!!! Otherwise we dont know if the document was removed
        }

        broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_BINARY, doc.getFileURI());
        getLock().acquire(LockMode.WRITE_LOCK);

        try {
            
            if(doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new PermissionDeniedException("document " + doc.getFileURI() + " is not a binary object");
            }
            
            if(doc.isLockedForWrite()) {
                throw new PermissionDeniedException("Document " + doc.getFileURI() + " is locked for write");
            }
            
            doc.getUpdateLock().acquire(LockMode.WRITE_LOCK);
            
            DocumentTriggers trigger = new DocumentTriggers(broker, null, this, isTriggersEnabled() ? getConfiguration(broker) : null);

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
                documents.remove(doc.getFileURI().getRawCollectionPath());
            } finally {
                indexController.endIndexDocument(transaction, listener);
            }

            trigger.afterDeleteDocument(broker, transaction, doc.getURI());

        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
            doc.getUpdateLock().release(LockMode.WRITE_LOCK);
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source)
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
            final XMLReader reader = getReader(broker, false, storeInfo.getCollectionConfig());
            storeInfo.setReader(reader, null);
            try {
                reader.parse(source);
            } catch(final IOException e) {
                throw new EXistException(e);
            } finally {
                releaseReader(broker, storeInfo, reader);
            }
        });
    }

    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final String data)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, storeInfo -> {
            final CollectionConfiguration colconf = storeInfo.getDocument().getCollection().getConfiguration(broker);
            final XMLReader reader = getReader(broker, false, colconf);
            storeInfo.setReader(reader, null);
            try {
                reader.parse(new InputSource(new StringReader(data)));
            } catch(final IOException e) {
                throw new EXistException(e);
            } finally {
                releaseReader(broker, storeInfo, reader);
            }
        });
    }

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
                LOG.debug("storing document " + document.getDocId() + " ...");
            }

            //Sanity check
            if(!document.getUpdateLock().isLockedForWrite()) {
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
            document.getUpdateLock().release(LockMode.WRITE_LOCK);
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
        broker.deleteObservers();
        
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

    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return validateXMLResource(transaction, broker, name, new InputSource(new StringReader(data)));
    }

    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);
        
        return validateXMLResourceInternal(transaction, broker, name, colconf, (info) -> {
            final XMLReader reader = getReader(broker, true, colconf);
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
            } finally {
                releaseReader(broker, info, reader);
            }
        });
    }
    
    //stops streams on the input source from being closed
    private InputSource closeShieldInputSource(final InputSource source) {
        final InputSource protectedInputSource = new InputSource();
        protectedInputSource.setEncoding(source.getEncoding());
        protectedInputSource.setSystemId(source.getSystemId());
        protectedInputSource.setPublicId(source.getPublicId());
        
        if(source.getByteStream() != null) {
            //TODO consider AutoCloseInputStream
            final InputStream closeShieldByteStream = new CloseShieldInputStream(source.getByteStream());
            protectedInputSource.setByteStream(closeShieldByteStream);
        }
        
        if(source.getCharacterStream() != null) {
            //TODO consider AutoCloseReader
            final Reader closeShieldReader = new CloseShieldReader(source.getCharacterStream());
            protectedInputSource.setCharacterStream(closeShieldReader);
        }
        
        return protectedInputSource;
    }
    
    private static class CloseShieldReader extends Reader {
        private final Reader reader;
        public CloseShieldReader(final Reader reader) {
            this.reader = reader;
        }

        @Override
        public int read(final char[] cbuf, final int off, final int len) throws IOException {
            return reader.read(cbuf, off, len);
        }

        @Override
        public void close() throws IOException {
            //do nothing as we are close shield
        }
    }

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
        
        DocumentImpl oldDoc = null;
        boolean oldDocLocked = false;

        db.getProcessMonitor().startJob(ProcessMonitor.ACTION_VALIDATE_DOC, name);
        getLock().acquire(LockMode.WRITE_LOCK);
        try {
            DocumentImpl document = new DocumentImpl((BrokerPool) db, this, name);
            oldDoc = documents.get(name.getRawCollectionPath());
            checkPermissionsForAddDocument(broker, oldDoc);
            checkCollectionConflict(name);
            manageDocumentInformation(oldDoc, document);
            final Indexer indexer = new Indexer(broker, transaction);
            
            final IndexInfo info = new IndexInfo(indexer, config);
            info.setCreating(oldDoc == null);
            info.setOldDocPermissions(oldDoc != null ? oldDoc.getPermissions() : null);
            indexer.setDocument(document, config);
            addObserversToIndexer(broker, indexer);
            indexer.setValidating(true);
            
            final DocumentTriggers trigger = new DocumentTriggers(broker, indexer, this, isTriggersEnabled() ? config : null);
            trigger.setValidating(true);
            
            info.setTriggers(trigger);

            if(oldDoc == null) {
                trigger.beforeCreateDocument(broker, transaction, getURI().append(name));
            } else {
                trigger.beforeUpdateDocument(broker, transaction, oldDoc);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Scanning document " + getURI().append(name));
            }
            
            validator.accept(info);
            // new document is valid: remove old document
            if (oldDoc != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removing old document " + oldDoc.getFileURI());
                }
                updateModificationTime(document);
                oldDoc.getUpdateLock().acquire(LockMode.WRITE_LOCK);
                oldDocLocked = true;

                /**
                 * Matching {@link StreamListener#endReplaceDocument(Txn)} call is in
                 * {@link #storeXMLInternal(Txn, DBBroker, IndexInfo, Consumer2E)}
                 */
                final StreamListener listener = broker.getIndexController().getStreamListener(document, StreamListener.ReindexMode.REPLACE_DOCUMENT);
                listener.startReplaceDocument(transaction);

                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    //TODO : use a more elaborated method ? No triggers...
                    broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
                    documents.remove(oldDoc.getFileURI().getRawCollectionPath());
                    //This lock is released in storeXMLInternal()
                    //TODO : check that we go until there to ensure the lock is released
//                    if (transaction != null)
//                    	transaction.acquireLock(document.getUpdateLock(), LockMode.WRITE_LOCK);
//                	else
                    document.getUpdateLock().acquire(LockMode.WRITE_LOCK);
                    
                    document.setDocId(broker.getNextResourceId(transaction, this));
                    addDocument(transaction, broker, document);
                } else {
                    //TODO : use a more elaborated method ? No triggers...
                    broker.removeXMLResource(transaction, oldDoc, false);
                    oldDoc.copyOf(document, true);
                    indexer.setDocumentObject(oldDoc);
                    //old has become new at this point
                    document = oldDoc;
                    oldDocLocked = false;		
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removed old document " + oldDoc.getFileURI());
                }
            } else {
                //This lock is released in storeXMLInternal()
                //TODO : check that we go until there to ensure the lock is released
//            	if (transaction != null)
//                	transaction.acquireLock(document.getUpdateLock(), LockMode.WRITE_LOCK);
//            	else
                document.getUpdateLock().acquire(LockMode.WRITE_LOCK);
            	
                document.setDocId(broker.getNextResourceId(transaction, this));
                addDocument(transaction, broker, document);
            }
            
            trigger.setValidating(false);

            return info;
        } finally {
            if (oldDoc != null && oldDocLocked) {
                oldDoc.getUpdateLock().release(LockMode.WRITE_LOCK);
            }
            getLock().release(LockMode.WRITE_LOCK);
            
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
     * Add observers to the indexer
     *
     * @param broker    The database broker
     * @param indexer   The indexer to add observers to
     */
    private void addObserversToIndexer(final DBBroker broker, final Indexer indexer) {
        broker.deleteObservers();
        observable.forEachObserver(observer -> {
            indexer.addObserver(observer);
            broker.addObserver(observer);
        });
    }


    /**
     * If an old document exists, keep information about  the document.
     *
     * @param oldDoc The old document
     * @param document The current/new document
     */
    private void manageDocumentInformation(final DocumentImpl oldDoc, final DocumentImpl document) {
        final DocumentMetadata metadata;
        if (oldDoc != null) {
            metadata = oldDoc.getMetadata();
            metadata.setCreated(oldDoc.getMetadata().getCreated());
            document.setPermissions(oldDoc.getPermissions());
        } else {
            metadata = new DocumentMetadata();
            metadata.setCreated(System.currentTimeMillis());
        }
        document.setMetadata(metadata);
    }

     /**
      * Update the modification time of a document
      *
      * @param document The document whose modification time should be updated
      */
    private void updateModificationTime(final DocumentImpl document) {
        final DocumentMetadata metadata = document.getMetadata();
        metadata.setLastModified(System.currentTimeMillis());
        document.setMetadata(metadata);
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
            
            LOG.debug("Found old doc " + oldDoc.getDocId());
            
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

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, data, mimeType, null, null);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, new FastByteArrayInputStream(data), mimeType, data.length, created, modified);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, name, is, mimeType, size, null, null);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name,
            final InputStream is, final String mimeType, final long size, final Date created, final Date modified)
            throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {

        final Database db = broker.getBrokerPool();
        if (db.isReadOnly()) {
            throw new IOException("Database is read-only");
        }

        final XmldbURI uri = getURI().append(name);

        getLock().acquire(LockMode.WRITE_LOCK);
        try {

            final DocumentImpl oldDoc = getDocument(broker, name);
            final BinaryDocument blob = new BinaryDocument(broker.getBrokerPool(), this, name);

            return addBinaryResource(db, transaction, broker, blob, is, mimeType, size, created, modified, oldDoc);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public BinaryDocument validateBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException, IOException {
        return new BinaryDocument(broker.getBrokerPool(), this, name);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        final Database db = broker.getBrokerPool();
        if (db.isReadOnly()) {
            throw new IOException("Database is read-only");
        }

        final XmldbURI docUri = blob.getFileURI();

        getLock().acquire(LockMode.WRITE_LOCK);
        try {

            final DocumentImpl oldDoc = getDocument(broker, docUri);

            return addBinaryResource(db, transaction, broker, blob, is, mimeType, size, created, modified,
                    oldDoc);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    private BinaryDocument addBinaryResource(final Database db, final Txn transaction, final DBBroker broker,
            final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created,
            final Date modified, final DocumentImpl oldDoc) throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException {

        final DocumentTriggers trigger = new DocumentTriggers(broker, null, this, isTriggersEnabled() ? getConfiguration(broker) : null);
        final XmldbURI docUri = blob.getFileURI();
        try {

            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_BINARY, docUri);
            checkPermissionsForAddDocument(broker, oldDoc);
            checkCollectionConflict(docUri);
            manageDocumentInformation(oldDoc, blob);
            final DocumentMetadata metadata = blob.getMetadata();
            metadata.setMimeType(mimeType == null ? MimeType.BINARY_TYPE.getName() : mimeType);
            if (created != null) {
                metadata.setCreated(created.getTime());
            }
            if (modified != null) {
                metadata.setLastModified(modified.getTime());
            }
            blob.setContentLength(size);

            if (oldDoc == null) {
                trigger.beforeCreateDocument(broker, transaction, blob.getURI());
            } else {
                trigger.beforeUpdateDocument(broker, transaction, oldDoc);
            }

            if (oldDoc != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removing old document db entry" + oldDoc.getFileURI());
                }

                updateModificationTime(blob);

                // remove the old document
                broker.removeResource(transaction, oldDoc);
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

            blob.getUpdateLock().acquire(LockMode.READ_LOCK);
            try {
                if (oldDoc == null) {
                    trigger.afterCreateDocument(broker, transaction, blob);
                } else {
                    trigger.afterUpdateDocument(broker, transaction, blob);
                }
            } finally {
                blob.getUpdateLock().release(LockMode.READ_LOCK);
            }
            return blob;
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
    }

    @Override
    public void setId(final int id) {
        this.collectionId = id;
    }

    @Override
    public void setPermissions(final int mode) throws LockException, PermissionDeniedException {
        try {
            getLock().acquire(LockMode.WRITE_LOCK);
            permissions.setMode(mode);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void setPermissions(final String mode) throws SyntaxException, LockException, PermissionDeniedException {
        try {
            getLock().acquire(LockMode.WRITE_LOCK);
            permissions.setMode(mode);
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void setPermissions(final Permission permissions) throws LockException {
        try {
            getLock().acquire(LockMode.WRITE_LOCK);
            this.permissions = permissions;
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
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
    public void setAddress(final long addr) {
        this.address = addr;
    }

    @Override
    public long getAddress() {
        return this.address;
    }

    @Override
    public void setCreationTime(final long ms) {
        created = ms;
    }

    @Override
    public long getCreationTime() {
        return created;
    }

    @Override
    public void setTriggersEnabled(final boolean enabled) {
        try {
            getLock().acquire(LockMode.WRITE_LOCK);
            this.triggersEnabled = enabled;
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            //Ouch ! -pb
            this.triggersEnabled = enabled;
        } finally {
            getLock().release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void setReader(final XMLReader reader){
        userReader = reader;
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
        // If user-defined Reader is set, return it;
        if (userReader != null) {
            return userReader;
        }
        // Get reader from readerpool.
        final XMLReader reader = broker.getBrokerPool().getParserPool().borrowXMLReader();
        
        // If Collection configuration exists (try to) get validation mode
        // and setup reader with this information.
        if (!validation) {
            XMLReaderObjectFactory.setReaderValidationMode(XMLReaderObjectFactory.VALIDATION_SETTING.DISABLED, reader);
            
        } else if( collectionConf!=null ) {
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
     * @param info The indexing info
     * @param reader The XML Reader to release
     */    
    private void releaseReader(final DBBroker broker, final IndexInfo info, final XMLReader reader) {
        if(userReader != null){
            return;
        }
        
        if(info.getIndexer().getDocSize() > POOL_PARSER_THRESHOLD) {
            return;
        }
        
        // Get validation mode from static configuration
        final Configuration config = broker.getConfiguration();
        final String optionValue = (String) config.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE);
        final VALIDATION_SETTING validationMode = XMLReaderObjectFactory.convertValidationMode(optionValue);
        
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
    public Observable getObservable() {
        return observable;
    }

    @Override
    public long getKey() {
        return collectionId;
    }

    @Override
    public int getReferenceCount() {
        return refCount;
    }

    @Override
    public int incReferenceCount() {
        return ++refCount;
    }

    @Override
    public int decReferenceCount() {
        return refCount > 0 ? --refCount : 0;
    }

    @Override
    public void setReferenceCount(final int count) {
        refCount = count;
    }

    @Override
    public void setTimestamp(final int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean sync(final boolean syncJournal) {
        return false;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append( getURI() );
        buf.append("[");

        try {
            for (final Iterator<String> i = copyOfDocNames().iterator(); i.hasNext(); ) {
                buf.append(i.next());
                if (i.hasNext()) {
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

    private static class ObservaleMutableCollection extends Observable {
        private Observer[] observers = null;

        @Override
        public synchronized void addObserver(final Observer o) {
            if(hasObserver(o)) {
                return;
            }
            if(observers == null) {
                observers = new Observer[1];
                observers[0] = o;
            } else {
                final Observer n[] = new Observer[observers.length + 1];
                System.arraycopy(observers, 0, n, 0, observers.length);
                n[observers.length] = o;
                observers = n;
            }
        }

        private boolean hasObserver(final Observer o) {
            if(observers == null) {
                return false;
            }
            for (Observer observer : observers) {
                if (observer == o) {
                    return true;
                }
            }
            return false;
        }

        void forEachObserver(final Consumer<Observer> consumer) {
            if(observers != null) {
                for(final Observer observer : observers) {
                    consumer.accept(observer);
                }
            }
        }

        @Override
        public synchronized void deleteObservers() {
            if(observers != null) {
                observers = null;
            }
        }
    }
}
