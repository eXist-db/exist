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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.ArraySet;
import org.exist.dom.AttrImpl;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.memtree.DOMIndexer;
import org.exist.security.MD5;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Paged;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.DOMFileIterator;
import org.exist.storage.dom.DOMTransaction;
import org.exist.storage.dom.NodeIterator;
import org.exist.storage.index.BFile;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Main class for the native XML storage backend.
 *  By "native" it is meant file-based, embedded backend.
 * 
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 * 
 * Observer Design Pattern: role : this class is the subject (alias observable)
 * for various classes that generate indices for the database content :
 * @link org.exist.storage.NativeElementIndex,
 * @link org.exist.storage.NativeTextEngine,
 * @link org.exist.storage.NativeValueIndex, 
 * @link org.exist.storage.NativeValueIndexByQName
 * 
 * This class dispatches the various events (defined by the methods 
 * of @link org.exist.storage.ContentLoadingObserver) to indexing classes.
 * 
 *@author     Wolfgang Meier
 */
public class NativeBroker extends DBBroker {
	
    public static final byte COLLECTIONS_DBX_ID = 0;
    public static final byte ELEMENTS_DBX_ID = 1;
    public static final byte VALUES_DBX_ID = 2;
    public static final byte WORDS_DBX_ID = 3;
    public static final byte DOM_DBX_ID = 4;
    public static final byte VALUES_QNAME_DBX_ID = 5;
    
    private static final String ELEMENTS_DBX = "elements.dbx";
    private static final String VALUES_DBX = "values.dbx";
    private static final String VALUES_QNAME_DBX = "values-by-qname.dbx";
    private static final String DOM_DBX = "dom.dbx";
    private static final String COLLECTIONS_DBX = "collections.dbx";
    private static final String WORDS_DBX = "words.dbx";
    
    private static final byte[] ALL_STORAGE_FILES = {
    	COLLECTIONS_DBX_ID, ELEMENTS_DBX_ID, VALUES_DBX_ID,
    	VALUES_QNAME_DBX_ID, WORDS_DBX_ID, DOM_DBX_ID
    };
    
    private static final String TEMP_FRAGMENT_REMOVE_ERROR = "Could not remove temporary fragment";

	// private static final String TEMP_STORE_ERROR = "An error occurred while storing temporary data: ";
	private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
	private static final String DATABASE_IS_READ_ONLY = "database is read-only";
	
	/**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(NativeBroker.class);

    private static final long TEMP_FRAGMENT_TIMEOUT = 300000;	
	
	/** default buffer size setting */
	protected final static int BUFFERS = 256;

	/** check available memory after storing MEM_LIMIT_CHECK nodes */
	protected final static int MEM_LIMIT_CHECK = 10000;

	/** the database files */
	protected CollectionStore collectionsDb;
	protected DOMFile domDb;
	protected BFile elementsDb;
	protected BFile valuesDb;
    protected BFile dbWords;
	protected BFile valuesDbQname;
    
	/** the index processors */
	protected NativeTextEngine textEngine;
	protected NativeElementIndex elementIndex;
	protected NativeValueIndex valueIndex;
	protected NativeValueIndexByQName qnameValueIndex;
    
	/** switch to activate/deactivate the feature "new index by QName" */
	private boolean qnameValueIndexation = true; // false;
	
	protected Serializer xmlSerializer;
	
	protected int defaultIndexDepth = 1;
	protected IndexSpec idxConf;
	
	protected boolean readOnly = false;
	
	protected int memMinFree;
	
	/** used to count the nodes inserted after the last memory check */
	protected int nodesCount = 0;

    protected String dataDir;
	protected int pageSize;
	
	private final Runtime run = Runtime.getRuntime();

    private NodeProcessor nodeProcessor = new NodeProcessor();
    
	/** initialize database; read configuration, etc. */
	public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
		super(pool, config);
		LOG.debug("Initializing broker " + hashCode());
		if ((dataDir = (String) config.getProperty("db-connection.data-dir")) == null)
			dataDir = "data";

		if ((pageSize = config.getInteger("db-connection.page-size")) < 0)
			pageSize = 4096;

		if ((defaultIndexDepth = config.getInteger("indexer.index-depth")) < 0)
			defaultIndexDepth = 1;
		if ((memMinFree = config.getInteger("db-connection.min_free_memory")) < 0)
			memMinFree = 5000000;
		
		Paged.setPageSize(pageSize);

		try {

			if ((domDb = (DOMFile) config.getProperty("db-connection.dom")) == null) {
				domDb =
					new DOMFile(pool, new File(dataDir + File.separatorChar + DOM_DBX),
						pool.getCacheManager());

				config.setProperty("db-connection.dom", domDb);
				if (!readOnly)
					readOnly = domDb.isReadOnly();
			}

			// Initialize collections storage
            
			if ((collectionsDb =
				(CollectionStore) config.getProperty("db-connection.collections"))
				== null) {
				collectionsDb =
					new CollectionStore(pool, new File(dataDir + File.separatorChar + COLLECTIONS_DBX),
                            pool.getCacheManager());

				config.setProperty("db-connection.collections", collectionsDb);
				if (!readOnly)
					readOnly = collectionsDb.isReadOnly();
            }
            
			createIndexFiles();
			
			if (readOnly)
				LOG.info("database runs in read-only mode");
			
			idxConf = (IndexSpec) config.getProperty("indexer.config");

			
			xmlSerializer = new NativeSerializer(this, config);
			user = SecurityManager.SYSTEM_USER;
		} catch (DBException e) {
			LOG.debug("failed to initialize database: " + e.getMessage(), e);
			throw new EXistException(e);
		}
	}

    /**
     * @param pool
     * @param config
     * @param dataDir
     * @param buffers
     * @throws DBException
     */
    private void createIndexFiles() throws DBException {
        elementsDb = createValueIndexFile(ELEMENTS_DBX_ID, false, config, dataDir, ELEMENTS_DBX, "db-connection.elements", 0.05 );
        valuesDb = createValueIndexFile(VALUES_DBX_ID, false, config, dataDir, VALUES_DBX, "db-connection.values", 0.05 );
        if ( qnameValueIndexation ) {
                valuesDbQname = createValueIndexFile(VALUES_QNAME_DBX_ID, false, config, dataDir, VALUES_QNAME_DBX,
                        "db-connection2.values", 0.4 );
        }
        
        if ((dbWords = (BFile) config.getProperty("db-connection.words")) == null) {
        	LOG.debug("Creating words.dbx ....\n\n");
        	dbWords = new BFile(pool, NativeBroker.WORDS_DBX_ID, false, 
                    new File(dataDir + File.separatorChar + WORDS_DBX), pool.getCacheManager(), 1.4, 0.01, 0.015);
            config.setProperty("db-connection.words", dbWords);
        }
        textEngine = new NativeTextEngine(this, config, dbWords);
        valueIndex = new NativeValueIndex(this, valuesDb);
        if ( qnameValueIndexation ) {
            qnameValueIndex = new NativeValueIndexByQName(this, valuesDbQname);
            addContentLoadingObserver(qnameValueIndex);
        }
        elementIndex = new NativeElementIndex(this, elementsDb);
        
        addContentLoadingObserver(textEngine);
        addContentLoadingObserver(valueIndex);
        addContentLoadingObserver(elementIndex);
    }

    /**
     * @param config
     * @param dataDir
     * @param buffers
     * @param pathSep
     * @throws DBException
     */
    private BFile createValueIndexFile(byte id, boolean transactional, Configuration config, String dataDir, 
            String dataFile, String propertyName, double thresholdData ) throws DBException {
        BFile db;
        
        if ((db = (BFile) config.getProperty(propertyName))
                == null) {
            db =
                new BFile(pool, id, transactional, new File(dataDir + File.separatorChar + dataFile ), pool.getCacheManager(), 1.25, 0.01, thresholdData);
            
            config.setProperty(propertyName, db);
            readOnly = db.isReadOnly();
        }
        return db;
    }

    /** Observer Design Pattern: List of ContentLoadingObserver objects */
    private List contentLoadingObservers = new ArrayList();
    
    /** Remove all observers */
    public void clearContentLoadingObservers() {
    	contentLoadingObservers.clear();
    }
    
    /** Observer Design Pattern: add an observer. */
    public void addContentLoadingObserver(ContentLoadingObserver observer) {
        contentLoadingObservers.add(observer);
    }
    /** Observer Design Pattern: remove an observer. */
    public void removeContentLoadingObserver(ContentLoadingObserver observer) {
        contentLoadingObservers.remove(observer);
    }
    
    // ============ dispatch the various events to indexing classes ==========

    private void notifyStartElement(ElementImpl elem, NodePath currentPath, boolean index) {
        // WM: don't use an iterator here. The method may be called a few million times for a single document. 
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.startElement(elem, currentPath, index);
        }
	}
  
	private void notifyStoreAttribute(AttrImpl attr, NodePath currentPath, boolean index) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.storeAttribute( attr, currentPath, index );
        }	
	}
	

	private void notifyStoreText(TextImpl text, NodePath currentPath, boolean index ) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.storeText(text, currentPath, index);
        }
	}
	

	private void notifyRemoveElement(ElementImpl elem, NodePath currentPath, String content) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.removeElement(elem, currentPath, content);
        }
	}
	
    private void notifyDropIndex(DocumentImpl doc) throws ReadOnlyException {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.dropIndex(doc);
        }
    }
    private void notifyDropIndex(Collection collection) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.dropIndex(collection);
        }
    }
    private void notifyFlush() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.flush();
        }
    }
    private void notifyRemove() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.remove();
        }
    }
    private void notifyReindex(final DocumentImpl oldDoc, final NodeImpl node) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.reindex(oldDoc, node);
        }
    }
    private void notifySync() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.sync();
        }
    }
    // etc ... TODO for all methods of ContentLoadingObserver
    
	/** changes // into /  */
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

    public void deleteObservers() {
        super.deleteObservers();
        textEngine.deleteObservers();
        elementIndex.deleteObservers();
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
		notifyFlush();
		if (symbols != null && symbols.hasChanged())
			try {
				saveSymbols();
			} catch (EXistException e) {
				LOG.warn(e.getMessage(), e);
			}
		nodesCount = 0;
	}

	/** Takes care of actually remove entries from the indices;
	 * must be called after one or more call to {@link #removeNode()}. */
	public void endRemove() {
		notifyRemove();
	}

	/**
	 *  Adds all the documents in the database to the specified DocumentSet.
	 *
	 * @param docs a (possibly empty) document set to which the found
     *  documents are added.
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
	 *@param  name  collection name
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

		Collection collection = null;
		CollectionCache collectionsCache = pool.getCollectionsCache();
		synchronized(collectionsCache) {
			collection = collectionsCache.get(name);
			if(collection == null) {
				VariableByteInput is = null;
				Lock lock = collectionsDb.getLock();
				try {
					lock.acquire(Lock.READ_LOCK);
					
					collection = new Collection(name);
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
						if (is == null) {
				                        return null;
                        			}
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
			if(!pool.isInitializing())
				// don't cache the collection during initialization: SecurityManager is not yet online
				collectionsCache.add(collection);
			//			LOG.debug(
			//				"loading collection "
			//					+ name
			//					+ " took "
			//					+ (System.currentTimeMillis() - start)
			//					+ "ms.");
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
		return collection;
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
	 *  get a document by its file name. The document's file name is used to
	 *  identify a document.
	 *
	 *@param  fileName                       absolute file name in the database; name can be given with or without the leading path /db/shakespeare.
	 *@return                                The document value
	 *@exception  PermissionDeniedException  
	 */
	public Document getDocument(String fileName) throws PermissionDeniedException {
		fileName = XmldbURI.checkPath2(fileName, ROOT_COLLECTION);

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
		docPath = XmldbURI.checkPath2(docPath, ROOT_COLLECTION);

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
	protected void freeCollection(Txn transaction, short id) throws PermissionDeniedException {
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
				collectionsDb.put(transaction, key, ndata, true);
			} else {
				byte[] data = new byte[2];
				ByteConversion.shortToByte(id, data, 0);
				collectionsDb.put(transaction, key, data, true);
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
	protected short getFreeCollectionId(Txn transaction) throws ReadOnlyException {
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
					collectionsDb.put(transaction, key, ndata, true);
				} else
					collectionsDb.remove(transaction, key);
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
	protected short getNextCollectionId(Txn transaction) throws ReadOnlyException {
		short nextCollectionId = getFreeCollectionId(transaction);
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
			collectionsDb.put(transaction, key, d, true);
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
	protected void freeDocument(Txn transaction, int id) throws PermissionDeniedException {
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
				collectionsDb.put(transaction, key, ndata, true);
			} else {
				byte[] data = new byte[4];
				ByteConversion.intToByte(id, data, 0);
				collectionsDb.put(transaction, key, data, true);
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
	protected int getFreeDocId(Txn transaction) throws ReadOnlyException {
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
					collectionsDb.put(transaction, key, ndata, true);
				} else
					collectionsDb.remove(transaction, key);
			}
		} catch (LockException e) {
			LOG.warn("failed to acquire lock on collections store", e);
			return -1;
		} finally {
			lock.release();
		}
		return freeDocId;
	}
	
    /** get next Free Doc Id */
	public int getNextDocId(Txn transaction, Collection collection) {
		int nextDocId;
		try {
			nextDocId = getFreeDocId(transaction);
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
				collectionsDb.put(transaction, key, d, true);
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
	
    /** Delegate for Node Processings : indexing */
    private class NodeProcessor {
        
        private Txn transaction;
        private NodeImpl node;
        private NodePath currentPath;
        /** work variables */
        private short nodeType;
        private long gid;
        private DocumentImpl doc;
        private long address;
        private IndexSpec idxSpec;
        private FulltextIndexSpec ftIdx;
        private int depth;
        private int level;
        
        /** overall switch to activate fulltext indexation */
        private boolean index = true;
        private boolean repairMode = false;
        
        NodeProcessor() {
        }

        public void reset(Txn transaction, NodeImpl node, NodePath currentPath) {
            if (node.getGID() < 0)
                LOG.debug("illegal node: " + node.getGID() + "; " + node.getNodeName());
            this.transaction = transaction;
            this.node = node;
            this.currentPath = currentPath;
            nodeType = node.getNodeType();
            gid = node.getGID();
            doc = (DocumentImpl) node.getOwnerDocument();
            address = node.getInternalAddress();
            idxSpec = doc.getCollection().getIdxConf(NativeBroker.this);
            ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
            depth = idxSpec == null ? defaultIndexDepth : idxSpec.getIndexDepth();
            level = doc.getTreeLevel(gid);
        }
        
        public void reset(Txn transaction, NodeImpl node, NodePath currentPath, boolean index) {
            reset(transaction, node, currentPath);
            this.index = index;
        }

        public void setRepairMode(boolean repair) {
            this.repairMode = repair;
        }
        
        /** Updates the various indices */
        public void doIndex() {
            int indexType = RangeIndexSpec.NO_INDEX;
            final boolean isTemp = TEMP_COLLECTION.equals(doc.getCollection().getName());
            switch (nodeType) {
                case Node.ELEMENT_NODE :
                    if (idxSpec != null) {
                        
                        // --move to-- NativeValueIndex
                        RangeIndexSpec spec = idxSpec.getIndexByPath(currentPath);
                        if(spec != null) {
                            indexType = spec.getIndexType();
                        }
                        
//                        // --move to-- NativeValueIndexByQName
//                        RangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
//                        if (qnIdx != null && qnameValueIndexation) {
//                            indexType |= RangeIndexSpec.QNAME_INDEX;
//                        }
                    }
                    
                    // --move to-- NativeTextEngine
                    if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
                        indexType |= RangeIndexSpec.TEXT;
                    if(node.getChildCount() - node.getAttributesCount() > 1) {
                        indexType |= RangeIndexSpec.MIXED_CONTENT;
                    }
                    
                    // --move to-- NativeValueIndex NativeTextEngine
                    ((ElementImpl) node).setIndexType(indexType);
                    
                    // qnameValueIndex.startElement((ElementImpl)node, currentPath, index);
                    notifyStartElement((ElementImpl)node, currentPath, index);
                    break;
                    
                case Node.ATTRIBUTE_NODE :
                    boolean indexAttribs = false;
                    
                    QName qname = node.getQName();
    				if (currentPath != null)
    					currentPath.addComponent(qname);
    				
                    // --move to-- NativeElementIndex NativeValueIndex NativeTextEngine
                    if(index && (ftIdx == null || currentPath == null || ftIdx.matchAttribute(currentPath))) {
                        indexType |= RangeIndexSpec.TEXT;
                        indexAttribs = true;
                    }
                    
                    // --move to-- NativeValueIndex
                    // TODO : valueIndex.storeAttribute( (AttrImpl)node, currentPath, index);
                    GeneralRangeIndexSpec valSpec = null;
                    if (idxSpec != null) {
                        valSpec = idxSpec.getIndexByPath(currentPath);
                        if(valSpec != null) {
                            indexType |= valSpec.getIndexType();
                        }
                    }
                    if (valSpec != null) {
                        valueIndex.setDocument(doc);
                        valueIndex.storeAttribute(valSpec, (AttrImpl) node);
                    }
                    
                    // qnameValueIndex.storeAttribute( (AttrImpl)node, currentPath, index);
                    notifyStoreAttribute( (AttrImpl)node, currentPath, index);
                    
                    // --move to-- NativeTextEngine
                    // TODO : textEngine.storeAttribute( (AttrImpl)node, currentPath, index);
                    if (indexAttribs && !isTemp )
                        textEngine.storeAttribute(ftIdx, (AttrImpl) node);
                    
//                  --move to-- NativeElementIndex
                    // TODO : elementIndex.storeAttribute(node, currentPath, index);
                    elementIndex.setDocument(doc);
                    final NodeProxy tempProxy = new NodeProxy(doc, gid, address);
                    tempProxy.setIndexType(indexType);
                    qname.setNameType(ElementValue.ATTRIBUTE);
                    elementIndex.addRow(qname, tempProxy);
                    
                    // --move to-- NativeElementIndex
                    // TODO : elementIndex.storeAttribute(node, currentPath, index);
                    // if the attribute has type ID, store the ID-value
                    // to the element index as well
                    if (((AttrImpl) node).getType() == AttrImpl.ID) {
                        qname = new QName(((AttrImpl) node).getValue(), "", null);
                        //LOG.debug("found ID: " + qname.getLocalName());
                        qname.setNameType(ElementValue.ATTRIBUTE_ID);
                        elementIndex.addRow(qname, tempProxy);
                    }
                    
//                    // --move to-- ???
                    if (currentPath != null)
                        currentPath.removeLastComponent();
                    break;
                    
                case Node.TEXT_NODE:
                    // --move to-- NativeTextEngine
                    // TODO textEngine.storeText( (TextImpl) node, currentPath, index);
                    // check if this textual content should be fulltext-indexed
                    // by calling IndexPaths.match(path)
                    boolean indexText = true;
                    if (ftIdx != null && currentPath != null)
                        indexText = ftIdx.match(currentPath);
                    if (indexText && !isTemp && index ) {
                        boolean valore = (ftIdx == null || currentPath == null ? false
                                : ftIdx.preserveContent(currentPath));
                        textEngine.storeText(ftIdx, (TextImpl) node, valore);
                    }
                    
                    notifyStoreText( (TextImpl)node, currentPath, index );
                    // storeText( TextImpl node, NodePath currentPath, boolean fullTextIndexSwitch );
                    break;
            }
        }

		/** Stores this node into the database, if it's an element */
        public void store() {
            if (!repairMode && nodeType == Node.ELEMENT_NODE && level <= depth) {
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() throws ReadOnlyException {
                        try {
                            domDb.addValue(transaction, new NodeRef(doc.getDocId(), gid), address);
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
        
        /** check available memory */
        private void checkAvailableMemory() {
            if (nodesCount > MEM_LIMIT_CHECK) {
                final int percent = (int) (run.freeMemory() /
                        (run.totalMemory() / 100));
                if (percent < memMinFree) {
                    //LOG.info(
                    //  "total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                    flush();
                    System.gc();
                    LOG.info(
                        "total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                }
            }
        }
        
        /** Updates the various indices and stores this node into the database */
        public void index() {
            ++nodesCount;
            checkAvailableMemory();
            doIndex();
            store();
        }
        
        /** Updates the various indices and stores this node into the database
         * if necessary */
        public void reindex() {
            if (level >= doc.reindexRequired()) {
                NodeIndexListener listener = doc.getIndexListener();
                if(listener != null)
                    listener.nodeChanged(node);
                store();
                doIndex();
            }
        }
    }
    
	/**
	 * Index a single node, which has been added through an XUpdate
	 * operation. This method is only called if inserting the node is possible
	 * without changing the node identifiers of sibling or parent nodes. In other 
	 * cases, reindex will be called.
	 */
    public void index(Txn transaction, NodeImpl node, NodePath currentPath) {
        index(transaction, node, currentPath, false);
    }
    
	public void index(final Txn transaction, final NodeImpl node, NodePath currentPath, boolean repairMode) {
        nodeProcessor.reset(transaction, node, currentPath);
        nodeProcessor.index();
	}

	/**
	 * Reindex the nodes in the document. This method will either reindex all
	 * descendant nodes of the passed node, or all nodes below some level of
	 * the document if node is null.
	 */
	public void reindex(final Txn transaction, final DocumentImpl oldDoc, final DocumentImpl doc, 
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
									domDb.removeValue(transaction, ref);
								}
							} else
								domDb.removeValue(transaction, ref);
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
					scanNodes(transaction, iterator, n, new NodePath(), false, false);
				}
			} else {
				iterator =
					getNodeIterator(
						new NodeProxy(doc, node.getGID(), node.getInternalAddress()));
				iterator.next();
				scanNodes(transaction, iterator, node, node.getPath(), false, false);
			}
		} catch(Exception e) {
			LOG.error("Error occured while reindexing document: " + e.getMessage(), e);
		}
        notifyReindex(oldDoc, node);
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
	private void reindex(final Txn transaction, final NodeImpl node, NodePath currentPath) {
        nodeProcessor.reset(transaction, node, currentPath);
        nodeProcessor.reindex();
	}

	/**
	 * Called by reindex to walk through all nodes in the tree and reindex them
	 * if necessary.
	 * 
	 * @param iterator
	 * @param node
	 * @param currentPath
	 */
	private void scanNodes(Txn transaction, Iterator iterator, NodeImpl node, NodePath currentPath,
	        boolean fullReindex, boolean repairMode) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.addComponent(node.getQName());
		if(fullReindex)
		    index(transaction, node, currentPath, repairMode);
		else
		    reindex(transaction, node, currentPath);
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
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
				scanNodes(transaction, iterator, child, currentPath, fullReindex, repairMode);
			}
		}
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			if((fullReindex || doc.getTreeLevel(node.getGID()) >= doc.reindexRequired())) {
			    endElement(node, currentPath, null);
			}
			currentPath.removeLastComponent();
		}
	}

    public void removeAll(Txn transaction, NodeImpl node, NodePath currentPath) {
        Iterator iterator = 
            getNodeIterator(new NodeProxy((DocumentImpl)node.getOwnerDocument(), node.getGID(), node.getInternalAddress()));
        iterator.next();
        Stack stack = new Stack();
        collectForRemove(stack, iterator, node, currentPath);
        RemovedNode next;
        while (!stack.isEmpty()) {
            next = (RemovedNode) stack.pop();
            removeNode(transaction, next.node, next.path, next.content);
        }
    }
    
    private void collectForRemove(Stack stack, Iterator iterator, NodeImpl node, NodePath currentPath) {
        RemovedNode removed;
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
                String content = null;
                IndexSpec idxSpec = 
                    doc.getCollection().getIdxConf(this);
                if (idxSpec != null) {
                    GeneralRangeIndexSpec spec = idxSpec.getIndexByPath(currentPath);
                    RangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                    if (spec != null || qnIdx != null) {
                        NodeProxy p = new NodeProxy(doc, node.getGID(), node.getInternalAddress());
                        content = getNodeValue(p, false);
                    }
                }
                removed = new RemovedNode(node, new NodePath(currentPath), content);
                stack.push(removed);

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
                        if (child.getNodeType() == Node.ELEMENT_NODE)
                            currentPath.addComponent(((ElementImpl) child).getQName());
                        collectForRemove(stack, iterator, child, currentPath);
                        if (child.getNodeType() == Node.ELEMENT_NODE)
                            currentPath.removeLastComponent();
                    }
                }
                break;
            default :
                removed = new RemovedNode(node, new NodePath(currentPath), null);
                stack.push(removed);
                break;
        }
    }
    
    private final static class RemovedNode {
        NodeImpl node;
        String content;
        NodePath path;
        
        RemovedNode(NodeImpl node, NodePath path, String content) {
            this.node = node;
            this.path = path;
            this.content = content;
        }
    }
    
	/**
	 * Reindex the nodes in the document. This method will either reindex all
	 * descendant nodes of the passed node, or all nodes below some level of
	 * the document if node is null.
	 */
	private void reindex(Txn transaction, DocumentImpl doc, boolean repairMode) {
//		LOG.debug("Reindexing document " + doc.getFileName());
		if(doc.getFileName().endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX))
		    doc.getCollection().setConfigEnabled(false);
//		final long start = System.currentTimeMillis();
		Iterator iterator;
		NodeList nodes = doc.getChildNodes();
		NodeImpl n;
		for (int i = 0; i < nodes.getLength(); i++) {
		    n = (NodeImpl) nodes.item(i);
		    iterator =
		        getNodeIterator(
		                new NodeProxy(doc, n.getGID(), n.getInternalAddress()));
		    iterator.next();
		    scanNodes(transaction, iterator, n, new NodePath(), true, repairMode);
		}
		flush();
		if(doc.getFileName().endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX))
		    doc.getCollection().setConfigEnabled(true);
//		LOG.debug("reindex took " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	public void copyResource(Txn transaction, DocumentImpl doc, Collection destination, String newName) 
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
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
                    destination.removeBinaryResource(transaction, this, oldDoc);
                else
                    destination.removeDocument(transaction, this, oldDoc.getFileName());
	        } else {
	        	if(!destination.getPermissions().validate(user, Permission.WRITE))
	    	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	    	                destination.getName());
	        }
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE)  {
                byte[] data = getBinaryResourceData((BinaryDocument) doc); 
                destination.addBinaryResource(transaction, this, newName, data, doc.getMimeType());
            } else {
                DocumentImpl newDoc = new DocumentImpl(this, newName, destination);
                newDoc.copyOf(doc);
                newDoc.setDocId(getNextDocId(transaction, destination));
                copyResource(transaction, doc, newDoc);
                destination.addDocument(transaction, this, newDoc);
                storeDocument(transaction, newDoc);
            }
//	        saveCollection(destination);
        } catch (EXistException e) {
            LOG.warn("An error occurred while copying resource", e);
		} catch (TriggerException e) {
			throw new PermissionDeniedException(e.getMessage());
		} finally {
	    	lock.release();
	    }
	}
	
	private void copyResource(Txn transaction, DocumentImpl oldDoc, DocumentImpl newDoc) {
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
		    copyNodes(transaction, iterator, n, new NodePath(), newDoc, true);
		}
		flush();
		closeDocument();
		LOG.debug("Copy took " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	public void defrag(final Txn transaction, final DocumentImpl doc) {
		LOG.debug("============> Defragmenting document " + 
		        doc.getCollection().getName() + '/' + doc.getFileName());
//        Writer writer = new StringWriter();
//        try {
//            domDb.dump(writer);
//        } catch (BTreeException e1) {
//            //  Auto-generated catch block
//            e1.printStackTrace();
//        } catch (IOException e1) {
//            //  Auto-generated catch block
//            e1.printStackTrace();
//        }
//        System.out.println(writer.toString());
        
		final long start = System.currentTimeMillis();
		try {
//			checkTree(doc);
//            try {
//                domDb.printFreeSpaceList();
//            } catch (IOException e1) {
//                // Auto-generated catch block
//                e1.printStackTrace();
//            }
			// remember this for later remove
		    final long firstChild = doc.getFirstChildAddress();
		    	
			// dropping old structure index
			elementIndex.dropIndex(doc);
			valueIndex.dropIndex(doc);
			if (qnameValueIndex != null)
				qnameValueIndex.dropIndex(doc);
			
			// dropping dom index
			NodeRef ref = new NodeRef(doc.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.remove(transaction, idx, null);
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
			    copyNodes(transaction, iterator, n, new NodePath(), tempDoc, false);
			}
			flush();
			
//			checkTree(tempDoc);
			// remove the old nodes
			new DOMTransaction(this, domDb) {
				public Object start() {
					domDb.removeAll(transaction, firstChild);
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
			
			storeDocument(transaction, doc);
			LOG.debug("new doc address = " + StorageAddress.toString(doc.getAddress()));
			closeDocument();
//			new DOMTransaction(this, domDb, Lock.READ_LOCK) {
//				public Object start() throws ReadOnlyException {
//					LOG.debug("Pages used: " + domDb.debugPages(doc));
//					return null;
//				}
//			}.run();
			storeDocument(transaction, doc);
//            checkTree(doc);
			LOG.debug("Defragmentation took " + (System.currentTimeMillis() - start) + "ms.");
		} catch (ReadOnlyException e) {
			LOG.warn(DATABASE_IS_READ_ONLY, e);
		}
	}
	
	private void copyNodes(Txn transaction, Iterator iterator, NodeImpl node, NodePath currentPath, 
	        DocumentImpl newDoc, boolean index) {
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    currentPath.addComponent(node.getQName());
		final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
		node.setOwnerDocument(newDoc);
		node.setInternalAddress(-1);
		store(transaction, node, currentPath, index);
		if (node.getNodeType() == Node.ELEMENT_NODE)
		    endElement(node, currentPath, null);
		
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
				copyNodes(transaction, iterator, child, currentPath, newDoc, index);
			}
		}
		if(node.getNodeType() == Node.ELEMENT_NODE) {
		    currentPath.removeLastComponent();
		}
	}

	/** consistency Check of the database; useful after XUpdates;
	 * called if xupdate.consistency-checks is true in configuration */	
	public void consistencyCheck(DocumentImpl doc) throws EXistException {
		if(xupdateConsistencyChecks) {
			LOG.debug("Checking document " + doc.getFileName());
			checkTree(doc);
//			elementIndex.consistencyCheck(doc);
		}
	}
	
	/** consistency Check of the database; useful after XUpdates;
	 * called by {@link #consistencyCheck()} */
	public void checkTree(final DocumentImpl doc) {
		LOG.debug("Checking DOM tree for document " + doc.getFileName());
		if(xupdateConsistencyChecks) {
			new DOMTransaction(this, domDb, Lock.READ_LOCK) {
				public Object start() throws ReadOnlyException {
					LOG.debug("Pages used: " + domDb.debugPages(doc, false));
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
	
	public String getNodeValue(final NodeProxy proxy, final boolean addWhitespace) {
		return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
			public Object start() {
				return domDb.getNodeValue(proxy, addWhitespace);
			}
		}
		.run();
	}

	public NodeSet getNodesEqualTo(
		NodeSet context,
		DocumentSet docs,
		int relation,
        int truncation,
		String expr,
		Collator collator) {
		//		long start = System.currentTimeMillis();
		// NodeSet temp;
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
	
	public Collection getOrCreateCollection(Txn transaction, String name)
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
					LOG.debug("creating root collection " + ROOT_COLLECTION);
					current = new Collection(ROOT_COLLECTION);
					current.getPermissions().setPermissions(0777);
					current.getPermissions().setOwner(user);
					current.getPermissions().setGroup(user.getPrimaryGroup());
					current.setId(getNextCollectionId(transaction));
					current.setCreationTime(System.currentTimeMillis());
                    if (transaction != null)
                        transaction.acquireLock(current.getLock(), Lock.WRITE_LOCK);
					saveCollection(transaction, current);
				}
				while (tok.hasMoreTokens()) {
					temp = tok.nextToken();
					path = path + "/" + temp;
					if (current.hasSubcollection(temp)) {
						current = getCollection(path);
                        if (current == null)
                            LOG.debug("Collection " + path + " not found!");
					} else {
						if (readOnly)
							throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
						if (!current.getPermissions().validate(user, Permission.WRITE)) {
							LOG.debug("permission denied to create collection " + path);
							throw new PermissionDeniedException("User '"+ user.getName() + "' not allowed to write to collection '" + current.getName() + "'");
						}
						LOG.debug("creating collection " + path);
						sub = new Collection(path);
						sub.getPermissions().setOwner(user);
						sub.getPermissions().setGroup(user.getPrimaryGroup());
						sub.setId(getNextCollectionId(transaction));
						sub.setCreationTime(System.currentTimeMillis());
                        if (transaction != null)
                            transaction.acquireLock(sub.getLock(), Lock.WRITE_LOCK);
                        current.addCollection(sub);
                        saveCollection(transaction, current);
						current = sub;
					}
				}
				//			LOG.debug("getOrCreateCollection took " + 
				//				(System.currentTimeMillis() - start) + "ms.");
				return current;
			} catch(ReadOnlyException e) {
				throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
			} catch (LockException e) {
                LOG.warn("Failed to acquire lock on collection", e);
            }
            return null;
		}
	}

    public NodeList getRange(final Document doc, final long first, final long last) {
        return getRange(doc, first, last, -1);
    }
    
	/** @return all nodes whose global unique id's are in given interval. */	
	public NodeList getRange(final Document doc, final long first, final long last, final long parentPointer) {
		NodeListImpl result = new NodeListImpl((int) (last - first + 1));
		for (long gid = first; gid <= last; gid++) {
			result.add(objectWith(doc, gid, parentPointer));
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
        return objectWith(doc, gid, -1);
    }
    
	/** @return node with given global unique id. */	
	public Node objectWith(final Document doc, final long gid, final long parentPointer) {
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
			return objectWith(p.getDocument(), p.getGID());
		return (Node) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(p.getInternalAddress());
				if (val == null) {
					LOG.debug("Node " + p.getGID() + " not found in document " + p.getDocument().getName() +
							"; docId = " + p.getDocument().getDocId());
//					LOG.debug(domDb.debugPages(p.doc, true));
//					return null;
					return objectWith(p.getDocument(), p.getGID()); // retry?
				}
				NodeImpl node =
					NodeImpl.deserialize(
						val.getData(),
						0,
						val.getLength(),
						(DocumentImpl) p.getDocument());
				node.setGID(p.getGID());
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
	    
        notifyDropIndex(collection);
	    
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
	
    public void reindex(Collection collection, boolean repairMode) throws PermissionDeniedException {
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            reindex(transaction, collection, repairMode);
            transact.commit(transaction);
        } catch (TransactionException e) {
            transact.abort(transaction);
            LOG.warn("An error occurred during reindex: " + e.getMessage(), e);
        }
    }
    
	public void reindex(Txn transaction, Collection collection, boolean repairMode) throws PermissionDeniedException {
	    if (!collection.getPermissions().validate(user, Permission.WRITE))
	        throw new PermissionDeniedException("insufficient privileges on collection " + collection.getName());
	    LOG.debug("Reindexing collection " + collection.getName());
        
        if (!repairMode)
            dropIndex(collection);
        for(Iterator i = collection.iterator(this); i.hasNext(); ) {
            DocumentImpl next = (DocumentImpl)i.next();
            reindex(transaction, next, repairMode);
        }
        for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
            String next = (String)i.next();
            Collection child = getCollection(collection.getName() + '/' + next);
            if(child == null)
                LOG.warn("Collection " + next + " not found");
            else {
                reindex(transaction, child, repairMode);
            }
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
	    reindex(collection, false);
	}
	
    protected void repair() throws PermissionDeniedException {
        Collection root = getCollection(ROOT_COLLECTION);
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        LOG.debug("Removing index files ...");
        clearContentLoadingObservers();
        
        elementsDb.closeAndRemove();
        config.setProperty("db-connection.elements", null);
        
        dbWords.closeAndRemove();
        config.setProperty("db-connection.words", null);
        
        valuesDb.closeAndRemove();
        config.setProperty("db-connection.values", null);
        
        if (qnameValueIndexation) {
            valuesDbQname.closeAndRemove();
            config.setProperty("db-connection2.values", null);
        }
        LOG.debug("Recreating index files ...");
        try {
            createIndexFiles();
        } catch (DBException e) {
            LOG.warn("Exception during repair: " + e.getMessage(), e);
        }
        LOG.info("Reindexing database files ...");
        reindex(null, root, true);
    }
    
    public boolean removeCollection(final Txn transaction, Collection collection) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        if (!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("User '"+ user.getName() + "' not allowed to remove collection '" + collection.getName() + "'");
        
        long start = System.currentTimeMillis();
        
        final boolean isRoot = collection.getParentPath() == null;
        
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            String name = collection.getName();
            
            if (!isRoot) {
                // remove from parent collection
                Collection parent = openCollection(collection.getParentPath(), Lock.WRITE_LOCK);
                // keep the lock for the transaction
                if (transaction != null)
                    transaction.registerLock(parent.getLock(), Lock.WRITE_LOCK);
                if (parent != null) {
                    try {
                        parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
                        saveCollection(transaction, parent);
                    } catch (LockException e) {
                        LOG.warn("LockException while removing collection " + name);
                    } finally {
                        if (transaction == null)
                            collection.getLock().release();
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
                    removeCollection(transaction, childCollection);
                } finally {
                    childCollection.getLock().release();
                }
            }
            
            Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);
                
                // if this is not the root collection remove it completely
                if (isRoot)
                    saveCollection(transaction, collection);
                else {
                    Value key;
                    try {
                        key = new Value(name.getBytes("UTF-8"));
                    } catch (UnsupportedEncodingException uee) {
                        key = new Value(name.getBytes());
                    }	
                    collectionsDb.remove(transaction, key);
                    collectionsCache.remove(collection);
                    freeCollection(transaction, collection.getId());
                }
                
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock on collections.dbx");
            } catch (ReadOnlyException e) {
                throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            } finally {
                lock.release();
            }
            
            notifyDropIndex(collection);
//            textEngine.dropIndex(collection);
//            elementIndex.dropIndex(collection);
//            valueIndex.dropIndex(collection);
//            qnameValueIndex.dropIndex(collection);
            
            LOG.debug("removing resources ...");
            for (Iterator i = collection.iterator(this); i.hasNext();) {
                final DocumentImpl doc = (DocumentImpl) i.next();
//                LOG.debug("removing document " + doc.getFileName());
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() {
                        if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                            domDb.removeOverflowValue(transaction, ((BinaryDocument)doc).getPage());
                        } else {
                            NodeImpl node = (NodeImpl)doc.getFirstChild();
                            domDb.removeAll(transaction, node.getInternalAddress());
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
                            domDb.remove(transaction, query, null);
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
                removeDocMetadata(transaction, doc);
                freeDocument(transaction, doc.getDocId());
            }
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Removing collection took " + (System.currentTimeMillis() - start));
        return true;
    }

	public void removeDocument(final Txn transaction, final DocumentImpl document, 
            boolean freeDocId) throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		try {
            if (LOG.isInfoEnabled()) {
                LOG.info("removeDocument() - "
                    + "removing document "
                    + document.getDocId()
                    + " ...");
            }
            
            notifyDropIndex(document);
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
			new DOMTransaction(this, domDb) {
				public Object start() {
				    NodeImpl node = (NodeImpl)document.getFirstChild();
					domDb.removeAll(transaction, node.getInternalAddress());
					return null;
				}
			}
			.run();
			
			NodeRef ref = new NodeRef(document.getDocId());
			final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			new DOMTransaction(this, domDb) {
				public Object start() {
					try {
						domDb.remove(transaction, idx, null);
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
            
            removeDocMetadata(transaction, document);
            
			if(freeDocId)
			    freeDocument(transaction, document.getDocId());
		} catch (ReadOnlyException e) {
            LOG.warn("removeDocument(String) - " + DATABASE_IS_READ_ONLY);
		}
	}

    /**
     * @param transaction
     * @param document
     */
    private void removeDocMetadata(final Txn transaction, final DocumentImpl document) {
        // remove document metadata
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            
            Value key = new DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on collections.dbx");
        } finally {
            lock.release();
        }
    }

	/** Removes the Node Reference from the database.
	 * The index will be updated later, i.e. after all nodes have been physically 
	 * removed. See {@link #endRemove()}. 
	 * removeNode() just adds the node ids to the list in elementIndex 
	 * for later removal.
	 */
    public void removeNode(final Txn transaction, final NodeImpl node, NodePath currentPath, String content) {
		final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
		final IndexSpec idxSpec = 
		    doc.getCollection().getIdxConf(this);
		final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
		final long gid = node.getGID();
		final short nodeType = node.getNodeType();
//		final String nodeName = node.getNodeName();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
			public Object start() {
				final long address = node.getInternalAddress();
				if (address > -1)
					domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getGID()), address);
				else
					domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getGID()));
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
				
				if (idxSpec != null) {
				    GeneralRangeIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeElement(spec.getType(), (ElementImpl) node, content);
				    }
				}				
				// qnameValueIndex.removeElement((ElementImpl) node, currentPath, content);
				notifyRemoveElement( (ElementImpl) node, currentPath, content );
				break;
				
			case Node.ATTRIBUTE_NODE :
			    currentPath.addComponent(node.getQName());
				elementIndex.setDocument(doc);
				qname = node.getQName();
				qname.setNameType(ElementValue.ATTRIBUTE);
				elementIndex.addRow(qname, tempProxy);
				
				// check if attribute value should be fulltext-indexed
				// by calling IndexPaths.match(path) 
				boolean indexAttribs = true;
				if(ftIdx != null) {
					indexAttribs = ftIdx.matchAttribute(currentPath);
				}
				if (indexAttribs)
					textEngine.storeAttribute(ftIdx, (AttrImpl) node);
				if (idxSpec != null) {
				    GeneralRangeIndexSpec spec = idxSpec.getIndexByPath(currentPath);
				    if(spec != null) {
				        valueIndex.setDocument(doc);
				        valueIndex.storeAttribute(spec, (AttrImpl) node);
				    }				    
//					RangeIndexSpec qnIdx = idxSpec.getIndexByQName(idxQName);
//					if (qnIdx != null && qnameValueIndexation) {
//						qnameValueIndex.setDocument(doc);
//						qnameValueIndex.storeAttribute(qnIdx, (AttrImpl) node);
//					}
				}
				
				if (qnameValueIndex != null)
					qnameValueIndex.removeAttribute( (AttrImpl)node, currentPath, true);
                // qnameValueIndex.storeAttribute( (AttrImpl)node, currentPath, true);

				
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
				break;
		}
	}

	/**
     * Saves the specified collection to storage. Collections are usually cached in
     * memory. If a collection is modified, this method needs to be called to make
     * the changes persistent.
     * 
     * Note: appending a new document to a collection does not require a save.
     * Instead, {@link #addDocument(Collection, DocumentImpl)} is called.
	 */
    public void saveCollection(Txn transaction, Collection collection) throws PermissionDeniedException {
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
				collection.setId(getNextCollectionId(transaction));

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
				final long addr = collectionsDb.put(transaction, name, ostream.data(), true);
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

    /** move Resource to another collection, with possible rename */
	public void moveResource(Txn transaction, DocumentImpl doc, Collection destination, String newName)
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
      
	    if (doc.getUserLock() != null) {
           if(!(doc.getUserLock().getName()).equals(user.getName()))
     	    	throw new PermissionDeniedException("Cannot move the resource because is locked by another user " +
     	               doc.getFileName());
	    }
 	    
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
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
                    destination.removeBinaryResource(transaction, this, oldDoc);
                else
                    destination.removeDocument(transaction, this, oldDoc.getFileName());
	        } else
	            if(!destination.getPermissions().validate(user, Permission.WRITE))
	    	        throw new PermissionDeniedException("Insufficient privileges on target collection " +
	    	                destination.getName());
	            
	        boolean renameOnly = collection.getId() == destination.getId();
	        collection.unlinkDocument(doc);
            removeDocMetadata(transaction, doc);
            doc.setFileName(newName);
            doc.setCollection(destination);
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {
    	        if(!renameOnly) {
                    notifyDropIndex(doc);
    				saveCollection(transaction, collection);
    	        }
    			destination.addDocument(transaction, this, doc);
    
    	        if(!renameOnly) {
    		        // reindexing
    				reindex(transaction, doc, false);
    	        }
            } else {
                // binary resource
                destination.addDocument(transaction, this, doc);
            }
            storeDocument(transaction, doc);
			saveCollection(transaction, destination);
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(e.getMessage());
        } finally {
	        lock.release();
	    }
	}
	
	public void copyCollection(Txn transaction, Collection collection, Collection destination, String newName)
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
	    		removeCollection(transaction, old);
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
		    destCollection = getOrCreateCollection(transaction, newName);
		    for(Iterator i = collection.iterator(this); i.hasNext(); ) {
		    	DocumentImpl child = (DocumentImpl) i.next();
		    	LOG.debug("Copying resource: " + child.getName());
		    	DocumentImpl newDoc = new DocumentImpl(this, child.getFileName(), destCollection);
		        newDoc.copyOf(child);
                newDoc.setDocId(getNextDocId(transaction, destination));
		        copyResource(transaction, child, newDoc);
                storeDocument(transaction, newDoc);
		        destCollection.addDocument(transaction, this, newDoc);
		    }
		    saveCollection(transaction, destCollection);
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
	            	copyCollection(transaction, child, destCollection, childName);
	            } finally {
	            	child.release();
	            }
	        }
	    }
	    saveCollection(transaction, destCollection);
	    saveCollection(transaction, destination);
	}
	
	public void moveCollection(Txn transaction, Collection collection, Collection destination, String newName) 
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
	    		removeCollection(transaction, old);
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
				collectionsDb.remove(transaction, key);
				
			    collection.setName(destination.getName() + '/' + newName);
			    collection.setCreationTime(System.currentTimeMillis());
			    
			    destination.addCollection(collection);
			    if(parent != null)
			        saveCollection(transaction, parent);
			    if(parent != destination)
			        saveCollection(transaction, destination);
			    saveCollection(transaction, collection);
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
		            	moveCollection(transaction, child, collection, childName);
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
		if (relation == Constants.REGEXP) {
				regexp = Pattern.compile(expr.toLowerCase(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
				truncation = Constants.REGEXP;
		}
		for (Iterator i = context.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			try {
				domDb.getLock().acquire(Lock.READ_LOCK);
				domDb.setOwnerObject(this);
				content = domDb.getNodeValue(p, false);
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
                    Matcher matcher = regexp.matcher(cmp);
					if (regexp != null && matcher.find()) {
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
			
//            if (qnameValueIndexation)
//                valuesDbQname.close();
			if (qnameValueIndex != null)
				qnameValueIndex.close();
			
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
	 *      fulltext-indexed).  @param index switch to activate fulltext indexation
	 */
	public void store(final Txn transaction, final NodeImpl node, NodePath currentPath, boolean index) {
	    checkAvailableMemory();
        
        final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
//        final boolean isTemp = TEMP_COLLECTION.equals(doc.getCollection().getName());
        final IndexSpec idxSpec = 
            doc.getCollection().getIdxConf(this);
//        final FulltextIndexSpec ftIdx = idxSpec != null ? idxSpec.getFulltextIndexSpec() : null;
        final long gid = node.getGID();
        if (gid < 0) {
            LOG.debug("illegal node: " + gid + "; " + node.getNodeName());
            Thread.dumpStack();
            return;
        }
        final short nodeType = node.getNodeType();
        final int depth = idxSpec == null ? defaultIndexDepth : idxSpec.getIndexDepth();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            public Object start() throws ReadOnlyException {
                long address = -1;
                final byte data[] = node.serialize();
                if (nodeType == Node.TEXT_NODE
                    || nodeType == Node.ATTRIBUTE_NODE
                    || doc.getTreeLevel(gid) > depth)
                    address = domDb.add(transaction, data);
                else {
                    address = domDb.put(transaction, new NodeRef(doc.getDocId(), gid), data);
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

        nodeProcessor.reset(transaction, node, currentPath, index);
        nodeProcessor.doIndex();
	}

    /** check available memory */
    private void checkAvailableMemory() {
        if (nodesCount > MEM_LIMIT_CHECK) {
            final int percent = (int) (run.freeMemory() /
                    (run.totalMemory() / 100));
            if (percent < memMinFree) {
                //LOG.info(
                //  "total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                flush();
                System.gc();
                LOG.info(
                    "total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
            }
        }
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
	 */
	public void endElement(final NodeImpl node, NodePath currentPath, String content) {
	    final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
//	    tempProxy.reset(doc, node.getGID(), node.getInternalAddress());
        final NodeProxy tempProxy = new NodeProxy(doc, node.getGID(), node.getInternalAddress());
//	    final IndexSpec idxSpec = 
//		    doc.getCollection().getIdxConf(this);
		
		final int indexType = ((ElementImpl) node).getIndexType();
		tempProxy.setIndexType(indexType);
		
		node.getQName().setNameType(ElementValue.ELEMENT);
		// TODO move_to NativeValueIndex
		if (RangeIndexSpec.hasRangeIndex(indexType)) {
				if (content == null)
					content = getNodeValue(tempProxy, false);
		        valueIndex.setDocument(doc);
		        valueIndex.storeElement(RangeIndexSpec.indexTypeToXPath(indexType), 
						(ElementImpl) node, content.toString());
		}
		
		// TODO move_to NativeValueIndexByQName	
		if ( RangeIndexSpec.hasQNameIndex(indexType) ) {
//			RangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
			if (content == null)
				content = getNodeValue(tempProxy, false);
			
//			qnameValueIndex.setDocument(doc);
//			qnameValueIndex.storeElement(qnIdx.getType(), 
//					(ElementImpl) node, content.toString());
			
			if (qnameValueIndex != null)
				qnameValueIndex.endElement((ElementImpl) node, currentPath, content);
			// notifyEndElement((ElementImpl) node, currentPath, content);
		}
		
//		 TODO move_to NativeElementIndex; name change (See ContentLoadingObserver ): addRow() --> endElement()
		// save element by calling ElementIndex
		elementIndex.setDocument(doc);
		elementIndex.addRow(node.getQName(), tempProxy);
	}
    
	/** store Document entry into its collection. */
	public void storeDocument(final Txn transaction, final DocumentImpl doc) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
            doc.write(ostream);
            Value key = new DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            collectionsDb.put(transaction, key, ostream.data(), true);
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on collections.dbx");
        } catch (IOException e) {
            LOG.warn("IOException while writing document data", e);
        } finally {
            lock.release();
        }
	}

    public void readDocuments(Collection collection) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            
            Value key = new DocumentKey(collection.getId());
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, new DocumentCallback(collection));
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on collections.dbx");
        } catch (BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } catch (TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release();
        }
    }
	
	public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data) {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				LOG.debug("Storing binary resource " + blob.getFileName());
				blob.setPage(domDb.addBinary(transaction, blob, data));
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

	public void removeBinaryResource(final Txn transaction, final BinaryDocument blob)
		throws PermissionDeniedException {
		if (readOnly)
			throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		LOG.info("removing binary resource " + blob.getDocId() + "...");
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
				domDb.removeOverflowValue(transaction, blob.getPage());
				return null;
			}
		}
		.run();
        removeDocMetadata(transaction, blob);
	}

	public void sync(int syncEvent) {
        if (isReadOnly())
            return;
		try {
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
                Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    collectionsDb.flush();
                } catch (LockException e) {
                    LOG.warn("failed to acquire lock on collections store", e);
                } finally {
                    lock.release();
                }
                notifySync();
//				System.gc();
				Runtime runtime = Runtime.getRuntime();
				LOG.info("Memory: " + (runtime.totalMemory() / 1024) + "K total; " +
						(runtime.maxMemory() / 1024) + "K max; " +
						(runtime.freeMemory() / 1024) + "K free");
				
				// uncomment this to get statistics on page buffer usage
				collectionsDb.printStatistics();
                elementsDb.printStatistics();
				valuesDb.printStatistics();
				domDb.printStatistics();
				if (valuesDbQname != null)
					valuesDbQname.printStatistics();
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

	public void update(final Txn transaction, final NodeImpl node) {
		try {
			final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
			final long internalAddress = node.getInternalAddress();
			final byte[] data = node.serialize();
			new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
					if (-1 < internalAddress)
						domDb.update(transaction, internalAddress, data);
					else {
						domDb.update(transaction, new NodeRef(doc.getDocId(), node.getGID()), data);
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
	public void insertAfter(final Txn transaction, final NodeImpl previous, final NodeImpl node) {
		final byte data[] = node.serialize();
		final DocumentImpl doc = (DocumentImpl) previous.getOwnerDocument();
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
			public Object start() {
				long address = previous.getInternalAddress();
				if (address > -1) {
					address = domDb.insertAfter(transaction, doc, address, data);
				} else {
					NodeRef ref = new NodeRef(doc.getDocId(), previous.getGID());
					address = domDb.insertAfter(transaction, doc, ref, data);
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

	/** store into the temporary collection of the database a given in-memory Document */
	public DocumentImpl storeTemporaryDoc(org.exist.memtree.DocumentImpl doc) 
    throws EXistException, PermissionDeniedException, LockException {
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        
        user = pool.getSecurityManager().getUser(SecurityManager.DBA_USER);
		String docName = MD5.md(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis())) +
			".xml";
		Collection temp = openCollection(TEMP_COLLECTION, Lock.WRITE_LOCK);
        
        try {
            if(temp == null)
                temp = createTempCollection(transaction);
            else
                transaction.registerLock(temp.getLock(), Lock.WRITE_LOCK);
            DocumentImpl targetDoc = new DocumentImpl(this, temp);
            targetDoc.setFileName(docName);
            targetDoc.setPermissions(0771);
            long now = System.currentTimeMillis();
            targetDoc.setLastModified(now);
            targetDoc.setCreated(now);
            targetDoc.setDocId(getNextDocId(transaction, temp));

    		DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc);
            indexer.scan();
            indexer.store();
            temp.addDocument(transaction, this, targetDoc);
            storeDocument(transaction, targetDoc);
            closeDocument();
            flush();
            transact.commit(transaction);
            return targetDoc;
        } catch (Exception e) {
            transact.abort(transaction);
        }
        return null;
	}
	
	/** remove from the temporary collection of the database a given list of Documents. */
	public void removeTempDocs(List docs) {
		Collection temp = openCollection(TEMP_COLLECTION, Lock.WRITE_LOCK);
		if(temp == null)
			return;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        txn.registerLock(temp.getLock(), Lock.WRITE_LOCK);
		try {
			for(Iterator i = docs.iterator(); i.hasNext(); )
				temp.removeDocument(txn, this, (String) i.next());
            transact.commit(txn);
		} catch (PermissionDeniedException e) {
            transact.abort(txn);
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} catch (TriggerException e) {
            transact.abort(txn);
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} catch (LockException e) {
            transact.abort(txn);
			LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
		} catch (TransactionException e) {
            transact.abort(txn);
            LOG.warn(TEMP_FRAGMENT_REMOVE_ERROR, e);
        }
	}

	/** remove temporary collection */	
	public void cleanUpAll() {
		Collection temp = getCollection(TEMP_COLLECTION);
		if(temp == null)
			return;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
		try {
			removeCollection(txn, temp);
            transact.commit(txn);
		} catch (PermissionDeniedException e) {
            transact.abort(txn);
			LOG.warn("Failed to remove temporary collection: " + e.getMessage(), e);
		} catch (TransactionException e) {
            transact.abort(txn);
            LOG.warn("Failed to remove temporary collection: " + e.getMessage(), e);
        }
	}
	
	/** remove all documents from temporary collection */	
	public void cleanUp() {
		Collection temp = getCollection(TEMP_COLLECTION);
		if(temp == null)
			return;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for(Iterator i = temp.iterator(this); i.hasNext(); ) {
            	DocumentImpl next = (DocumentImpl) i.next();
            	long modified = next.getLastModified();
            	if(now - modified > TEMP_FRAGMENT_TIMEOUT)
            		try {
            			temp.removeDocument(txn, this, next.getFileName());
            		} catch (PermissionDeniedException e) {
            			LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
            		} catch (TriggerException e) {
            			LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
            		} catch (LockException e) {
            			LOG.warn("Failed to remove temporary fragment: " + e.getMessage(), e);
            		}
            }
            transact.commit(txn);
        } catch (TransactionException e) {
            transact.abort(txn);
            LOG.warn("Transaction aborted: " + e.getMessage(), e);
        }
	}
	
	/** create temporary collection */	
	private Collection createTempCollection(Txn transaction) throws LockException, PermissionDeniedException {
		User u = user;
		Lock lock = null;
		try {
			lock = collectionsDb.getLock();
			lock.acquire(Lock.WRITE_LOCK);
			user = pool.getSecurityManager().getUser(SecurityManager.DBA_USER);
			Collection temp = getOrCreateCollection(transaction, TEMP_COLLECTION);
			temp.setPermissions(0771);
			saveCollection(transaction, temp);
			return temp;
		} finally {
			user = u;
            lock.release();
		}
	}
    
    public int getBackendType() {
        return NATIVE;
    }
    
    public DOMFile getDOMFile() {
        return domDb;
    }
    
    public BTree getStorage(byte id) {
        switch (id) {
            case COLLECTIONS_DBX_ID :
                return collectionsDb;
            case ELEMENTS_DBX_ID :
            	return elementsDb;
            case WORDS_DBX_ID :
            	return dbWords;
            case VALUES_DBX_ID :
                return valuesDb;
            case VALUES_QNAME_DBX_ID :
            	return valuesDbQname;
            case DOM_DBX_ID :
                return domDb;
            default:
                return null;
        }
    }
    
    public byte[] getStorageFileIds() {
    	return ALL_STORAGE_FILES;
    }
    

	public final static class NodeRef extends Value {
        /**
         * Log4J Logger for this class
         */
//        private static final Logger LOG = Logger.getLogger(NodeRef.class);

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


	public NativeValueIndexByQName getQNameValueIndex() {
		return qnameValueIndex;
	}

    private final class DocumentCallback implements BTreeCallback {
        
        private Collection collection;
        
        public DocumentCallback(Collection collection) {
            this.collection = collection;
        }
        
        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            try {
                byte type = key.data()[key.start() + 2];
                VariableByteInput istream = collectionsDb.getAsStream(pointer);
                DocumentImpl doc = null;
                if (type == DocumentImpl.BINARY_FILE)
                    doc = new BinaryDocument(NativeBroker.this, collection);
                else
                    doc = new DocumentImpl(NativeBroker.this, collection);
                doc.read(istream);
                doc.setAddress(pointer);
                collection.addDocument(null, NativeBroker.this, doc);
            } catch (EOFException e) {
                LOG.debug("EOFException while reading document data", e);
            } catch (IOException e) {
                LOG.debug("IOException while reading document data", e);
            }
            return true;
        }
    }
}
