
/*
 *  DBBroker.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.BLOBDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is the base class for all database backends. All other components rely
 * on the methods defined here.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    20. Mai 2002
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

	protected boolean caseSensitive = true;

	protected Configuration config;
	protected BrokerPool pool;
	protected static File symbolsFile;
	protected static SymbolTable symbols = null;
	protected User user = null;

	protected static void saveSymbols() throws EXistException {
		synchronized (symbols) {
			try {
				VariableByteOutputStream os = new VariableByteOutputStream(256);
				symbols.write(os);
				FileOutputStream fos = new FileOutputStream(symbolsFile, false);
				fos.write(os.toByteArray());
				fos.close();
			} catch (FileNotFoundException e) {
				throw new EXistException(
					"file not found: " + symbolsFile.getAbsolutePath());
			} catch (IOException e) {
				throw new EXistException(
					"io error occurred while creating "
						+ symbolsFile.getAbsolutePath());
			}
		}
	}

	protected static void loadSymbols() throws EXistException {
		try {
			FileInputStream fis = new FileInputStream(symbolsFile);
			VariableByteInputStream is = new VariableByteInputStream(fis);
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

	public static synchronized SymbolTable getSymbols() {
		return symbols;
	}

	/**
	 *  Constructor for the DBBroker object
	 *
	 *@param  config  Description of the Parameter
	 */
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
		if (symbols == null) {
			symbolsFile =
				new File(dataDir + File.separatorChar + "symbols.dbx");
			symbols = new SymbolTable();
			if (!symbolsFile.canRead()) {
				saveSymbols();
			} else
				loadSymbols();
		}
		this.pool = pool;
	}

	public void setUser(User user) {
		this.user = user;
	}
	
	public User getUser() {
		return user;
	}
	
	/**
	 *  find elements by their tag name. This method is comparable to the DOM's
	 *  method call getElementsByTagName. All elements matching tagName and
	 *  belonging to one of the documents in the DocumentSet docs are returned.
	 *
	 *@param  docs     Description of the Parameter
	 *@param  tagName  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public abstract NodeSet findElementsByTagName(
		byte type,
		DocumentSet docs,
		QName qname);

	/**  flush all data that has not been written before. */
	public void flush() {
		/*
		 *  do nothing
		 */
	}

	/**
	 *  get all the documents in this database repository. The documents are
	 *  returned as a DocumentSet.
	 *
	 *@param  user  Description of the Parameter
	 *@return       The allDocuments value
	 */
	public abstract DocumentSet getAllDocuments(DocumentSet docs);

	/**
	 *  find elements by their tag name. This method is comparable to the DOM's
	 *  method call getElementsByTagName. All elements matching tagName and
	 *  belonging to one of the documents in the DocumentSet docs are returned.
	 *
	 *@param  docs  Description of the Parameter
	 *@param  name  Description of the Parameter
	 *@return       The attributesByName value
	 */
	public abstract NodeSet getAttributesByName(DocumentSet docs, QName qname);

	/**
	 *  Gets the collection attribute of the DBBroker object
	 *
	 *@param  name  Description of the Parameter
	 *@return       The collection value
	 */
	public abstract Collection getCollection(String name);

	public Collection getCollection(String name, long address) {
		return null;
	}

	/**
	 *  get the configuration.
	 *
	 *@return    The configuration value
	 */
	public Configuration getConfiguration() {
		return config;
	}

	/**
	 *  Gets the dOMIterator attribute of the DBBroker object
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The dOMIterator value
	 */
	public Iterator getDOMIterator(Document doc, long gid) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  Gets the dOMIterator attribute of the DBBroker object
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        The dOMIterator value
	 */
	public Iterator getDOMIterator(NodeProxy proxy) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	public Iterator getNodeIterator(NodeProxy proxy) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  return the type of database this broker is connected to.
	 *
	 *@return    one of the constants defined above.
	 */
	public abstract int getDatabaseType();

	/**
	 *  get a document by it's file name. The document's file name is used to
	 *  identify a document. File names are stored without the leading path.
	 *
	 *@param  fileName                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The document value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public abstract Document getDocument(String fileName)
		throws PermissionDeniedException;

	/**
	 *  Gets the documentsByCollection attribute of the DBBroker object
	 *
	 *@param  collection                     Description of the Parameter
	 *@return                                The documentsByCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public abstract DocumentSet getDocumentsByCollection(String collection, DocumentSet docs)
		throws PermissionDeniedException;
		
	/**
	 *  Gets the documentsByCollection attribute of the DBBroker object
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  inclusive                      Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The documentsByCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public abstract DocumentSet getDocumentsByCollection(
		String collection, DocumentSet docs, boolean inclusive)
		throws PermissionDeniedException;

	/**
	 *  get a common prefix for a namespace URI. It should be guaranteed that
	 *  only one prefix is associated with one namespace URI throughout the
	 *  database.
	 *
	 *@param  namespace  Description of the Parameter
	 *@return            The namespacePrefix value
	 */
	public String getNamespacePrefix(String namespace) {
		return "";
	}

	/**
	 *  get the namespace associated with the given prefix. Every broker
	 *  subclass should keep an internal map, where it stores the prefixes used
	 *  for different namespaces. It should be guaranteed that only one prefix
	 *  is associated with one namespace URI.
	 *
	 *@param  prefix  Description of the Parameter
	 *@return         The namespaceURI value
	 */
	public String getNamespaceURI(String prefix) {
		return "";
	}

	/**
	 *  Gets the nextDocId attribute of the DBBroker object
	 *
	 *@param  collection  Description of the Parameter
	 *@return             The nextDocId value
	 */
	public abstract int getNextDocId(Collection collection);

	/**
	 *  Gets the nodeValue attribute of the DBBroker object
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        The nodeValue value
	 */
	public String getNodeValue(NodeProxy proxy) {
		throw new RuntimeException("not implemented for this storage backend");
	}

	/**
	 *  find all Nodes whose string value is equal to expr in the document set.
	 *
	 *@param  context   Description of the Parameter
	 *@param  docs      Description of the Parameter
	 *@param  relation  Description of the Parameter
	 *@param  expr      Description of the Parameter
	 *@return           The nodesEqualTo value
	 */
	public abstract NodeSet getNodesEqualTo(
		NodeSet context,
		DocumentSet docs,
		int relation,
		String expr);

	/**
	 *  Retrieve a collection by name. This method is used by NativeBroker.java.
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The orCreateCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Collection getOrCreateCollection(String name)
		throws PermissionDeniedException {
		return null;
	}

	/**
	 *  get a range of nodes with given owner document from the database,
	 *  starting at first and ending at last.
	 *
	 *@param  doc    the document the node's belong to
	 *@param  first  unique id of the first node to retrieve
	 *@param  last   unique id of the last node to retrieve
	 *@return        The range value
	 */
	public abstract NodeList getRange(Document doc, long first, long last);

	/**
	 *  get an instance of the Serializer used for converting nodes back to XML.
	 *  Subclasses of DBBroker may have specialized subclasses of Serializer to
	 *  convert a node into an XML-string
	 *
	 *@return    The serializer value
	 */
	public abstract Serializer getSerializer();

	/**
	 *  get the TextSearchEngine associated with this broker. Every subclass of
	 *  DBBroker will have it's own implementation of TextSearchEngine.
	 *
	 *@return    The textEngine value
	 */
	public abstract TextSearchEngine getTextEngine();

	/**
	 *  Gets the caseSensitive attribute of the DBBroker object
	 *
	 *@return    The caseSensitive value
	 */
	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public abstract Serializer newSerializer();

	/**
	 *  get a node with given owner document and id from the database.
	 *
	 *@param  doc  the document the node belongs to
	 *@param  gid  the node's unique identifier
	 *@return      Description of the Return Value
	 */
	public abstract Node objectWith(Document doc, long gid);
	public abstract Node objectWith(NodeProxy p);

	/**
	 *  associate a prefix with a given namespace. Every broker subclass should
	 *  keep an internal map, where it stores the prefixes used for different
	 *  namespaces. It should be guaranteed that only one prefix is associated
	 *  with one namespace URI.
	 *
	 *@param  namespace  Description of the Parameter
	 *@param  prefix     Description of the Parameter
	 */
	public void registerNamespace(String namespace, String prefix) {
		// do nothing
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name                           Description of the Parameter
	 *@return                                Description of the Return Value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public abstract boolean removeCollection(String name)
		throws PermissionDeniedException;

	/**
	 *  remove the document with the given document name.
	 *
	 *@param  docName                        Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public abstract void removeDocument(String docName)
		throws PermissionDeniedException;

	/**
	 *  Store a collection into the database.
	 *
	 *@param  collection  Description of the Parameter
	 */
	public abstract void saveCollection(Collection collection)
		throws PermissionDeniedException;

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
	}

	public void closeDocument() {
	}

	/**
	 *  shutdown the broker. All open files, jdbc connections etc. should be
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
	public abstract void store(NodeImpl node, CharSequence currentPath);

	/**
	 *  Store a document into the database.
	 *
	 *@param  doc  Description of the Parameter
	 */
	public abstract void storeDocument(DocumentImpl doc);

	public abstract void storeBinaryResource(BLOBDocument blob, byte[] data);
	
	public abstract byte[] getBinaryResourceData(final BLOBDocument blob);
	
	public abstract void removeBinaryResource(final BLOBDocument blob) throws PermissionDeniedException;
	
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

}