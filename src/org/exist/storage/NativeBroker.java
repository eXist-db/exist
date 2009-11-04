/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 * $Id$
 */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.*;
import org.exist.fulltext.FTIndex;
import org.exist.fulltext.FTIndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.memtree.DOMIndexer;
import org.exist.numbering.NodeId;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Paged;
import org.exist.storage.btree.Paged.Page;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.DOMTransaction;
import org.exist.storage.dom.NodeIterator;
import org.exist.storage.dom.RawNodeIterator;
import org.exist.storage.index.BFile;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.journal.Journal;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.Loggable;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Observer;
import java.util.Stack;
import java.util.StringTokenizer;

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
	
    public final static byte LOG_RENAME_BINARY = 0x40;
    public final static byte LOG_CREATE_BINARY = 0x41;
    public final static byte LOG_UPDATE_BINARY = 0x42;
    
    static {
       LogEntryTypes.addEntryType(LOG_RENAME_BINARY, RenameBinaryLoggable.class);
       LogEntryTypes.addEntryType(LOG_CREATE_BINARY, CreateBinaryLoggable.class);
       LogEntryTypes.addEntryType(LOG_UPDATE_BINARY, UpdateBinaryLoggable.class);
    }
    
    public static final byte PREPEND_DB_ALWAYS = 0;
    public static final byte PREPEND_DB_NEVER = 1;
    public static final byte PREPEND_DB_AS_NEEDED = 2;
    
    public static final byte COLLECTIONS_DBX_ID = 0;
    public static final byte ELEMENTS_DBX_ID = 1;
    public static final byte VALUES_DBX_ID = 2;
    public static final byte DOM_DBX_ID = 3;
    //Note : no ID for symbols ? Too bad...

    public static final String PAGE_SIZE_ATTRIBUTE = "pageSize";
    public static final String INDEX_DEPTH_ATTRIBUTE = "index-depth"; 

    public static final String PROPERTY_INDEX_DEPTH = "indexer.index-depth";
    private static final byte[] ALL_STORAGE_FILES = {
    	COLLECTIONS_DBX_ID, ELEMENTS_DBX_ID, VALUES_DBX_ID, DOM_DBX_ID
    };
    
    //private static final String TEMP_FRAGMENT_REMOVE_ERROR = "Could not remove temporary fragment";
    // private static final String TEMP_STORE_ERROR = "An error occurred while storing temporary data: ";
    private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
    private static final String DATABASE_IS_READ_ONLY = "database is read-only";
    
    public static final String DEFAULT_DATA_DIR = "data";
    public static final int DEFAULT_INDEX_DEPTH = 1;
    public static final int DEFAULT_MIN_MEMORY = 5000000;
    public static final long TEMP_FRAGMENT_TIMEOUT = 60000;
    /** default buffer size setting */
    public static final int BUFFERS = 256;
    /** check available memory after storing DEFAULT_NODES_BEFORE_MEMORY_CHECK nodes */
    public static final int DEFAULT_NODES_BEFORE_MEMORY_CHECK = 500;
    
    public static int OFFSET_COLLECTION_ID = 0;
    public static int OFFSET_VALUE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2

    /** the database files */
    protected CollectionStore collectionsDb;
    protected DOMFile domDb;

    /** the index processors */	
    protected NativeElementIndex elementIndex;
    protected NativeValueIndex valueIndex;
    
    protected IndexSpec indexConfiguration;
    
    protected int defaultIndexDepth;    
	
    protected Serializer xmlSerializer;		
	
    protected boolean readOnly = false;
	
    /** used to count the nodes inserted after the last memory check */
    protected int nodesCount = 0;

    protected int nodesCountThreshold = DEFAULT_NODES_BEFORE_MEMORY_CHECK;
    
    protected String dataDir;
    protected File fsDir;
    protected File fsBackupDir;
    protected int pageSize;
	
    protected byte prepend;
	
    private final Runtime run = Runtime.getRuntime();

    private NodeProcessor nodeProcessor = new NodeProcessor();

    private EmbeddedXMLStreamReader streamReader = null;
    
    protected Journal logManager;

    /** initialize database; read configuration, etc. */
    public NativeBroker(BrokerPool pool, Configuration config) throws EXistException {
    	super(pool, config);
        this.logManager = pool.getTransactionManager().getJournal();
    	LOG.debug("Initializing broker " + hashCode());
        
        String prependDB = (String) config.getProperty("db-connection.prepend-db");
		if ("always".equalsIgnoreCase(prependDB)) {
	        prepend = PREPEND_DB_ALWAYS;
		} else if("never".equalsIgnoreCase(prependDB)) {
		    prepend = PREPEND_DB_NEVER;
		} else {
		    prepend = PREPEND_DB_AS_NEEDED;
		}

        dataDir = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
		if (dataDir == null)
            dataDir = DEFAULT_DATA_DIR;
        
        fsDir = new File(new File(dataDir),"fs");
        if (!fsDir.exists()) {
           if (!fsDir.mkdir()) {
              throw new EXistException("Cannot make collection filesystem directory: "+fsDir);
           }
        }
        fsBackupDir = new File(new File(dataDir),"fs.journal");
        if (!fsBackupDir.exists()) {
           if (!fsBackupDir.mkdir()) {
              throw new EXistException("Cannot make collection filesystem directory: "+fsBackupDir);
           }
        }
        
        nodesCountThreshold = config.getInteger(BrokerPool.PROPERTY_NODES_BUFFER);
        if (nodesCountThreshold > 0)
            nodesCountThreshold = nodesCountThreshold * 1000;
        
        defaultIndexDepth = config.getInteger(PROPERTY_INDEX_DEPTH);
		if (defaultIndexDepth < 0)
		    defaultIndexDepth = DEFAULT_INDEX_DEPTH;
        
        indexConfiguration = (IndexSpec) config.getProperty(Indexer.PROPERTY_INDEXER_CONFIG);
        xmlSerializer = new NativeSerializer(this, config);
        setUser( SecurityManager.SYSTEM_USER );            
        
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
            
		    //Initialize collections storage            
            collectionsDb = (CollectionStore) config.getProperty(CollectionStore.getConfigKeyForFile());
		    if (collectionsDb == null)
		    	collectionsDb = new CollectionStore(pool, COLLECTIONS_DBX_ID, dataDir, config);	
		    readOnly = readOnly || collectionsDb.isReadOnly();
	            
		    elementIndex = new NativeElementIndex(this, ELEMENTS_DBX_ID, dataDir, config);
		    valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
		    if (readOnly)
		    	LOG.info("Database runs in read-only mode");
	
		} catch (DBException e) {
		    LOG.debug(e.getMessage(), e);
		    throw new EXistException(e);
		}
    }
        
    public void addObserver(Observer o) {
        super.addObserver(o);
//        textEngine.addObserver(o);
        elementIndex.addObserver(o);
        //TODO : what about other indexes observers ?
    }
    
    public void deleteObservers() {
        super.deleteObservers();
        if (elementIndex != null)
            elementIndex.deleteObservers();
        //TODO : what about other indexes observers ?
//        if (textEngine != null)
//            textEngine.deleteObservers();
    }
  
    // ============ dispatch the various events to indexing classes ==========

    private void notifyRemoveNode(StoredNode node, NodePath currentPath, String content) {
        for (int i = 0; i < contentLoadingObservers.size(); i++) {
            ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
            observer.removeNode(node, currentPath, content);
        }
    }

    //private void notifyStoreAttribute(AttrImpl attr, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove) {
    //    for (int i = 0; i < contentLoadingObservers.size(); i++) {
    //        ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
    //        observer.storeAttribute(attr, currentPath, indexingHint, spec, remove);
    //    }	
    //}	

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
    public void endElement(final StoredNode node, NodePath currentPath, String content, boolean remove) {
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
            valueIndex.storeElement((ElementImpl) node, content, RangeIndexSpec.indexTypeToXPath(indexType),
				    NativeValueIndex.IDX_GENERIC, remove);
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
            valueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
            valueIndex.storeElement((ElementImpl) node, content, RangeIndexSpec.indexTypeToXPath(indexType),
				    NativeValueIndex.IDX_QNAME, remove);
	    //            qnameValueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
	    //            qnameValueIndex.endElement((ElementImpl) node, currentPath, content);
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
    public void endRemove(Txn transaction) {
        notifyRemove();
    }

    public boolean isReadOnly() {
        return readOnly;
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
	default:
	    return null;
        }
    }
    
    public byte[] getStorageFileIds() {
        return ALL_STORAGE_FILES;
    }        

    public int getDefaultIndexDepth() {
        return defaultIndexDepth;
    }
    
    public void backupToArchive(RawDataBackup backup) throws IOException {
        for (byte i = 0; i < ALL_STORAGE_FILES.length; i++) {
            Paged paged = getStorage(i);
            if (paged == null) {
                LOG.warn("Storage file is null: " + i);
                continue;
            }
            OutputStream os = backup.newEntry(paged.getFile().getName());
            paged.backupToStream(os);
            backup.closeEntry();
        }
        OutputStream os = backup.newEntry(pool.getSymbols().getFile().getName());
        pool.getSymbols().backupSymbolsTo(os);
        backup.closeEntry();
        pool.getIndexManager().backupToArchive(backup);
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
    
    public TextSearchEngine getTextEngine() {
        FTIndexWorker worker = (FTIndexWorker) indexController.getWorkerByIndexId(FTIndex.ID);
        if (worker == null) {
            LOG.warn("Fulltext index is not configured. Please check the <modules> section in conf.xml");
            return null;
        }
        return worker.getEngine();
    }

    public EmbeddedXMLStreamReader getXMLStreamReader(NodeHandle node, boolean reportAttributes)
	throws IOException, XMLStreamException {
        if (streamReader == null) {
            RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
            streamReader = new EmbeddedXMLStreamReader(this, (DocumentImpl) node.getOwnerDocument(), iterator, node, reportAttributes);
        } else {
            streamReader.reposition(this, node, reportAttributes);
        }
        return streamReader;
    }

    public EmbeddedXMLStreamReader newXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException {
        RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
        return new EmbeddedXMLStreamReader(this, (DocumentImpl) node.getOwnerDocument(), iterator, null, reportAttributes);
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
    
    /**
     * Creates a temporary collecion
     * 
     * @param transaction : The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
     * @return The temporary collection
     * @throws LockException
     * @throws PermissionDeniedException
     * @throws IOException
     */
    private Collection createTempCollection(Txn transaction) throws LockException, PermissionDeniedException, IOException
    {
        User u = getUser();
        try
        {
            setUser( pool.getSecurityManager().getUser(SecurityManager.DBA_USER) );
            Collection temp = getOrCreateCollection(transaction, XmldbURI.TEMP_COLLECTION_URI);
            temp.setPermissions(0771);
            saveCollection(transaction, temp);
            return temp;
        }
        finally
        {
            setUser( u );
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.DBBroker#getOrCreateCollection(org.exist.storage.txn.Txn, org.exist.xmldb.XmldbURI)
     */
    public Collection getOrCreateCollection(Txn transaction, XmldbURI name) throws PermissionDeniedException,
										   IOException {
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
                    current.getPermissions().setOwner(getUser());
                    current.getPermissions().setGroup(getUser().getPrimaryGroup());
                    current.setId(getNextCollectionId(transaction));
                    current.setCreationTime(System.currentTimeMillis());
                    if (transaction != null)
                        transaction.acquireLock(current.getLock(), Lock.WRITE_LOCK);
                    //TODO : acquire lock manually if transaction is null ?
                    saveCollection(transaction, current);
                }
                for(int i=1;i<segments.length;i++) {
                    XmldbURI temp = segments[i];
                    path = path.append(temp);
                    if (current.hasSubcollectionNoLock(temp)) {
                        current = getCollection(path);
                        if (current == null)
                            LOG.debug("Collection '" + path + "' not found!");
                    } else {
                        if (readOnly)
                            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                        if (!current.getPermissionsNoLock().validate(getUser(), Permission.WRITE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("User '"+ getUser().getName() + "' not allowed to write to collection '" + current.getURI() + "'");
                        }
                        LOG.debug("Creating collection '" + path + "'...");
                        sub = new Collection(path);
                        sub.setId(getNextCollectionId(transaction));
                        if (transaction != null)
                            transaction.acquireLock(sub.getLock(), Lock.WRITE_LOCK);
                        //TODO : acquire lock manually if transaction is null ?
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
	return openCollection(uri, Lock.NO_LOCK);
    }
	
    public Collection openCollection(XmldbURI uri, int lockMode) {
	return openCollection(uri, BFile.UNKNOWN_ADDRESS, lockMode);
    }

    /**
     *  Get collection object. If the collection does not exist, null is
     *  returned.
     *
     *@param  uri  collection URI
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
				    if (is == null)
				    	return null;

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
				    lock.release(Lock.READ_LOCK);
				}
    		} else {
                if (!collection.getURI().equalsInternal(uri)) {
                    LOG.error("The collection received from the cache is not the requested: " + uri +
                        "; received: " + collection.getURI());
                }
                collectionsCache.add(collection);
    		}
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
    
    /* (non-Javadoc)
     * @see org.exist.storage.DBBroker#copyCollection(org.exist.storage.txn.Txn, org.exist.collections.Collection, org.exist.collections.Collection, org.exist.xmldb.XmldbURI)
     */
    public void copyCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newUri)
   	throws PermissionDeniedException, LockException, IOException {
    	if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		//TODO : resolve URIs !!!
		if (newUri != null && newUri.numSegments() !=1) {
		    throw new PermissionDeniedException("New collection name must have one segment!");
		}
        if(!collection.getPermissions().validate(getUser(), Permission.READ))
            throw new PermissionDeniedException("Read permission denied on collection " +
						collection.getURI());
        if(collection.getId() == destination.getId())
            throw new PermissionDeniedException("Cannot move collection to itself");
        if(collection.getURI().equals(destination.getURI().append(newUri)))
        	throw new PermissionDeniedException("Cannot move collection to itself");       
        if(!destination.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges on target collection " +
						destination.getURI());
        try {
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_COPY_COLLECTION, collection.getURI());
            //TODO : relativize URI !!!
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
                    old.release(Lock.WRITE_LOCK);
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
                        //TODO : put a lock on newDoc ?
                        DocumentImpl newDoc = new DocumentImpl(pool, destCollection, child.getFileURI());
                        newDoc.copyOf(child);
                        newDoc.setDocId(getNextResourceId(transaction, destination));
                        copyXMLResource(transaction, child, newDoc);
                        storeXMLResource(transaction, newDoc);
                        destCollection.addDocument(transaction, this, newDoc);
                    } else {
                        BinaryDocument newDoc = new BinaryDocument(pool, destCollection, child.getFileURI());
                        newDoc.copyOf(child);
                        newDoc.setDocId(getNextResourceId(transaction, destination));
                        /*
                        byte[] data = getBinaryResource((BinaryDocument) child);
                        storeBinaryResource(transaction, newDoc, data);
                         */
                        InputStream is = getBinaryResource((BinaryDocument)child);
                        storeBinaryResource(transaction,newDoc,is);
                        is.close();
                        storeXMLResource(transaction, newDoc);
                        destCollection.addDocument(transaction, this, newDoc);
                    }
                }
                saveCollection(transaction, destCollection);
            } finally {
                lock.release(Lock.WRITE_LOCK);
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
                        child.release(Lock.WRITE_LOCK);
                    }
                }
            }
            saveCollection(transaction, destCollection);
            saveCollection(transaction, destination);
        } finally {
            pool.getProcessMonitor().endJob();
        }
    }
    
    public void moveCollection(Txn transaction, Collection collection, Collection destination, XmldbURI newName) 
	throws PermissionDeniedException, LockException, IOException {
        pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_MOVE_COLLECTION, collection.getURI());
        try {
            // sourceDir must be known in advance, because once moveCollectionRecursive
	    // is called, both collection and destination can point to the same resource
            File sourceDir = getCollectionFile(fsDir,collection.getURI(),false);
            // Need to move each collection in the source tree individually, so recurse.
            moveCollectionRecursive(transaction, collection, destination, newName);
            // For binary resources, though, just move the top level directory and all descendants come with it.
            moveBinaryFork(transaction, sourceDir, destination, newName);
        } finally {
            pool.getProcessMonitor().endJob();
        }
    }

	private void moveBinaryFork(Txn transaction, File sourceDir, Collection destination, XmldbURI newName) throws IOException {
        File targetDir = getCollectionFile(fsDir,destination.getURI().append(newName),false);
        if (sourceDir.exists()) {
           targetDir.getParentFile().mkdirs();
           if (sourceDir.renameTo(targetDir)) {
              Loggable loggable = new RenameBinaryLoggable(this,transaction,sourceDir,targetDir);
              try {
                 logManager.writeToLog(loggable);
              } catch (TransactionException e) {
                  LOG.warn(e.getMessage(), e);
              }
           } else {
              LOG.fatal("Cannot move "+sourceDir+" to "+targetDir);
           }
        }
	}

	private void moveCollectionRecursive(Txn transaction, Collection collection, Collection destination, XmldbURI newName) throws PermissionDeniedException, IOException, LockException {
		if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
		if(newName!=null && newName.numSegments()!=1) {
		    throw new PermissionDeniedException("New collection name must have one segment!");
		}
        if(collection.getId() == destination.getId())
            throw new PermissionDeniedException("Cannot move collection to itself");
        if(collection.getURI().equals(destination.getURI().append(newName)))
        	throw new PermissionDeniedException("Cannot move collection to itself");          
        if(collection.getURI().equals(XmldbURI.ROOT_COLLECTION_URI))
            throw new PermissionDeniedException("Cannot move the db root collection");
        if(!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move collection " +
						collection.getURI());
        if(!destination.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges on target collection " +
						destination.getURI());
        
        // check if another collection with the same name exists at the destination
        Collection old = openCollection(destination.getURI().append(newName), Lock.WRITE_LOCK);
        if(old != null) {
            try {
                removeCollection(transaction, old);
            } finally {
                old.release(Lock.WRITE_LOCK);
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
                    parent.release(Lock.WRITE_LOCK);
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
                lock.release(Lock.WRITE_LOCK);
            }        

            for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
            	XmldbURI childName = (XmldbURI)i.next();
            	//TODO : resolve URIs !!! name.resolve(childName)
            	Collection child = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                if (child == null)
                    LOG.warn("Child collection " + childName + " not found");
                else {
                    try {
                        moveCollectionRecursive(transaction, child, collection, childName);
                    } finally {
                        child.release(Lock.WRITE_LOCK);
                    }
                }
            }
        }
	}    
    
    private void canRemoveCollection(Collection collection) throws PermissionDeniedException {
        if(!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("User '"+ getUser().getName() + "' not allowed to remove collection '" + collection.getURI() + "'");
        final XmldbURI uri = collection.getURI();
        for(Iterator i = collection.collectionIterator(); i.hasNext();)
        {
            final XmldbURI childName = (XmldbURI) i.next();
            //TODO : resolve from collection's base URI
            //TODO : resulve URIs !!! (uri.resolve(childName))
            Collection childCollection = openCollection(uri.append(childName), Lock.WRITE_LOCK);
            try {
                canRemoveCollection(childCollection);
            } finally {
                if (childCollection != null)
                    childCollection.getLock().release(Lock.WRITE_LOCK);
                else
                    LOG.warn("childCollection is null !");
            }
        }
    }

    /**
     * Removes a collection and all child collections and resources
     * 
     * @param transaction the transaction to use
     * @param collection the collection to remove
     * @return true if the collection was removed, false otherwise
     */
    public boolean removeCollection(final Txn transaction, Collection collection) throws PermissionDeniedException, IOException
    {
        if(readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);

        if(!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("User '"+ getUser().getName() + "' not allowed to remove collection '" + collection.getURI() + "'");

        try {
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_COLLECTION, collection.getURI());
            long start = System.currentTimeMillis();
            final CollectionCache collectionsCache = pool.getCollectionsCache();
            File sourceDir = getCollectionFile(fsDir,collection.getURI(),false);
            File targetDir = getCollectionFile(fsBackupDir,transaction,collection.getURI(),true);

            synchronized(collectionsCache)
        {
            canRemoveCollection(collection);

            final XmldbURI uri = collection.getURI();
                final String collName = uri.getRawCollectionPath();
                final boolean isRoot = collection.getParentURI() == null;

                // Notify the collection configuration manager
                pool.getConfigurationManager().invalidateAll(uri);

                // remove child collections
                if(LOG.isDebugEnabled())
                    LOG.debug("Removing children collections from their parent '" + collName + "'...");

                for(Iterator i = collection.collectionIterator(); i.hasNext();)
                {
                    final XmldbURI childName = (XmldbURI) i.next();
                    //TODO : resolve from collection's base URI
                    //TODO : resulve URIs !!! (uri.resolve(childName))
                    Collection childCollection = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                    try {
                        removeCollection(transaction, childCollection);
                    } finally {
                        if (childCollection != null)
                            childCollection.getLock().release(Lock.WRITE_LOCK);
                        else
                            LOG.warn("childCollection is null !");
                    }
                }

                //Drop all index entries
                notifyDropIndex(collection);

                // Drop custom indexes
                indexController.removeCollection(collection, this);

                if(!isRoot)
                {
                    // remove from parent collection
                    //TODO : resolve URIs ! (uri.resolve(".."))
                    Collection parentCollection = openCollection(collection.getParentURI(), Lock.WRITE_LOCK);

                    // keep the lock for the transaction
                    if(transaction != null)
                        transaction.registerLock(parentCollection.getLock(), Lock.WRITE_LOCK);

                    if(parentCollection != null)
                    {
                        try
                        {
                            LOG.debug("Removing collection '" + collName + "' from its parent...");
                            //TODO : resolve from collection's base URI
                            parentCollection.removeCollection(uri.lastSegment());
                            saveCollection(transaction, parentCollection);
                        }
                        catch(LockException e)
                        {
                            LOG.warn("LockException while removing collection '" + collName + "'");
                        }
                        finally
                        {
                            if(transaction == null)
                                parentCollection.getLock().release(Lock.WRITE_LOCK);
                        }
                    }
                }

                //Update current state
                Lock lock = collectionsDb.getLock();
                try
                {
                    lock.acquire(Lock.WRITE_LOCK);
                    // remove the metadata of all documents in the collection
                    Value docKey = new CollectionStore.DocumentKey(collection.getId());
                    IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, docKey);
                    collectionsDb.removeAll(transaction, query);

                    // if this is not the root collection remove it...
                    if(!isRoot)
                    {
                        Value key = new CollectionStore.CollectionKey(collName);
                        //... from the disk
                        collectionsDb.remove(transaction, key);
                        //... from the cache
                        collectionsCache.remove(collection);
                        //and free its id for any futher use
                        freeCollectionId(transaction, collection.getId());
                    }
                    else
                    {
                        //Simply save the collection on disk
                        //It will remain cached
                        //and its id well never be made available
                        saveCollection(transaction, collection);
                    }
                }
                catch(LockException e)
                {
                    LOG.warn("Failed to acquire lock on '" + collectionsDb.getFile().getName() + "'");
                }
                catch(ReadOnlyException e)
                {
                    throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                }
                catch(BTreeException e)
                {
                    LOG.warn("Exception while removing collection: " + e.getMessage(), e);
                }
                catch(IOException e)
                {
                    LOG.warn("Exception while removing collection: " + e.getMessage(), e);
                }
                finally
                {
                    lock.release(Lock.WRITE_LOCK);
                }

                //Remove child resources
                if (LOG.isDebugEnabled())
                    LOG.debug("Removing resources in '" + collName + "'...");

                for(Iterator i = collection.iterator(this); i.hasNext();)
                {
                    final DocumentImpl doc = (DocumentImpl) i.next();
                    //Remove doc's metadata
                    // WM: now removed in one step. see above.
                    //removeResourceMetadata(transaction, doc);
                    //Remove document nodes' index entries
                    new DOMTransaction(this, domDb, Lock.WRITE_LOCK)
                    {
                        public Object start()
                        {
                            try
                            {
                                Value ref = new NodeRef(doc.getDocId());
                                IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                                domDb.remove(transaction, query, null);
                            }
                            catch(BTreeException e)
                            {
                                LOG.warn("btree error while removing document", e);
                            }
                            catch(IOException e)
                            {
                                LOG.warn("io error while removing document", e);
                            }
                            catch(TerminatedException e)
                            {
                                LOG.warn("method terminated", e);
                            }
                            return null;
                        }
                    }.run();

                    //Remove nodes themselves
                    new DOMTransaction(this, domDb, Lock.WRITE_LOCK)
                    {
                        public Object start()
                        {
                            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                            {
                                long page = ((BinaryDocument)doc).getPage();

                                if (page > Page.NO_PAGE)
                                    domDb.removeOverflowValue(transaction, page);
                            }
                            else
                            {
                                StoredNode node = (StoredNode)doc.getFirstChild();
                                domDb.removeAll(transaction, node.getInternalAddress());
                            }
                            return null;
                        }
                    }.run();

                    //Make doc's id available again
                    freeResourceId(transaction, doc.getDocId());
                }

                if (sourceDir.exists()) {
                   targetDir.getParentFile().mkdirs();
                   if (sourceDir.renameTo(targetDir)) {
                     Loggable loggable = new RenameBinaryLoggable(this,transaction,sourceDir,targetDir);
                     try {
                        logManager.writeToLog(loggable);
                     } catch (TransactionException e) {
                         LOG.warn(e.getMessage(), e);
                     }

                   } else {
                      LOG.fatal("Cannot rename "+sourceDir+" to "+targetDir);
                   }
                }
                if(LOG.isDebugEnabled())
                    LOG.debug("Removing collection '" + collName + "' took " + (System.currentTimeMillis() - start));

                return true;
            }
        } finally {
            pool.getProcessMonitor().endJob();
        }
    }
    
    /**
     * Saves the specified collection to storage. Collections are usually cached in
     * memory. If a collection is modified, this method needs to be called to make
     * the changes persistent.
     * 
     * Note: appending a new document to a collection does not require a save.
     */
    public void saveCollection(Txn transaction, Collection collection) throws 
    	PermissionDeniedException, IOException {
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

        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     * Release the collection id assigned to a collection so it can be
     * reused later.
     * 
     * @param id
     * @throws PermissionDeniedException
     */
    protected void freeCollectionId(Txn transaction, int id) throws PermissionDeniedException {       
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_COLLECTION_ID_KEY);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                byte[] ndata = new byte[data.length + Collection.LENGTH_COLLECTION_ID];
                System.arraycopy(data, 0, ndata, OFFSET_VALUE, data.length);
                ByteConversion.intToByte(id, ndata, OFFSET_COLLECTION_ID);
                collectionsDb.put(transaction, key, ndata, true);
            } else {
                byte[] data = new byte[Collection.LENGTH_COLLECTION_ID];
                ByteConversion.intToByte(id, data, OFFSET_COLLECTION_ID);
                collectionsDb.put(transaction, key, data, true);
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            //TODO : rethrow ? -pb
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     * Get the next free collection id. If a collection is removed, its collection id
     * is released so it can be reused.
     * 
     * @return next free collection id.
     * @throws ReadOnlyException
     */
    public int getFreeCollectionId(Txn transaction) throws ReadOnlyException {
        int freeCollectionId = Collection.UNKNOWN_COLLECTION_ID;
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.FREE_COLLECTION_ID_KEY);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                freeCollectionId = ByteConversion.byteToInt(data, data.length - Collection.LENGTH_COLLECTION_ID);
                //LOG.debug("reusing collection id: " + freeCollectionId);
                if(data.length - Collection.LENGTH_COLLECTION_ID > 0) {
                    byte[] ndata = new byte[data.length - Collection.LENGTH_COLLECTION_ID];
                    System.arraycopy(data, 0, ndata, OFFSET_COLLECTION_ID, ndata.length);
                    collectionsDb.put(transaction, key, ndata, true);
                } else
                    collectionsDb.remove(transaction, key);
//            } else {
//                try {
//                    StringWriter sw = new StringWriter();
//                    collectionsDb.dump(sw);
//                    LOG.debug(CollectionStore.FREE_COLLECTION_ID_KEY + ": " + key.dump());
//                    LOG.debug(sw.toString());
//                } catch (IOException e) {
//                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                } catch (BTreeException e) {
//                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                }
            }
            return freeCollectionId;
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /**
     * Get the next available unique collection id.
     * 
     * @return next available unique collection id
     * @throws ReadOnlyException
     */
    public int getNextCollectionId(Txn transaction) throws ReadOnlyException {
        
        int nextCollectionId = getFreeCollectionId(transaction);
        
        if(nextCollectionId != Collection.UNKNOWN_COLLECTION_ID)
            return nextCollectionId;        
        
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_COLLECTION_ID_KEY);
            Value data = collectionsDb.get(key);
            if (data != null) {
                nextCollectionId = ByteConversion.byteToInt(data.getData(), OFFSET_COLLECTION_ID);
                ++nextCollectionId;
            }
            byte[] d = new byte[Collection.LENGTH_COLLECTION_ID];
            ByteConversion.intToByte(nextCollectionId, d, OFFSET_COLLECTION_ID);
            collectionsDb.put(transaction, key, d, true);
            return nextCollectionId;
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
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
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REINDEX_COLLECTION, collection.getURI());
            reindexCollection(transaction, collection, mode);
            transact.commit(transaction);
        } catch (TransactionException e) {
            transact.abort(transaction);
            LOG.warn("An error occurred during reindex: " + e.getMessage(), e);
        } finally {
            pool.getProcessMonitor().endJob();
        }
    }
    
    public void reindexCollection(Txn transaction, Collection collection, int mode) throws PermissionDeniedException {
        if (!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("insufficient privileges on collection " + collection.getURI());
        LOG.debug("Reindexing collection " + collection.getURI());
        
        if (mode == NodeProcessor.MODE_STORE)
            dropCollectionIndex(transaction, collection);
        for(Iterator i = collection.iterator(this); i.hasNext(); ) {
            DocumentImpl next = (DocumentImpl)i.next();
            reindexXMLResource(transaction, next, mode);
            if (mode == NodeProcessor.MODE_REPAIR)
                pool.signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
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
    
    public void dropCollectionIndex(final Txn transaction, Collection collection) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        if (!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("insufficient privileges on collection " + 
						collection.getURI());
        
        notifyDropIndex(collection);
        indexController.removeCollection(collection, this);
        
        for (Iterator i = collection.iterator(this); i.hasNext();) {
            final DocumentImpl doc = (DocumentImpl) i.next();
            LOG.debug("Dropping index for document " + doc.getFileURI());
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                public Object start() {
                    try {
                        Value ref = new NodeRef(doc.getDocId());
                        IndexQuery query =
                            new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                        domDb.remove(transaction, query, null);
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
    
    /** Store into the temporary collection of the database a given in-memory Document
     *
     * The in-memory Document is stored without a transaction and is not journalled,
     * if there is no temporary collection, this will first be created with a transaction
     *
     * @param doc The in-memory Document to store
     * @return The document stored in the temp collection
     */
    public DocumentImpl storeTempResource(org.exist.memtree.DocumentImpl doc) throws EXistException, PermissionDeniedException, LockException
    {
        //store the currentUser
        User currentUser = getUser();
        
        //elevate getUser() to DBA_USER
        setUser( pool.getSecurityManager().getUser(SecurityManager.DBA_USER) );
    	
        //start a transaction
    	TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        
        //create a name for the temporary document
        XmldbURI docName = XmldbURI.create(MessageDigester.md5(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis()),false) + ".xml");
        
        //get the temp collection
        Collection temp = openCollection(XmldbURI.TEMP_COLLECTION_URI, Lock.WRITE_LOCK);
        boolean created = false;
        try
	    {
        	//if no temp collection
        	if(temp == null)
		    {
        		//creates temp collection (with write lock)
        		temp = createTempCollection(transaction);
        		if(temp == null)
        		{
        			LOG.warn("Failed to create temporary collection");
        		}
                created = true;
            }
            //create a temporary document
			DocumentImpl targetDoc = new DocumentImpl(pool, temp, docName);
			targetDoc.setPermissions(0771);
			long now = System.currentTimeMillis();
			DocumentMetadata metadata = new DocumentMetadata();
			metadata.setLastModified(now);
			metadata.setCreated(now);
			targetDoc.setMetadata(metadata);
			targetDoc.setDocId(getNextResourceId(transaction, temp));

			//index the temporary document
			DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc); //NULL transaction, so temporary fragment is not journalled - AR
			indexer.scan();
			indexer.store();

			//store the temporary document
			temp.addDocument(transaction, this, targetDoc); //NULL transaction, so temporary fragment is not journalled - AR

            // unlock the temp collection
            if(transaction == null)
            {
                temp.getLock().release(Lock.WRITE_LOCK);
            }
            else if (!created)
            {
                transaction.registerLock(temp.getLock(), Lock.WRITE_LOCK);
            }
            
 			storeXMLResource(transaction, targetDoc); //NULL transaction, so temporary fragment is not journalled - AR
			flush();
			closeDocument();            
	        
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
        	//restore the getUser()
        	setUser( currentUser );
	    }
        
        return null;
    }
    
    /** remove all documents from temporary collection */
    public void cleanUpTempResources()
    {
    	cleanUpTempResources(false);
    }
    
    /** remove all documents from temporary collection
     * 
     * @param forceRemoval Should temporary resources be forcefully removed 
     */
    public void cleanUpTempResources(boolean forceRemoval)
    {
        Collection temp = getCollection(XmldbURI.TEMP_COLLECTION_URI);
        if(temp == null)
            return;
    
        /* We can remove the entire collection if all temp
        data has timed out. It is much faster to remove a collection
        than a number of documents from a collection
        */
        boolean removeCollection = true;
        if(!forceRemoval)
        {
	        long now = System.currentTimeMillis();
	        for(Iterator i = temp.iterator(this); i.hasNext();)
	        {
	            DocumentImpl next = (DocumentImpl) i.next();
	            long modified = next.getMetadata().getLastModified();
	            if(now - modified < TEMP_FRAGMENT_TIMEOUT)
	            {
	                removeCollection = false;
	                break;
	            }
	        }
        }
        
        //remove the entire temp collection?
        if(removeCollection)
        {
        	TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
        	
            try
            {
                removeCollection(transaction, temp);
                transact.commit(transaction);
            }
            catch(Exception e)
            {
            	transact.abort(transaction);
                LOG.warn("Failed to remove temp collection: " + e.getMessage(), e);
            }
        }
    }
    
    /** store Document entry into its collection. */
    public void storeXMLResource(final Txn transaction, final DocumentImpl doc) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
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
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    private File getCollectionFile(File dir,XmldbURI uri,boolean create)
       throws IOException
    {
       return getCollectionFile(dir,null,uri,create);
    }
    
    private File getCollectionFile(File dir,Txn transaction,XmldbURI uri,boolean create)
       throws IOException
    {
       if (transaction!=null) {
          dir = new File(dir,"txn."+transaction.getId());
          if (create && !dir.exists()) {
             if (!dir.mkdir()) {
                throw new IOException("Cannot make transaction filesystem directory: "+dir);
             }
          }
       }
       XmldbURI [] segments = uri.getPathSegments();
       File binFile = dir;
       int last = segments.length-1;
       for (int i=0; i<segments.length; i++) {
          binFile = new File(binFile,segments[i].toString());
          if (create && i!=last && !binFile.exists()) {
             if (!binFile.mkdir()) {
                throw new IOException("Cannot make collection filesystem directory: "+binFile);
             }
          }
       }
       return binFile;
    }
    
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data) 
      throws IOException
    {
       blob.setPage(Page.NO_PAGE);
       File binFile = getCollectionFile(fsDir,blob.getURI(),true);
       File backupFile = null;
       boolean exists = binFile.exists();
       if (exists) {
          backupFile = getCollectionFile(fsBackupDir,transaction,blob.getURI(),true);
          if (!binFile.renameTo(backupFile)) {
             throw new IOException("Cannot backup binary resource for journal to "+backupFile);
          }
       }
       
       OutputStream os = new FileOutputStream(binFile);
       os.write(data,0,data.length);
       os.close();
       
       if (exists) {
          Loggable loggable = new UpdateBinaryLoggable(this,transaction,binFile,backupFile);
          try {
             logManager.writeToLog(loggable);
          } catch (TransactionException e) {
             LOG.warn(e.getMessage(), e);
          }
       } else {
          Loggable loggable = new CreateBinaryLoggable(this,transaction,binFile);
          try {
             logManager.writeToLog(loggable);
          } catch (TransactionException e) {
             LOG.warn(e.getMessage(), e);
          }
       }
       
    }    
    
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final InputStream is) 
       throws IOException
    {
       blob.setPage(Page.NO_PAGE);
       File binFile = getCollectionFile(fsDir,blob.getURI(),true);
       
       File backupFile = null;
       boolean exists = binFile.exists();
       if (exists) {
          backupFile = getCollectionFile(fsBackupDir,transaction,blob.getURI(),true);
          if (!binFile.renameTo(backupFile)) {
             throw new IOException("Cannot backup binary resource for journal to "+backupFile);
          }
       }
       
       byte [] buffer = new byte[65536];
       OutputStream os = new FileOutputStream(binFile);
       int len;
       while ((len = is.read(buffer))>=0) {
          if (len>0) {
             os.write(buffer,0,len);
          }
       }
       os.close();
       
       if (exists) {
          Loggable loggable = new UpdateBinaryLoggable(this,transaction,binFile,backupFile);
          try {
             logManager.writeToLog(loggable);
          } catch (TransactionException e) {
             LOG.warn(e.getMessage(), e);
          }
       } else {
          Loggable loggable = new CreateBinaryLoggable(this,transaction,binFile);
          try {
             logManager.writeToLog(loggable);
          } catch (TransactionException e) {
             LOG.warn(e.getMessage(), e);
          }
       }
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
        if (!collection.getPermissions().validate(getUser(), Permission.READ))
            throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "'");
        
        DocumentImpl doc = collection.getDocument(this, docUri);
        if (doc == null) {
            LOG.debug("document '" + fileName + "' not found!");
            return null;
        }
        
       if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
           BinaryDocument bin = (BinaryDocument)doc;
           try {
              bin.setContentLength((int)getBinaryResourceSize(bin));
           } catch (IOException ex) {
              LOG.fatal("Cannot get content size for "+bin.getURI(),ex);
           }
       }
	//      if (!doc.getPermissions().validate(getUser(), Permission.READ))
	//          throw new PermissionDeniedException("not allowed to read document");
        
        return doc;
    }
    
    public DocumentImpl getXMLResource(XmldbURI fileName, int lockMode) throws PermissionDeniedException {

        if(fileName==null){
          return null;
        }

        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !
        XmldbURI collUri = fileName.removeLastSegment();
        XmldbURI docUri = fileName.lastSegment();
        
        Collection collection = openCollection(collUri, lockMode);
        if (collection == null) {
            LOG.debug("collection '" + collUri + "' not found!");
            return null;
        }
        try {
           if (!collection.getPermissions().validate(getUser(), Permission.READ))
               throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "'");

            DocumentImpl doc = collection.getDocumentWithLock(this, docUri, lockMode);
            if (doc == null) {
		//                LOG.debug("document '" + fileName + "' not found!");
                return null;
            }
            
	    //      if (!doc.getPermissions().validate(getUser(), Permission.READ))
	    //          throw new PermissionDeniedException("not allowed to read document");
            
             if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                 BinaryDocument bin = (BinaryDocument)doc;
                 try {
                    bin.setContentLength((int)getBinaryResourceSize(bin));
                 } catch (IOException ex) {
                    LOG.fatal("Cannot get content size for "+bin.getURI(),ex);
                 }
             }
            return doc;
        } catch (LockException e) {
            LOG.warn("Could not acquire lock on document " + fileName, e);
            //TODO : exception ? -pb
        } finally {
            //TOUNDERSTAND : by whom is this lock acquired ? -pb
            if(collection != null)
                collection.release(lockMode);            
        }
        return null;
    }    
    
    /*
    public byte[] getBinaryResource(final BinaryDocument blob) 
       throws IOException
    {
    	if (blob.getPage() == Page.NO_PAGE)
    		return new byte[0];
        byte[] data = (byte[]) new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
			public Object start() throws ReadOnlyException {
			    return domDb.getBinary(blob.getPage());
			}
	    }
	    .run();
        return data;
       ByteArrayOutputStream os = new ByteArrayOutputStream();
       readBinaryResource(blob,os);
       return os.toByteArray();
    }
     */
    
    public void readBinaryResource(final BinaryDocument blob, final OutputStream os) 
      throws IOException
    {
       /*
    	if (blob.getPage() == Page.NO_PAGE)
    		return;
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                domDb.readBinary(blob.getPage(), os);
                return null;
            }
        }.run();
        */
       InputStream is = null;
       try
       {
           is = getBinaryResource(blob);
           byte [] buffer = new byte[65536];
           int len;
           while ((len=is.read(buffer))>=0) {
              os.write(buffer,0,len);
           }
       }
       finally
       {
           if(is != null)
               is.close();
       }
    }
    
    public long getBinaryResourceSize(final BinaryDocument blob) 
      throws IOException
    {
       File binFile = getCollectionFile(fsDir,blob.getURI(),false);
       return binFile.length();
    }
    public InputStream getBinaryResource(final BinaryDocument blob) 
      throws IOException
    {
       /*
    	if (blob.getPage() == Page.NO_PAGE)
    		return;
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            public Object start() throws ReadOnlyException {
                domDb.readBinary(blob.getPage(), os);
                return null;
            }
        }.run();
        */
       File binFile = getCollectionFile(fsDir,blob.getURI(),false);
       return new FileInputStream(binFile);
    }
    
    //TODO : consider a better cooperation with Collection -pb
    public void getCollectionResources(Collection collection) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);            
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
            lock.release(Lock.READ_LOCK);
        }
    }

    public void getResourcesFailsafe(BTreeCallback callback, boolean fullScan) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            Value key = new CollectionStore.DocumentKey();
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            if (fullScan)
                collectionsDb.rawScan(query, callback);
            else
                collectionsDb.query(query, callback);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } catch (BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } catch (TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    public void getCollectionsFailsafe(BTreeCallback callback) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            Value key = new CollectionStore.CollectionKey();
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, callback);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } catch (BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } catch (TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     *  Get all the documents in this database matching the given
     *  document-type's name.
     * @return The documentsByDoctype value
     */
    public MutableDocumentSet getXMLResourcesByDoctype(String doctypeName, MutableDocumentSet result) {
        MutableDocumentSet docs = getAllXMLResources(new DefaultDocumentSet());
        for (Iterator i = docs.getDocumentIterator(); i.hasNext();) {
		    DocumentImpl doc = (DocumentImpl) i.next();
		    DocumentType doctype = doc.getDoctype();
            if (doctype == null)
                continue;
            if (doctypeName.equals(doctype.getName())
                && doc.getCollection().getPermissions().validate(getUser(), Permission.READ)
                && doc.getPermissions().validate(getUser(), Permission.READ))
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
    public MutableDocumentSet getAllXMLResources(MutableDocumentSet docs) {
        long start = System.currentTimeMillis();
        Collection rootCollection = null;
        try {
		    rootCollection = openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.READ_LOCK);
		    rootCollection.allDocs(this, docs, true, false);
            if (LOG.isDebugEnabled()) {
                LOG.debug("getAllDocuments(DocumentSet) - end - "
			  + "loading "
			  + docs.getDocumentCount()
			  + " documents took "
			  + (System.currentTimeMillis() - start)
			  + "ms.");
            }
            return docs;
        } finally {
            if (rootCollection != null)
            	rootCollection.release(Lock.READ_LOCK);
        }
    }    
    
    //TODO : consider a better cooperation with Collection -pb
    public void getResourceMetadata(DocumentImpl document) {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            VariableByteInput istream = collectionsDb.getAsStream(key);
            document.readDocumentMeta(istream);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch (IOException e) {
            LOG.warn("IOException while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    public void copyResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName) 
	throws PermissionDeniedException, LockException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        Collection collection = doc.getCollection();
        if (!collection.getPermissions().validate(getUser(), Permission.READ))
            throw new PermissionDeniedException("Insufficient privileges to copy resource " +
						doc.getFileURI());
        if (!doc.getPermissions().validate(getUser(), Permission.READ))
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
                if(!destination.getPermissions().validate(getUser(), Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
							"collection and update is denied");
                if(!oldDoc.getPermissions().validate(getUser(), Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
							"collection and update is denied");
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
                    destination.removeBinaryResource(transaction, this, oldDoc);
                else
                    destination.removeXMLResource(transaction, this, oldDoc.getFileURI());
            } else {
                if(!destination.getPermissions().validate(getUser(), Permission.WRITE))
                    throw new PermissionDeniedException("Insufficient privileges on target collection " +
							destination.getURI());
            }
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE)
            {
                InputStream is = null;
                try
                {
                    is = getBinaryResource((BinaryDocument) doc);
                    destination.addBinaryResource(transaction, this, newName, is, doc.getMetadata().getMimeType(),-1);
                }
                finally
                {
                    if(is != null)
                        is.close();
                }
            }
            else
            {
                DocumentImpl newDoc = new DocumentImpl(pool, destination, newName);
                newDoc.copyOf(doc);
                newDoc.setDocId(getNextResourceId(transaction, destination));
                newDoc.setPermissions(doc.getPermissions());
                newDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                try {
	                copyXMLResource(transaction, doc, newDoc);
	                destination.addDocument(transaction, this, newDoc);
	                storeXMLResource(transaction, newDoc);
                } finally {
               	 newDoc.getUpdateLock().release(Lock.WRITE_LOCK);
                }
            }
	    //          saveCollection(destination);
        } catch (EXistException e) {
            LOG.warn("An error occurred while copying resource", e);
        } catch (IOException e) {
            LOG.warn("An error occurred while copying resource", e);
        } catch (TriggerException e) {
            throw new PermissionDeniedException(e.getMessage());
        } finally {
            lock.release(Lock.WRITE_LOCK);
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
    public void moveResource(Txn transaction, DocumentImpl doc, Collection destination, XmldbURI newName)
	throws PermissionDeniedException, LockException, IOException {
        
        /* Copy reference to original document */
        File originalDocument = getCollectionFile(fsDir,doc.getURI(),true);
        
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        //TODO : somewhat inconsistent (READ is enough for original doc whereas WRITE is mandatory for destination) -pb
        Collection collection = doc.getCollection();                
        if(!collection.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move resource " +
						doc.getFileURI());
        if(!doc.getPermissions().validate(getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move resource " +
						doc.getFileURI());
      
        User docUser = doc.getUserLock();
        if (docUser != null) {
        	if(!(getUser().getName()).equals(docUser.getName()))
                throw new PermissionDeniedException("Cannot move '" + doc.getFileURI() + 
						    " because is locked by getUser() '" + docUser.getName() + "'");
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
                if (!destination.getPermissions().validate(getUser(), Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
							"collection and update is denied");
                if (!oldDoc.getPermissions().validate(getUser(), Permission.UPDATE))
                    throw new PermissionDeniedException("Resource with same name exists in target " +
							"collection and update is denied");
                if (oldDoc.getResourceType() == DocumentImpl.BINARY_FILE)
                    destination.removeBinaryResource(transaction, this, oldDoc);
                else
                    destination.removeXMLResource(transaction, this, oldDoc.getFileURI());
            } else
                if (!destination.getPermissions().validate(getUser(), Permission.WRITE))
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
                
                File colDir = getCollectionFile(fsDir,destination.getURI(),true);
                File binFile = new File(colDir,newName.lastSegment().toString());
                File sourceFile = getCollectionFile(fsDir,doc.getURI(),false);
                
                /* Create required directories */
                binFile.getParentFile().mkdirs();
                
                /* Rename original file to new location */
                if (originalDocument.renameTo(binFile)) {
                   Loggable loggable = new RenameBinaryLoggable(this,transaction,sourceFile,binFile);
                   try {
                      logManager.writeToLog(loggable);
                   } catch (TransactionException e) {
                      LOG.warn(e.getMessage(), e);
                   }
                } else {
                   LOG.fatal("Cannot rename "+sourceFile+" to "+binFile+" for journaling of binary resource move.");
                }
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
                LOG.info("Removing document " + document.getFileURI() + 
                		" (" + document.getDocId() + ") ...");
            }

            dropIndex(transaction, document);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
            if (!document.getMetadata().isReferenced()) {
                new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                    public Object start() {
                        StoredNode node = (StoredNode)document.getFirstChild();
                        domDb.removeAll(transaction, node.getInternalAddress());
                        return null;
                    }
                }
                .run();
            }

            NodeRef ref = new NodeRef(document.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
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
    	indexController.setDocument(document, StreamListener.REMOVE_ALL_NODES);
        StreamListener listener = indexController.getStreamListener();
        NodeList nodes = document.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            StoredNode node = (StoredNode) nodes.item(i);
            Iterator iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), NodeProcessor.MODE_REMOVE, listener);
        }
        notifyDropIndex(document);
        indexController.flush();
    }

    public void removeBinaryResource(final Txn transaction, final BinaryDocument blob)
	throws PermissionDeniedException,IOException
    {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        LOG.info("removing binary resource " + blob.getDocId() + "...");
        File binFile = getCollectionFile(fsDir,blob.getURI(),false);
        if (binFile.exists()) {
           File binBackupFile = getCollectionFile(fsBackupDir,transaction,blob.getURI(),true);
           Loggable loggable = new RenameBinaryLoggable(this,transaction,binFile,binBackupFile);
           if (!binFile.renameTo(binBackupFile)) {
              throw new IOException("Cannot move file "+binFile+" for delete journal to "+binBackupFile);
           }
           try {
              logManager.writeToLog(loggable);
           } catch (TransactionException e) {
               LOG.warn(e.getMessage(), e);
           }
        }
        /*
        if (blob.getPage() != Page.NO_PAGE) {
		    new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
				public Object start() throws ReadOnlyException {
				    domDb.removeOverflowValue(transaction, blob.getPage());
				    return null;
				}
		    }
	        .run();
        }
         */
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
            lock.acquire(Lock.READ_LOCK);
            if (LOG.isDebugEnabled())
                LOG.debug("Removing resource metadata for " + document.getDocId());
            Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
        } catch (ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } finally {
            lock.release(Lock.READ_LOCK);
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
		    lock.release(Lock.WRITE_LOCK);
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
			//LOG.debug("reusing document id: " + freeDocId);
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
		    lock.release(Lock.WRITE_LOCK);
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
		    lock.release(Lock.WRITE_LOCK);
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
        indexController.setDocument(doc, StreamListener.STORE);
        StreamListener listener = indexController.getStreamListener();
        NodeList nodes = doc.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
	    StoredNode node = (StoredNode) nodes.item(i);
	    Iterator iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), mode, listener);
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
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
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
            DocumentImpl tempDoc = new DocumentImpl(pool, doc.getCollection(), doc.getFileURI());
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
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
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
        Object property = pool.getConfiguration().getProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS);
        if (property != null)
	        xupdateConsistencyChecks = ((Boolean) property).booleanValue();
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
        Object property = pool.getConfiguration().getProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS);
        if (property != null)
	        xupdateConsistencyChecks = ((Boolean) property).booleanValue();
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
                StringBuilder buf = new StringBuilder();
//                Pass buf to the following method to get a dump of all node ids in the document
                if (!checkNodeTree(iterator, node, buf)) {
                    LOG.debug("node tree: " + buf.toString());
                    throw new RuntimeException("Error in document tree structure");
                }
            }
            NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, Lock.READ_LOCK) {
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
    public void storeNode(final Txn transaction, final StoredNode node, NodePath currentPath, IndexSpec indexSpec, boolean fullTextIndex) {
        checkAvailableMemory();
        final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
        final short nodeType = node.getNodeType();
        final byte data[] = node.serialize();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            public Object start() throws ReadOnlyException {
                long address;
                if (nodeType == Node.TEXT_NODE
                    || nodeType == Node.ATTRIBUTE_NODE
                    || nodeType == Node.CDATA_SECTION_NODE
                    || node.getNodeId().getTreeLevel() > defaultIndexDepth)
                    address = domDb.add(transaction, data);
                else {
                    address = domDb.put(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                }
                if (address == BFile.UNKNOWN_ADDRESS)
                    LOG.warn("address is missing");
                //TODO : how can we continue here ? -pb
                node.setInternalAddress(address);
                return null;
            }
        }
	    .run();
        ++nodesCount;
        ByteArrayPool.releaseByteArray(data);
        
        nodeProcessor.reset(transaction, node, currentPath, indexSpec, fullTextIndex);
        nodeProcessor.doIndex();
    }
    
    public void updateNode(final Txn transaction, final StoredNode node, boolean reindex) {
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
	//        if (reindex) {
	//            StreamListener listener = indexController.getStreamListener(node.getDocument(), StreamListener.STORE);
	//            IndexUtils.scanNode(transaction, node, listener);
	//        }
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
        storeNode(transaction, node, currentPath, null, index);
        if (defrag && oldNodeId != null)
            pool.getNotificationService().notifyMove(oldNodeId, node);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            //save old value, whatever it is
            long address = node.getInternalAddress();
            node.setInternalAddress(oldAddress);
            endElement(node, currentPath, null);
            //restore old value, whatever it was
            node.setInternalAddress(address);
            node.setDirty(false);
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
     * removed. See {@link #endRemove(org.exist.storage.txn.Txn)}.
     * removeNode() just adds the node ids to the list in elementIndex 
     * for later removal.
     */
    public void removeNode(final Txn transaction, final StoredNode node, NodePath currentPath, String content) {
        final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();

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
	    
	    notifyRemoveNode(node, currentPath, content);
        
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
		valueIndex.storeElement((ElementImpl) node, content, spec1.getType(), NativeValueIndex.IDX_GENERIC, false);
	    }
	    QNameRangeIndexSpec qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
	    if (qnSpec != null) {
		valueIndex.setDocument(doc);
		valueIndex.storeElement((ElementImpl) node, content, qnSpec.getType(),
					NativeValueIndex.IDX_QNAME, false);
	    }
	    break;
                
	case Node.ATTRIBUTE_NODE :
	    qname = node.getQName();
	    qname.setNameType(ElementValue.ATTRIBUTE);
	    currentPath.addComponent(qname);
                
	    elementIndex.setDocument(doc);
	    elementIndex.addNode(qname, p);
                
	    //Strange : does it mean that the node is added 2 times under 2 different identities ?

	    AttrImpl attr;
		attr = (AttrImpl) node;
		 switch(attr.getType()) {
			 case AttrImpl.ID:
				 valueIndex.setDocument(doc);
				 valueIndex.storeAttribute(attr, attr.getValue(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.ID, NativeValueIndex.IDX_GENERIC, false);
				 break;
			 case AttrImpl.IDREF:
				 valueIndex.setDocument(doc);
				 valueIndex.storeAttribute(attr, attr.getValue(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, false);
				 break;
			 case AttrImpl.IDREFS:
				 valueIndex.setDocument(doc);
				 StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
				 while (tokenizer.hasMoreTokens()) {
					 valueIndex.storeAttribute(attr, tokenizer.nextToken(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, false);
				 }
				 break;
			 default:
				 // do nothing special
		 }

		RangeIndexSpec spec2 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
	    if(spec2 != null) {
		valueIndex.setDocument(doc);
		valueIndex.storeAttribute(attr, null, NativeValueIndex.WITHOUT_PATH, spec2, false);
	    }                   
	    qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
	    if (qnSpec != null) {
		valueIndex.setDocument(doc);
		valueIndex.storeAttribute(attr, null, NativeValueIndex.WITHOUT_PATH, qnSpec, false);
	    }
                
	    currentPath.removeLastComponent();
	    break;
	case Node.TEXT_NODE :
	    break;
        }
    }

    public void removeAllNodes(Txn transaction, StoredNode node, NodePath currentPath, StreamListener listener) {
        Iterator iterator = getNodeIterator(node);
        iterator.next();
        Stack stack = new Stack();
        collectNodesForRemoval(transaction, stack, iterator, listener, node, currentPath);
        while (!stack.isEmpty()) {
	    RemovedNode next = (RemovedNode) stack.pop();
            removeNode(transaction, next.node, next.path, next.content);
        }
    }
    
    private void collectNodesForRemoval(Txn transaction, Stack stack, Iterator iterator, StreamListener listener, StoredNode node,
                                        NodePath currentPath) {
        RemovedNode removed;
        switch (node.getNodeType()) {
	case Node.ELEMENT_NODE:
	    DocumentImpl doc = node.getDocument();
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

	    if (listener != null) {
		listener.startElement(transaction, (ElementImpl) node, currentPath);
	    }
	    if (node.hasChildNodes()) {
		int childCount = node.getChildCount();
		for (int i = 0; i < childCount; i++) {
		    StoredNode child = (StoredNode) iterator.next();
		    if (child.getNodeType() == Node.ELEMENT_NODE)
			currentPath.addComponent(child.getQName());
		    collectNodesForRemoval(transaction, stack, iterator, listener, child, currentPath);
		    if (child.getNodeType() == Node.ELEMENT_NODE)
			currentPath.removeLastComponent();
		}
	    }
	    if (listener != null) {
		listener.endElement(transaction, (ElementImpl) node, currentPath);
	    }
	    break;
	case Node.TEXT_NODE :
	    if (listener != null) {
		listener.characters(transaction, (TextImpl) node, currentPath);
	    }
	    break;
	case Node.ATTRIBUTE_NODE :
	    if (listener != null) {
		listener.attribute(transaction, (AttrImpl) node, currentPath);
	    }
	    break;
        }
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            removed = new RemovedNode(node, new NodePath(currentPath), null);
            stack.push(removed);
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
        nodeProcessor.reset(transaction, node, currentPath, null, true);
        nodeProcessor.setMode(repairMode);
        nodeProcessor.index();
    }
    
    private boolean checkNodeTree(Iterator iterator, StoredNode node, StringBuilder buf) {
        if (buf != null) {
            if (buf.length() > 0)
                buf.append(", ");
            buf.append(node.getNodeId());
        }
        boolean docIsValid = true;
        if (node.hasChildNodes()) {
            int count = node.getChildCount();
            if (buf != null)
                buf.append('[').append(count).append(']');
            StoredNode previous = null;
            for (int i = 0; i < count; i++) {
                StoredNode child = (StoredNode) iterator.next();
                if (i > 0 && !(child.getNodeId().isSiblingOf(previous.getNodeId()) &&
                        child.getNodeId().compareTo(previous.getNodeId()) > 0)) {
                    LOG.fatal("node " + child.getNodeId() + " cannot be a sibling of " + previous.getNodeId() +
                        "; node read from " + StorageAddress.toString(child.getInternalAddress()));
                    docIsValid = false;
                }
                previous = child;
                
                if(child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
                    ": " + node.getNodeId() + "; children = " + node.getChildCount());
                    docIsValid = false;
                }
                NodeId parentId = child.getNodeId().getParentId();
                if (!parentId.equals(node.getNodeId())) {
                    LOG.fatal(child.getNodeId() + " is not a child of " + node.getNodeId());
                    docIsValid = false;
                }
                boolean check = checkNodeTree(iterator, child, buf);
                if (docIsValid)
                    docIsValid = check;
            }
        }
        return docIsValid;
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
			   int mode, StreamListener listener) {
        if (node.getNodeType() == Node.ELEMENT_NODE)
            currentPath.addComponent(node.getQName());
        indexNode(transaction, node, currentPath, mode);
        if (listener != null) {
            switch (node.getNodeType()) {
                case Node.TEXT_NODE :
                    listener.characters(transaction, (TextImpl) node, currentPath);
                    break;
                case Node.ELEMENT_NODE :
                    listener.startElement(transaction, (ElementImpl) node, currentPath);
                    break;
                case Node.ATTRIBUTE_NODE :
                    listener.attribute(transaction, (AttrImpl) node, currentPath);
                    break;
                case Node.COMMENT_NODE :
                case Node.PROCESSING_INSTRUCTION_NODE :
                    break;
                default :
                    LOG.debug("Unhandled node type: " + node.getNodeType());
            }
        }
        if (node.hasChildNodes()) {
            final int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
            	StoredNode child = (StoredNode) iterator.next();
                if (child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
			      "; children = " + node.getChildCount());
                    throw new IllegalStateException("Wrong node id");
                }
                scanNodes(transaction, iterator, child, currentPath, mode, listener);
            }
            if (mode == NodeProcessor.MODE_REPAIR)
                pool.signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            endElement(node, currentPath, null, mode == NodeProcessor.MODE_REMOVE);
            if (listener != null)
                listener.endElement(transaction, (ElementImpl) node, currentPath);
            currentPath.removeLastComponent();
        }
    }

    public String getNodeValue(final StoredNode node, final boolean addWhitespace) {
	return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
		public Object start() {
		    return domDb.getNodeValue(NativeBroker.this, node, addWhitespace);
		}
	    }
	    .run();
    }
    
    public StoredNode objectWith(final Document doc, final NodeId nodeId) {    
	return (StoredNode) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
		public Object start() {
		    Value val = domDb.get(NativeBroker.this, new NodeProxy((DocumentImpl) doc, nodeId));
		    if (val == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Node " + nodeId + " not found");
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
            return objectWith(p.getDocument(), p.getNodeId());
        return (StoredNode) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
            public Object start() {
                // DocumentImpl sets the nodeId to DOCUMENT_NODE when it's trying to find its top-level
                // children (for which it doesn't persist the actual node ids), so ignore that.  Nobody else
                // should be passing DOCUMENT_NODE into here.
                boolean fakeNodeId = p.getNodeId().equals(NodeId.DOCUMENT_NODE);
                Value val = domDb.get(p.getInternalAddress(), false);
                if (val == null) {
                    LOG.debug("Node " + p.getNodeId() + " not found in document " + p.getDocument().getURI() +
                            "; docId = " + p.getDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()));
                    //					LOG.debug(domDb.debugPages(p.doc, true));
                    //					return null;
                    if (fakeNodeId) return null;
                } else {
                    StoredNode node = StoredNode.deserialize(val.getData(), 0, val.getLength(), p.getDocument());
                    node.setOwnerDocument((DocumentImpl)p.getOwnerDocument());
                    node.setInternalAddress(p.getInternalAddress());
                    if (fakeNodeId) return node;
                    if (p.getDocument().getDocId() == node.getDocId() && p.getNodeId().equals(node.getNodeId())) {
                        return node;
                    } else {
                        LOG.debug(
                                "Node " + p.getNodeId() + " not found in document " + p.getDocument().getURI() +
                                        "; docId = " + p.getDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()) +
                                        "; found node " + node.getNodeId() + " instead"
                        );
                    }
                }
                // retry based on nodeid
                StoredNode node = objectWith(p.getDocument(), p.getNodeId());
                if (node != null) p.setInternalAddress(node.getInternalAddress());  // update proxy with correct address
                return node;
            }
        }
                .run();
    }
    
    public void repair() throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        
        LOG.info("Removing index files ..."); 
        notifyCloseAndRemove();
        try {
            pool.getIndexManager().removeIndexes();
        } catch (DBException e) {
            LOG.warn("Failed to remove index failes during repair: " + e.getMessage(), e);
        }

        LOG.info("Recreating index files ...");
        try {
	    elementIndex = new NativeElementIndex(this, ELEMENTS_DBX_ID, dataDir, config);        	
	    valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
        } catch (DBException e) {
            LOG.warn("Exception during repair: " + e.getMessage(), e);
        }

        try {
            pool.getIndexManager().reopenIndexes();
        } catch (DatabaseConfigurationException e) {
            LOG.warn("Failed to reopen index files after repair: " + e.getMessage(), e);
        }
        initIndexModules();
        LOG.info("Reindexing database files ...");
        //Reindex from root collection
        reindexCollection(null, getCollection(XmldbURI.ROOT_COLLECTION_URI), NodeProcessor.MODE_REPAIR);
    }
    
    public void flush() {
        notifyFlush();
        try {
	        pool.getSymbols().flush();
        } catch (EXistException e) {
            LOG.warn(e);
        }
        indexController.flush();
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
            }.run();
            if(syncEvent == Sync.MAJOR_SYNC) {
                Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    collectionsDb.flush();
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }
                notifySync();
                pool.getIndexManager().sync();
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
	    LOG.warn(e.getMessage(), e);
	}
        super.shutdown();
    }    

    /** check available memory */
    public void checkAvailableMemory() {
        if (nodesCountThreshold <= 0) {
            if (nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
                if (run.totalMemory() >= run.maxMemory() && run.freeMemory() < pool.getReservedMem()) {
                    NumberFormat nf = NumberFormat.getNumberInstance();
                    LOG.info("total memory: " + nf.format(run.totalMemory()) +
                            "; max: " + nf.format(run.maxMemory()) +
                            "; free: " + nf.format(run.freeMemory()) +
                            "; reserved: " + nf.format(pool.getReservedMem()) +
                            "; used: " + nf.format(pool.getCacheManager().getSizeInBytes()));
                    flush();
                    System.gc();
                }
                nodesCount = 0;
            }
        } else if (nodesCount > nodesCountThreshold) {
            flush();
            nodesCount = 0;
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
        
        public void reset(Txn transaction, StoredNode node, NodePath currentPath, IndexSpec indexSpec, boolean fullTextIndex) {
            if (node.getNodeId() == null)
                LOG.warn("illegal node: " + node.getNodeName());
            //TODO : why continue processing ? return ? -pb
            this.transaction = transaction;
            this.node = node;
            this.currentPath = currentPath;
            this.mode = MODE_STORE;

            doc = (DocumentImpl) node.getOwnerDocument();
            address = node.getInternalAddress();

            if (indexSpec == null)
                indexSpec = doc.getCollection().getIndexConfiguration(NativeBroker.this);
            idxSpec = indexSpec;
            ftIdx = idxSpec == null ? null : idxSpec.getFulltextIndexSpec();
            level = node.getNodeId().getTreeLevel();
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
                    if (idxSpec != null) {
                        QNameRangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                        if (qnIdx != null) {
                            indexType |= RangeIndexSpec.QNAME_INDEX;
                            if (!RangeIndexSpec.hasRangeIndex(indexType))
                                indexType |= qnIdx.getIndexType();
                        }
                    }
                    ((ElementImpl) node).setIndexType(indexType);
                    //	                    notifyStartElement((ElementImpl)node, currentPath, fullTextIndex);

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
                    if (idxSpec != null) {
                        RangeIndexSpec rangeSpec = idxSpec.getIndexByPath(currentPath);
                        if (rangeSpec != null) {
                            indexType |= rangeSpec.getIndexType();
                        }
                        if (rangeSpec != null) {
                            valueIndex.setDocument((DocumentImpl)node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath, NativeValueIndex.WITHOUT_PATH,
                                    rangeSpec, mode == MODE_REMOVE);
                        }
                        QNameRangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                        if (qnIdx != null) {
                            indexType |= RangeIndexSpec.QNAME_INDEX;
                            if (!RangeIndexSpec.hasRangeIndex(indexType))
                                indexType |= qnIdx.getIndexType();
                            valueIndex.setDocument((DocumentImpl)node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath, NativeValueIndex.WITHOUT_PATH,
                                    qnIdx, mode == MODE_REMOVE);
                        }
                    }

                    //notifyStoreAttribute((AttrImpl)node, currentPath, NativeValueIndex.WITH_PATH, null);

                    elementIndex.setDocument(doc);
                    final NodeProxy tempProxy = new NodeProxy(doc, node.getNodeId(), address);
                    tempProxy.setIndexType(indexType);

                    qname.setNameType(ElementValue.ATTRIBUTE);
                    if (mode != MODE_REMOVE)
                        elementIndex.addNode(qname, tempProxy);

                    AttrImpl attr = (AttrImpl) node;
						 switch(attr.getType()) {
							 case AttrImpl.ID:
								 valueIndex.setDocument(doc);
								 valueIndex.storeAttribute(attr, attr.getValue(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.ID, NativeValueIndex.IDX_GENERIC, mode == MODE_REMOVE);
								 break;
							 case AttrImpl.IDREF:
								 valueIndex.setDocument(doc);
								 valueIndex.storeAttribute(attr, attr.getValue(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, mode == MODE_REMOVE);
								 break;
							 case AttrImpl.IDREFS:
								 valueIndex.setDocument(doc);
								 StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
								 while (tokenizer.hasMoreTokens()) {
									 valueIndex.storeAttribute(attr, tokenizer.nextToken(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, mode == MODE_REMOVE);
								 }
								 break;
							 default:
								 // do nothing special
						 }

                    if (currentPath != null)
                        currentPath.removeLastComponent();
                    break;
                }

                case Node.TEXT_NODE:

                    notifyStoreText( (TextImpl)node, currentPath,
                            fullTextIndex ? NativeTextEngine.DO_NOT_TOKENIZE : NativeTextEngine.TOKENIZE);
                    break;
            }
        }

        /** Stores this node into the database, if it's an element */
        public void store() {
            final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
            if (mode == MODE_STORE && node.getNodeType() == Node.ELEMENT_NODE && level <= defaultIndexDepth) {
                //TODO : used to be this, but NativeBroker.this avoids an owner change
                new DOMTransaction(NativeBroker.this, domDb, Lock.WRITE_LOCK) {
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
            if (mode != MODE_REMOVE && nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
                if (run.totalMemory() >= run.maxMemory() && run.freeMemory() < pool.getReservedMem()) {
                    //LOG.info(
                    //  "total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                    flush();
                    System.gc();
                    LOG.info("total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                }
                nodesCount = 0;
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
                    doc = new BinaryDocument(pool, collection);
                else
                    doc = new DocumentImpl(pool, collection);
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
