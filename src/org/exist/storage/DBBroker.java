
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
import java.util.Iterator;
import java.util.Observable;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.XQuery;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is the base class for all database backends. All other components rely
 * on the methods defined here.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class DBBroker extends Observable {

	public final static int MATCH_EXACT = 0;
	public final static int MATCH_REGEXP = 1;
	public final static int MATCH_WILDCARDS = 2;

	// constants for database type
	public final static int MYSQL = 0;
	public final static int NATIVE = 4;
	public final static int ORACLE = 1;
	public final static int POSTGRESQL = 2;
	public final static int DBM = 3;

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
		String sym, dataDir;
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
		this.pool = pool;
		xqueryService = new XQuery(this);
	}

	public void setUser(User user) {
		this.user = user;
	}
	
	public User getUser() {
		return user;
	}
	
	public XQuery getXQueryService() {
	    return xqueryService;
	}
	
	/**
	 *  Returns a node set containing all the attributes matching the given QName and
	 * belonging to one of the documents in the DocumentSet.
	 *
	 */
	public abstract NodeSet findElementsByTagName(
		byte type,
		DocumentSet docs,
		QName qname,
		NodeSelector selector);

	/**  Flush all data that has not been written before. */
	public void flush() {
		/*
		 *  do nothing
		 */
	}

	/**
	 *  Get all the documents currently in the database. The documents are
	 *  returned as a DocumentSet.
	 *
	 */
	public abstract DocumentSet getAllDocuments(DocumentSet docs);

	/**
	 *  Returns a node set containing all the attributes matching the given QName and
	 * belonging to one of the documents in the DocumentSet.
	 */
	public abstract NodeSet getAttributesByName(DocumentSet docs, QName qname);

	/**
	 *  Returns the database collection identified by the specified path.
	 * The path should be absolute, e.g. /db/system.
	 * 
	 * @return collection or null if no collection matches the path
	 */
	public abstract Collection getCollection(String name);

	/**
	 *  Returns the database collection identified by the specified path.
	 * If the collection does not yet exist, it is created - including all
	 * ancestors. The path should be absolute, e.g. /db/system.
	 * 
	 * @return collection or null if no collection matches the path
	 */
	public Collection getOrCreateCollection(String name)
		throws PermissionDeniedException {
		return null;
	}
	
	/**
	 * Returns the database collection identified by the specified path.
	 * The storage address is used to locate the collection without
	 * looking up the path in the btree.
	 * 
	 * @return
	 */
	public Collection getCollection(String name, long address) {
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
	 *  Return a {@link org.exist.storage.store.DOMFileIterator} starting
	 * at the specified node.
	 *
	 */
	public Iterator getDOMIterator(Document doc, long gid) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Return a {@link org.exist.storage.store.DOMFileIterator} starting
	 * at the specified node.
	 *
	 */
	public Iterator getDOMIterator(NodeProxy proxy) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 * Return a {@link org.exist.storage.store.NodeIterator} starting
	 * at the specified node.
	 * 
	 * @param proxy
	 * @return
	 */
	public Iterator getNodeIterator(NodeProxy proxy) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Return the document stored at the specified path. The
	 * path should be absolute, e.g. /db/shakespeare/plays/hamlet.xml.
	 * 
	 * @return the document or null if no document could be found at the
	 * specified location.
	 */
	public abstract Document getDocument(String path)
		throws PermissionDeniedException;

	/**
	 *  Returns a DocumentSet containing all the documents found in the
	 * specified collection. The collection should be specified with its full path.
	 */
	public abstract DocumentSet getDocumentsByCollection(String collection, DocumentSet docs)
		throws PermissionDeniedException;
		
	/**
	 *  Returns a DocumentSet containing all the documents found in the
	 * specified collection. The collection should be specified with its full path.
	 * 
	 * @param inclusive if true, recursively include documents in subcollections. 
	 */
	public abstract DocumentSet getDocumentsByCollection(
		String collection, DocumentSet docs, boolean inclusive)
		throws PermissionDeniedException;

	/**
	 * Get a new document id that does not yet exist within the collection.
	 */
	public abstract int getNextDocId(Collection collection);

	/**
	 * Get the string value of the specified node.
	 */
	public String getNodeValue(NodeProxy proxy) {
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
		String expr);

	/**
	 *  Get a range of nodes with given owner document from the database,
	 *  starting at first and ending at last.
	 *
	 *@param  doc    the document the nodes belong to
	 *@param  first  unique id of the first node to retrieve
	 *@param  last   unique id of the last node to retrieve
	 */
	public abstract NodeList getRange(Document doc, long first, long last);

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
	public abstract Node objectWith(Document doc, long gid);
	public abstract Node objectWith(NodeProxy p);

	/**
	 * Remove the collection and all its subcollections from
	 * the database.
	 * 
	 */
	public abstract boolean removeCollection(String name)
		throws PermissionDeniedException;

	/**
	 *  Remove a document from the database.
	 *
	 */
	public abstract void removeDocument(String docName)
		throws PermissionDeniedException;

	public abstract void reindex(String collectionName) 
		throws PermissionDeniedException;
	
	/**
	 * Store a collection into the database.
	 */
	public abstract void saveCollection(Collection collection)
		throws PermissionDeniedException;

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
	}

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
	public abstract void store(NodeImpl node, NodePath currentPath);

	/**
	 *  Store a document into the database.
	 *
	 *@param  doc  Description of the Parameter
	 */
	public abstract void storeDocument(DocumentImpl doc);

	public abstract void storeBinaryResource(BinaryDocument blob, byte[] data);
	
	public abstract byte[] getBinaryResourceData(final BinaryDocument blob);
	
	public abstract void removeBinaryResource(final BinaryDocument blob) throws PermissionDeniedException;

	/**
	 * Move a collection and all its subcollections to another collection and rename it.
	 * Moving a collection just modifies the collection path and all resource paths. The
	 * data itself remains in place.
	 * 
	 * @param collection the collection to move
	 * @param destination the destination collection
	 * @param newName the new name the collection should have in the destination collection
	 */
	public abstract void moveCollection(Collection collection, Collection destination, String newName) 
	throws PermissionDeniedException, LockException;
	
	public abstract void moveResource(DocumentImpl doc, Collection destination, String newName)
	throws PermissionDeniedException, LockException;
	
	public void sync() {
		/*
		 *  do nothing
		 */
	}

	/**
	 *  Update a node's data. This method is only used by the NativeBroker. To
	 *  keep nodes in a correct sequential order, it sometimes needs to update a
	 *  previous written node. Warning: don't use it for other purposes.
	 *  RelationalBroker does not implement this method.
	 *
	 *@param  node  Description of the Parameter
	 */
	public void update(NodeImpl node) {
		throw new RuntimeException("not implemented");
	}

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

	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		throw new RuntimeException("not implemented");
	}

	public void reindex(DocumentImpl oldDoc, DocumentImpl doc, NodeImpl node) {
		throw new RuntimeException("not implemented");
	}

	public void index(NodeImpl node) {
		index(node, null);
	}
	
	public void index(NodeImpl node, NodePath currentPath) {
		throw new RuntimeException("not implemented");
	}

	public void removeNode(final NodeImpl node, String currentPath) {
		throw new RuntimeException("not implemented");
	}

	public void endRemove() {
		throw new RuntimeException("not implemented");
	}

	public Occurrences[] scanIndexedElements(
		Collection collection,
		boolean inclusive)
		throws PermissionDeniedException {
		throw new RuntimeException("not implemented");
	}

	public void readDocumentMetadata(final DocumentImpl doc) {
	}
	
	/**
	 *  get all the documents in this database matching the given  document-type's name.@param  doctypeName  Description of the Parameter@param  user         Description of the Parameter@return              The documentsByDoctype value  
	 */
	public abstract DocumentSet getDocumentsByDoctype(
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
	
	public abstract int getPageSize();
}