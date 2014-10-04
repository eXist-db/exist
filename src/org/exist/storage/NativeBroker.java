/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist-db Project
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
 */
package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection;
import org.exist.collections.Collection.CollectionEntry;
import org.exist.collections.Collection.SubCollectionEntry;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.*;
import org.exist.dom.*;
import org.exist.fulltext.FTIndex;
import org.exist.fulltext.FTIndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StructuralIndex;
import org.exist.memtree.DOMIndexer;
import org.exist.numbering.NodeId;
import org.exist.security.*;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.btree.*;
import org.exist.storage.btree.Paged.Page;
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
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main class for the native XML storage backend.
 * By "native" it is meant file-based, embedded backend.
 *
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 *
 * Observer Design Pattern: role : this class is the subject (alias observable)
 * for various classes that generate indices for the database content :
 *
 * @author Wolfgang Meier
 * @link org.exist.storage.NativeElementIndex
 * @link org.exist.storage.NativeTextEngine
 * @link org.exist.storage.NativeValueIndex
 * @link org.exist.storage.NativeValueIndexByQName
 *
 * This class dispatches the various events (defined by the methods
 * of @link org.exist.storage.ContentLoadingObserver) to indexing classes.
 */
public class NativeBroker extends DBBroker {

    public final static String EXIST_STATISTICS_LOGGER = "org.exist.statistics";

    protected final static Logger LOG_STATS = Logger.getLogger(EXIST_STATISTICS_LOGGER);

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
    public static final byte VALUES_DBX_ID = 2;
    public static final byte DOM_DBX_ID = 3;
    //Note : no ID for symbols ? Too bad...

    public static final String PAGE_SIZE_ATTRIBUTE = "pageSize";
    public static final String INDEX_DEPTH_ATTRIBUTE = "index-depth";

    public static final String PROPERTY_INDEX_DEPTH = "indexer.index-depth";
    private static final byte[] ALL_STORAGE_FILES = {
        COLLECTIONS_DBX_ID, VALUES_DBX_ID, DOM_DBX_ID
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

    public final static String INIT_COLLECTION_CONFIG = "collection.xconf.init";

    /** in-memory buffer size to use when copying binary resources */
    private final static int BINARY_RESOURCE_BUF_SIZE = 65536;

    /** the database files */
    protected CollectionStore collectionsDb;
    protected DOMFile domDb;

    /** the index processors */
    protected NativeValueIndex valueIndex;

    protected IndexSpec indexConfiguration;

    protected int defaultIndexDepth;

    protected Serializer xmlSerializer;

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

    protected boolean incrementalDocIds = false;

    /** initialize database; read configuration, etc. */
    public NativeBroker(final BrokerPool pool, final Configuration config) throws EXistException {
        super(pool, config);
        this.logManager = pool.getTransactionManager().getJournal();
        LOG.debug("Initializing broker " + hashCode());

        final String prependDB = (String) config.getProperty("db-connection.prepend-db");
        if("always".equalsIgnoreCase(prependDB)) {
            prepend = PREPEND_DB_ALWAYS;
        } else if("never".equalsIgnoreCase(prependDB)) {
            prepend = PREPEND_DB_NEVER;
        } else {
            prepend = PREPEND_DB_AS_NEEDED;
        }

        dataDir = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if(dataDir == null) {
            dataDir = DEFAULT_DATA_DIR;
        }

        fsDir = new File(new File(dataDir), "fs");
        if(!fsDir.exists()) {
            if(!fsDir.mkdir()) {
                throw new EXistException("Cannot make collection filesystem directory: " + fsDir);
            }
        }
        fsBackupDir = new File(new File(dataDir), "fs.journal");
        if(!fsBackupDir.exists()) {
            if(!fsBackupDir.mkdir()) {
                throw new EXistException("Cannot make collection filesystem directory: " + fsBackupDir);
            }
        }

        nodesCountThreshold = config.getInteger(BrokerPool.PROPERTY_NODES_BUFFER);
        if(nodesCountThreshold > 0) {
            nodesCountThreshold = nodesCountThreshold * 1000;
        }

        defaultIndexDepth = config.getInteger(PROPERTY_INDEX_DEPTH);
        if(defaultIndexDepth < 0) {
            defaultIndexDepth = DEFAULT_INDEX_DEPTH;
        }

        final String docIdProp = (String) config.getProperty(BrokerPool.DOC_ID_MODE_PROPERTY);
        if(docIdProp != null) {
            incrementalDocIds = docIdProp.equalsIgnoreCase("incremental");
        }

        indexConfiguration = (IndexSpec) config.getProperty(Indexer.PROPERTY_INDEXER_CONFIG);
        xmlSerializer = new NativeSerializer(this, config);
        setSubject(pool.getSecurityManager().getSystemSubject());

        try {
            //TODO : refactor so that we can,
            //1) customize the different properties (file names, cache settings...)
            //2) have a consistent READ-ONLY behaviour (based on *mandatory* files ?)
            //3) have consistent file creation behaviour (we can probably avoid some unnecessary files)
            //4) use... *customized* factories for a better index extensibility ;-)
            // Initialize DOM storage
            domDb = (DOMFile) config.getProperty(DOMFile.getConfigKeyForFile());
            if(domDb == null) {
                domDb = new DOMFile(pool, DOM_DBX_ID, dataDir, config);
            }
            if(domDb.isReadOnly()) {
                LOG.warn(domDb.getFile().getName() + " is read-only!");
                pool.setReadOnly();
            }

            //Initialize collections storage
            collectionsDb = (CollectionStore) config.getProperty(CollectionStore.getConfigKeyForFile());
            if(collectionsDb == null) {
                collectionsDb = new CollectionStore(pool, COLLECTIONS_DBX_ID, dataDir, config);
            }
            if(collectionsDb.isReadOnly()) {
                LOG.warn(collectionsDb.getFile().getName() + " is read-only!");
                pool.setReadOnly();
            }

            valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
            if(pool.isReadOnly()) {
                LOG.info("Database runs in read-only mode");
            }
        } catch(final DBException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e);
        }
    }

    @Override
    public ElementIndex getElementIndex() {
        return null;
    }

    @Override
    public synchronized void addObserver(final Observer o) {
        super.addObserver(o);
        //textEngine.addObserver(o);
        //elementIndex.addObserver(o);
        //TODO : what about other indexes observers ?
    }

    @Override
    public synchronized void deleteObservers() {
        super.deleteObservers();
        //if (elementIndex != null)
        //elementIndex.deleteObservers();
        //TODO : what about other indexes observers ?
        //if (textEngine != null)
        //textEngine.deleteObservers();
    }

    // ============ dispatch the various events to indexing classes ==========

    private void notifyRemoveNode(final StoredNode node, final NodePath currentPath, final String content) {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.removeNode(node, currentPath, content);
        }
    }

    //private void notifyStoreAttribute(AttrImpl attr, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove) {
    //    for (int i = 0; i < contentLoadingObservers.size(); i++) {
    //        ContentLoadingObserver observer = (ContentLoadingObserver) contentLoadingObservers.get(i);
    //        observer.storeAttribute(attr, currentPath, indexingHint, spec, remove);
    //    }	
    //}	

    private void notifyStoreText(final TextImpl text, final NodePath currentPath, final int indexingHint) {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.storeText(text, currentPath, indexingHint);
        }
    }

    private void notifyDropIndex(final Collection collection) {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.dropIndex(collection);
        }
    }

    private void notifyDropIndex(final DocumentImpl doc) throws ReadOnlyException {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.dropIndex(doc);
        }
    }

    private void notifyRemove() {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.remove();
        }
    }

    private void notifySync() {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.sync();
        }
    }

    private void notifyFlush() {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            try {
                observer.flush();
            } catch(final DBException e) {
                LOG.warn(e);
                //Ignore the exception ; try to continue on other files
            }
        }
    }

    private void notifyPrintStatistics() {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.printStatistics();
        }
    }

    private void notifyClose() throws DBException {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.close();
        }
        clearContentLoadingObservers();
    }

    private void notifyCloseAndRemove() {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.closeAndRemove();
        }
        clearContentLoadingObservers();
    }

    /**
     * Update indexes for the given element node. This method is called when the indexer
     * encounters a closing element tag. It updates any range indexes defined on the
     * element value and adds the element id to the structural index.
     *
     * @param node        the current element node
     * @param currentPath node path leading to the element
     * @param content     contains the string value of the element. Needed if a range index
     *                    is defined on it.
     */
    @Override
    public void endElement(final StoredNode node, final NodePath currentPath, String content, final boolean remove) {
        final int indexType = ((ElementImpl) node).getIndexType();
        //TODO : do not care about the current code redundancy : this will move in the (near) future
        // TODO : move to NativeValueIndex
        if(RangeIndexSpec.hasRangeIndex(indexType)) {
            node.getQName().setNameType(ElementValue.ELEMENT);
            if(content == null) {
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
        if(RangeIndexSpec.hasQNameIndex(indexType)) {
            node.getQName().setNameType(ElementValue.ELEMENT);
            if(content == null) {
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
            //qnameValueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
            //qnameValueIndex.endElement((ElementImpl) node, currentPath, content);
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
      }
      */

    /**
     * Takes care of actually removing entries from the indices;
     * must be called after one or more call to {@link #removeNode(Txn, StoredNode, NodePath, String)}.
     */
    @Override
    public void endRemove(final Txn transaction) {
        notifyRemove();
    }

    @Override
    public boolean isReadOnly() {
        return pool.isReadOnly();
    }

    public DOMFile getDOMFile() {
        return domDb;
    }

    public BTree getStorage(final byte id) {
        //Notice that there is no entry for the symbols table
        switch(id) {
            case DOM_DBX_ID:
                return domDb;
            case COLLECTIONS_DBX_ID:
                return collectionsDb;
            case VALUES_DBX_ID:
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

    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException, EXistException {
        for(final byte i : ALL_STORAGE_FILES) {
            final Paged paged = getStorage(i);
            if(paged == null) {
                LOG.warn("Storage file is null: " + i);
                continue;
            }
            final OutputStream os = backup.newEntry(paged.getFile().getName());
            paged.backupToStream(os);
            backup.closeEntry();
        }
        pool.getSymbols().backupToArchive(backup);
        backupBinary(backup, fsDir, "");
        pool.getIndexManager().backupToArchive(backup);
        //TODO backup counters
        //TODO USE zip64 or tar to create snapshots larger then 4Gb
    }

    private void backupBinary(final RawDataBackup backup, final File file, String path) throws IOException {
        path = path + "/" + file.getName();
        if(file.isDirectory()) {
            for(final File f : file.listFiles()) {
                backupBinary(backup, f, path);
            }
        } else {
            final OutputStream os = backup.newEntry(path);
            final InputStream is = new FileInputStream(file);
            final byte[] buf = new byte[4096];
            int len;
            while((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            is.close();
            backup.closeEntry();
        }
    }

    @Override
    public IndexSpec getIndexConfiguration() {
        return indexConfiguration;
    }

    @Override
    public StructuralIndex getStructuralIndex() {
        return (StructuralIndex) getIndexController().getWorkerByIndexName(StructuralIndex.STRUCTURAL_INDEX_ID);
    }

    @Override
    public NativeValueIndex getValueIndex() {
        return valueIndex;
    }

    @Override
    public TextSearchEngine getTextEngine() {
        final FTIndexWorker worker = (FTIndexWorker) indexController.getWorkerByIndexId(FTIndex.ID);
        if(worker == null) {
            LOG.warn("Fulltext index is not configured. Please check the <modules> section in conf.xml");
            return null;
        }
        return worker.getEngine();
    }

    @Override
    public EmbeddedXMLStreamReader getXMLStreamReader(final NodeHandle node, final boolean reportAttributes)
        throws IOException, XMLStreamException {
        if(streamReader == null) {
            final RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
            streamReader = new EmbeddedXMLStreamReader(this, (DocumentImpl) node.getOwnerDocument(), iterator, node, reportAttributes);
        } else {
            streamReader.reposition(this, node, reportAttributes);
        }
        return streamReader;
    }

    @Override
    public EmbeddedXMLStreamReader newXMLStreamReader(final NodeHandle node, final boolean reportAttributes)
        throws IOException, XMLStreamException {
        final RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
        return new EmbeddedXMLStreamReader(this, (DocumentImpl) node.getOwnerDocument(), iterator, null, reportAttributes);
    }

    @Override
    public Iterator<StoredNode> getNodeIterator(final StoredNode node) {
        if(node == null) {
            throw new IllegalArgumentException("The node parameter cannot be null.");
        }
        try {
            return new NodeIterator(this, domDb, node, false);
        } catch(final BTreeException | IOException e) {
            LOG.warn("failed to create node iterator", e);
        }
        return null;
    }

    @Override
    public Serializer getSerializer() {
        xmlSerializer.reset();
        return xmlSerializer;
    }

    @Override
    public Serializer newSerializer() {
        return new NativeSerializer(this, getConfiguration());
    }

    public XmldbURI prepend(final XmldbURI uri) {
        switch(prepend) {
            case PREPEND_DB_ALWAYS:
                return uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
            case PREPEND_DB_AS_NEEDED:
                return uri.startsWith(XmldbURI.ROOT_COLLECTION_URI) ? uri : uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
            default:
                return uri;
        }
    }

    /**
     * Creates a temporary collection
     *
     * @param transaction The transaction, which registers the acquired write locks.
     *                    The locks should be released on commit/abort.
     * @return The temporary collection
     * @throws LockException
     * @throws PermissionDeniedException
     * @throws IOException
     * @throws TriggerException
     */
    private Collection createTempCollection(final Txn transaction)
        throws LockException, PermissionDeniedException, IOException, TriggerException {
        final Subject u = getSubject();
        try {
            setSubject(pool.getSecurityManager().getSystemSubject());
            final Collection temp = getOrCreateCollection(transaction, XmldbURI.TEMP_COLLECTION_URI);
            temp.setPermissions(0771);
            saveCollection(transaction, temp);
            return temp;
        } finally {
            setSubject(u);
        }
    }

    private final String readInitCollectionConfig() {
        final File fInitCollectionConfig = new File(pool.getConfiguration().getExistHome(), INIT_COLLECTION_CONFIG);
        if(fInitCollectionConfig.exists() && fInitCollectionConfig.isFile()) {

            InputStream is = null;
            try {
                final StringBuilder initCollectionConfig = new StringBuilder();

                is = new FileInputStream(fInitCollectionConfig);
                int read = -1;
                final byte buf[] = new byte[1024];
                while((read = is.read(buf)) != -1) {
                    initCollectionConfig.append(new String(buf, 0, read));
                }

                return initCollectionConfig.toString();
            } catch(final IOException ioe) {
                LOG.error(ioe.getMessage(), ioe);
            } finally {
                if(is != null) {
                    try {
                        is.close();
                    } catch(final IOException ioe) {
                        LOG.warn(ioe.getMessage(), ioe);
                    }
                }
            }

        }

        return null;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.DBBroker#getOrCreateCollection(org.exist.storage.txn.Txn, org.exist.xmldb.XmldbURI)
     */
    @Override
    public Collection getOrCreateCollection(final Txn transaction, XmldbURI name) throws PermissionDeniedException, IOException, TriggerException {

        name = prepend(name.normalizeCollectionPath());

        final CollectionCache collectionsCache = pool.getCollectionsCache();

        synchronized(collectionsCache) {
            try {
                //TODO : resolve URIs !
                final XmldbURI[] segments = name.getPathSegments();
                XmldbURI path = XmldbURI.ROOT_COLLECTION_URI;
                Collection sub;
                Collection current = getCollection(XmldbURI.ROOT_COLLECTION_URI);
                if(current == null) {

                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Creating root collection '" + XmldbURI.ROOT_COLLECTION_URI + "'");
                    }

                    final CollectionTrigger trigger = new CollectionTriggers(this);
                    trigger.beforeCreateCollection(this, transaction, XmldbURI.ROOT_COLLECTION_URI);

                    current = new Collection(this, XmldbURI.ROOT_COLLECTION_URI);
                    current.setId(getNextCollectionId(transaction));
                    current.setCreationTime(System.currentTimeMillis());

                    if(transaction != null) {
                        transaction.acquireLock(current.getLock(), Lock.WRITE_LOCK);
                    }

                    //TODO : acquire lock manually if transaction is null ?
                    saveCollection(transaction, current);

                    trigger.afterCreateCollection(this, transaction, current);

                    //import an initial collection configuration
                    try {
                        final String initCollectionConfig = readInitCollectionConfig();
                        if(initCollectionConfig != null) {
                            CollectionConfigurationManager collectionConfigurationManager = pool.getConfigurationManager();
                            if(collectionConfigurationManager == null) {
                                //might not yet have been initialised
                                pool.initCollectionConfigurationManager(this);
                                collectionConfigurationManager = pool.getConfigurationManager();
                            }

                            if(collectionConfigurationManager != null) {
                                collectionConfigurationManager.addConfiguration(transaction, this, current, initCollectionConfig);
                            }
                        }
                    } catch(final CollectionConfigurationException cce) {
                        LOG.error("Could not load initial collection configuration for /db: " + cce.getMessage(), cce);
                    }
                }

                for(int i = 1; i < segments.length; i++) {
                    final XmldbURI temp = segments[i];
                    path = path.append(temp);
                    if(current.hasSubcollectionNoLock(this, temp)) {
                        current = getCollection(path);
                        if(current == null) {
                            LOG.debug("Collection '" + path + "' not found!");
                        }
                    } else {

                        if(pool.isReadOnly()) {
                            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                        }

                        if(!current.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' not allowed to write to collection '" + current.getURI() + "'");
                        }

                        if(!current.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' not allowed to execute to collection '" + current.getURI() + "'");
                        }

                        if(current.hasDocument(this, path.lastSegment())) {
                            LOG.error("Collection '" + current.getURI() + "' have document '" + path.lastSegment() + "'");
                            throw new PermissionDeniedException("Collection '" + current.getURI() + "' have document '" + path.lastSegment() + "'.");
                        }

                        if(LOG.isDebugEnabled()) {
                            LOG.debug("Creating collection '" + path + "'...");
                        }

                        final CollectionTrigger trigger = new CollectionTriggers(this, current);
                        trigger.beforeCreateCollection(this, transaction, path);

                        sub = new Collection(this, path);
                        //inherit the group to the sub-collection if current collection is setGid
                        if(current.getPermissions().isSetGid()) {
                            sub.getPermissions().setGroupFrom(current.getPermissions()); //inherit group
                            sub.getPermissions().setSetGid(true); //inherit setGid bit
                        }
                        sub.setId(getNextCollectionId(transaction));

                        if(transaction != null) {
                            transaction.acquireLock(sub.getLock(), Lock.WRITE_LOCK);
                        }

                        //TODO : acquire lock manually if transaction is null ?
                        current.addCollection(this, sub, true);
                        saveCollection(transaction, current);

                        trigger.afterCreateCollection(this, transaction, sub);

                        current = sub;
                    }
                }
                return current;
            } catch(final LockException e) {
                LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
                return null;
            } catch(final ReadOnlyException e) {
                throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            }
        }
    }

    @Override
    public Collection getCollection(final XmldbURI uri) throws PermissionDeniedException {
        return openCollection(uri, Lock.NO_LOCK);
    }

    @Override
    public Collection openCollection(final XmldbURI uri, final int lockMode) throws PermissionDeniedException {
        return openCollection(uri, BFile.UNKNOWN_ADDRESS, lockMode);
    }


    @Override
    public List<String> findCollectionsMatching(final String regexp) {

        final List<String> collections = new ArrayList<>();

        final Pattern p = Pattern.compile(regexp);
        final Matcher m = p.matcher("");

        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);

            //TODO write a regexp lookup for key data in BTree.query
            //IndexQuery idxQuery = new IndexQuery(IndexQuery.REGEXP, regexp);
            //List<Value> keys = collectionsDb.findKeysByCollectionName(idxQuery);
            final List<Value> keys = collectionsDb.getKeys();

            for(final Value key : keys) {

                //TODO restrict keys to just collection uri's

                final String collectionName = new String(key.getData());
                m.reset(collectionName);

                if(m.matches()) {
                    collections.add(collectionName);
                }
            }
        } catch(final UnsupportedEncodingException e) {
            //LOG.error("Unable to encode '" + uri + "' in UTF-8");
            //return null;
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
            //return null;
        } catch(final TerminatedException | IOException | BTreeException e) {
            LOG.error(e.getMessage(), e);
            //return null;
        } finally {
            lock.release(Lock.READ_LOCK);
        }

        return collections;
    }

    @Override
    public void readCollectionEntry(final SubCollectionEntry entry) {

        final XmldbURI uri = prepend(entry.getUri().toCollectionPathURI());

        Collection collection;
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            collection = collectionsCache.get(uri);
            if(collection == null) {
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.READ_LOCK);

                    final Value key = new CollectionStore.CollectionKey(uri.toString());
                    final VariableByteInput is = collectionsDb.getAsStream(key);
                    if(is == null) {
                        LOG.warn("Could not read collection entry for: " + uri);
                        return;
                    }

                    //read the entry details
                    entry.read(is);

                } catch(final UnsupportedEncodingException e) {
                    LOG.error("Unable to encode '" + uri + "' in UTF-8");
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
                } catch(final IOException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            } else {

                if(!collection.getURI().equalsInternal(uri)) {
                    LOG.error("The collection received from the cache is not the requested: " + uri +
                        "; received: " + collection.getURI());
                    return;
                }

                entry.read(collection);

                collectionsCache.add(collection);
            }
        }
    }

    /**
     * Get collection object. If the collection does not exist, null is
     * returned.
     *
     * @param uri collection URI
     * @return The collection value
     */
    private Collection openCollection(XmldbURI uri, final long address, final int lockMode) throws PermissionDeniedException {
        uri = prepend(uri.toCollectionPathURI());
        //We *must* declare it here (see below)
        Collection collection;
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            collection = collectionsCache.get(uri);
            if(collection == null) {
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.READ_LOCK);
                    VariableByteInput is;
                    if(address == BFile.UNKNOWN_ADDRESS) {
                        final Value key = new CollectionStore.CollectionKey(uri.toString());
                        is = collectionsDb.getAsStream(key);
                    } else {
                        is = collectionsDb.getAsStream(address);
                    }
                    if(is == null) {
                        return null;
                    }
                    collection = new Collection(this, uri);
                    collection.read(this, is);
                    //TODO : manage this from within the cache -pb
                    if(!pool.isInitializing()) {
                        collectionsCache.add(collection);
                    }
                    //TODO : rethrow exceptions ? -pb
                } catch(final UnsupportedEncodingException e) {
                    LOG.error("Unable to encode '" + uri + "' in UTF-8");
                    return null;
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
                    return null;
                } catch(final IOException e) {
                    LOG.error(e.getMessage(), e);
                    return null;
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            } else {
                if(!collection.getURI().equalsInternal(uri)) {
                    LOG.error("The collection received from the cache is not the requested: " + uri +
                        "; received: " + collection.getURI());
                }
                collectionsCache.add(collection);

                if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
                    throw new PermissionDeniedException("Permission denied to open collection: " + collection.getURI().toString() + " by " + getSubject().getName());
                }
            }
        }

        //Important : 
        //This code must remain outside of the synchronized block
        //because another thread may already own a lock on the collection
        //This would result in a deadlock... until the time-out raises the Exception
        //TODO : make an attempt to an immediate lock ?
        //TODO : manage a collection of requests for locks ?
        //TODO : another yet smarter solution ?
        if(lockMode != Lock.NO_LOCK) {
            try {
                collection.getLock().acquire(lockMode);
            } catch(final LockException e) {
                LOG.warn("Failed to acquire lock on collection '" + uri + "'");
            }
        }
        return collection;
    }

    /**
     * Checks all permissions in the tree to ensure that a copy operation will succeed
     */
    protected void checkPermissionsForCopy(final Collection src, final XmldbURI destUri, final XmldbURI newName) throws PermissionDeniedException, LockException {

        if(!src.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE | Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " by " + getSubject().getName());
        }

        final Collection dest = getCollection(destUri);
        final XmldbURI newDestUri = destUri.append(newName);
        final Collection newDest = getCollection(newDestUri);

        if(dest != null) {
            //if(!dest.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE | Permission.WRITE | Permission.READ)) {
            //TODO do we really need WRITE permission on the dest?
            if(!dest.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE | Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + dest.getURI() + " by " + getSubject().getName());
            }

            if(newDest != null) {
                //TODO why do we need READ access on the dest collection?
                /*if(!dest.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE | Permission.READ)) {
                    throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + dest.getURI() + " by " + getSubject().getName());
                }*/

                //if(newDest.isEmpty(this)) {
                if(!newDest.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE | Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + newDest.getURI() + " by " + getSubject().getName());
                }
                //}
            }
        }

        for(final Iterator<DocumentImpl> itSrcSubDoc = src.iterator(this); itSrcSubDoc.hasNext(); ) {
            final DocumentImpl srcSubDoc = itSrcSubDoc.next();
            if(!srcSubDoc.getPermissions().validate(getSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " for resource " + srcSubDoc.getURI() + " by " + getSubject().getName());
            }

            //if the destination resource exists, we must have write access to replace it's metadata etc. (this follows the Linux convention)
            if(newDest != null && !newDest.isEmpty(this)) {
                final DocumentImpl newDestSubDoc = newDest.getDocument(this, srcSubDoc.getFileURI()); //TODO check this uri is just the filename!
                if(newDestSubDoc != null) {
                    if(!newDestSubDoc.getPermissions().validate(getSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " for resource " + newDestSubDoc.getURI() + " by " + getSubject().getName());
                    }
                }
            }
        }

        for(final Iterator<XmldbURI> itSrcSubColUri = src.collectionIterator(this); itSrcSubColUri.hasNext(); ) {
            final XmldbURI srcSubColUri = itSrcSubColUri.next();
            final Collection srcSubCol = getCollection(src.getURI().append(srcSubColUri));

            checkPermissionsForCopy(srcSubCol, newDestUri, srcSubColUri);
        }
    }


    /* (non-Javadoc)
     * @see org.exist.storage.DBBroker#copyCollection(org.exist.storage.txn.Txn, org.exist.collections.Collection, org.exist.collections.Collection, org.exist.xmldb.XmldbURI)
     */
    @Override
    public void copyCollection(final Txn transaction, final Collection collection, final Collection destination, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        //TODO : resolve URIs !!!
        if(newName != null && newName.numSegments() != 1) {
            throw new PermissionDeniedException("New collection name must have one segment!");
        }

        final XmldbURI srcURI = collection.getURI();
        final XmldbURI dstURI = destination.getURI().append(newName);

        if(collection.getURI().equals(dstURI)) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + collection.getURI() + "'.");
        }
        if(collection.getId() == destination.getId()) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + collection.getURI() + "'.");
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            final Lock lock = collectionsDb.getLock();
            try {
                pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_COPY_COLLECTION, collection.getURI());
                lock.acquire(Lock.WRITE_LOCK);

                final XmldbURI parentName = collection.getParentURI();
                final Collection parent = parentName == null ? collection : getCollection(parentName);

                final CollectionTrigger trigger = new CollectionTriggers(this, parent);
                trigger.beforeCopyCollection(this, transaction, collection, dstURI);

                //atomically check all permissions in the tree to ensure a copy operation will succeed before starting copying
                checkPermissionsForCopy(collection, destination.getURI(), newName);

                final DocumentTrigger docTrigger = new DocumentTriggers(this);

                final Collection newCollection = doCopyCollection(transaction, docTrigger, collection, destination, newName);

                trigger.afterCopyCollection(this, transaction, newCollection, srcURI);
            } finally {
                lock.release(Lock.WRITE_LOCK);
                pool.getProcessMonitor().endJob();
            }
        }
    }

    private Collection doCopyCollection(final Txn transaction, final DocumentTrigger trigger, final Collection collection, final Collection destination, XmldbURI newName) throws PermissionDeniedException, IOException, EXistException, TriggerException, LockException {

        if(newName == null) {
            newName = collection.getURI().lastSegment();
        }
        newName = destination.getURI().append(newName);

        if(LOG.isDebugEnabled()) {
            LOG.debug("Copying collection to '" + newName + "'");
        }

        final Collection destCollection = getOrCreateCollection(transaction, newName);
        for(final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
            final DocumentImpl child = i.next();

            if(LOG.isDebugEnabled()) {
                LOG.debug("Copying resource: '" + child.getURI() + "'");
            }

            //TODO The code below seems quite different to that in NativeBroker#copyResource presumably should be the same?


            final XmldbURI newUri = destCollection.getURI().append(child.getFileURI());
            trigger.beforeCopyDocument(this, transaction, child, newUri);

            //are we overwriting an existing document?
            final CollectionEntry oldDoc;
            if(destCollection.hasDocument(this, child.getFileURI())) {
                oldDoc = destCollection.getResourceEntry(this, child.getFileURI().toString());
            } else {
                oldDoc = null;
            }

            DocumentImpl createdDoc;
            if(child.getResourceType() == DocumentImpl.XML_FILE) {
                //TODO : put a lock on newDoc ?
                final DocumentImpl newDoc = new DocumentImpl(pool, destCollection, child.getFileURI());
                newDoc.copyOf(child, false);
                if(oldDoc != null) {
                    //preserve permissions from existing doc we are replacing
                    newDoc.setPermissions(oldDoc.getPermissions()); //TODO use newDoc.copyOf(oldDoc) ideally, but we cannot currently access oldDoc without READ access to it, which we may not have (and should not need for this)!
                }

                newDoc.setDocId(getNextResourceId(transaction, destination));
                copyXMLResource(transaction, child, newDoc);
                storeXMLResource(transaction, newDoc);
                destCollection.addDocument(transaction, this, newDoc);

                createdDoc = newDoc;
            } else {
                final BinaryDocument newDoc = new BinaryDocument(pool, destCollection, child.getFileURI());
                newDoc.copyOf(child, false);
                if(oldDoc != null) {
                    //preserve permissions from existing doc we are replacing
                    newDoc.setPermissions(oldDoc.getPermissions()); //TODO use newDoc.copyOf(oldDoc) ideally, but we cannot currently access oldDoc without READ access to it, which we may not have (and should not need for this)!
                }
                newDoc.setDocId(getNextResourceId(transaction, destination));

                InputStream is = null;
                try {
                    is = getBinaryResource((BinaryDocument) child);
                    storeBinaryResource(transaction, newDoc, is);
                } finally {
                    if(is != null) {
                        is.close();
                    }
                }
                storeXMLResource(transaction, newDoc);
                destCollection.addDocument(transaction, this, newDoc);

                createdDoc = newDoc;
            }

            trigger.afterCopyDocument(this, transaction, createdDoc, child.getURI());
        }
        saveCollection(transaction, destCollection);

        final XmldbURI name = collection.getURI();
        for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
            final XmldbURI childName = i.next();
            //TODO : resolve URIs ! collection.getURI().resolve(childName)
            final Collection child = openCollection(name.append(childName), Lock.WRITE_LOCK);
            if(child == null) {
                LOG.warn("Child collection '" + childName + "' not found");
            } else {
                try {
                    doCopyCollection(transaction, trigger, child, destCollection, childName);
                } finally {
                    child.release(Lock.WRITE_LOCK);
                }
            }
        }
        saveCollection(transaction, destCollection);
        saveCollection(transaction, destination);

        return destCollection;
    }

    @Override
    public void moveCollection(final Txn transaction, final Collection collection, final Collection destination, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException {

        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        if(newName != null && newName.numSegments() != 1) {
            throw new PermissionDeniedException("New collection name must have one segment!");
        }

        if(collection.getId() == destination.getId()) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + collection.getURI() + "'.");
        }
        if(collection.getURI().equals(destination.getURI().append(newName))) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + collection.getURI() + "'.");
        }
        if(collection.getURI().equals(XmldbURI.ROOT_COLLECTION_URI)) {
            throw new PermissionDeniedException("Cannot move the db root collection");
        }

        final XmldbURI parentName = collection.getParentURI();
        final Collection parent = parentName == null ? collection : getCollection(parentName);
        if(!parent.getPermissionsNoLock().validate(getSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on collection " + parent.getURI() + " to move collection " + collection.getURI());
        }

        if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on collection to move collection " + collection.getURI());
        }

        if(!destination.getPermissionsNoLock().validate(getSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on collection " + parent.getURI() + " to move collection " + collection.getURI());
        }
        
        /*
         * If replacing another collection in the move i.e. /db/col1/A -> /db/col2 (where /db/col2/A exists)
         * we have to make sure the permissions to remove /db/col2/A are okay!
         * 
         * So we must call removeCollection on /db/col2/A
         * Which will ensure that collection can be removed and then remove it.
         */
        final XmldbURI movedToCollectionUri = destination.getURI().append(newName);
        final Collection existingMovedToCollection = getCollection(movedToCollectionUri);
        if(existingMovedToCollection != null) {
            removeCollection(transaction, existingMovedToCollection);
        }

        pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_MOVE_COLLECTION, collection.getURI());

        try {

            final XmldbURI srcURI = collection.getURI();
            final XmldbURI dstURI = destination.getURI().append(newName);

            final CollectionTrigger trigger = new CollectionTriggers(this, parent);
            trigger.beforeMoveCollection(this, transaction, collection, dstURI);

            // sourceDir must be known in advance, because once moveCollectionRecursive
            // is called, both collection and destination can point to the same resource
            final File fsSourceDir = getCollectionFile(fsDir, collection.getURI(), false);

            // Need to move each collection in the source tree individually, so recurse.
            moveCollectionRecursive(transaction, trigger, collection, destination, newName, false);

            // For binary resources, though, just move the top level directory and all descendants come with it.
            moveBinaryFork(transaction, fsSourceDir, destination, newName);

            trigger.afterMoveCollection(this, transaction, collection, srcURI);

        } finally {
            pool.getProcessMonitor().endJob();
        }

    }

    private void moveBinaryFork(final Txn transaction, final File sourceDir, final Collection destination, final XmldbURI newName) throws IOException {
        final File targetDir = getCollectionFile(fsDir, destination.getURI().append(newName), false);
        if(sourceDir.exists()) {
            if(targetDir.exists()) {
                final File targetDelDir = getCollectionFile(fsBackupDir, transaction, destination.getURI().append(newName), true);
                targetDelDir.getParentFile().mkdirs();
                if(targetDir.renameTo(targetDelDir)) {
                    final Loggable loggable = new RenameBinaryLoggable(this, transaction, targetDir, targetDelDir);
                    try {
                        logManager.writeToLog(loggable);
                    } catch(final TransactionException e) {
                        LOG.warn(e.getMessage(), e);
                    }
                } else {
                    LOG.fatal("Cannot rename " + targetDir + " to " + targetDelDir);
                }
            }
            targetDir.getParentFile().mkdirs();
            if(sourceDir.renameTo(targetDir)) {
                final Loggable loggable = new RenameBinaryLoggable(this, transaction, sourceDir, targetDir);
                try {
                    logManager.writeToLog(loggable);
                } catch(final TransactionException e) {
                    LOG.warn(e.getMessage(), e);
                }
            } else {
                LOG.fatal("Cannot move " + sourceDir + " to " + targetDir);
            }
        }
    }

    //TODO bug the trigger param is reused as this is a recursive method, but in the current design triggers
    // are only meant to be called once for each action and then destroyed!
    /**
     * @param transaction
     * @param trigger
     * @param collection
     * @param destination
     * @param newName
     * @param fireTrigger Indicates whether the CollectionTrigger should be fired
     *                    on the collection the first time this function is called.
     *                    Triggers will always be fired for recursive calls of this
     *                    function.
     */
    private void moveCollectionRecursive(final Txn transaction, final CollectionTrigger trigger, final Collection collection, final Collection destination, final XmldbURI newName, final boolean fireTrigger) throws PermissionDeniedException, IOException, LockException, TriggerException {

        final XmldbURI uri = collection.getURI();
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {

            final XmldbURI srcURI = collection.getURI();
            final XmldbURI dstURI = destination.getURI().append(newName);

            if(fireTrigger) {
                trigger.beforeMoveCollection(this, transaction, collection, dstURI);
            }

            final XmldbURI parentName = collection.getParentURI();
            final Collection parent = openCollection(parentName, Lock.WRITE_LOCK);

            if(parent != null) {
                try {
                    //TODO : resolve URIs
                    parent.removeCollection(this, uri.lastSegment());
                } finally {
                    parent.release(Lock.WRITE_LOCK);
                }
            }

            final Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);
                collectionsCache.remove(collection);
                final Value key = new CollectionStore.CollectionKey(uri.toString());
                collectionsDb.remove(transaction, key);
                //TODO : resolve URIs destination.getURI().resolve(newName)
                collection.setPath(destination.getURI().append(newName));
                collection.setCreationTime(System.currentTimeMillis());
                destination.addCollection(this, collection, false);
                if(parent != null) {
                    saveCollection(transaction, parent);
                }
                if(parent != destination) {
                    saveCollection(transaction, destination);
                }
                saveCollection(transaction, collection);
                //} catch (ReadOnlyException e) {
                //throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            } finally {
                lock.release(Lock.WRITE_LOCK);
            }

            if(fireTrigger) {
                trigger.afterMoveCollection(this, transaction, collection, srcURI);
            }

            for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                final XmldbURI childName = i.next();
                //TODO : resolve URIs !!! name.resolve(childName)
                final Collection child = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                if(child == null) {
                    LOG.warn("Child collection " + childName + " not found");
                } else {
                    try {
                        moveCollectionRecursive(transaction, trigger, child, collection, childName, true);
                    } finally {
                        child.release(Lock.WRITE_LOCK);
                    }
                }
            }
        }
    }

    /**
     * Removes a collection and all child collections and resources
     *
     * We first traverse down the Collection tree to ensure that the Permissions
     * enable the Collection Tree to be removed. We then return back up the Collection
     * tree, removing each child as we progresses upwards.
     *
     * @param transaction the transaction to use
     * @param collection  the collection to remove
     * @return true if the collection was removed, false otherwise
     * @throws TriggerException
     */
    @Override
    public boolean removeCollection(final Txn transaction, final Collection collection) throws PermissionDeniedException, IOException, TriggerException {

        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        final XmldbURI parentName = collection.getParentURI();
        final boolean isRoot = parentName == null;
        final Collection parent = isRoot ? collection : getCollection(parentName);

        //parent collection permissions
        if(!parent.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        if(!parent.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        //this collection permissions
        if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        if(!collection.isEmpty(this)) {
            if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Account '" + getSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
            }

            if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
                throw new PermissionDeniedException("Account '" + getSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
            }
        }

        try {

            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_COLLECTION, collection.getURI());

            final CollectionTrigger colTrigger = new CollectionTriggers(this, parent);

            colTrigger.beforeDeleteCollection(this, transaction, collection);

            final long start = System.currentTimeMillis();
            final CollectionCache collectionsCache = pool.getCollectionsCache();

            synchronized(collectionsCache) {
                final XmldbURI uri = collection.getURI();
                final String collName = uri.getRawCollectionPath();

                // Notify the collection configuration manager
                final CollectionConfigurationManager manager = pool.getConfigurationManager();
                if(manager != null) {
                    manager.invalidate(uri, getBrokerPool());
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Removing children collections from their parent '" + collName + "'...");
                }

                for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                    final XmldbURI childName = i.next();
                    //TODO : resolve from collection's base URI
                    //TODO : resolve URIs !!! (uri.resolve(childName))
                    final Collection childCollection = openCollection(uri.append(childName), Lock.WRITE_LOCK);
                    try {
                        removeCollection(transaction, childCollection);
                    } catch(NullPointerException npe) {
                        LOG.error("childCollection '" + childName + "' is corrupted. Caught NPE to be able to actually remove the parent.");
                    } finally {
                        if(childCollection != null) {
                            childCollection.getLock().release(Lock.WRITE_LOCK);
                        } else {
                            LOG.warn("childCollection is null !");
                        }
                    }
                }

                //Drop all index entries
                notifyDropIndex(collection);

                // Drop custom indexes
                indexController.removeCollection(collection, this, false);

                if(!isRoot) {
                    // remove from parent collection
                    //TODO : resolve URIs ! (uri.resolve(".."))
                    final Collection parentCollection = openCollection(collection.getParentURI(), Lock.WRITE_LOCK);
                    // keep the lock for the transaction
                    if(transaction != null) {
                        transaction.registerLock(parentCollection.getLock(), Lock.WRITE_LOCK);
                    }

                    if(parentCollection != null) {
                        try {
                            LOG.debug("Removing collection '" + collName + "' from its parent...");
                            //TODO : resolve from collection's base URI
                            parentCollection.removeCollection(this, uri.lastSegment());
                            saveCollection(transaction, parentCollection);

                        } catch(final LockException e) {
                            LOG.warn("LockException while removing collection '" + collName + "'");
                        } finally {
                            if(transaction == null) {
                                parentCollection.getLock().release(Lock.WRITE_LOCK);
                            }
                        }
                    }
                }

                //Update current state
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    // remove the metadata of all documents in the collection
                    final Value docKey = new CollectionStore.DocumentKey(collection.getId());
                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, docKey);
                    collectionsDb.removeAll(transaction, query);
                    // if this is not the root collection remove it...
                    if(!isRoot) {
                        final Value key = new CollectionStore.CollectionKey(collName);
                        //... from the disk
                        collectionsDb.remove(transaction, key);
                        //... from the cache
                        collectionsCache.remove(collection);
                        //and free its id for any further use
                        collectionsDb.freeCollectionId(collection.getId());
                    } else {
                        //Simply save the collection on disk
                        //It will remain cached
                        //and its id well never be made available
                        saveCollection(transaction, collection);
                    }
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on '" + collectionsDb.getFile().getName() + "'");
                }
                //catch(ReadOnlyException e) {
                //throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                //}
                catch(final BTreeException | IOException e) {
                    LOG.warn("Exception while removing collection: " + e.getMessage(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }

                //Remove child resources
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Removing resources in '" + collName + "'...");
                }

                final DocumentTrigger docTrigger = new DocumentTriggers(this, collection);

                for(final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();

                    docTrigger.beforeDeleteDocument(this, transaction, doc);

                    //Remove doc's metadata
                    // WM: now removed in one step. see above.
                    //removeResourceMetadata(transaction, doc);
                    //Remove document nodes' index entries
                    new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                        @Override
                        public Object start() {
                            try {
                                final Value ref = new NodeRef(doc.getDocId());
                                final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                                domDb.remove(transaction, query, null);
                            } catch(final BTreeException e) {
                                LOG.warn("btree error while removing document", e);
                            } catch(final IOException e) {
                                LOG.warn("io error while removing document", e);
                            } catch(final TerminatedException e) {
                                LOG.warn("method terminated", e);
                            }
                            return null;
                        }
                    }.run();
                    //Remove nodes themselves
                    new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                        @Override
                        public Object start() {
                            if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                                final long page = ((BinaryDocument) doc).getPage();
                                if(page > Page.NO_PAGE) {
                                    domDb.removeOverflowValue(transaction, page);
                                }
                            } else {
                                final StoredNode node = (StoredNode) doc.getFirstChild();
                                domDb.removeAll(transaction, node.getInternalAddress());
                            }
                            return null;
                        }
                    }.run();

                    docTrigger.afterDeleteDocument(this, transaction, doc.getURI());

                    //Make doc's id available again
                    collectionsDb.freeResourceId(doc.getDocId());
                }

                //now that the database has been updated, update the binary collections on disk
                final File fsSourceDir = getCollectionFile(fsDir, collection.getURI(), false);
                final File fsTargetDir = getCollectionFile(fsBackupDir, transaction, collection.getURI(), true);

                // remove child binary collections
                if(fsSourceDir.exists()) {
                    fsTargetDir.getParentFile().mkdirs();

                    //XXX: log first, rename second ??? -shabanovd
                    // DW: not sure a Fatal is required here. Copy and delete
                    // maybe?
                    if(fsSourceDir.renameTo(fsTargetDir)) {
                        final Loggable loggable = new RenameBinaryLoggable(this, transaction, fsSourceDir, fsTargetDir);
                        try {
                            logManager.writeToLog(loggable);
                        } catch(final TransactionException e) {
                            LOG.warn(e.getMessage(), e);
                        }
                    } else {
                        //XXX: throw IOException -shabanovd
                        LOG.fatal("Cannot rename " + fsSourceDir + " to " + fsTargetDir);
                    }
                }


                if(LOG.isDebugEnabled()) {
                    LOG.debug("Removing collection '" + collName + "' took " + (System.currentTimeMillis() - start));
                }

                colTrigger.afterDeleteCollection(this, transaction, collection.getURI());

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
     *
     * @throws PermissionDeniedException
     * @throws IOException
     * @throws TriggerException
     */
    @Override
    public void saveCollection(final Txn transaction, final Collection collection) throws PermissionDeniedException, IOException, TriggerException {
        if(collection == null) {
            LOG.error("NativeBroker.saveCollection called with collection == null! Aborting.");
            return;
        }
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        if(!pool.isInitializing()) {
            // don't cache the collection during initialization: SecurityManager is not yet online
            pool.getCollectionsCache().add(collection);
        }

        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);

            if(collection.getId() == Collection.UNKNOWN_COLLECTION_ID) {
                collection.setId(getNextCollectionId(transaction));
            }
            final Value name = new CollectionStore.CollectionKey(collection.getURI().toString());
            final VariableByteOutputStream os = new VariableByteOutputStream(8);
            collection.write(this, os);
            final long address = collectionsDb.put(transaction, name, os.data(), true);
            if(address == BFile.UNKNOWN_ADDRESS) {
                //TODO : exception !!! -pb
                LOG.warn("could not store collection data for '" + collection.getURI() + "'");
                return;
            }
            collection.setAddress(address);
            os.close();

        } catch(final ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
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
    public int getNextCollectionId(final Txn transaction) throws ReadOnlyException {
        int nextCollectionId = collectionsDb.getFreeCollectionId();
        if(nextCollectionId != Collection.UNKNOWN_COLLECTION_ID) {
            return nextCollectionId;
        }
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            final Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_COLLECTION_ID_KEY);
            final Value data = collectionsDb.get(key);
            if(data != null) {
                nextCollectionId = ByteConversion.byteToInt(data.getData(), OFFSET_COLLECTION_ID);
                ++nextCollectionId;
            }
            final byte[] d = new byte[Collection.LENGTH_COLLECTION_ID];
            ByteConversion.intToByte(nextCollectionId, d, OFFSET_COLLECTION_ID);
            collectionsDb.put(transaction, key, d, true);
            return nextCollectionId;
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    @Override
    public void reindexCollection(XmldbURI collectionName) throws PermissionDeniedException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }
        collectionName = prepend(collectionName.toCollectionPathURI());
        final Collection collection = getCollection(collectionName);
        if(collection == null) {
            LOG.debug("collection " + collectionName + " not found!");
            return;
        }
        reindexCollection(collection, NodeProcessor.MODE_STORE);
    }

    public void reindexCollection(final Collection collection, final int mode) throws PermissionDeniedException {
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        long start = System.currentTimeMillis();

        try {
            LOG.info(String.format("Start indexing collection %s", collection.getURI().toString()));
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REINDEX_COLLECTION, collection.getURI());
            reindexCollection(transaction, collection, mode);
            transact.commit(transaction);

        } catch(final Exception e) {
            transact.abort(transaction);
            LOG.warn("An error occurred during reindex: " + e.getMessage(), e);

        } finally {
            transact.close(transaction);
            pool.getProcessMonitor().endJob();
            LOG.info(String.format("Finished indexing collection %s in %s ms.",
                collection.getURI().toString(), System.currentTimeMillis() - start));
        }
    }

    public void reindexCollection(final Txn transaction, final Collection collection, final int mode) throws PermissionDeniedException {
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
            }
            LOG.debug("Reindexing collection " + collection.getURI());
            if(mode == NodeProcessor.MODE_STORE) {
                dropCollectionIndex(transaction, collection, true);
            }
            for(final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                final DocumentImpl next = i.next();
                reindexXMLResource(transaction, next, mode);
            }
            for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                final XmldbURI next = i.next();
                //TODO : resolve URIs !!! (collection.getURI().resolve(next))
                final Collection child = getCollection(collection.getURI().append(next));
                if(child == null) {
                    LOG.warn("Collection '" + next + "' not found");
                } else {
                    reindexCollection(transaction, child, mode);
                }
            }
        }
    }

    public void dropCollectionIndex(final Txn transaction, final Collection collection) throws PermissionDeniedException {
        dropCollectionIndex(transaction, collection, false);
    }

    public void dropCollectionIndex(final Txn transaction, final Collection collection, final boolean reindex) throws PermissionDeniedException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }
        if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
        }
        notifyDropIndex(collection);
        indexController.removeCollection(collection, this, reindex);
        for(final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            LOG.debug("Dropping index for document " + doc.getFileURI());
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() {
                    try {
                        final Value ref = new NodeRef(doc.getDocId());
                        final IndexQuery query =
                            new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                        domDb.remove(transaction, query, null);
                        domDb.flush();
                    } catch(final BTreeException e) {
                        LOG.warn("btree error while removing document", e);
                    } catch(final DBException e) {
                        LOG.warn("db error while removing document", e);
                    } catch(final IOException e) {
                        LOG.warn("io error while removing document", e);
                    } catch(final TerminatedException e) {
                        LOG.warn("method terminated", e);
                    }
                    return null;
                }
            }
                .run();
        }
    }

    /**
     * Store into the temporary collection of the database a given in-memory Document
     *
     * The in-memory Document is stored without a transaction and is not journalled,
     * if there is no temporary collection, this will first be created with a transaction
     *
     * @param doc The in-memory Document to store
     * @return The document stored in the temp collection
     */
    @Override
    public DocumentImpl storeTempResource(final org.exist.memtree.DocumentImpl doc)
        throws EXistException, PermissionDeniedException, LockException {
        //store the currentUser
        final Subject currentUser = getSubject();
        //elevate getUser() to DBA_USER
        setSubject(pool.getSecurityManager().getSystemSubject());
        //start a transaction
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        //create a name for the temporary document
        final XmldbURI docName = XmldbURI.create(MessageDigester.md5(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis()), false) + ".xml");

        //get the temp collection
        Collection temp = openCollection(XmldbURI.TEMP_COLLECTION_URI, Lock.WRITE_LOCK);
        boolean created = false;
        try {
            //if no temp collection
            if(temp == null) {
                //creates temp collection (with write lock)
                temp = createTempCollection(transaction);
                if(temp == null) {
                    LOG.warn("Failed to create temporary collection");
                    //TODO : emergency exit?
                }
                created = true;
            }
            //create a temporary document
            final DocumentImpl targetDoc = new DocumentImpl(pool, temp, docName);
            targetDoc.getPermissions().setMode(Permission.DEFAULT_TEMPORARY_DOCUMENT_PERM);
            final long now = System.currentTimeMillis();
            final DocumentMetadata metadata = new DocumentMetadata();
            metadata.setLastModified(now);
            metadata.setCreated(now);
            targetDoc.setMetadata(metadata);
            targetDoc.setDocId(getNextResourceId(transaction, temp));
            //index the temporary document
            final DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc); //NULL transaction, so temporary fragment is not journalled - AR
            indexer.scan();
            indexer.store();
            //store the temporary document
            temp.addDocument(transaction, this, targetDoc); //NULL transaction, so temporary fragment is not journalled - AR
            // unlock the temp collection
            if(transaction == null) {
                temp.getLock().release(Lock.WRITE_LOCK);
            } else if(!created) {
                transaction.registerLock(temp.getLock(), Lock.WRITE_LOCK);
            }
            //NULL transaction, so temporary fragment is not journalled - AR
            storeXMLResource(transaction, targetDoc);
            flush();
            closeDocument();
            //commit the transaction
            transact.commit(transaction);
            return targetDoc;
        } catch(final Exception e) {
            LOG.warn("Failed to store temporary fragment: " + e.getMessage(), e);
            //abort the transaction
            transact.abort(transaction);
        } finally {
            transact.close(transaction);
            //restore the user
            setUser(currentUser);
        }
        return null;
    }

    /**
     * remove all documents from temporary collection
     *
     * @param forceRemoval Should temporary resources be forcefully removed
     */
    @Override
    public void cleanUpTempResources(final boolean forceRemoval) throws PermissionDeniedException {
        final Collection temp = getCollection(XmldbURI.TEMP_COLLECTION_URI);
        if(temp == null) {
            return;
        }
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            removeCollection(transaction, temp);
            transact.commit(transaction);
        } catch(final Exception e) {
            transact.abort(transaction);
            LOG.warn("Failed to remove temp collection: " + e.getMessage(), e);
        } finally {
            transact.close(transaction);
        }
    }

    @Override
    public DocumentImpl getResourceById(final int collectionId, final byte resourceType, final int documentId) throws PermissionDeniedException {
        XmldbURI uri = null;
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            //final VariableByteOutputStream os = new VariableByteOutputStream(8);
            //doc.write(os);
            //Value key = new CollectionStore.DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            //collectionsDb.put(transaction, key, os.data(), true);

            //Value collectionKey = new CollectionStore.CollectionKey
            //collectionsDb.get(Value.EMPTY_VALUE)

            //get the collection uri
            String collectionUri = null;
            if(collectionId == 0) {
                collectionUri = "/db";
            } else {
                for(final Value collectionDbKey : collectionsDb.getKeys()) {
                    if(collectionDbKey.data()[0] == CollectionStore.KEY_TYPE_COLLECTION) {
                        //Value collectionDbValue = collectionsDb.get(collectionDbKey);

                        final VariableByteInput vbi = collectionsDb.getAsStream(collectionDbKey);
                        final int id = vbi.readInt();
                        //check if the collection id matches (first 4 bytes)
                        if(collectionId == id) {
                            collectionUri = new String(Arrays.copyOfRange(collectionDbKey.data(), 1, collectionDbKey.data().length));
                            break;
                        }
                    }
                }
            }

            //get the resource uri
            final Value key = new CollectionStore.DocumentKey(collectionId, resourceType, documentId);
            final VariableByteInput vbi = collectionsDb.getAsStream(key);
            vbi.readInt(); //skip doc id
            final String resourceUri = vbi.readUTF();

            //get the resource
            uri = XmldbURI.createInternal(collectionUri + "/" + resourceUri);

        } catch(final TerminatedException te) {
            LOG.error("Query Terminated", te);
            return null;
        } catch(final BTreeException bte) {
            LOG.error("Problem reading btree", bte);
            return null;
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on " + collectionsDb.getFile().getName());
            return null;
        } catch(final IOException e) {
            LOG.error("IOException while reading resource data", e);
            return null;
        } finally {
            lock.release(Lock.READ_LOCK);
        }

        return getResource(uri, Permission.READ);
    }

    /**
     * store Document entry into its collection.
     */
    @Override
    public void storeXMLResource(final Txn transaction, final DocumentImpl doc) {


        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            final VariableByteOutputStream os = new VariableByteOutputStream(8);
            doc.write(os);
            final Value key = new CollectionStore.DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            collectionsDb.put(transaction, key, os.data(), true);
            //} catch (ReadOnlyException e) {
            //LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch(final IOException e) {
            LOG.warn("IOException while writing document data", e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    public void storeMetadata(final Txn transaction, final DocumentImpl doc) throws TriggerException {
        final Collection col = doc.getCollection();
        final DocumentTrigger trigger = new DocumentTriggers(this, col);

        trigger.beforeUpdateDocumentMetadata(this, transaction, doc);

        storeXMLResource(transaction, doc);

        trigger.afterUpdateDocumentMetadata(this, transaction, doc);
    }

    private File getCollectionFile(final File dir, final XmldbURI uri, final boolean create) throws IOException {
        return getCollectionFile(dir, null, uri, create);
    }

    public File getCollectionBinaryFileFsPath(final XmldbURI uri) {
        return new File(fsDir, uri.getURI().toString());
    }

    private File getCollectionFile(File dir, final Txn transaction, final XmldbURI uri, boolean create)
        throws IOException {
        if(transaction != null) {
            dir = new File(dir, "txn." + transaction.getId());
            if(create && !dir.exists()) {
                if(!dir.mkdir()) {
                    throw new IOException("Cannot make transaction filesystem directory: " + dir);
                }
            }

            //XXX: replace by transaction operation id/number from Txn
            //add unique id for operation in transaction
            dir = new File(dir, "oper." + UUID.randomUUID().toString());
            if(create && !dir.exists()) {
                if(!dir.mkdir()) {
                    throw new IOException("Cannot make transaction filesystem directory: " + dir);
                }
            }
        }
        final XmldbURI[] segments = uri.getPathSegments();
        File binFile = dir;
        final int last = segments.length - 1;
        for(int i = 0; i < segments.length; i++) {
            binFile = new File(binFile, segments[i].toString());
            if(create && i != last && !binFile.exists()) {
                if(!binFile.mkdir()) {
                    throw new IOException("Cannot make collection filesystem directory: " + binFile);
                }
            }
        }
        return binFile;
    }

    @Deprecated
    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data)
        throws IOException {
        blob.setPage(Page.NO_PAGE);
        final File binFile = getCollectionFile(fsDir, blob.getURI(), true);
        File backupFile = null;
        final boolean exists = binFile.exists();
        if(exists) {
            backupFile = getCollectionFile(fsBackupDir, transaction, blob.getURI(), true);
            if(!binFile.renameTo(backupFile)) {
                throw new IOException("Cannot backup binary resource for journal to " + backupFile);
            }
        }
        final OutputStream os = new FileOutputStream(binFile);
        os.write(data, 0, data.length);
        os.close();

        final Loggable loggable;
        if(exists) {
            loggable = new UpdateBinaryLoggable(this, transaction, binFile, backupFile);
        } else {
            loggable = new CreateBinaryLoggable(this, transaction, binFile);
        }
        try {
            logManager.writeToLog(loggable);
        } catch(final TransactionException e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final InputStream is)
        throws IOException {
        blob.setPage(Page.NO_PAGE);
        final File binFile = getCollectionFile(fsDir, blob.getURI(), true);
        File backupFile = null;
        final boolean exists = binFile.exists();
        if(exists) {
            backupFile = getCollectionFile(fsBackupDir, transaction, blob.getURI(), true);
            if(!binFile.renameTo(backupFile)) {
                throw new IOException("Cannot backup binary resource for journal to " + backupFile);
            }
        }
        final byte[] buffer = new byte[BINARY_RESOURCE_BUF_SIZE];
        final OutputStream os = new FileOutputStream(binFile);
        int len;
        while((len = is.read(buffer)) >= 0) {
            if(len > 0) {
                os.write(buffer, 0, len);
            }
        }
        os.close();

        final Loggable loggable;
        if(exists) {
            loggable = new UpdateBinaryLoggable(this, transaction, binFile, backupFile);
        } else {
            loggable = new CreateBinaryLoggable(this, transaction, binFile);
        }
        try {
            logManager.writeToLog(loggable);
        } catch(final TransactionException e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    public Document getXMLResource(final XmldbURI fileName) throws PermissionDeniedException {
        return getResource(fileName, Permission.READ);
    }

    /**
     * get a document by its file name. The document's file name is used to
     * identify a document.
     *
     * @param fileName absolute file name in the database;
     *                 name can be given with or without the leading path /db/shakespeare.
     * @return The document value
     * @throws PermissionDeniedException
     */
    @Override
    public DocumentImpl getResource(XmldbURI fileName, final int accessType) throws PermissionDeniedException {
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !!!
        final XmldbURI collUri = fileName.removeLastSegment();
        final XmldbURI docUri = fileName.lastSegment();
        final Collection collection = getCollection(collUri);
        if(collection == null) {
            LOG.debug("collection '" + collUri + "' not found!");
            return null;
        }

        //if(!collection.getPermissions().validate(getSubject(), Permission.READ)) {
        //throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getSubject().getName());
        //}

        final DocumentImpl doc = collection.getDocument(this, docUri);
        if(doc == null) {
            LOG.debug("document '" + fileName + "' not found!");
            return null;
        }

        if(!doc.getPermissions().validate(getSubject(), accessType)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' not allowed requested access to document '" + fileName + "'");
        }

        if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
            final BinaryDocument bin = (BinaryDocument) doc;
            try {
                bin.setContentLength(getBinaryResourceSize(bin));
            } catch(final IOException ex) {
                LOG.fatal("Cannot get content size for " + bin.getURI(), ex);
            }
        }
        return doc;
    }

    @Override
    public DocumentImpl getXMLResource(XmldbURI fileName, final int lockMode) throws PermissionDeniedException {
        if(fileName == null) {
            return null;
        }
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !
        final XmldbURI collUri = fileName.removeLastSegment();
        final XmldbURI docUri = fileName.lastSegment();
        final Collection collection = openCollection(collUri, lockMode);
        if(collection == null) {
            LOG.debug("collection '" + collUri + "' not found!");
            return null;
        }
        try {
            //if (!collection.getPermissions().validate(getSubject(), Permission.EXECUTE)) {
            //    throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getSubject().getName());
            //}
            final DocumentImpl doc = collection.getDocumentWithLock(this, docUri, lockMode);
            if(doc == null) {
                //LOG.debug("document '" + fileName + "' not found!");
                return null;
            }
            //if (!doc.getMode().validate(getUser(), Permission.READ))
            //throw new PermissionDeniedException("not allowed to read document");
            if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                final BinaryDocument bin = (BinaryDocument) doc;
                try {
                    bin.setContentLength(getBinaryResourceSize(bin));
                } catch(final IOException ex) {
                    LOG.fatal("Cannot get content size for " + bin.getURI(), ex);
                }
            }
            return doc;
        } catch(final LockException e) {
            LOG.warn("Could not acquire lock on document " + fileName, e);
            //TODO : exception ? -pb
        } finally {
            //TODO UNDERSTAND : by whom is this lock acquired ? -pb
            // If we don't check for the NO_LOCK we'll pop someone else's lock off
            if(lockMode != Lock.NO_LOCK) {
                collection.release(lockMode);
            }
        }
        return null;
    }

    @Override
    public void readBinaryResource(final BinaryDocument blob, final OutputStream os)
        throws IOException {
        InputStream is = null;
        try {
            is = getBinaryResource(blob);
            final byte[] buffer = new byte[BINARY_RESOURCE_BUF_SIZE];
            int len;
            while((len = is.read(buffer)) >= 0) {
                os.write(buffer, 0, len);
            }
        } finally {
            if(is != null) {
                is.close();
            }
        }
    }

    @Override
    public long getBinaryResourceSize(final BinaryDocument blob)
        throws IOException {
        final File binFile = getCollectionFile(fsDir, blob.getURI(), false);
        return binFile.length();
    }

    @Override
    public File getBinaryFile(final BinaryDocument blob) throws IOException {
        return getCollectionFile(fsDir, blob.getURI(), false);
    }

    @Override
    public InputStream getBinaryResource(final BinaryDocument blob)
        throws IOException {
        final File binFile = getCollectionFile(fsDir, blob.getURI(), false);
        return new FileInputStream(binFile);
    }

    //TODO : consider a better cooperation with Collection -pb
    @Override
    public void getCollectionResources(final Collection.InternalAccess collectionInternalAccess) {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            final Value key = new CollectionStore.DocumentKey(collectionInternalAccess.getId());
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);

            collectionsDb.query(query, new DocumentCallback(collectionInternalAccess));
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch(final IOException | BTreeException | TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    @Override
    public void getResourcesFailsafe(final BTreeCallback callback, final boolean fullScan) throws TerminatedException {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            final Value key = new CollectionStore.DocumentKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            if(fullScan) {
                collectionsDb.rawScan(query, callback);
            } else {
                collectionsDb.query(query, callback);
            }
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch(final IOException | BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    @Override
    public void getCollectionsFailsafe(final BTreeCallback callback) throws TerminatedException {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            final Value key = new CollectionStore.CollectionKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, callback);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch(final IOException | BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * Get all the documents in this database matching the given
     * document-type's name.
     *
     * @return The documentsByDoctype value
     */
    @Override
    public MutableDocumentSet getXMLResourcesByDoctype(final String doctypeName, final MutableDocumentSet result) throws PermissionDeniedException {
        final MutableDocumentSet docs = getAllXMLResources(new DefaultDocumentSet());
        for(final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            final DocumentType doctype = doc.getDoctype();
            if(doctype == null) {
                continue;
            }
            if(doctypeName.equals(doctype.getName())
                && doc.getCollection().getPermissionsNoLock().validate(getSubject(), Permission.READ)
                && doc.getPermissions().validate(getSubject(), Permission.READ)) {
                result.add(doc);
            }
        }
        return result;
    }

    /**
     * Adds all the documents in the database to the specified DocumentSet.
     *
     * @param docs a (possibly empty) document set to which the found
     *             documents are added.
     */
    @Override
    public MutableDocumentSet getAllXMLResources(final MutableDocumentSet docs) throws PermissionDeniedException {
        final long start = System.currentTimeMillis();
        Collection rootCollection = null;
        try {
            rootCollection = openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.READ_LOCK);
            rootCollection.allDocs(this, docs, true);
            if(LOG.isDebugEnabled()) {
                LOG.debug("getAllDocuments(DocumentSet) - end - "
                    + "loading "
                    + docs.getDocumentCount()
                    + " documents took "
                    + (System.currentTimeMillis() - start)
                    + "ms.");
            }
            return docs;
        } finally {
            if(rootCollection != null) {
                rootCollection.release(Lock.READ_LOCK);
            }
        }
    }

    //TODO : consider a better cooperation with Collection -pb
    @Override
    public void getResourceMetadata(final DocumentImpl document) {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            final Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            final VariableByteInput is = collectionsDb.getAsStream(key);
            if(is != null) {
                document.readDocumentMeta(is);
            }
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } catch(final IOException e) {
            LOG.warn("IOException while reading document data", e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * @param doc         src document
     * @param destination destination collection
     * @param newName     the new name for the document
     */
    @Override
    public void copyResource(final Txn transaction, final DocumentImpl doc, final Collection destination, XmldbURI newName) throws PermissionDeniedException, LockException, EXistException {

        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        final Collection collection = doc.getCollection();

        if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' has insufficient privileges to copy the resource '" + doc.getFileURI() + "'.");
        }

        if(!doc.getPermissions().validate(getSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account '" + getSubject().getName() + "' has insufficient privileges to copy the resource '" + doc.getFileURI() + "'.");
        }

        if(newName == null) {
            newName = doc.getFileURI();
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            final Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);
                final DocumentImpl oldDoc = destination.getDocument(this, newName);

                if(!destination.getPermissionsNoLock().validate(getSubject(), Permission.EXECUTE)) {
                    throw new PermissionDeniedException("Account '" + getSubject().getName() + "' does not have execute access on the destination collection '" + destination.getURI() + "'.");
                }

                if(destination.hasChildCollection(this, newName.lastSegment())) {
                    throw new EXistException(
                        "The collection '" + destination.getURI() + "' already has a sub-collection named '" + newName.lastSegment() + "', you cannot create a Document with the same name as an existing collection."
                    );
                }

                final XmldbURI newURI = destination.getURI().append(newName);
                final XmldbURI oldUri = doc.getURI();

                final DocumentTrigger trigger = new DocumentTriggers(this, collection);

                if(oldDoc == null) {
                    if(!destination.getPermissionsNoLock().validate(getSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("Account '" + getSubject().getName() + "' does not have write access on the destination collection '" + destination.getURI() + "'.");
                    }
                } else {
                    //overwrite existing document

                    if(doc.getDocId() == oldDoc.getDocId()) {
                        throw new EXistException("Cannot copy resource to itself '" + doc.getURI() + "'.");
                    }

                    if(!oldDoc.getPermissions().validate(getSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("A resource with the same name already exists in the target collection '" + oldDoc.getURI() + "', and you do not have write access on that resource.");
                    }

                    trigger.beforeDeleteDocument(this, transaction, oldDoc);
                    trigger.afterDeleteDocument(this, transaction, newURI);
                }

                trigger.beforeCopyDocument(this, transaction, doc, newURI);

                DocumentImpl newDocument = null;
                if(doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    InputStream is = null;
                    try {
                        is = getBinaryResource((BinaryDocument) doc);
                        newDocument = destination.addBinaryResource(transaction, this, newName, is, doc.getMetadata().getMimeType(), -1);
                    } finally {
                        if(is != null) {
                            is.close();
                        }
                    }
                } else {
                    final DocumentImpl newDoc = new DocumentImpl(pool, destination, newName);
                    newDoc.copyOf(doc, oldDoc != null);
                    newDoc.setDocId(getNextResourceId(transaction, destination));
                    newDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                    try {
                        copyXMLResource(transaction, doc, newDoc);
                        destination.addDocument(transaction, this, newDoc);
                        storeXMLResource(transaction, newDoc);
                    } finally {
                        newDoc.getUpdateLock().release(Lock.WRITE_LOCK);
                    }
                    newDocument = newDoc;
                }

                trigger.afterCopyDocument(this, transaction, newDocument, oldUri);

            } catch(final IOException e) {
                LOG.warn("An error occurred while copying resource", e);
            } catch(final TriggerException e) {
                throw new PermissionDeniedException(e.getMessage(), e);
            } finally {
                lock.release(Lock.WRITE_LOCK);
            }
        }
    }

    private void copyXMLResource(final Txn transaction, final DocumentImpl oldDoc, final DocumentImpl newDoc) {
        LOG.debug("Copying document " + oldDoc.getFileURI() + " to " +
            newDoc.getURI());
        final long start = System.currentTimeMillis();
        indexController.setDocument(newDoc, StreamListener.STORE);
        final StreamListener listener = indexController.getStreamListener();
        final NodeList nodes = oldDoc.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final StoredNode node = (StoredNode) nodes.item(i);
            final Iterator<StoredNode> iterator = getNodeIterator(node);
            iterator.next();
            copyNodes(transaction, iterator, node, new NodePath(), newDoc, false, true, listener);
        }
        flush();
        closeDocument();
        LOG.debug("Copy took " + (System.currentTimeMillis() - start) + "ms.");
    }

    /**
     * Move (and/or rename) a Resource to another collection
     *
     * @param doc         source document
     * @param destination the destination collection
     * @param newName     the new name for the resource
     * @throws TriggerException
     */
    @Override
    public void moveResource(final Txn transaction, final DocumentImpl doc, final Collection destination, XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException {

        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        final Account docUser = doc.getUserLock();
        if(docUser != null) {
            if(!(getSubject().getName()).equals(docUser.getName())) {
                throw new PermissionDeniedException("Cannot move '" + doc.getFileURI() + " because is locked by getUser() '" + docUser.getName() + "'");
            }
        }

        final Collection collection = doc.getCollection();

        if(!collection.getPermissionsNoLock().validate(getSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on source Collection to move resource " + doc.getFileURI());
        }

        /**
         * As per the rules of Linux -
         *
         * mv is NOT a copy operation unless we are traversing filesystems.
         * We consider eXist to be a single filesystem, so we only need
         * WRITE and EXECUTE access on the source and destination collections
         * as we are effectively just re-linking the file.
         *
         * - Adam 2013-03-26
         */
        //must be owner of have execute access for the rename
//        if(!((doc.getPermissions().getOwner().getId() != getSubject().getId()) | (doc.getPermissions().validate(getSubject(), Permission.EXECUTE)))) {
//            throw new PermissionDeniedException("Account "+getSubject().getName()+" have insufficient privileges on destination Collection to move resource " + doc.getFileURI());
//        }

        if(!destination.getPermissionsNoLock().validate(getSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getSubject().getName() + " have insufficient privileges on destination Collection to move resource " + doc.getFileURI());
        }
        
        
        /* Copy reference to original document */
        final File fsOriginalDocument = getCollectionFile(fsDir, doc.getURI(), true);


        final XmldbURI oldName = doc.getFileURI();
        if(newName == null) {
            newName = oldName;
        }

        try {
            if(destination.hasChildCollection(this, newName.lastSegment())) {
                throw new PermissionDeniedException(
                    "The collection '" + destination.getURI() + "' have collection '" + newName.lastSegment() + "'. " +
                        "Document with same name can't be created."
                );
            }

            final DocumentTrigger trigger = new DocumentTriggers(this, collection);

            // check if the move would overwrite a collection
            //TODO : resolve URIs : destination.getURI().resolve(newName)
            final DocumentImpl oldDoc = destination.getDocument(this, newName);
            if(oldDoc != null) {

                if(doc.getDocId() == oldDoc.getDocId()) {
                    throw new PermissionDeniedException("Cannot move resource to itself '" + doc.getURI() + "'.");
                }

                // GNU mv command would prompt for Confirmation here, you can say yes or pass the '-f' flag. As we cant prompt for confirmation we assume OK
                /* if(!oldDoc.getPermissions().validate(getSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Resource with same name exists in target collection and write is denied");
                }
                */

                trigger.beforeDeleteDocument(this, transaction, oldDoc);
                trigger.afterDeleteDocument(this, transaction, oldDoc.getURI());
            }

            boolean renameOnly = collection.getId() == destination.getId();

            final XmldbURI oldURI = doc.getURI();
            final XmldbURI newURI = destination.getURI().append(newName);

            trigger.beforeMoveDocument(this, transaction, doc, newURI);

            collection.unlinkDocument(this, doc);
            removeResourceMetadata(transaction, doc);
            doc.setFileURI(newName);
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {
                if(!renameOnly) {
                    //XXX: BUG: doc have new uri here!
                    dropIndex(transaction, doc);
                    saveCollection(transaction, collection);
                }
                doc.setCollection(destination);
                destination.addDocument(transaction, this, doc);
                if(!renameOnly) {
                    // reindexing
                    reindexXMLResource(transaction, doc, NodeProcessor.MODE_REPAIR);
                }
            } else {
                // binary resource
                doc.setCollection(destination);
                destination.addDocument(transaction, this, doc);
                final File colDir = getCollectionFile(fsDir, destination.getURI(), true);
                final File binFile = new File(colDir, newName.lastSegment().toString());
                final File sourceFile = getCollectionFile(fsDir, doc.getURI(), false);
                /* Create required directories */
                binFile.getParentFile().mkdirs();
                /* Rename original file to new location */
                if(fsOriginalDocument.renameTo(binFile)) {
                    final Loggable loggable = new RenameBinaryLoggable(this, transaction, sourceFile, binFile);
                    try {
                        logManager.writeToLog(loggable);
                    } catch(final TransactionException e) {
                        LOG.warn(e.getMessage(), e);
                    }
                } else {
                    LOG.fatal("Cannot rename " + sourceFile + " to " + binFile + " for journaling of binary resource move.");
                }
            }
            storeXMLResource(transaction, doc);
            saveCollection(transaction, destination);

            trigger.afterMoveDocument(this, transaction, doc, oldURI);

        } catch(final ReadOnlyException e) {
            throw new PermissionDeniedException(e.getMessage(), e);
        }
    }

    @Override
    public void removeXMLResource(final Txn transaction, final DocumentImpl document, boolean freeDocId) throws PermissionDeniedException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }
        try {
            if(LOG.isInfoEnabled()) {
                LOG.info("Removing document " + document.getFileURI() +
                    " (" + document.getDocId() + ") ...");
            }

            final DocumentTrigger trigger = new DocumentTriggers(this);

            if(freeDocId) {
                trigger.beforeDeleteDocument(this, transaction, document);
            }

            dropIndex(transaction, document);
            if(LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
            try {
                if(!document.getMetadata().isReferenced()) {
                    new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                        @Override
                        public Object start() {
                            final StoredNode node = (StoredNode) document.getFirstChild();
                            domDb.removeAll(transaction, node.getInternalAddress());
                            return null;
                        }
                    }.run();
                }
            } catch(NullPointerException npe0) {
                LOG.error("Caught NPE in DOMTransaction to actually be able to remove the document.");
            }

            final NodeRef ref = new NodeRef(document.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() {
                    try {
                        domDb.remove(transaction, idx, null);
                    } catch(final BTreeException | IOException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
                    } catch(final TerminatedException e) {
                        LOG.warn("method terminated", e);
                    }
                    return null;
                }
            }.run();
            removeResourceMetadata(transaction, document);
            if(freeDocId) {
                collectionsDb.freeResourceId(document.getDocId());

                trigger.afterDeleteDocument(this, transaction, document.getURI());
            }

        } catch(final ReadOnlyException e) {
            LOG.warn("removeDocument(String) - " + DATABASE_IS_READ_ONLY);
        } catch(final TriggerException e) {
            LOG.warn(e);
        }
    }

    private void dropIndex(final Txn transaction, final DocumentImpl document) throws ReadOnlyException {
        indexController.setDocument(document, StreamListener.REMOVE_ALL_NODES);
        final StreamListener listener = indexController.getStreamListener();
        final NodeList nodes = document.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final StoredNode node = (StoredNode) nodes.item(i);
            final Iterator<StoredNode> iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), NodeProcessor.MODE_REMOVE, listener);
        }
        notifyDropIndex(document);
        indexController.flush();
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final BinaryDocument blob) throws PermissionDeniedException, IOException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("removing binary resource " + blob.getDocId() + "...");
        }

        final File binFile = getCollectionFile(fsDir, blob.getURI(), false);
        if(binFile.exists()) {
            final File binBackupFile = getCollectionFile(fsBackupDir, transaction, blob.getURI(), true);
            final Loggable loggable = new RenameBinaryLoggable(this, transaction, binFile, binBackupFile);
            if(!binFile.renameTo(binBackupFile)) {
                // Workaround for Java bug 6213298 - renameTo() sometimes doesn't work
                // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6213298
                System.gc(); //TODO remove this, must be a better approach in Java 7?
                try {
                    Thread.sleep(50);
                } catch(final Exception e) {
                    //ignore
                }
                if(!binFile.renameTo(binBackupFile)) {
                    throw new IOException("Cannot move file " + binFile
                        + " for delete journal to " + binBackupFile);
                }
            }
            try {
                logManager.writeToLog(loggable);
            } catch(final TransactionException e) {
                LOG.warn(e.getMessage(), e);
            }
        }
        removeResourceMetadata(transaction, blob);

        getIndexController().setDocument(blob, StreamListener.REMOVE_BINARY);
        getIndexController().flush();
    }

    /**
     * @param transaction
     * @param document
     */
    private void removeResourceMetadata(final Txn transaction, final DocumentImpl document) {
        // remove document metadata
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            if(LOG.isDebugEnabled()) {
                LOG.debug("Removing resource metadata for " + document.getDocId());
            }
            final Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
            //} catch (ReadOnlyException e) {
            //LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName());
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * get next Free Doc Id
     *
     * @throws EXistException If there's no free document id
     */
    @Override
    public int getNextResourceId(final Txn transaction, final Collection collection) throws EXistException {
        int nextDocId = collectionsDb.getFreeResourceId();
        if(nextDocId != DocumentImpl.UNKNOWN_DOCUMENT_ID) {
            return nextDocId;
        }
        nextDocId = 1;
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            final Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_DOC_ID_KEY);
            final Value data = collectionsDb.get(key);
            if(data != null) {
                nextDocId = ByteConversion.byteToInt(data.getData(), 0);
                ++nextDocId;
                if(nextDocId == 0x7FFFFFFF) {
                    pool.setReadOnly();
                    throw new EXistException("Max. number of document ids reached. Database is set to " +
                        "read-only state. Please do a complete backup/restore to compact the db and " +
                        "free document ids.");
                }
            }
            final byte[] d = new byte[4];
            ByteConversion.intToByte(nextDocId, d, 0);
            collectionsDb.put(transaction, key, d, true);
            //} catch (ReadOnlyException e) {
            //LOG.warn("Database is read-only");
            //return DocumentImpl.UNKNOWN_DOCUMENT_ID;
            //TODO : rethrow ? -pb
        } catch(final LockException e) {
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
    private void reindexXMLResource(final Txn transaction, final DocumentImpl doc, final int mode) {
        if(doc.isCollectionConfig()) {
            doc.getCollection().setCollectionConfigEnabled(false);
        }
        indexController.setDocument(doc, StreamListener.STORE);
        final StreamListener listener = indexController.getStreamListener();
        final NodeList nodes = doc.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final StoredNode node = (StoredNode) nodes.item(i);
            final Iterator<StoredNode> iterator = getNodeIterator(node);
            iterator.next();
            scanNodes(transaction, iterator, node, new NodePath(), mode, listener);
        }
        flush();
        if(doc.isCollectionConfig()) {
            doc.getCollection().setCollectionConfigEnabled(true);
        }
    }

    @Override
    public void defragXMLResource(final Txn transaction, final DocumentImpl doc) {
        //TODO : use dedicated function in XmldbURI
        LOG.debug("============> Defragmenting document " +
            doc.getCollection().getURI() + "/" + doc.getFileURI());
        final long start = System.currentTimeMillis();
        try {
            final long firstChild = doc.getFirstChildAddress();
            // dropping old structure index
            dropIndex(transaction, doc);
            // dropping dom index
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() {
                    try {
                        domDb.remove(transaction, idx, null);
                        domDb.flush();
                    } catch(final IOException | DBException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
                    } catch(final TerminatedException e) {
                        LOG.warn("method terminated", e);
                    }
                    return null;
                }
            }.run();
            // create a copy of the old doc to copy the nodes into it
            final DocumentImpl tempDoc = new DocumentImpl(pool, doc.getCollection(), doc.getFileURI());
            tempDoc.copyOf(doc, true);
            tempDoc.setDocId(doc.getDocId());
            indexController.setDocument(doc, StreamListener.STORE);
            final StreamListener listener = indexController.getStreamListener();
            // copy the nodes
            final NodeList nodes = doc.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                final StoredNode node = (StoredNode) nodes.item(i);
                final Iterator<StoredNode> iterator = getNodeIterator(node);
                iterator.next();
                copyNodes(transaction, iterator, node, new NodePath(), tempDoc, true, true, listener);
            }
            flush();
            // remove the old nodes
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() {
                    domDb.removeAll(transaction, firstChild);
                    try {
                        domDb.flush();
                    } catch(final DBException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
                    }
                    return null;
                }
            }.run();
            doc.copyChildren(tempDoc);
            doc.getMetadata().setSplitCount(0);
            doc.getMetadata().setPageCount(tempDoc.getMetadata().getPageCount());
            storeXMLResource(transaction, doc);
            closeDocument();
            LOG.debug("Defragmentation took " + (System.currentTimeMillis() - start) + "ms.");
        } catch(final ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY, e);
        }
    }

    /**
     * consistency Check of the database; useful after XUpdates;
     * called if xupdate.consistency-checks is true in configuration
     */
    @Override
    public void checkXMLResourceConsistency(final DocumentImpl doc) throws EXistException {
        boolean xupdateConsistencyChecks = false;
        final Object property = pool.getConfiguration().getProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS);
        if(property != null) {
            xupdateConsistencyChecks = ((Boolean) property).booleanValue();
        }
        if(xupdateConsistencyChecks) {
            LOG.debug("Checking document " + doc.getFileURI());
            checkXMLResourceTree(doc);
        }
    }

    /**
     * consistency Check of the database; useful after XUpdates;
     * called by {@link #checkXMLResourceConsistency(DocumentImpl)}
     */
    @Override
    public void checkXMLResourceTree(final DocumentImpl doc) {
        LOG.debug("Checking DOM tree for document " + doc.getFileURI());
        boolean xupdateConsistencyChecks = false;
        final Object property = pool.getConfiguration().getProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS);
        if(property != null) {
            xupdateConsistencyChecks = ((Boolean) property).booleanValue();
        }
        if(xupdateConsistencyChecks) {
            new DOMTransaction(this, domDb, Lock.READ_LOCK) {
                @Override
                public Object start() throws ReadOnlyException {
                    LOG.debug("Pages used: " + domDb.debugPages(doc, false));
                    return null;
                }
            }.run();
            final NodeList nodes = doc.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                final StoredNode node = (StoredNode) nodes.item(i);
                final Iterator<StoredNode> iterator = getNodeIterator(node);
                iterator.next();
                final StringBuilder buf = new StringBuilder();
                //Pass buf to the following method to get a dump of all node ids in the document
                if(!checkNodeTree(iterator, node, buf)) {
                    LOG.debug("node tree: " + buf.toString());
                    throw new RuntimeException("Error in document tree structure");
                }
            }
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, Lock.READ_LOCK) {
                @Override
                public Object start() {
                    try {
                        domDb.findKeys(idx);
                    } catch(final BTreeException | IOException e) {
                        LOG.warn("start() - " + "error while removing doc", e);
                    }
                    return null;
                }
            }.run();
        }
    }

    /**
     * Store a node into the database. This method is called by the parser to
     * write a node to the storage backend.
     *
     * @param node        the node to be stored
     * @param currentPath path expression which points to this node's
     *                    element-parent or to itself if it is an element (currently used by
     *                    the Broker to determine if a node's content should be
     *                    fulltext-indexed).  @param index switch to activate fulltext indexation
     */
    @Override
    public void storeNode(final Txn transaction, final StoredNode node, final NodePath currentPath, final IndexSpec indexSpec, final boolean fullTextIndex) {
        checkAvailableMemory();
        final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
        final short nodeType = node.getNodeType();
        final byte data[] = node.serialize();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            @Override
            public Object start() throws ReadOnlyException {
                long address;
                if(nodeType == Node.TEXT_NODE
                    || nodeType == Node.ATTRIBUTE_NODE
                    || nodeType == Node.CDATA_SECTION_NODE
                    || node.getNodeId().getTreeLevel() > defaultIndexDepth) {
                    address = domDb.add(transaction, data);
                } else {
                    address = domDb.put(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                }
                if(address == BFile.UNKNOWN_ADDRESS) {
                    LOG.warn("address is missing");
                }
                //TODO : how can we continue here ? -pb
                node.setInternalAddress(address);
                return null;
            }
        }.run();
        ++nodesCount;
        ByteArrayPool.releaseByteArray(data);
        nodeProcessor.reset(transaction, node, currentPath, indexSpec, fullTextIndex);
        nodeProcessor.doIndex();
    }

    @Override
    public void updateNode(final Txn transaction, final StoredNode node, final boolean reindex) {
        try {
            final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
            final long internalAddress = node.getInternalAddress();
            final byte[] data = node.serialize();
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() throws ReadOnlyException {
                    if(StorageAddress.hasAddress(internalAddress)) {
                        domDb.update(transaction, internalAddress, data);
                    } else {
                        domDb.update(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                    }
                    return null;
                }
            }.run();
            ByteArrayPool.releaseByteArray(data);
        } catch(final Exception e) {
            final Value oldVal = domDb.get(node.getInternalAddress());
            final StoredNode old = StoredNode.deserialize(oldVal.data(),
                oldVal.start(), oldVal.getLength(),
                (DocumentImpl) node.getOwnerDocument(), false);
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
    @Override
    public void insertNodeAfter(final Txn transaction, final StoredNode previous, final StoredNode node) {
        final byte data[] = node.serialize();
        final DocumentImpl doc = (DocumentImpl) previous.getOwnerDocument();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            @Override
            public Object start() {
                long address = previous.getInternalAddress();
                if(address != BFile.UNKNOWN_ADDRESS) {
                    address = domDb.insertAfter(transaction, doc, address, data);
                } else {
                    final NodeRef ref = new NodeRef(doc.getDocId(), previous.getNodeId());
                    address = domDb.insertAfter(transaction, doc, ref, data);
                }
                node.setInternalAddress(address);
                return null;
            }
        }.run();
    }

    private void copyNodes(final Txn transaction, final Iterator<StoredNode> iterator, final StoredNode node,
                           final NodePath currentPath, final DocumentImpl newDoc, final boolean defragment, final boolean index,
                           final StreamListener listener) {
        copyNodes(transaction, iterator, node, currentPath, newDoc, defragment, index, listener, null);
    }

    private void copyNodes(final Txn transaction, Iterator<StoredNode> iterator, final StoredNode node,
                           final NodePath currentPath, final DocumentImpl newDoc, final boolean defragment, final boolean index,
                           final StreamListener listener, NodeId oldNodeId) {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.addComponent(node.getQName());
        }
        final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
        final long oldAddress = node.getInternalAddress();
        node.setOwnerDocument(newDoc);
        node.setInternalAddress(BFile.UNKNOWN_ADDRESS);
        storeNode(transaction, node, currentPath, null, index);
        if(defragment && oldNodeId != null) {
            pool.getNotificationService().notifyMove(oldNodeId, node);
        }
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            //save old value, whatever it is
            final long address = node.getInternalAddress();
            node.setInternalAddress(oldAddress);
            endElement(node, currentPath, null);
            //restore old value, whatever it was
            node.setInternalAddress(address);
            node.setDirty(false);
        }
        if(node.getNodeId().getTreeLevel() == 1) {
            newDoc.appendChild(node);
        }
        node.setOwnerDocument(doc);
        if(listener != null) {
            switch(node.getNodeType()) {
                case Node.TEXT_NODE:
                    listener.characters(transaction, (TextImpl) node, currentPath);
                    break;
                case Node.ELEMENT_NODE:
                    listener.startElement(transaction, (ElementImpl) node, currentPath);
                    break;
                case Node.ATTRIBUTE_NODE:
                    listener.attribute(transaction, (AttrImpl) node, currentPath);
                    break;
                case Node.COMMENT_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE:
                    break;
                default:
                    LOG.debug("Unhandled node type: " + node.getNodeType());
            }
        }
        if(node.hasChildNodes()) {
            final int count = node.getChildCount();
            NodeId nodeId = node.getNodeId();
            for(int i = 0; i < count; i++) {
                final StoredNode child = iterator.next();
                oldNodeId = child.getNodeId();
                if(defragment) {
                    if(i == 0) {
                        nodeId = nodeId.newChild();
                    } else {
                        nodeId = nodeId.nextSibling();
                    }
                    child.setNodeId(nodeId);
                }
                copyNodes(transaction, iterator, child, currentPath, newDoc, defragment, index, listener, oldNodeId);
            }
        }
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            if(listener != null) {
                listener.endElement(transaction, (ElementImpl) node, currentPath);
            }
            currentPath.removeLastComponent();
        }
    }

    /**
     * Removes the Node Reference from the database.
     * The index will be updated later, i.e. after all nodes have been physically
     * removed. See {@link #endRemove(org.exist.storage.txn.Txn)}.
     * removeNode() just adds the node ids to the list in elementIndex
     * for later removal.
     */
    @Override
    public void removeNode(final Txn transaction, final StoredNode node, final NodePath currentPath,
                           final String content) {
        final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK, doc) {
            @Override
            public Object start() {
                final long address = node.getInternalAddress();
                if(StorageAddress.hasAddress(address)) {
                    domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), address);
                } else {
                    domDb.remove(transaction, new NodeRef(doc.getDocId(), node.getNodeId()));
                }
                return null;
            }
        }.run();
        notifyRemoveNode(node, currentPath, content);
        final NodeProxy p = new NodeProxy(node);
        QName qname;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE:
                qname = node.getQName();
                qname.setNameType(ElementValue.ELEMENT);
                final GeneralRangeIndexSpec spec1 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec1 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, spec1.getType(), NativeValueIndex.IDX_GENERIC, false);
                }
                QNameRangeIndexSpec qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpec != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, qnSpec.getType(),
                        NativeValueIndex.IDX_QNAME, false);
                }
                break;
            case Node.ATTRIBUTE_NODE:
                qname = node.getQName();
                qname.setNameType(ElementValue.ATTRIBUTE);
                currentPath.addComponent(qname);
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
                        final StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
                        while(tokenizer.hasMoreTokens()) {
                            valueIndex.storeAttribute(attr, tokenizer.nextToken(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, false);
                        }
                        break;
                    default:
                        // do nothing special
                }
                final RangeIndexSpec spec2 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec2 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute(attr, null, NativeValueIndex.WITHOUT_PATH, spec2, false);
                }
                qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpec != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute(attr, null, NativeValueIndex.WITHOUT_PATH, qnSpec, false);
                }
                currentPath.removeLastComponent();
                break;
            case Node.TEXT_NODE:
                break;
        }
    }

    @Override
    public void removeAllNodes(final Txn transaction, final StoredNode node, final NodePath currentPath,
                               final StreamListener listener) {
        final Iterator<StoredNode> iterator = getNodeIterator(node);
        iterator.next();
        final Stack<RemovedNode> stack = new Stack<>();
        collectNodesForRemoval(transaction, stack, iterator, listener, node, currentPath);
        while(!stack.isEmpty()) {
            final RemovedNode next = stack.pop();
            removeNode(transaction, next.node, next.path, next.content);
        }
    }

    private void collectNodesForRemoval(final Txn transaction, final Stack<RemovedNode> stack,
                                        final Iterator<StoredNode> iterator, final StreamListener listener, final StoredNode node, final NodePath currentPath) {
        RemovedNode removed;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE:
                final DocumentImpl doc = node.getDocument();
                String content = null;
                final GeneralRangeIndexSpec spec = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec != null) {
                    content = getNodeValue(node, false);
                } else {
                    final QNameRangeIndexSpec qnIdx = doc.getCollection().getIndexByQNameConfiguration(this, node.getQName());
                    if(qnIdx != null) {
                        content = getNodeValue(node, false);
                    }
                }
                removed = new RemovedNode(node, new NodePath(currentPath), content);
                stack.push(removed);
                if(listener != null) {
                    listener.startElement(transaction, (ElementImpl) node, currentPath);
                }
                if(node.hasChildNodes()) {
                    final int childCount = node.getChildCount();
                    for(int i = 0; i < childCount; i++) {
                        final StoredNode child = iterator.next();
                        if(child.getNodeType() == Node.ELEMENT_NODE) {
                            currentPath.addComponent(child.getQName());
                        }
                        collectNodesForRemoval(transaction, stack, iterator, listener, child, currentPath);
                        if(child.getNodeType() == Node.ELEMENT_NODE) {
                            currentPath.removeLastComponent();
                        }
                    }
                }
                if(listener != null) {
                    listener.endElement(transaction, (ElementImpl) node, currentPath);
                }
                break;
            case Node.TEXT_NODE:
                if(listener != null) {
                    listener.characters(transaction, (TextImpl) node, currentPath);
                }
                break;
            case Node.ATTRIBUTE_NODE:
                if(listener != null) {
                    listener.attribute(transaction, (AttrImpl) node, currentPath);
                }
                break;
        }
        if(node.getNodeType() != Node.ELEMENT_NODE) {
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
    @Override
    public void indexNode(final Txn transaction, final StoredNode node, final NodePath currentPath) {
        indexNode(transaction, node, currentPath, NodeProcessor.MODE_STORE);
    }

    public void indexNode(final Txn transaction, final StoredNode node, final NodePath currentPath, final int repairMode) {
        nodeProcessor.reset(transaction, node, currentPath, null, true);
        nodeProcessor.setMode(repairMode);
        nodeProcessor.index();
    }

    private boolean checkNodeTree(final Iterator<StoredNode> iterator, final StoredNode node, final StringBuilder buf) {
        if(buf != null) {
            if(buf.length() > 0) {
                buf.append(", ");
            }
            buf.append(node.getNodeId());
        }
        boolean docIsValid = true;
        if(node.hasChildNodes()) {
            final int count = node.getChildCount();
            if(buf != null) {
                buf.append('[').append(count).append(']');
            }
            StoredNode previous = null;
            for(int i = 0; i < count; i++) {
                StoredNode child = iterator.next();
                if(i > 0 && !(child.getNodeId().isSiblingOf(previous.getNodeId()) &&
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
                    //TODO : emergency exit ?
                }
                final NodeId parentId = child.getNodeId().getParentId();
                if(!parentId.equals(node.getNodeId())) {
                    LOG.fatal(child.getNodeId() + " is not a child of " + node.getNodeId());
                    docIsValid = false;
                }
                boolean check = checkNodeTree(iterator, child, buf);
                if(docIsValid) {
                    docIsValid = check;
                }
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
    private void scanNodes(final Txn transaction, final Iterator<StoredNode> iterator, final StoredNode node,
                           final NodePath currentPath, final int mode, final StreamListener listener) {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.addComponent(node.getQName());
        }
        indexNode(transaction, node, currentPath, mode);
        if(listener != null) {
            switch(node.getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    listener.characters(transaction, (CharacterDataImpl) node, currentPath);
                    break;
                case Node.ELEMENT_NODE:
                    listener.startElement(transaction, (ElementImpl) node, currentPath);
                    break;
                case Node.ATTRIBUTE_NODE:
                    listener.attribute(transaction, (AttrImpl) node, currentPath);
                    break;
                case Node.COMMENT_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE:
                    break;
                default:
                    LOG.debug("Unhandled node type: " + node.getNodeType());
            }
        }
        if(node.hasChildNodes()) {
            final int count = node.getChildCount();
            for(int i = 0; i < count; i++) {
                final StoredNode child = iterator.next();
                if(child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
                        "; children = " + node.getChildCount());
                } else {
                    scanNodes(transaction, iterator, child, currentPath, mode, listener);
                }
            }
        }
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            endElement(node, currentPath, null, mode == NodeProcessor.MODE_REMOVE);
            if(listener != null) {
                listener.endElement(transaction, (ElementImpl) node, currentPath);
            }
            currentPath.removeLastComponent();
        }
    }

    @Override
    public String getNodeValue(final StoredNode node, final boolean addWhitespace) {
        return (String) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
            @Override
            public Object start() {
                return domDb.getNodeValue(NativeBroker.this, node, addWhitespace);
            }
        }.run();
    }

    @Override
    public StoredNode objectWith(final Document doc, final NodeId nodeId) {
        return (StoredNode) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
            @Override
            public Object start() {
                final Value val = domDb.get(NativeBroker.this, new NodeProxy((DocumentImpl) doc, nodeId));
                if(val == null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Node " + nodeId + " not found. This is usually not an error.");
                    }
                    return null;
                }
                final StoredNode node = StoredNode.deserialize(val.getData(), 0, val.getLength(), (DocumentImpl) doc);
                node.setOwnerDocument((DocumentImpl) doc);
                node.setInternalAddress(val.getAddress());
                return node;
            }
        }.run();
    }

    @Override
    public StoredNode objectWith(final NodeProxy p) {
        if(!StorageAddress.hasAddress(p.getInternalAddress())) {
            return objectWith(p.getDocument(), p.getNodeId());
        }
        return (StoredNode) new DOMTransaction(this, domDb, Lock.READ_LOCK) {
            @Override
            public Object start() {
                // DocumentImpl sets the nodeId to DOCUMENT_NODE when it's trying to find its top-level
                // children (for which it doesn't persist the actual node ids), so ignore that.  Nobody else
                // should be passing DOCUMENT_NODE into here.
                final boolean fakeNodeId = p.getNodeId().equals(NodeId.DOCUMENT_NODE);
                final Value val = domDb.get(p.getInternalAddress(), false);
                if(val == null) {
                    LOG.debug("Node " + p.getNodeId() + " not found in document " + p.getDocument().getURI() +
                        "; docId = " + p.getDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()));
                    if(fakeNodeId) {
                        return null;
                    }
                } else {
                    final StoredNode node = StoredNode.deserialize(val.getData(), 0, val.getLength(), p.getDocument());
                    node.setOwnerDocument((DocumentImpl) p.getOwnerDocument());
                    node.setInternalAddress(p.getInternalAddress());
                    if(fakeNodeId) {
                        return node;
                    }
                    if(p.getDocument().getDocId() == node.getDocId() &&
                        p.getNodeId().equals(node.getNodeId())) {
                        return node;
                    }
                    LOG.debug(
                        "Node " + p.getNodeId() + " not found in document " + p.getDocument().getURI() +
                            "; docId = " + p.getDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()) +
                            "; found node " + node.getNodeId() + " instead"
                    );
                }
                // retry based on node id
                final StoredNode node = objectWith(p.getDocument(), p.getNodeId());
                if(node != null) {
                    p.setInternalAddress(node.getInternalAddress());
                }  // update proxy with correct address
                return node;
            }
        }.run();
    }

    @Override
    public void repair() throws PermissionDeniedException {
        if(pool.isReadOnly()) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        }

        LOG.info("Removing index files ...");
        notifyCloseAndRemove();
        try {
            pool.getIndexManager().removeIndexes();
        } catch(final DBException e) {
            LOG.warn("Failed to remove index files during repair: " + e.getMessage(), e);
        }

        LOG.info("Recreating index files ...");
        try {
            valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
        } catch(final DBException e) {
            LOG.warn("Exception during repair: " + e.getMessage(), e);
        }

        try {
            pool.getIndexManager().reopenIndexes();
        } catch(final DatabaseConfigurationException e) {
            LOG.warn("Failed to reopen index files after repair: " + e.getMessage(), e);
        }

        initIndexModules();
        LOG.info("Reindexing database files ...");
        //Reindex from root collection
        reindexCollection(null, getCollection(XmldbURI.ROOT_COLLECTION_URI), NodeProcessor.MODE_REPAIR);
    }

    @Override
    public void repairPrimary() {
        rebuildIndex(DOM_DBX_ID);
        rebuildIndex(COLLECTIONS_DBX_ID);
    }

    protected void rebuildIndex(final byte indexId) {
        final BTree btree = getStorage(indexId);
        final Lock lock = btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);

            LOG.info("Rebuilding index " + btree.getFile().getName());
            btree.rebuild();
            LOG.info("Index " + btree.getFile().getName() + " was rebuilt.");
        } catch(LockException | IOException | TerminatedException | DBException e) {
            LOG.warn("Caught error while rebuilding core index " + btree.getFile().getName() + ": " + e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    @Override
    public void flush() {
        notifyFlush();
        try {
            pool.getSymbols().flush();
        } catch(final EXistException e) {
            LOG.warn(e);
        }
        indexController.flush();
        nodesCount = 0;
    }

    @Override
    public void sync(final int syncEvent) {
        if(isReadOnly()) {
            return;
        }
        try {
            new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
                @Override
                public Object start() {
                    try {
                        domDb.flush();
                    } catch(final DBException e) {
                        LOG.warn("error while flushing dom.dbx", e);
                    }
                    return null;
                }
            }.run();
            if(syncEvent == Sync.MAJOR_SYNC) {
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    collectionsDb.flush();
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on " + collectionsDb.getFile().getName(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }
                notifySync();
                pool.getIndexManager().sync();
                final NumberFormat nf = NumberFormat.getNumberInstance();
                LOG_STATS.info("Memory: " + nf.format(run.totalMemory() / 1024) + "K total; " +
                    nf.format(run.maxMemory() / 1024) + "K max; " +
                    nf.format(run.freeMemory() / 1024) + "K free");
                domDb.printStatistics();
                collectionsDb.printStatistics();
                notifyPrintStatistics();
            }
        } catch(final DBException dbe) {
            dbe.printStackTrace();
            LOG.warn(dbe);
        }
    }

    @Override
    public void shutdown() {
        try {
            flush();
            sync(Sync.MAJOR_SYNC);
            domDb.close();
            collectionsDb.close();
            notifyClose();
        } catch(final Exception e) {
            LOG.warn(e.getMessage(), e);
        }
        super.shutdown();
    }

    /**
     * check available memory
     */
    @Override
    public void checkAvailableMemory() {
        if(nodesCountThreshold <= 0) {
            if(nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
                if(run.totalMemory() >= run.maxMemory() && run.freeMemory() < pool.getReservedMem()) {
                    final NumberFormat nf = NumberFormat.getNumberInstance();
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
        } else if(nodesCount > nodesCountThreshold) {
            flush();
            nodesCount = 0;
        }
    }

    //TODO UNDERSTAND : why not use shutdown ? -pb
    @Override
    public void closeDocument() {
        new DOMTransaction(this, domDb, Lock.WRITE_LOCK) {
            @Override
            public Object start() {
                domDb.closeDocument();
                return null;
            }
        }.run();
    }

    public final static class NodeRef extends Value {

        public static int OFFSET_DOCUMENT_ID = 0;
        public static int OFFSET_NODE_ID = OFFSET_DOCUMENT_ID + DocumentImpl.LENGTH_DOCUMENT_ID;

        public NodeRef(final int docId) {
            len = DocumentImpl.LENGTH_DOCUMENT_ID;
            data = new byte[len];
            ByteConversion.intToByte(docId, data, OFFSET_DOCUMENT_ID);
            pos = OFFSET_DOCUMENT_ID;
        }

        public NodeRef(final int docId, final NodeId nodeId) {
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
        final StoredNode node;
        final String content;
        final NodePath path;

        RemovedNode(final StoredNode node, final NodePath path, final String content) {
            this.node = node;
            this.path = path;
            this.content = content;
        }
    }

    /**
     * Delegate for Node Processing : indexing
     */
    private class NodeProcessor {

        final static int MODE_STORE = 0;
        final static int MODE_REPAIR = 1;
        final static int MODE_REMOVE = 2;

        private Txn transaction;
        private StoredNode node;
        private NodePath currentPath;

        /**
         * work variables
         */
        private DocumentImpl doc;
        private long address;

        private IndexSpec idxSpec;
        //private FulltextIndexSpec ftIdx;
        private int level;
        private int mode = MODE_STORE;

        /**
         * overall switch to activate fulltext indexation
         */
        private boolean fullTextIndex = true;

        NodeProcessor() {
            //ignore
        }

        public void reset(final Txn transaction, final StoredNode node, final NodePath currentPath, IndexSpec indexSpec, final boolean fullTextIndex) {
            if(node.getNodeId() == null) {
                LOG.warn("illegal node: " + node.getNodeName());
            }
            //TODO : why continue processing ? return ? -pb
            this.transaction = transaction;
            this.node = node;
            this.currentPath = currentPath;
            this.mode = MODE_STORE;
            doc = (DocumentImpl) node.getOwnerDocument();
            address = node.getInternalAddress();
            if(indexSpec == null) {
                indexSpec = doc.getCollection().getIndexConfiguration(NativeBroker.this);
            }
            idxSpec = indexSpec;
            //ftIdx = idxSpec == null ? null : idxSpec.getFulltextIndexSpec();
            level = node.getNodeId().getTreeLevel();
            this.fullTextIndex = fullTextIndex;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        /**
         * Updates the various indices
         */
        public void doIndex() {
            //TODO : resolve URI !
            //final boolean isTemp = XmldbURI.TEMP_COLLECTION_URI.equalsInternal(((DocumentImpl) node.getOwnerDocument()).getCollection().getURI());
            int indexType;
            switch(node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    //Compute index type
                    //TODO : let indexers OR it themselves
                    //we'd need to notify the ElementIndexer at the very end then...
                    indexType = RangeIndexSpec.NO_INDEX;
                    if(idxSpec != null && idxSpec.getIndexByPath(currentPath) != null) {
                        indexType |= idxSpec.getIndexByPath(currentPath).getIndexType();
                    }
                    if(idxSpec != null) {
                        final QNameRangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                        if(qnIdx != null) {
                            indexType |= RangeIndexSpec.QNAME_INDEX;
                            if(!RangeIndexSpec.hasRangeIndex(indexType)) {
                                indexType |= qnIdx.getIndexType();
                            }
                        }
                    }
                    ((ElementImpl) node).setIndexType(indexType);
                    //notifyStartElement((ElementImpl)node, currentPath, fullTextIndex);
                    break;

                case Node.ATTRIBUTE_NODE:
                    final QName qname = node.getQName();
                    if(currentPath != null) {
                        currentPath.addComponent(qname);
                    }
                    //Compute index type
                    //TODO : let indexers OR it themselves
                    //we'd need to notify the ElementIndexer at the very end then...
                    indexType = RangeIndexSpec.NO_INDEX;
                    if(idxSpec != null) {
                        final RangeIndexSpec rangeSpec = idxSpec.getIndexByPath(currentPath);
                        if(rangeSpec != null) {
                            indexType |= rangeSpec.getIndexType();
                        }
                        if(rangeSpec != null) {
                            valueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath,
                                NativeValueIndex.WITHOUT_PATH,
                                rangeSpec, mode == MODE_REMOVE);
                        }
                        final QNameRangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                        if(qnIdx != null) {
                            indexType |= RangeIndexSpec.QNAME_INDEX;
                            if(!RangeIndexSpec.hasRangeIndex(indexType)) {
                                indexType |= qnIdx.getIndexType();
                            }
                            valueIndex.setDocument((DocumentImpl) node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath, NativeValueIndex.WITHOUT_PATH,
                                qnIdx, mode == MODE_REMOVE);
                        }
                    }
                    final NodeProxy tempProxy = new NodeProxy(doc, node.getNodeId(), address);
                    tempProxy.setIndexType(indexType);
                    qname.setNameType(ElementValue.ATTRIBUTE);
                    final AttrImpl attr = (AttrImpl) node;
                    attr.setIndexType(indexType);
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
                            final StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
                            while(tokenizer.hasMoreTokens()) {
                                valueIndex.storeAttribute(attr, tokenizer.nextToken(), currentPath, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, mode == MODE_REMOVE);
                            }
                            break;

                        default:
                            // do nothing special
                    }
                    if(currentPath != null) {
                        currentPath.removeLastComponent();
                    }
                    break;

                case Node.TEXT_NODE:
                    notifyStoreText((TextImpl) node, currentPath,
                        fullTextIndex ? NativeTextEngine.DO_NOT_TOKENIZE : NativeTextEngine.TOKENIZE);
                    break;
            }
        }

        /**
         * Stores this node into the database, if it's an element
         */
        public void store() {
            final DocumentImpl doc = (DocumentImpl) node.getOwnerDocument();
            if(mode == MODE_STORE && node.getNodeType() == Node.ELEMENT_NODE && level <= defaultIndexDepth) {
                //TODO : used to be this, but NativeBroker.this avoids an owner change
                new DOMTransaction(NativeBroker.this, domDb, Lock.WRITE_LOCK) {
                    @Override
                    public Object start() throws ReadOnlyException {
                        try {
                            domDb.addValue(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), address);
                        } catch(final BTreeException | IOException e) {
                            LOG.warn(EXCEPTION_DURING_REINDEX, e);
                        }
                        return null;
                    }
                }.run();
            }
        }

        /**
         * check available memory
         */
        private void checkAvailableMemory() {
            if(mode != MODE_REMOVE && nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
                if(run.totalMemory() >= run.maxMemory() && run.freeMemory() < pool.getReservedMem()) {
                    //LOG.info("total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                    flush();
                    System.gc();
                    LOG.info("total memory: " + run.totalMemory() + "; free: " + run.freeMemory());
                }
                nodesCount = 0;
            }
        }

        /**
         * Updates the various indices and stores this node into the database
         */
        public void index() {
            ++nodesCount;
            checkAvailableMemory();
            doIndex();
            store();
        }
    }

    private final class DocumentCallback implements BTreeCallback {

        private final Collection.InternalAccess collectionInternalAccess;

        private DocumentCallback(final Collection.InternalAccess collectionInternalAccess) {
            this.collectionInternalAccess = collectionInternalAccess;
        }

        @Override
        public boolean indexInfo(final Value key, final long pointer) throws TerminatedException {

            try {
                final byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                final VariableByteInput is = collectionsDb.getAsStream(pointer);

                final DocumentImpl doc;
                if(type == DocumentImpl.BINARY_FILE) {
                    doc = new BinaryDocument(pool);
                } else {
                    doc = new DocumentImpl(pool);
                }
                doc.read(is);

                collectionInternalAccess.addDocument(doc);
            } catch(final EXistException | IOException e) {
                LOG.error("Exception while reading document data", e);
            }

            return true;
        }
    }
}
