/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.Iterator;
import java.util.Map;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.Paged;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.AttrImpl;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.store.BFile;
import org.exist.storage.store.CollectionStore;
import org.exist.storage.store.DOMFile;
import org.exist.storage.store.StorageAddress;
import org.exist.storage.sync.Sync;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Main class for the native XML storage backend.
 * 
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 *
 *@author     Wolfgang Meier
 */
public class NewNativeBroker extends DBBroker {
	
    /**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(NewNativeBroker.class);

	static final String DATABASE_IS_READ_ONLY = "database is read-only";
	static final String ROOT_COLLECTION = "/db";
    static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
	
	/** default buffer size setting */
	protected final static int BUFFERS = 256;

	/** check available memory after storing MEM_LIMIT_CHECK nodes */
	protected final static int MEM_LIMIT_CHECK = 10000;

	/** Indexer to access the collection db */
    protected NativeCollectionIndexer collectionIndexer = null;
    
    /** Indexer to access the dom db */
    protected NativeDomIndexer domIndexer = null;
    
    /** Indexer to access elements db */
	protected NativeElementIndex elementIndex;

	protected NativeTextEngine textEngine;
	protected Serializer xmlSerializer;
	
	protected int defaultIndexDepth = 1;
	protected Map idxPathMap;
	
	protected boolean readOnly = false;
	
	protected int memMinFree;
	
	// used to count the nodes inserted after the last memory check
	protected int nodesCount = 0;

	protected int pageSize;
	
	private final Runtime run = Runtime.getRuntime();

	public NewNativeBroker(BrokerPool pool, Configuration config) throws EXistException {
		super(pool, config);
		String dataDir;
		int buffers, cacheSize;
		String temp;
		boolean compress = false;
		if ((dataDir = (String) config.getProperty("db-connection.data-dir")) == null)
			dataDir = "data";

		if ((pageSize = config.getInteger("db-connection.page-size")) < 0)
			pageSize = 4096;
		if ((buffers = config.getInteger("db-connection.buffers")) < 0)
			buffers = BUFFERS;
		if ((cacheSize = config.getInteger("db-connection.cache-size")) > 0) {
			long totalMem = cacheSize * 1024 * 1024;
			buffers = (int) (totalMem / pageSize / 64);
		}

		if ((defaultIndexDepth = config.getInteger("indexer.index-depth")) < 0)
			defaultIndexDepth = 1;
		if ((memMinFree = config.getInteger("db-connection.min_free_memory")) < 0)
			memMinFree = 5000000;
		
		Paged.setPageSize(pageSize);
		String pathSep = System.getProperty("file.separator", "/");
		int indexBuffers, dataBuffers;
        
		try {
            BFile elementsDb = null;
			if ((elementsDb = (BFile) config.getProperty("db-connection.elements"))
				== null) {
				if ((indexBuffers = config.getInteger("db-connection.elements.buffers"))
					< 0) {
					indexBuffers = buffers * 4;
					dataBuffers = buffers * 10;
				} else
					dataBuffers = indexBuffers >> 2;

				LOG.debug(
					"elements index buffer size: " + indexBuffers + "; " + dataBuffers);
				elementsDb =
					new BFile(pool,
						new File(dataDir + pathSep + "elements.dbx"),
						indexBuffers,
						dataBuffers);
				if (!elementsDb.exists()) {
					LOG.info("creating elements.dbx");
					elementsDb.create();
				} else
					elementsDb.open();

				config.setProperty("db-connection.elements", elementsDb);
				readOnly = elementsDb.isReadOnly();
			}
            
			// Initialize dom store
			DOMFile domDb = null;
			if ((domDb = (DOMFile) config.getProperty("db-connection.dom")) == null) {
				if (config.hasProperty("db-connection.buffers")) {
					indexBuffers = buffers;
					dataBuffers = 512;
				} else {
					indexBuffers = buffers * 4;
					dataBuffers = buffers * 4;
				}
				LOG.debug("page buffer size = " + indexBuffers + "; " + dataBuffers);
				domDb =
					new DOMFile(pool, new File(dataDir + pathSep + "dom.dbx"),
						indexBuffers, dataBuffers);
				if (!domDb.exists()) {
					LOG.info("creating dom.dbx");
					domDb.create();
				} else
					domDb.open();

				config.setProperty("db-connection.dom", domDb);
				if (!readOnly)
					readOnly = domDb.isReadOnly();
			}
			// finally initialize NativeDomIndexer
            domIndexer = new NativeDomIndexer(this, domDb);
            
            // Initialize collection store
			CollectionStore collectionsDb = null;
            
			if ((collectionsDb =
				(CollectionStore) config.getProperty("db-connection.collections"))
				== null) {
				if ((indexBuffers =
					config.getInteger("db-connection.collections.buffers"))
					< 0) {
					indexBuffers = buffers * 6;
					dataBuffers = buffers * 6;
				} else
					dataBuffers = indexBuffers;
				LOG.debug(
					"collections index buffer size: "
						+ indexBuffers
						+ "; "
						+ dataBuffers);
				collectionsDb =
					new CollectionStore(pool,
						new File(dataDir + pathSep + "collections.dbx"),
						indexBuffers,
						dataBuffers);
				if (!collectionsDb.exists()) {
					LOG.info("creating collections.dbx");
					collectionsDb.create();
				} else
					collectionsDb.open();

				config.setProperty("db-connection.collections", collectionsDb);
				if (!readOnly)
					readOnly = collectionsDb.isReadOnly();
			}
			// finally initialize NativeCollectionIndexer
            collectionIndexer = new NativeCollectionIndexer(pool, this, collectionsDb);
            
            // set readonly attributes
            collectionIndexer.setReadOnly(readOnly);
            domIndexer.setReadOnly(readOnly);
            
			if (readOnly)
				LOG.info("database runs in read-only mode");
			
			idxPathMap = (Map) config.getProperty("indexer.map");
			textEngine = new NativeTextEngine(this, config, buffers);
			xmlSerializer = new NativeSerializer(this, config);
			elementIndex = new NativeElementIndex(this, config, elementsDb);
			user = new User("admin", null, "dba");
			if(pool.isInitializing())
				getOrCreateCollection(ROOT_COLLECTION);
		} catch (DBException e) {
			LOG.debug("failed to initialize database: " + e.getMessage(), e);
			throw new EXistException(e);
		} catch (PermissionDeniedException e) {
			LOG.debug("failed to initialize database: " + e.getMessage(), e);
			throw new EXistException(e);
		}
	}

	
	public void addObserver(Observer o) {
		super.addObserver(o);
		textEngine.addObserver(o);
		elementIndex.addObserver(o);
	}

	public ElementIndex getElementIndex() {
	    return elementIndex;
	}

	public void flush() {
		textEngine.flush();
		elementIndex.flush();
		if (symbols != null && symbols.hasChanged())
			try {
				saveSymbols();
			} catch (EXistException e) {
				LOG.warn(e.getMessage(), e);
			}
		nodesCount = 0;
	}

	public void endRemove() {
		textEngine.remove();
		elementIndex.remove();
	}

	/**
	 *  get all the documents in this database repository. The documents are
	 *  returned as a DocumentSet.
	 *
	 *@param  user  Description of the Parameter
	 *@return       The allDocuments value
	 */
	public DocumentSet getAllDocuments(DocumentSet docs) {
		long start = System.currentTimeMillis();
		Collection root = null;
		try {
			root = openCollection(ROOT_COLLECTION, Lock.READ_LOCK);
			root.allDocs(this, docs, true, false);
			if (LOG.isDebugEnabled()) {
				LOG.debug("getAllDocuments(DocumentSet) - end - "
						+ "loading "
						+ docs.getLength()
						+ " documents from "
						+ docs.getCollectionCount()
						+ "collections took "
						+ (System.currentTimeMillis() - start)
						+ "ms.");
			}
			return docs;
		} finally {
			root.release();
		}
	}

    /**
     * Move later to DBBroker?
     */
	public Collection getCollection(String name) {
		return openCollection(name, -1, Lock.NO_LOCK);
	}
	
    /**
     * Move later to DBBroker?
     */
	public Collection getCollection(String name, long addr) {
		return openCollection(name, addr, Lock.NO_LOCK);
	}
	
    /**
     * Move later to DBBroker?
     */
	public Collection openCollection(String name, int lockMode) {
		return openCollection(name, -1, lockMode);
	}

	/**
	 *  Get collection object. If the collection does not exist, null is
	 *  returned.
	 *
	 *@param  name  Description of the Parameter
	 *@return       The collection value
	 */
	public Collection openCollection(String name, long addr, int lockMode) {
		return collectionIndexer.openCollection(name, addr, lockMode);
	}

	public void reloadCollection(Collection collection) {
		collectionIndexer.reloadCollection(collection);
	}
	
	public Iterator getDOMIterator(NodeProxy proxy) {
		return domIndexer.getDOMIterator(proxy);
	}

	public Iterator getNodeIterator(NodeProxy proxy) {
	    return domIndexer.getNodeIterator(proxy);
	}

	/**
	 *  get a document by it's file name. The document's file name is used to
	 *  identify a document. File names are stored without the leading path.
	 *
	 *@param  fileName                       Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The document value
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Document getDocument(String fileName) throws PermissionDeniedException {
		if (!fileName.startsWith("/"))
			fileName = '/' + fileName;
		if (!fileName.startsWith("/db"))
		    fileName = "/db" + fileName;

		int pos = fileName.lastIndexOf('/');
		String collName = fileName.substring(0, pos);
		String docName = fileName.substring(pos + 1);
		
		Collection collection = getCollection(collName);
		if (collection == null) {
			LOG.debug("collection " + collName + " not found!");
			return null;
		}
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("permission denied to read collection");
		DocumentImpl doc = collection.getDocument(this, docName);
		if (doc == null) {
			LOG.debug("document " + fileName + " not found!");
			return null;
		}
//		if (!doc.getPermissions().validate(user, Permission.READ))
//			throw new PermissionDeniedException("not allowed to read document");
		return doc;
	}

	public DocumentImpl openDocument(String docPath, int lockMode) throws PermissionDeniedException {
		if (!docPath.startsWith("/"))
			docPath = '/' + docPath;
		if (!docPath.startsWith("/db"))
		    docPath = "/db" + docPath;

		int pos = docPath.lastIndexOf('/');
		String collName = docPath.substring(0, pos);
		String docName = docPath.substring(pos + 1);
		
		Collection collection = null;
		try {
			collection = openCollection(collName, lockMode);
			if (collection == null) {
				LOG.debug("collection " + collName + " not found!");
				return null;
			}
			if (!collection.getPermissions().validate(user, Permission.READ)) {
				throw new PermissionDeniedException("permission denied to read collection");
			}
			DocumentImpl doc = collection.getDocumentWithLock(this, docName, lockMode);
			if (doc == null) {
				LOG.debug("document " + docPath + " not found!");
				return null;
			}
	//		if (!doc.getPermissions().validate(user, Permission.READ))
	//			throw new PermissionDeniedException("not allowed to read document");
			return doc;
		} catch (LockException e) {
			LOG.warn("Could not acquire lock on document " + docPath, e);
		} finally {
			collection.release();
		}
		return null;
	}
	
	public DocumentSet getDocumentsByCollection(String collection, DocumentSet docs)
		throws PermissionDeniedException {
		return getDocumentsByCollection(collection, docs, true);
	}

	public DocumentSet getDocumentsByCollection(
		String collection,
		DocumentSet docs,
		boolean inclusive)
		throws PermissionDeniedException {
		long start = System.currentTimeMillis();
		if (collection == null || collection.length() == 0)
			return docs;
		if (collection.charAt(0) != '/')
			collection = "/" + collection;
		if (!collection.startsWith(ROOT_COLLECTION))
			collection = ROOT_COLLECTION + collection;
		Collection root = getCollection(collection);
		if (root == null) {
			LOG.debug("collection " + collection + " not found");
			return docs;
		}
		docs = root.allDocs(this, docs, inclusive, false);
		LOG.debug(
			"loading "
				+ docs.getLength()
				+ " documents from collection "
				+ collection
				+ " took "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return docs;
	}

	/**
	 *  get all the documents in this database matching the given
	 *  document-type's name.
	 *
	 *@param  doctypeName  Description of the Parameter
	 *@param  user         Description of the Parameter
	 *@return              The documentsByDoctype value
	 */
	public DocumentSet getDocumentsByDoctype(String doctypeName, DocumentSet result) {
		DocumentSet docs = getAllDocuments(new DocumentSet());
		DocumentImpl doc;
		DocumentType doctype;
		for (Iterator i = docs.iterator(); i.hasNext();) {
			doc = (DocumentImpl) i.next();
			doctype = doc.getDoctype();
			if (doctype == null)
				continue;
			if (doctypeName.equals(doctype.getName())
				&& doc.getCollection().getPermissions().validate(user, Permission.READ)
				&& doc.getPermissions().validate(user, Permission.READ))
				result.add(doc);

		}
		return result;
	}

	
	
	public int getNextDocId(Collection collection) {
		return collectionIndexer.getNextDocId(collection);
	}

	/**
	 * Index a single node, which has been added through an XUpdate
	 * operation. This method is only called if inserting the node is possible
	 * without changing the node identifiers of sibling or parent nodes. In other 
	 * cases, reindex will be called.
	 */
	public void index(final NodeImpl node, NodePath currentPath) {
	    // first, check available memory
		if (++nodesCount > MEM_LIMIT_CHECK) {
			final int percent = (int) (run.freeMemory() / (run.totalMemory() / 100));
			if (percent < memMinFree) {
				//LOG.info(
				//	"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
				flush();
				System.gc();
				LOG.info(
					"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
			}
		}
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		final long address = node.getInternalAddress();
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		if (address < 0)
			LOG.debug("node " + gid + ": internal address missing");
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		NodeProxy tempProxy;
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				qname = node.getQName();
				qname.setNameType(ElementValue.ELEMENT);
				tempProxy = new NodeProxy(doc, gid, address);
				tempProxy.setHasIndex(idx == null || currentPath == null || idx.match(currentPath));
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				tempProxy = new NodeProxy(doc, gid, address);
				tempProxy.setHasIndex(idx == null || currentPath == null || idx.match(currentPath));
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path)
				boolean indexAttribs = true;
				if(idx != null) {
				    if(idx.getIncludeAttributes()) {
				        if(currentPath != null) {
						    currentPath.addComponent('@' + nodeName);
						    indexAttribs = idx.match(currentPath);
						    currentPath.removeLastComponent();
				        }
				    } else
				        indexAttribs = false;
				}
				if(indexAttribs)
					textEngine.storeAttribute(idx, (AttrImpl) node);
				
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
			    boolean indexText = true;
				if(idx != null && currentPath != null)
				    indexText = idx.match(currentPath);	                	                
                boolean valore = (idx == null || currentPath == null ? false : idx.preserveContent(currentPath));
				textEngine.storeText(idx, (TextImpl) node, valore);
				break;
		}
		if (nodeType == Node.ELEMENT_NODE && level <= depth) {
			domIndexer.n1(doc, node, gid);
		}
	}

	/**
	 * Reindex the nodes in the document. This method will either reindex all
	 * descendant nodes of the passed node, or all nodes below some level of
	 * the document if node is null.
	 */
	public void reindex(final DocumentImpl oldDoc, final DocumentImpl doc, 
			final NodeImpl node) {
		int idxLevel = doc.reindexRequired();
		if (idxLevel < 0) {
			flush();
			return;
		}
		oldDoc.setReindexRequired(idxLevel);
		if (node == null)
			LOG.debug("reindexing level " + idxLevel + " of document " + doc.getDocId());
//		checkTree(doc);
		
		final long start = System.currentTimeMillis();
		// remove all old index keys from the btree 
		domIndexer.n2(doc, oldDoc, node);
        
		try {
			// now reindex the nodes
			Iterator iterator;
			if (node == null) {
				NodeList nodes = doc.getChildNodes();
				NodeImpl n;
				for (int i = 0; i < nodes.getLength(); i++) {
					n = (NodeImpl) nodes.item(i);
					iterator =
						getNodeIterator(
							new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
					iterator.next();
					scanNodes(iterator, n, new NodePath(), false);
				}
			} else {
				iterator =
					getNodeIterator(
						new NodeProxy(doc, node.getGID(), node.getInternalAddress()));
				iterator.next();
				scanNodes(iterator, node, node.getPath(), false);
			}
		} catch(Exception e) {
			LOG.error("Error occured while reindexing document: " + e.getMessage(), e);
		}
		elementIndex.reindex(oldDoc, node);
		textEngine.reindex(oldDoc, node);
		doc.setReindexRequired(-1);
//		checkTree(doc);
		LOG.debug("reindex took " + (System.currentTimeMillis() - start) + "ms.");
	}

	/**
	 * Reindex the given node after the DOM tree has been 
	 * modified by an XUpdate.
	 * 
	 * @param node
	 * @param currentPath
	 */
	private void reindex(final NodeImpl node, NodePath currentPath) {
		if (node.getGID() < 0)
			LOG.debug("illegal node: " + node.getGID() + "; " + node.getNodeName());
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		final short nodeType = node.getNodeType();
		final long gid = node.getGID();
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		if (level >= doc.reindexRequired()) {
			NodeIndexListener listener = doc.getIndexListener();
			// jmv if ((listener = doc.getIndexListener()) != null)
			if(listener != null)
				listener.nodeChanged(node);
			if (nodeType == Node.ELEMENT_NODE && level <= depth) {
				domIndexer.n4(doc, node, gid);
			}
			final NodeProxy tempProxy =
				new NodeProxy(doc, gid, node.getInternalAddress());
			QName qname;
			switch (nodeType) {
				case Node.ELEMENT_NODE :
					// save element by calling ElementIndex
					qname = node.getQName();
					qname.setNameType(ElementValue.ELEMENT);
					tempProxy.setHasIndex(
						idx == null || idx.match(currentPath));
					elementIndex.setDocument(doc);
					elementIndex.addRow(qname, tempProxy);
					break;
				case Node.ATTRIBUTE_NODE :
					tempProxy.setHasIndex(
						idx == null || idx.match(currentPath));
					elementIndex.setDocument(doc);
					qname =
						new QName(
							node.getLocalName(),
							node.getNamespaceURI(),
							node.getPrefix());
					qname.setNameType(ElementValue.ATTRIBUTE);
					elementIndex.addRow(qname, tempProxy);
					// check if attribute value should be fulltext-indexed
					// by calling IndexPaths.match(path) 
					boolean indexAttribs = true;
					if(idx != null) {
					    if(idx.getIncludeAttributes()) {
						    currentPath.addComponent('@' + ((AttrImpl)node).getName());
						    indexAttribs = idx.match(currentPath);
						    currentPath.removeLastComponent();
					    } else
					        indexAttribs = false;
					}
					if (indexAttribs)
						textEngine.storeAttribute(idx, (AttrImpl) node);
					// if the attribute has type ID, store the ID-value
					// to the element index as well
					if (((AttrImpl) node).getType() == AttrImpl.ID) {
						qname = new QName("&" + ((AttrImpl) node).getValue(), "", null);
						qname.setNameType(ElementValue.ATTRIBUTE_ID);
						elementIndex.addRow(qname, tempProxy);
					}
					break;
				case Node.TEXT_NODE :
					// check if this textual content should be fulltext-indexed
					// by calling IndexPaths.match(path)
					if (idx == null || idx.match(currentPath)){
						boolean valore = (idx == null ? false : idx.preserveContent(currentPath));
						textEngine.storeText(idx, (TextImpl) node, valore);}
					break;
			}
		}
	}

	/**
	 * Called by reindex to walk through all nodes in the tree and reindex them
	 * if necessary.
	 * 
	 * @param iterator
	 * @param node
	 * @param currentPath
	 */
	private void scanNodes(Iterator iterator, NodeImpl node, NodePath currentPath,
	        boolean fullReindex) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.addComponent(node.getNodeName());
		if(fullReindex)
		    index(node, currentPath);
		else
		    reindex(node, currentPath);
		if (node.hasChildNodes()) {
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			final long firstChildId = XMLUtil.getFirstChildId(doc, node.getGID());
			if (firstChildId < 0) {
				LOG.fatal(
					"no child found: expected = "
						+ node.getChildCount()
						+ "; node = "
						+ node.getNodeName()
						+ "; gid = "
						+ node.getGID());
				throw new IllegalStateException("wrong node id");
			}
			final long lastChildId = firstChildId + node.getChildCount();
			NodeImpl child;
			for (long gid = firstChildId; gid < lastChildId; gid++) {
				child = (NodeImpl) iterator.next();
				if(child == null)
					LOG.debug("child " + gid + " not found for node: " + node.getNodeName() +
							"; last = " + lastChildId + "; children = " + node.getChildCount());
				child.setGID(gid);
				scanNodes(iterator, child, currentPath, fullReindex);
			}
		}
		if(node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.removeLastComponent();
	}

	/**
	 * Reindex the nodes in the document. This method will either reindex all
	 * descendant nodes of the passed node, or all nodes below some level of
	 * the document if node is null.
	 */
	private void reindex(DocumentImpl doc) {
		LOG.debug("Reindexing document " + doc.getFileName());
		final long start = System.currentTimeMillis();
		Iterator iterator;
		NodeList nodes = doc.getChildNodes();
		NodeImpl n;
		for (int i = 0; i < nodes.getLength(); i++) {
		    n = (NodeImpl) nodes.item(i);
		    iterator =
		        getNodeIterator(
		                new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
		    iterator.next();
		    scanNodes(iterator, n, new NodePath(), true);
		}
		flush();
		LOG.debug("reindex took " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	public void copyResource(DocumentImpl doc, Collection destination, String newName) 
	throws PermissionDeniedException, LockException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    Collection collection = doc.getCollection();
	    if(!collection.getPermissions().validate(user, Permission.READ))
	        throw new PermissionDeniedException("Insufficient privileges to copy resource " +
	                doc.getFileName());
	    if(!doc.getPermissions().validate(user, Permission.READ))
	    	throw new PermissionDeniedException("Insufficient privileges to copy resource " +
	                doc.getFileName());
	    if(newName == null) {
            int p = doc.getFileName().lastIndexOf('/');
            newName = doc.getFileName().substring(p + 1);
        }

	    Lock lock = null;
	    try {
	        lock = collectionIndexer.getLock();
	        lock.acquire(Lock.WRITE_LOCK);
	        // check if the move would overwrite a collection
	        if(getCollection(destination.getName() + '/' + newName) != null)
	            throw new PermissionDeniedException("A resource can not replace an existing collection");
	        DocumentImpl oldDoc = destination.getDocument(this, newName);
	        if(oldDoc != null) {
	        	if(doc.getDocId() == oldDoc.getDocId())
	            	throw new PermissionDeniedException("Cannot copy resource to itself");
	            if(!destination.getPermissions().validate(user, Permission.UPDATE))
	                throw new PermissionDeniedException("Resource with same name exists in target " +
	                		"collection and update is denied");
	            if(!oldDoc.getPermissions().validate(user, Permission.UPDATE))
	                throw new PermissionDeniedException("Resource with same name exists in target " +
	                		"collection and update is denied");
	            collection.removeDocument(this, oldDoc.getFileName());
	        } else {
	        	if(!destination.getPermissions().validate(user, Permission.WRITE))
	    	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	    	                destination.getName());
	        }
	        DocumentImpl newDoc = new DocumentImpl(this, newName, destination);
	        newDoc.copyOf(doc);
	        newDoc.setDocId(getNextDocId(destination));
	        copyResource(doc, newDoc);
	        destination.addDocument(this, newDoc);
	        updateDocument(newDoc);
//	        saveCollection(destination);
		} catch (TriggerException e) {
			throw new PermissionDeniedException(e.getMessage());
		} finally {
	    	lock.release();
	    }
	}
	
	private void copyResource(DocumentImpl oldDoc, DocumentImpl newDoc) {
		LOG.debug("Copying document " + oldDoc.getFileName() + " to " + 
				newDoc.getName());
		final long start = System.currentTimeMillis();
		Iterator iterator;
		NodeList nodes = oldDoc.getChildNodes();
		NodeImpl n;
		for (int i = 0; i < nodes.getLength(); i++) {
		    n = (NodeImpl) nodes.item(i);
		    iterator =
		        getNodeIterator(
		                new NodeProxy(oldDoc, n.getGID(), n.getInternalAddress()));
		    iterator.next();
		    copyNodes(iterator, n, new NodePath(), newDoc, true);
		}
		flush();
		closeDocument();
		LOG.debug("Copy took " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	public void defrag(final DocumentImpl doc) {
		LOG.debug("============> Defragmenting document " + 
		        doc.getCollection().getName() + '/' + doc.getFileName());
		final long start = System.currentTimeMillis();
		try {
//			checkTree(doc);
			
			// remember this for later remove
		    final long firstChild = doc.getFirstChildAddress();
		    	
			// dropping old structure index
			elementIndex.dropIndex(doc);
			
			// dropping dom index
			domIndexer.n5(doc);
            
			// create a copy of the old doc to copy the nodes into it
			DocumentImpl tempDoc = new DocumentImpl(this, doc.getFileName(), doc.getCollection());
			tempDoc.copyOf(doc);
			tempDoc.setDocId(doc.getDocId());
			
			// copy the nodes
			Iterator iterator;
			NodeList nodes = doc.getChildNodes();
			NodeImpl n;
			for (int i = 0; i < nodes.getLength(); i++) {
			    n = (NodeImpl) nodes.item(i);
			    iterator =
			        getNodeIterator(
			                new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
			    iterator.next();
			    copyNodes(iterator, n, new NodePath(), tempDoc, false);
			}
			flush();
			
//			checkTree(tempDoc);
			
			// remove the old nodes
			domIndexer.n6(firstChild);
			
//			checkTree(tempDoc);
			
			doc.copyChildren(tempDoc);
			doc.setSplitCount(0);
			doc.setAddress(-1);
			doc.setPageCount(tempDoc.getPageCount());
//			checkTree(doc);
			storeDocument(doc);
			LOG.debug("new doc address = " + StorageAddress.toString(doc.getAddress()));
			closeDocument();
//			new DOMTransaction(this, domDb, Lock.READ_LOCK) {
//				public Object start() throws ReadOnlyException {
//					LOG.debug("Pages used: " + domDb.debugPages(doc));
//					return null;
//				}
//			}.run();
			saveCollection(doc.getCollection());
			LOG.debug("Defragmentation took " + (System.currentTimeMillis() - start) + "ms.");
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY, e);
		} catch (PermissionDeniedException e) {
		    LOG.warn(DATABASE_IS_READ_ONLY, e);
        }
	}
	
	private void copyNodes(Iterator iterator, NodeImpl node, NodePath currentPath, 
	        DocumentImpl newDoc, boolean index) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.addComponent(node.getNodeName());
		final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
		node.setOwnerDocument(newDoc);
		node.setInternalAddress(-1);
		store(node, currentPath, index);
		if(node.getGID() == 1)
		    newDoc.appendChild(node);
		node.setOwnerDocument(doc);
		
		if (node.hasChildNodes()) {
			final long firstChildId = XMLUtil.getFirstChildId(doc, node.getGID());
			if (firstChildId < 0) {
				LOG.fatal(
					"no child found: expected = "
						+ node.getChildCount()
						+ "; node = "
						+ node.getNodeName()
						+ "; gid = "
						+ node.getGID());
				throw new IllegalStateException("wrong node id");
			}
			final long lastChildId = firstChildId + node.getChildCount();
			NodeImpl child;
			for (long gid = firstChildId; gid < lastChildId; gid++) {
				child = (NodeImpl) iterator.next();
				if(child == null)
					LOG.debug("child " + gid + " not found for node: " + node.getNodeName() +
							"; last = " + lastChildId + "; children = " + node.getChildCount());
				child.setGID(gid);
				copyNodes(iterator, child, currentPath, newDoc, index);
			}
		}
		if(node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.removeLastComponent();
	}
	
	public void consistencyCheck(DocumentImpl doc) throws EXistException {
		if(xupdateConsistencyChecks) {
			LOG.debug("Checking document " + doc.getFileName());
			checkTree(doc);
//			elementIndex.consistencyCheck(doc);
		}
	}
	
	public void checkTree(final DocumentImpl doc) {
		domIndexer.checkTree(doc);
	}

	public String getNodeValue(final NodeProxy proxy) {
		return domIndexer.getNodeValue(proxy);
	}

	/**
	 *  This method handles left or left-and-right truncated search terms. In
	 *  these cases it is not possible to use the cdata-index, since it contains
	 *  just the first 8 bytes of every cdata-string.
	 *
	 *@param  context   Description of the Parameter
	 *@param  docs      Description of the Parameter
	 *@param  relation  Description of the Parameter
	 *@param  expr      Description of the Parameter
	 *@return           The nodesEqualTo value
	 */
	public NodeSet getNodesEqualTo(
		NodeSet context,
		DocumentSet docs,
		int relation,
		String expr,
		Collator collator) {
		//		long start = System.currentTimeMillis();
		// NodeSet temp;
		int truncation = Constants.TRUNC_NONE;
		if (expr.length() > 0 && expr.charAt(0) == '%') {
			expr = expr.substring(1);
			truncation = Constants.TRUNC_LEFT;
		}
		if (expr.length() > 1 && expr.charAt(expr.length() - 1) == '%') {
			expr = expr.substring(0, expr.length() - 1);
			truncation =
				(truncation == Constants.TRUNC_LEFT)
					? Constants.TRUNC_BOTH
					: Constants.TRUNC_RIGHT;
		}
		if (!isCaseSensitive())
			expr = expr.toLowerCase();
		NodeSet result = domIndexer.scanSequential(context, docs, relation, truncation, expr, collator);
		//				LOG.debug(
		//					"searching "
		//						+ result.getLength()
		//						+ " nodes took "
		//						+ (System.currentTimeMillis() - start)
		//						+ "ms.");
		return result;
	}
	
	/**
	 *  get collection object If the collection does not yet exists, it is
	 *  created automatically.
	 *
	 *@param  name                           the collection's name
	 *@param  user                           Description of the Parameter
	 *@return                                The orCreateCollection value
	 *@exception  PermissionDeniedException  Description of the Exception
	 *@author=@author
	 */
	public Collection getOrCreateCollection(String name)
		throws PermissionDeniedException {
		return collectionIndexer.getOrCreateCollection(name);
	}
	
	/**
	 *  Gets a range of nodes, starting with first, ending with last
	 *
	 *@param  doc    the document
	 *@param  first  node-id of the first node
	 *@param  last   node-id of the last node
	 *@return        a list of nodes
	 */
	public NodeList getRange(final Document doc, final long first, final long last) {
		NodeListImpl result = new NodeListImpl((int) (last - first + 1));
		for (long gid = first; gid <= last; gid++) {
			result.add(objectWith(doc, gid));
		}
		return result;
	}

	public Serializer getSerializer() {
		xmlSerializer.reset();
		return xmlSerializer;
	}

	public TextSearchEngine getTextEngine() {
		return textEngine;
	}

	public Serializer newSerializer() {
		return new NativeSerializer(this, null);
	}

	public Node objectWith(final Document doc, final long gid) {
		return domIndexer.objectWith(doc, gid);
	}

	public Node objectWith(final NodeProxy p) {
		return domIndexer.objectWith(p);
	}

	public void dropIndex(Collection collection) throws PermissionDeniedException {
	    if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    if (!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("insufficient privileges on collection " + 
	                collection.getName());
	    
	    textEngine.dropIndex(collection);
	    elementIndex.dropIndex(collection);
	    
	    for (Iterator i = collection.iterator(this); i.hasNext();) {
	        final DocumentImpl doc = (DocumentImpl) i.next();
	        domIndexer.n10(doc);
	    }
	}
	
	public void reindex(Collection collection) throws PermissionDeniedException {
	    if (!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("insufficient privileges on collection " + collection.getName());
	    LOG.debug("Reindexing collection " + collection.getName());
	    dropIndex(collection);
	    for(Iterator i = collection.iterator(this); i.hasNext(); ) {
	        DocumentImpl next = (DocumentImpl)i.next();
	        reindex(next);
	    }
	    
	    for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
	        String next = (String)i.next();
	        Collection child = getCollection(collection.getName() + '/' + next);
	        if(child == null)
	            LOG.warn("Collection " + next + " not found");
	        else
	            reindex(child);
	    }
	}
	
	public void reindex(String collectionName) throws PermissionDeniedException {
	    if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    if (!collectionName.startsWith(ROOT_COLLECTION))
	        collectionName = ROOT_COLLECTION + collectionName;
	    
	    Collection collection = getCollection(collectionName);
	    if (collection == null) {
	        LOG.debug("collection " + collectionName + " not found!");
	        return;
	    }
	    reindex(collection);
	}
	
	public boolean removeCollection(Collection collection) throws PermissionDeniedException {
	    if (readOnly)
	        throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    
	    if (!collection.getPermissions().validate(user, Permission.WRITE))
	    	throw new PermissionDeniedException("not allowed to remove collection");
	    
	    final boolean isRoot = collection.getParentPath() == null;
	    
	    final CollectionCache collectionsCache = pool.getCollectionsCache();
	    synchronized(collectionsCache) {
		    String name = collection.getName();
		    
		    if (!isRoot) {
			    // remove from parent collection
			    Collection parent = openCollection(collection.getParentPath(), Lock.WRITE_LOCK);
			    if (parent != null) {
			    	try {
			    		parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
			    		saveCollection(parent);
			    	} catch (LockException e) {
			    		LOG.warn("LockException while removing collection " + name);
			    	} finally {
			    		parent.getLock().release();
			    	}
			    }
		    }
		    
		    // remove child collections
		    String childName;
		    Collection childCollection;
		    LOG.debug("removing sub-collections");
		    for (Iterator i = collection.collectionIterator(); i.hasNext();) {
		    	childName = (String) i.next();
		    	childCollection = openCollection(name + '/' + childName, Lock.WRITE_LOCK);
		    	try {
		    		removeCollection(childCollection);
		    	} finally {
		    		childCollection.getLock().release();
		    	}
		    }
		    
		    Lock lock = collectionIndexer.getLock();
		    try {
		    	lock.acquire(Lock.WRITE_LOCK);
		    	
		    	// if this is not the root collection remove it completely
		    	if (isRoot)
		    		saveCollection(collection);
		    	else {
		    		Value key;
		    		try {
		    			key = new Value(name.getBytes("UTF-8"));
		    		} catch (UnsupportedEncodingException uee) {
		    			key = new Value(name.getBytes());
		    		}	
		    		collectionIndexer.remove(key);
		    		collectionsCache.remove(collection);
				   collectionIndexer.freeCollection(collection.getId());
		    	}

		    } catch (LockException e) {
		    	LOG.warn("Failed to acquire lock on collections.dbx");
		    } catch (ReadOnlyException e) {
		    	throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		    } finally {
		    	lock.release();
		    }
	    
		    textEngine.dropIndex(collection);
		    elementIndex.dropIndex(collection);
		    
		    LOG.debug("removing resources ...");
		    for (Iterator i = collection.iterator(this); i.hasNext();) {
		    	final DocumentImpl doc = (DocumentImpl) i.next();
		    	domIndexer.n9(doc);
		    	collectionIndexer.freeDocument(doc.getDocId());
		    }
		    return true;
	    }
	}

	public void removeDocument(final DocumentImpl document, boolean freeDocId) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		try {
            if (LOG.isInfoEnabled()) {
                LOG.info("removeDocument() - "
                    + "removing document "
                    + document.getDocId()
                    + " ...");
            }

			elementIndex.dropIndex(document);
			textEngine.dropIndex(document);
			domIndexer.n7(document);
            
			if(freeDocId)
			    collectionIndexer.freeDocument(document.getDocId());
		} catch (ReadOnlyException e) {
            LOG.warn("removeDocument(String) - " + DATABASE_IS_READ_ONLY);
		}
	}

	public void removeNode(final NodeImpl node, NodePath currentPath) {
		final IndexPaths idx =
			(IndexPaths) idxPathMap.get(node.getOwnerDocument().getDoctype().getName());
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		domIndexer.n3(doc, node);
        
		final NodeProxy tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// save element by calling ElementIndex
				qname = node.getQName();
				qname.setNameType(ElementValue.ELEMENT);
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				elementIndex.setDocument(doc);
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				boolean indexAttribs = true;
				if(idx != null) {
				    if(idx.getIncludeAttributes()) {
					    currentPath.addComponent('@' + ((AttrImpl)node).getName());
					    indexAttribs = idx.match(currentPath);
					    currentPath.removeLastComponent();
				    } else
				        indexAttribs = false;
				}
				if (indexAttribs)
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				if (idx == null || idx.match(currentPath)){
					boolean valore = (idx == null ? false : idx.preserveContent(currentPath));
					textEngine.storeText(idx, (TextImpl) node, valore);}
				break;
		}
	}

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
		Lock lock = collectionIndexer.getLock();
		try {
			lock.acquire();

			Value name;
			try {
				name = new Value(collection.getName().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				LOG.debug(uee);
				name = new Value(collection.getName().getBytes());
			}
			storeDocument(doc);
			VariableByteOutputStream ostream = new VariableByteOutputStream(6);
			doc.write(ostream);
			long address = collectionIndexer.append(name, ostream.data());
			if (address < 0) {
				LOG.debug("could not store collection data for " + collection.getName());
				return;
			}
			collection.setAddress(address);
			ostream.close();
		} catch (IOException ioe) {
			LOG.debug(ioe);
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections.dbx");
		} finally {
			lock.release();
		}
	}

	public void saveCollection(Collection collection) throws PermissionDeniedException {
		collectionIndexer.saveCollection(collection);
	}

	public void moveResource(DocumentImpl doc, Collection destination, String newName)
	throws PermissionDeniedException, LockException {
	    if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    Collection collection = doc.getCollection();
	    if(!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("Insufficient privileges to move resource " +
	                doc.getFileName());
	    if(!doc.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("Insufficient privileges to move resource " +
	                doc.getFileName());
	    if(newName == null) {
            int p = doc.getFileName().lastIndexOf('/');
            newName = doc.getFileName().substring(p + 1);
        }
	    Lock lock = null;
	    try {
	        lock = collectionIndexer.getLock();
	        lock.acquire(Lock.WRITE_LOCK);
	        // check if the move would overwrite a collection
	        if(getCollection(destination.getName() + '/' + newName) != null)
	            throw new PermissionDeniedException("A resource can not replace an existing collection");
	        DocumentImpl oldDoc = destination.getDocument(this, newName);
	        if(oldDoc != null) {
	        	if(doc.getDocId() == oldDoc.getDocId())
	            	throw new PermissionDeniedException("Cannot move resource to itself");
	            if(!destination.getPermissions().validate(user, Permission.UPDATE))
	                throw new PermissionDeniedException("Resource with same name exists in target " +
	                		"collection and update is denied");
	            if(!oldDoc.getPermissions().validate(user, Permission.UPDATE))
	                throw new PermissionDeniedException("Resource with same name exists in target " +
	                		"collection and update is denied");
	            collection.removeDocument(this, oldDoc.getFileName());
	        } else
	            if(!destination.getPermissions().validate(user, Permission.WRITE))
	    	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	    	                destination.getName());
	            
	        boolean renameOnly = collection.getId() == destination.getId();
	        collection.unlinkDocument(doc);
	        if(!renameOnly) {
		        elementIndex.dropIndex(doc);
				textEngine.dropIndex(doc);
				saveCollection(collection);
	        }
			doc.setFileName(newName);
			destination.addDocument(this, doc);
	        doc.setCollection(destination);

	        if(!renameOnly) {
		        // reindexing
				reindex(doc);
	        }
			saveCollection(destination);
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(e.getMessage());
        } finally {
	        lock.release();
	    }
	}
	
	public void copyCollection(Collection collection, Collection destination, String newName)
	throws PermissionDeniedException, LockException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		if(!collection.getPermissions().validate(user, Permission.READ))
			throw new PermissionDeniedException("Read permission denied on collection " +
					collection.getName());
		if(collection.getId() == destination.getId())
	    	throw new PermissionDeniedException("Cannot move collection to itself");
		if(!destination.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	                destination.getName());
		if(newName == null) {
            int p = collection.getName().lastIndexOf('/');
            newName = collection.getName().substring(p + 1);
        }
	    if(newName.indexOf('/') > -1)
	        throw new PermissionDeniedException("New collection name is illegal (may not contain a '/')");
	    //	check if another collection with the same name exists at the destination
	    Collection old = openCollection(newName, Lock.WRITE_LOCK);
	    if(old != null) {
	    	try {
	    		removeCollection(old);
	    	} finally {
	    		old.release();
	    	}
	    }
	    Lock lock = null;
	    try {
	        lock = collectionIndexer.getLock();
	        lock.acquire(Lock.WRITE_LOCK);
	        newName = destination.getName() + '/' + newName;
	        
		    Collection destCollection = getOrCreateCollection(newName);
		    for(Iterator i = collection.iterator(this); i.hasNext(); ) {
		    	DocumentImpl child = (DocumentImpl) i.next();
		    	
		    	DocumentImpl newDoc = new DocumentImpl(this, child.getFileName(), destCollection);
		        newDoc.copyOf(child);
		        copyResource(child, newDoc);
		        flush();
		        destCollection.addDocument(this, child);
		    }
		    saveCollection(destCollection);
		    saveCollection(destination);
        } finally {
	        lock.release();
	    }
	}
	
	public void moveCollection(Collection collection, Collection destination, String newName) 
	throws PermissionDeniedException, LockException {
        collectionIndexer.moveCollection(collection, destination, newName);
	}
	
	public void shutdown() {
		super.shutdown();
		try {
			flush();
			sync(Sync.MAJOR_SYNC);
			textEngine.close();
			domIndexer.close();
			elementIndex.close();
			collectionIndexer.close();
		} catch (Exception e) {
			LOG.debug(e);
			e.printStackTrace();
		}
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
	public void store(final NodeImpl node, NodePath currentPath, boolean index) {
		// first, check available memory
		if (nodesCount > MEM_LIMIT_CHECK) {
			final int percent = (int) (run.freeMemory() / (run.totalMemory() / 100));
			if (percent < memMinFree) {
				//LOG.info(
				//	"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
				flush();
				System.gc();
				LOG.info(
					"total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
			}
		}
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexPaths idx = (IndexPaths) idxPathMap.get(doc.getDoctype().getName());
		final long gid = node.getGID();
		if (gid < 0) {
			LOG.debug("illegal node: " + gid + "; " + node.getNodeName());
			Thread.dumpStack();
			return;
		}
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		// final String localName = node.getLocalName();
		final int depth = idx == null ? defaultIndexDepth : idx.getIndexDepth();
		domIndexer.n8(doc, node, nodeType, gid, depth);
        
		++nodesCount;
		NodeProxy tempProxy = null;
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
				tempProxy.setHasIndex(idx == null || idx.match(currentPath));
				// save element by calling ElementIndex
				elementIndex.setDocument(doc);
				elementIndex.addRow(node.getQName(), tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
				tempProxy.setHasIndex(idx == null || idx.match(currentPath));
				qname =
					new QName(
						node.getLocalName(),
						node.getNamespaceURI(),
						node.getPrefix());
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.setDocument(doc);
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				boolean indexAttribs = index;
				if(index && idx != null) {
				    if(idx.getIncludeAttributes()) {
				        currentPath.addComponent('@' + nodeName);
				        indexAttribs = idx.match(currentPath);
				        currentPath.removeLastComponent();
				    } else
				        indexAttribs = false;
				}
				if(indexAttribs)
					textEngine.storeAttribute(idx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					//LOG.debug("found ID: " + qname.getLocalName());
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				if (index && (idx == null || idx.match(currentPath))){     
	                boolean valore = (idx == null ? false : idx.preserveContent(currentPath));
					textEngine.storeText(idx, (TextImpl) node, valore);
				}	
				break;
		}
	}

	public void storeDocument(final DocumentImpl doc) {
		domIndexer.storeDocument(doc);
	}

	public void updateDocument(DocumentImpl doc) throws LockException, PermissionDeniedException {
		storeDocument(doc);
		saveCollection(doc.getCollection());
	}
	
	public void storeBinaryResource(final BinaryDocument blob, final byte[] data) {
		domIndexer.storeBinaryResource(blob, data);
	}
	
	public byte[] getBinaryResourceData(final BinaryDocument blob) {
		return domIndexer.getBinaryResourceData(blob);
	}

	public void removeBinaryResource(final BinaryDocument blob)
		throws PermissionDeniedException {
		domIndexer.removeBinaryResource(blob);
	}

	public void readDocumentMetadata(final DocumentImpl doc) {
		domIndexer.readDocumentMetadata(doc);
	}

	public void sync(int syncEvent) {
		try {
            collectionIndexer.sync(syncEvent);
			domIndexer.sync(syncEvent);
            
			if(syncEvent == Sync.MAJOR_SYNC) {
				elementIndex.sync();
				textEngine.sync();
				
				Runtime runtime = Runtime.getRuntime();
				LOG.debug("Memory: " + (runtime.totalMemory() / 1024) + "K total; " +
						(runtime.freeMemory() / 1024) + "K free");
				
				// uncomment this to get statistics on page buffer usage
				elementIndex.printStatistics();
				collectionIndexer.printStatistics();
				domIndexer.printStatistics();
			}
		} catch (DBException dbe) {
			dbe.printStackTrace();
			LOG.debug(dbe);
		}
	}

	public int getPageSize() {
	    return pageSize;
	}
	
	public void closeDocument() {
		domIndexer.closeDocument();
	}

	public void update(final NodeImpl node) {
		domIndexer.update(node);
	}

	/**
	 * Physically insert a node into the DOM storage.
	 */
	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		domIndexer.insertAfter(previous, node);
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public final static class NodeRef extends Value {
        /**
         * Log4J Logger for this class
         */
        private static final Logger LOG = Logger.getLogger(NodeRef.class);

		public NodeRef() {
			data = new byte[12];
		}

		public NodeRef(int docId, long gid) {
			data = new byte[12];
			ByteConversion.intToByte(docId, data, 0);
			ByteConversion.longToByte(gid, data, 4);
			len = 12;
			pos = 0;
		}

		public NodeRef(int docId) {
			data = new byte[4];
			ByteConversion.intToByte(docId, data, 0);
			len = 4;
			pos = 0;
		}

		int getDocId() {
			return ByteConversion.byteToInt(data, 0);
		}

		long getGid() {
			return ByteConversion.byteToLong(data, 4);
		}

		void set(int docId, long gid) {
			ByteConversion.intToByte(docId, data, 0);
			ByteConversion.longToByte(gid, data, 4);
			len = 12;
			pos = 0;
		}
	}
}
