/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.XMLSecurityManager;
import org.exist.storage.DBBroker;
import org.exist.storage.FulltextIndexSpec;
import org.exist.storage.GeneralRangeIndexSpec;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.QNameRangeIndexSpec;
import org.exist.storage.UpdateListener;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.lock.ReentrantReadWriteLock;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;

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
public  class Collection extends Observable implements Comparable, Cacheable
{    
    public static int LENGTH_COLLECTION_ID = 4; //sizeof int

    public static final int POOL_PARSER_THRESHOLD = 500;

    private final static int SHALLOW_SIZE = 550;

    private final static int DOCUMENT_SIZE = 450;
    
    private final static Logger LOG = Logger.getLogger(Collection.class);
    
    //private final static int VALIDATION_ENABLED = 0;
    //private final static int VALIDATION_AUTO = 1;
    //private final static int VALIDATION_DISABLED = 2;
    
    public final static int UNKNOWN_COLLECTION_ID = -1;
    
    // Internal id
    private int collectionId = UNKNOWN_COLLECTION_ID;
    
    // the documents contained in this collection
    private Map documents = new TreeMap();
    
    // the path of this collection
    private XmldbURI path;
    
    // the permissions assigned to this collection
    private Permission permissions = PermissionFactory.getPermission(0775);
    
    // stores child-collections with their storage address
    private ObjectHashSet subcollections = new ObjectHashSet(19);
    
    // Storage address of the collection in the BFile
    private long address = BFile.UNKNOWN_ADDRESS;
    
    // creation time
    private long created = 0;
    
    private Observer[] observers = null;
    
    private volatile boolean collectionConfEnabled = true;
    private boolean triggersEnabled = true;
    
    // fields required by the collections cache
    private int refCount = 0;
    private int timestamp = 0;
    
    private Lock lock = null;
    
    /** user-defined Reader */
    private XMLReader userReader = null;
    
    /** is this a temporary collection? */
    private boolean isTempCollection = false;

    public Collection(){

    }
    
    public Collection(XmldbURI path) {
        setPath(path);
        lock = new ReentrantReadWriteLock(path);
    }
    
    public void setPath(XmldbURI path) {
    	path = path.toCollectionPathURI();
    	//TODO : see if the URI resolves against DBBroker.TEMP_COLLECTION
        isTempCollection = path.getRawCollectionPath().equals(DBBroker.TEMP_COLLECTION);
        this.path=path;
    }
    
    public Lock getLock() {
        return lock;
    }
    
    /**
     *  Add a new sub-collection to the collection.
     *
     */
    public void addCollection(DBBroker broker, Collection child, boolean isNew) {
        XmldbURI childName = child.getURI().lastSegment();
        if (!subcollections.contains(childName))
            subcollections.add(childName);
        if (isNew) {
        	User user = broker.getUser();
            child.setCreationTime(System.currentTimeMillis());
        	child.permissions.setOwner(user);
            CollectionConfiguration config = getConfiguration(broker);
            String group = user.getPrimaryGroup();
            if (config != null){
            	group = config.getDefCollGroup(user);
                child.permissions.setPermissions(config.getDefCollPermissions());
            }
            child.permissions.setGroup(group);
        }
    }
    
    public boolean hasChildCollection(XmldbURI path) {
        return subcollections.contains(path);
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
    public void release(int mode) {
        getLock().release(mode);
    }
    
    /**
     * Update the specified child-collection.
     *
     * @param child
     */
    public void update(Collection child) {
        final XmldbURI childName = child.getURI().lastSegment();
        subcollections.remove(childName);
        subcollections.add(childName);
    }
    
    /**
     *  Add a document to the collection.
     *
     *@param  doc
     */
    public void addDocument(Txn transaction, DBBroker broker, DocumentImpl doc) {
        if (doc.getDocId() == DocumentImpl.UNKNOWN_DOCUMENT_ID)
            doc.setDocId(broker.getNextResourceId(transaction, this));
        documents.put(doc.getFileURI().getRawCollectionPath(), doc);
    }
    
    /**
     * Removes the document from the internal list of resources, but
     * doesn't delete the document object itself.
     *
     * @param doc
     */
    public void unlinkDocument(DocumentImpl doc) {
        documents.remove(doc.getFileURI().getRawCollectionPath());
    }
    
    /**
     *  Return an iterator over all subcollections.
     *
     * The list of subcollections is copied first, so modifications
     * via the iterator have no affect.
     *
     *@return    Description of the Return Value
     */
    public Iterator collectionIterator() {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subcollections.stableIterator();
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            return null;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public Iterator collectionIteratorNoLock() {
        return subcollections.stableIterator();
    }

    /**
     * Load all collections below this collections
     * and return them in a List.
     *
     * @return List
     */
    public List getDescendants(DBBroker broker, User user) {
        final ArrayList cl = new ArrayList(subcollections.size());
        try {
            getLock().acquire(Lock.READ_LOCK);
            Collection child;
            XmldbURI childName;
            for (Iterator i = subcollections.iterator(); i.hasNext(); ) {
                childName = (XmldbURI) i.next();
                //TODO : resolve URI !
                child = broker.getCollection(path.append(childName));
                if (permissions.validate(user, Permission.READ)) {
                    cl.add(child);
                    if (child.getChildCollectionCount() > 0)
                        cl.addAll(child.getDescendants(broker, user));
                }
            }
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        return cl;
    }

    public MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive,
                                    boolean checkPermissions) {
        return allDocs(broker, docs, recursive, checkPermissions, null);
    }

    /**
     * Retrieve all documents contained in this collections.
     *
     * If recursive is true, documents from sub-collections are
     * included.
     *
     * @param broker
     * @param docs
     * @param recursive
     * @param checkPermissions
     * @return The set of documents.
     */
    public MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive,
                                    boolean checkPermissions, LockedDocumentMap protectedDocs) {
        if (permissions.validate(broker.getUser(), Permission.READ)) {
            List subColls = null;
            try {
                // acquire a lock on the collection
                getLock().acquire(Lock.READ_LOCK);
                // add all docs in this collection to the returned set
                getDocuments(broker, docs, checkPermissions);
                // get a list of subcollection URIs. We will process them after unlocking this collection.
                // otherwise we may deadlock ourselves
                subColls = subcollections.keys();
            } catch (LockException e) {
                LOG.warn(e.getMessage(), e);
            } finally {
                getLock().release(Lock.READ_LOCK);
            }
            if (recursive && subColls != null) {
                // process the child collections
                for (int i = 0; i < subColls.size(); i++) {
                    XmldbURI childName = (XmldbURI) subColls.get(i);
                    //TODO : resolve URI !
                    Collection child = broker.openCollection(path.appendInternal(childName), Lock.NO_LOCK);
                    // a collection may have been removed in the meantime, so check first
                    if (child != null)
                        child.allDocs(broker, docs, recursive, checkPermissions, protectedDocs);
                }
            }
        }
        return docs;
    }

    public DocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive, LockedDocumentMap lockMap, int lockType) throws LockException {
        if (permissions.validate(broker.getUser(), Permission.READ)) {
            List subColls = null;
            XmldbURI uris[] = null;
            try {
                // acquire a lock on the collection
                getLock().acquire(Lock.READ_LOCK);
                // add all docs in this collection to the returned set
                getDocuments(broker, docs, lockMap, lockType);
                // get a list of subcollection URIs. We will process them after unlocking this collection.
                // otherwise we may deadlock ourselves
                subColls = subcollections.keys();
                if (subColls != null) {
                    uris = new XmldbURI[subColls.size()];
                    for (int i = 0; i < subColls.size(); i++) {
                        XmldbURI childName = (XmldbURI) subColls.get(i);
                        uris[i] = path.appendInternal(childName);
                    }
                }
            } catch (LockException e) {
                LOG.warn(e.getMessage());
                throw e;
            } finally {
                getLock().release(Lock.READ_LOCK);
            }
            if (recursive && uris != null) {
                // process the child collections
                for (int i = 0; i < uris.length; i++) {
                    //TODO : resolve URI !
                    Collection child = broker.openCollection(uris[i], Lock.NO_LOCK);
                    // a collection may have been removed in the meantime, so check first
                    if (child != null)
                        child.allDocs(broker, docs, recursive, lockMap, lockType);
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
    public DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs, boolean checkPermissions) {
        try {
            getLock().acquire(Lock.READ_LOCK);
            docs.addCollection(this);
            
            docs.addAll(broker, this, getDocumentPaths(), checkPermissions);
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        return docs;
    }

    public DocumentSet getDocumentsNoLock(DBBroker broker, MutableDocumentSet docs, boolean checkPermissions) {
        docs.addCollection(this);
        docs.addAll(broker, this, getDocumentPaths(), checkPermissions);
        return docs;
    }

    public DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs, LockedDocumentMap lockMap, int lockType) throws LockException {
        try {
            getLock().acquire(Lock.READ_LOCK);
            docs.addCollection(this);
            docs.addAll(broker, this, getDocumentPaths(), lockMap, lockType);
        } catch (LockException e) {
            throw e;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
        return docs;
    }

    private String[] getDocumentPaths() {
        String paths[] = new String[documents.size()];
        int i = 0;
        for (Iterator iter = documents.keySet().iterator(); iter.hasNext(); i++) {
            paths[i] = (String) iter.next();
        }
        return paths;
    }

    /**
     * Check if this collection may be safely removed from the
     * cache. Returns false if there are ongoing write operations,
     * i.e. one or more of the documents is locked for
     * write.
     *
     * @return A boolean value where true indicates it may be unloaded.
     */
    public boolean allowUnload() {
        for (Iterator i = documents.values().iterator(); i.hasNext(); ) {
            DocumentImpl doc = (DocumentImpl) i.next();
            if (doc.isLockedForWrite())
                return false;
        }
        return true;
//		try {
//			lock.acquire(Lock.WRITE_LOCK);
//			for (Iterator i = documents.values().iterator(); i.hasNext(); ) {
//				DocumentImpl doc = (DocumentImpl) i.next();
//				if (doc.isLockedForWrite())
//					return false;
//			}
//			return true;
//		} catch (LockException e) {
//			LOG.warn("Failed to acquire lock on collection: " + getName(), e);
//		} finally {
//			lock.release();
//		}
//		return false;
    }
    
    public int compareTo(Object obj) {
        Collection other = (Collection) obj;
        if (collectionId == other.collectionId)
            return Constants.EQUAL;
        else if (collectionId < other.collectionId)
            return Constants.INFERIOR;
        else
            return Constants.SUPERIOR;
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof Collection))
            return false;
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
     *  Return the number of child-collections managed by this
     * collection.
     *
     *@return    The childCollectionCount value
     */
    public int getChildCollectionCount() {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subcollections.size();
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            return 0;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }
    
   /**
     *  Get a child resource as identified by path. This method doesn't put
     * a lock on the document nor does it recognize locks held by other threads.
     * There's no guarantee that the document still exists when accessing it.
     *
     *@param  broker
     *@param  path  The name of the document (without collection path)
     *@return   the document
     */
    public DocumentImpl getDocument(DBBroker broker, XmldbURI path) {
        try {
            getLock().acquire(Lock.READ_LOCK);
            DocumentImpl doc = (DocumentImpl) documents.get(path.getRawCollectionPath());
            if(doc == null)
            	LOG.debug("Document " + path + " not found!");
            return doc;
        } catch (LockException e) {
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
     * @deprecated Use other method
     * @param broker
     * @param name
     * @return The document that was locked.
     * @throws LockException
     */
    public DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI name) throws LockException {
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
    public DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI uri, int lockMode)
    throws LockException {
        try {
            getLock().acquire(Lock.READ_LOCK);
            DocumentImpl doc = (DocumentImpl) documents.get(uri.getRawCollectionPath());
            if(doc != null)
            	doc.getUpdateLock().acquire(lockMode);
            return doc;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public DocumentImpl getDocumentNoLock(String rawPath) {
        return (DocumentImpl) documents.get(rawPath);
    }

    /**
     * Release any locks held on the document.
     * @deprecated Use other method
     * @param doc
     */
    public void releaseDocument(DocumentImpl doc) {
        if(doc != null) {
            doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }

    
    /**
     * Release any locks held on the document.
     *
     * @param doc
     */
    public void releaseDocument(DocumentImpl doc, int mode) {
        if(doc != null) {
            doc.getUpdateLock().release(mode);
        }
    }
    
    /**
     *  Returns the number of documents in this collection.
     *
     *@return    The documentCount value
     */
    public int getDocumentCount() {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return documents.size();
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            return 0;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public int getDocumentCountNoLock() {
        return documents.size();
    }
    
    /**
     *  Get the internal id.
     *
     *@return    The id value
     */
    public int getId() {
        return collectionId;
    }
    
    /**
     *  Get the name of this collection.
     *
     *@return    The name value
     */
    public XmldbURI getURI() {
        return path;
    }
    
    /**
     *  Returns the parent-collection.
     *
     *@return    The parent-collection or null if this
     *is the root collection.
     */
    public XmldbURI getParentURI() {
        if (path.equals(XmldbURI.ROOT_COLLECTION_URI))
            return null;
        //TODO : resolve URI against ".." !
         return path.removeLastSegment();
    }
    
    /**
     *  Gets the permissions attribute of the Collection object
     *
     *@return    The permissions value
     */
    public Permission getPermissions() {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return permissions;
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            return permissions;
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public Permission getPermissionsNoLock() {
        return permissions;
    }

    /**
     *  Check if the collection has a child document.
     *
     *@param  uri  the name (without path) of the document
     *@return A value of true when the collection has the document identified.
     */
    public boolean hasDocument(XmldbURI uri) {
        return documents.containsKey(uri.getRawCollectionPath());
    }
    
    /**
     *  Check if the collection has a sub-collection.
     *
     *@param  name  the name of the subcollection (without path).
     *@return A value of true when the subcollection exists.
     */
    public boolean hasSubcollection(XmldbURI name) {
        try {
            getLock().acquire(Lock.READ_LOCK);
            return subcollections.contains(name);
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            //TODO : ouch ! -pb
            return subcollections.contains(name);
        } finally {
            getLock().release(Lock.READ_LOCK);
        }
    }

    public boolean hasSubcollectionNoLock(XmldbURI name) {
        return subcollections.contains(name);
    }

    /**
     *  Returns an iterator on the child-documents in this collection.
     *
     *@return A iterator of all the documents in the collection.
     */
    public Iterator iterator(DBBroker broker) {
        return getDocuments(broker, new DefaultDocumentSet(), false).getDocumentIterator();
    }

    public Iterator iteratorNoLock(DBBroker broker) {
        return getDocumentsNoLock(broker, new DefaultDocumentSet(), false).getDocumentIterator();
    }

    /**
     * Read collection contents from the stream.
     *
     * @param istream
     * @throws IOException
     */
    public void read(DBBroker broker, VariableByteInput istream) throws IOException {
        collectionId = istream.readInt();
        final int collLen = istream.readInt();
        subcollections = new ObjectHashSet(collLen == 0 ? 19 : collLen);
        for (int i = 0; i < collLen; i++)
            subcollections.add(XmldbURI.create(istream.readUTF()));
        final int uid = istream.readInt();
        final int gid = istream.readInt();
        final int perm = istream.readInt();
        created = istream.readLong();
        
        final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        if (secman == null) {
            //TODO : load default permissions ? -pb
            permissions.setOwner(SecurityManager.DBA_USER);
            permissions.setGroup(SecurityManager.DBA_GROUP);
        } else {
            permissions.setOwner(secman.getUser(uid));
            Group group = secman.getGroup(gid);
            if (group != null)
                permissions.setGroup(group.getName());
        }
        ///TODO : why this mask ? -pb
        permissions.setPermissions(perm & 0777);
        broker.getCollectionResources(this);
    }
    
    /**
     *  Remove the specified sub-collection.
     *
     *@param  name  Description of the Parameter
     */
    public void removeCollection(XmldbURI name) throws LockException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            subcollections.remove(name);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     *  Remove the specified document from the collection.
     *
     *@param  transaction
     *@param  broker
     *@param  docUri
     */
    public void removeXMLResource(Txn transaction, DBBroker broker, XmldbURI docUri)
    throws PermissionDeniedException, TriggerException, LockException {
    	DocumentImpl doc = null;
    	try {
            broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_XML, docUri);

	    	//Doh ! READ lock ?
	        getLock().acquire(Lock.READ_LOCK);        
	        
	        doc = (DocumentImpl) documents.get(docUri.getRawCollectionPath());
	        if (doc == null)
	            return; //TODO should throw an exception!!! Otherwise we dont know if the document was removed
	        doc.getUpdateLock().acquire(Lock.WRITE_LOCK);
	        
	        if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
	            throw new PermissionDeniedException(
	                    "Write access to collection denied; user=" + broker.getUser().getName());
	        if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
	            throw new PermissionDeniedException("Permission to remove document denied");
	        
	        DocumentTrigger trigger = null;
	        if (!CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(docUri)) {
	            if (triggersEnabled) {
	                CollectionConfiguration config = getConfiguration(broker);
	                if (config != null)
                        try {
                            trigger = (DocumentTrigger) config.newTrigger(Trigger.REMOVE_DOCUMENT_EVENT, broker, this);
                        } catch (CollectionConfigurationException e) {
                            LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                        }
                }
	        } else {
	            // we remove a collection.xconf configuration file: tell the configuration manager to
	            // reload the configuration.
	            CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
	            confMgr.invalidateAll(getURI());
	        }
	        
	        if (trigger != null) {
	            trigger.prepare(Trigger.REMOVE_DOCUMENT_EVENT, broker, transaction,
	                    getURI().append(docUri), doc);
	        }
	        
	        broker.removeXMLResource(transaction, doc);
            documents.remove(docUri.getRawCollectionPath());
	        
	        if (trigger != null) {
	            trigger.finish(Trigger.REMOVE_DOCUMENT_EVENT, broker, transaction, getURI().append(docUri), null);
	        }
	        
	        broker.getBrokerPool().getNotificationService().notifyUpdate(doc, UpdateListener.REMOVE);
    	} finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
    		if (doc != null)
    			doc.getUpdateLock().release(Lock.WRITE_LOCK);
    		//Doh ! A READ lock ?
    		getLock().release(Lock.READ_LOCK);
    	}
    }
    
    public void removeBinaryResource(Txn transaction, DBBroker broker, XmldbURI uri)
    throws PermissionDeniedException, LockException, TriggerException {
    	try {
	        getLock().acquire(Lock.WRITE_LOCK);
	     
	        DocumentImpl doc = getDocument(broker, uri);
	        if(doc.isLockedForWrite())
	            throw new PermissionDeniedException("Document " + doc.getFileURI() +
	                    " is locked for write");
	        if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
	            throw new PermissionDeniedException(
	                    "write access to collection denied; user=" + broker.getUser().getName());
	        if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
	            throw new PermissionDeniedException("permission to remove document denied");
	        
	        removeBinaryResource(transaction, broker, doc);
	        
    	} finally {
    		getLock().release(Lock.WRITE_LOCK);
    	}

    }
    
    public void removeBinaryResource(Txn transaction, DBBroker broker, DocumentImpl doc)
    throws PermissionDeniedException, LockException, TriggerException {
        
        if (doc == null)
            return;  //TODO should throw an exception!!! Otherwise we dont know if the document was removed
        
        try {
            broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_BINARY, doc.getFileURI());
            getLock().acquire(Lock.WRITE_LOCK);
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new PermissionDeniedException("document " + doc.getFileURI()
                + " is not a binary object");
            if(doc.isLockedForWrite())
                throw new PermissionDeniedException("Document " + doc.getFileURI() +
                        " is locked for write");
            if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
                throw new PermissionDeniedException(
                        "write access to collection denied; user=" + broker.getUser().getName());
            if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
                throw new PermissionDeniedException("permission to remove document denied");
            
            DocumentTrigger trigger = null;
            if (triggersEnabled) {
                CollectionConfiguration config = getConfiguration(broker);
                if (config != null) {
                    try {
                        trigger = (DocumentTrigger) config.newTrigger(Trigger.REMOVE_DOCUMENT_EVENT, broker, this);
                    } catch (CollectionConfigurationException e) {
                        LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                    }
                }
            }
            
            if (trigger != null)
                trigger.prepare(Trigger.REMOVE_DOCUMENT_EVENT, broker, transaction, doc.getURI(), doc);
            
            try {
               broker.removeBinaryResource(transaction, (BinaryDocument) doc);
            } catch (IOException ex) {
               throw new PermissionDeniedException("Cannot delete file.");
            }
            documents.remove(doc.getFileURI().getRawCollectionPath());
            
            if (trigger != null) {
                trigger.finish(Trigger.REMOVE_DOCUMENT_EVENT, broker, transaction, doc.getURI(), null);
            }
        } finally {
            broker.getBrokerPool().getProcessMonitor().endJob();
       		getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    /** Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * @param transaction
     * @param broker
     * @param info
     * @param source
     * @param privileged
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    public void store(Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source, boolean privileged)
    throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            public void run() throws EXistException, SAXException {
                try {
                    final InputStream is = source.getByteStream();
                    if (is != null && is.markSupported())
                        is.reset();
                    else {
                        final Reader cs = source.getCharacterStream();
                        if (cs != null && cs.markSupported())
                            cs.reset();
                    }
                } catch (IOException e) {
                    // mark is not supported: exception is expected, do nothing
                }

                XMLReader reader = getReader(broker, false, info.getCollectionConfig());

                info.setReader(reader, null);
                try {
                    reader.parse(source);
                } catch (IOException e) {
                    throw new EXistException(e);
                } finally {
                    releaseReader(broker, info, reader);
                }
            }
        });
    }
    
    /** Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * @param transaction
     * @param broker
     * @param info
     * @param data
     * @param privileged
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    public void store(Txn transaction, final DBBroker broker, final IndexInfo info, final String data, boolean privileged)
    throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            public void run() throws SAXException, EXistException {
                
                CollectionConfiguration colconf 
                        = info.getDocument().getCollection().getConfiguration(broker);
                XMLReader reader = getReader(broker, false, colconf);

                info.setReader(reader, null);
                try {
                    reader.parse(new InputSource(new StringReader(data)));
                } catch (IOException e) {
                    throw new EXistException(e);
                } finally {
                    releaseReader(broker, info, reader);
                }
            }
        });
    }
    
    /** Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * @param transaction
     * @param broker
     * @param info
     * @param node
     * @param privileged
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */  
    public void store(Txn transaction, DBBroker broker, final IndexInfo info, final Node node, boolean privileged)
    throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        storeXMLInternal(transaction, broker, info, privileged, new StoreBlock() {
            public void run() throws EXistException, SAXException {
                info.getDOMStreamer().serialize(node, true);
            }
        });
    }
    
    private interface StoreBlock {
        public void run() throws EXistException, SAXException;
    }
    
    /** Stores an XML document in the database. {@link #validateXMLResourceInternal(org.exist.storage.txn.Txn,
     * org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, CollectionConfiguration, org.exist.collections.Collection.ValidateBlock)} 
     * should have been called previously in order to acquire a write lock for the document. Launches the finish trigger.
     * @param transaction
     * @param broker
     * @param info
     * @param privileged
     * @param doParse
     * @throws EXistException
     * @throws SAXException
     */
    private void storeXMLInternal(Txn transaction, DBBroker broker, IndexInfo info, boolean privileged, StoreBlock doParse) throws EXistException, SAXException {
        DocumentImpl document = info.getIndexer().getDocument();
        LOG.debug("storing document " + document.getDocId() + " ...");
        //Sanity check
    	if (!document.getUpdateLock().isLockedForWrite()) {
    		LOG.warn("document is not locked for write !");
    	}
        try {
            broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_DOC, document.getFileURI());
            doParse.run();
            
            broker.storeXMLResource(transaction, document);
            broker.flush();
            broker.closeDocument();
            
//			broker.checkTree(document);
            LOG.debug("document stored.");
            // if we are running in privileged mode (e.g. backup/restore), notify the SecurityManager about changes
            if (getURI().equals(XmldbURI.SYSTEM_COLLECTION_URI) 
                && document.getFileURI().equals(XMLSecurityManager.ACL_FILE_URI)
                && privileged == false) {
                // inform the security manager that system data has changed
                LOG.debug("users.xml changed");
                broker.getBrokerPool().reloadSecurityManager(broker);
            }
        } finally {
        	//This lock has been acquired in validateXMLResourceInternal()
            document.getUpdateLock().release(Lock.WRITE_LOCK);
            broker.getBrokerPool().getProcessMonitor().endJob();
        }
        
        collectionConfEnabled = true;
        broker.deleteObservers();
        info.finishTrigger(broker, transaction, document.getURI(), document);
        broker.getBrokerPool().getNotificationService().notifyUpdate(document,
                (info.getEvent() == Trigger.UPDATE_DOCUMENT_EVENT ? UpdateListener.UPDATE : UpdateListener.ADD));
        
        //Is it a collection configuration file ?
        XmldbURI docName = document.getFileURI();
        //WARNING : there is no reason to lock the collection since setPath() is normally called in a safe way
        //TODO: *resolve* URI against CollectionConfigurationManager.CONFIG_COLLECTION_URI 
        if (getURI().startsWith(XmldbURI.CONFIG_COLLECTION_URI)
        		&& docName.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
            broker.sync(Sync.MAJOR_SYNC);

            CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
            if (manager != null) {
                try {
                    manager.invalidateAll(getURI());
                    manager.loadConfiguration(broker, this);
                } catch (CollectionConfigurationException e) { 
                    // DIZ: should this exception really been thrown? bugid=1807744
                    throw new EXistException("Error while reading new collection configuration: " + e.getMessage(), e);
                }
            }
        }
    }
    
    private interface ValidateBlock {
        public void run(IndexInfo info) throws SAXException, EXistException;
    }

    /** Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * @param transaction
     * @param broker
     * @param docUri   
     * @param data  
     * @return An {@link IndexInfo} with a write lock on the document. 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI docUri, String data)
    throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException, IOException {
        return validateXMLResource(transaction, broker, docUri, new InputSource(new StringReader(data)));
    }
    
    /** Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * @param transaction
     * @param broker
     * @param docUri
     * @param source
     * @return An {@link IndexInfo} with a write lock on the document. 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(Txn transaction, final DBBroker broker, XmldbURI docUri, final InputSource source)
    throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException, IOException {
        final CollectionConfiguration colconf = getConfiguration(broker);
        return validateXMLResourceInternal(transaction, broker, docUri, colconf, new ValidateBlock() {
            public void run(IndexInfo info) throws SAXException, EXistException {
                XMLReader reader = getReader(broker, true, colconf);

                info.setReader(reader, null);
                try {
                    reader.parse(source);
                } catch (SAXException e) {
                    throw new SAXException("The XML parser reported a problem: " + e.getMessage(), e);
                } catch (IOException e) {
                    throw new EXistException(e);
                } finally {
                    releaseReader(broker, info, reader);
                }
            }
        });
    }
    
    /** Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * @param transaction
     * @param broker
     * @param docUri
     * @param node
     * @return An {@link IndexInfo} with a write lock on the document. 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */    
    public IndexInfo validateXMLResource(Txn transaction, final DBBroker broker, XmldbURI docUri, final Node node)
    throws EXistException, PermissionDeniedException, TriggerException,
            SAXException, LockException, IOException {
        return validateXMLResourceInternal(transaction, broker, docUri, getConfiguration(broker), new ValidateBlock() {
            public void run(IndexInfo info) throws SAXException {
                info.setDOMStreamer(new DOMStreamer());
                info.getDOMStreamer().serialize(node, true);
            }
        });
    }
    
    /** Validates an XML document et prepares it for further storage. Launches prepare and postValidate triggers.
     * Since the process is dependant from the collection configuration, the collection acquires a write lock during the process.
     * @param transaction
     * @param broker
     * @param docUri
     * @param doValidate
     * @return An {@link IndexInfo} with a write lock on the document. 
     * @throws EXistException
     * @throws PermissionDeniedException
     * @throws TriggerException
     * @throws SAXException
     * @throws LockException
     */
    private IndexInfo validateXMLResourceInternal(Txn transaction, DBBroker broker, XmldbURI docUri, CollectionConfiguration config, ValidateBlock doValidate)
    throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
    	//Make the necessary operations if we process a collection configuration document
        checkConfigurationDocument(transaction, broker, docUri);

        if (broker.isReadOnly()) throw new PermissionDeniedException("Database is read-only");     
        DocumentImpl oldDoc = null;
        boolean oldDocLocked = false;
        try {
			broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_VALIDATE_DOC, docUri); 
            getLock().acquire(Lock.WRITE_LOCK);   
            DocumentImpl document = new DocumentImpl(broker.getBrokerPool(), this, docUri);
            
            oldDoc = (DocumentImpl) documents.get(docUri.getRawCollectionPath());
            
            checkPermissions(transaction, broker, oldDoc);
            manageDocumentInformation(broker, oldDoc, document );
            
            Indexer indexer = new Indexer(broker, transaction);
            IndexInfo info = new IndexInfo(indexer, config);
            indexer.setDocument(document, config);
            addObserversToIndexer(broker, indexer);
            indexer.setValidating(true);
            
            // if !triggersEnabled, setupTriggers will return null anyway, so no need to check
            info.setTrigger(
                    setupTriggers(broker, docUri, oldDoc != null, config),
                    oldDoc == null ? Trigger.STORE_DOCUMENT_EVENT : Trigger.UPDATE_DOCUMENT_EVENT);
            
            info.prepareTrigger(broker, transaction, getURI().append(docUri), oldDoc);
            
            LOG.debug("Scanning document " + getURI().append(docUri));
            doValidate.run(info);
            
            // new document is valid: remove old document
            if (oldDoc != null) {
                LOG.debug("removing old document " + oldDoc.getFileURI());
                oldDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                oldDocLocked = true;
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    //TODO : use a more elaborated method ? No triggers...
                    broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
                    documents.remove(oldDoc.getFileURI().getRawCollectionPath());
                    //This lock is released in storeXMLInternal()
                	//TODO : check that we go until there to ensure the lock is released
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
                LOG.debug("removed old document " + oldDoc.getFileURI());
            } else {
            	//This lock is released in storeXMLInternal()
            	//TODO : check that we go until there to ensure the lock is released
            	document.getUpdateLock().acquire(Lock.WRITE_LOCK);
                document.setDocId(broker.getNextResourceId(transaction, this));
                addDocument(transaction, broker, document);
            }
            
            indexer.setValidating(false);
            info.postValidateTrigger();
            return info;
        } finally {
            if (oldDocLocked) 
            	oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
            getLock().release(Lock.WRITE_LOCK);
			broker.getBrokerPool().getProcessMonitor().endJob();
        }
    }
    
    private void checkConfigurationDocument(Txn transaction, DBBroker broker, XmldbURI docUri) throws EXistException, PermissionDeniedException,
    	IOException {
    	//Is it a collection configuration file ?
    	//TODO : use XmldbURI.resolve() !
        if (!getURI().startsWith(XmldbURI.CONFIG_COLLECTION_URI))
        	return;
        if (!docUri.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI))
        	return;
        //Allow just one configuration document per collection
        //TODO : do not throw the exception if a system property allows several ones -pb
    	for(Iterator i = iterator(broker); i.hasNext(); ) {
	        DocumentImpl confDoc = (DocumentImpl) i.next();
	        XmldbURI currentConfDocName = confDoc.getFileURI();
	        if(currentConfDocName != null && !currentConfDocName.equals(docUri)) {
	        	throw new EXistException("Could not store configuration '" + docUri + "': A configuration document with a different name ("
	        			+ currentConfDocName + ") already exists in this collection (" + getURI() + ")");
	        }
        }
    	
//        broker.saveCollection(transaction, this);
//        CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
//        if(confMgr != null)
//            try {
//                confMgr.reload(broker, this);
//            } catch (CollectionConfigurationException e) {
//                throw new EXistException("An error occurred while reloading the updated collection configuration: " + e.getMessage(), e);
//            }
    }
    
    /** add observers to the indexer
     * @param broker
     * @param indexer
     */
    private void addObserversToIndexer(DBBroker broker, Indexer indexer) {
        broker.deleteObservers();
        if (observers != null) {
            for (int i = 0; i < observers.length; i++) {
                indexer.addObserver(observers[i]);
                broker.addObserver(observers[i]);
            }
        }
    }
    
    /** If an old document exists, keep information  about  the document.
     * @param broker
     * @param document
     */
    private void manageDocumentInformation(DBBroker broker, DocumentImpl oldDoc,
            DocumentImpl document) {
    	DocumentMetadata metadata = new DocumentMetadata();
        if (oldDoc != null) {
            metadata = oldDoc.getMetadata();
            metadata.setCreated(oldDoc.getMetadata().getCreated());
            metadata.setLastModified(System.currentTimeMillis());
            document.setPermissions(oldDoc.getPermissions());
        } else {
        	User user = broker.getUser();
            metadata.setCreated(System.currentTimeMillis());
            document.getPermissions().setOwner(user);
            String group; 
            CollectionConfiguration config = getConfiguration(broker);
            if (config != null) {
                document.setPermissions(config.getDefResPermissions());
                group = config.getDefResGroup(user);
            } else {
            	group = user.getPrimaryGroup();
            }
            document.getPermissions().setGroup(group);
        }
        document.setMetadata(metadata);
    }
    
    /**
     * Check Permissions about user and document, and throw exceptions if necessary.
     *
     * @param broker
     * @param oldDoc old Document existing in database prior to adding a new one with same name.
     * @throws LockException
     * @throws PermissionDeniedException
     */
    private void checkPermissions(Txn transaction, DBBroker broker, DocumentImpl oldDoc) throws LockException, PermissionDeniedException {
        if (oldDoc != null) {
            
            LOG.debug("Found old doc " + oldDoc.getDocId());
            // check if the document is locked by another user
            User lockUser = oldDoc.getUserLock();
            if(lockUser != null && !lockUser.equals(broker.getUser()))
                throw new PermissionDeniedException("The document is locked by user " +
                        lockUser.getName());
            
            
            // do we have permissions for update?
            if (!oldDoc.getPermissions().validate(broker.getUser(),
                    Permission.UPDATE))
                throw new PermissionDeniedException(
                        "Document exists and update is not allowed");
            if (!(getPermissions().validate(broker.getUser(), Permission.UPDATE) ||
                    getPermissions().validate(broker.getUser(), Permission.WRITE)))
                throw new PermissionDeniedException(
                        "Document exists and update is not allowed for the collection");
            // do we have write permissions?
        } else if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
            throw new PermissionDeniedException(
                    "User '" + broker.getUser().getName() + "' not allowed to write to collection '" + getURI() + "'");
    }
    
    private DocumentTrigger setupTriggers(DBBroker broker, XmldbURI docUri, boolean update, CollectionConfiguration config) {
        
        //TODO : is this the right place for such a task ? -pb
        if (CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI.equals(docUri)) {
            // we are updating collection.xconf. Notify configuration manager
//            CollectionConfigurationManager confMgr = broker.getBrokerPool().getConfigurationManager();
//            confMgr.invalidateAll(getURI());
            collectionConfEnabled = false;
            return null;
        }
        if (!triggersEnabled)
            return null;
        
        if (config == null)
            return null;
        
        DocumentTrigger trigger = null;
        try {
            if (update)
                trigger = (DocumentTrigger) config.newTrigger(Trigger.UPDATE_DOCUMENT_EVENT, broker, this);
            else
                trigger = (DocumentTrigger) config.newTrigger(Trigger.STORE_DOCUMENT_EVENT, broker, this);
        } catch (CollectionConfigurationException e) {
            LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
        }

        if(trigger == null)
            return null;
        
        if (update)
            LOG.debug("Using update trigger '" + trigger.getClass().getName() + "'");
        else
            LOG.debug("Using store trigger '" + trigger.getClass().getName() + "'");
        
        return trigger;
    }
    
    // Blob
    public BinaryDocument addBinaryResource(Txn transaction, DBBroker broker,
                XmldbURI docUri, byte[] data, String mimeType)
            throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, data, mimeType, null, null);
    }
    
    // Blob
    public BinaryDocument addBinaryResource(Txn transaction, DBBroker broker,
    		XmldbURI docUri, byte[] data, String mimeType, Date created, Date modified)
            throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, 
                                new ByteArrayInputStream(data), mimeType, data.length, created, modified);
    }
    
    // Streaming
    public BinaryDocument addBinaryResource(Txn transaction, DBBroker broker,
    		XmldbURI docUri, InputStream is, String mimeType, int size)
            throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
        return addBinaryResource(transaction, broker, docUri, is, mimeType, size, null, null);
    }
    
    // Streaming
    public BinaryDocument addBinaryResource(Txn transaction, DBBroker broker,
    		XmldbURI docUri, InputStream is, String mimeType, int size, Date created, Date modified)
            throws EXistException, PermissionDeniedException, LockException, TriggerException,IOException {
    	
        if (broker.isReadOnly())
            throw new PermissionDeniedException("Database is read-only");
        
        BinaryDocument blob = new BinaryDocument(broker.getBrokerPool(), this, docUri);
        
        //TODO : move later, i.e. after the collection lock is acquired ?
        DocumentImpl oldDoc = getDocument(broker, docUri);
        
        try {
            broker.getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_STORE_BINARY, docUri);
        	getLock().acquire(Lock.WRITE_LOCK);
	        
	        checkPermissions(transaction, broker, oldDoc);
	        DocumentTrigger trigger = null;
	        int event = 0;

	        if (triggersEnabled) {
	            CollectionConfiguration config = getConfiguration(broker);
	            if (config != null) {
	                event = oldDoc != null ? Trigger.UPDATE_DOCUMENT_EVENT : Trigger.STORE_DOCUMENT_EVENT;
                        try {
                            trigger = (DocumentTrigger) config.newTrigger(event, broker, this);
                        } catch (CollectionConfigurationException e) {
                            LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                        }
                        if (trigger != null) {
	                    trigger.prepare(event, broker, transaction, getURI().append(docUri), oldDoc);
	                }
	            }
	        }
	        
	        manageDocumentInformation(broker, oldDoc, blob );
	        DocumentMetadata metadata = blob.getMetadata();
	        metadata.setMimeType(mimeType == null ? MimeType.BINARY_TYPE.getName() : mimeType);
	        
	        if (oldDoc != null) {
	            LOG.debug("removing old document " + oldDoc.getFileURI());
	            if (oldDoc instanceof BinaryDocument)
	                broker.removeBinaryResource(transaction, (BinaryDocument) oldDoc);
	            else
	                broker.removeXMLResource(transaction, oldDoc);
	        }
	        
	        if(created != null)
	            metadata.setCreated(created.getTime());
	        
	        if(modified != null)
	        	metadata.setLastModified(modified.getTime());
	        
	        blob.setContentLength(size);
	        broker.storeBinaryResource(transaction, blob, is);
	        addDocument(transaction, broker, blob);
	        
	        broker.storeXMLResource(transaction, blob);
	        
                /*
                if (triggersEnabled) {
	            CollectionConfiguration config = getConfiguration(broker);
	            if (config != null) {
	                event = oldDoc != null ? Trigger.UPDATE_DOCUMENT_EVENT : Trigger.STORE_DOCUMENT_EVENT;
                    try {
                        trigger = (DocumentTrigger) config.newTrigger(event, broker, this);
                    } catch (CollectionConfigurationException e) {
                        LOG.debug("An error occurred while initializing a trigger for collection " + getURI() + ": " + e.getMessage(), e);
                    }
                    if (trigger != null) {
	                    trigger.prepare(event, broker, transaction, blob.getURI(), blob);
	                }
	            }
	        }*/
	        
	        
                // This is no longer needed as the dom.dbx isn't used
	        //broker.closeDocument();
	        
	        if (trigger != null) {
	            trigger.finish(event, broker, transaction, blob.getURI(), blob);
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
    
    public void setPermissions(int mode) throws LockException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            permissions.setPermissions(mode);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    public void setPermissions(String mode) throws SyntaxException, LockException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            permissions.setPermissions(mode);
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     * Set permissions for the collection.
     *
     * @param permissions
     */
    public void setPermissions(Permission permissions) throws LockException {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            this.permissions = permissions;
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     * Write collection contents to stream.
     *
     * @param ostream
     * @throws IOException
     */
    public void write(DBBroker broker, VariableByteOutputStream ostream) throws IOException {
        ostream.writeInt(collectionId);
        ostream.writeInt(subcollections.size());
        XmldbURI childColl;
        for (Iterator i = subcollections.iterator(); i.hasNext(); ) {
            childColl = (XmldbURI)i.next();
            ostream.writeUTF(childColl.toString());
        }
        SecurityManager secman = broker.getBrokerPool().getSecurityManager();
        if (secman == null) {
            ostream.writeInt(1);
            ostream.writeInt(1);
        } else {
            User user = secman.getUser(permissions.getOwner());
            Group group = secman.getGroup(permissions.getOwnerGroup());
            if (user==null) {
               throw new IllegalStateException("The user "+permissions.getOwner()+" for the collection cannot be found.");
            }
            if (group == null)
                group = secman.getGroup(SecurityManager.GUEST_GROUP);
            ostream.writeInt(user.getUID());
            ostream.writeInt(group.getId());
        }
        ostream.writeInt(permissions.getPermissions());
        ostream.writeLong(created);
    }

    public CollectionConfiguration getConfiguration(DBBroker broker) {
        if (!collectionConfEnabled)
            return null;

        //System collection has no configuration
        if (DBBroker.SYSTEM_COLLECTION.equals(getURI().getRawCollectionPath()))
            return null;

        CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
        if (manager == null)
            return null;

        //Attempt to get configuration
        CollectionConfiguration configuration = null;
        try {
            //TODO: AR: if a Trigger throws CollectionConfigurationException from its configure() method, is the rest of the collection configurartion (indexes etc.) ignored even though they might be fine?
            configuration = manager.getConfiguration(broker, this);
            collectionConfEnabled = true;
        } catch (CollectionConfigurationException e) {
            collectionConfEnabled = false;
            LOG.warn("Failed to load collection configuration for '" + getURI() + "'", e);
        }
//        LOG.debug("Loaded configuration for collection:  " + getURI());
        return configuration;
    }
    
    /**
     * Should the collection configuration document be enabled
     * for this collection? Called by {@link org.exist.storage.NativeBroker}
     * before doing a reindex.
     *
     * @param enabled
     */
    public void setConfigEnabled(boolean enabled) {
        collectionConfEnabled = enabled;
    }
    
    /**
     * Set the internal storage address of the collection data.
     *
     * @param addr
     */
    public void setAddress(long addr) {
        this.address = addr;
    }
    
    public long getAddress() {
        return this.address;
    }
    
    public void setCreationTime(long ms) {
        created = ms;
    }
    
    public long getCreationTime() {
        return created;
    }
    
    public void setTriggersEnabled(boolean enabled) {
        try {
            getLock().acquire(Lock.WRITE_LOCK);
            this.triggersEnabled = enabled;
        } catch (LockException e) {
            LOG.warn(e.getMessage(), e);
            //Ouch ! -pb
            this.triggersEnabled = enabled;
        } finally {
            getLock().release(Lock.WRITE_LOCK);
        }
    }
    
    
    /** set user-defined Reader */
    public void setReader(XMLReader reader){
        userReader = reader;
    }
    
//    /** 
//     * If user-defined Reader is set, return it; otherwise return JAXP 
//     * default XMLReader configured by eXist. 
//     */
//    private XMLReader getReader(DBBroker broker) throws EXistException,
//            SAXException {
//        
//        if(userReader != null){
//            return userReader;
//        }
//
//        return broker.getBrokerPool().getParserPool().borrowXMLReader();
//    }
    
    /** 
     * Get xml reader from readerpool and setup validation when needed.
     */
    private XMLReader getReader(DBBroker broker, boolean validation, CollectionConfiguration colconfig) throws EXistException,
            SAXException {
        
        // If user-defined Reader is set, return it;
        if(userReader != null){
            return userReader;
        }
        
        // Get reader from readerpool.
        XMLReader reader= broker.getBrokerPool().getParserPool().borrowXMLReader();

        // If Collection configuration exists (try to) get validation mode
        // and setup reader with this information.
        if (!validation)
            XMLReaderObjectFactory.setReaderValidationMode(XMLReaderObjectFactory.VALIDATION_DISABLED, reader);
        else if( colconfig!=null ) {
            int mode=colconfig.getValidationMode();
            XMLReaderObjectFactory.setReaderValidationMode(mode, reader);
        }

        // Return configured reader.
        return reader;       
    }
    
    /**
     * Reset validation mode of reader and return reader to reader pool.
     */    
    private void releaseReader(DBBroker broker, IndexInfo info, XMLReader reader) {
        if(userReader != null){
            return;
        }
        if (info.getIndexer().getDocSize() > POOL_PARSER_THRESHOLD)
            return;
        // Get validation mode from static configuration
        Configuration config = broker.getConfiguration();
        String optionValue = (String) config.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE);
        int validationMode = XMLReaderObjectFactory.convertValidationMode(optionValue);

        // Restore default validation mode
        XMLReaderObjectFactory.setReaderValidationMode(validationMode, reader);
                   
        // Return reader
        broker.getBrokerPool().getParserPool().returnXMLReader(reader);
    }
    
    /* (non-Javadoc)
     * @see java.util.Observable#addObserver(java.util.Observer)
     */
    public void addObserver(Observer o) {
        if (hasObserver(o)) return;
        if (observers == null) {
            observers = new Observer[1];
            observers[0] = o;
        } else {
            Observer n[] = new Observer[observers.length + 1];
            System.arraycopy(observers, 0, n, 0, observers.length);
            n[observers.length] = o;
            observers = n;
        }
    }
    
    private boolean hasObserver(Observer o) {
        if (observers == null)
            return false;
        for (int i = 0; i < observers.length; i++) {
            if (observers[i] == o)
                return true;
        }
        return false;
    }
    
        /* (non-Javadoc)
         * @see java.util.Observable#deleteObservers()
         */
    public void deleteObservers() {
        if (observers != null)
            observers = null;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#getKey()
         */
    public long getKey() {
        return collectionId;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#getReferenceCount()
         */
    public int getReferenceCount() {
        return refCount;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#incReferenceCount()
         */
    public int incReferenceCount() {
        return ++refCount;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#decReferenceCount()
         */
    public int decReferenceCount() {
        return refCount > 0 ? --refCount : 0;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#setReferenceCount(int)
         */
    public void setReferenceCount(int count) {
        refCount = count;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#setTimestamp(int)
         */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#getTimestamp()
         */
    public int getTimestamp() {
        return timestamp;
    }
    
        /* (non-Javadoc)
         * @see org.exist.storage.cache.Cacheable#release()
         */
    public boolean sync(boolean syncJournal) {
        return false;
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#isDirty()
     */
    public boolean isDirty() {
        return false;
    }
    
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append( getURI() );
        buf.append("[");
        for(Iterator i = documents.keySet().iterator(); i.hasNext(); ) {
            buf.append(i.next());
            if(i.hasNext())
                buf.append(", ");
        }
        buf.append("]");
        return buf.toString();
    }
    
    /**
     * (Make private?)
     * @param broker
     */
    public IndexSpec getIndexConfiguration(DBBroker broker) {
        CollectionConfiguration conf = getConfiguration(broker);
        //If the collection has its own config...
        if (conf == null) {
            return broker.getIndexConfiguration();
            //... otherwise return the general config (the broker's one)
        } else {
            return conf.getIndexConfiguration();
        }
    }
    
    public GeneralRangeIndexSpec getIndexByPathConfiguration(DBBroker broker, NodePath path) {
    	IndexSpec idxSpec = getIndexConfiguration(broker);
    	return (idxSpec == null) ? null : idxSpec.getIndexByPath(path);
    }    
    
    public QNameRangeIndexSpec getIndexByQNameConfiguration(DBBroker broker, QName qname) {
    	IndexSpec idxSpec = getIndexConfiguration(broker);
    	return (idxSpec == null) ? null : idxSpec.getIndexByQName(qname);
    }    
    
    public FulltextIndexSpec getFulltextIndexConfiguration(DBBroker broker) {
    	IndexSpec idxSpec = getIndexConfiguration(broker);
    	return (idxSpec == null) ? null : idxSpec.getFulltextIndexSpec();
    }
}
