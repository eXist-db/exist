/*
 *  Collection.java - eXist Open Source Native XML Database
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
 * $Id$
 */
package org.exist.collections;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.store.CollectionStore;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReentrantReadWriteLock;
import org.exist.util.SyntaxException;
import org.exist.util.hashtable.ObjectHashSet;
import org.exist.util.serializer.DOMStreamer;
import org.w3c.dom.Node;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * This class represents a collection in the database. A collection maintains a list of 
 * sub-collections and documents, and provides the methods to store/remove resources.
 * 
 * Collections are shared between {@link org.exist.storage.DBBroker} instances. The caller
 * is responsible to lock/unlock the collection. Call {@link DBBroker#openCollection(String, int)}  
 * to get a collection with a read or write lock and {@link #release()} to release the lock.
 *  
 * @author wolf
 */
public final class Collection extends Observable

implements Comparable, EntityResolver, Cacheable {

	private final static Logger LOG = Logger.getLogger(Collection.class);

	public final static String COLLECTION_CONFIG_FILE = "collection.xconf";

	private final static int VALIDATION_ENABLED = 0;
	private final static int VALIDATION_AUTO = 1;
	private final static int VALIDATION_DISABLED = 2;

	// the unique internal id to identify this collection
	private short collectionId = -1;

	// the documents contained in this collection
	private Map documents = new TreeMap();

	private boolean reloadRequired = false;
	
	// the name of this collection
	private String name;

	// the permissions assigned to this collection
	private Permission permissions = new Permission(0755);

	// stores child-collections with their storage address
	private ObjectHashSet subcollections = new ObjectHashSet(19);

	// temporary field for the storage address
	private long address = -1;

	// creation time
	private long created = 0;

	private CatalogResolver resolver;

	private List observers = null;

	private CollectionConfiguration configuration = null;
	private boolean collectionConfEnabled = true;
	private boolean triggersEnabled = true;

	// fields required by the collections cache
	private int refCount = 0;
	private int timestamp = 0;
	
	// the collection store where this collections is stored.
	private CollectionStore db;
	
	private Lock lock = null;
	
	/** user-defined Reader */
	private XMLReader userReader = null;
	
	public Collection(CollectionStore db, String name) {
		this.name = name;
		this.db = db;
		lock = new ReentrantReadWriteLock(name);
	}

	public void setName(String name) {
	    this.name = name;
	}
	
	public Lock getLock() {
		return lock;
	}
	
	/**
	 *  Add a new sub-collection to the collection.
	 *
	 *@param  name
	 */
	public void addCollection(Collection child) {
		final int p = child.name.lastIndexOf('/') + 1;
		final String childName = child.name.substring(p);
		if (!subcollections.contains(childName))
			subcollections.add(childName);
	}

	public boolean hasChildCollection(String name) {
		return subcollections.contains(name);
	}
	
	/**
	 * Closes the collection, i.e. releases the lock held by 
	 * the current thread. This is a shortcut for getLock().release().
	 */
	public void release() {
//		LOG.debug("releasing lock on " + name);
		lock.release();
	}
	
	/**
	 * Update the specified child-collection.
	 * 
	 * @param child
	 */
	public void update(Collection child) {
		final int p = child.name.lastIndexOf('/') + 1;
		final String childName = child.name.substring(p);
		subcollections.remove(childName);
		subcollections.add(childName);
	}

	/**
	 *  Add a document to the collection.
	 *
	 *@param  doc 
	 */
	public void addDocument(DBBroker broker, DocumentImpl doc) {
		if (doc.getDocId() < 0)
			doc.setDocId(broker.getNextDocId(this));
		documents.put(doc.getFileName(), doc);
	}

	/**
	 * Adds a document to the collection, but doesn't keep the document
	 * object in memory. The collection will be reloaded the first time the
	 * new document is accessed. Using this method helps to keep memory
	 * consumption low when loading many documents in a batch.
	 * 
	 * @param broker
	 * @param doc
	 */
	public void addDocumentLink(DBBroker broker, DocumentImpl doc) {
	    if (doc.getDocId() < 0)
			doc.setDocId(broker.getNextDocId(this));
		documents.put(doc.getFileName(), null);
		reloadRequired = true;
	}
	
	/**
	 * Removes the document from the internal list of resources, but
	 * doesn't delete the document object itself.
	 * 
	 * @param doc
	 */
	public void unlinkDocument(DocumentImpl doc) {
	    documents.remove(doc.getFileName());
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
			lock.acquire(Lock.READ_LOCK);
			return subcollections.stableIterator();
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return null;
		} finally {
			lock.release();
		}
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
			lock.acquire(Lock.READ_LOCK);
			Collection child;
			String childName;
			for (Iterator i = subcollections.iterator(); i.hasNext(); ) {
				childName = (String) i.next();
				child = broker.getCollection(name + '/' + childName);
				if (permissions.validate(user, Permission.READ)) {
					cl.add(child);
					if (child.getChildCollectionCount() > 0)
						cl.addAll(child.getDescendants(broker, user));
				}
			}
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
		} finally {
			lock.release();
		}
		return cl;
	}

	/**
	 * Retrieve all documents contained in this collections.
	 * 
	 * If recursive is true, documents from sub-collections are
	 * included.
	 * 
	 * @param user
	 * @param recursive
	 * @return
	 */
	public DocumentSet allDocs(DBBroker broker, DocumentSet docs,
			boolean recursive, boolean checkPermissions) {
		if (permissions.validate(broker.getUser(), Permission.READ)) {
			CollectionCache cache = broker.getBrokerPool().getCollectionsCache();
			synchronized (cache) {
				getDocuments(broker, docs, checkPermissions);
				if (recursive)
					allDocs(broker, docs, checkPermissions);
			}
		}
		return docs;
	}

	private DocumentSet allDocs(DBBroker broker, DocumentSet docs, boolean checkPermissions) {
		try {
			lock.acquire(Lock.READ_LOCK);
			Collection child;
			String childName;
			long addr;
			for (Iterator i = subcollections.iterator(); i.hasNext(); ) {
				childName = (String) i.next();
				child = broker.getCollection(name + '/' + childName);
				if(child == null) {
					LOG.warn("child collection " + childName + " not found. Skipping ...");
					// we always check if we have permissions to read the child collection
				} else if (child.permissions.validate(broker.getUser(), Permission.READ)) {
					child.getDocuments(broker, docs, checkPermissions);
					if (child.getChildCollectionCount() > 0)
						child.allDocs(broker, docs, checkPermissions);
				}
			}
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
		} finally {
			lock.release();
		}
		return docs;
	}

	/**
	 * Add all documents to the specified document set.
	 *  
	 * @param docs
	 */
	public DocumentSet getDocuments(DBBroker broker, DocumentSet docs, boolean checkPermissions) {
		try {
			lock.acquire(Lock.READ_LOCK);
			if(reloadRequired) {
			    broker.reloadCollection(this);
			    reloadRequired = false;
			}
			docs.addCollection(this);
			docs.addAll(broker, documents.values(), checkPermissions);
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
		} finally {
			lock.release();
		}
		return docs;
	}

	/**
	 * Check if this collection may be safely removed from the
	 * cache. Returns false if there are ongoing write operations,
	 * i.e. one or more of the documents is locked for
	 * write.
	 * 
	 * @return
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
			return 0;
		else if (collectionId < other.collectionId)
			return -1;
		else
			return 1;
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Collection))
			return false;
		return ((Collection) obj).collectionId == collectionId;
	}

	/**
	 *  Return the number of child-collections managed by this
	 * collection.
	 *
	 *@return    The childCollectionCount value
	 */
	public int getChildCollectionCount() {
		try {
			lock.acquire(Lock.READ_LOCK);
			return subcollections.size();
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return 0;
		} finally {
			lock.release();
		}
	}

	/**
	 *  Get a child resource as identified by path. This method doesn't put
	 * a lock on the document nor does it recognize locks held by other threads.
	 * There's no guarantee that the document still exists when accessing it.
	 * 
	 *@param  name  The name of the document (without collection path)
	 *@return   the document
	 */
	public DocumentImpl getDocument(DBBroker broker, String name) {
		try {
			lock.acquire(Lock.READ_LOCK);
			if(reloadRequired) {
			    broker.reloadCollection(this);
			    reloadRequired = false;
			}
			DocumentImpl doc = (DocumentImpl) documents.get(name);
			if(doc == null)
			    LOG.debug("Document " + name + " not found!");
			return doc;
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return null;
		} finally {
			lock.release();
		}
	}
	
	/**
	 * Retrieve a child resource after putting a read lock on it. With this method,
	 * access to the received document object is safe. 
	 * 
	 * @param broker
	 * @param name
	 * @return
	 * @throws LockException
	 */
	public DocumentImpl getDocumentWithLock(DBBroker broker, String name) 
	throws LockException {
	    try {
	        lock.acquire(Lock.READ_LOCK);
	        if(reloadRequired) {
			    broker.reloadCollection(this);
			    reloadRequired = false;
			}
	        DocumentImpl doc = (DocumentImpl) documents.get(name);
	        if(doc == null)
	        	return null;
	        Lock updateLock = doc.getUpdateLock();
	        updateLock.acquire(Lock.READ_LOCK);
	        return doc;
        } finally {
	        lock.release();
	    }
	}

	/**
	 * Retrieve a child resource after putting a read lock on it. With this method,
	 * access to the received document object is safe. 
	 * 
	 * @param broker
	 * @param name
	 * @return
	 * @throws LockException
	 */
	public DocumentImpl getDocumentWithLock(DBBroker broker, String name, int lockMode) 
	throws LockException {
	    try {
	        lock.acquire(Lock.READ_LOCK);
	        if(reloadRequired) {
			    broker.reloadCollection(this);
			    reloadRequired = false;
			}
	        DocumentImpl doc = (DocumentImpl) documents.get(name);
	        if(doc == null)
	        	return null;
	        Lock updateLock = doc.getUpdateLock();
	        updateLock.acquire(lockMode);
	        return doc;
        } finally {
	        lock.release();
	    }
	}
	
	/**
	 * Release any locks held on the document.
	 * 
	 * @param doc
	 */
	public void releaseDocument(DocumentImpl doc) {
	    if(doc != null) {
	        doc.getUpdateLock().release(Lock.READ_LOCK);
	    }
	}
	
	/**
	 *  Returns the number of documents in this collection.
	 *
	 *@return    The documentCount value
	 */
	public int getDocumentCount() {
		try {
			lock.acquire(Lock.READ_LOCK);
			return documents.size();
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return 0;
		} finally {
			lock.release();
		}
	}
	
	/**
	 *  Get the internal id.
	 *
	 *@return    The id value
	 */
	public short getId() {
		return collectionId;
	}

	/**
	 *  Get the name of this collection.
	 *
	 *@return    The name value
	 */
	public String getName() {
		return name;
	}

	/**
	 *  Returns the parent-collection.
	 *
	 *@return    The parent-collection or null if this
	 *is the root collection.
	 */
	public String getParentPath() {
		if (name.equals("/db"))
			return null;
		String parent = (name.lastIndexOf("/") < 1 ? "/db" : name.substring(0,
				name.lastIndexOf("/")));
		return parent;
	}

	/**
	 *  Gets the permissions attribute of the Collection object
	 *
	 *@return    The permissions value
	 */
	public Permission getPermissions() {
		try {
			lock.acquire(Lock.READ_LOCK);
			return permissions;
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return permissions;
		} finally {
			lock.release();
		}
	}

	/**
	 *  Check if the collection has a child document.
	 *
	 *@param  name  the name (without path) of the document
	 *@return  
	 */
	public boolean hasDocument(String name) {
		return documents.containsKey(name);
	}

	/**
	 *  Check if the collection has a sub-collection.
	 *
	 *@param  name  the name of the subcollection (without path).
	 *@return  
	 */
	public boolean hasSubcollection(String name) {
		try {
			lock.acquire(Lock.READ_LOCK);
			return subcollections.contains(name);
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			return subcollections.contains(name);
		} finally {
			lock.release();
		}
	}

	/**
	 *  Returns an iterator on the child-documents in this collection.
	 *
	 *@return
	 */
	public Iterator iterator(DBBroker broker) {
		return getDocuments(broker, new DocumentSet(), false).iterator();
	}
	
	/**
	 * Read collection contents from the stream.
	 * 
	 * @param istream
	 * @throws IOException
	 */
	public void read(DBBroker broker, VariableByteInput istream)
			throws IOException {
		collectionId = istream.readShort();
		final int collLen = istream.readInt();
		String sub;
		subcollections = new ObjectHashSet(collLen);
		for (int i = 0; i < collLen; i++)
			subcollections.add(istream.readUTF());

		final SecurityManager secman = broker.getBrokerPool()
				.getSecurityManager();
		final int uid = istream.readInt();
		final int gid = istream.readInt();
		final int perm = (istream.readInt() & 0777);
		if (secman == null) {
			permissions.setOwner(SecurityManager.DBA_USER);
			permissions.setGroup(SecurityManager.DBA_GROUP);
		} else {
			permissions.setOwner(secman.getUser(uid));
			permissions.setGroup(secman.getGroup(gid).getName());
		}
		permissions.setPermissions(perm);
		created = istream.readLong();
		DocumentImpl doc;
		byte resourceType;
		try {
			while (istream.available() > 0) {
				resourceType = istream.readByte();
				switch (resourceType) {
					case DocumentImpl.XML_FILE :
						doc = new DocumentImpl(broker, this);
						break;
					case DocumentImpl.BINARY_FILE :
						doc = new BinaryDocument(broker, this);
						break;
					case -1 :
						return; // EOF found
					default :
						LOG.warn("unknown resource type: " + resourceType);
						throw new IOException(
								"unable to determine resource type while reading collection "
										+ getName());
				}
				doc.read(istream);
				addDocument(broker, doc);
			}
		} catch (EOFException e) {
		}
	}

	/**
	 *  Remove the specified sub-collection.
	 *
	 *@param  name  Description of the Parameter
	 */
	public void removeCollection(String name) throws LockException {
		try {
			lock.acquire(Lock.WRITE_LOCK);
			subcollections.remove(name);
		} finally {
			lock.release();
		}
	}
	
	/**
	 *  Remove the specified document from the collection.
	 *
	 *@param  name
	 */
	public void removeDocument(DBBroker broker, String docname)
			throws PermissionDeniedException, TriggerException, LockException {
		try {
			lock.acquire(Lock.READ_LOCK);
			Trigger trigger = null;
			if (!docname.equals(COLLECTION_CONFIG_FILE)) {
				if (triggersEnabled) {
					CollectionConfiguration config = getConfiguration(broker);
					if (config != null)
						trigger = config
								.getTrigger(Trigger.REMOVE_DOCUMENT_EVENT);
				}
			} else
				configuration = null;
			DocumentImpl doc = getDocument(broker, docname);
			if (doc == null)
				return;
			if(doc.isLockedForWrite())
				throw new PermissionDeniedException("Document " + doc.getFileName() + 
					" is locked for write");
			if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
				throw new PermissionDeniedException(
						"Write access to collection denied; user=" + broker.getUser().getName());
			if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
				throw new PermissionDeniedException("Permission to remove document denied");
			if (trigger != null && triggersEnabled) {
				trigger.prepare(Trigger.REMOVE_DOCUMENT_EVENT, broker, docname,
						doc);
			}
			broker.removeDocument(doc);
			documents.remove(docname);
			broker.saveCollection(this);
			if (trigger != null && triggersEnabled) {
				trigger.finish(Trigger.REMOVE_DOCUMENT_EVENT, broker, docname,
						doc);
			}

		} finally {
			lock.release();
		}
	}

	public void removeBinaryResource(DBBroker broker,
			String docname) throws PermissionDeniedException, LockException {
		try {
			lock.acquire(Lock.WRITE_LOCK);
			DocumentImpl doc = getDocument(broker, docname);
			if(doc.isLockedForWrite())
				throw new PermissionDeniedException("Document " + doc.getFileName() + 
						" is locked for write");
			if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
				throw new PermissionDeniedException(
						"write access to collection denied; user=" + broker.getUser().getName());
			if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
				throw new PermissionDeniedException("permission to remove document denied");
			removeBinaryResource(broker, doc);
		} finally {
			lock.release();
		}
	}

	public void removeBinaryResource(DBBroker broker,
			DocumentImpl doc) throws PermissionDeniedException, LockException {
		if (doc == null)
			return;
		if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
			throw new PermissionDeniedException("document " + doc.getFileName()
					+ " is not a binary object");
		if(doc.isLockedForWrite())
			throw new PermissionDeniedException("Document " + doc.getFileName() + 
					" is locked for write");
		if (!getPermissions().validate(broker.getUser(), Permission.WRITE))
			throw new PermissionDeniedException(
					"write access to collection denied; user=" + broker.getUser().getName());
		if (!doc.getPermissions().validate(broker.getUser(), Permission.WRITE))
			throw new PermissionDeniedException("permission to remove document denied");
		try {
			lock.acquire(Lock.WRITE_LOCK);
			broker.removeBinaryResource((BinaryDocument) doc);
			documents.remove(doc.getFileName());
			broker.saveCollection(this);
		} finally {
			lock.release();
		}
	}

	public IndexInfo validate(DBBroker broker, String name, InputSource source) 
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		try {
			lock.acquire(Lock.WRITE_LOCK);
			XMLReader currentReader = getReader(broker);
			DocumentImpl oldDoc = (DocumentImpl)documents.get(name);
			DocumentImpl document = new DocumentImpl(broker, name, this);
			// first pass: parse the document to determine tree structure
			return determineTreeStructure(broker, name, document, oldDoc, currentReader, source);
		} finally {
			lock.release();
		}
	}
	
	public void store(DBBroker broker, IndexInfo info, InputSource source, boolean privileged)
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
		// reset the input source
		try {
			final InputStream is = source.getByteStream();
			if (is != null)
				is.reset();
			else {
				final Reader cs = source.getCharacterStream();
				if (cs != null)
					cs.reset();
			}
		} catch (IOException e) {
			LOG.debug("could not reset input source", e);
		}
		// second pass: store the document
		Indexer indexer = info.getIndexer();
		DocumentImpl document = indexer.getDocument();
		LOG.debug("storing document " + document.getDocId() + "; " + document.getFileName() + " ...");
		try {
			try {
				info.getReader().parse(source);
			} catch (IOException e) {
				throw new EXistException(e);
			}
			if(!hasDocument(document.getFileName())) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
//			broker.checkTree(document);
			LOG.debug("document stored.");
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
			releaseReader(broker, info.getReader());
		}
		broker.deleteObservers();
		return;
	}
	
	public IndexInfo validate(DBBroker broker, String name, String data)
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		InputSource source;
		try {
			lock.acquire(Lock.WRITE_LOCK);
			source = new InputSource(new StringReader(data));
			XMLReader currentReader = getReader(broker);
			if(currentReader == null)
				throw new EXistException("No reader!");
			DocumentImpl oldDoc = (DocumentImpl)documents.get(name);
			DocumentImpl document = new DocumentImpl(broker, name, this);
			// first pass: parse the document to determine tree structure
			return determineTreeStructure(broker, name, document, oldDoc, currentReader, source);
		} finally {
			lock.release();
		}
	}
	
	public void store(DBBroker broker, IndexInfo info, String data, boolean privileged)
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
		InputSource source = new InputSource(new StringReader(data));

		// second pass: store the document
		Indexer indexer = info.getIndexer();
		DocumentImpl document = indexer.getDocument();
		LOG.debug("storing document " + document.getDocId() + " ...");
		try {
			try {
				info.getReader().parse(source);
			} catch (IOException e) {
				throw new EXistException(e);
			}

			if(!hasDocument(document.getFileName())) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
//			broker.checkTree(document);
			LOG.debug("document stored.");
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
			releaseReader(broker, info.getReader());
		}
		broker.deleteObservers();
		return;
	}
	
	public IndexInfo validate(DBBroker broker, String name, Node node) 
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		DocumentImpl document, oldDoc = null;
		try {
			lock.acquire(Lock.WRITE_LOCK);
			oldDoc = getDocument(broker, name);
			document = new DocumentImpl(broker, name,	this);
		
			checkPermissions(broker, name, oldDoc);
			
			manageDocumentInformation(broker, name, oldDoc, document );
			
			Trigger trigger = setupTriggers(broker, name, oldDoc);
			
			Indexer indexer = new Indexer(broker);
			IndexInfo info = new IndexInfo(indexer);
			indexer.setDocument(document);

			addObserversToIndexer(broker, indexer);
			indexer.setValidating(true);
			DOMStreamer streamer = new DOMStreamer();
			info.setDOMStreamer(streamer);
			if (trigger != null && triggersEnabled) {
				streamer.setContentHandler(trigger.getInputHandler());
				streamer.setLexicalHandler(trigger.getLexicalInputHandler());
				trigger.setOutputHandler(indexer);
				trigger.setValidating(true);
				// prepare the trigger
				trigger.prepare(oldDoc == null
						? Trigger.STORE_DOCUMENT_EVENT
						: Trigger.UPDATE_DOCUMENT_EVENT, broker, name, oldDoc);
			} else {
				streamer.setContentHandler(indexer);
				streamer.setLexicalHandler(indexer);
			}

			// first pass: parse the document to determine tree structure
			LOG.debug("validating document " + name);
			streamer.serialize(node, true);
			document.setMaxDepth(document.getMaxDepth() + 1);
			document.calculateTreeLevelStartPoints();
			// new document is valid: remove old document 
			if (oldDoc != null) {
				LOG.debug("removing old document " + oldDoc.getFileName());
				if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
					broker.removeBinaryResource((BinaryDocument) oldDoc);
				else
					broker.removeDocument(oldDoc, false);
				oldDoc.copyOf(document);
				indexer.setDocumentObject(oldDoc);
				document = oldDoc;
			} else {
			    document.getUpdateLock().acquire(Lock.WRITE_LOCK);
			    document.setDocId(broker.getNextDocId(this));
			}

			indexer.setValidating(false);
			if (trigger != null)
				trigger.setValidating(false);
			return info;
		} catch(EXistException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(SAXException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(PermissionDeniedException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(TriggerException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} finally {
			lock.release();
		}
	}
	
	public void store(DBBroker broker, IndexInfo info, Node node, boolean privileged)
	throws EXistException, PermissionDeniedException, TriggerException,
	SAXException, LockException {
//		 second pass: store the document
		Indexer indexer = info.getIndexer();
		DocumentImpl document = indexer.getDocument();
		LOG.debug("storing document " + document.getDocId() + " ...");
		DOMStreamer streamer = info.getDOMStreamer();
		try {
			streamer.serialize(node, true);

			if(!hasDocument(document.getFileName())) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
//			broker.checkTree(document);
			LOG.debug("document stored.");
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
			releaseReader(broker, info.getReader());
		}
		broker.deleteObservers();
		return;
	}
	
	public DocumentImpl addDocument(DBBroker broker, String name, String data)
			throws EXistException, PermissionDeniedException, TriggerException,
			SAXException, LockException {
		return addDocument(broker, name, data, false);
	}

	public DocumentImpl addDocument(DBBroker broker, String name, String data,
			boolean privileged) throws EXistException,
			PermissionDeniedException, TriggerException, SAXException,
			LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");

		DocumentImpl document, oldDoc = null;
		XMLReader reader;
		InputSource source;
		try {
			lock.acquire(Lock.WRITE_LOCK);
			source = new InputSource(new StringReader(data));
			document = new DocumentImpl(broker, name, this);
			reader = getReader(broker);
			oldDoc = (DocumentImpl) documents.get(name);
			
			// first pass: parse the document to determine tree structure
			IndexInfo info = determineTreeStructure(broker, name, document, oldDoc, reader, source);
			document = info.getIndexer().getDocument();
		} finally {
			lock.release();
		}
		
		// reset the input source
		source = new InputSource(new StringReader(data));

		// second pass: store the document
		LOG.debug("storing document " + document.getDocId() + " ...");
		try {
			try {
				reader.parse(source);
			} catch (IOException e) {
				throw new EXistException(e);
			}

			if(oldDoc == null) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
//			broker.checkTree(document);
			LOG.debug("document stored.");
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
		}
		broker.deleteObservers();
		return document;
	}

	/**
	 * @param broker
	 * @param name
	 * @param document
	 * @param oldDoc
	 * @param reader
	 * @param source
	 * @return
	 * @throws LockException
	 * @throws EXistException
	 * @throws SAXException
	 * @throws PermissionDeniedException
	 * @throws TriggerException
	 */
	private IndexInfo determineTreeStructure(DBBroker broker, String name, DocumentImpl document, DocumentImpl oldDoc, XMLReader reader, InputSource source) throws LockException, EXistException, SAXException, PermissionDeniedException, TriggerException {
		try {
			checkPermissions(broker, name, oldDoc);
			
			manageDocumentInformation(broker, name, oldDoc, document );
			
			Trigger trigger = setupTriggers(broker, name, oldDoc);
			Indexer indexer = new Indexer(broker);
			IndexInfo info = new IndexInfo(indexer);
			indexer.setDocument(document);

			addObserversToIndexer(broker, indexer);
			prepareSAXParser(broker, name, oldDoc, trigger, info, reader );

			// first pass: parse the document to determine tree structure
			LOG.debug("validating document " + name);
			try {
				reader.parse(source);
			} catch (IOException e) {
				throw new EXistException(e);
			}
			document.setMaxDepth(document.getMaxDepth() + 1);
			document.calculateTreeLevelStartPoints();
			
			// new document is valid: remove old document
			if (oldDoc != null) {
				LOG.debug("removing old document " + oldDoc.getFileName());
				if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
					broker.removeBinaryResource((BinaryDocument) oldDoc);
				else {
//					broker.checkTree(oldDoc);
					broker.removeDocument(oldDoc, false);
				}
				// we continue to use the old document object and just replace its contents
				oldDoc.copyOf(document);
				indexer.setDocumentObject(oldDoc);
				document = oldDoc;
			} else {
			    document.getUpdateLock().acquire(Lock.WRITE_LOCK);
			    document.setDocId(broker.getNextDocId(this));
			}
			indexer.setValidating(false);
			if (trigger != null)
				trigger.setValidating(false);
			return info;
		} catch(EXistException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(SAXException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(PermissionDeniedException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(TriggerException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		}
	}

	/** prepare the SAX parser
	 * @param broker
	 * @param name
	 * @param oldDoc
	 * @param trigger
	 * @param indexer
	 * @param reader the real source of the XML data
	 * @throws EXistException
	 * @throws SAXException
	 * @throws SAXNotRecognizedException
	 * @throws SAXNotSupportedException
	 * @throws TriggerException
	 */
	private void prepareSAXParser(DBBroker broker, String name, DocumentImpl oldDoc, 
			Trigger trigger, IndexInfo info, XMLReader reader) throws EXistException, SAXException, SAXNotRecognizedException, SAXNotSupportedException, TriggerException {
		//XMLReader reader;
		Indexer indexer = info.getIndexer();
		indexer.setValidating(true);
		// reader = getReader(broker);
		reader.setEntityResolver(this);

		if (trigger != null && triggersEnabled) {
			reader.setProperty(
					"http://xml.org/sax/properties/lexical-handler",
					trigger.getLexicalInputHandler());
			reader.setContentHandler(trigger.getInputHandler());
			trigger.setOutputHandler(indexer);
			trigger.setLexicalOutputHandler(indexer);
			trigger.setValidating(true);
			// prepare the trigger
			trigger.prepare(oldDoc == null
					? Trigger.STORE_DOCUMENT_EVENT
					: Trigger.UPDATE_DOCUMENT_EVENT, broker, name, oldDoc);
		} else {
			reader
			.setProperty(
					"http://xml.org/sax/properties/lexical-handler",
					indexer);
			reader.setContentHandler(indexer);
		}
		reader.setErrorHandler(indexer);
		info.setReader(reader);
		//return reader;
	}

	/** add observers to the indexer
	 * @param broker
	 * @param indexer
	 */
	private void addObserversToIndexer(DBBroker broker, Indexer indexer) {
		Observer observer;
		broker.deleteObservers();
		if (observers != null) {
			for (Iterator i = observers.iterator(); i.hasNext(); ) {
				observer = (Observer) i.next();
				indexer.addObserver(observer);
				broker.addObserver(observer);
			}
		}
	}

	/** If an old document exists, keep information  about  the document.
	 * @param broker
	 * @param name
	 * @param oldDoc
	 * @param document
	 */
	private void manageDocumentInformation(DBBroker broker, String name, DocumentImpl oldDoc,
			DocumentImpl document ) {
		if (oldDoc != null) {
			document.setCreated(oldDoc.getCreated());
			document.setLastModified(System.currentTimeMillis());
			document.setPermissions(oldDoc.getPermissions());
		} else {
			document.setCreated(System.currentTimeMillis());
			document.getPermissions().setOwner(broker.getUser());
			document.getPermissions().setGroup(
					broker.getUser().getPrimaryGroup());
		}
	}

	/** 
	 * Check Permissions about user and document, and throw exceptions if necessary.
	 * 
	 * @param broker
	 * @param name
	 * @param oldDoc old Document existing in database prior to adding a new one with same name.
	 * @throws LockException
	 * @throws PermissionDeniedException
	 */
	private void checkPermissions(DBBroker broker, String name, DocumentImpl oldDoc) throws LockException, PermissionDeniedException {
		if (oldDoc != null) {

			LOG.debug("Found old doc " + oldDoc.getDocId());
			// check if the document is locked by another user
			User lockUser = oldDoc.getUserLock();
			if(lockUser != null && !lockUser.equals(broker.getUser()))
				throw new PermissionDeniedException("The document is locked by user " +
						lockUser.getName());
			
			// check if the document is currently being changed by someone else
			oldDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
			
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
		} else if (!getPermissions().validate(broker.getUser(),
				Permission.WRITE))
			throw new PermissionDeniedException(
					"Not allowed to write to collection " + getName());
	}
	
	public DocumentImpl addDocument(DBBroker broker, String name,
			InputSource source) throws EXistException, LockException,
			PermissionDeniedException, TriggerException, SAXException {
		return addDocument(broker, name, source, false);
	}

	public DocumentImpl addDocument(DBBroker broker, String name,
			InputSource source, boolean privileged) throws EXistException,
			PermissionDeniedException, SAXException, TriggerException,
			LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		DocumentImpl document = null, oldDoc = null;
		XMLReader reader;
		try {
			lock.acquire(Lock.WRITE_LOCK);
			oldDoc = getDocument(broker, name);
			document = new DocumentImpl(broker, name,	this);
			reader = getReader(broker);
	
			// first pass: parse the document to determine tree structure
			IndexInfo info = determineTreeStructure(broker, name, document, oldDoc, reader, source);
			document = info.getIndexer().getDocument();
		} finally {
			lock.release();
		}
		
		// reset the input source
		try {
			final InputStream is = source.getByteStream();
			if (is != null)
				is.reset();
			else {
				final Reader cs = source.getCharacterStream();
				if (cs != null)
					cs.reset();
			}
		} catch (IOException e) {
			LOG.debug("could not reset input source", e);
		}

		// second pass: store the document
		LOG.debug("storing document ...");
		try {
			try {
				reader.parse(source);
			} catch (IOException e) {
				throw new EXistException(e);
			}

			if(oldDoc == null) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
			
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
		}
		broker.deleteObservers();
		return document;
	}

	public DocumentImpl addDocument(DBBroker broker, String name, Node node)
			throws EXistException, PermissionDeniedException, TriggerException,
			SAXException, LockException {
		return addDocument(broker, name, node, false);
	}

	public DocumentImpl addDocument(DBBroker broker, String name, Node node,
			boolean privileged) throws EXistException, LockException,
			PermissionDeniedException, TriggerException, SAXException {
		Indexer indexer = new Indexer(broker);
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		DocumentImpl document, oldDoc = null;
		DOMStreamer streamer;
		try {
			lock.acquire(Lock.WRITE_LOCK);
			oldDoc = getDocument(broker, name);
			document = new DocumentImpl(broker, name,	this);
		
			checkPermissions(broker, name, oldDoc);
			
			manageDocumentInformation(broker, name, oldDoc, document );
			
			Trigger trigger = setupTriggers(broker, name, oldDoc);
			indexer.setDocument(document);

			addObserversToIndexer(broker, indexer);
			indexer.setValidating(true);
			streamer = new DOMStreamer();
			if (trigger != null && triggersEnabled) {
				streamer.setContentHandler(trigger.getInputHandler());
				streamer.setLexicalHandler(trigger.getLexicalInputHandler());
				trigger.setOutputHandler(indexer);
				trigger.setValidating(true);
				// prepare the trigger
				trigger.prepare(oldDoc == null
						? Trigger.STORE_DOCUMENT_EVENT
						: Trigger.UPDATE_DOCUMENT_EVENT, broker, name, oldDoc);
			} else {
				streamer.setContentHandler(indexer);
				streamer.setLexicalHandler(indexer);
			}

			// first pass: parse the document to determine tree structure
			LOG.debug("validating document " + name);
			streamer.serialize(node, true);
			document.setMaxDepth(document.getMaxDepth() + 1);//ddddddddddddddddddd
			document.calculateTreeLevelStartPoints();
			// new document is valid: remove old document 
			if (oldDoc != null) {
				LOG.debug("removing old document " + oldDoc.getFileName());
				if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
					broker.removeBinaryResource((BinaryDocument) oldDoc);
				else
					broker.removeDocument(oldDoc, false);
				oldDoc.copyOf(document);
				indexer.setDocumentObject(oldDoc);
				document = oldDoc;
			} else {
			    document.getUpdateLock().acquire(Lock.WRITE_LOCK);
			    document.setDocId(broker.getNextDocId(this));
			}

			indexer.setValidating(false);
			if (trigger != null)
				trigger.setValidating(false);
		} catch(EXistException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(SAXException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(PermissionDeniedException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} catch(TriggerException e) {
		    if(oldDoc != null) oldDoc.getUpdateLock().release(Lock.WRITE_LOCK);
		    throw e;
		} finally {
			lock.release();
		}//ffffffffffffffffffffffffffffffffffffffffffffffff
		
		try {
			// second pass: store the document
		    if(LOG.isDebugEnabled())
		        LOG.debug("storing document " + document.getFileName());
			streamer.serialize(node, true);
	
			if(oldDoc == null) {
				addDocument(broker, document);
				broker.addDocument(this, document);
			} else {
				broker.updateDocument(document);
			}
			broker.closeDocument();
			broker.flush();
			// if we are running in privileged mode (e.g. backup/restore)
			// notify the SecurityManager about changes
			if (getName().equals(SecurityManager.SYSTEM) && document.getFileName().equals(SecurityManager.ACL_FILE)
			        && privileged == false) {
			    // inform the security manager that system data has changed
			    if(LOG.isDebugEnabled())
			        LOG.debug("users.xml changed");
			    broker.getBrokerPool().reloadSecurityManager(broker);
			}
		} finally {
			document.getUpdateLock().release(Lock.WRITE_LOCK);
		}
		broker.deleteObservers();
		return document;
	}

	/**
	 * @param broker
	 * @param name
	 * @param oldDoc
	 * @return
	 */
	private Trigger setupTriggers(DBBroker broker, String name, DocumentImpl oldDoc) {
		Trigger trigger = null;
		if (name.equals(COLLECTION_CONFIG_FILE)) {
		    // set configuration to null if we are updating collection.xconf
			configuration = null;
			collectionConfEnabled = false;
		} else {
		    collectionConfEnabled = true;
			if (triggersEnabled) {
				if (triggersEnabled) {
					CollectionConfiguration config = getConfiguration(broker);
					if (config != null) {
						if (oldDoc == null)
							trigger = config
									.getTrigger(Trigger.STORE_DOCUMENT_EVENT);
						else
							trigger = config
									.getTrigger(Trigger.UPDATE_DOCUMENT_EVENT);
					}
				}
			}
		}
		return trigger;
	}

	public BinaryDocument addBinaryResource(DBBroker broker,
			String name, byte[] data) throws EXistException,
			PermissionDeniedException, LockException {
		if (broker.isReadOnly())
			throw new PermissionDeniedException("Database is read-only");
		BinaryDocument blob = null;
		DocumentImpl oldDoc = getDocument(broker, name);
		blob = new BinaryDocument(broker, name, this);
		try {
			lock.acquire(Lock.WRITE_LOCK);
			checkPermissions(broker, name, oldDoc);

			manageDocumentInformation(broker, name, oldDoc, blob );
			
			if (oldDoc != null) {
			LOG.debug("removing old document " + oldDoc.getFileName());
			if (oldDoc instanceof BinaryDocument)
				broker.removeBinaryResource((BinaryDocument) oldDoc);
			else
				broker.removeDocument(oldDoc);
			}

			broker.storeBinaryResource(blob, data);
			addDocument(broker, blob);
			broker.addDocument(this, blob);
			broker.closeDocument();
			return blob;
		} finally {
			lock.release();
		}
	}

	public void setId(short id) {
		this.collectionId = id;
	}

	public void setPermissions(int mode) throws LockException {
		try {
			lock.acquire(Lock.WRITE_LOCK);
		permissions.setPermissions(mode);
		} finally {
			lock.release();
		}
	}

	public void setPermissions(String mode) throws SyntaxException, LockException {
		try {
			lock.acquire(Lock.WRITE_LOCK);
			permissions.setPermissions(mode);
		} finally {
			lock.release();
		}
	}

	/**
	 * Set permissions for the collection.
	 * 
	 * @param permissions
	 */
	public void setPermissions(Permission permissions) throws LockException {
		try {
			lock.acquire(Lock.WRITE_LOCK);
			this.permissions = permissions;
		} finally {
			lock.release();
		}
	}

	/**
	 * Write collection contents to stream.
	 * 
	 * @param ostream
	 * @throws IOException
	 */
	public void write(DBBroker broker, VariableByteOutputStream ostream)
			throws IOException {
		ostream.writeShort(collectionId);
		ostream.writeInt(subcollections.size());
		String childColl;
		for (Iterator i = subcollections.iterator(); i.hasNext(); ) {
			childColl = (String) i.next();
			ostream.writeUTF(childColl);
		}
		org.exist.security.SecurityManager secman = broker.getBrokerPool()
				.getSecurityManager();
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
		ostream.writeLong(created);
		if(reloadRequired) {
		    broker.reloadCollection(this);
		    reloadRequired = false;
		}
		DocumentImpl doc;
		for (Iterator i = documents.values().iterator(); i.hasNext(); ) {
			doc = (DocumentImpl) i.next();
			doc.write(ostream);
		}
	}

	private CollectionConfiguration getConfiguration(DBBroker broker) {
	    if (!collectionConfEnabled)
	        return null;
		if (configuration == null)
			configuration = readCollectionConfiguration(broker);
		
//		if (configuration == null)
//		{			
//			String recursiv = parentHasDoc(broker,getName(), COLLECTION_CONFIG_FILE );
//			if (!(recursiv == null)) {
//				LOG.debug (" -->Try to use conf from "+recursiv);
//				configuration = broker.getCollection( recursiv).readCollectionConfiguration(broker);
//			}		
//		}
		
		return configuration;
	}

	private CollectionConfiguration readCollectionConfiguration(DBBroker broker) {
		if (hasDocument(COLLECTION_CONFIG_FILE)) {			
		    DocumentImpl doc = getDocument(broker, COLLECTION_CONFIG_FILE);
		    if(doc == null) {
		        LOG.warn("collection.xconf exists but could not be loaded");
		        return null;
		    }
			LOG.debug("found collection.xconf");
			triggersEnabled = false;
			try {
				return new CollectionConfiguration(broker, this, doc);
			} catch (CollectionConfigurationException e) {
				LOG.warn("Failed to load collection configuration " + e.getMessage());
			} finally {
				triggersEnabled = true;
			}
		}
		return null;
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
			lock.acquire(Lock.WRITE_LOCK);
			this.triggersEnabled = enabled;
		} catch (LockException e) {
			LOG.warn(e.getMessage(), e);
			this.triggersEnabled = enabled;
		} finally {
			lock.release();
		}
	}

	/** set user-defined Reader */
	public void setReader(XMLReader reader){
			userReader = reader;
	}
	
	/** If user-defined Reader is set, return it; otherwise return JAXP default XMLReader 
	 * configured by eXist. */
	private XMLReader getReader(DBBroker broker) throws EXistException,
			SAXException {
		
		if ( userReader != null )
			return userReader;
		
		Configuration config = broker.getConfiguration();
		resolver = (CatalogResolver) config.getProperty("resolver");
		return broker.getBrokerPool().getParserPool().borrowXMLReader();
	}

	private void releaseReader(DBBroker broker, XMLReader reader) {
		if (userReader != null )
			return;
		broker.getBrokerPool().getParserPool().returnXMLReader(reader);
	}
	
	/**
	 * Try to resolve external entities.
	 * 
	 * This method forwards the request to the resolver. If that fails,
	 * the method replaces absolute file names with relative ones 
	 * and retries to resolve. This makes it possible to use relative
	 * file names in the catalog.
	 * 
	 * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
	 */
	public InputSource resolveEntity(String publicId, String systemId)
			throws SAXException, IOException {
		InputSource is = resolver.resolveEntity(publicId, systemId);
		// if resolution failed and publicId == null,
		// try to make absolute file names relative and retry
		if (is == null) {
			if (publicId != null)
				return null;
			URL url = new URL(systemId);
			if (url.getProtocol().equals("file")) {
				String path = url.getPath();
				File f = new File(path);
				if (!f.canRead())
					return resolver.resolveEntity(null, f.getName());
				else
					return new InputSource(f.getAbsolutePath());
			} else
				return new InputSource(url.openStream());
		}
		return is;
	}

	private void setFeature(SAXParserFactory factory, String feature,
			boolean value) {
		try {
			factory.setFeature(feature, value);
		} catch (SAXNotRecognizedException e) {
			LOG.warn(e);
		} catch (SAXNotSupportedException snse) {
			LOG.warn(snse);
		} catch (ParserConfigurationException pce) {
			LOG.warn(pce);
		}
	}

	/* (non-Javadoc)
	 * @see java.util.Observable#addObserver(java.util.Observer)
	 */
	public void addObserver(Observer o) {
		if (observers == null)
			observers = new ArrayList(1);
		if (!observers.contains(o))
			observers.add(o);
	}

	/* (non-Javadoc)
	 * @see java.util.Observable#deleteObservers()
	 */
	public void deleteObservers() {
		if (observers != null)
			observers.clear();
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
	public boolean sync() {
	    return false;
	}
	
	
    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cacheable#isDirty()
     */
    public boolean isDirty() {
        return false;
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[");
        for(Iterator i = documents.keySet().iterator(); i.hasNext(); ) {
            buf.append(i.next());
            if(i.hasNext())
                buf.append(", ");
        }
        buf.append("]");
        return buf.toString();
    }
    
	public String getParentPathGen(String col) {
		if (col.equals("/db"))
			return null;
		String col1 = (col.lastIndexOf("/") < 1 ? "/db" : col.substring(0,
				col.lastIndexOf("/")));
		return col1;
	}
    
	public String parentHasDoc(DBBroker broker, String col, String document) {
		Collection collection = null;
		String col2 = "";
		String result = null;
		boolean test = true;
		CollectionCache cache = broker.getBrokerPool().getCollectionsCache();
		synchronized (cache) {
			while (test) {
				col = getParentPathGen(col);
				if (col == null)
					test = false;
				else {
					collection = broker.getCollection(col);
	//				collection = broker.openCollection(col, Lock.READ_LOCK);
					if (collection.hasDocument(document)) {
						result = col;
						test = false;
					}
	//				collection.release();
				}
			}
		}
		return result;		
	}

	public IndexSpec getIdxConf(DBBroker broker, String doctype) {
	    CollectionConfiguration conf = getConfiguration(broker);
	    if(conf == null)
	        return broker.getIndexConfiguration().getByDoctype(doctype);
	    else {
	        return conf.getByDoctype(doctype);
	    }
	}
}
