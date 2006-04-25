
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Collator;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is the base class for all database backends. All the basic database operations like storing,
 * removing or index access are provided by subclasses of this class.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class DBBroker extends Observable {

	public final static int MATCH_EXACT = 0;
	public final static int MATCH_REGEXP = 1;
	public final static int MATCH_WILDCARDS = 2;
	
    public final static int NATIVE = 0;
    public final static int NATIVE_CLUSTER = 1;
    
    public final static String ROOT_COLLECTION_NAME = "db";
    public final static String ROOT_COLLECTION = "/" + ROOT_COLLECTION_NAME;
    public final static String SYSTEM_COLLECTION = ROOT_COLLECTION + "/system";    
    public final static String TEMP_COLLECTION = SYSTEM_COLLECTION + "/temp";
    public final static String CONFIG_COLLECTION = SYSTEM_COLLECTION + "/config";
    public final static String COLLECTION_CONFIG_FILENAME = "collection.xconf";
    
    protected final static Logger LOG = Logger.getLogger(DBBroker.class);
	
	protected boolean caseSensitive = true;

	protected Configuration config;
	
	protected BrokerPool pool;
	
	protected File symbolsFile;
	protected SymbolTable symbols = null;
	
	protected User user = null;
	
	protected XQuery xqueryService;
	
	private int referenceCount = 0;

	protected int xupdateGrowthFactor = 1;
	
	protected int docFragmentationLimit = 25;
	
	protected boolean xupdateConsistencyChecks = false;
	
	protected String id;
	
	/**
	 * Save the global symbol table. The global symbol table stores
	 * QNames and namespace/prefix mappings.
	 *  
	 * @throws EXistException
	 */
	protected void saveSymbols() throws EXistException {
		synchronized (symbols) {
			try {
				VariableByteOutputStream os = new VariableByteOutputStream(256);
				symbols.write(os);
				FileOutputStream fos = new FileOutputStream(symbols.getFile().getAbsolutePath(), false);
				fos.write(os.toByteArray());
				fos.close();
			} catch (FileNotFoundException e) {
				throw new EXistException(
					"file not found: " + symbols.getFile().getAbsolutePath());
			} catch (IOException e) {
				throw new EXistException(
					"io error occurred while creating "
						+ symbolsFile.getAbsolutePath());
			}
		}
	}
	
	/**
	 * Read the global symbol table. The global symbol table stores
	 * QNames and namespace/prefix mappings.
	 *  
	 * @throws EXistException
	 */
	protected void loadSymbols() throws EXistException {
		try {
			FileInputStream fis = new FileInputStream(symbols.getFile());
			VariableByteInput is = new VariableByteInputStream(fis);
			symbols.read(is);
			fis.close();
		} catch (FileNotFoundException e) {
			throw new EXistException(
				"could not read " + symbolsFile.getAbsolutePath());
		} catch (IOException e) {
			throw new EXistException(
				"io error occurred while reading "
					+ symbolsFile.getAbsolutePath());
		}
	}

	public void backupSymbolsTo(OutputStream os) throws IOException {
		FileInputStream fis = new FileInputStream(symbols.getFile());
		byte[] buf = new byte[1024];
        int len;
        while ((len = fis.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
        fis.close();
	}
	
	public SymbolTable getSymbols() {
		return symbols;
	}
	
	public DBBroker(BrokerPool pool, Configuration config)
		throws EXistException {
		this.config = config;
		Boolean temp;
		if ((temp = (Boolean) config.getProperty("indexer.case-sensitive"))
			!= null)
			caseSensitive = temp.booleanValue();
		String dataDir;
		if ((dataDir = (String) config.getProperty("db-connection.data-dir"))
			== null)
			dataDir = "data";
		if ((symbols = (SymbolTable) config.getProperty("db-connection.symbol-table")) == null) {
			symbolsFile =
				new File(dataDir + File.separatorChar + "symbols.dbx");
			LOG.debug("Loading symbol table from " + symbolsFile.getAbsolutePath());
			symbols = new SymbolTable(symbolsFile);
			if (!symbolsFile.canRead()) {
				saveSymbols();
			} else
				loadSymbols();
			config.setProperty("db-connection.symbol-table", symbols);
		}
		if ((xupdateGrowthFactor = config.getInteger("xupdate.growth-factor")) < 0)
		    xupdateGrowthFactor = 1;
		if ((docFragmentationLimit = config.getInteger("xupdate.fragmentation")) < 0)
		    docFragmentationLimit = 50;
		if ((temp = (Boolean) config.getProperty("xupdate.consistency-checks")) != null)
			xupdateConsistencyChecks = temp.booleanValue();
		
		LOG.debug("fragmentation = " + docFragmentationLimit);		
		this.pool = pool;
		xqueryService = new XQuery(this);
	}
	
	/**
	 * Set the user that is currently using this DBBroker object.
	 * 
	 * @param user
	 */
	public void setUser(User user) {
		this.user = user;
		
		/*synchronized (this){
			System.out.println("DBBroker.setUser(" + user.getName() + ")");
			Thread.dumpStack();
		}*/ //debugging user escalation permissions problem - deliriumsky.
	}
	
	/**
	 * Get the user that is currently using this DBBroker object.
	 * 
	 * @return
	 */
	public User getUser() {
		return user;
	}
	
	/**
	 * Returns a reference to the global {@link XQuery} service.
	 * 
	 * @return
	 */
	public XQuery getXQueryService() {
	    return xqueryService;
	}
	
	public abstract ElementIndex getElementIndex();

	/**  Flush all data that has not been written before. */
	public void flush() {
		/*
		 *  do nothing
		 */
	}

	/**
	 *  Adds all the documents in the database to the specified DocumentSet.
     *  
     *  @param docs a (possibly empty) document set to which the found
     *  documents are added.
	 *
	 */
	public abstract DocumentSet getAllXMLResources(DocumentSet docs);

	/**
	 *  Returns the database collection identified by the specified path.
	 * The path should be absolute, e.g. /db/shakespeare.
	 * 
	 * @return collection or null if no collection matches the path
	 *
	 * @deprecated Use XmldbURI instead!
	 *
	public abstract Collection getCollection(String name);
	*/
	
	/**
	 *  Returns the database collection identified by the specified path.
	 * The path should be absolute, e.g. /db/shakespeare.
	 * 
	 * @return collection or null if no collection matches the path
	 */
	public abstract Collection getCollection(XmldbURI uri);

	/**
	 * Returns the database collection identified by the specified path.
	 * The storage address is used to locate the collection without
	 * looking up the path in the btree.
	 * 
	 * @return
	 * @deprecated Use XmldbURI instead!
	 *
	public abstract Collection getCollection(String name, long address);
	*/
	
	/**
	 * Returns the database collection identified by the specified path.
	 * The storage address is used to locate the collection without
	 * looking up the path in the btree.
	 * 
	 * @return
	 */
	public abstract Collection getCollection(XmldbURI uri, long address);
	
	/**
	 * Open a collection for reading or writing. The collection is identified by its
	 * absolute path, e.g. /db/shakespeare. It will be loaded and locked according to the
	 * lockMode argument. 
	 * 
	 * The caller should take care to release the collection lock properly.
	 * 
	 * @param name the collection path
	 * @param lockMode one of the modes specified in class {@link org.exist.storage.lock.Lock}
	 * @return collection or null if no collection matches the path
	 * 
	 * @deprecated Use XmldbURI instead!
	 *
	public abstract Collection openCollection(String name, int lockMode);
	*/
	
	/**
	 * Open a collection for reading or writing. The collection is identified by its
	 * absolute path, e.g. /db/shakespeare. It will be loaded and locked according to the
	 * lockMode argument. 
	 * 
	 * The caller should take care to release the collection lock properly.
	 * 
	 * @param name the collection path
	 * @param lockMode one of the modes specified in class {@link org.exist.storage.lock.Lock}
	 * @return collection or null if no collection matches the path
	 */
	public abstract Collection openCollection(XmldbURI uri, int lockMode);
	
	/**
	 *  Returns the database collection identified by the specified path.
	 * If the collection does not yet exist, it is created - including all
	 * ancestors. The path should be absolute, e.g. /db/shakespeare.
	 * 
	 * @return collection or null if no collection matches the path
	 * 
	 * @deprecated Use XmldbURI instead!
	 *
	public Collection getOrCreateCollection(Txn transaction, String name)
		throws PermissionDeniedException {
		return null;
	}
	*/
	
	/**
	 *  Returns the database collection identified by the specified path.
	 * If the collection does not yet exist, it is created - including all
	 * ancestors. The path should be absolute, e.g. /db/shakespeare.
	 * 
	 * @return collection or null if no collection matches the path
	 */
	public Collection getOrCreateCollection(Txn transaction, XmldbURI uri)
		throws PermissionDeniedException {
		return null;
	}
	
	/**
	 *  Returns the configuration object used to initialize the 
	 * current database instance.
	 * 
	 */
	public Configuration getConfiguration() {
		return config;
	}

	/**
	 *  Return a {@link org.exist.storage.dom.DOMFileIterator} starting
	 * at the specified node.
	 *
	 */
	public Iterator getDOMIterator(Document doc, long gid) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Return a {@link org.exist.storage.dom.DOMFileIterator} starting
	 * at the specified node.
	 *
	 */
	public Iterator getDOMIterator(StoredNode node) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 * Return a {@link org.exist.storage.dom.NodeIterator} starting
	 * at the specified node.
	 * 
	 * @param proxy
	 * @return
	 */
	public Iterator getNodeIterator(StoredNode node) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Return the document stored at the specified path. The
	 * path should be absolute, e.g. /db/shakespeare/plays/hamlet.xml.
	 * 
	 * @return the document or null if no document could be found at the
	 * specified location.
	 * 
	 * @deprecated Use XmldbURI instead!
	 *
	public abstract Document getXMLResource(String path) throws PermissionDeniedException;
	*/

	/**
	 *  Return the document stored at the specified path. The
	 * path should be absolute, e.g. /db/shakespeare/plays/hamlet.xml.
	 * 
	 * @return the document or null if no document could be found at the
	 * specified location.
	 */
	public abstract Document getXMLResource(XmldbURI docURI) throws PermissionDeniedException;

	/**
	 * @deprecated Use XmldbURI instead!
	 *
	public abstract DocumentImpl getXMLResource(String docPath, int lockMode) 
		throws PermissionDeniedException;
	*/

	/**
	 *  Return the document stored at the specified path. The
	 * path should be absolute, e.g. /db/shakespeare/plays/hamlet.xml,
	 * with the specified lock.
	 * 
	 * @return the document or null if no document could be found at the
	 * specified location.
	 */
	public abstract DocumentImpl getXMLResource(XmldbURI docURI, int lockMode) 
		throws PermissionDeniedException;

	/**
	 * Get a new document id that does not yet exist within the collection.
	 */
	public abstract int getNextResourceId(Txn transaction, Collection collection);

	/**
	 * Get the string value of the specified node.
     * 
     * If addWhitespace is set to true, an extra space character will be
     * added between adjacent elements in mixed content nodes.
	 */
	public String getNodeValue(StoredNode node, boolean addWhitespace) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Find all Nodes whose string value is equal to expr in the document set.
	 *
	 *@param  context   the set of nodes to process
	 *@param  docs      the current set of documents
	 *@param  relation  less-than, equal etc. One of the constants specified in
	 *{@link org.exist.xquery.Constants}
	 *@param  expr      the string value to search for        
	 */
	public abstract NodeSet getNodesEqualTo(
		NodeSet context,
		DocumentSet docs,
		int relation,
        int truncation,
		String expr,
		Collator collator);

	/**
	 *  Get a range of nodes with given owner document from the database,
	 *  starting at first and ending at last.
	 *
	 *@param  doc    the document the nodes belong to
	 *@param  first  unique id of the first node to retrieve
	 *@param  last   unique id of the last node to retrieve
	 */
	public abstract NodeList getNodeRange(Document doc, long first, long last);

	/**
	 *  Get an instance of the Serializer used for converting nodes back to XML.
	 *  Subclasses of DBBroker may have specialized subclasses of Serializer to
	 *  convert a node into an XML-string
	 */
	public abstract Serializer getSerializer();

	/**
	 *  Get the TextSearchEngine associated with this broker. Every subclass of
	 *  DBBroker will have it's own implementation of TextSearchEngine.
	 */
	public abstract TextSearchEngine getTextEngine();

	public abstract NativeValueIndex getValueIndex();
	
	public abstract NativeValueIndexByQName getQNameValueIndex();
	
	/**
	 *  Is string comparison case sensitive?
	 *
	 */
	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public abstract Serializer newSerializer();

	/**
	 *  Get a node with given owner document and id from the database.
	 *
	 *@param  doc  the document the node belongs to
	 *@param  gid  the node's unique identifier
	 */
	public abstract StoredNode objectWith(Document doc, long gid);
    
	public abstract StoredNode objectWith(NodeProxy p);

	/**
	 * Remove the collection and all its subcollections from
	 * the database.
	 * 
	 */
	public abstract boolean removeCollection(Txn transaction, Collection collection)
		throws PermissionDeniedException;

	/**
	 *  Remove a document from the database.
	 *
	 */
	public void removeXMLResource(Txn transaction, DocumentImpl document)
	throws PermissionDeniedException {
	    removeXMLResource(transaction, document, true);
	}
	
	public abstract void removeXMLResource(Txn transaction, DocumentImpl document, boolean freeDocId)
		throws PermissionDeniedException;

	/**
	 * Reindex a collection.
	 * 
	 * @param collectionName
	 * @throws PermissionDeniedException
	 *
	public abstract void reindexCollection(String collectionName) 
		throws PermissionDeniedException;
	*/
	public abstract void reindexCollection(XmldbURI collectionName) 
		throws PermissionDeniedException;
	
    public abstract void repair() throws PermissionDeniedException;
    
	/**
     * Saves the specified collection to storage. Collections are usually cached in
     * memory. If a collection is modified, this method needs to be called to make
     * the changes persistent.
     * Note: appending a new document to a collection does not require a save.
     * Instead, {@link #addDocument(Collection, DocumentImpl)} is called.
     *
     * @param collection to store
	 */
	public abstract void saveCollection(Txn transaction, Collection collection)
	throws PermissionDeniedException;

	public void closeDocument() {
	}

	/**
	 *  Shut down the database instance. All open files, jdbc connections etc. should be
	 *  closed.
	 */
	public void shutdown() {
	}

	/**
	 *  Store a node into the database. This method is called by the parser to
	 *  write a node to the storage backend.
	 *
	 *@param  node         the node to be stored
	 *@param  currentPath  path expression which points to this node's
	 *      element-parent or to itself if it is an element (currently used by
	 *      the Broker to determine if a node's content should be
	 *      fulltext-indexed).
	 */
	public abstract void storeNode(Txn transaction, StoredNode node, NodePath currentPath, boolean index);

	public void storeNode(Txn transaction, StoredNode node, NodePath currentPath) {
	    storeNode(transaction, node, currentPath, true);
	}

    public void endElement(final StoredNode node, NodePath currentPath, String content) {
        endElement(node, currentPath, content, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * Update indexes for the given element node. This method is called when the indexer
     * encounters a closing element tag. It updates any range indexes defined on the
     * element value and adds the element id to the structural index.
     * 
     * @param node the current element node
     * @param currentPath node path leading to the element
     * @param content contains the string value of the element. Needed if a range index
     * is defined on it.
     * @param oldAddress when copying a node, contains the storage address of the old node.
     */
	public abstract void endElement(final StoredNode node, NodePath currentPath, String content, long oldAddress);
	
	/**
	 * Store a document (descriptor) into the database
     * (all metadata information which is returned by 
     * {@link org.exist.dom.DocumentImpl#serialize()}).
	 *
	 * @param doc the document's metadata to store.
	 */
	public abstract void storeXMLResource(Txn transaction, DocumentImpl doc);

    /**
     * Stores the given data under the given binary resource descriptor 
     * (BinaryDocument).
     * 
     * @param blob the binary document descriptor
     * @param data the document binary data
     */
    public abstract void storeBinaryResource(Txn transaction, BinaryDocument blob, byte[] data);
    
    public abstract void getCollectionResources(Collection collection);
	
    /**
     * Retrieve the binary data stored under the resource descriptor
     * BinaryDocument.
     * 
     * @param blob the binary document descriptor
     * @return the document binary data
     */
    public abstract byte[] getBinaryResource(BinaryDocument blob);
    
    public abstract void getResourceMetadata(DocumentImpl doc);
    

	

	
    /**
     * Completely delete this binary document (descriptor and binary
     * data).
     * 
     * @param blob the binary document descriptor
     * @throws PermissionDeniedException if you don't have the right to do this
     */
	public abstract void removeBinaryResource(Txn transaction, BinaryDocument blob) throws PermissionDeniedException;

	/**
	 * Move a collection and all its subcollections to another collection and rename it.
	 * Moving a collection just modifies the collection path and all resource paths. The
	 * data itself remains in place.
	 * 
	 * @param collection the collection to move
	 * @param destination the destination collection
	 * @param newName the new name the collection should have in the destination collection
	 * @deprecated Use XmldbURI instead
	 *
	public abstract void moveCollection(Txn transaction, Collection collection, Collection destination, String newName) 
	throws PermissionDeniedException, LockException;
	*/
	
	/**
	 * Move a collection and all its subcollections to another collection and rename it.
	 * Moving a collection just modifies the collection path and all resource paths. The
	 * data itself remains in place.
	 * 
	 * @param collection the collection to move
	 * @param destination the destination collection
	 * @param newName the new name the collection should have in the destination collection
	 */
	public abstract void moveCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newName) 
	throws PermissionDeniedException, LockException;
	
	/**
	 * Move a resource to the destination collection and rename it.
	 * 
	 * @param doc the resource to move
	 * @param destination the destination collection
	 * @param new Name the new name the resource should have in the destination collection
	 * @deprecated Use XmldbURI version instead
	 *
	public abstract void moveXMLResource(Txn transaction, DocumentImpl doc, Collection destination, String newName)
	throws PermissionDeniedException, LockException;
	*/
	
	/**
	 * Move a resource to the destination collection and rename it.
	 * 
	 * @param doc the resource to move
	 * @param destination the destination collection
	 * @param new Name the new name the resource should have in the destination collection
	 */
	public abstract void moveXMLResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName)
	throws PermissionDeniedException, LockException;
	
	/**
	 * Copy a collection to the destination collection and rename it.
	 * 
	 * @param doc the resource to move
	 * @param destination the destination collection
	 * @param new Name the new name the resource should have in the destination collection
	 * @deprecated Use XmldbURI version instead
	 *
	public abstract void copyCollection(Txn transaction, Collection collection, Collection destination, String newName)
	throws PermissionDeniedException, LockException;
	*/
	
	/**
	 * Copy a collection to the destination collection and rename it.
	 * 
	 * @param doc the resource to move
	 * @param destination the destination collection
	 * @param new Name the new name the resource should have in the destination collection
	 */
	public abstract void copyCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newName)
	throws PermissionDeniedException, LockException;
	
	/**
	 * Copy a resource to the destination collection and rename it.
	 * 
	 * @param doc the resource to copy
	 * @param destination the destination collection
	 * @param newName the new name the resource should have in the destination collection
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @deprecated Use XmldbURI version instead
	 *
	public abstract void copyXMLResource(Txn transaction, DocumentImpl doc, Collection destination, String newName) 
	throws PermissionDeniedException, LockException;
	*/
	
	/**
	 * Copy a resource to the destination collection and rename it.
	 * 
	 * @param doc the resource to copy
	 * @param destination the destination collection
	 * @param newName the new name the resource should have in the destination collection
	 * @throws PermissionDeniedException
	 * @throws LockException
	 */
	public abstract void copyXMLResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName) 
	throws PermissionDeniedException, LockException;
	
    /**
     * Defragment pages of this document. This will minimize the number of
     * split pages.
     * 
     * @param doc to defrag
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
	
	public abstract void checkXMLResourceConsistency(DocumentImpl doc) throws EXistException;
	
    /**
     * Sync dom and collection state data (pages) to disk.
     * In case of {@link org.exist.storage.sync.Sync.MAJOR_SYNC}, sync all
     * states (dom, collection, text and element) to disk.
     * 
     * @param syncEvent Sync.MAJOR_SYNC or Sync.MINOR_SYNC
     */
	public abstract void sync(int syncEvent);

	/**
	 *  Update a node's data. To keep nodes in a correct sequential order, it is sometimes 
	 * necessary to update a previous written node. Warning: don't use it for other purposes.
	 *
	 *@param  node  Description of the Parameter
	 */
	public abstract void updateNode(Txn transaction, StoredNode node);

	/**
	 * Is the database running read-only? Returns false by default.
	 * Storage backends should override this if they support read-only
	 * mode.
	 * 
	 * @return boolean
	 */
	public boolean isReadOnly() {
		return false;
	}

	public BrokerPool getBrokerPool() {
		return pool;
	}

	public abstract void insertNodeAfter(Txn transaction, final StoredNode previous, final StoredNode node);

	public abstract void reindexXMLResource(Txn transaction, DocumentImpl oldDoc, DocumentImpl doc, StoredNode node);

    public void indexNode(Txn transaction, StoredNode node) {
        indexNode(transaction, node, null);
    }
    
    public abstract void indexNode(Txn transaction, StoredNode node, NodePath currentPath);    

	public abstract void removeNode(Txn transaction, StoredNode node, NodePath currentPath, String content);

    public abstract void removeAllNodes(Txn transaction, StoredNode node, NodePath currentPath);
    
	public abstract void endRemove();

	/**
	 * Create a temporary document in the temp collection and store the
	 * supplied data.
	 * 
	 * @param data
	 * @return
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 * @throws LockException
	 */
	public abstract DocumentImpl storeTempResource(org.exist.memtree.DocumentImpl doc) 
		throws EXistException, PermissionDeniedException, LockException;
	
	/**
	 * Clean up any temporary resources.
	 *
	 */
	public abstract void cleanUpTempCollection();    
	
	/**
	 * Clean up temporary resources. Called by the sync daemon.
	 *
	 */
	public abstract void cleanUpTempResources();
    
    /**
     * Remove the temporary document fragments specified by a list
     * of names.
     * 
     * @param docs
     */
    public abstract void cleanUpTempResources(List docs);    
    
	
	/**
	 *   
	 */
	public abstract DocumentSet getXMLResourcesByDoctype(
		String doctype,
		DocumentSet result);

	public int getReferenceCount() {
		return referenceCount;
	}
	
	public void incReferenceCount() {
		++referenceCount;
	}
	
	public void decReferenceCount() {
		--referenceCount;
	}
	
	public int getXUpdateGrowthFactor() {
	    return xupdateGrowthFactor;
	}
	
	public int getFragmentationLimit() {
	    return docFragmentationLimit;
	}
	
	public boolean consistencyChecksEnabled() {
		return xupdateConsistencyChecks;
	}
	
	public abstract int getPageSize();
	
	public abstract IndexSpec getIndexConfiguration();
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}
	
	public String toString() {
		return id;
	}
    
    public abstract int getBackendType();
}
