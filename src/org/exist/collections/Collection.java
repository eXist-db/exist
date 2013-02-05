/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.collections;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.triggers.*;
import org.exist.dom.*;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.Subject;
import org.exist.storage.*;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.*;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.SyntaxException;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.hashtable.ObjectHashSet;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * This class represents a collection in the database. A collection maintains a list of
 * sub-collections and documents, and provides the methods to store/remove resources.
 *
 * Collections are shared between {@link org.exist.storage.DBBroker} instances. The caller
 * is responsible to lock/unlock the collection. Call {@link DBBroker#openCollection(XmldbURI, int)}
 * to get a collection with a read or write lock and {@link #release(int)} to release the lock.
 *
 * @author wolf
 */
public class Collection extends Observable implements Comparable<Collection>, Cacheable {

    public static int LENGTH_COLLECTION_ID = 4; //sizeof int

    public static final int POOL_PARSER_THRESHOLD = 500;

    private final static int SHALLOW_SIZE = 550;

    private final static int DOCUMENT_SIZE = 450;
    
    private final static Logger LOG = Logger.getLogger(Collection.class);
    
    public final static int UNKNOWN_COLLECTION_ID = -1;
    
    // Internal id
    private int collectionId = UNKNOWN_COLLECTION_ID;
    
    // the documents contained in this collection
    private Map<String, DocumentImpl> documents = new TreeMap<String, DocumentImpl>();
    
    // the path of this collection
    private XmldbURI path;
    
    // stores child-collections with their storage address
    private ObjectHashSet<XmldbURI> subCollections = new ObjectHashSet<XmldbURI>(19);
    
    // Storage address of the collection in the BFile
    private long address = BFile.UNKNOWN_ADDRESS;
    
    // creation time
    private long created = 0;
    
    private Observer[] observers = null;
    
    private volatile boolean collectionConfigEnabled = true;
    private boolean triggersEnabled = true;
    
    // fields required by the collections cache
    private int refCount;
    private int timestamp;
    
    private Lock lock = null;
    
    /** user-defined Reader */
    private XMLReader userReader;
    
    /** is this a temporary collection? */
    private boolean isTempCollection;

    private Permission permissions;

    private Collection(final DBBroker broker){
        //The permissions assigned to this collection
        permissions = PermissionFactory.getDefaultCollectionPermission();
    }

    public Collection(final DBBroker broker, final XmldbURI path) {
        this(broker);
        setPath(path);
        lock = new ReentrantReadWriteLock(path);
    }

    public boolean isTriggersEnabled() {
        return triggersEnabled;
    }

    public final void setPath(XmldbURI path) {
        path = path.toCollectionPathURI();
        //TODO : see if the URI resolves against DBBroker.TEMP_COLLECTION
        isTempCollection = path.getRawCollectionPath().equals(XmldbURI.TEMP_COLLECTION);
        this.path=path;
    }

    public Lock getLock() {
        return lock;
    }

    /**
     *  Add a new sub-collection to the collection.
     *
     */
    public void addCollection(final DBBroker broker, final Collection child, final boolean isNew) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
        }
        
        final XmldbURI childName = child.getURI().lastSegment();
        if(!subCollections.contains(childName)) {
            subCollections.add(childName);
        }
        if(isNew) {
            child.setCreationTime(System.currentTimeMillis());
        }
    }

    public boolean hasChildCollection(final DBBroker broker, final XmldbURI path) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        return subCollections.contains(path);
    }

    public abstract class CollectionEntry {
        private final XmldbURI uri;
        private Permission permissions;
        private long created = -1;

        protected CollectionEntry(final XmldbURI uri, final Permission permissions) {
            this.uri = uri;
            this.permissions = permissions;
        }
        
        public abstract void readMetadata(DBBroker broker);
        public abstract void read(VariableByteInput is) throws IOException;

        public XmldbURI getUri() {
            return uri;
        }
        
        public long getCreated() {
            return created;
        }

        protected void setCreated(final long created) {
            this.created = created;
        }

        public Permission getPermissions() {
            return permissions;
        }

        protected void setPermissions(final Permission permissions) {
            this.permissions = permissions;
        }

    }

    public class SubCollectionEntry extends CollectionEntry {

        public SubCollectionEntry(final XmldbURI uri) {
            super(uri, PermissionFactory.getDefaultCollectionPermission());
        }

        @Override
        public void readMetadata(final DBBroker broker) {
            broker.readCollectionEntry(this);
        }

        @Override
        public void read(final VariableByteInput is) throws IOException {
            is.skip(1);
            final int collLen = is.readInt();
            for(int i = 0; i < collLen; i++) {
                is.readUTF();
            }
            getPermissions().read(is);
            setCreated(is.readLong());
        }

        public void read(final Collection collection) {
            setPermissions(collection.getPermissionsNoLock());
            setCreated(collection.getCreationTime());
        }
    }

    public class DocumentEntry extends CollectionEntry {

        public DocumentEntry(final DocumentImpl document) {
            super(document.getURI(), document.getPermissions());
            setCreated(document.getMetadata().getCreated());
        }

        @Override
        public void readMetadata(final DBBroker broker) {
        }

        @Override
        public void read(final VariableByteInput is) throws IOException {
        }
    }

    public List<CollectionEntry> getEntries(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        final List<CollectionEntry> list = new ArrayList<CollectionEntry>();
        final Iterator<XmldbURI> subCollectionIterator = subCollections.iterator();
        while(subCollectionIterator.hasNext()) {
            final XmldbURI subCollectionURI = subCollectionIterator.next();
            final CollectionEntry entry = new SubCollectionEntry(subCollectionURI);
            entry.readMetadata(broker);
            list.add(entry);
        }
        for(final DocumentImpl document : documents.values()) {
            final CollectionEntry entry = new DocumentEntry(document);
            entry.readMetadata(broker);
            list.add(entry);
        }
        return list;
    }

    public CollectionEntry getSubCollectionEntry(final DBBroker broker, final String name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        final XmldbURI subCollectionURI = getURI().append(name);
        final CollectionEntry entry = new SubCollectionEntry(subCollectionURI);
        entry.readMetadata(broker);
        return entry;
    }

    public CollectionEntry getResourceEntry(final DBBroker broker, final String name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        final CollectionEntry entry = new DocumentEntry(documents.get(name));
        entry.readMetadata(broker);
        return entry;
    }

    /**
     * Returns true if this is a temporary collection. By default,
     * the temporary collection is in /db/system/temp.
     *
     * @return A boolean where true means the collection is temporary.
     */
    public boolean isTempCollection() {
        return isTempCollection;
    }

    /**
     * Closes the collection, i.e. releases the lock held by
     * the current thread. This is a shortcut for getLock().release().
     */
    public void release(final int mode) {
        getLock().release(mode);
    }

    /**
     * Update the specified child-collection.
     *
     * @param child
     */
    public void update(final DBBroker broker, final Collection child) throws PermissionDeniedException {
        final XmldbURI childName = child.getURI().lastSegment();
        subCollections.remove(childName);
        subCollections.add(childName);
    }

    /**
     * Add a document to the collection.
     *
     * @param  doc
     */
    public void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException {
        addDocument(transaction, broker, doc, null);
    }
    
    /**
     * @param oldDoc if not null, then this document is replacing another and so WRITE access on the collection is not required,
     * just WRITE access on the old document
     */
    private void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc, final DocumentImpl oldDoc) throws PermissionDeniedException {
        if(oldDoc == null) {
            
            /* create */
            if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Permission to write to Collection denied for " + this.getURI());
            }
        } else {
            
            /* update-replace */
            if(!oldDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
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
        documents.put(doc.getFileURI().getRawCollectionPath(), doc);
    }

    /**
     * Removes the document from the internal list of resources, but
     * doesn't delete the document object itself.
     *
     * @param doc
     */
    public void unlinkDocument(final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to remove document from collection: " + path);
        }
        documents.remove(doc.getFileURI().getRawCollectionPath());
    }

    /**
     *  Return an iterator over all sub-collections.
     *
     * The list of sub-collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @return An iterator over the collections
     */
    public Iterator<XmldbURI> collectionIterator(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subCollections.stableIterator();
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return null;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    /**
     *  Return an iterator over all sub-collections.
     *
     * The list of sub-collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @return An iterator over the collections
     */
    public Iterator<XmldbURI> collectionIteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }
        return subCollections.stableIterator();
    }

    /**
     * Return the collections below this collection
     *
     * @return List
     */
    public List<Collection> getDescendants(final DBBroker broker, final Subject user) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission to list sub-collections denied on " + this.getURI());
        }
        
        final ArrayList<Collection> collectionList = new ArrayList<Collection>(subCollections.size());
        try {
            getLock().acquire(Lock.READ_LOCK);
            for(final Iterator<XmldbURI> i = subCollections.iterator(); i.hasNext(); ) {
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
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        return collectionList;
    }

    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive) throws PermissionDeniedException {
        return allDocs(broker, docs, recursive, null);
    }

    /**
     * Retrieve all documents contained in this collections.
     *
     * If recursive is true, documents from sub-collections are
     * included.
     *
     * @return The set of documents.
     */
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap protectedDocs) throws PermissionDeniedException {
        List<XmldbURI> subColls = null;
        if(getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            try {
                //Acquire a lock on the collection
                getLock().acquire(Lock.READ_LOCK);
                //Add all docs in this collection to the returned set
                getDocuments(broker, docs);
                //Get a list of sub-collection URIs. We will process them
                //after unlocking this collection. otherwise we may deadlock ourselves
                subColls = subCollections.keys();
            } catch(final LockException e) {
                LOG.warn(e.getMessage(), e);
            } finally {
                getLock().release(Lock.READ_LOCK);
            }
        }
        if(recursive && subColls != null) {
            // process the child collections
            for(final XmldbURI childName : subColls) {
                //TODO : resolve URI !
                try {
                    final Collection child = broker.openCollection(path.appendInternal(childName), Lock.NO_LOCK);
                    //A collection may have been removed in the meantime, so check first
                    if(child != null) {
                        child.allDocs(broker, docs, recursive, protectedDocs);
                    }
                } catch(final PermissionDeniedException pde) {
                    //SKIP to next collection
                    //TODO create an audit log??!
                }
            }
        }
        return docs;
    }

    public DocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap lockMap, final int lockType) throws LockException, PermissionDeniedException {
        
        XmldbURI uris[] = null;
        if(getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            try {
                //Acquire a lock on the collection
                getLock().acquire(Lock.READ_LOCK);
                //Add all documents in this collection to the returned set
                getDocuments(broker, docs, lockMap, lockType);
                //Get a list of sub-collection URIs. We will process them
                //after unlocking this collection.
                //otherwise we may deadlock ourselves
                final List<XmldbURI> subColls = subCollections.keys();
                if (subColls != null) {
                    uris = new XmldbURI[subColls.size()];
                    for(int i = 0; i < subColls.size(); i++) {
                        uris[i] = path.appendInternal(subColls.get(i));
                    }
                }
            } catch(final LockException e) {
                LOG.error(e.getMessage());
                throw e;
            } finally {
                getLock().release(Lock.READ_LOCK);
            }
        }
        
        if(recursive && uris != null) {
            //Process the child collections
            for(int i = 0; i < uris.length; i++) {
                //TODO : resolve URI !
                try {
                    final Collection child = broker.openCollection(uris[i], Lock.NO_LOCK);
                    // a collection may have been removed in the meantime, so check first
                    if(child != null) {
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

    /**
     * Add all documents to the specified document set.
     *
     * @param docs
     */
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            docs.addCollection(this);
            addDocumentsToSet(broker, docs);
        } catch(final LockException le) {
            //TODO this should not be caught - it should be thrown - lock errors are bad!!!
            LOG.error(le.getMessage(), le);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        
        return docs;
    }

    public DocumentSet getDocumentsNoLock(final DBBroker broker, final MutableDocumentSet docs) {
        docs.addCollection(this);
        addDocumentsToSet(broker, docs);
        return docs;
    }

    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs, final LockedDocumentMap lockMap, final int lockType) throws LockException, PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            docs.addCollection(this);
            addDocumentsToSet(broker, docs, lockMap, lockType);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        return docs;
    }

    private void addDocumentsToSet(final DBBroker broker, final MutableDocumentSet docs, final LockedDocumentMap lockMap, final int lockType) throws LockException {
    	for(final DocumentImpl doc : documents.values()) {
            if(doc.getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
                lock = doc.getUpdateLock();

                lock.acquire(Lock.WRITE_LOCK);
                docs.add(doc);
                lockMap.add(doc);
            }
    	}
    }
    
    private void addDocumentsToSet(final DBBroker broker, final MutableDocumentSet docs) {
    	for(final DocumentImpl doc : documents.values()) {
            if(doc.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                docs.add(doc);
            }
    	}
    }
    
    /*
    private String[] getDocumentPaths() {
        final String paths[] = new String[documents.size()];
        int i = 0;
        for (Iterator<String> iter = documents.keySet().iterator(); iter.hasNext(); i++) {
            paths[i] = iter.next();
        }
        return paths;
    }
    */

    /**
     * Check if this collection may be safely removed from the
     * cache. Returns false if there are ongoing write operations,
     * i.e. one or more of the documents is locked for write.
     *
     * @return A boolean value where true indicates it may be unloaded.
     */
    @Override
    public boolean allowUnload() {
        for(final DocumentImpl doc : documents.values()) {
            if(doc.isLockedForWrite()) {
                return false;
            }
        }
        return true;
        //try {
            //lock.acquire(Lock.WRITE_LOCK);
            //for (Iterator i = documents.values().iterator(); i.hasNext(); ) {
                //DocumentImpl doc = (DocumentImpl) i.next();
                //if (doc.isLockedForWrite())
                    //return false;
            //}
            //return true;
        //} catch (LockException e) {
            //LOG.warn("Failed to acquire lock on collection: " + getName(), e);
        //} finally {
            //lock.release();
        //}
        //return false;
    }

    @Override
    public int compareTo(final Collection other) {
        if(collectionId == other.collectionId) {
            return Constants.EQUAL;
        } else if(collectionId < other.collectionId) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if(!(obj instanceof Collection)) {
            return false;
        }
        
        return ((Collection) obj).collectionId == collectionId;
    }

    /**
     * Returns the estimated amount of memory used by this collection
     * and its documents. This information is required by the
     * {@link org.exist.storage.CollectionCacheManager} to be able
     * to resize the caches.
     *
     * @return estimated amount of memory in bytes
     */
    public int getMemorySize() {
        return SHALLOW_SIZE + documents.size() * DOCUMENT_SIZE;
    }

    /**
     * Return the number of child-collections managed by this collection.
     *
     * @return The childCollectionCount value
     */
    public int getChildCollectionCount(final DBBroker broker) throws PermissionDeniedException {
    
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subCollections.size();
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return 0;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }
    
    /**
     * Determines if this Collection has any documents, or sub-collections
     */
    public boolean isEmpty(final DBBroker broker) throws PermissionDeniedException {
        
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            return documents.isEmpty() && subCollections.isEmpty();
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return false;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

   /**
     * Get a child resource as identified by path. This method doesn't put
     * a lock on the document nor does it recognize locks held by other threads.
     * There's no guarantee that the document still exists when accessing it.
     *
     * @param  broker
     * @param  path  The name of the document (without collection path)
     * @return the document
     */
    public DocumentImpl getDocument(final DBBroker broker, final XmldbURI path) throws PermissionDeniedException {
        try {
            getLock().acquire(Lock.READ_LOCK);
            final DocumentImpl doc = documents.get(path.getRawCollectionPath());
            if(doc != null){
                if(!doc.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                    throw new PermissionDeniedException("Permission denied to read document: " + path.toString());
                }
            } else {
            	LOG.debug("Document " + path + " not found!");
            }
            
            return doc;
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return null;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    /**
     * Retrieve a child resource after putting a read lock on it. With this method,
     * access to the received document object is safe.
     *
     * @deprecated Use getDocumentWithLock(DBBroker broker, XmldbURI uri, int lockMode)
     * @param broker
     * @param name
     * @return The document that was locked.
     * @throws LockException
     */
    @Deprecated
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
    	return getDocumentWithLock(broker,name,Lock.READ_LOCK);
    }

    /**
     * Retrieve a child resource after putting a read lock on it. With this method,
     * access to the received document object is safe.
     *
     * @param broker
     * @param uri
     * @param lockMode
     * @return The document that was locked.
     * @throws LockException
     */
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI uri, final int lockMode) throws LockException, PermissionDeniedException {
        try {
            getLock().acquire(Lock.READ_LOCK);
            final DocumentImpl doc = documents.get(uri.getRawCollectionPath());
            
            if(doc != null) {
                if(!doc.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                    throw new PermissionDeniedException("Permission denied to read document: " + uri.toString());
                }
            	doc.getUpdateLock().acquire(lockMode);
            }
            return doc;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public DocumentImpl getDocumentNoLock(final DBBroker broker, final String rawPath) throws PermissionDeniedException {
        final DocumentImpl doc = documents.get(rawPath);
        if(doc != null) {
            if(!doc.getPermissions().validate(broker.getSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to read document: " + rawPath);
            }
        }
        return doc;
    }

    /**
     * Release any locks held on the document.
     * @deprecated Use releaseDocument(DocumentImpl doc, int mode)
     * @param doc
     */
    @Deprecated
    public void releaseDocument(final DocumentImpl doc) {
        if(doc != null) {
            doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }

    /**
     * Release any locks held on the document.
     *
     * @param doc
     */
    public void releaseDocument(final DocumentImpl doc, final int mode) {
        if(doc != null) {
            doc.getUpdateLock().release(mode);
        }
    }

    /**
     * Returns the number of documents in this collection.
     *
     * @return The documentCount value
     */
    public int getDocumentCount(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            return documents.size();
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return 0;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public int getDocumentCountNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        return documents.size();
    }

    /**
     * Get the internal id.
     *
     * @return    The id value
     */
    public int getId() {
        return collectionId;
    }

    /**
     * Get the name of this collection.
     *
     * @return    The name value
     */
    public XmldbURI getURI() {
        return path;
    }

    /**
     * Returns the parent-collection.
     *
     * @return The parent-collection or null if this is the root collection.
     */
    public XmldbURI getParentURI() {
        if(path.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            return null;
        }
        //TODO : resolve URI against ".." !
         return path.removeLastSegment();
    }

    /**
     * Gets the permissions attribute of the Collection object
     *
     * @return The permissions value
     */
    final public Permission getPermissions() {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return permissions;
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            return permissions;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    final public Permission getPermissionsNoLock() {
        return permissions;
    }

    /**
     * Check if the collection has a child document.
     *
     * @param  uri  the name (without path) of the document
     * @return A value of true when the collection has the document identified.
     */
    public boolean hasDocument(final DBBroker broker, final XmldbURI uri) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        return documents.containsKey(uri.getRawCollectionPath());
    }

    /**
     * Check if the collection has a sub-collection.
     *
     * @param  name  the name of the subcollection (without path).
     * @return A value of true when the subcollection exists.
     */
    public boolean hasSubcollection(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subCollections.contains(name);
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            //TODO : ouch ! Should we return at any price ? Xithout even logging ? -pb
            return subCollections.contains(name);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public boolean hasSubcollectionNoLock(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        return subCollections.contains(name);
    }

    /**
     * Returns an iterator on the child-documents in this collection.
     *
     * @return A iterator of all the documents in the collection.
     */
    public Iterator<DocumentImpl> iterator(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        return getDocuments(broker, new DefaultDocumentSet()).getDocumentIterator();
    }

    public Iterator<DocumentImpl> iteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        return getDocumentsNoLock(broker, new DefaultDocumentSet()).getDocumentIterator();
    }

    /**
     * Write collection contents to stream.
     *
     * @param ostream
     * @throws IOException
     */
    public void write(final DBBroker broker, final VariableByteOutputStream ostream) throws IOException {
        ostream.writeInt(collectionId);
        ostream.writeInt(subCollections.size());
        
        for(final Iterator<XmldbURI> i = subCollections.iterator(); i.hasNext(); ) {
            final XmldbURI childCollectionURI = i.next();
            ostream.writeUTF(childCollectionURI.toString());
        }
        permissions.write(ostream);
        ostream.writeLong(created);
    }
    
    public interface InternalAccess {
        public void addDocument(DocumentImpl doc) throws EXistException;
        public int getId();
    }
    
    /**
     * Read collection contents from the stream.
     *
     * @param istream
     * @throws IOException
     */
    public void read(final DBBroker broker, final VariableByteInput istream) throws IOException, PermissionDeniedException {
        collectionId = istream.readInt();
        final int collLen = istream.readInt();
        subCollections = new ObjectHashSet<XmldbURI>(collLen == 0 ? 19 : collLen); //TODO what is this number 19?
        for (int i = 0; i < collLen; i++) {
            subCollections.add(XmldbURI.create(istream.readUTF()));
        }
        
        permissions.read(istream);

        created = istream.readLong();
        
        if(!permissions.validate(broker.getSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Permission denied to open the Collection " + path);
        }
        
        final Collection col = this;
        broker.getCollectionResources(new InternalAccess() {
            @Override
            public void addDocument(final DocumentImpl doc) throws EXistException {
                doc.setCollection(col);
                
                if(doc.getDocId() == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
                    LOG.error("Document must have ID. ["+doc+"]");
                    throw new EXistException("Document must have ID.");
                }
                
                documents.put(doc.getFileURI().getRawCollectionPath(), doc);
            }

            @Override
            public int getId() {
                return col.getId();
            }
        });
    }

    /**
     * Remove the specified sub-collection.
     *
     * @param  name  Description of the Parameter
     */
    public void removeCollection(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to read collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            subCollections.remove(name);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    /**
     * Remove the specified document from the collection.
     *
     * @param  transaction
     * @param  broker
     * @param  docUri
     */
    public void removeXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri) throws PermissionDeniedException, TriggerException, LockException {
        
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        DocumentImpl doc = null;
        
        final BrokerPool db = broker.getBrokerPool();
        try {
            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_XML, docUri);

            getLock().acquire(Lock.WRITE_LOCK);
            
            doc = documents.get(docUri.getRawCollectionPath());
            
            if (doc == null) {
                return; //TODO should throw an exception!!! Otherwise we dont know if the document was removed
            }
            
            doc.getUpdateLock().acquire(Lock.WRITE_LOCK);
            
            boolean useTriggers = isTriggersEnabled();
            if (CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(docUri)) {
                // we remove a collection.xconf configuration file: tell the configuration manager to
                // reload the configuration.
                useTriggers = false;
                CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
                confMgr.invalidateAll(getURI());
            }
            
            DocumentTriggersVisitor triggersVisitor = null;
            if(useTriggers) {
                triggersVisitor = getConfiguration(broker).getDocumentTriggerProxies().instantiateVisitor(broker);
                triggersVisitor.beforeDeleteDocument(broker, transaction, doc);
            }
            
            broker.removeXMLResource(transaction, doc);
            documents.remove(docUri.getRawCollectionPath());
            
            if(useTriggers) {
                triggersVisitor.afterDeleteDocument(broker, transaction, getURI().append(docUri));
            }
            
            broker.getBrokerPool().getNotificationService().notifyUpdate(doc, UpdateListener.REMOVE);
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
            if(doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI uri) throws PermissionDeniedException, LockException, TriggerException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        try {
            getLock().acquire(Lock.READ_LOCK);
            DocumentImpl doc = getDocument(broker, uri);
            
            if(doc.isLockedForWrite()) {
                throw new PermissionDeniedException("Document " + doc.getFileURI() + " is locked for write");
            }
            
            removeBinaryResource(transaction, broker, doc);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException, TriggerException {
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        if(doc == null) {
            return;  //TODO should throw an exception!!! Otherwise we dont know if the document was removed
        }
        
        try {
            broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_BINARY, doc.getFileURI());
            getLock().acquire(Lock.WRITE_LOCK);
            
            if(doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new PermissionDeniedException("document " + doc.getFileURI() + " is not a binary object");
            }
            
            if(doc.isLockedForWrite()) {
                throw new PermissionDeniedException("Document " + doc.getFileURI() + " is locked for write");
            }
            
            doc.getUpdateLock().acquire(Lock.WRITE_LOCK);

            DocumentTriggersVisitor triggersVisitor = null;
            if(isTriggersEnabled()) {
                triggersVisitor = getConfiguration(broker).getDocumentTriggerProxies().instantiateVisitor(broker);
                triggersVisitor.beforeDeleteDocument(broker, transaction, doc);
            }
            

            try {
               broker.removeBinaryResource(transaction, (BinaryDocument) doc);
            } catch (IOException ex) {
               throw new PermissionDeniedException("Cannot delete file: " + doc.getURI().toString() + ": " + ex.getMessage(), ex);
            }
            
            documents.remove(doc.getFileURI().getRawCollectionPath());
            
            if(isTriggersEnabled()) {
                triggersVisitor.afterDeleteDocument(broker, transaction, doc.getURI());
            }

        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
            doc.getUpdateLock().release(Lock.WRITE_LOCK);
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    /** Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * 
     * @param transaction
     * @param broker
     * @param info
     * @param source
     * @param privileged
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source, boolean privileged) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            @Override
            public void run() throws EXistException, SAXException {
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
                final XMLReader reader = getReader(broker, false, info.getCollectionConfig());
                info.setReader(reader, null);
                try {
                    reader.parse(source);
                } catch(final IOException e) {
                    throw new EXistException(e);
                } finally {
                    releaseReader(broker, info, reader);
                }
            }
        });
    }
    
    /** 
     * Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * 
     * @param transaction
     * @param broker
     * @param info
     * @param data
     * @param privileged
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final String data, boolean privileged) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            @Override
            public void run() throws SAXException, EXistException {
                final CollectionConfiguration colconf = info.getDocument().getCollection().getConfiguration(broker);
                final XMLReader reader = getReader(broker, false, colconf);
                info.setReader(reader, null);
                try {
                    reader.parse(new InputSource(new StringReader(data)));
                } catch(final IOException e) {
                    throw new EXistException(e);
                } finally {
                    releaseReader(broker, info, reader);
                }
            }
        });
    }

    /** 
     * Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * 
     * @param transaction
     * @param broker
     * @param info
     * @param node
     * @param privileged
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */  
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final Node node, boolean privileged) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        
        if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Permission denied to write collection: " + path);
        }
        
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            @Override
            public void run() throws EXistException, SAXException {
                info.getDOMStreamer().serialize(node, true);
            }
        });
    }

    private interface StoreBlock {
        public void run() throws EXistException, SAXException;
    }

    /** 
     * Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * 
     * @param transaction
     * @param broker
     * @param info
     * @param privileged
     * @param doParse
     * 
     * @throws EXistException
     * @throws SAXException
     */
    private void storeXMLInternal(final Txn transaction, final DBBroker broker, final IndexInfo info, final boolean privileged, final StoreBlock doParse) throws EXistException, SAXException, PermissionDeniedException {
        
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
                if(!getPermissionsNoLock().validate(broker.getSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission denied to write collection: " + path);
                }
            } else {
                // update

                final Permission oldDocPermissions = info.getOldDocPermissions();
                if(!((oldDocPermissions.getOwner().getId() != broker.getSubject().getId()) | (oldDocPermissions.validate(broker.getSubject(), Permission.WRITE)))) {
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
            doParse.run();
            broker.storeXMLResource(transaction, document);
            broker.flush();
            broker.closeDocument();
            //broker.checkTree(document);
            LOG.debug("document stored.");
        } finally {
            //This lock has been acquired in validateXMLResourceInternal()
            document.getUpdateLock().release(Lock.WRITE_LOCK);
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
        setCollectionConfigEnabled(true);
        broker.deleteObservers();
        
        if(info.isCreating()) {
            db.getDocumentTrigger().afterCreateDocument(broker, transaction, document);
        } else {
            db.getDocumentTrigger().afterUpdateDocument(broker, transaction, document);
        }
        
        if(isTriggersEnabled() && isCollectionConfigEnabled() && info.getTriggersVisitor() != null) {
            if(info.isCreating()) {
                info.getTriggersVisitor().afterCreateDocument(broker, transaction, document);
            } else {
                info.getTriggersVisitor().afterUpdateDocument(broker, transaction, document);
            }
        }
        
        db.getNotificationService().notifyUpdate(document, (info.isCreating() ? UpdateListener.ADD : UpdateListener.UPDATE));
        //Is it a collection configuration file ?
        final XmldbURI docName = document.getFileURI();
        //WARNING : there is no reason to lock the collection since setPath() is normally called in a safe way
        //TODO: *resolve* URI against CollectionConfigurationManager.CONFIG_COLLECTION_URI 
        if (getURI().startsWith(XmldbURI.CONFIG_COLLECTION_URI)
                && docName.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
            broker.sync(Sync.MAJOR_SYNC);
            CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
            if(manager != null) {
                try {
                    manager.invalidateAll(getURI());
                    manager.loadConfiguration(broker, this);
                } catch(final PermissionDeniedException pde) {
                    throw new EXistException(pde.getMessage(), pde);
                } catch(final LockException le) {
                    throw new EXistException(le.getMessage(), le);
                } catch(final CollectionConfigurationException e) { 
                    // DIZ: should this exception really been thrown? bugid=1807744
                    throw new EXistException("Error while reading new collection configuration: " + e.getMessage(), e);
                }
            }
        }
    }

    private interface ValidateBlock {
        public void run(IndexInfo info) throws SAXException, EXistException;
    }

    /** 
     * Validates an XML document and prepares it for further storage.
     * Launches prepare and postValidate triggers.
     * Since the process is dependent from the collection configuration, 
     * the collection acquires a write lock during the process.
     * 
     * @param transaction
     * @param broker
     * @param docUri   
     * @param data  
     * 
     * @return An {@link IndexInfo} with a write lock on the document. 
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return validateXMLResource(transaction, broker, docUri, new InputSource(new StringReader(data)));
    }

    /** 
     * Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * 
     * @param transaction
     * @param broker
     * @param docUri
     * @param source
     * 
     * @return An {@link IndexInfo} with a write lock on the document. 
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);
        
        return validateXMLResourceInternal(transaction, broker, docUri, colconf, new ValidateBlock() {
            @Override
            public void run(final IndexInfo info) throws SAXException, EXistException {
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
    
    private class CloseShieldReader extends Reader {
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

    /** 
     * Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * 
     * @param transaction
     * @param broker
     * @param docUri
     * @param node
     * 
     * @return An {@link IndexInfo} with a write lock on the document. 
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
    	
        return validateXMLResourceInternal(transaction, broker, docUri, getConfiguration(broker), new ValidateBlock() {
            @Override
            public void run(final IndexInfo info) throws SAXException {
                info.setDOMStreamer(new DOMStreamer());
                info.getDOMStreamer().serialize(node, true);
            }
        });
    }

    /** 
     * Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * 
     * @param transaction
     * @param broker
     * @param docUri
     * @param doValidate
     * 
     * @return An {@link IndexInfo} with a write lock on the document. 
     * 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    private IndexInfo validateXMLResourceInternal(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final CollectionConfiguration config, final ValidateBlock doValidate) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        //Make the necessary operations if we process a collection configuration document
        checkConfigurationDocument(transaction, broker, docUri);
        
        final Database db = broker.getBrokerPool();
        
        if (db.isReadOnly()) {
            throw new PermissionDeniedException("Database is read-only");
        }
        
        DocumentImpl oldDoc = null;
        boolean oldDocLocked = false;
        try {
            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_VALIDATE_DOC, docUri); 
            getLock().acquire(Lock.WRITE_LOCK);   
            
            DocumentImpl document = new DocumentImpl((BrokerPool) db, this, docUri);
            oldDoc = documents.get(docUri.getRawCollectionPath());
            checkPermissionsForAddDocument(broker, oldDoc);
            checkCollectionConflict(docUri);
            manageDocumentInformation(oldDoc, document);
            Indexer indexer = new Indexer(broker, transaction);
            
            final IndexInfo info = new IndexInfo(indexer, config);
            info.setCreating(oldDoc == null);
            info.setOldDocPermissions(oldDoc != null ? oldDoc.getPermissions() : null);
            indexer.setDocument(document, config);
            addObserversToIndexer(broker, indexer);
            indexer.setValidating(true);
            
            if(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(docUri)) {
                // we are updating collection.xconf. Notify configuration manager
                //CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
                //confMgr.invalidateAll(getURI());
                setCollectionConfigEnabled(false);
            }

            if(oldDoc == null) {
                db.getDocumentTrigger().beforeCreateDocument(broker, transaction, getURI().append(docUri));
            } else {
            	db.getDocumentTrigger().beforeUpdateDocument(broker, transaction, oldDoc);
            }

            DocumentTriggersVisitor triggersVisitor = null;
            if(isTriggersEnabled() && isCollectionConfigEnabled()) {
                triggersVisitor = getConfiguration(broker).getDocumentTriggerProxies().instantiateVisitor(broker);
                
                triggersVisitor.setOutputHandler(indexer);
                triggersVisitor.setLexicalOutputHandler(indexer);
                triggersVisitor.setValidating(true);
                
                if(oldDoc == null) {
                    triggersVisitor.beforeCreateDocument(broker, transaction, getURI().append(docUri));
                } else {
                    triggersVisitor.beforeUpdateDocument(broker, transaction, oldDoc);
                }
                
                info.setTriggersVisitor(triggersVisitor);
            }
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scanning document " + getURI().append(docUri));
            }
            doValidate.run(info);
            // new document is valid: remove old document
            if (oldDoc != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("removing old document " + oldDoc.getFileURI());
                }
                oldDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                oldDocLocked = true;
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    //TODO : use a more elaborated method ? No triggers...
                    broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
                    documents.remove(oldDoc.getFileURI().getRawCollectionPath());
                    //This lock is released in storeXMLInternal()
                    //TODO : check that we go until there to ensure the lock is released
//                    if (transaction != null)
//                    	transaction.acquireLock(document.getUpdateLock(), Lock.WRITE_LOCK);
//                	else
                    document.getUpdateLock().acquire(Lock.WRITE_LOCK);
                    
                    document.setDocId(broker.getNextResourceId(transaction, this));
                    addDocument(transaction, broker, document);
                } else {
                    //TODO : use a more elaborated method ? No triggers...
                    broker.removeXMLResource(transaction, oldDoc, false);
                    oldDoc.copyOf(document);
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
//                	transaction.acquireLock(document.getUpdateLock(), Lock.WRITE_LOCK);
//            	else
                document.getUpdateLock().acquire(Lock.WRITE_LOCK);
            	
                document.setDocId(broker.getNextResourceId(transaction, this));
                addDocument(transaction, broker, document);
            }
            indexer.setValidating(false);
            if(triggersVisitor != null) {
                triggersVisitor.setValidating(false);
            }
            return info;
        } finally {
            if (oldDoc != null && oldDocLocked) {
                oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            getLock().release(Lock.WRITE_LOCK);
            
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

    /** add observers to the indexer
     * @param broker
     * @param indexer
     */
    private void addObserversToIndexer(final DBBroker broker, final Indexer indexer) {
        broker.deleteObservers();
        if(observers != null) {
            for(int i = 0; i < observers.length; i++) {
                indexer.addObserver(observers[i]);
                broker.addObserver(observers[i]);
            }
        }
    }


    /** If an old document exists, keep information  about  the document.
     * @param broker
     * @param document
     */
    private void manageDocumentInformation(final DocumentImpl oldDoc, final DocumentImpl document) {
        DocumentMetadata metadata = new DocumentMetadata();
        if (oldDoc != null) {
            metadata = oldDoc.getMetadata();
            metadata.setCreated(oldDoc.getMetadata().getCreated());
            metadata.setLastModified(System.currentTimeMillis());
            document.setPermissions(oldDoc.getPermissions());
        } else {
        	//Account user = broker.getSubject();
                metadata.setCreated(System.currentTimeMillis());

                /*
            if(!document.getPermissions().getOwner().equals(user)) {
                document.getPermissions().setOwner(broker.getSubject(), user);
            }

            CollectionConfiguration config = getConfiguration(broker);
            if (config != null) {
                document.getPermissions().setMode(config.getDefResPermissions());
                group = config.getDefResGroup(user);
            } else {
                group = user.getPrimaryGroup();
            }

            if(!document.getPermissions().getGroup().equals(group)) {
                document.getPermissions().setGroup(broker.getSubject(), group);
            }*/
        }
        document.setMetadata(metadata);
    }

    /**
     * Check Permissions about user and document when a document is added to the databse, and throw exceptions if necessary.
     *
     * @param broker
     * @param oldDoc old Document existing in database prior to adding a new one with same name, or null if none exists
     * @throws LockException
     * @throws PermissionDeniedException
     */
    private void checkPermissionsForAddDocument(final DBBroker broker, final DocumentImpl oldDoc) throws LockException, PermissionDeniedException {
        
        // do we have execute permission on the collection?
        if(!getPermissions().validate(broker.getSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Execute permission is not granted on the Collection.");
        }
            
        if(oldDoc != null) {   
            
            /* update document */
            
            LOG.debug("Found old doc " + oldDoc.getDocId());
            
            // check if the document is locked by another user
            final Account lockUser = oldDoc.getUserLock();
            if(lockUser != null && !lockUser.equals(broker.getSubject())) {
                throw new PermissionDeniedException("The document is locked by user '" + lockUser.getName() + "'.");
            }
            
            // do we have write permission on the old document or are we the owner of the old document?
            if(!((oldDoc.getPermissions().getOwner().getId() != broker.getSubject().getId()) | (oldDoc.getPermissions().validate(broker.getSubject(), Permission.WRITE)))) {
                throw new PermissionDeniedException("A resource with the same name already exists in the target collection '" + path + "', and you do not have write access on that resource.");
            }
        } else {
            
            /* create document */
            
            if(!getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
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

    // Blob
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final byte[] data, final String mimeType) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, data, mimeType, null, null);
    }

    // Blob
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final byte[] data, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, new ByteArrayInputStream(data), mimeType, data.length, created, modified);
    }

    // Streaming
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final InputStream is, final String mimeType, final long size) throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, is, mimeType, size, null, null);
    }

    // Streaming
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        final BinaryDocument blob = new BinaryDocument(broker.getBrokerPool(), this, docUri);
        
        return addBinaryResource(transaction, broker, blob, is, mimeType, size, created, modified);
    }

    public BinaryDocument validateBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI docUri, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws PermissionDeniedException, LockException, TriggerException, IOException {
        return new BinaryDocument(broker.getBrokerPool(), this, docUri);
    }

    // Streaming
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        final Database db = broker.getBrokerPool();
        if (db.isReadOnly()) {
            throw new PermissionDeniedException("Database is read-only");
        }
        final XmldbURI docUri = blob.getFileURI();
        //TODO : move later, i.e. after the collection lock is acquired ?
        final DocumentImpl oldDoc = getDocument(broker, docUri);
        try {
            db.getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_BINARY, docUri);
            getLock().acquire(Lock.WRITE_LOCK);
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
                db.getDocumentTrigger().beforeCreateDocument(broker, transaction, blob.getURI());
            } else {
                db.getDocumentTrigger().beforeUpdateDocument(broker, transaction, oldDoc);
            }
            DocumentTriggersVisitor triggersVisitor = null;
            if (isTriggersEnabled()) {
                triggersVisitor = getConfiguration(broker).getDocumentTriggerProxies().instantiateVisitor(broker);
                if (oldDoc == null) {
                    triggersVisitor.beforeCreateDocument(broker, transaction, blob.getURI());
                } else {
                    triggersVisitor.beforeUpdateDocument(broker, transaction, oldDoc);
                }
            }
            if (oldDoc != null) {
                LOG.debug("removing old document " + oldDoc.getFileURI());
                if (oldDoc instanceof BinaryDocument) {
                    broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
                } else {
                    broker.removeXMLResource(transaction, oldDoc);
                }
            }
            broker.storeBinaryResource(transaction, blob, is);
            addDocument(transaction, broker, blob, oldDoc);
            broker.storeXMLResource(transaction, blob);
            if (oldDoc == null) {
                db.getDocumentTrigger().afterCreateDocument(broker, transaction, blob);
            } else {
                db.getDocumentTrigger().afterUpdateDocument(broker, transaction, blob);
            }
            if (isTriggersEnabled()) {
                //Strange ! What is the "if" clause for ? -pb
                if (oldDoc == null) {
                    triggersVisitor.afterCreateDocument(broker, transaction, blob);
                } else {
                    triggersVisitor.afterUpdateDocument(broker, transaction, blob);
                }
            }
            return blob;
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    public void setId(int id) {
        this.collectionId = id;
    }

    public void setPermissions(final int mode) throws LockException, PermissionDeniedException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            permissions.setMode(mode);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    @Deprecated
    public void setPermissions(final String mode) throws SyntaxException, LockException, PermissionDeniedException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            permissions.setMode(mode);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    /**
     * Set permissions for the collection.
     *
     * @param permissions
     */
    public void setPermissions(final Permission permissions) throws LockException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            this.permissions = permissions;
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    public CollectionConfiguration getConfiguration(final DBBroker broker) {
        if(!isCollectionConfigEnabled()) {
            return null;
        }
        
        final CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
        if(manager == null) {
            return null;
        }
        //Attempt to get configuration
        CollectionConfiguration configuration = null;
        try {
            //TODO: AR: if a Trigger throws CollectionConfigurationException
            //from its configure() method, is the rest of the collection 
            //configuration (indexes etc.) ignored even though they might be fine?
            configuration = manager.getConfiguration(broker, this);
            setCollectionConfigEnabled(true);
        } catch(final CollectionConfigurationException e) {
            setCollectionConfigEnabled(false);
            LOG.warn("Failed to load collection configuration for '" + getURI() + "'", e);
        }
        return configuration;
    }

    /**
     * Should the collection configuration document be enabled
     * for this collection? Called by {@link org.exist.storage.NativeBroker}
     * before doing a re-index.
     *
     * @param collectionConfigEnabled
     */
    public void setCollectionConfigEnabled(final boolean collectionConfigEnabled) {
        this.collectionConfigEnabled = collectionConfigEnabled;
    }

    public boolean isCollectionConfigEnabled() {
        return collectionConfigEnabled;
    }

    /**
     * Set the internal storage address of the collection data.
     *
     * @param addr
     */
    public void setAddress(final long addr) {
        this.address = addr;
    }

    public long getAddress() {
        return this.address;
    }

    public void setCreationTime(final long ms) {
        created = ms;
    }

    public long getCreationTime() {
        return created;
    }

    /*** TODO why do we need this? is it just for the versioning trigger?
     * If so we need to enable/disable specific triggers!
     ***/
    public void setTriggersEnabled(final boolean enabled) {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            this.triggersEnabled = enabled;
        } catch(final LockException e) {
            LOG.warn(e.getMessage(), e);
            //Ouch ! -pb
            this.triggersEnabled = enabled;
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }

    /** set user-defined Reader */
    public void setReader(final XMLReader reader){
        userReader = reader;
    }

    /** 
     * Get xml reader from readerpool and setup validation when needed.
     */
    private XMLReader getReader(final DBBroker broker, final boolean validation, final CollectionConfiguration colconfig) {
        // If user-defined Reader is set, return it;
        if (userReader != null) {
            return userReader;
        }
        // Get reader from readerpool.
        final XMLReader reader = broker.getBrokerPool().getParserPool().borrowXMLReader();
        // If Collection configuration exists (try to) get validation mode
        // and setup reader with this information.
        if (!validation) {
            XMLReaderObjectFactory.setReaderValidationMode(XMLReaderObjectFactory.VALIDATION_DISABLED, reader);
        } else if( colconfig!=null ) {
            final int mode = colconfig.getValidationMode();
            XMLReaderObjectFactory.setReaderValidationMode(mode, reader);
        }
        // Return configured reader.
        return reader;
    }

    /**
     * Reset validation mode of reader and return reader to reader pool.
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
        final int validationMode = XMLReaderObjectFactory.convertValidationMode(optionValue);
        // Restore default validation mode
        XMLReaderObjectFactory.setReaderValidationMode(validationMode, reader);
        // Return reader
        broker.getBrokerPool().getParserPool().returnXMLReader(reader);
    }

    /* (non-Javadoc)
     * @see java.util.Observable#addObserver(java.util.Observer)
     */
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
        for(int i = 0; i < observers.length; i++) {
            if(observers[i] == o) {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see java.util.Observable#deleteObservers()
     */
    @Override
    public synchronized void deleteObservers() {
        if(observers != null) {
            observers = null;
        }
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#getKey()
     */
    @Override
    public long getKey() {
        return collectionId;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#getReferenceCount()
     */
    @Override
    public int getReferenceCount() {
        return refCount;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#incReferenceCount()
     */
    @Override
    public int incReferenceCount() {
        return ++refCount;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#decReferenceCount()
     */
    @Override
    public int decReferenceCount() {
        return refCount > 0 ? --refCount : 0;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#setReferenceCount(int)
     */
    @Override
    public void setReferenceCount(final int count) {
        refCount = count;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#setTimestamp(int)
     */
    @Override
    public void setTimestamp(final int timestamp) {
        this.timestamp = timestamp;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#getTimestamp()
     */
    @Override
    public int getTimestamp() {
        return timestamp;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#release()
     */
    @Override
    public boolean sync(final boolean syncJournal) {
        return false;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#isDirty()
     */
    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append( getURI() );
        buf.append("[");
        for(final Iterator<String> i = documents.keySet().iterator(); i.hasNext(); ) {
            buf.append(i.next());
            if(i.hasNext()) {
                buf.append(", ");
            }
        }
        buf.append("]");
        return buf.toString();
    }

    /**
     * (Make private?)
     * @param broker
     */
    public IndexSpec getIndexConfiguration(final DBBroker broker) {
        final CollectionConfiguration conf = getConfiguration(broker);
        //If the collection has its own config...
        if (conf == null) {
            return broker.getIndexConfiguration();
        }
        //... otherwise return the general config (the broker's one)
        return conf.getIndexConfiguration();
    }

    public GeneralRangeIndexSpec getIndexByPathConfiguration(final DBBroker broker, final NodePath path) {
        final IndexSpec idxSpec = getIndexConfiguration(broker);
        return (idxSpec == null) ? null : idxSpec.getIndexByPath(path);
    }

    public QNameRangeIndexSpec getIndexByQNameConfiguration(final DBBroker broker, final QName qname) {
        final IndexSpec idxSpec = getIndexConfiguration(broker);
        return (idxSpec == null) ? null : idxSpec.getIndexByQName(qname);
    }

    public FulltextIndexSpec getFulltextIndexConfiguration(final DBBroker broker) {
        final IndexSpec idxSpec = getIndexConfiguration(broker);
        return (idxSpec == null) ? null : idxSpec.getFulltextIndexSpec();
    }

    /*
    public DocumentTrigger getDocumentTrigger(DBBroker broker) {
        if (triggersEnabled) {
            CollectionConfiguration config = getConfiguration(broker);
            if (config != null)
                try {
                    return config.newDocumentTrigger(broker, this);
                } catch (CollectionConfigurationException e) {
                    LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                }
        }
        return null;
    }

    public CollectionTrigger getCollectionTrigger(DBBroker broker) {
        if (triggersEnabled) {
            CollectionConfiguration config = getConfiguration(broker);
            if (config != null)
                try {
                    return config.newCollectionTrigger(broker, this);
                } catch (CollectionConfigurationException e) {
                    LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                }
        }
        return null;
    }*/
}
