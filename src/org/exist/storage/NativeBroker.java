/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.AttrImpl;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.memtree.DOMIndexer;
import org.exist.numbering.NodeId;
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
import org.exist.storage.btree.Paged.Page;
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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Observer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  Main class for the native XML storage backend.
 *  By "native" it is meant file-based, embedded backend.
 * 
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 * 
 * Observer Design Pattern: role : this class is the subject (alias observable)
 * for various classes that generate indices for the database content :
 * @link org.exist.storage.NativeElementIndex
 * @link org.exist.storage.NativeTextEngine
 * @link org.exist.storage.NativeValueIndex 
 * @link org.exist.storage.NativeValueIndexByQName
 * 
 * This class dispatches the various events (defined by the methods 
 * of @link org.exist.storage.ContentLoadingObserver) to indexing classes.
 * 
 *@author     Wolfgang Meier
 */
public class NativeBroker extends DBBroker {
	
    public static final byte PREPEND_DB_ALWAYS = 0;
    public static final byte PREPEND_DB_NEVER = 1;
    public static final byte PREPEND_DB_AS_NEEDED = 2;
    
    public static final byte COLLECTIONS_DBX_ID = 0;
    public static final byte ELEMENTS_DBX_ID = 1;
    public static final byte VALUES_DBX_ID = 2;
    public static final byte WORDS_DBX_ID = 3;
    public static final byte DOM_DBX_ID = 4;
    public static final byte VALUES_QNAME_DBX_ID = 5;
    //Note : no ID for symbols ? Too bad...

    public static final String PROPERTY_PAGE_SIZE = "db-connection.page-size";
    public static final String PROPERTY_MIN_FREE_MEMORY = "db-connection.min_free_memory";
    public static final String PROPERTY_INDEX_DEPTH = "indexer.index-depth";
    
    private static final byte[] ALL_STORAGE_FILES = {
    	COLLECTIONS_DBX_ID, ELEMENTS_DBX_ID, VALUES_DBX_ID,
    	VALUES_QNAME_DBX_ID, WORDS_DBX_ID, DOM_DBX_ID
    };
    
    private static final String TEMP_FRAGMENT_REMOVE_ERROR = "Could not remove temporary fragment";
	// private static final String TEMP_STORE_ERROR = "An error occurred while storing temporary data: ";
	private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
	private static final String DATABASE_IS_READ_ONLY = "database is read-only";
    
    public static final String DEFAULT_DATA_DIR = "data";
    public static final int DEFAULT_PAGE_SIZE = 4096;
    public static final int DEFAULT_INDEX_DEPTH = 1;
    public static final int DEFAULT_MIN_MEMORY = 5000000;
    public static final long TEMP_FRAGMENT_TIMEOUT = 60000;
    /** default buffer size setting */
    public static final int BUFFERS = 256;
    /** check available memory after storing DEFAULT_NODES_BEFORE_MEMORY_CHECK nodes */
    public static final int DEFAULT_NODES_BEFORE_MEMORY_CHECK = 10000;
    
	public static int OFFSET_COLLECTION_ID = 0;
	public static int OFFSET_VALUE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2

	/** the database files */
	protected CollectionStore collectionsDb;
	protected DOMFile domDb;
	protected File symbolsFile;

	protected SymbolTable symbols = null;
    
	/** the index processors */	
	protected NativeElementIndex elementIndex;
	protected NativeValueIndex valueIndex;
	protected NativeValueIndexByQName qnameValueIndex;
	protected NativeTextEngine textEngine;
    
    protected IndexSpec indexConfiguration;
    
    protected int defaultIndexDepth;    
	
	protected Serializer xmlSerializer;		
	
	protected boolean readOnly = false;
	
	protected int memMinFree;
	
	/** used to count the nodes inserted after the last memory check */
	protected int nodesCount = 0;

    protected String dataDir;
	protected int pageSize;
	
	protected byte prepend;
	
	private final Runtime run = Runtime.getRuntime();

    private NodeProcessor nodeProcessor = new NodeProcessor();
    
	/** initialize database; read configuration, etc. */
	public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
		super(pool, config);
		LOG.debug("Initializing broker " + hashCode());
        
        String prependDB = (String) config.getProperty("db-connection.prepend-db");
		if ("always".equalsIgnoreCase(prependDB)) {
            prepend = PREPEND_DB_ALWAYS;
		} else if("never".equalsIgnoreCase(prependDB)) {
			prepend = PREPEND_DB_NEVER;
		} else {
			prepend = PREPEND_DB_AS_NEEDED;
		}

        dataDir = (String) config.getProperty("db-connection.data-dir");
		if (dataDir == null)
            dataDir = DEFAULT_DATA_DIR;

        pageSize = config.getInteger(PROPERTY_PAGE_SIZE);
		if (pageSize < 0)
            pageSize = DEFAULT_PAGE_SIZE;
        Paged.setPageSize(pageSize);

        defaultIndexDepth = config.getInteger(PROPERTY_INDEX_DEPTH);
		if (defaultIndexDepth < 0)
			defaultIndexDepth = DEFAULT_INDEX_DEPTH;
        
        memMinFree = config.getInteger(PROPERTY_MIN_FREE_MEMORY);
		if (memMinFree < 0)
			memMinFree = DEFAULT_MIN_MEMORY;
        
        indexConfiguration = (IndexSpec) config.getProperty("indexer.config");         
        xmlSerializer = new NativeSerializer(this, config);
        user = SecurityManager.SYSTEM_USER;            
        
        readOnly = pool.isReadOnly();
		try {
			
			//TODO : refactor so that we can,
			//1) customize the different properties (file names, cache settings...)
			//2) have a consistent READ-ONLY behaviour (based on *mandatory* files ?)
			//3) have consistent file creation behaviour (we can probably avoid some unnecessary files)
			//4) use... *customized* factories for a better index plugability ;-)
			
            // Initialize DOM storage
			domDb = (DOMFile) config.getProperty(DOMFile.getConfigKeyForFile());
			if (domDb == null)
				domDb =	new DOMFile(pool, DOM_DBX_ID, dataDir, config);	                        
			readOnly = readOnly || domDb.isReadOnly();    
            
			// Initialize collections storage            
            collectionsDb = (CollectionStore) config.getProperty(CollectionStore.getConfigKeyForFile());
			if (collectionsDb == null) 
				collectionsDb = new CollectionStore(pool, COLLECTIONS_DBX_ID, dataDir, config);	
			readOnly = readOnly || collectionsDb.isReadOnly();
            
            // Initialize symbols storage
			//Notice that there is no ID :-(
    		symbols = (SymbolTable) config.getProperty("db-connection.symbol-table");
    		if (symbols == null) 
    			symbols = new SymbolTable(pool, dataDir, config);    			
    		readOnly = readOnly || !symbols.getFile().canWrite();
            
    		elementIndex = new NativeElementIndex(this, ELEMENTS_DBX_ID, dataDir, config);
        	valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);        	
    		qnameValueIndex = new NativeValueIndexByQName(this, VALUES_QNAME_DBX_ID, dataDir, config);    		
    		textEngine = new NativeTextEngine(this, WORDS_DBX_ID, dataDir, config);    		
			
			if (readOnly)
				LOG.info("Database runs in read-only mode");

		} catch (DBException e) {
			LOG.debug(e.getMessage(), e);
			throw new EXistException(e);
		}
	}
        
    public void addObserver(Observer o) {
        super.addObserver(o);
        textEngine.addObserver(o);
        elementIndex.addObserver(o);
        //TODO : what about other indexes observers ?
    }
    
    public void deleteObservers() {
        super.deleteObservers();
        if (elementIndex != null)
            elementIndex.deleteObservers();
        //TODO : what about other indexes observers ?
        if (textEngine != null)
            textEngine.deleteObservers();
    }
  
    // ============ dispatch the various events to indexing classes ==========

    private void notifyStartElement(ElementImpl elem, NodePath currentPath, boolean index) {
        // WM: don't use an iterator here. The method may be called a few million times for a single document. 
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.startElement(elem, currentPath, index);
        }
	}
    
    private void notifyRemoveElement(ElementImpl elem, NodePath currentPath, String content) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.removeElement(elem, currentPath, content);
        }
    }

    private void notifyStoreAttribute(AttrImpl attr, NodePath currentPath, int indexingHint, RangeIndexSpec spec) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.storeAttribute(attr, currentPath, indexingHint, spec);
        }	
	}	

	private void notifyStoreText(TextImpl text, NodePath currentPath, int indexingHint) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.storeText(text, currentPath, indexingHint);
        }
	}
    
    private void notifyDropIndex(Collection collection) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.dropIndex(collection);
        }
    }

    private void notifyDropIndex(DocumentImpl doc) throws ReadOnlyException {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.dropIndex(doc);
        }
    }
    
    private void notifyRemove() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.remove();
        }
    }
    
    private void notifySync() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.sync();
        }
    }

    private void notifyFlush() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
        	try {
        		observer.flush();
        	} catch (DBException e) {
        		LOG.warn(e);
        		//Ignore the exception ; try to continue on other files
        	}
        }
    }    

    private void notifyPrintStatistics() throws DBException {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.printStatistics();
        }      
    }    
    
    private void notifyClose() throws DBException {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.close();
        }     
        clearContentLoadingObservers();
    }

    private void notifyCloseAndRemove() {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
        	ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.closeAndRemove();
        }
        clearContentLoadingObservers();
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
    public void endElement(final StoredNode node, NodePath currentPath, String content) {
        final int indexType = ((ElementImpl) node).getIndexType();
        
        //TODO : do not care about the current code redundancy : this will move in the (near) future
        
        // TODO : move to NativeValueIndex
        if (RangeIndexSpec.hasRangeIndex(indexType)) {
        	node.getQName().setNameType(ElementValue.ELEMENT);
            if (content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());                
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            valueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
            valueIndex.storeElement((ElementImpl) node, content, RangeIndexSpec.indexTypeToXPath(indexType));
        }
        
        // TODO : move to NativeValueIndexByQName 
        if ( RangeIndexSpec.hasQNameIndex(indexType) ) {
        	node.getQName().setNameType(ElementValue.ELEMENT);
            if (content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            qnameValueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
            qnameValueIndex.endElement((ElementImpl) node, currentPath, content);
        }

        // TODO : move to NativeTextEngine 
        if (RangeIndexSpec.hasMixedTextIndex(indexType)) {
        	node.getQName().setNameType(ElementValue.ELEMENT);
            if (content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            textEngine.setDocument((DocumentImpl) node.getOwnerDocument());
            textEngine.storeText(node, content, NativeTextEngine.FOURTH_OPTION, null);
        }

        FulltextIndexSpec ftIdx = ((DocumentImpl)node.getOwnerDocument()).getCollection().getFulltextIndexConfiguration(this);
        if (ftIdx != null && ftIdx.hasQNameIndex(node.getQName())) {
        	node.getQName().setNameType(ElementValue.ELEMENT);
            if (content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            //Grrr : unify with above !
            textEngine.setDocument((DocumentImpl) node.getOwnerDocument());
            textEngine.storeText(node, content, NativeTextEngine.TEXT_BY_QNAME, ftIdx);
        }
    }

    /*
    private String getOldNodeContent(StoredNode node, long oldAddress) {
        NodeProxy p = new NodeProxy(node);
        if (oldAddress != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
            p.setInternalAddress(oldAddress);
        String content = getNodeValue(node, false);
        //Curious... I assume getNodeValue() needs the old address
        p.setInternalAddress(node.getInternalAddress());
        return content;
    }*/

    /** Takes care of actually remove entries from the indices;
     * must be called after one or more call to {@link #removeNode(Txn, StoredNode, NodePath, String)}. */
    public void endRemove() {
        notifyRemove();
    }
    
    public int getBackendType() {
        return NATIVE;
    }
    
    public boolean isReadOnly() {
        return readOnly;
    } 
    
    public int getPageSize() {
        return pageSize;
    }    
    
    public DOMFile getDOMFile() {
        return domDb;
    }
    
    public BTree getStorage(byte id) {
    	//Notice that there is no entry for the symbols table
        switch (id) {
        	case DOM_DBX_ID :
        		return domDb;
            case COLLECTIONS_DBX_ID :
                return collectionsDb;
            case ELEMENTS_DBX_ID :
                return elementIndex.dbNodes;
            case VALUES_DBX_ID :
                return valueIndex.dbValues;
            case VALUES_QNAME_DBX_ID :
                return qnameValueIndex.dbValues;
            case WORDS_DBX_ID :
                return textEngine.dbTokens;
            default:
                return null;
        }
    }
    
    public byte[] getStorageFileIds() {
        return ALL_STORAGE_FILES;
    }        

	public SymbolTable getSymbols() {
		return symbols;
	}

	public IndexSpec getIndexConfiguration() {
	    return indexConfiguration;
	}
    
    public ElementIndex getElementIndex() {
        return elementIndex;
    }    

    public NativeValueIndex getValueIndex() {
        return valueIndex;
    }
    
    public NativeValueIndexByQName getQNameValueIndex() {
        return qnameValueIndex;
    }     
    
    public TextSearchEngine getTextEngine() {
        return textEngine;
    }
    
    public Iterator getDOMIterator(StoredNode node) {
        try {
            return new DOMFileIterator(this, domDb, new NodeProxy(node));
        } catch (BTreeException e) {
            LOG.warn("failed to create DOM iterator", e);
        } catch (IOException e) {
            LOG.warn("failed to create DOM iterator", e);
        }
        return null;
    }

    public Iterator getNodeIterator(StoredNode node) {
       if (node == null)
          throw new IllegalArgumentException("The node parameter cannot be null.");
        try {
            return new NodeIterator(this, domDb, node, false);
        } catch (BTreeException e) {
            LOG.warn("failed to create node iterator", e);
        } catch (IOException e) {
            LOG.warn("failed to create node iterator", e);
        }
        return null;
    }
    
    public Serializer getSerializer() {
        xmlSerializer.reset();
        return xmlSerializer;
    }
    
    public Serializer newSerializer() {
        return new NativeSerializer(this, getConfiguration());
    } 
   
    public XmldbURI prepend(XmldbURI uri) {
    	switch(prepend) {
    	case PREPEND_DB_ALWAYS:
    		return uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
    	case PREPEND_DB_AS_NEEDED:
    		return uri.startsWith(XmldbURI.ROOT_COLLECTION_URI)?uri:uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
    	default:
    		return uri;
    	}
    }
    
    /** create temporary collection */  
    private Collection createTempCollection(Txn transaction) throws LockException, PermissionDeniedException {
        User u = user;
        Lock lock = collectionsDb.getLock();;
        try {           
            lock.acquire(Lock.WRITE_LOCK);
            user = pool.getSecurityManager().getUser(SecurityManager.DBA_USER);
            Collection temp = getOrCreateCollection(transaction, XmldbURI.TEMP_COLLECTION_URI);
            temp.setPermissions(0771);
            saveCollection(transaction, temp);
            return temp;
        } finally {
            user = u;
            lock.release();
        }
    } 
    
    /** remove temporary collection */  
    public void cleanUpTempCollection() {
        Collection temp = getCollection(XmldbURI.TEMP_COLLECTION_URI);
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
    
    public Collection getOrCreateCollection(Txn transaction, XmldbURI name) throws PermissionDeniedException {
    	name = prepend(name.normalizeCollectionPath());
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            try {
            	//TODO : resolve URIs !
                XmldbURI[] segments = name.getPathSegments();
                XmldbURI path = XmldbURI.ROOT_COLLECTION_URI;
                Collection sub;
                Collection current = getCollection(XmldbURI.ROOT_COLLECTION_URI);
                if (current == null) {
                    LOG.debug("Creating root collection '" + XmldbURI.ROOT_COLLECTION_URI + "'");
                    current = new Collection(XmldbURI.ROOT_COLLECTION_URI);
                    current.getPermissions().setPermissions(0777);
                    current.getPermissions().setOwner(user);
                    current.getPermissions().setGroup(user.getPrimaryGroup());
                    current.setId(getNextCollectionId(transaction));
                    current.setCreationTime(System.currentTimeMillis());
                    if (transaction != null)
                        transaction.acquireLock(current.getLock(), Lock.WRITE_LOCK);
                    saveCollection(transaction, current);
                }
                for(int i=1;i<segments.length;i++) {
                    XmldbURI temp = segments[i];
                    path = path.append(temp);
                    if (current.hasSubcollection(temp)) {
                        current = getCollection(path);
                        if (current == null)
                            LOG.debug("Collection '" + path + "' not found!");
                    } else {
                        if (readOnly)
                            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                        if (!current.getPermissions().validate(user, Permission.WRITE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("User '"+ user.getName() + "' not allowed to write to collection '" + current.getURI() + "'");
                        }
                        LOG.debug("Creating collection '" + path + "'...");
                        sub = new Collection(path);
                        sub.getPermissions().setOwner(user);
                        sub.getPermissions().setGroup(user.getPrimaryGroup());
                        sub.setId(getNextCollectionId(transaction));
                        sub.setCreationTime(System.currentTimeMillis());
                        if (transaction != null)
                            transaction.acquireLock(sub.getLock(), Lock.WRITE_LOCK);
                        current.addCollection(this, sub, true);
                        saveCollection(transaction, current);
                        current = sub;
                    }
                }
                return current;  
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
                return null;                
            } catch (ReadOnlyException e) {
                throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            }                
        }
    }    

	public Collection getCollection(XmldbURI uri) {
		return openCollection(uri, BFile.UNKNOWN_ADDRESS, Lock.NO_LOCK);
	}
	
	public Collection openCollection(XmldbURI uri, int lockMode) {
		return openCollection(uri, BFile.UNKNOWN_ADDRESS, lockMode);
	}

	/**
	 *  Get collection object. If the collection does not exist, null is
	 *  returned.
	 *
	 *@param  name  collection name
	 *@return       The collection value
	 */
	private Collection openCollection(XmldbURI uri, long addr, int lockMode) {
	    uri = prepend(uri.toCollectionPathURI());
	    //We *must* declare it here (see below)
	    Collection collection;
	    final CollectionCache collectionsCache = pool.getCollectionsCache();  
	    synchronized(collectionsCache) {      
	        collection = collectionsCache.get(uri);
	        if (collection == null) {				
	            final Lock lock = collectionsDb.getLock();
	            try {
	                lock.acquire(Lock.READ_LOCK);
	                VariableByteInput is;
	                if (addr == BFile.UNKNOWN_ADDRESS) {
                        Value key = new CollectionStore.CollectionKey(uri.toString());
                        is = collectionsDb.getAsStream(key);
	                } else {
	                    is = collectionsDb.getAsStream(addr);
	                }
	                if (is == null) return null;   

                    collection = new Collection(uri);
	                collection.read(this, is);
	                
	                //TODO : manage this from within the cache -pb
	                if(!pool.isInitializing())
	                    collectionsCache.add(collection);
	                
	                //TODO : rethrow exceptions ? -pb
	            } catch (UnsupportedEncodingException e) {
	                LOG.error("Unable to encode '" + uri + "' in UTF-8");
	                return null;
	            } catch (LockException e) {
	                LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
	                return null;
	            } catch (IOException e) {
	                LOG.error(e.getMessage(), e);
	                return null;
	            } finally {
	                lock.release();
	            }
	        } else
                collectionsCache.add(collection);
        }

        //Important : 
        //This code must remain ouside of the synchonized block
        //because another thread may already own a lock on the collection
        //This would result in a deadlock... until the time-out raises the Exception
        //TODO : make an attempt to an immediate lock ?
        //TODO : manage a collection of requests for locks ?
        //TODO : another yet smarter solution ?
        if(lockMode != Lock.NO_LOCK) {
            try {
            	collection.getLock().acquire(lockMode);
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock on collection '" + uri + "'");
            }
        }
	   
        return collection;           
	}
    
   public void copyCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newUri)
   	throws PermissionDeniedException, LockException {
       if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
       //TODO : resolve URIs !!!
 	   if (newUri != null && newUri.numSegments() !=1) {
		   throw new PermissionDeniedException("New collection name must have one segment!");
	   }
        if(!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("Read permission denied on collection " +
                    collection.getURI());
        if(collection.getId() == destination.getId())
            throw new PermissionDeniedException("Cannot move collection to itself");
        if(!destination.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges on target collection " +
                    destination.getURI());
        if(newUri == null) {
        	newUri = collection.getURI().lastSegment();
        }
        //  check if another collection with the same name exists at the destination
        //TODO : resolve URIs !!! (destination.getURI().resolve(newURI))
        Collection old = openCollection(destination.getURI().append(newUri), Lock.WRITE_LOCK);
        if(old != null) {
            LOG.debug("removing old collection: " + newUri);
            try {
                removeCollection(transaction, old);
            } finally {
                old.release();
            }
        }
        Collection destCollection = null;
        Lock lock = collectionsDb.getLock();
        try {           
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : resolve URIs !!!
            newUri = destination.getURI().append(newUri);
            LOG.debug("Copying collection to '" + newUri + "'");
            destCollection = getOrCreateCollection(transaction, newUri);
            for(Iterator i = collection.iterator(this); i.hasNext(); ) {
                DocumentImpl child = (DocumentImpl) i.next();
                LOG.debug("Copying resource: '" + child.getURI() + "'");
                if (child.getResourceType() == DocumentImpl.XML_FILE) {
                    DocumentImpl newDoc = new DocumentImpl(this, destCollection, child.getFileURI());
                    newDoc.copyOf(child);
                    newDoc.setDocId(getNextResourceId(transaction, destination));
                    copyXMLResource(transaction, child, newDoc);
                    storeXMLResource(transaction, newDoc);
                    destCollection.addDocument(transaction, this, newDoc);
                } else {
                    BinaryDocument newDoc = new BinaryDocument(this, destCollection, child.getFileURI());
                    newDoc.copyOf(child);
                    newDoc.setDocId(getNextResourceId(transaction, destination));
                    byte[] data = getBinaryResource((BinaryDocument) child);
                    storeBinaryResource(transaction, newDoc, data);
                    storeXMLResource(transaction, newDoc);
                    destCollection.addDocument(transaction, this, newDoc);
                }
            }
            saveCollection(transaction, destCollection);
        } finally {
            lock.release();
        }

        XmldbURI name = collection.getURI();
        for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
        	XmldbURI childName = (XmldbURI)i.next();
        	//TODO : resolve URIs ! collection.getURI().resolve(childName)
            Collection child = openCollection(name.append(childName), Lock.WRITE_LOCK);
            if(child == null)
                LOG.warn("Child collection '" + childName + "' not found");
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
    
    public void moveCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newName) 
    throws PermissionDeniedException, LockException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
 	   if(newName!=null && newName.numSegments()!=1) {
		   throw new PermissionDeniedException("New collection name must have one segment!");
	   }
        if(collection.getId() == destination.getId())
            throw new PermissionDeniedException("Cannot move collection to itself");
        if(collection.getURI().equals(XmldbURI.ROOT_COLLECTION_URI))
            throw new PermissionDeniedException("Cannot move the db root collection");
        if(!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move collection " +
                    collection.getURI());
        if(!destination.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges on target collection " +
                    destination.getURI());
            // check if another collection with the same name exists at the destination
        Collection old = openCollection(destination.getURI().append(newName), Lock.WRITE_LOCK);
        if(old != null) {
            try {
                removeCollection(transaction, old);
            } finally {
                old.release();
            }
        }

        XmldbURI uri = collection.getURI();
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            Collection parent = openCollection(collection.getParentURI(), Lock.WRITE_LOCK);
            if(parent != null) {
                try {
                	//TODO : resolve URIs
                    parent.removeCollection(uri.lastSegment());
                } finally {
                    parent.release();
                }
            }
            Lock lock = collectionsDb.getLock();
            try {               
                lock.acquire(Lock.WRITE_LOCK);                
                collectionsCache.remove(collection);
                Value key = new CollectionStore.CollectionKey(uri.toString());
                collectionsDb.remove(transaction, key);
                //TODO : resolve URIs destination.getURI().resolve(newName)
                collection.setPath(destination.getURI().append(newName));
                collection.setCreationTime(System.currentTimeMillis());
                
                destination.addCollection(this, collection, false);
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

            for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
            	XmldbURI childName = (XmldbURI)i.next();
            	//TODO : resolve URIs !!! name.resolve(childName)
            	Collection child = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                if (child == null)
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
    
    public boolean removeCollection(final Txn transaction, Collection collection) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);        
        if (!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("User '"+ user.getName() + "' not allowed to remove collection '" + collection.getURI() + "'");
        long start = System.currentTimeMillis();
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            final XmldbURI uri = collection.getURI();
            final String collName = uri.getRawCollectionPath();
            final boolean isRoot = collection.getParentURI() == null;
            //Drop all index entries
            notifyDropIndex(collection);            
            if (!isRoot) {
                // remove from parent collection
            	//TODO : resolve URIs ! (uri.resolve(".."))
                Collection parentCollection = openCollection(collection.getParentURI(), Lock.WRITE_LOCK);
                // keep the lock for the transaction
                if (transaction != null)
                    transaction.registerLock(parentCollection.getLock(), Lock.WRITE_LOCK);
                if (parentCollection != null) {
                    try {
                        LOG.debug("Removing collection '" + collName + "' from its parent...");
                        //TODO : resolve from collection's base URI
                        parentCollection.removeCollection(uri.lastSegment());
                        saveCollection(transaction, parentCollection);
                    } catch (LockException e) {
                        LOG.warn("LockException while removing collection '" + collName + "'");
                    } finally {
                        if (transaction == null)
                        	parentCollection.getLock().release();
                    }
                }
            }
            // remove child collections
            if (LOG.isDebugEnabled())
                LOG.debug("Removing children collections from their parent '" + collName + "'...");
            for (Iterator i = collection.collectionIterator(); i.hasNext();) {
                final XmldbURI childName = (XmldbURI) i.next();
                //TODO : resolve from collection's base URI
                //TODO : resulve URIs !!! (uri.resolve(childName))
                Collection childCollection = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                try {                    
                    removeCollection(transaction, childCollection);                    
                } finally {
                    childCollection.getLock().release();
                }
            }
            //Update current state
            Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);
                // remove the metadata of all documents in the collection
                Value docKey = new CollectionStore.DocumentKey(collection.getId());
                IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, docKey);
                collectionsDb.removeAll(transaction, query);
                
                // if this is not the root collection remove it...
                if (!isRoot) {
                    Value key = new CollectionStore.CollectionKey(collName);
                    //... from the disk
                    collectionsDb.remove(transaction, key);
                    //... from the cache
                    collectionsCache.remove(collection);
                    //and free its id for any futher use
                    freeCollectionId(transaction, collection.getId());
                } else {
                    //Simply save the collection on disk
                    //It will remain cached
                    //and its id well never be made available 
                    saveCollection(transaction, collection);
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock on '" + collectionsDb.getFile().getName() + "'");
            } catch (ReadOnlyException e) {
                throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            } catch (BTreeException e) {
                LOG.warn("Exception while removing collection: " + e.getMessage(), e);
            } catch (IOException e) {
                LOG.warn("Exception while removing collection: " + e.getMessage(), e);
            } finally {
                lock.release();
            }
            //Remove child resources
            if (LOG.isDebugEnabled())
                LOG.debug("Removing resources in '" + collName + "'...");
            for (Iterator i = collection.iterator(this); i.hasNext();) {
                final DocumentImpl doc = (DocumentImpl) i.next();
                //Remove doc's metadata
                // WM: now removed in one step. see above.
//                removeResourceMetadata(transaction, doc);
                //Remove document nodes' index entries
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() {
                        try {                          
                            Value ref = new NodeRef(doc.getDocId());
                            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);                           
                            domDb.remove(transaction, query, null);
                        } catch (BTreeException e) {
                            LOG.warn("btree error while removing document", e);
                        } catch (IOException e) {
                            LOG.warn("io error while removing document", e);
                        } catch (TerminatedException e) {
                            LOG.warn("method terminated", e);
                        }
                        return null;
                    }
                }
                .run();  
                //Remove nodes themselves
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() {
                        if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                        	long page = ((BinaryDocument)doc).getPage();
                        	if (page > Page.NO_PAGE)
                        		domDb.removeOverflowValue(transaction, page);
                        } else {
                            StoredNode node = (StoredNode)doc.getFirstChild();
                            domDb.removeAll(transaction, node.getInternalAddress());
                        }
                        return null;
                    }
                }
                .run();
                //Make doc's id available again
                freeResourceId(transaction, doc.getDocId());
            }
            if (LOG.isDebugEnabled())
                LOG.debug("Removing collection '" + collName + "' took " + (System.currentTimeMillis() - start));
            return true;
        }
    }
    
    /**
     * Saves the specified collection to storage. Collections are usually cached in
     * memory. If a collection is modified, this method needs to be called to make
     * the changes persistent.
     * 
     * Note: appending a new document to a collection does not require a save.
     */
    public void saveCollection(Txn transaction, Collection collection) throws PermissionDeniedException {
        if (collection == null) {
            LOG.error("NativeBroker.saveCollection called with collection == null! Aborting.");
            return;
        }
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        if (!pool.isInitializing())
            // don't cache the collection during initialization: SecurityManager is not yet online
            pool.getCollectionsCache().add(collection);
        
        Lock lock = collectionsDb.getLock();
        try {           
            lock.acquire(Lock.WRITE_LOCK);

            if (collection.getId() == Collection.UNKNOWN_COLLECTION_ID)
                collection.setId(getNextCollectionId(transaction));

            Value name = new CollectionStore.CollectionKey(collection.getURI().toString());
            
            try {
                final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
                collection.write(this, ostream);
                final long addr = collectionsDb.put(transaction, name, ostream.data(), true);
                if (addr == BFile.UNKNOWN_ADDRESS) {
                    //TODO : exception !!! -pb
                    LOG.warn("could not store collection data for '" + collection.getURI()+ "'");
                    return;
                }
                collection.setAddress(addr);
                ostream.close();
            } catch (IOException ioe) {
                LOG.warn(ioe);
            }
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
        } finally {
            lock.release();
        }
    }
    
    /**
     * Release the collection id assigned to a collection so it can be
     * reused later.
     * 
     * @param id
     * @throws PermissionDeniedException
     */
    protected void freeCollectionId(Txn transaction, short id) throws PermissionDeniedException {       
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_COLLECTION_ID_KEY);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                byte[] ndata = new byte[data.length + Collection.LENGTH_COLLECTION_ID];
                System.arraycopy(data, 0, ndata, OFFSET_VALUE, data.length);
                ByteConversion.shortToByte(id, ndata, OFFSET_COLLECTION_ID);
                collectionsDb.put(transaction, key, ndata, true);
            } else {
                byte[] data = new byte[Collection.LENGTH_COLLECTION_ID];
                ByteConversion.shortToByte(id, data, OFFSET_COLLECTION_ID);
                collectionsDb.put(transaction, key, data, true);
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            //TODO : rethrow ? -pb
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
     * @return next free collection id.
     * @throws ReadOnlyException
     */
    public short getFreeCollectionId(Txn transaction) throws ReadOnlyException {
        short freeCollectionId = Collection.UNKNOWN_COLLECTION_ID;      
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_COLLECTION_ID_KEY);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                freeCollectionId = ByteConversion.byteToShort(data, data.length - Collection.LENGTH_COLLECTION_ID);
//              LOG.debug("reusing collection id: " + freeCollectionId);
                if(data.length - Collection.LENGTH_COLLECTION_ID > 0) {
                    byte[] ndata = new byte[data.length - Collection.LENGTH_COLLECTION_ID];
                    System.arraycopy(data, 0, ndata, OFFSET_COLLECTION_ID, ndata.length);
                    collectionsDb.put(transaction, key, ndata, true);
                } else
                    collectionsDb.remove(transaction, key);
            }
            return freeCollectionId;
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release();
        }
    }
    
    /**
     * Get the next available unique collection id.
     * 
     * @return next available unique collection id
     * @throws ReadOnlyException
     */
    public short getNextCollectionId(Txn transaction) throws ReadOnlyException {
        
        short nextCollectionId = getFreeCollectionId(transaction);
        
        if(nextCollectionId != Collection.UNKNOWN_COLLECTION_ID)
            return nextCollectionId;        
        
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_COLLECTION_ID_KEY);
            Value data = collectionsDb.get(key);
            if (data != null) {
                nextCollectionId = ByteConversion.byteToShort(data.getData(), OFFSET_COLLECTION_ID);
                ++nextCollectionId;
            }
            byte[] d = new byte[Collection.LENGTH_COLLECTION_ID];
            ByteConversion.shortToByte(nextCollectionId, d, OFFSET_COLLECTION_ID);
            collectionsDb.put(transaction, key, d, true);
            return nextCollectionId;
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release();
        }       
    }
    
    public void reindexCollection(XmldbURI collectionName) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        collectionName=prepend(collectionName.toCollectionPathURI());
        
        Collection collection = getCollection(collectionName);
        if (collection == null) {
            LOG.debug("collection " + collectionName + " not found!");
            return;
        }
        reindexCollection(collection, NodeProcessor.MODE_STORE);
    }    
    
    public void reindexCollection(Collection collection, int mode) throws PermissionDeniedException {
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            reindexCollection(transaction, collection, mode);
            transact.commit(transaction);
        } catch (TransactionException e) {
            transact.abort(transaction);
            LOG.warn("An error occurred during reindex: " + e.getMessage(), e);
        }
    }
    
    public void reindexCollection(Txn transaction, Collection collection, int mode) throws PermissionDeniedException {
        if (!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("insufficient privileges on collection " + collection.getURI());
        LOG.debug("Reindexing collection " + collection.getURI());
        
        if (mode == NodeProcessor.MODE_STORE)
            dropCollectionIndex(collection);
        for(Iterator i = collection.iterator(this); i.hasNext(); ) {
            DocumentImpl next = (DocumentImpl)i.next();
            reindexXMLResource(transaction, next, mode);
        }
        for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
        	XmldbURI next = (XmldbURI)i.next();
        	//TODO : resolve URIs !!! (collection.getURI().resolve(next))
            Collection child = getCollection(collection.getURI().append(next));
            if(child == null)
                LOG.warn("Collection '" + next + "' not found");
            else {
                reindexCollection(transaction, child, mode);
            }
        }
    }
    
    public void dropCollectionIndex(Collection collection) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        if (!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("insufficient privileges on collection " + 
                    collection.getURI());
        
        notifyDropIndex(collection);
        
        for (Iterator i = collection.iterator(this); i.hasNext();) {
            final DocumentImpl doc = (DocumentImpl) i.next();
            LOG.debug("Dropping index for document " + doc.getFileURI());
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
    
    /** store into the temporary collection of the database a given in-memory Document */
    public DocumentImpl storeTempResource(org.exist.memtree.DocumentImpl doc) throws EXistException, PermissionDeniedException, LockException
    {
        //store the currentUser
        User currentUser = user;
        
        //elevate user to DBA_USER
        user = pool.getSecurityManager().getUser(SecurityManager.DBA_USER);
    	
        //start a transaction
    	TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        
        //create a name for the temporary document
        XmldbURI docName = XmldbURI.create(MD5.md(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis()),false) + ".xml");
        
        //get the temp collection
        Collection temp = openCollection(XmldbURI.TEMP_COLLECTION_URI, Lock.WRITE_LOCK);
        
        try
        {
        	//if no temp collection
            if(temp == null)
            {
            	//creates temp collection with lock
                temp = createTempCollection(transaction);
            }
            else
            {
            	//lock the temp collection
                transaction.registerLock(temp.getLock(), Lock.WRITE_LOCK);
            }
            
            //create a temporary document
            DocumentImpl targetDoc = new DocumentImpl(this, temp, docName);
            targetDoc.setPermissions(0771);
            long now = System.currentTimeMillis();
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setLastModified(now);
            metadata.setCreated(now);
            targetDoc.setMetadata(metadata);
            targetDoc.setDocId(getNextResourceId(transaction, temp));

            //index the temporary document
            DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc);
            indexer.scan();
            indexer.store();

            //store the temporary document
            temp.addDocument(transaction, this, targetDoc);
            storeXMLResource(transaction, targetDoc);
            closeDocument();
            flush();
        
            //commit the transaction
            transact.commit(transaction);
            
            return targetDoc;
        }
        catch (Exception e)
        {
            LOG.warn("Failed to store temporary fragment: " + e.getMessage(), e);
         
            //abort the transaction
            transact.abort(transaction);
        }
        finally
        {
            //restore the user
            user = currentUser;
        }
        
        return null;
    }
    
    /** remove all documents from temporary collection */   
    public void cleanUpTempResources() {
        Collection temp = getCollection(XmldbURI.TEMP_COLLECTION_URI);
        if(temp == null)
            return;
        // remove the entire collection if all temp data has timed out
        boolean removeCollection = true;
        long now = System.currentTimeMillis();
        for(Iterator i = temp.iterator(this); i.hasNext(); ) {
            DocumentImpl next = (DocumentImpl) i.next();
            long modified = next.getMetadata().getLastModified();
            if(now - modified < TEMP_FRAGMENT_TIMEOUT) {
                removeCollection = false;
                break;
            }
        }
        
        if (removeCollection) {
            TransactionManager transact = pool.getTransactionManager();
            Txn txn = transact.beginTransaction();
            try {
                removeCollection(txn, temp);
                transact.commit(txn);
            } catch (TransactionException e) {
                transact.abort(txn);
                LOG.warn("Transaction aborted: " + e.getMessage(), e);
            } catch (PermissionDeniedException e) {
                transact.abort(txn);
                LOG.warn("Failed to remove temp collection: " + e.getMessage(), e);
            }
        }
    }
    
    /** remove from the temporary collection of the database a given list of Documents. */
    public void cleanUpTempResources(List docs) {
        Collection temp = openCollection(XmldbURI.TEMP_COLLECTION_URI, Lock.WRITE_LOCK);
        if(temp == null)
            return;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        txn.registerLock(temp.getLock(), Lock.WRITE_LOCK);
        try {
            for(Iterator i = docs.iterator(); i.hasNext(); )
                temp.removeXMLResource(txn, this, XmldbURI.create((String) i.next()));
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
    
    /** store Document entry into its collection. */
    public void storeXMLResource(final Txn transaction, final DocumentImpl doc) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
            doc.write(ostream);
            Value key = new CollectionStore.DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            collectionsDb.put(transaction, key, ostream.data(), true);
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while writing document data", e);
        } finally {
            lock.release();
        }
    }
    
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data) {
    	if (data.length == 0) {
    		blob.setPage(Page.NO_PAGE);
    		return;
    	}
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                LOG.debug("Storing binary resource " + blob.getFileURI());
                blob.setPage(domDb.addBinary(transaction, blob, data));
                return null;
            }
        }
        .run();
    }    
    
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final InputStream is) {
    	if (is == null) {
    		blob.setPage(Page.NO_PAGE);
    		return;
    	}
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                LOG.debug("Storing binary resource as a stream " + blob.getFileURI());
                blob.setPage(domDb.addBinary(transaction, blob, is));
                return null;
            }
        }
        .run();
    }  
    
    
    /**
     *  get a document by its file name. The document's file name is used to
     *  identify a document.
     *
     *@param  fileName absolute file name in the database; 
     *name can be given with or without the leading path /db/shakespeare.
     *@return  The document value
     *@exception  PermissionDeniedException 
     */
    public Document getXMLResource(XmldbURI fileName) throws PermissionDeniedException {
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !!!
        XmldbURI collUri = fileName.removeLastSegment();
        XmldbURI docUri = fileName.lastSegment();
         
        Collection collection = getCollection(collUri);
        if (collection == null) {
            LOG.debug("collection '" + collUri + "' not found!");
            return null;
        }
        if (!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "'");
        
        DocumentImpl doc = collection.getDocument(this, docUri);
        if (doc == null) {
            LOG.debug("document '" + fileName + "' not found!");
            return null;
        }
        
//      if (!doc.getPermissions().validate(user, Permission.READ))
//          throw new PermissionDeniedException("not allowed to read document");
        
        return doc;
    }
    
    public DocumentImpl getXMLResource(XmldbURI fileName, int lockMode) throws PermissionDeniedException {
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !
        XmldbURI collUri = fileName.removeLastSegment();
        XmldbURI docUri = fileName.lastSegment();
        
        Collection collection = openCollection(collUri, lockMode);
        if (collection == null) {
            LOG.debug("collection '" + collUri + "' not found!");
            return null;
        }
        if (!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "'");
        
        try {
            DocumentImpl doc = collection.getDocumentWithLock(this, docUri, lockMode);
            if (doc == null) {
                LOG.debug("document '" + fileName + "' not found!");
                return null;
            }
            
    //      if (!doc.getPermissions().validate(user, Permission.READ))
    //          throw new PermissionDeniedException("not allowed to read document");
            
            return doc;
        } catch (LockException e) {
            LOG.warn("Could not acquire lock on document " + fileName, e);
            //TODO : exception ? -pb
        } finally {
            //TOUNDERSTAND : by whom is this lock acquired ? -pb
            if(collection != null)
                collection.release();            
        }
        return null;
    }    
    
    public byte[] getBinaryResource(final BinaryDocument blob) {
    	if (blob.getPage() == Page.NO_PAGE)
    		return new byte[0];
        byte[] data = (byte[]) new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                return domDb.getBinary(blob.getPage());
            }
        }
        .run();
        return data;
    }
    
    public void readBinaryResource(final BinaryDocument blob, final OutputStream os) {
    	if (blob.getPage() == Page.NO_PAGE)
    		return;
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                domDb.readBinary(blob.getPage(), os);
                return null;
            }
        }.run();
    }
    
    //TODO : consider a better cooperation with Collection -pb
    public void getCollectionResources(Collection collection) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();            
            Value key = new CollectionStore.DocumentKey(collection.getId());
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, new DocumentCallback(collection));
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } catch (BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } catch (TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release();
        }
    }
    
    /**
     *  Get all the documents in this database matching the given
     *  document-type's name.
     * @return The documentsByDoctype value
     */
    public DocumentSet getXMLResourcesByDoctype(String doctypeName, DocumentSet result) {
        DocumentSet docs = getAllXMLResources(new DocumentSet());
        for (Iterator i = docs.iterator(); i.hasNext();) {
        	DocumentImpl doc = (DocumentImpl) i.next();
        	DocumentType doctype = doc.getDoctype();
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
     *  Adds all the documents in the database to the specified DocumentSet.
     *
     * @param docs a (possibly empty) document set to which the found
     *  documents are added.
     */
    public DocumentSet getAllXMLResources(DocumentSet docs) {
        long start = System.currentTimeMillis();
        Collection rootCollection = null;
        try {
        	rootCollection = openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.READ_LOCK);
        	rootCollection.allDocs(this, docs, true, false);
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
            if (rootCollection != null)
            	rootCollection.release();
        }
    }    
    
    //TODO : consider a better cooperation with Collection -pb
    public void getResourceMetadata(DocumentImpl document) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            VariableByteInput istream = collectionsDb.getAsStream(key);
            document.readDocumentMeta(istream);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } finally {
            lock.release();
        }
    }

    public void copyXMLResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName) 
    throws PermissionDeniedException, LockException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        Collection collection = doc.getCollection();
        if (!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("Insufficient privileges to copy resource " +
                    doc.getFileURI());
        if (!doc.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("Insufficient privileges to copy resource " +
                    doc.getFileURI());

        if(newName==null) {
        	newName = doc.getFileURI();
        }
        Lock lock = collectionsDb.getLock();
        try {
            
            lock.acquire(Lock.WRITE_LOCK);
            // check if the move would overwrite a collection
            if(getCollection(destination.getURI().append(newName)) != null)
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
                    destination.removeXMLResource(transaction, this, oldDoc.getFileURI());
            } else {
                if(!destination.getPermissions().validate(user, Permission.WRITE))
                    throw new PermissionDeniedException("Insufficient privileges on target collection " +
                            destination.getURI());
            }
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE)  {
                byte[] data = getBinaryResource((BinaryDocument) doc); 
                destination.addBinaryResource(transaction, this, newName, data, doc.getMetadata().getMimeType());
            } else {
                DocumentImpl newDoc = new DocumentImpl(this, destination, newName);
                newDoc.copyOf(doc);
                newDoc.setDocId(getNextResourceId(transaction, destination));
                newDoc.setPermissions(doc.getPermissions()); 
                copyXMLResource(transaction, doc, newDoc);
                destination.addDocument(transaction, this, newDoc);
                storeXMLResource(transaction, newDoc);
            }
//          saveCollection(destination);
        } catch (EXistException e) {
            LOG.warn("An error occurred while copying resource", e);
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } finally {
            lock.release();
        }
    }
    
    private void copyXMLResource(Txn transaction, DocumentImpl oldDoc, DocumentImpl newDoc) {
        LOG.debug("Copying document " + oldDoc.getFileURI() + " to " + 
                newDoc.getURI());
        final long start = System.currentTimeMillis();
        NodeList nodes = oldDoc.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
        	StoredNode node = (StoredNode) nodes.item(i);
        	Iterator iterator = getNodeIterator(node);
            iterator.next();
            copyNodes(transaction, iterator, node, new NodePath(), newDoc, false, true);
        }
        flush();
        closeDocument();
        LOG.debug("Copy took " + (System.currentTimeMillis() - start) + "ms.");
    }
    
    /** move Resource to another collection, with possible rename */
    public void moveXMLResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName)
    throws PermissionDeniedException, LockException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        //TODO : somewhat inconsistent (READ is enough for original doc whereas WRITE is mandatory for destination) -pb
        Collection collection = doc.getCollection();                
        if(!collection.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move resource " +
                    doc.getFileURI());
        if(!doc.getPermissions().validate(user, Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move resource " +
                    doc.getFileURI());
      
        User docUser = doc.getUserLock();
        if (docUser != null) {
           if(!(user.getName()).equals(docUser.getName()))
                throw new PermissionDeniedException("Cannot move '" + doc.getFileURI() + 
                        " because is locked by user '" + docUser.getName() + "'");
        }
        
        if ( newName==null) {
        	newName = doc.getFileURI();
        }
        try {
            // check if the move would overwrite a collection
        	//TODO : resolve URIs : destination.getURI().resolve(newName)
            if(getCollection(destination.getURI().append(newName)) != null)
                throw new PermissionDeniedException("A resource can not replace an existing collection");
            DocumentImpl oldDoc = destination.getDocument(this, newName);
            if(oldDoc != null) {
                if (doc.getDocId() == oldDoc.getDocId())
                    throw new PermissionDeniedException("Cannot move resource to itself");
                if (!destination.getPermissions().validate(user, Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
                            "collection and update is denied");
                if (!oldDoc.getPermissions().validate(user, Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
                            "collection and update is denied");
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
                    destination.removeBinaryResource(transaction, this, oldDoc);
                else
                    destination.removeXMLResource(transaction, this, oldDoc.getFileURI());
            } else
                if (!destination.getPermissions().validate(user, Permission.WRITE))
                    throw new PermissionDeniedException("Insufficient privileges on target collection " +
                            destination.getURI());
                
            boolean renameOnly = collection.getId() == destination.getId();
            collection.unlinkDocument(doc);
            removeResourceMetadata(transaction, doc);
            
            doc.setFileURI(newName);
            doc.setCollection(destination);
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {
                if (!renameOnly) {
                    dropIndex(transaction, doc);
                    saveCollection(transaction, collection);
                }
                destination.addDocument(transaction, this, doc);

                if (!renameOnly) {
                    // reindexing
                    reindexXMLResource(transaction, doc, NodeProcessor.MODE_REPAIR);
                }
            } else {
                // binary resource
                destination.addDocument(transaction, this, doc);
            }
            storeXMLResource(transaction, doc);
            saveCollection(transaction, destination);
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(e.getMessage());
        }
    }
    
    public void removeXMLResource(final Txn transaction, final DocumentImpl document, 
            boolean freeDocId) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        try {
            if (LOG.isInfoEnabled()) {
                LOG.info("Removing document "
                    + document.getFileURI() + " (" + document.getDocId() + ") ...");
            }

            dropIndex(transaction, document);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
            new DOMTransaction(this, domDb) {
                public Object start() {
                    StoredNode node = (StoredNode)document.getFirstChild();
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
                    } catch (IOException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
                    } catch (TerminatedException e) {
                        LOG.warn("method terminated", e);
                    }
                    return null;
                }
            }
            .run();
            
            removeResourceMetadata(transaction, document);
            
            if(freeDocId)
                freeResourceId(transaction, document.getDocId());
        } catch (ReadOnlyException e) {
            LOG.warn("removeDocument(String) - " + DATABASE_IS_READ_ONLY);
        }
    }

    private void dropIndex(Txn transaction, DocumentImpl document) throws ReadOnlyException {
        NodeList nodes = document.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            StoredNode node = (StoredNode) nodes.item(i);
            Iterator iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), NodeProcessor.MODE_REMOVE);
        }
        notifyDropIndex(document);
    }

    public void removeBinaryResource(final Txn transaction, final BinaryDocument blob)
    throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        LOG.info("removing binary resource " + blob.getDocId() + "...");
        if (blob.getPage() != Page.NO_PAGE) {
	        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
	            public Object start() throws ReadOnlyException {
	                domDb.removeOverflowValue(transaction, blob.getPage());
	                return null;
	            }
	        }
	        .run();
        }
        removeResourceMetadata(transaction, blob);
    }

    /**
     * @param transaction
     * @param document
     */
    private void removeResourceMetadata(final Txn transaction, final DocumentImpl document) {
        // remove document metadata
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire();
            if (LOG.isDebugEnabled())
                LOG.debug("Removing resource metadata for " + document.getDocId());
            Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } finally {
            lock.release();
        }
    }

	/**
	 * Release the document id reserved for a document so it
	 * can be reused.
	 * 
	 * @param id
	 * @throws PermissionDeniedException
	 */
	protected void freeResourceId(Txn transaction, int id) throws PermissionDeniedException {		
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_DOC_ID_KEY);
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
			LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            //TODO : rethrow ? -pb
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
	 * @return Next unused document id
	 * @throws ReadOnlyException
	 */
	public int getFreeResourceId(Txn transaction) throws ReadOnlyException {
		int freeDocId = DocumentImpl.UNKNOWN_DOCUMENT_ID;		
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_DOC_ID_KEY);
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
            //TODO : maybe something ? -pb
		} catch (LockException e) {
			LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
			return DocumentImpl.UNKNOWN_DOCUMENT_ID;
            //TODO : rethrow ? -pb
		} finally {
			lock.release();
		}
		return freeDocId;
	}
	
    /** get next Free Doc Id */
	public int getNextResourceId(Txn transaction, Collection collection) {
		int nextDocId;
		try {
			nextDocId = getFreeResourceId(transaction);
		} catch (ReadOnlyException e) {
            //TODO : rethrow ? -pb
			return 1;
		}
		if (nextDocId != DocumentImpl.UNKNOWN_DOCUMENT_ID)
			return nextDocId;
		else
			nextDocId = 1;
		
		Lock lock = collectionsDb.getLock();
		try {
			lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_DOC_ID_KEY);
            Value data = collectionsDb.get(key);
			if (data != null) {
				nextDocId = ByteConversion.byteToInt(data.getData(), 0);
				++nextDocId;
			}
			byte[] d = new byte[4];
			ByteConversion.intToByte(nextDocId, d, 0);
			collectionsDb.put(transaction, key, d, true);
		} catch (ReadOnlyException e) {
			LOG.warn("Database is read-only");
			return DocumentImpl.UNKNOWN_DOCUMENT_ID;
            //TODO : rethrow ? -pb
		} catch (LockException e) {
			LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            //TODO : rethrow ? -pb
		} finally {
			lock.release();
		}
		return nextDocId;
	}
    
    /**
     * Reindex the nodes in the document. This method will either reindex all
     * descendant nodes of the passed node, or all nodes below some level of
     * the document if node is null.
     */
    private void reindexXMLResource(Txn transaction, DocumentImpl doc, int mode) {
        if(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE.equals(doc.getFileURI()))
            doc.getCollection().setConfigEnabled(false);
        NodeList nodes = doc.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
        	StoredNode node = (StoredNode) nodes.item(i);
        	Iterator iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), mode);
        }
        flush();
        if(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE.equals(doc.getFileURI()))
            doc.getCollection().setConfigEnabled(true);
    }
    
    public void defragXMLResource(final Txn transaction, final DocumentImpl doc) {
        //TODO : use dedicated function in XmldbURI
        LOG.debug("============> Defragmenting document " + 
                doc.getCollection().getURI() + "/" + doc.getFileURI());
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
//          checkTree(doc);
//            try {
//                domDb.printFreeSpaceList();
//            } catch (IOException e1) {
//                // Auto-generated catch block
//                e1.printStackTrace();
//            }
            // remember this for later remove
            final long firstChild = doc.getFirstChildAddress();
                
            // dropping old structure index
            dropIndex(transaction, doc);
            
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
            DocumentImpl tempDoc = new DocumentImpl(this, doc.getCollection(), doc.getFileURI());
            tempDoc.copyOf(doc);
            tempDoc.setDocId(doc.getDocId());
            
            // copy the nodes
            NodeList nodes = doc.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
            	StoredNode node = (StoredNode) nodes.item(i);
            	Iterator iterator = getNodeIterator(node);
                iterator.next();                
                copyNodes(transaction, iterator, node, new NodePath(), tempDoc, true, true);
            }
            flush();
            // checkTree(tempDoc);
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
            
            // checkTree(tempDoc);            
            doc.copyChildren(tempDoc);
            doc.getMetadata().setSplitCount(0);
            doc.getMetadata().setPageCount(tempDoc.getMetadata().getPageCount());
            
            storeXMLResource(transaction, doc);
            //Commented out since DocmentImpl has no more internal address
            //LOG.debug("new doc address = " + StorageAddress.toString(doc.getInternalAddress()));
            closeDocument();
//          new DOMTransaction(this, domDb, Lock.READ_LOCK) {
//              public Object start() throws ReadOnlyException {
//                  LOG.debug("Pages used: " + domDb.debugPages(doc));
//                  return null;
//              }
//          }.run();
//            storeXMLResource(transaction, doc);
//            checkTree(doc);
            LOG.debug("Defragmentation took " + (System.currentTimeMillis() - start) + "ms.");
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY, e);
        }
    }        
    
    /** consistency Check of the database; useful after XUpdates;
     * called if xupdate.consistency-checks is true in configuration */ 
    public void checkXMLResourceConsistency(DocumentImpl doc) throws EXistException {
        boolean xupdateConsistencyChecks = false;
        if (customProperties.get(PROPERTY_XUPDATE_CONSISTENCY_CHECKS) != null)
        	xupdateConsistencyChecks = ((Boolean)customProperties.get(PROPERTY_XUPDATE_CONSISTENCY_CHECKS)).booleanValue();
        if(xupdateConsistencyChecks) {
            LOG.debug("Checking document " + doc.getFileURI());
            checkXMLResourceTree(doc);
//          elementIndex.consistencyCheck(doc);
        }
    }
    
    /** consistency Check of the database; useful after XUpdates;
     * called by {@link #checkXMLResourceConsistency(DocumentImpl)} */
    public void checkXMLResourceTree(final DocumentImpl doc) {
        LOG.debug("Checking DOM tree for document " + doc.getFileURI());
        boolean xupdateConsistencyChecks = false;
        if (customProperties.get(PROPERTY_XUPDATE_CONSISTENCY_CHECKS) != null)
        	xupdateConsistencyChecks = ((Boolean)customProperties.get(PROPERTY_XUPDATE_CONSISTENCY_CHECKS)).booleanValue();
        if(xupdateConsistencyChecks) {
            new DOMTransaction(this, domDb, Lock.READ_LOCK) {
                public Object start() throws ReadOnlyException {
                    LOG.debug("Pages used: " + domDb.debugPages(doc, false));
                    return null;
                }
            }.run();            
            NodeList nodes = doc.getChildNodes();            
            for (int i = 0; i < nodes.getLength(); i++) {
            	StoredNode node = (StoredNode) nodes.item(i);
                Iterator iterator = getNodeIterator(node);
                iterator.next();
                checkNodeTree(iterator, node);
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
    public void storeNode(final Txn transaction, final StoredNode node, NodePath currentPath, boolean fullTextIndex) {
        checkAvailableMemory();
        final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        final short nodeType = node.getNodeType();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            public Object start() throws ReadOnlyException {
                long address = BFile.UNKNOWN_ADDRESS;
                final byte data[] = node.serialize();
                int depth = doc.getCollection().getIndexDepth(NativeBroker.this);
                if (depth == -1) 
                	depth = defaultIndexDepth;
                if (nodeType == Node.TEXT_NODE
                    || nodeType == Node.ATTRIBUTE_NODE
                    || nodeType == Node.CDATA_SECTION_NODE
                    || node.getNodeId().getTreeLevel() > depth + 1)
                    address = domDb.add(transaction, data);
                else {
                    address = domDb.put(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                }
                if (address == BFile.UNKNOWN_ADDRESS)
                    LOG.warn("address is missing");
                //TODO : how can we continue here ? -pb
                node.setInternalAddress(address);
                ByteArrayPool.releaseByteArray(data);
                return null;
            }
        }
        .run();
        ++nodesCount;

        nodeProcessor.reset(transaction, node, currentPath, fullTextIndex);
        nodeProcessor.doIndex();
    }
    
    public void updateNode(final Txn transaction, final StoredNode node) {
        try {
            final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
            final long internalAddress = node.getInternalAddress();
            final byte[] data = node.serialize();
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                public Object start() throws ReadOnlyException {
                    if (internalAddress != BFile.UNKNOWN_ADDRESS)
                        domDb.update(transaction, internalAddress, data);
                    else {
                        domDb.update(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                    }
                    return null;
                }
            }
            .run();
            ByteArrayPool.releaseByteArray(data);
        } catch (Exception e) {
            Value oldVal = domDb.get(node.getInternalAddress());
            StoredNode old = 
                StoredNode.deserialize(oldVal.data(), oldVal.start(), oldVal.getLength(), 
                		(DocumentImpl)node.getOwnerDocument(), false);
            LOG.warn(
                "Exception while storing "
                    + node.getNodeName()
                    + "; gid = "
                    + node.getNodeId()
                    + "; old = " + old.getNodeName(),
                e);
        }
    }

    /**
     * Physically insert a node into the DOM storage.
     */
    public void insertNodeAfter(final Txn transaction, final StoredNode previous, final StoredNode node) {
        final byte data[] = node.serialize();
        final DocumentImpl doc = (DocumentImpl)previous.getOwnerDocument();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            public Object start() {
                long address = previous.getInternalAddress();
                if (address != BFile.UNKNOWN_ADDRESS) {
                    address = domDb.insertAfter(transaction, doc, address, data);
                } else {
                    NodeRef ref = new NodeRef(doc.getDocId(), previous.getNodeId());
                    address = domDb.insertAfter(transaction, doc, ref, data);
                }
                node.setInternalAddress(address);
                return null;
            }
        }
        .run();
    }

    private void copyNodes(Txn transaction, Iterator iterator, StoredNode node, NodePath currentPath,
            DocumentImpl newDoc, boolean defrag, boolean index) {
        copyNodes(transaction, iterator, node, currentPath, newDoc, defrag, index, null);
    }

    private void copyNodes(Txn transaction, Iterator iterator, StoredNode node, NodePath currentPath,
            DocumentImpl newDoc, boolean defrag, boolean index, NodeId oldNodeId) {
        if (node.getNodeType() == Node.ELEMENT_NODE)
            currentPath.addComponent(node.getQName());
        final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        final long oldAddress = node.getInternalAddress();
        node.setOwnerDocument(newDoc);
        node.setInternalAddress(BFile.UNKNOWN_ADDRESS);
        storeNode(transaction, node, currentPath, index);
        if (defrag && oldNodeId != null)
            pool.getNotificationService().notifyMove(oldNodeId, node);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
        	//save old value, whatever it is
        	long address = node.getOldInternalAddress();
        	node.setOldInternalAddress(oldAddress);
            endElement(node, currentPath, null);
            //restore old value, whatever it was
            node.setOldInternalAddress(address);
        }
        if (node.getNodeId().getTreeLevel() == 1)
            newDoc.appendChild(node);
        node.setOwnerDocument(doc);
        
        if (node.hasChildNodes()) {
            int count = node.getChildCount();
            NodeId nodeId = node.getNodeId();
            for (int i = 0; i < count; i++) {
            	StoredNode child = (StoredNode) iterator.next();
                oldNodeId = child.getNodeId();
                if (defrag) {
	                if (i == 0)
	            		nodeId = nodeId.newChild();
	            	else
	            		nodeId = nodeId.nextSibling();
                    child.setNodeId(nodeId);
                }
                copyNodes(transaction, iterator, child, currentPath, newDoc, defrag, index, oldNodeId);
            }
        }
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.removeLastComponent();
        }
    }
    
    /** Removes the Node Reference from the database.
     * The index will be updated later, i.e. after all nodes have been physically 
     * removed. See {@link #endRemove()}. 
     * removeNode() just adds the node ids to the list in elementIndex 
     * for later removal.
     */
    public void removeNode(final Txn transaction, final StoredNode node, NodePath currentPath, String content) {
        final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        final FulltextIndexSpec ftIdx = doc.getCollection().getFulltextIndexConfiguration(this);

        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            public Object start() {
                final long address = node.getInternalAddress();
                if (address != BFile.UNKNOWN_ADDRESS)
                    domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), address);
                else
                    domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getNodeId()));
                return null;
            }
        }
        .run();
        
        NodeProxy p = new NodeProxy(node);

        QName qname;
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE :
                // save element by calling ElementIndex
                qname = node.getQName();
                qname.setNameType(ElementValue.ELEMENT);
                elementIndex.setDocument(doc);
                elementIndex.addNode(qname, p);
                

                GeneralRangeIndexSpec spec1 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec1 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, spec1.getType());
                }
                // qnameValueIndex.removeElement((ElementImpl) node, currentPath, content);
                notifyRemoveElement( (ElementImpl) node, currentPath, content );
                break;
                
            case Node.ATTRIBUTE_NODE :
                qname = node.getQName();
                qname.setNameType(ElementValue.ATTRIBUTE);
                currentPath.addComponent(qname);
                
                elementIndex.setDocument(doc);
                elementIndex.addNode(qname, p);
                
                //Strange : does it mean that the node is added 2 times under 2 different identities ?

                // if the attribute has type ID, store the ID-value
                // to the element index as well
                if (((AttrImpl) node).getType() == AttrImpl.ID) {
                    qname = new QName(((AttrImpl) node).getValue(), "", null);
                    qname.setNameType(ElementValue.ATTRIBUTE_ID);
                    elementIndex.addNode(qname, p);
                }                

                GeneralRangeIndexSpec spec2 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec2 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute((AttrImpl) node, null, NativeValueIndex.WITHOUT_PATH, spec2);
                }                   
                
                qnameValueIndex.storeAttribute(null, (AttrImpl)node, currentPath, true);
                
                // check if attribute value should be fulltext-indexed
                // by calling IndexPaths.match(path) 
                if(ftIdx != null && ftIdx.matchAttribute(currentPath)) {
                	textEngine.setDocument(doc);
                	textEngine.storeAttribute((AttrImpl) node, null, NativeTextEngine.ATTRIBUTE_NOT_BY_QNAME, ftIdx);
                }
                
                currentPath.removeLastComponent();
                break;
            case Node.TEXT_NODE :
                // check if this textual content should be fulltext-indexed
                // by calling IndexPaths.match(path)
                if (ftIdx == null) {
                	textEngine.setDocument(doc);
                	textEngine.storeText((TextImpl) node, NativeTextEngine.TOKENIZE, ftIdx);
                } else if (ftIdx.match(currentPath)) {
                	textEngine.setDocument(doc);
                	textEngine.storeText((TextImpl) node, NativeTextEngine.DO_NOT_TOKENIZE, ftIdx);
                }
                break;
        }
    }

    public void removeAllNodes(Txn transaction, StoredNode node, NodePath currentPath) {
        Iterator iterator = getNodeIterator(node);
        iterator.next();
        Stack stack = new Stack();
        collectNodesForRemoval(stack, iterator, node, currentPath);
        while (!stack.isEmpty()) {
        	RemovedNode next = (RemovedNode) stack.pop();
            removeNode(transaction, next.node, next.path, next.content);
        }
    }
    
    private void collectNodesForRemoval(Stack stack, Iterator iterator, StoredNode node, NodePath currentPath) {
        RemovedNode removed;
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
                String content = null;
                GeneralRangeIndexSpec spec = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if (spec != null) {
                    content = getNodeValue(node, false);
                } else {
                	QNameRangeIndexSpec qnIdx = doc.getCollection().getIndexByQNameConfiguration(this, node.getQName());
                    if (qnIdx != null) {
                        content = getNodeValue(node, false);
                    }
                }
                removed = new RemovedNode(node, new NodePath(currentPath), content);
                stack.push(removed);

                if (node.hasChildNodes()) {
                    int childCount = node.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                    	StoredNode child = (StoredNode) iterator.next();
                        if (child.getNodeType() == Node.ELEMENT_NODE)
                            currentPath.addComponent(((ElementImpl) child).getQName());
                        collectNodesForRemoval(stack, iterator, child, currentPath);
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
    
    /**
     * Index a single node, which has been added through an XUpdate
     * operation. This method is only called if inserting the node is possible
     * without changing the node identifiers of sibling or parent nodes. In other 
     * cases, reindex will be called.
     */
    public void indexNode(Txn transaction, StoredNode node, NodePath currentPath) {
        indexNode(transaction, node, currentPath, NodeProcessor.MODE_STORE);
    }
    
    public void indexNode(final Txn transaction, final StoredNode node, NodePath currentPath, int repairMode) {
        elementIndex.setInUpdateMode(true);
        nodeProcessor.reset(transaction, node, currentPath);
        nodeProcessor.setMode(repairMode);
        nodeProcessor.index();
    }
    
    private void checkNodeTree(Iterator iterator, StoredNode node) {
        if (node.hasChildNodes()) {
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
            	StoredNode child = (StoredNode) iterator.next();
                if(child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
                            "; children = " + node.getChildCount());
                    throw new IllegalStateException("Wrong node id");
                }
                checkNodeTree(iterator, child);
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
    private void scanNodes(Txn transaction, Iterator iterator, StoredNode node, NodePath currentPath,
            int mode) {
        if (node.getNodeType() == Node.ELEMENT_NODE)
            currentPath.addComponent(node.getQName());
        indexNode(transaction, node, currentPath, mode);
        if (node.hasChildNodes()) {
            final int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
            	StoredNode child = (StoredNode) iterator.next();
                if (child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
                            "; children = " + node.getChildCount());
                    throw new IllegalStateException("Wrong node id");
                }
                scanNodes(transaction, iterator, child, currentPath, mode);
            }
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            endElement(node, currentPath, null);
            currentPath.removeLastComponent();
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
    protected NodeSet scanNodesSequential(NodeSet context, DocumentSet doc, 
            int relation, int truncation, String expr, Collator collator) {
        ExtArrayNodeSet resultNodeSet = new ExtArrayNodeSet();        
        Pattern regexp = null;        
        if (relation == Constants.REGEXP) {
                regexp = Pattern.compile(expr.toLowerCase(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                truncation = Constants.REGEXP;
        }
        for (Iterator i = context.iterator(); i.hasNext();) {
            NodeProxy p = (NodeProxy) i.next();
            String content;
            try {
                domDb.getLock().acquire(Lock.READ_LOCK);
                domDb.setOwnerObject(this);                
                content = domDb.getNodeValue(new StoredNode(p), false);
            } catch (LockException e) {
            	LOG.warn("Failed to acquire read lock on " + domDb.getFile().getName());
                continue;
            } finally {
                domDb.getLock().release();
            }
            String cmp;
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
                    if (Collations.indexOf(collator, cmp, expr) != Constants.STRING_NOT_FOUND)
                        resultNodeSet.add(p);
                    break;
                case Constants.TRUNC_NONE :             
                    int result = Collations.compare(collator, cmp, expr);
                    switch (relation) {
                        case Constants.LT :
                            if (result < 0)
                                resultNodeSet.add(p);
                            break;
                        case Constants.LTEQ :
                            if (result <= 0)
                                resultNodeSet.add(p);
                            break;
                        case Constants.GT :
                            if (result > 0)
                                resultNodeSet.add(p);
                            break;
                        case Constants.GTEQ :
                            if (result >= 0)
                                resultNodeSet.add(p);
                            break;
                        case Constants.EQ :
                            if (result == Constants.EQUAL)
                                resultNodeSet.add(p);
                            break;
                        case Constants.NEQ :
                            if (result != Constants.EQUAL)
                                resultNodeSet.add(p);
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument 'relation': " + relation);
                    }
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
	
	public String getNodeValue(final StoredNode node, final boolean addWhitespace) {
		return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
			public Object start() {
				return domDb.getNodeValue(node, addWhitespace);
			}
		}
		.run();
	}

	public NodeSet getNodesEqualTo(NodeSet context, DocumentSet docs, 
            int relation, int truncation, String expr, Collator collator) {
		if (!isCaseSensitive())
			expr = expr.toLowerCase();
		return scanNodesSequential(context, docs, relation, truncation, expr, collator);		
	}
    
    public StoredNode objectWith(final Document doc, final NodeId nodeId) {    
		return (StoredNode) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(new NodeProxy((DocumentImpl) doc, nodeId));
				if (val == null) {
                    LOG.warn("Node " + nodeId + " not found");
					return null;
                }
				StoredNode node = StoredNode.deserialize(val.getData(),	0, val.getLength(),	(DocumentImpl) doc);
				node.setOwnerDocument((DocumentImpl)doc);
				node.setInternalAddress(val.getAddress());
				return node;
			}
		}
		.run();
	}

	public StoredNode objectWith(final NodeProxy p) {       
		if (p.getInternalAddress() == StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
			return (StoredNode) objectWith(p.getDocument(), p.getNodeId());
		return (StoredNode) new DOMTransaction(this, domDb) {
			public Object start() {
				Value val = domDb.get(p.getInternalAddress());
				if (val == null) {
					LOG.debug("Node " + p.getNodeId() + " not found in document " + p.getDocument().getURI() +
							"; docId = " + p.getDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()));
//					LOG.debug(domDb.debugPages(p.doc, true));
//					return null;
					return objectWith(p.getDocument(), p.getNodeId()); // retry?
				}
				StoredNode node = StoredNode.deserialize(val.getData(), 0, val.getLength(), p.getDocument());
				node.setOwnerDocument((DocumentImpl)p.getOwnerDocument());
				node.setInternalAddress(p.getInternalAddress());
				return node;
			}
		}
		.run();
	}

    public void storeNode(Txn transaction, StoredNode node, NodePath currentPath) {
        super.storeNode(transaction, node, currentPath);
    }

    public void repair() throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        LOG.info("Removing index files ..."); 
        notifyCloseAndRemove();  

        LOG.info("Recreating index files ...");
        try {
        	elementIndex = new NativeElementIndex(this, ELEMENTS_DBX_ID, dataDir, config);        	
        	valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
    		qnameValueIndex = new NativeValueIndexByQName(this, VALUES_QNAME_DBX_ID, dataDir, config);    		
    		textEngine = new NativeTextEngine(this, WORDS_DBX_ID, dataDir, config);    		
        } catch (DBException e) {
            LOG.warn("Exception during repair: " + e.getMessage(), e);
        }
        
        LOG.info("Reindexing database files ...");
        //Reindex from root collection
        reindexCollection(null, getCollection(XmldbURI.ROOT_COLLECTION_URI), NodeProcessor.MODE_REPAIR);
    }
    
    public void flush() {
        notifyFlush();
        try {
        	symbols.flush();
        } catch (EXistException e) {
            LOG.warn(e);
        }	
        nodesCount = 0;
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
                    LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
                } finally {
                    lock.release();
                }
                notifySync();
//              System.gc();
                NumberFormat nf = NumberFormat.getNumberInstance();
                LOG.info("Memory: " + nf.format(run.totalMemory() / 1024) + "K total; " +
                        nf.format(run.maxMemory() / 1024) + "K max; " +
                        nf.format(run.freeMemory() / 1024) + "K free");              
                
                domDb.printStatistics();
                collectionsDb.printStatistics();
                notifyPrintStatistics();
            }
        } catch (DBException dbe) {
            dbe.printStackTrace();
            LOG.warn(dbe);
        }
    }

	public void shutdown() {		
		try {
			flush();
			sync(Sync.MAJOR_SYNC);
            domDb.close();
            collectionsDb.close();
            notifyClose();
		} catch (Exception e) {
			LOG.warn(e);
		}
        super.shutdown();
	}    

    /** check available memory */
    private void checkAvailableMemory() {
        if (nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
            final double percent = ((double) run.freeMemory() / (double) run.maxMemory()) * 100;
            if (percent < memMinFree) {
                flush();
                System.gc();
                NumberFormat nf = NumberFormat.getNumberInstance();
                LOG.info("total memory: " + nf.format(run.totalMemory()) + 
                		"; free: " + nf.format(run.freeMemory()));
            }
        }
    }

	//TOUNDERSTAND : why not use shutdown ? -pb
	public void closeDocument() {
		new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() {
				domDb.closeDocument();
				return null;
			}
		}
		.run();
	}

	public final static class NodeRef extends Value {
		
		public static int OFFSET_DOCUMENT_ID = 0;
		public static int OFFSET_NODE_ID = OFFSET_DOCUMENT_ID + DocumentImpl.LENGTH_DOCUMENT_ID;
		
		public NodeRef(int docId) {
			len = DocumentImpl.LENGTH_DOCUMENT_ID;
            data = new byte[len];
            ByteConversion.intToByte(docId, data, OFFSET_DOCUMENT_ID);            
            pos = OFFSET_DOCUMENT_ID;
        }
        
        public NodeRef(int docId, NodeId nodeId) {
        	len = DocumentImpl.LENGTH_DOCUMENT_ID + nodeId.size();
			data = new byte[len];
			ByteConversion.intToByte(docId, data, OFFSET_DOCUMENT_ID);
			nodeId.serialize(data, OFFSET_NODE_ID);
            pos = OFFSET_DOCUMENT_ID;
		}

		int getDocId() {
			return ByteConversion.byteToInt(data, OFFSET_DOCUMENT_ID);
		}
	}
    
    private final static class RemovedNode {
        StoredNode node;
        String content;
        NodePath path;
        
        RemovedNode(StoredNode node, NodePath path, String content) {
            this.node = node;
            this.path = path;
            this.content = content;
        }
    }    
    
    /** Delegate for Node Processings : indexing */
    private class NodeProcessor {

        final static int MODE_STORE = 0;
        final static int MODE_REPAIR = 1;
        final static int MODE_REMOVE = 2;

        private Txn transaction;
        private StoredNode node;
        private NodePath currentPath;

        /** work variables */
        private DocumentImpl doc;
        private long address;

        private IndexSpec idxSpec;
        private FulltextIndexSpec ftIdx;
        private int level;
        private int mode = MODE_STORE;

        /** overall switch to activate fulltext indexation */
        private boolean fullTextIndex = true;

        NodeProcessor() {
        }

        public void reset(Txn transaction, StoredNode node, NodePath currentPath) {            
            if (node.getNodeId() == null)
                LOG.warn("illegal node: " + node.getNodeName());
                //TODO : why continue processing ? return ? -pb
            this.transaction = transaction;
            this.node = node;
            this.currentPath = currentPath;
            this.mode = MODE_STORE;
            
            doc = (DocumentImpl) node.getOwnerDocument();
            address = node.getInternalAddress();
            
            idxSpec = doc.getCollection().getIndexConfiguration(NativeBroker.this);
            ftIdx = doc.getCollection().getFulltextIndexConfiguration(NativeBroker.this);
            level = node.getNodeId().getTreeLevel();
        }
        
        public void reset(Txn transaction, StoredNode node, NodePath currentPath, boolean fullTextIndex) {
            reset(transaction, node, currentPath);
            this.fullTextIndex = fullTextIndex;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }
        
        /** Updates the various indices */
        public void doIndex() {
        	//TODO : resolve URI !
            final boolean isTemp = XmldbURI.TEMP_COLLECTION_URI.equalsInternal(((DocumentImpl)node.getOwnerDocument()).getCollection().getURI());
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE : 
                	{
	                	//Compute index type
                		//TODO : let indexers OR it themselves
                		//we'd need to notify the ElementIndexer at the very end then...
	                	int indexType = RangeIndexSpec.NO_INDEX;
	                    if (idxSpec != null && idxSpec.getIndexByPath(currentPath) != null) {
	                        indexType |= idxSpec.getIndexByPath(currentPath).getIndexType();                      
	                    }
	                    if(ftIdx == null || currentPath == null || ftIdx.match(currentPath))
	                        indexType |= RangeIndexSpec.TEXT;
	                    if (ftIdx != null && currentPath != null && ftIdx.matchMixedElement(currentPath))
	                        indexType |= RangeIndexSpec.TEXT_MIXED_CONTENT;
	                    if(node.getChildCount() - node.getAttributesCount() > 1) {
	                        indexType |= RangeIndexSpec.MIXED_CONTENT;
	                    }
	
	                    ((ElementImpl) node).setIndexType(indexType);
	                    notifyStartElement((ElementImpl)node, currentPath, fullTextIndex);
	                    
	                    if (mode != MODE_REMOVE) {
	                        NodeProxy p = new NodeProxy(node);
	                        p.setIndexType(indexType);
	                        elementIndex.setDocument(doc);
	                        elementIndex.addNode(node.getQName(), p);
	                    }
	                    break;
                	}
                case Node.ATTRIBUTE_NODE : 
                	{                	
                		QName qname = node.getQName();
	                    if (currentPath != null)
	                        currentPath.addComponent(qname);
	                    
	                    boolean fullTextIndexing = false;

	                    //Compute index type
	                    //TODO : let indexers OR it themselves
                		//we'd need to notify the ElementIndexer at the very end then...	                    
	                	int indexType = RangeIndexSpec.NO_INDEX;                	
	                    if(fullTextIndex && (ftIdx == null || currentPath == null || ftIdx.matchAttribute(currentPath))) {
	                        indexType |= RangeIndexSpec.TEXT;
	                        fullTextIndexing = true;
	                    }
	                    if (idxSpec != null && idxSpec.getIndexByPath(currentPath) != null) {
	                        indexType |= idxSpec.getIndexByPath(currentPath).getIndexType();                        
	                    }
	                    if (idxSpec != null && idxSpec.getIndexByPath(currentPath) != null) {
	                        valueIndex.setDocument((DocumentImpl)node.getOwnerDocument());
	                        //Oh dear : is it the right semantics then ?
	                        valueIndex.storeAttribute((AttrImpl) node, currentPath, NativeValueIndex.WITHOUT_PATH, idxSpec.getIndexByPath(currentPath));
	                    }
	                    //Special handling for fulltext index
	                    //TODO : harmonize
	                    if (fullTextIndexing && !isTemp ) {
	                    	textEngine.setDocument((DocumentImpl)node.getOwnerDocument());
	                    	textEngine.storeAttribute((AttrImpl) node, null, NativeTextEngine.ATTRIBUTE_NOT_BY_QNAME, ftIdx);
	                    }	                        
	                    if (ftIdx != null && ftIdx.hasQNameIndex(node.getQName())) {
	                    	textEngine.setDocument((DocumentImpl)node.getOwnerDocument());
	                        textEngine.storeAttribute((AttrImpl) node, null, NativeTextEngine.ATTRIBUTE_BY_QNAME, ftIdx);
	                    }
	                    
	                    notifyStoreAttribute((AttrImpl)node, currentPath, NativeValueIndex.WITH_PATH, null);

	                    elementIndex.setDocument(doc);
	                    final NodeProxy tempProxy = new NodeProxy(doc, node.getNodeId(), address);
	                    tempProxy.setIndexType(indexType);
	
	                    qname.setNameType(ElementValue.ATTRIBUTE);
	                    if (mode != MODE_REMOVE)
	                        elementIndex.addNode(qname, tempProxy);
	                    
	                    // --move to-- NativeElementIndex
	                    // TODO : elementIndex.storeAttribute(node, currentPath, index);
	                    // if the attribute has type ID, store the ID-value
	                    // to the element index as well
	                    if (((AttrImpl) node).getType() == AttrImpl.ID) {
	                        qname = new QName(((AttrImpl) node).getValue(), "", null);
	                        //LOG.debug("found ID: " + qname.getLocalName());
	                        qname.setNameType(ElementValue.ATTRIBUTE_ID);
	                        elementIndex.addNode(qname, tempProxy);
	                    }
	                    
	                    if (currentPath != null)
	                        currentPath.removeLastComponent();
	                    break;
                	}
                    
                case Node.TEXT_NODE:
                    // --move to-- NativeTextEngine
                    // TODO textEngine.storeText( (TextImpl) node, currentPath, index);
                    // check if this textual content should be fulltext-indexed
                    // by calling IndexPaths.match(path)
                	if (fullTextIndex && !isTemp) {
	                    if (ftIdx == null || currentPath == null) {
	                    	textEngine.setDocument(doc);
	                    	textEngine.storeText((TextImpl) node, NativeTextEngine.TOKENIZE, ftIdx);
	                    } else if (ftIdx.match(currentPath)) {
	                    	int tokenize = ftIdx.preserveContent(currentPath) ? 
	                        		NativeTextEngine.DO_NOT_TOKENIZE : NativeTextEngine.TOKENIZE;
	                    	textEngine.setDocument(doc);
	                        textEngine.storeText((TextImpl) node, tokenize, ftIdx);
	                    }
                	}
                    
                    notifyStoreText( (TextImpl)node, currentPath, 
                    		fullTextIndex ? NativeTextEngine.DO_NOT_TOKENIZE : NativeTextEngine.TOKENIZE);
                    break;
            }
        }

        /** Stores this node into the database, if it's an element */
        public void store() {
            final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
            int depth = doc.getCollection().getIndexDepth(NativeBroker.this);
            if (depth == -1) 
            	depth = defaultIndexDepth;
            if (mode == MODE_STORE && node.getNodeType() == Node.ELEMENT_NODE && level <= depth) {
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() throws ReadOnlyException {
                        try {
                            domDb.addValue(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), address);
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
            if (nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
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
    }    

    private final class DocumentCallback implements BTreeCallback {
        
        private Collection collection;
        
        public DocumentCallback(Collection collection) {
            this.collection = collection;
        }
        
        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            try {
                byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE]; 
                VariableByteInput istream = collectionsDb.getAsStream(pointer);
                DocumentImpl doc = null;
                if (type == DocumentImpl.BINARY_FILE)
                    doc = new BinaryDocument(NativeBroker.this, collection);
                else
                    doc = new DocumentImpl(NativeBroker.this, collection);
                doc.read(istream);
                collection.addDocument(null, NativeBroker.this, doc);
            } catch (EOFException e) {
                LOG.warn("EOFException while reading document data", e);
            } catch (IOException e) {
                LOG.warn("IOException while reading document data", e);
            }
            return true;
        }
    }
}