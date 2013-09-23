/*
 *  DBBroker.java - eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.*;
import org.exist.indexing.IndexController;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StructuralIndex;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQuery;
import org.w3c.dom.Document;

import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import org.exist.collections.Collection.SubCollectionEntry;

/**
 * This is the base class for all database backends. All the basic database
 * operations like storing, removing or index access are provided by subclasses
 * of this class.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class DBBroker extends Observable {

	// Matching types
	public final static int MATCH_EXACT 		= 0;
	public final static int MATCH_REGEXP 		= 1;
	public final static int MATCH_WILDCARDS 	= 2;
	public final static int MATCH_CONTAINS 		= 3;
	public final static int MATCH_STARTSWITH 	= 4;
	public final static int MATCH_ENDSWITH 		= 5;
	
	//TODO : move elsewhere
	public static final String CONFIGURATION_ELEMENT_NAME = "xupdate";
    
    //TODO : move elsewhere
    public final static String XUPDATE_FRAGMENTATION_FACTOR_ATTRIBUTE = "allowed-fragmentation";

    //TODO : move elsewhere
    public final static String PROPERTY_XUPDATE_FRAGMENTATION_FACTOR = "xupdate.fragmentation";

    //TODO : move elsewhere
    public final static String XUPDATE_CONSISTENCY_CHECKS_ATTRIBUTE = "enable-consistency-checks";

    //TODO : move elsewhere
    public final static String PROPERTY_XUPDATE_CONSISTENCY_CHECKS = "xupdate.consistency-checks";

    protected final static Logger LOG = Logger.getLogger(DBBroker.class);

    protected boolean caseSensitive = true;

    protected Configuration config;

    protected BrokerPool pool;

    private Subject subject = null;

    protected XQuery xqueryService;

    private int referenceCount = 0;

    protected String id;

    protected IndexController indexController;

    //TODO: remove after interface it
    public DBBroker() {
        //Nothing todo
    }

    public DBBroker(BrokerPool pool, Configuration config) {
        this.config = config;
        final Boolean temp = (Boolean) config.getProperty(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE);
        if (temp != null)
            {caseSensitive = temp.booleanValue();}
        this.pool = pool;
        xqueryService = new XQuery(this);
        initIndexModules();
    }

    public void initIndexModules() {
        indexController = new IndexController(this);
    }
    

    /**
     * Set the user that is currently using this DBBroker object.
     *
     * @param user
     * @deprecated use setSubject
     */
    public void setUser(Subject user) {
        this.subject = user;

        /*
        synchronized (this){ System.out.println("DBBroker.setUser(" +
        user.getName() + ")"); Thread.dumpStack(); }
         */
        // debugging user escalation permissions problem - deliriumsky.
    }

    /**
     * @return The user that is currently using this DBBroker object
     * @deprecated user getSubject
     */
    @Deprecated
    public Subject getUser() {
        return getSubject();
    }

    /**
     * Set the subject that is currently using this DBBroker object.
     *
     * @param subject
     */
    //TODO: this should be done in connection with authenticate (SecurityManager)
    public void setSubject(Subject subject) {
        this.subject = subject;
        /*
        synchronized (this){ System.out.println("DBBroker.setUser(" +
            user.getName() + ")"); Thread.dumpStack(); }
        */
        // debugging user escalation permissions problem - deliriumsky.
    }

    /**
     * The subject that is currently using this DBBroker object
     * 
     * @return Subject 
     */
    public Subject getSubject() {
        return subject;
    }

    public IndexController getIndexController() {
        return indexController;
    }

    /**
     * @return A reference to the global {@link XQuery} service.
     */
    public XQuery getXQueryService() {
        return xqueryService;
    }

    public abstract ElementIndex getElementIndex();

    public abstract StructuralIndex getStructuralIndex();

    /** Flush all data that has not been written before. */
    public void flush() {
        // do nothing
    }

    /** Observer Design Pattern: List of ContentLoadingObserver objects */
    protected List<ContentLoadingObserver> contentLoadingObservers = new ArrayList<ContentLoadingObserver>();	

    /** Remove all observers */
    public void clearContentLoadingObservers() {
        contentLoadingObservers.clear();
    }    

    /** Observer Design Pattern: add an observer. */
    public void addContentLoadingObserver(ContentLoadingObserver observer) {
        if (!contentLoadingObservers.contains(observer))
            {contentLoadingObservers.add(observer);}
    }

    /** Observer Design Pattern: remove an observer. */
    public void removeContentLoadingObserver(ContentLoadingObserver observer) {
        if (contentLoadingObservers.contains(observer))
            {contentLoadingObservers.remove(observer);}
    }

    /**
     * Adds all the documents in the database to the specified DocumentSet.
     * 
     * @param docs
     *            a (possibly empty) document set to which the found documents
     *            are added.
     * 
     */
    public abstract MutableDocumentSet getAllXMLResources(MutableDocumentSet docs) throws PermissionDeniedException;

    public abstract void getResourcesFailsafe(BTreeCallback callback, boolean fullScan) throws TerminatedException;

    public abstract void getCollectionsFailsafe(BTreeCallback callback) throws TerminatedException;

    /**
     * Returns the database collection identified by the specified path. The
     * path should be absolute, e.g. /db/shakespeare.
     * 
     * @return collection or null if no collection matches the path
     * 
     * deprecated Use XmldbURI instead!
     * 
     * public abstract Collection getCollection(String name);
     */

    /**
     * Returns the database collection identified by the specified path. The
     * path should be absolute, e.g. /db/shakespeare.
     * 
     * @return collection or null if no collection matches the path
     */
    public abstract Collection getCollection(XmldbURI uri) throws PermissionDeniedException;

    /**
     * Returns the database collection identified by the specified path. The
     * storage address is used to locate the collection without looking up the
     * path in the btree.
     * 
     * @return deprecated Use XmldbURI instead!
     * 
     * public abstract Collection getCollection(String name, long address);
     */

    /**
     * Returns the database collection identified by the specified path. The
     * storage address is used to locate the collection without looking up the
     * path in the btree.
     * 
     * @return Database collection
     * 
     * public abstract Collection getCollection(XmldbURI uri, long address);
     */	

    /**
     * Open a collection for reading or writing. The collection is identified by
     * its absolute path, e.g. /db/shakespeare. It will be loaded and locked
     * according to the lockMode argument.
     * 
     * The caller should take care to release the collection lock properly.
     * 
     * @param name
     *            the collection path
     * @param lockMode
     *            one of the modes specified in class
     *            {@link org.exist.storage.lock.Lock}
     * @return collection or null if no collection matches the path
     * 
     * deprecated Use XmldbURI instead!
     * 
     * public abstract Collection openCollection(String name, int lockMode);
     */

    /**
     * Open a collection for reading or writing. The collection is identified by
     * its absolute path, e.g. /db/shakespeare. It will be loaded and locked
     * according to the lockMode argument.
     * 
     * The caller should take care to release the collection lock properly.
     * 
     * @param uri
     *            The collection path
     * @param lockMode
     *            one of the modes specified in class
     *            {@link org.exist.storage.lock.Lock}
     * @return collection or null if no collection matches the path
     * 
     */
    public abstract Collection openCollection(XmldbURI uri, int lockMode) throws PermissionDeniedException;

    public abstract List<String> findCollectionsMatching(String regexp);
    
    /**
     * Returns the database collection identified by the specified path. If the
     * collection does not yet exist, it is created - including all ancestors.
     * The path should be absolute, e.g. /db/shakespeare.
     * 
     * @return collection or null if no collection matches the path
     * 
     * deprecated Use XmldbURI instead!
     * 
     * public Collection getOrCreateCollection(Txn transaction, String name)
     * throws PermissionDeniedException { return null; }
     */

    /**
     * Returns the database collection identified by the specified path. If the
     * collection does not yet exist, it is created - including all ancestors.
     * The path should be absolute, e.g. /db/shakespeare.
     * 
     * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
     * @param uri The collection's URI
     * @return The collection or <code>null</code> if no collection matches the path
     * @throws PermissionDeniedException
     * @throws IOException
     * @throws TriggerException 
     */
    public abstract Collection getOrCreateCollection(Txn transaction, XmldbURI uri)
        throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Returns the configuration object used to initialize the current database
     * instance.
     * 
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Return a {@link org.exist.storage.dom.NodeIterator} starting at the
     * specified node.
     * 
     * @param node
     * @return NodeIterator of node.
     */
    public Iterator<StoredNode> getNodeIterator(StoredNode node) {
        throw new RuntimeException("not implemented for this storage backend");
    }

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml.
     * 
     * @return the document or null if no document could be found at the
     *         specified location.
     * 
     * deprecated Use XmldbURI instead!
     * 
     * public abstract Document getXMLResource(String path) throws
     * PermissionDeniedException;
     */

    public abstract Document getXMLResource(XmldbURI docURI) throws PermissionDeniedException;
    
    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml.
     * 
     * @return the document or null if no document could be found at the
     *         specified location.
     */
    public abstract DocumentImpl getResource(XmldbURI docURI, int accessType) throws PermissionDeniedException;

    public abstract DocumentImpl getResourceById(int collectionId, byte resourceType, int documentId) throws PermissionDeniedException;
    
    /**
     * deprecated Use XmldbURI instead!
     * 
     * public abstract DocumentImpl getXMLResource(String docPath, int lockMode)
     * throws PermissionDeniedException;
     */

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml, with the specified lock.
     * 
     * @return the document or null if no document could be found at the
     *         specified location.
     */
    public abstract DocumentImpl getXMLResource(XmldbURI docURI, int lockMode)
        throws PermissionDeniedException;

    /**
     * Get a new document id that does not yet exist within the collection.
     * @throws EXistException 
     */
    public abstract int getNextResourceId(Txn transaction, Collection collection) throws EXistException;

    /**
     * Get the string value of the specified node.
     * 
     * If addWhitespace is set to true, an extra space character will be added
     * between adjacent elements in mixed content nodes.
     */
    public String getNodeValue(StoredNode node, boolean addWhitespace) {
        throw new RuntimeException("not implemented for this storage backend");
    }

    /**
     * Get an instance of the Serializer used for converting nodes back to XML.
     * Subclasses of DBBroker may have specialized subclasses of Serializer to
     * convert a node into an XML-string
     */
    public abstract Serializer getSerializer();

    /**
     * Get the TextSearchEngine associated with this broker. Every subclass of
     * DBBroker will have it's own implementation of TextSearchEngine.
     */
    public abstract TextSearchEngine getTextEngine();

    public abstract NativeValueIndex getValueIndex();

    public abstract Serializer newSerializer();

    /**
     * Get a node with given owner document and id from the database.
     * 
     * @param doc
     *            the document the node belongs to
     * @param nodeId
     *            the node's unique identifier
     */
    public abstract StoredNode objectWith(Document doc, NodeId nodeId);

    public abstract StoredNode objectWith(NodeProxy p);

    /**
     * Remove the collection and all its subcollections from the database.
     * 
     * @throws PermissionDeniedException 
     * @throws IOException 
     * @throws TriggerException 
     * 
     */
    public abstract boolean removeCollection(Txn transaction,
        Collection collection) throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Remove a document from the database.
     * 
     */
    public void removeXMLResource(Txn transaction, DocumentImpl document)
            throws PermissionDeniedException {
        removeXMLResource(transaction, document, true);
    }

    public abstract void removeXMLResource(Txn transaction,
        DocumentImpl document, boolean freeDocId) throws PermissionDeniedException;

    /**
     * Reindex a collection.
     * 
     * @param collectionName
     * @throws PermissionDeniedException
     * 
     * public abstract void reindexCollection(String collectionName) throws
     * PermissionDeniedException;
     */
    public abstract void reindexCollection(XmldbURI collectionName)
        throws PermissionDeniedException;

    public abstract void repair() throws PermissionDeniedException;

    /**
     * Saves the specified collection to storage. Collections are usually cached
     * in memory. If a collection is modified, this method needs to be called to
     * make the changes persistent. Note: appending a new document to a
     * collection does not require a save. 
     * 
     * @param transaction 
     * @param collection Collection to store
     * @throws org.exist.security.PermissionDeniedException 
     * @throws IOException 
     * @throws TriggerException 
     */
    public abstract void saveCollection(Txn transaction, Collection collection)
        throws PermissionDeniedException, IOException, TriggerException;

    public void closeDocument() {
        //Nothing to do
    }

    /**
     * Shut down the database instance. All open files, jdbc connections etc.
     * should be closed.
     */
    public void shutdown() {
        //Nothing to do
    }

    /**
     * Store a node into the database. This method is called by the parser to
     * write a node to the storage backend.
     * 
     * @param node
     *            the node to be stored
     * @param currentPath
     *            path expression which points to this node's element-parent or
     *            to itself if it is an element (currently used by the Broker to
     *            determine if a node's content should be fulltext-indexed).
     */
    public abstract void storeNode(Txn transaction, StoredNode node,
        NodePath currentPath, IndexSpec indexSpec, boolean index);

    public void storeNode(Txn transaction, StoredNode node, NodePath currentPath, IndexSpec indexSpec) {
        storeNode(transaction, node, currentPath, indexSpec, true);
    }

    public void endElement(final StoredNode node, NodePath currentPath, String content) {
        endElement(node, currentPath, content, false);
    }

    public abstract void endElement(final StoredNode node, NodePath currentPath, String content, boolean remove);

    /**
     * Store a document (descriptor) into the database.
     * 
     * @param doc
     *            the document's metadata to store.
     */
    public abstract void storeXMLResource(Txn transaction, DocumentImpl doc);

    public abstract void storeMetadata(Txn transaction, DocumentImpl doc) throws TriggerException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     * 
     * @param blob
     *            the binary document descriptor
     * @param data
     *            the document binary data
     */
    @Deprecated
    public abstract void storeBinaryResource(Txn transaction,
        BinaryDocument blob, byte[] data) throws IOException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     * 
     * @param blob
     *            the binary document descriptor
     * @param is
     *            the document binary data as input stream
     */
    public abstract void storeBinaryResource(Txn transaction,
        BinaryDocument blob, InputStream is) throws IOException;

    public abstract void getCollectionResources(Collection.InternalAccess collectionInternalAccess);

    /* *
     * Retrieve the binary data stored under the resource descriptor
     * BinaryDocument.
     * 
     * @param blob
     *            the binary document descriptor
     * @return the document binary data
     */
    /*
    public abstract byte[] getBinaryResource(BinaryDocument blob)
           throws IOException;
     */

    public abstract void readBinaryResource(final BinaryDocument blob,
        final OutputStream os) throws IOException;

    public abstract File getBinaryFile(final BinaryDocument blob) throws IOException;

	public abstract InputStream getBinaryResource(final BinaryDocument blob)
           throws IOException;

    public abstract long getBinaryResourceSize(final BinaryDocument blob)
           throws IOException;

    public abstract void getResourceMetadata(DocumentImpl doc);
    
    /**
     * Completely delete this binary document (descriptor and binary data).
     * 
     * @param blob
     *            the binary document descriptor
     * @throws PermissionDeniedException
     *             if you don't have the right to do this
     */
    public abstract void removeBinaryResource(Txn transaction,
        BinaryDocument blob) throws PermissionDeniedException,IOException;

	/**
	 * Move a collection and all its subcollections to another collection and
	 * rename it. Moving a collection just modifies the collection path and all
	 * resource paths. The data itself remains in place.
	 * 
	 * @param collection
	 *            the collection to move
	 * @param destination
	 *            the destination collection
	 * @param newName
	 *            the new name the collection should have in the destination
	 *            collection
	 * 
	 * @throws PermissionDeniedException 
	 * @throws LockException 
	 * @throws IOException 
	 * @throws TriggerException 
	 */
	public abstract void moveCollection(Txn transaction, Collection collection,
			Collection destination, XmldbURI newName)
			throws PermissionDeniedException, LockException, IOException, TriggerException;

	/**
	 * Move a resource to the destination collection and rename it.
	 * 
	 * @param doc
	 *            the resource to move
	 * @param destination
	 *            the destination collection
	 * @param newName
	 *            the new name the resource should have in the destination
	 *            collection
	 * 
	 * @throws PermissionDeniedException 
	 * @throws LockException 
	 * @throws IOException 
	 * @throws TriggerException 
	 */
	public abstract void moveResource(Txn transaction, DocumentImpl doc,
			Collection destination, XmldbURI newName)
			throws PermissionDeniedException, LockException, IOException, TriggerException;

	/**
	 * Copy a collection to the destination collection and rename it.
	 * 
	 * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
	 * @param collection The origin collection
	 * @param destination The destination parent collection
	 * @param newName The new name of the collection
	 * 
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @throws IOException
	 * @throws TriggerException 
	 * @throws EXistException 
	 */
	public abstract void copyCollection(Txn transaction, Collection collection,
			Collection destination, XmldbURI newName)
			throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;

	/**
	 * Copy a resource to the destination collection and rename it.
	 * 
	 * @param doc
	 *            the resource to copy
	 * @param destination
	 *            the destination collection
	 * @param newName
	 *            the new name the resource should have in the destination
	 *            collection
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @throws EXistException 
	 */
	public abstract void copyResource(Txn transaction, DocumentImpl doc,
			Collection destination, XmldbURI newName)
			throws PermissionDeniedException, LockException, EXistException;

	/**
	 * Defragment pages of this document. This will minimize the number of split
	 * pages.
	 * 
	 * @param doc
	 *            to defrag
	 */
	public abstract void defragXMLResource(Txn transaction, DocumentImpl doc);

	/**
	 * Perform a consistency check on the specified document.
	 * 
	 * This checks if the DOM tree is consistent.
	 * 
	 * @param doc
	 */
	public abstract void checkXMLResourceTree(DocumentImpl doc);

	public abstract void checkXMLResourceConsistency(DocumentImpl doc)
			throws EXistException;

	/**
	 * Sync dom and collection state data (pages) to disk. In case of
	 * {@link org.exist.storage.sync.Sync#MAJOR_SYNC}, sync all states (dom,
	 * collection, text and element) to disk.
	 * 
	 * @param syncEvent
	 *            Sync.MAJOR_SYNC or Sync.MINOR_SYNC
	 */
	public abstract void sync(int syncEvent);

	/**
	 * Update a node's data. To keep nodes in a correct sequential order, it is
	 * sometimes necessary to update a previous written node. Warning: don't use
	 * it for other purposes.
	 * 
	 * @param node
	 *            Description of the Parameter
	 */
	public abstract void updateNode(Txn transaction, StoredNode node, boolean reindex);

	/**
	 * Is the database running read-only? Returns false by default. Storage
	 * backends should override this if they support read-only mode.
	 * 
	 * @return boolean
	 */
	public boolean isReadOnly() {
		return false;
	}

	public BrokerPool getBrokerPool() {
		return pool;
	}

	public Database getDatabase() {
		return pool;
	}

	public abstract void insertNodeAfter(Txn transaction,
			final StoredNode previous, final StoredNode node);
    
    public abstract void indexNode(Txn transaction, StoredNode node, NodePath currentPath);    

	public void indexNode(Txn transaction, StoredNode node) {
		indexNode(transaction, node, null);
	}

	public abstract void removeNode(Txn transaction, StoredNode node,
			NodePath currentPath, String content);

	public abstract void removeAllNodes(Txn transaction, StoredNode node,
			NodePath currentPath, StreamListener listener);

	public abstract void endRemove(Txn transaction);

	/**
	 * Create a temporary document in the temp collection and store the supplied
	 * data.
	 * 
	 * @param doc
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 * @throws LockException
	 */
	public abstract DocumentImpl storeTempResource(
			org.exist.memtree.DocumentImpl doc) throws EXistException,
			PermissionDeniedException, LockException;
		
	/**
	 * Clean up temporary resources. Called by the sync daemon.
	 * 
	 * @param forceRemoval Should temporary resources be forcefully removed
	 */
	public abstract void cleanUpTempResources(boolean forceRemoval) throws PermissionDeniedException;
	
	/** Convenience method that allows to check available memory during broker-related processes.
	 * This method should eventually trigger flush() events.
	 */
	public abstract void checkAvailableMemory();

	/**
	 * 
	 */
	public abstract MutableDocumentSet getXMLResourcesByDoctype(String doctype, MutableDocumentSet result) throws PermissionDeniedException;

	public int getReferenceCount() {
		return referenceCount;
	}

	public void incReferenceCount() {
		++referenceCount;
	}

	public void decReferenceCount() {
		--referenceCount;
	}

	public abstract IndexSpec getIndexConfiguration();

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return id;
	}

    public abstract EmbeddedXMLStreamReader getXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    public abstract EmbeddedXMLStreamReader newXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    public abstract void backupToArchive(RawDataBackup backup) throws IOException, EXistException;

    public abstract void readCollectionEntry(SubCollectionEntry entry);

	public void release() {
		pool.release(this);
	}
}
