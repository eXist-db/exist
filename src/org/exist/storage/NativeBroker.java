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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.filer.Paged;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.ArraySet;
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
import org.exist.security.MD5;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.store.BFile;
import org.exist.storage.store.CollectionStore;
import org.exist.storage.store.DOMFile;
import org.exist.storage.store.DOMFileIterator;
import org.exist.storage.store.DOMTransaction;
import org.exist.storage.store.NodeIterator;
import org.exist.storage.store.StorageAddress;
import org.exist.storage.sync.Sync;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *  Main class for the native XML storage backend.
 * 
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 *
 *@author     Wolfgang Meier
 */
public class NativeBroker extends DBBroker {
	
    private static final String TEMP_FRAGMENT_REMOVE_ERROR = "Could not remove temporary fragment";
	private static final String TEMP_STORE_ERROR = "An error occurred while storing temporary data: ";
	private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
	private static final String DATABASE_IS_READ_ONLY = "database is read-only";
	
	/**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(NativeBroker.class);

    private static final long TEMP_FRAGMENT_TIMEOUT = 300000;
    
	private static final String ROOT_COLLECTION = "/db";
	private static final String TEMP_COLLECTION ="/db/system/temp";
	
	/** default buffer size setting */
	protected final static int BUFFERS = 256;

	/** check available memory after storing MEM_LIMIT_CHECK nodes */
	protected final static int MEM_LIMIT_CHECK = 10000;

	// the database files
	protected CollectionStore collectionsDb;
	protected DOMFile domDb;
	protected BFile elementsDb;
	protected BFile valuesDb;
	
	protected NativeTextEngine textEngine;
	protected NativeElementIndex elementIndex;
	protected NativeValueIndex valueIndex;
	protected Serializer xmlSerializer;
	
	protected PatternCompiler compiler = new Perl5Compiler();
	protected PatternMatcher matcher = new Perl5Matcher();
	
	protected int defaultIndexDepth = 1;
	protected IndexSpec idxConf;
	
	protected boolean readOnly = false;
	
	protected int memMinFree;
	
	// used to count the nodes inserted after the last memory check
	protected int nodesCount = 0;

	protected int pageSize;
	
	private final Runtime run = Runtime.getRuntime();

	public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
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
					new BFile(new File(dataDir + pathSep + "elements.dbx"),
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

			if ((valuesDb = (BFile) config.getProperty("db-connection.values"))
			        == null) {
			    indexBuffers = buffers * 4;
			    dataBuffers = buffers * 10;
			    
			    LOG.debug(
			            "values index buffer size: " + indexBuffers + "; " + dataBuffers);
			    valuesDb =
			        new BFile(new File(dataDir + pathSep + "values.dbx"),
			                indexBuffers,
			                dataBuffers);
			    if (!valuesDb.exists()) {
			        LOG.info("creating values.dbx");
			        valuesDb.create();
			    } else
			        valuesDb.open();
			    
			    config.setProperty("db-connection.values", valuesDb);
			    readOnly = valuesDb.isReadOnly();
			}
			
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
					new DOMFile(new File(dataDir + pathSep + "dom.dbx"),
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
					new CollectionStore(new File(dataDir + pathSep + "collections.dbx"),
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

			if (readOnly)
				LOG.info("database runs in read-only mode");
			
			idxConf = (IndexSpec) config.getProperty("indexer.config");
			textEngine = new NativeTextEngine(this, config, buffers);
			valueIndex = new NativeValueIndex(this, valuesDb);
			xmlSerializer = new NativeSerializer(this, config);
			elementIndex = new NativeElementIndex(this, elementsDb);
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

	protected final static String normalizeCollectionName(String name) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < name.length(); i++)
			if (name.charAt(i) == '/'
				&& name.length() > i + 1
				&& name.charAt(i + 1) == '/')
				i++;
			else
				out.append(name.charAt(i));

		return out.toString();
	}

	public void addObserver(Observer o) {
		super.addObserver(o);
		textEngine.addObserver(o);
		elementIndex.addObserver(o);
	}

	private final boolean compare(Collator collator, String o1, String o2, int relation) {
		int cmp = Collations.compare(collator, o1, o2);
		switch (relation) {
			case Constants.LT :
				return (cmp < 0);
			case Constants.LTEQ :
				return (cmp <= 0);
			case Constants.GT :
				return (cmp > 0);
			case Constants.GTEQ :
				return (cmp >= 0);
			case Constants.EQ :
				return (cmp == 0);
			case Constants.NEQ :
				return (cmp != 0);
		}
		return false;
		// never reached
	}

	public ElementIndex getElementIndex() {
	    return elementIndex;
	}

	public IndexSpec getIndexConfiguration() {
	    return idxConf;
	}
	
	public void flush() {
		textEngine.flush();
		elementIndex.flush();
		valueIndex.flush();
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
		valueIndex.remove();
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

	public Collection getCollection(String name) {
		return openCollection(name, -1, Lock.NO_LOCK);
	}
	
	public Collection getCollection(String name, long addr) {
		return openCollection(name, addr, Lock.NO_LOCK);
	}
	
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
		//	final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith(ROOT_COLLECTION))
			name = ROOT_COLLECTION + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);

		CollectionCache collectionsCache = pool.getCollectionsCache();
		synchronized(collectionsCache) {
			Collection collection = collectionsCache.get(name);
			if(collection == null) {
//				LOG.debug("loading collection " + name);
				VariableByteInput is = null;
				Lock lock = collectionsDb.getLock();
				try {
					lock.acquire(Lock.READ_LOCK);
					
					collection = new Collection(collectionsDb, name);
					Value key = null;
					if (addr == -1) {
						try {
							key = new Value(name.getBytes("UTF-8"));
						} catch (UnsupportedEncodingException uee) {
							key = new Value(name.getBytes());
						}
					}
					try {
						if (addr < 0) {
							is = collectionsDb.getAsStream(key);
						} else {
							is = collectionsDb.getAsStream(addr);
						}
						if (is == null)
							return null;
						collection.read(this, is);
					} catch (IOException ioe) {
						LOG.warn(ioe.getMessage(), ioe);
					}
				} catch (LockException e) {
					LOG.warn("failed to acquire lock on collections.dbx");
					return null;
				} finally {
					lock.release();
				}
			}
			if(lockMode != Lock.NO_LOCK) {
				try {
//					LOG.debug("acquiring lock on " + collection.getName());
					collection.getLock().acquire(lockMode);
//					LOG.debug("lock acquired");
				} catch (LockException e1) {
					LOG.warn("Could not acquire lock on collection " + name);
				}
			}
			if(!pool.isInitializing())
				// don't cache the collection during initialization: SecurityManager is not yet online
				collectionsCache.add(collection);
			//			LOG.debug(
			//				"loading collection "
			//					+ name
			//					+ " took "
			//					+ (System.currentTimeMillis() - start)
			//					+ "ms.");
			return collection;
		}
	}

	public void reloadCollection(Collection collection) {
		Value key = null;
		if (collection.getAddress() == -1)
			try {
				key = new Value(collection.getName().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				key = new Value(collection.getName().getBytes());
			}
		VariableByteInput is = null;
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.READ_LOCK);
			try {
				if (collection.getAddress() == -1) {
					is = collectionsDb.getAsStream(key);
				} else {
					is = collectionsDb.getAsStream(collection.getAddress());
				}
			} catch (IOException ioe) {
				LOG.warn(ioe.getMessage(), ioe);
			}
			if (is == null) {
				LOG.warn("Collection data not found for collection " + collection.getName());
			    return;
			}
			
			try {
				collection.read(this, is);
			} catch (IOException ioe) {
				LOG.warn(ioe);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections.dbx");
		} finally {
			lock.release();
		}
	}
	
	public Iterator getDOMIterator(NodeProxy proxy) {
		try {
			return new DOMFileIterator(this, domDb, proxy);
		} catch (BTreeException e) {
			LOG.debug("failed to create DOM iterator", e);
		} catch (IOException e) {
			LOG.debug("failed to create DOM iterator", e);
		}
		return null;
	}

	public Iterator getNodeIterator(NodeProxy proxy) {
//		domDb.setOwnerObject(this);
		try {
			return new NodeIterator(this, domDb, proxy, false);
		} catch (BTreeException e) {
			LOG.debug("failed to create node iterator", e);
		} catch (IOException e) {
			LOG.debug("failed to create node iterator", e);
		}
		return null;
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
//				LOG.debug("document " + docPath + " not found!");
				return null;
			}
	//		if (!doc.getPermissions().validate(user, Permission.READ))
	//			throw new PermissionDeniedException("not allowed to read document");
			return doc;
		} catch (LockException e) {
			LOG.warn("Could not acquire lock on document " + docPath, e);
		} finally {
			if(collection != null)
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

	/**
	 * Release the collection id assigned to a collection so it can be
	 * reused later.
	 * 
	 * @param id
	 * @throws PermissionDeniedException
	 */
	protected void freeCollection(short id) throws PermissionDeniedException {
//		LOG.debug("freeing collection " + id);
		Value key = new Value("__free_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				byte[] ndata = new byte[data.length + 2];
				System.arraycopy(data, 0, ndata, 2, data.length);
				ByteConversion.shortToByte(id, ndata, 0);
				collectionsDb.put(key, ndata, true);
			} else {
				byte[] data = new byte[2];
				ByteConversion.shortToByte(id, data, 0);
				collectionsDb.put(key, data, true);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} catch (ReadOnlyException e) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        } finally {
			lock.release();
		}
	}
	
	/**
	 * Get the next free collection id. If a collection is removed, its collection id
	 * is released so it can be reused.
	 * 
	 * @return
	 * @throws ReadOnlyException
	 */
	protected short getFreeCollectionId() throws ReadOnlyException {
		short freeCollectionId = -1;
		Value key = new Value("__free_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				freeCollectionId = ByteConversion.byteToShort(data, data.length - 2);
//				LOG.debug("reusing collection id: " + freeCollectionId);
				if(data.length - 2 > 0) {
					byte[] ndata = new byte[data.length - 2];
					System.arraycopy(data, 0, ndata, 0, ndata.length);
					collectionsDb.put(key, ndata, true);
				} else
					collectionsDb.remove(key);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return freeCollectionId;
	}
	
	/**
	 * Get the next available unique collection id.
	 * 
	 * @return
	 * @throws ReadOnlyException
	 */
	protected short getNextCollectionId() throws ReadOnlyException {
		short nextCollectionId = getFreeCollectionId();
		if(nextCollectionId > -1)
			return nextCollectionId;
		Value key = new Value("__next_collection_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value data = collectionsDb.get(key);
			if (data != null) {
				nextCollectionId = ByteConversion.byteToShort(data.getData(), 0);
				++nextCollectionId;
			}
			byte[] d = new byte[2];
			ByteConversion.shortToByte(nextCollectionId, d, 0);
			collectionsDb.put(key, d, true);
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return nextCollectionId;
	}

	/**
	 * Release the document id reserved for a document so it
	 * can be reused.
	 * 
	 * @param id
	 * @throws PermissionDeniedException
	 */
	protected void freeDocument(int id) throws PermissionDeniedException {
//		LOG.debug("freeing document " + id);
		Value key = new Value("__free_doc_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				byte[] ndata = new byte[data.length + 4];
				System.arraycopy(data, 0, ndata, 4, data.length);
				ByteConversion.intToByte(id, ndata, 0);
				collectionsDb.put(key, ndata, true);
			} else {
				byte[] data = new byte[4];
				ByteConversion.intToByte(id, data, 0);
				collectionsDb.put(key, data, true);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} catch (ReadOnlyException e) {
		    throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        } finally {
			lock.release();
		}
	}
	
	/**
	 * Get the next unused document id. If a document is removed, its doc id is
	 * released, so it can be reused.
	 * 
	 * @return
	 * @throws ReadOnlyException
	 */
	protected int getFreeDocId() throws ReadOnlyException {
		int freeDocId = -1;
		Value key = new Value("__free_doc_id");
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			Value value = collectionsDb.get(key);
			if (value != null) {
				byte[] data = value.getData();
				freeDocId = ByteConversion.byteToInt(data, data.length - 4);
//				LOG.debug("reusing document id: " + freeDocId);
				if(data.length - 4 > 0) {
					byte[] ndata = new byte[data.length - 4];
					System.arraycopy(data, 0, ndata, 0, ndata.length);
					collectionsDb.put(key, ndata, true);
				} else
					collectionsDb.remove(key);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return freeDocId;
	}
	
	public int getNextDocId(Collection collection) {
		int nextDocId;
		try {
			nextDocId = getFreeDocId();
		} catch (ReadOnlyException e1) {
			return 1;
		}
		if(nextDocId > -1)
			return nextDocId;
		else
			nextDocId = 1;
		
		Value key = new Value("__next_doc_id");
		Value data;
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
			data = collectionsDb.get(key);
			if (data != null) {
				nextDocId = ByteConversion.byteToInt(data.getData(), 0);
				++nextDocId;
			}
			byte[] d = new byte[4];
			ByteConversion.intToByte(nextDocId, d, 0);
			try {
				collectionsDb.put(key, d, true);
			} catch (ReadOnlyException e) {
				LOG.debug("database read-only");
				return -1;
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
		} finally {
			lock.release();
		}
		return nextDocId;
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
		final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null; 
		if (address < 0)
			LOG.debug("node " + gid + ": internal address missing");
		final int depth = ftIdx == null ? defaultIndexDepth : ftIdx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		int indexType = ValueIndexSpec.NO_INDEX;
		NodeProxy tempProxy;
		QName qname;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				// skip
//				qname = node.getQName();
//				qname.setNameType(ElementValue.ELEMENT);
//				tempProxy = new NodeProxy(doc, gid, address);
//				if (idxSpec != null) {
//				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
//				    if(spec != null) {
//				        indexType = spec.getIndexType();
//				    }
//				}
//				if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
//				    indexType |= ValueIndexSpec.TEXT;
//				tempProxy.setIndexType(indexType);
//				elementIndex.setDocument(doc);
//				elementIndex.addRow(qname, tempProxy);
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
				currentPath.addComponent(new QName('@' + node.getLocalName(), node.getNamespaceURI()));
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null)
				        indexType = spec.getIndexType();
				}
				if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
				    indexType |= ValueIndexSpec.TEXT;
				tempProxy.setIndexType(indexType);
				
				elementIndex.addRow(qname, tempProxy);
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path)
				boolean indexAttribs = true;
				if(ftIdx != null) {
				    if(ftIdx.getIncludeAttributes()) {
				        if(currentPath != null) {
						    indexAttribs = ftIdx.match(currentPath);
				        }
				    } else
				        indexAttribs = false;
				}
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeAttribute(spec, (AttrImpl) node);
				    }
				}
				if(indexAttribs)
					textEngine.storeAttribute(ftIdx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
			    currentPath.removeLastComponent();
				break;
			case Node.TEXT_NODE :
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeText(spec, (TextImpl) node);
				    }
				}
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
			    boolean indexText = true;
				if(ftIdx != null && currentPath != null)
				    indexText = ftIdx.match(currentPath);	                	                
                boolean valore = (ftIdx == null || currentPath == null ? false : ftIdx.preserveContent(currentPath));
                if(indexText)
                    textEngine.storeText(ftIdx, (TextImpl) node, valore);
				break;
		}
		if (nodeType == Node.ELEMENT_NODE && level <= depth) {
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
					try {
						domDb.addValue(
							new NodeRef(doc.getDocId(), gid),
							node.getInternalAddress());
					} catch (BTreeException e) {
						LOG.warn(EXCEPTION_DURING_REINDEX, e);
					} catch (IOException e) {
						LOG.warn(EXCEPTION_DURING_REINDEX, e);
					}
					return null;
				}
			}
			.run();
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
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				try {
					Value ref = new NodeRef(doc.getDocId());
					IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
					final ArrayList nodes = domDb.findKeys(query);
					long gid;
					for (Iterator i = nodes.iterator(); i.hasNext();) {
						ref = (Value) i.next();
						gid = ByteConversion.byteToLong(ref.data(), ref.start() + 4);
						if (oldDoc.getTreeLevel(gid) >= doc.reindexRequired()) {
							if (node != null) {
								if (XMLUtil.isDescendant(oldDoc, node.getGID(), gid)) {
									domDb.removeValue(ref);
								}
							} else
								domDb.removeValue(ref);
						}
					}
				} catch (BTreeException e) {
					LOG.debug("Exception while reindexing document: " + e.getMessage(), e);
				} catch (IOException e) {
					LOG.debug("Exception while reindexing document: " + e.getMessage(), e);
				}
				return null;
			}
		}.run();
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
		valueIndex.reindex(oldDoc, node);
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
		final short nodeType = node.getNodeType();
		final long gid = node.getGID();
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
		final int depth = ftIdx == null ? defaultIndexDepth : ftIdx.getIndexDepth();
		final int level = doc.getTreeLevel(gid);
		if (level >= doc.reindexRequired()) {
			NodeIndexListener listener = doc.getIndexListener();
			// jmv if ((listener = doc.getIndexListener()) != null)
			if(listener != null)
				listener.nodeChanged(node);
			if (nodeType == Node.ELEMENT_NODE && level <= depth) {
				new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
					public Object start() throws ReadOnlyException {
						try {
							domDb.addValue(
								new NodeRef(doc.getDocId(), gid),
								node.getInternalAddress());
						} catch (BTreeException e) {
							LOG.warn(EXCEPTION_DURING_REINDEX, e);
						} catch (IOException e) {
							LOG.warn(EXCEPTION_DURING_REINDEX, e);
						}
						return null;
					}
				}
				.run();
			}
			NodeProxy tempProxy =
				new NodeProxy(doc, gid, node.getInternalAddress());
			int indexType = ValueIndexSpec.NO_INDEX;
			QName qname;
			switch (nodeType) {
				case Node.ELEMENT_NODE :
					// skip
//					qname = node.getQName();
//					qname.setNameType(ElementValue.ELEMENT);
//					if (idxSpec != null) {
//					    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
//					    if(spec != null)
//					        indexType = spec.getIndexType();
//					}
//					if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
//					    indexType |= ValueIndexSpec.TEXT;
//					tempProxy.setIndexType(indexType);
//
//					elementIndex.setDocument(doc);
//					elementIndex.addRow(qname, tempProxy);
					break;
				case Node.ATTRIBUTE_NODE :
				    currentPath.addComponent(new QName('@' + node.getLocalName(), node.getNamespaceURI()));
				    if (idxSpec != null) {
					    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
					    if(spec != null)
					        indexType = spec.getIndexType();
					}
					if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
					    indexType |= ValueIndexSpec.TEXT;
					tempProxy.setIndexType(indexType);
					elementIndex.setDocument(doc);
					qname =
						new QName(
							node.getLocalName(),
							node.getNamespaceURI(),
							node.getPrefix());
					qname.setNameType(ElementValue.ATTRIBUTE);
					elementIndex.addRow(qname, tempProxy);
					if (idxSpec != null) {
					    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
					    if(spec != null) {
					        valueIndex.setDocument(doc);
					        valueIndex.storeAttribute(spec, (AttrImpl) node);
					    }
					}
					// check if attribute value should be fulltext-indexed
					// by calling IndexPaths.match(path) 
					boolean indexAttribs = true;
					if(ftIdx != null) {
					    if(ftIdx.getIncludeAttributes()) {
						    indexAttribs = ftIdx.match(currentPath);
					    } else
					        indexAttribs = false;
					}
					if (indexAttribs)
						textEngine.storeAttribute(ftIdx, (AttrImpl) node);
					// if the attribute has type ID, store the ID-value
					// to the element index as well
					if (((AttrImpl) node).getType() == AttrImpl.ID) {
						qname = new QName("&" + ((AttrImpl) node).getValue(), "", null);
						qname.setNameType(ElementValue.ATTRIBUTE_ID);
						elementIndex.addRow(qname, tempProxy);
					}
					currentPath.removeLastComponent();
					break;
				case Node.TEXT_NODE :
					if (idxSpec != null) {
					    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
					    if(spec != null) {
					        valueIndex.setDocument(doc);
					        valueIndex.storeText(spec, (TextImpl) node);
					    }
					}
					// check if this textual content should be fulltext-indexed
					// by calling IndexPaths.match(path)
					if (ftIdx == null || ftIdx.match(currentPath)) {     
		                boolean valore = (ftIdx == null ? false : ftIdx.preserveContent(currentPath));
						textEngine.storeText(ftIdx, (TextImpl) node, valore);
					}
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
		    currentPath.addComponent(node.getQName());
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
		if(node.getNodeType() == Node.ELEMENT_NODE) {
		    endElement(node, currentPath);
		    currentPath.removeLastComponent();
		}
	}

	/**
	 * Reindex the nodes in the document. This method will either reindex all
	 * descendant nodes of the passed node, or all nodes below some level of
	 * the document if node is null.
	 */
	private void reindex(DocumentImpl doc) {
		LOG.debug("Reindexing document " + doc.getFileName());
		if(doc.getFileName().equals(CollectionConfiguration.COLLECTION_CONFIG_FILE))
		    doc.getCollection().setConfigEnabled(false);
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
		if(doc.getFileName().equals(CollectionConfiguration.COLLECTION_CONFIG_FILE))
		    doc.getCollection().setConfigEnabled(true);
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
	        lock = collectionsDb.getLock();
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
			valueIndex.dropIndex(doc);
			
			// dropping dom index
			NodeRef ref = new NodeRef(doc.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.remove(idx, null);
						domDb.flush();
					} catch (BTreeException e) {
			            LOG.warn("start() - " + "error while removing doc", e);
					} catch (IOException e) {
			            LOG.warn("start() - " + "error while removing doc", e);
					} catch (TerminatedException e) {
			            LOG.warn("method terminated", e);
			        } catch (DBException e) {
			        	LOG.warn("start() - " + "error while removing doc", e);
					}
					return null;
				}
			}
			.run();
			
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
			new DOMTransaction(this, domDb) {
				public Object start() {
					domDb.removeAll(firstChild);
					try {
						domDb.flush();
					} catch (DBException e) {
						LOG.warn("start() - " + "error while removing doc", e);
					}
					return null;
				}
			}
			.run();
			
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
		    currentPath.addComponent(node.getQName());
		final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
		node.setOwnerDocument(newDoc);
		node.setInternalAddress(-1);
		
		store(node, currentPath, index);
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    endElement(node, currentPath);
		
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
		if(node.getNodeType() == Node.ELEMENT_NODE) {
		    currentPath.removeLastComponent();
		}
	}
	
	public void consistencyCheck(DocumentImpl doc) throws EXistException {
		if(xupdateConsistencyChecks) {
			LOG.debug("Checking document " + doc.getFileName());
			checkTree(doc);
//			elementIndex.consistencyCheck(doc);
		}
	}
	
	public void checkTree(final DocumentImpl doc) {
		LOG.debug("Checking DOM tree for document " + doc.getFileName());
		if(xupdateConsistencyChecks) {
			new DOMTransaction(this, domDb, Lock.READ_LOCK) {
				public Object start() throws ReadOnlyException {
					LOG.debug("Pages used: " + domDb.debugPages(doc));
					return null;
				}
			}.run();
			
			NodeList nodes = doc.getChildNodes();
			NodeImpl n;
			for (int i = 0; i < nodes.getLength(); i++) {
			    n = (NodeImpl) nodes.item(i);
			    Iterator iterator =
			        getNodeIterator(
			                new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
			    iterator.next();
			    checkTree(iterator, n);
			}
			NodeRef ref = new NodeRef(doc.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.findKeys(idx);
					} catch (BTreeException e) {
			            LOG.warn("start() - " + "error while removing doc", e);
					} catch (IOException e) {
			            LOG.warn("start() - " + "error while removing doc", e);
					}
					return null;
				}
			}
			.run();
		}
	}
	
	private void checkTree(Iterator iterator, NodeImpl node) {
		if (node.hasChildNodes()) {
			final long firstChildId = XMLUtil.getFirstChildId((DocumentImpl)node.getOwnerDocument(), 
					node.getGID());
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
				checkTree(iterator, child);
			}
		}
	}
	
	public String getNodeValue(final NodeProxy proxy) {
		return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
			public Object start() {
				return domDb.getNodeValue(proxy);
			}
		}
		.run();
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
		NodeSet result = scanSequential(context, docs, relation, truncation, expr, collator);
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
		//		final long start = System.currentTimeMillis();
		name = normalizeCollectionName(name);
		if (name.length() > 0 && name.charAt(0) != '/')
			name = "/" + name;

		if (!name.startsWith(ROOT_COLLECTION))
			name = ROOT_COLLECTION + name;

		if (name.endsWith("/") && name.length() > 1)
			name = name.substring(0, name.length() - 1);
		final CollectionCache collectionsCache = pool.getCollectionsCache();
		synchronized(collectionsCache) {
			try {
				StringTokenizer tok = new StringTokenizer(name, "/");
				String temp = tok.nextToken();
				String path = ROOT_COLLECTION;
				Collection sub;
				Collection current = getCollection(ROOT_COLLECTION);
				if (current == null) {
					LOG.debug("creating root collection /db");
					current = new Collection(collectionsDb, ROOT_COLLECTION);
					current.getPermissions().setPermissions(0777);
					current.getPermissions().setOwner(user);
					current.getPermissions().setGroup(user.getPrimaryGroup());
					current.setId(getNextCollectionId());
					current.setCreationTime(System.currentTimeMillis());
					saveCollection(current);
				}
				while (tok.hasMoreTokens()) {
					temp = tok.nextToken();
					path = path + "/" + temp;
					if (current.hasSubcollection(temp)) {
						current = getCollection(path);
					} else {
						if (!current.getPermissions().validate(user, Permission.WRITE)) {
							LOG.debug("permission denied to create collection " + path);
							throw new PermissionDeniedException("not allowed to write to collection");
						}
						LOG.debug("creating collection " + path);
						sub = new Collection(collectionsDb, path);
						sub.getPermissions().setOwner(user);
						sub.getPermissions().setGroup(user.getPrimaryGroup());
						sub.setId(getNextCollectionId());
						sub.setCreationTime(System.currentTimeMillis());
						current.addCollection(sub);
						saveCollection(current);
						current = sub;
					}
				}
				//			LOG.debug("getOrCreateCollection took " + 
				//				(System.currentTimeMillis() - start) + "ms.");
				return current;
			} catch(ReadOnlyException e) {
				throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
			}
		}
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

	public NativeValueIndex getValueIndex() {
	    return valueIndex;
	}
	
	public Serializer newSerializer() {
		return new NativeSerializer(this, getConfiguration());
	}

	public Node objectWith(final Document doc, final long gid) {
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(new NodeProxy((DocumentImpl) doc, gid));
				if (val == null) {
//				    if(LOG.isDebugEnabled()) {
//				        LOG.debug("node " + gid + " not found in document " + ((DocumentImpl)doc).getDocId());
//				        Thread.dumpStack();
//				    }
					return null;
				}
				NodeImpl node =
					NodeImpl.deserialize(
						val.getData(),
						0,
						val.getLength(),
						(DocumentImpl) doc);
				node.setGID(gid);
				node.setOwnerDocument(doc);
				node.setInternalAddress(val.getAddress());
				return node;
			}
		}
		.run();
	}

	public Node objectWith(final NodeProxy p) {
		if (p.getInternalAddress() < 0)
			return objectWith(p.getDocument(), p.gid);
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(p.getInternalAddress());
				if (val == null) {
					LOG.debug("Node " + p.gid + " not found in document " + p.getDocument().getName() +
							"; docId = " + p.getDocument().getDocId());
//					LOG.debug(domDb.debugPages(p.doc));
					Thread.dumpStack();
//					return null;
					return objectWith(p.getDocument(), p.gid); // retry?
				}
				NodeImpl node =
					NodeImpl.deserialize(
						val.getData(),
						0,
						val.getLength(),
						(DocumentImpl) p.getDocument());
				node.setGID(p.gid);
				node.setOwnerDocument(p.getDocument());
				node.setInternalAddress(p.getInternalAddress());
				return node;
			}
		}
		.run();
	}

	public void dropIndex(Collection collection) throws PermissionDeniedException {
	    if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    if (!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("insufficient privileges on collection " + 
	                collection.getName());
	    
	    textEngine.dropIndex(collection);
	    elementIndex.dropIndex(collection);
	    valueIndex.dropIndex(collection);
	    
	    for (Iterator i = collection.iterator(this); i.hasNext();) {
	        final DocumentImpl doc = (DocumentImpl) i.next();
	        LOG.debug("Dropping index for document " + doc.getFileName());
	        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
	            public Object start() {
	                try {
	                    Value ref = new NodeRef(doc.getDocId());
	                    IndexQuery query =
	                        new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
	                    domDb.remove(query, null);
	                    domDb.flush();
	                } catch (BTreeException e) {
	                    LOG.warn("btree error while removing document", e);
	                } catch (DBException e) {
	                    LOG.warn("db error while removing document", e);
	                } catch (IOException e) {
	                    LOG.warn("io error while removing document", e);
	                } catch (TerminatedException e) {
	                    LOG.warn("method terminated", e);
	                }
	                return null;
	            }
	        }
	        .run();
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
		    
		    Lock lock = collectionsDb.getLock();
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
		    		collectionsDb.remove(key);
		    		collectionsCache.remove(collection);
				   freeCollection(collection.getId());
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
		    valueIndex.dropIndex(collection);
		    
		    LOG.debug("removing resources ...");
		    for (Iterator i = collection.iterator(this); i.hasNext();) {
		    	final DocumentImpl doc = (DocumentImpl) i.next();
		    	LOG.debug("removing document " + doc.getFileName());
		    	new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
		    		public Object start() {
		    			if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
		    				domDb.remove(doc.getAddress());
		    				domDb.removeOverflowValue(((BinaryDocument)doc).getPage());
		    			} else {
		    				NodeImpl node = (NodeImpl)doc.getFirstChild();
		    				domDb.removeAll(node.getInternalAddress());
		    			}
		    			return null;
		    		}
		    	}
		    	.run();
		    	new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
		    		public Object start() {
		    			try {
		    				Value ref = new NodeRef(doc.getDocId());
		    				IndexQuery query =
		    					new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
		    				domDb.remove(query, null);
		    				domDb.flush();
		    			} catch (BTreeException e) {
		    				LOG.warn("btree error while removing document", e);
		    			} catch (DBException e) {
		    				LOG.warn("db error while removing document", e);
		    			} catch (IOException e) {
		    				LOG.warn("io error while removing document", e);
		    			} catch (TerminatedException e) {
		    				LOG.warn("method terminated", e);
		    			}
		    			return null;
		    		}
		    	}
		    	.run();
		    	freeDocument(doc.getDocId());
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
			valueIndex.dropIndex(document);
			textEngine.dropIndex(document);
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
			new DOMTransaction(this, domDb) {
				public Object start() {
				    NodeImpl node = (NodeImpl)document.getFirstChild();
					domDb.removeAll(node.getInternalAddress());
					return null;
				}
			}
			.run();
			
			NodeRef ref = new NodeRef(document.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.remove(idx, null);
						domDb.flush();
					} catch (BTreeException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
					} catch (DBException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
					} catch (IOException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
					} catch (TerminatedException e) {
                        LOG.warn("method terminated", e);
                    }
					return null;
				}
			}
			.run();
			if(freeDocId)
			    freeDocument(document.getDocId());
		} catch (ReadOnlyException e) {
            LOG.warn("removeDocument(String) - " + DATABASE_IS_READ_ONLY);
		}
	}

	public void removeNode(final NodeImpl node, NodePath currentPath) {
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
			public Object start() {
				final long address = node.getInternalAddress();
				if (address > -1)
					domDb.remove(new NodeRef(doc.getDocId(), node.getGID()), address);
				else
					domDb.remove(new NodeRef(doc.getDocId(), node.getGID()));
				return null;
			}
		}
		.run();
		NodeProxy tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
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
			    currentPath.addComponent(new QName('@' + node.getLocalName(), node.getNamespaceURI()));
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
				if(ftIdx != null) {
				    if(ftIdx.getIncludeAttributes()) {
					    indexAttribs = ftIdx.match(currentPath);
				    } else
				        indexAttribs = false;
				}
				if (indexAttribs)
					textEngine.storeAttribute(ftIdx, (AttrImpl) node);
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeAttribute(spec, (AttrImpl) node);
				    }
				}
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				currentPath.removeLastComponent();
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				if (ftIdx == null || ftIdx.match(currentPath)){
					boolean valore = (ftIdx == null ? false : ftIdx.preserveContent(currentPath));
					textEngine.storeText(ftIdx, (TextImpl) node, valore);
				}
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeText(spec, (TextImpl) node);
				    }
				}
				break;
		}
	}

	public void addDocument(Collection collection, DocumentImpl doc)
		throws PermissionDeniedException {
		Lock lock = collectionsDb.getLock();
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
			long address = collectionsDb.append(name, ostream.data());
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
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		if (!pool.isInitializing())
			// don't cache the collection during initialization: SecurityManager is not yet online
			pool.getCollectionsCache().add(collection);
		Lock lock = null;
		try {
			lock = collectionsDb.getLock();
			lock.acquire(Lock.WRITE_LOCK);

			if (collection.getId() < 0)
				collection.setId(getNextCollectionId());

			Value name;
			try {
				name = new Value(collection.getName().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException uee) {
				LOG.debug(uee);
				name = new Value(collection.getName().getBytes());
			}
			try {
				final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
				collection.write(this, ostream);
				final long addr = collectionsDb.put(name, ostream.data());
				if (addr < 0) {
					LOG.debug(
						"could not store collection data for " + collection.getName());
					return;
				}
				collection.setAddress(addr);
				ostream.close();
			} catch (IOException ioe) {
				LOG.debug(ioe);
			}
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY);
		} catch (LockException e) {
			LOG.warn("could not acquire lock for collections store", e);
		} finally {
			lock.release();
		}
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
	        lock = collectionsDb.getLock();
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
				valueIndex.dropIndex(doc);
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
	    Collection old = openCollection(destination.getName() + '/' + newName, Lock.WRITE_LOCK);
	    if(old != null) {
	    	LOG.debug("removing old collection: " + newName);
	    	try {
	    		removeCollection(old);
	    	} finally {
	    		old.release();
	    	}
	    }
	    Collection destCollection = null;
	    Lock lock = null;
	    try {
	        lock = collectionsDb.getLock();
	        lock.acquire(Lock.WRITE_LOCK);
	        
	        newName = destination.getName() + '/' + newName;
	        LOG.debug("Copying collection to " + newName);
		    destCollection = getOrCreateCollection(newName);
		    for(Iterator i = collection.iterator(this); i.hasNext(); ) {
		    	DocumentImpl child = (DocumentImpl) i.next();
		    	LOG.debug("Copying resource: " + child.getName());
		    	DocumentImpl newDoc = new DocumentImpl(this, child.getFileName(), destCollection);
		        newDoc.copyOf(child);
		        copyResource(child, newDoc);
		        flush();
		        destCollection.addDocument(this, newDoc);
		    }
		    saveCollection(destCollection);
	    } finally {
	        lock.release();
	    }
	    String name = collection.getName();
	    for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
	    	String childName = (String)i.next();
	        Collection child = openCollection(name + '/' + childName, Lock.WRITE_LOCK);
	        if(child == null)
	            LOG.warn("Child collection " + childName + " not found");
	        else {
	            try {
	            	copyCollection(child, destCollection, childName);
	            } finally {
	            	child.release();
	            }
	        }
	    }
	    saveCollection(destCollection);
	    saveCollection(destination);
	}
	
	public void moveCollection(Collection collection, Collection destination, String newName) 
	throws PermissionDeniedException, LockException {
	    if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	    if(collection.getId() == destination.getId())
	    	throw new PermissionDeniedException("Cannot move collection to itself");
	    if(collection.getName().equals(ROOT_COLLECTION))
	        throw new PermissionDeniedException("Cannot move the db root collection");
	    if(!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("Insufficient privileges to move collection " +
	                collection.getName());
	    if(!destination.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	                destination.getName());
	    if(newName == null) {
            int p = collection.getName().lastIndexOf('/');
            newName = collection.getName().substring(p + 1);
        }
	    if(newName.indexOf('/') > -1)
	        throw new PermissionDeniedException("New collection name is illegal (may not contain a '/')");
	        // check if another collection with the same name exists at the destination
	    Collection old = openCollection(destination.getName() + '/' + newName, Lock.WRITE_LOCK);
	    if(old != null) {
	    	try {
	    		removeCollection(old);
	    	} finally {
	    		old.release();
	    	}
	    }
	    String name = collection.getName();
	    final CollectionCache collectionsCache = pool.getCollectionsCache();
	    synchronized(collectionsCache) {
		    Collection parent = openCollection(collection.getParentPath(), Lock.WRITE_LOCK);
	        if(parent != null) {
	        	try {
	        		parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
	        	} finally {
	        		parent.release();
	        	}
	        }
		    Lock lock = null;
		    try {
		    	lock = collectionsDb.getLock();
		    	lock.acquire(Lock.WRITE_LOCK);
			    
		        collectionsCache.remove(collection);
			    Value key;
				try {
					key = new Value(name.getBytes("UTF-8"));
				} catch (UnsupportedEncodingException uee) {
					key = new Value(name.getBytes());
				}	
				collectionsDb.remove(key);
				
			    collection.setName(destination.getName() + '/' + newName);
			    collection.setCreationTime(System.currentTimeMillis());
			    
			    destination.addCollection(collection);
			    if(parent != null)
			        saveCollection(parent);
			    if(parent != destination)
			        saveCollection(destination);
			    saveCollection(collection);
		    } catch (ReadOnlyException e) {
	            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
	        } finally {
		        lock.release();
		    }
	        String childName;
		    Collection child;
		    for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
		        childName = (String)i.next();
		        child = openCollection(name + '/' + childName, Lock.WRITE_LOCK);
		        if(child == null)
		            LOG.warn("Child collection " + childName + " not found");
		        else {
		            try {
		            	moveCollection(child, collection, childName);
		            } finally {
		            	child.release();
		            }
		        }
		    }
	    }
	}
	
	/**
	 *  Do a sequential search through the DOM-file.
	 *
	 *@param  context     Description of the Parameter
	 *@param  doc         Description of the Parameter
	 *@param  relation    Description of the Parameter
	 *@param  truncation  Description of the Parameter
	 *@param  expr        Description of the Parameter
	 *@return             Description of the Return Value
	 */
	protected NodeSet scanSequential(
		NodeSet context,
		DocumentSet doc,
		int relation,
		int truncation,
		String expr,
		Collator collator) {
		ArraySet resultNodeSet = new ArraySet(context.getLength());
		NodeProxy p;
		String content;
		String cmp;
		Pattern regexp = null;
		if (relation == Constants.REGEXP)
			try {
				regexp =
					compiler.compile(
						expr.toLowerCase(),
						Perl5Compiler.CASE_INSENSITIVE_MASK);
				truncation = Constants.REGEXP;
			} catch (MalformedPatternException e) {
				LOG.debug(e);
			}
		for (Iterator i = context.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			try {
				domDb.getLock().acquire(Lock.READ_LOCK);
				domDb.setOwnerObject(this);
				content = domDb.getNodeValue(p);
			} catch (LockException e) {
				LOG.warn("failed to acquire read lock on dom.dbx");
				continue;
			} finally {
				domDb.getLock().release();
			}
			if (isCaseSensitive())
				cmp = StringValue.collapseWhitespace(content);
			else {
				cmp = StringValue.collapseWhitespace(content.toLowerCase());
			}
			switch (truncation) {
				case Constants.TRUNC_LEFT :
					if (Collations.endsWith(collator, cmp, expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_RIGHT :
					if (Collations.startsWith(collator, cmp, expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_BOTH :
					if (-1 < Collations.indexOf(collator, cmp, expr))
						resultNodeSet.add(p);
					break;
				case Constants.TRUNC_NONE :
					if (compare(collator, cmp, expr, relation))
						resultNodeSet.add(p);
					break;
				case Constants.REGEXP :
					if (regexp != null && matcher.contains(cmp, regexp)) {
						resultNodeSet.add(p);
					}
					break;
			}
		}
		return resultNodeSet;
	}

	public void shutdown() {
		super.shutdown();
		try {
			flush();
			sync(Sync.MAJOR_SYNC);
			textEngine.close();
			domDb.close();
			elementsDb.close();
			valuesDb.close();
			collectionsDb.close();
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
		final boolean isTemp = TEMP_COLLECTION.equals(doc.getCollection().getName());
		final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
		final long gid = node.getGID();
		if (gid < 0) {
			LOG.debug("illegal node: " + gid + "; " + node.getNodeName());
			Thread.dumpStack();
			return;
		}
		final short nodeType = node.getNodeType();
		final String nodeName = node.getNodeName();
		final int depth = ftIdx == null ? defaultIndexDepth : ftIdx.getIndexDepth();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
			public Object start() throws ReadOnlyException {
				long address = -1;
				final byte data[] = node.serialize();
				if (nodeType == Node.TEXT_NODE
					|| nodeType == Node.ATTRIBUTE_NODE
					|| doc.getTreeLevel(gid) > depth)
					address = domDb.add(data);
				else {
					address = domDb.put(new NodeRef(doc.getDocId(), gid), data);
				}
				if (address < 0)
					LOG.warn("address is missing");
				node.setInternalAddress(address);
				ByteArrayPool.releaseByteArray(data);
				return null;
			}
		}
		.run();
		++nodesCount;
		NodeProxy tempProxy = null;
		QName qname;
		int indexType = ValueIndexSpec.NO_INDEX;
		switch (nodeType) {
			case Node.ELEMENT_NODE :
			    // skip
//				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
//				if (idxSpec != null) {
//				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
//				    if(spec != null)
//				        indexType = spec.getIndexType();
//				}
//				if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
//				    indexType |= ValueIndexSpec.TEXT;
//				tempProxy.setIndexType(indexType);
//				
//				// save element by calling ElementIndex
//				elementIndex.setDocument(doc);
//				elementIndex.addRow(node.getQName(), tempProxy);
				break;
			case Node.ATTRIBUTE_NODE :
				tempProxy = new NodeProxy(doc, gid, node.getInternalAddress());
				currentPath.addComponent(new QName('@' + node.getLocalName(), node.getNamespaceURI()));
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null)
				        indexType = spec.getIndexType();
				}
				if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
				    indexType |= ValueIndexSpec.TEXT;
				tempProxy.setIndexType(indexType);
				
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
				if(index && ftIdx != null) {
				    if(ftIdx.getIncludeAttributes()) {
				        indexAttribs = ftIdx.match(currentPath);
				    } else
				        indexAttribs = false;
				}
				if (idxSpec != null) {
				    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeAttribute(spec, (AttrImpl) node);
				    }
				}
				if(indexAttribs && !isTemp)
					textEngine.storeAttribute(ftIdx, (AttrImpl) node);
				// if the attribute has type ID, store the ID-value
				// to the element index as well
				if (((AttrImpl) node).getType() == AttrImpl.ID) {
					qname = new QName(((AttrImpl) node).getValue(), "", null);
					//LOG.debug("found ID: " + qname.getLocalName());
					qname.setNameType(ElementValue.ATTRIBUTE_ID);
					elementIndex.addRow(qname, tempProxy);
				}
				currentPath.removeLastComponent();
				break;
			case Node.TEXT_NODE :
				// check if this textual content should be fulltext-indexed
				// by calling IndexPaths.match(path)
				if (!isTemp && index) {
					if (idxSpec != null) {
					    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
					    if(spec != null) {
					        valueIndex.setDocument(doc);
					        valueIndex.storeText(spec, (TextImpl) node);
					    }
					}
					if (ftIdx == null || ftIdx.match(currentPath)) {     
		                boolean valore = (ftIdx == null ? false : ftIdx.preserveContent(currentPath));
						textEngine.storeText(ftIdx, (TextImpl) node, valore);
					}
				}
				break;
		}
	}

	public void endElement(final NodeImpl node, NodePath currentPath) {
	    final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
	    final NodeProxy tempProxy = new NodeProxy(doc, node.getGID(), node.getInternalAddress());
	    final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
		int indexType = ValueIndexSpec.NO_INDEX;
		if (idxSpec != null) {
		    ValueIndexSpec spec = idxSpec.getIndexByPath(currentPath);
		    if(spec != null)
		        indexType = spec.getIndexType();
		}
		if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
		    indexType |= ValueIndexSpec.TEXT;
		if(node.getChildCount() - node.getAttributesCount() > 1) {
		    indexType |= ValueIndexSpec.MIXED_CONTENT;
		}
		tempProxy.setIndexType(indexType);
		
		node.getQName().setNameType(ElementValue.ELEMENT);
		
		// save element by calling ElementIndex
		elementIndex.setDocument(doc);
		elementIndex.addRow(node.getQName(), tempProxy);
	}
	
	public void storeDocument(final DocumentImpl doc) {
		final byte data[] = doc.serialize();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				if (doc.getAddress() > -1) {
					domDb.remove(doc.getAddress());
				}
				doc.setAddress(domDb.add(data));
//				LOG.debug("Document metadata stored to " + StorageAddress.toString(doc.getAddress()));
				return null;
			}
		}
		.run();
	}

	public void updateDocument(DocumentImpl doc) throws LockException, PermissionDeniedException {
		storeDocument(doc);
		saveCollection(doc.getCollection());
	}
	
	public void storeBinaryResource(final BinaryDocument blob, final byte[] data) {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
//				if (blob.getPage() > -1) {
//					domDb.remove(blob.getPage());
//				}
				LOG.debug("Storing binary resource " + blob.getFileName());
				blob.setPage(domDb.addBinary(data));
				return null;
			}
		}
		.run();
	}
	
	public byte[] getBinaryResourceData(final BinaryDocument blob) {
		byte[] data = (byte[]) new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				return domDb.getBinary(blob.getPage());
			}
		}
		.run();
		return data;
	}

	public void removeBinaryResource(final BinaryDocument blob)
		throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		LOG.info("removing binary resource " + blob.getDocId() + "...");
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				domDb.remove(blob.getAddress());
				domDb.removeOverflowValue(blob.getPage());
				return null;
			}
		}
		.run();
	}

	public void readDocumentMetadata(final DocumentImpl doc) {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				final Value val = domDb.get(doc.getAddress());
				doc.deserialize(val.getData());
				return null;
			}
		}
		.run();

	}

	public void sync(int syncEvent) {
		try {
			Lock lock = collectionsDb.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				collectionsDb.flush();
			} catch (LockException e) {
				LOG.warn("failed to acquire lock on collections store", e);
			} finally {
				lock.release();
			}
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() {
					try {
						domDb.flush();
					} catch (DBException e) {
						LOG.warn("error while flushing dom.dbx", e);
					}
					return null;
				}
			}
			.run();
			if(syncEvent == Sync.MAJOR_SYNC) {
				elementIndex.sync();
				textEngine.sync();
				valueIndex.sync();
				System.gc();
				Runtime runtime = Runtime.getRuntime();
				LOG.info("Memory: " + (runtime.totalMemory() / 1024) + "K total; " +
						(runtime.maxMemory() / 1024) + "K max; " +
						(runtime.freeMemory() / 1024) + "K free");
				
				// uncomment this to get statistics on page buffer usage
				elementsDb.printStatistics();
				collectionsDb.printStatistics();
				valuesDb.printStatistics();
				domDb.printStatistics();
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
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() {
				domDb.closeDocument();
				return null;
			}
		}
		.run();
	}

	public void update(final NodeImpl node) {
		try {
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			final long internalAddress = node.getInternalAddress();
			final byte[] data = node.serialize();
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
					if (-1 < internalAddress)
						domDb.update(internalAddress, data);
					else {
						domDb.update(new NodeRef(doc.getDocId(), node.getGID()), data);
					}
					return null;
				}
			}
			.run();
			ByteArrayPool.releaseByteArray(data);
		} catch (Exception e) {
		    Value oldVal = domDb.get(node.getInternalAddress());
		    NodeImpl old = 
		        NodeImpl.deserialize(oldVal.data(), oldVal.start(), oldVal.getLength(), 
		                (DocumentImpl)node.getOwnerDocument(), false);
			LOG.debug(
				"Exception while storing "
					+ node.getNodeName()
					+ "; gid = "
					+ node.getGID()
					+ "; old = " + old.getNodeName(),
				e);
		}
	}

	/**
	 * Physically insert a node into the DOM storage.
	 */
	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		final byte data[] = node.serialize();
		final DocumentImpl doc = (DocumentImpl) previous.getOwnerDocument();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
			public Object start() {
				long address = previous.getInternalAddress();
				if (address > -1) {
					address = domDb.insertAfter(doc, address, data);
				} else {
					NodeRef ref = new NodeRef(doc.getDocId(), previous.getGID());
					address = domDb.insertAfter(doc, ref, data);
				}
				node.setInternalAddress(address);
				return null;
			}
		}
		.run();
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public DocumentImpl storeTemporaryDoc(String data) throws EXistException, PermissionDeniedException, LockException {
		String docName = MD5.md(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis())) +
			".xml";
		Collection temp = openCollection(TEMP_COLLECTION, Lock.WRITE_LOCK);
		if(temp == null)
			temp = createTempCollection();
		IndexInfo info;
		try {
			info = temp.validate(this, docName, data);
		} catch (TriggerException e) {
			throw new EXistException(TEMP_STORE_ERROR + e.getMessage());
		} catch (SAXException e) {
			throw new EXistException(TEMP_STORE_ERROR + e.getMessage());
		} finally {
			temp.release();
		}
		try {
			temp.store(this, info, data, false);
		} catch (TriggerException e) {
			throw new EXistException(TEMP_STORE_ERROR + e.getMessage());
		} catch (SAXException e) {
			throw new EXistException(TEMP_STORE_ERROR + e.getMessage());
		}
		return info.getDocument();
	}
	
	public void removeTempDocs(List docs) {
		Collection temp = openCollection(TEMP_COLLECTION, Lock.WRITE_LOCK);
		if(temp == null)
			return;
		try {
			for(Iterator i = docs.iterator(); i.hasNext(); )
				temp.removeDocument(this, (String) i.next());
		} catch (PermissionDeniedException e) {
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} catch (TriggerException e) {
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} catch (LockException e) {
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} finally {
			temp.release();
		}
	}
	
	public void cleanUpAll() {
		Collection temp = getCollection(TEMP_COLLECTION);
		if(temp == null)
			return;
		try {
			removeCollection(temp);
		} catch (PermissionDeniedException e) {
			LOG.warn("Failed to remove temporary collection: " + e.getMessage(), e);
		}
	}
	
	public void cleanUp() {
		Collection temp = getCollection(TEMP_COLLECTION);
		if(temp == null)
			return;
		long now = System.currentTimeMillis();
		for(Iterator i = temp.iterator(this); i.hasNext(); ) {
			DocumentImpl next = (DocumentImpl) i.next();
			long modified = next.getLastModified();
			if(now - modified > TEMP_FRAGMENT_TIMEOUT)
				try {
					temp.removeDocument(this, next.getFileName());
				} catch (PermissionDeniedException e) {
					LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
				} catch (TriggerException e) {
					LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
				} catch (LockException e) {
					LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
				}
		}
	}
	
	private Collection createTempCollection() throws LockException, PermissionDeniedException {
		User u = user;
		Lock lock = null;
		try {
			lock = collectionsDb.getLock();
			lock.acquire(Lock.WRITE_LOCK);
			user = pool.getSecurityManager().getUser(SecurityManager.DBA_USER);
			Collection temp = getOrCreateCollection(TEMP_COLLECTION);
			temp.setPermissions(0777);
			saveCollection(temp);
			temp.getLock().acquire(Lock.WRITE_LOCK);
			return temp;
		} finally {
			lock.release();
			user = u;
		}
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
