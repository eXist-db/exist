/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DOMIndexer;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.persistent.TextImpl;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection.CollectionEntry;
import org.exist.collections.Collection.SubCollectionEntry;
import org.exist.collections.triggers.*;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.StructuralIndex;
import org.exist.numbering.NodeId;
import org.exist.security.*;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.IEmbeddedXMLStreamReader;
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
import org.exist.storage.journal.*;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.persistent.StoredNode;
import org.exist.storage.dom.INodeIterator;
import com.evolvedbinary.j8fu.tuple.Tuple2;

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
 * @link org.exist.storage.NativeValueIndex
 * @link org.exist.storage.NativeValueIndexByQName
 *
 * This class dispatches the various events (defined by the methods
 * of @link org.exist.storage.ContentLoadingObserver) to indexing classes.
 */
public class NativeBroker extends DBBroker {

    public final static String EXIST_STATISTICS_LOGGER = "org.exist.statistics";

    protected final static Logger LOG_STATS = LogManager.getLogger(EXIST_STATISTICS_LOGGER);

    public final static byte LOG_RENAME_BINARY = 0x40;
    public final static byte LOG_CREATE_BINARY = 0x41;
    public final static byte LOG_UPDATE_BINARY = 0x42;

    static {
        LogEntryTypes.addEntryType(LOG_RENAME_BINARY, RenameBinaryLoggable::new);
        LogEntryTypes.addEntryType(LOG_CREATE_BINARY, CreateBinaryLoggable::new);
        LogEntryTypes.addEntryType(LOG_UPDATE_BINARY, UpdateBinaryLoggable::new);
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

    private static final String EXCEPTION_DURING_REINDEX = "exception during reindex";
    private static final String DATABASE_IS_READ_ONLY = "Database is read-only";

    public static final String DEFAULT_DATA_DIR = "data";
    public static final int DEFAULT_INDEX_DEPTH = 1;

    /** check available memory after storing DEFAULT_NODES_BEFORE_MEMORY_CHECK nodes */
    public static final int DEFAULT_NODES_BEFORE_MEMORY_CHECK = 500;

    public static final int OFFSET_COLLECTION_ID = 0;

    public final static String INIT_COLLECTION_CONFIG = "collection.xconf.init";

    /** in-memory buffer size to use when copying binary resources */
    private final static int BINARY_RESOURCE_BUF_SIZE = 65536;

    /** the database files */
    private final CollectionStore collectionsDb;
    private final DOMFile domDb;

    /** the index processors */
    private NativeValueIndex valueIndex;

    private final IndexSpec indexConfiguration;

    private int defaultIndexDepth;

    private final Serializer xmlSerializer;

    /** used to count the nodes inserted after the last memory check */
    private int nodesCount = 0;

    private int nodesCountThreshold = DEFAULT_NODES_BEFORE_MEMORY_CHECK;

    private final Path dataDir;
    private final Path fsDir;
    private final Optional<Path> fsJournalDir;
    private int pageSize;

    private final byte prepend;

    private final Runtime run = Runtime.getRuntime();

    private NodeProcessor nodeProcessor = new NodeProcessor();

    private IEmbeddedXMLStreamReader streamReader = null;

    private final Optional<JournalManager> logManager;

    private boolean incrementalDocIds = false;

    /** initialize database; read configuration, etc. */
    public NativeBroker(final BrokerPool pool, final Configuration config) throws EXistException {
        super(pool, config);
        this.logManager = pool.getJournalManager();
        LOG.debug("Initializing broker " + hashCode());

        final String prependDB = (String) config.getProperty("db-connection.prepend-db");
        if("always".equalsIgnoreCase(prependDB)) {
            this.prepend = PREPEND_DB_ALWAYS;
        } else if("never".equalsIgnoreCase(prependDB)) {
            this.prepend = PREPEND_DB_NEVER;
        } else {
            this.prepend = PREPEND_DB_AS_NEEDED;
        }

        this.dataDir = config.getProperty(BrokerPool.PROPERTY_DATA_DIR, Paths.get(DEFAULT_DATA_DIR));

        final Path fs = dataDir.resolve("fs");
        try {
            this.fsDir = Files.createDirectories(fs);
        } catch(final IOException ioe) {
            throw new EXistException("Cannot make collection filesystem directory: " + fs.toAbsolutePath().toString(), ioe);
        }

        if(pool.isRecoveryEnabled()) {
            final Path fsJournal = dataDir.resolve("fs.journal");
            try {
                this.fsJournalDir = Optional.of(Files.createDirectories(fsJournal));
            } catch(final IOException ioe) {
                throw new EXistException("Cannot make collection filesystem directory: " + fsJournal.toAbsolutePath().toString(), ioe);
            }
        } else {
            this.fsJournalDir = Optional.empty();
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

        this.indexConfiguration = (IndexSpec) config.getProperty(Indexer.PROPERTY_INDEXER_CONFIG);
        this.xmlSerializer = new NativeSerializer(this, config);

        try {
            pushSubject(pool.getSecurityManager().getSystemSubject());
            //TODO : refactor so that we can,
            //1) customize the different properties (file names, cache settings...)
            //2) have a consistent READ-ONLY behaviour (based on *mandatory* files ?)
            //3) have consistent file creation behaviour (we can probably avoid some unnecessary files)
            //4) use... *customized* factories for a better index extensibility ;-)
            // Initialize DOM storage
            final DOMFile configuredDomFile = (DOMFile) config.getProperty(DOMFile.getConfigKeyForFile());
            if(configuredDomFile != null) {
                this.domDb = configuredDomFile;
            } else {
                this.domDb = new DOMFile(pool, DOM_DBX_ID, dataDir, config);
            }
            if(domDb.isReadOnly()) {
                LOG.warn(FileUtils.fileName(domDb.getFile()) + " is read-only!");
                pool.setReadOnly();
            }

            //Initialize collections storage
            final CollectionStore configuredCollectionsDb = (CollectionStore) config.getProperty(CollectionStore.getConfigKeyForFile());
            if(configuredCollectionsDb != null) {
                this.collectionsDb = configuredCollectionsDb;
            } else {
                this.collectionsDb = new CollectionStore(pool, COLLECTIONS_DBX_ID, dataDir, config);
            }
            if(collectionsDb.isReadOnly()) {
                LOG.warn(FileUtils.fileName(collectionsDb.getFile()) + " is read-only!");
                pool.setReadOnly();
            }

            this.valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
            if(isReadOnly()) {
                LOG.warn(DATABASE_IS_READ_ONLY);
            }
        } catch(final DBException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e);
        } finally {
            popSubject();
        }
    }

    /**
     * Get the filesystem directory
     *
     * @return The filesystem directory
     */
    protected Path getFsDir() {
        return fsDir;
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

    private void notifyRemoveNode(final NodeHandle node, final NodePath currentPath, final String content) {
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

    private void notifyStoreText(final TextImpl text, final NodePath currentPath) {
        for(final ContentLoadingObserver observer : contentLoadingObservers) {
            observer.storeText(text, currentPath);
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
    public <T extends IStoredNode> void endElement(final IStoredNode<T> node, final NodePath currentPath, String content, final boolean remove) {
        final int indexType = ((ElementImpl) node).getIndexType();
        //TODO : do not care about the current code redundancy : this will move in the (near) future
        // TODO : move to NativeValueIndex
        if(RangeIndexSpec.hasRangeIndex(indexType)) {
            node.setQName(new QName(node.getQName(), ElementValue.ELEMENT));
            if(content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            valueIndex.setDocument(node.getOwnerDocument());
            valueIndex.storeElement((ElementImpl) node, content, RangeIndexSpec.indexTypeToXPath(indexType),
                NativeValueIndex.IndexType.GENERIC, remove);
        }

        // TODO : move to NativeValueIndexByQName 
        if(RangeIndexSpec.hasQNameIndex(indexType)) {
            node.setQName(new QName(node.getQName(), ElementValue.ELEMENT));
            if(content == null) {
                //NodeProxy p = new NodeProxy(node);
                //if (node.getOldInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                //    p.setInternalAddress(node.getOldInternalAddress());
                content = getNodeValue(node, false);
                //Curious... I assume getNodeValue() needs the old address
                //p.setInternalAddress(node.getInternalAddress());
            }
            valueIndex.setDocument(node.getOwnerDocument());
            valueIndex.storeElement((ElementImpl) node, content, RangeIndexSpec.indexTypeToXPath(indexType),
                NativeValueIndex.IndexType.QNAME, remove);
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
     * must be called after one or more call to {@link #removeNode(Txn, IStoredNode, NodePath, String)}.
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

            // do not use try-with-resources here, closing the OutputStream will close the entire backup
//            try(final OutputStream os = backup.newEntry(FileUtils.fileName(paged.getFile()))) {
            try {
                final OutputStream os = backup.newEntry(FileUtils.fileName(paged.getFile()));
                paged.backupToStream(os);
            } finally {
                backup.closeEntry();
            }
        }
        pool.getSymbols().backupToArchive(backup);
        backupBinary(backup, getFsDir(), "");
        pool.getIndexManager().backupToArchive(backup);
        //TODO backup counters
        //TODO USE zip64 or tar to create snapshots larger then 4Gb
    }

    private void backupBinary(final RawDataBackup backup, final Path file, final String path) throws IOException {
        final String thisPath = path + "/" + file.getFileName();
        if(Files.isDirectory(file)) {
            for(final Path p : FileUtils.list(file)) {
                backupBinary(backup, p, thisPath);
            }
        } else {
            // do not use try-with-resources here, closing the OutputStream will close the entire backup
//            try(final OutputStream os = backup.newEntry(thisPath)) {
            try {
                final OutputStream os = backup.newEntry(thisPath);
                Files.copy(file, os);
            } finally {
                backup.closeEntry();
            }
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
    public IEmbeddedXMLStreamReader getXMLStreamReader(final NodeHandle node, final boolean reportAttributes)
        throws IOException, XMLStreamException {
        if(streamReader == null) {
            final RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
            streamReader = new EmbeddedXMLStreamReader(this, node.getOwnerDocument(), iterator, node, reportAttributes);
        } else {
            streamReader.reposition(this, node, reportAttributes);
        }
        return streamReader;
    }

    @Override
    public IEmbeddedXMLStreamReader newXMLStreamReader(final NodeHandle node, final boolean reportAttributes)
        throws IOException, XMLStreamException {
        final RawNodeIterator iterator = new RawNodeIterator(this, domDb, node);
        return new EmbeddedXMLStreamReader(this, node.getOwnerDocument(), iterator, null, reportAttributes);
    }

    @Override
    public INodeIterator getNodeIterator(final NodeHandle node) {
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

    @Override
    public Serializer newSerializer(List<String> chainOfReceivers) {
        return new NativeSerializer(this, getConfiguration(), chainOfReceivers);
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
    private Tuple2<Boolean, Collection> getOrCreateTempCollection(final Txn transaction)
        throws LockException, PermissionDeniedException, IOException, TriggerException {
        try {
            pushSubject(pool.getSecurityManager().getSystemSubject());
            final Tuple2<Boolean, Collection> temp = getOrCreateCollectionExplicit(transaction, XmldbURI.TEMP_COLLECTION_URI);
            if (temp._1) {
                temp._2.setPermissions(0771);
                saveCollection(transaction, temp._2);
            }
            return temp;
        } finally {
            popSubject();
        }
    }

    private final String readInitCollectionConfig() {
        final Path fInitCollectionConfig = pool.getConfiguration().getExistHome()
                .map(h -> h.resolve(INIT_COLLECTION_CONFIG))
                .orElse(Paths.get(INIT_COLLECTION_CONFIG));

        if(Files.isRegularFile(fInitCollectionConfig)) {
            try(final InputStream is = Files.newInputStream(fInitCollectionConfig)) {
                final StringBuilder initCollectionConfig = new StringBuilder();

                int read = -1;
                final byte buf[] = new byte[1024];
                while((read = is.read(buf)) != -1) {
                    initCollectionConfig.append(new String(buf, 0, read));
                }

                return initCollectionConfig.toString();
            } catch(final IOException ioe) {
                LOG.error(ioe.getMessage(), ioe);
            }
        }

        return null;
    }

    @Override
    public Collection getOrCreateCollection(final Txn transaction, XmldbURI name) throws PermissionDeniedException, IOException, TriggerException {
        return getOrCreateCollectionExplicit(transaction, name)._2;
    }

    /**
     * @return A tuple whose first boolean value is set to true if the
     * collection was created, or false if the collection already existed
     */
    private Tuple2<Boolean, Collection> getOrCreateCollectionExplicit(final Txn transaction, XmldbURI name) throws PermissionDeniedException, IOException, TriggerException {
        name = prepend(name.normalizeCollectionPath());

        final CollectionCache collectionsCache = pool.getCollectionsCache();

        boolean created = false;
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

                    current = new MutableCollection(this, XmldbURI.ROOT_COLLECTION_URI);
                    current.setId(getNextCollectionId(transaction));
                    current.setCreationTime(System.currentTimeMillis());

                    if(transaction != null) {
                        transaction.acquireLock(current.getLock(), LockMode.WRITE_LOCK);
                    }

                    //TODO : acquire lock manually if transaction is null ?
                    saveCollection(transaction, current);
                    created = true;

                    //adding to make it available @ afterCreateCollection
                    collectionsCache.add(current);

                    trigger.afterCreateCollection(this, transaction, current);

                    //import an initial collection configuration
                    try {
                        final String initCollectionConfig = readInitCollectionConfig();
                        if(initCollectionConfig != null) {
                            CollectionConfigurationManager collectionConfigurationManager = pool.getConfigurationManager();
                            if(collectionConfigurationManager == null) {
                                if(pool.getConfigurationManager() == null) {
                                    throw new IllegalStateException();
                                    //might not yet have been initialised
                                    //pool.initCollectionConfigurationManager(this);
                                }
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
                    if(current.hasChildCollectionNoLock(this, temp)) {
                        current = getCollection(path);
                        if(current == null) {
                            LOG.error("Collection '" + path + "' found in subCollections set but is missing from collections.dbx!");
                        }
                    } else {

                        if(isReadOnly()) {
                            throw new IOException(DATABASE_IS_READ_ONLY);
                        }

                        if(!current.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' not allowed to write to collection '" + current.getURI() + "'");
                        }

                        if(!current.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                            LOG.error("Permission denied to create collection '" + path + "'");
                            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' not allowed to execute to collection '" + current.getURI() + "'");
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

                        sub = new MutableCollection(this, path);
                        //inherit the group to the sub-collection if current collection is setGid
                        if(current.getPermissions().isSetGid()) {
                            sub.getPermissions().setGroupFrom(current.getPermissions()); //inherit group
                            sub.getPermissions().setSetGid(true); //inherit setGid bit
                        }
                        sub.setId(getNextCollectionId(transaction));

                        if(transaction != null) {
                            transaction.acquireLock(sub.getLock(), LockMode.WRITE_LOCK);
                        }

                        //TODO : acquire lock manually if transaction is null ?
                        current.addCollection(this, sub, true);
                        saveCollection(transaction, current);
                        created = true;

                        //adding to make it available @ afterCreateCollection
                        collectionsCache.add(sub);

                        trigger.afterCreateCollection(this, transaction, sub);

                        current = sub;
                    }
                }
                return new Tuple2<>(created, current);
            } catch(final LockException e) {
                LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
                return null;
            } catch(final ReadOnlyException e) {
                throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
            }
        }
    }

    @Override
    public Collection getCollection(final XmldbURI uri) throws PermissionDeniedException {
        return openCollection(uri, LockMode.NO_LOCK);
    }

    @Override
    public Collection openCollection(final XmldbURI uri, final LockMode lockMode) throws PermissionDeniedException {
        return openCollection(uri, BFile.UNKNOWN_ADDRESS, lockMode);
    }


    @Override
    public List<String> findCollectionsMatching(final String regexp) {

        final List<String> collections = new ArrayList<>();

        final Pattern p = Pattern.compile(regexp);
        final Matcher m = p.matcher("");

        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.READ_LOCK);

            //TODO write a regexp lookup for key data in BTree.query
            //final IndexQuery idxQuery = new IndexQuery(IndexQuery.REGEXP, regexp);
            //List<Value> keys = collectionsDb.findKeysByCollectionName(idxQuery);

            final List<Value> keys = collectionsDb.getKeys();
            for(final Value key : keys) {
                final byte data[] = key.getData();
                if(data[0] == CollectionStore.KEY_TYPE_COLLECTION) {
                    final String collectionName = UTF8.decode(data, 1, data.length - 1).toString();
                    m.reset(collectionName);

                    if (m.matches()) {
                        collections.add(collectionName);
                    }
                }
            }
        } catch(final UnsupportedEncodingException e) {
            //LOG.error("Unable to encode '" + uri + "' in UTF-8");
            //return null;
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
            //return null;
        } catch(final TerminatedException | IOException | BTreeException e) {
            LOG.error(e.getMessage(), e);
            //return null;
        } finally {
            lock.release(LockMode.READ_LOCK);
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
                    lock.acquire(LockMode.READ_LOCK);

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
                    LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
                } catch(final IOException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    lock.release(LockMode.READ_LOCK);
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
    private Collection openCollection(XmldbURI uri, final long address, final LockMode lockMode) throws PermissionDeniedException {
        uri = prepend(uri.toCollectionPathURI());
        //We *must* declare it here (see below)
        Collection collection;
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            collection = collectionsCache.get(uri);
            if(collection == null) {
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(LockMode.READ_LOCK);
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
                    collection = MutableCollection.load(this, uri, is);

                    collectionsCache.add(collection);

                    //TODO : rethrow exceptions ? -pb
                } catch(final UnsupportedEncodingException e) {
                    LOG.error("Unable to encode '" + uri + "' in UTF-8");
                    return null;
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
                    return null;
                } catch(final IOException e) {
                    LOG.error(e.getMessage(), e);
                    return null;
                } finally {
                    lock.release(LockMode.READ_LOCK);
                }
            } else {
                if(!collection.getURI().equalsInternal(uri)) {
                    LOG.error("The collection received from the cache is not the requested: " + uri +
                        "; received: " + collection.getURI());
                }
                collectionsCache.add(collection);

                if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                    throw new PermissionDeniedException("Permission denied to open collection: " + collection.getURI().toString() + " by " + getCurrentSubject().getName());
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
        if(lockMode != LockMode.NO_LOCK) {
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

        if(!src.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " by " + getCurrentSubject().getName());
        }

        final Collection dest = getCollection(destUri);
        final XmldbURI newDestUri = destUri.append(newName);
        final Collection newDest = getCollection(newDestUri);

        if(dest != null) {
            //if(!dest.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.WRITE | Permission.READ)) {
            //TODO do we really need WRITE permission on the dest?
            if(!dest.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + dest.getURI() + " by " + getCurrentSubject().getName());
            }

            if(newDest != null) {
                //TODO why do we need READ access on the dest collection?
                /*if(!dest.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.READ)) {
                    throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + dest.getURI() + " by " + getCurrentSubject().getName());
                }*/

                //if(newDest.isEmpty(this)) {
                if(!newDest.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " to " + newDest.getURI() + " by " + getCurrentSubject().getName());
                }
                //}
            }
        }

        for(final Iterator<DocumentImpl> itSrcSubDoc = src.iterator(this); itSrcSubDoc.hasNext(); ) {
            final DocumentImpl srcSubDoc = itSrcSubDoc.next();
            if(!srcSubDoc.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " for resource " + srcSubDoc.getURI() + " by " + getCurrentSubject().getName());
            }

            //if the destination resource exists, we must have write access to replace it's metadata etc. (this follows the Linux convention)
            if(newDest != null && !newDest.isEmpty(this)) {
                final DocumentImpl newDestSubDoc = newDest.getDocument(this, srcSubDoc.getFileURI()); //TODO check this uri is just the filename!
                if(newDestSubDoc != null) {
                    if(!newDestSubDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("Permission denied to copy collection " + src.getURI() + " for resource " + newDestSubDoc.getURI() + " by " + getCurrentSubject().getName());
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
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        //TODO : resolve URIs !!!
        if(newName != null && newName.numSegments() != 1) {
            throw new PermissionDeniedException("New collection name must have one segment!");
        }

        final XmldbURI srcURI = collection.getURI();
        final XmldbURI dstURI = destination.getURI().append(newName);

        if(collection.getURI().equals(dstURI)) {
            throw new PermissionDeniedException("Cannot copy collection to itself '" + collection.getURI() + "'.");
        }
        if(collection.getId() == destination.getId()) {
            throw new PermissionDeniedException("Cannot copy collection to itself '" + collection.getURI() + "'.");
        }
        if(isSubCollection(collection, destination)) {
            throw new PermissionDeniedException("Cannot copy collection '" + collection.getURI() + "' to it child collection '"+destination.getURI()+"'.");
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            final Lock lock = collectionsDb.getLock();
            try {
                pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_COPY_COLLECTION, collection.getURI());
                lock.acquire(LockMode.WRITE_LOCK);

                //recheck here because now under 'synchronized(collectionsCache)'
                if(isSubCollection(collection, destination)) {
                    throw new PermissionDeniedException("Cannot copy collection '" + collection.getURI() + "' to it child collection '"+destination.getURI()+"'.");
                }

                final XmldbURI parentName = collection.getParentURI();
                final Collection parent = parentName == null ? collection : getCollection(parentName);

                final CollectionTrigger trigger = new CollectionTriggers(this, parent);
                trigger.beforeCopyCollection(this, transaction, collection, dstURI);

                //atomically check all permissions in the tree to ensure a copy operation will succeed before starting copying
                checkPermissionsForCopy(collection, destination.getURI(), newName);

                final DocumentTrigger docTrigger = new DocumentTriggers(this);

                final Collection newCollection = doCopyCollection(transaction, docTrigger, collection, destination, newName, false);

                trigger.afterCopyCollection(this, transaction, newCollection, srcURI);
            } finally {
                lock.release(LockMode.WRITE_LOCK);
                pool.getProcessMonitor().endJob();
            }
        }
    }

    private Collection doCopyCollection(final Txn transaction, final DocumentTrigger trigger, final Collection collection, final Collection destination, XmldbURI newName, final boolean copyCollectionMode) throws PermissionDeniedException, IOException, EXistException, TriggerException, LockException {

        if(newName == null) {
            newName = collection.getURI().lastSegment();
        }
        newName = destination.getURI().append(newName);

        if(LOG.isDebugEnabled()) {
            LOG.debug("Copying collection to '" + newName + "'");
        }

        final Tuple2<Boolean, Collection> destCollection = getOrCreateCollectionExplicit(transaction, newName);

        //if required, copy just the mode and acl of the permissions to the dest collection
        if(copyCollectionMode && destCollection._1) {
            final Permission srcPerms = collection.getPermissions();
            final Permission destPerms = destCollection._2.getPermissions();
            copyModeAndAcl(srcPerms, destPerms);
        }

        for(final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
            final DocumentImpl child = i.next();

            if(LOG.isDebugEnabled()) {
                LOG.debug("Copying resource: '" + child.getURI() + "'");
            }

            //TODO The code below seems quite different to that in NativeBroker#copyResource presumably should be the same?


            final XmldbURI newUri = destCollection._2.getURI().append(child.getFileURI());
            trigger.beforeCopyDocument(this, transaction, child, newUri);

            //are we overwriting an existing document?
            final CollectionEntry oldDoc;
            if(destCollection._2.hasDocument(this, child.getFileURI())) {
                oldDoc = destCollection._2.getResourceEntry(this, child.getFileURI().toString());
            } else {
                oldDoc = null;
            }

            DocumentImpl createdDoc;
            if(child.getResourceType() == DocumentImpl.XML_FILE) {
                //TODO : put a lock on newDoc ?
                final DocumentImpl newDoc = new DocumentImpl(pool, destCollection._2, child.getFileURI());
                newDoc.copyOf(child, false);
                if(oldDoc != null) {
                    //preserve permissions from existing doc we are replacing
                    newDoc.setPermissions(oldDoc.getPermissions()); //TODO use newDoc.copyOf(oldDoc) ideally, but we cannot currently access oldDoc without READ access to it, which we may not have (and should not need for this)!
                } else {
                    //copy just the mode and acl of the permissions to the dest document
                    final Permission srcPerm = child.getPermissions();
                    final Permission destPerm = newDoc.getPermissions();
                    copyModeAndAcl(srcPerm, destPerm);
                }

                newDoc.setDocId(getNextResourceId(transaction, destination));
                copyXMLResource(transaction, child, newDoc);
                storeXMLResource(transaction, newDoc);
                destCollection._2.addDocument(transaction, this, newDoc);

                createdDoc = newDoc;
            } else {
                final BinaryDocument newDoc = new BinaryDocument(pool, destCollection._2, child.getFileURI());
                newDoc.copyOf(child, false);
                if(oldDoc != null) {
                    //preserve permissions from existing doc we are replacing
                    newDoc.setPermissions(oldDoc.getPermissions()); //TODO use newDoc.copyOf(oldDoc) ideally, but we cannot currently access oldDoc without READ access to it, which we may not have (and should not need for this)!
                }
                newDoc.setDocId(getNextResourceId(transaction, destination));

                try(final InputStream is = getBinaryResource((BinaryDocument) child)) {
                    storeBinaryResource(transaction, newDoc, is);
                }
                storeXMLResource(transaction, newDoc);
                destCollection._2.addDocument(transaction, this, newDoc);

                createdDoc = newDoc;
            }

            trigger.afterCopyDocument(this, transaction, createdDoc, child.getURI());
        }
        saveCollection(transaction, destCollection._2);

        final XmldbURI name = collection.getURI();
        for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
            final XmldbURI childName = i.next();
            //TODO : resolve URIs ! collection.getURI().resolve(childName)
            Collection child = null;
            try {
                child = openCollection(name.append(childName), LockMode.READ_LOCK);
                if (child == null) {
                    LOG.warn("Child collection '" + childName + "' not found");
                } else {
                    doCopyCollection(transaction, trigger, child, destCollection._2, childName, true);
                }
            } finally {
                if(child != null) {
                    child.release(LockMode.READ_LOCK);
                }
            }
        }
        saveCollection(transaction, destCollection._2);
        saveCollection(transaction, destination);

        return destCollection._2;
    }

    /**
     * Copies just the mode and ACL from the src to the dest
     */
    private void copyModeAndAcl(final Permission srcPermission, final Permission destPermission) throws PermissionDeniedException {
        destPermission.setMode(srcPermission.getMode());
        if(srcPermission instanceof SimpleACLPermission && destPermission instanceof SimpleACLPermission) {
            ((SimpleACLPermission)destPermission).copyAclOf((SimpleACLPermission)srcPermission);
        }
    }

    private boolean isSubCollection(final Collection col, final Collection sub) {
        return sub.getURI().startsWith(col.getURI());
    }

    @Override
    public void moveCollection(final Txn transaction, final Collection collection, final Collection destination, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException {

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
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
        if(isSubCollection(collection, destination)) {
            throw new PermissionDeniedException("Cannot move collection '" + collection.getURI() + "' to it child collection '"+destination.getURI()+"'.");
        }

        final XmldbURI parentName = collection.getParentURI();
        final Collection parent = parentName == null ? collection : getCollection(parentName);
        if(!parent.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + parent.getURI() + " to move collection " + collection.getURI());
        }

        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection to move collection " + collection.getURI());
        }

        if(!destination.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + parent.getURI() + " to move collection " + collection.getURI());
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
            final Path fsSourceDir = getCollectionFile(getFsDir(), collection.getURI(), false);

            // Need to move each collection in the source tree individually, so recurse.
            moveCollectionRecursive(transaction, trigger, collection, destination, newName, false);

            // For binary resources, though, just move the top level directory and all descendants come with it.
            moveBinaryFork(transaction, fsSourceDir, destination, newName);

            trigger.afterMoveCollection(this, transaction, collection, srcURI);

        } finally {
            pool.getProcessMonitor().endJob();
        }

    }

    private void moveBinaryFork(final Txn transaction, final Path sourceDir, final Collection destination, final XmldbURI newName) throws IOException {
        final Path targetDir = getCollectionFile(getFsDir(), destination.getURI().append(newName), false);
        if(Files.exists(sourceDir)) {
            if(Files.exists(targetDir)) {

                if(fsJournalDir.isPresent()) {
                    final Path targetDelDir = getCollectionFile(fsJournalDir.get(), transaction, destination.getURI().append(newName), true);
                    Files.createDirectories(targetDelDir);
                    Files.move(targetDir, targetDelDir, StandardCopyOption.ATOMIC_MOVE);

                    if(logManager.isPresent()) {
                        final Loggable loggable = new RenameBinaryLoggable(this, transaction, targetDir, targetDelDir);
                        try {
                            logManager.get().journal(loggable);
                        } catch (final JournalException e) {
                            LOG.warn(e.getMessage(), e);
                        }
                    }
                } else {
                    FileUtils.delete(targetDir);
                }
            }
            Files.createDirectories(targetDir.getParent());
            Files.move(sourceDir, targetDir, StandardCopyOption.ATOMIC_MOVE);

            if(logManager.isPresent()) {
                final Loggable loggable = new RenameBinaryLoggable(this, transaction, sourceDir, targetDir);
                try {
                    logManager.get().journal(loggable);
                } catch (final JournalException e) {
                    LOG.warn(e.getMessage(), e);
                }
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

            //recheck here because now under 'synchronized(collectionsCache)'
            if(isSubCollection(collection, destination)) {
                throw new PermissionDeniedException("Cannot move collection '" + srcURI + "' to it child collection '"+dstURI+"'.");
            }

            if(fireTrigger) {
                trigger.beforeMoveCollection(this, transaction, collection, dstURI);
            }

            final XmldbURI parentName = collection.getParentURI();
            final Collection parent = openCollection(parentName, LockMode.WRITE_LOCK);

            if(parent != null) {
                try {
                    //TODO : resolve URIs
                    parent.removeCollection(this, uri.lastSegment());
                } finally {
                    parent.release(LockMode.WRITE_LOCK);
                }
            }

            final Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(LockMode.WRITE_LOCK);
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
                lock.release(LockMode.WRITE_LOCK);
            }

            if(fireTrigger) {
                trigger.afterMoveCollection(this, transaction, collection, srcURI);
            }

            for(final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                final XmldbURI childName = i.next();
                //TODO : resolve URIs !!! name.resolve(childName)
                final Collection child = openCollection(uri.append(childName), LockMode.WRITE_LOCK);
                if(child == null) {
                    LOG.warn("Child collection " + childName + " not found");
                } else {
                    try {
                        moveCollectionRecursive(transaction, trigger, child, collection, childName, true);
                    } finally {
                        child.release(LockMode.WRITE_LOCK);
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

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        final XmldbURI parentName = collection.getParentURI();
        final boolean isRoot = parentName == null;
        final Collection parent = isRoot ? collection : getCollection(parentName);

        //parent collection permissions
        if(!parent.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        if(!parent.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        //this collection permissions
        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
        }

        if(!collection.isEmpty(this)) {
            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
            }

            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
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

                try {
                    for (final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                        final XmldbURI childName = i.next();
                        //TODO : resolve from collection's base URI
                        //TODO : resolve URIs !!! (uri.resolve(childName))
                        final Collection childCollection = openCollection(uri.append(childName), LockMode.WRITE_LOCK);
                        try {
                            removeCollection(transaction, childCollection);
                        } catch (final NullPointerException npe) {
                            LOG.error("childCollection '" + childName + "' is corrupted. Caught NPE to be able to actually remove the parent.");
                        } finally {
                            if (childCollection != null) {
                                childCollection.getLock().release(LockMode.WRITE_LOCK);
                            } else {
                                LOG.warn("childCollection is null !");
                            }
                        }
                    }
                } catch(final LockException e) {
                    LOG.error("LockException while removing collection '" + collName + "'", e);
                    return false;
                }

                //Drop all index entries
                notifyDropIndex(collection);

                // Drop custom indexes
                indexController.removeCollection(collection, this, false);

                if(!isRoot) {
                    // remove from parent collection
                    //TODO : resolve URIs ! (uri.resolve(".."))
                    final Collection parentCollection = openCollection(collection.getParentURI(), LockMode.WRITE_LOCK);
                    if(parentCollection != null) {
                        // keep the lock for the transaction
                        if(transaction != null) {
                            transaction.registerLock(parentCollection.getLock(), LockMode.WRITE_LOCK);
                        }

                        try {
                            LOG.debug("Removing collection '" + collName + "' from its parent...");
                            //TODO : resolve from collection's base URI
                            parentCollection.removeCollection(this, uri.lastSegment());
                            saveCollection(transaction, parentCollection);

                        } catch(final LockException e) {
                            LOG.warn("LockException while removing collection '" + collName + "'");
                        } finally {
                            if(transaction == null) {
                                parentCollection.getLock().release(LockMode.WRITE_LOCK);
                            }
                        }
                    }
                }

                //Update current state
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(LockMode.WRITE_LOCK);
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
                    LOG.warn("Failed to acquire lock on '" + FileUtils.fileName(collectionsDb.getFile()) + "'");
                }
                //catch(ReadOnlyException e) {
                //throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
                //}
                catch(final BTreeException | IOException e) {
                    LOG.warn("Exception while removing collection: " + e.getMessage(), e);
                } finally {
                    lock.release(LockMode.WRITE_LOCK);
                }

                //Remove child resources
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Removing resources in '" + collName + "'...");
                }

                final DocumentTrigger docTrigger = new DocumentTriggers(this, collection);

                try {
                    for (final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                        final DocumentImpl doc = i.next();

                        docTrigger.beforeDeleteDocument(this, transaction, doc);

                        //Remove doc's metadata
                        // WM: now removed in one step. see above.
                        //removeResourceMetadata(transaction, doc);
                        //Remove document nodes' index entries
                        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
                            @Override
                            public Object start() {
                                try {
                                    final Value ref = new NodeRef(doc.getDocId());
                                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                                    domDb.remove(transaction, query, null);
                                } catch (final BTreeException e) {
                                    LOG.warn("btree error while removing document", e);
                                } catch (final IOException e) {
                                    LOG.warn("io error while removing document", e);
                                } catch (final TerminatedException e) {
                                    LOG.warn("method terminated", e);
                                }
                                return null;
                            }
                        }.run();
                        //Remove nodes themselves
                        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
                            @Override
                            public Object start() {
                                if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                                    final long page = ((BinaryDocument) doc).getPage();
                                    if (page > Page.NO_PAGE) {
                                        domDb.removeOverflowValue(transaction, page);
                                    }
                                } else {
                                    final NodeHandle node = (NodeHandle) doc.getFirstChild();
                                    domDb.removeAll(transaction, node.getInternalAddress());
                                }
                                return null;
                            }
                        }.run();

                        docTrigger.afterDeleteDocument(this, transaction, doc.getURI());

                        //Make doc's id available again
                        collectionsDb.freeResourceId(doc.getDocId());
                    }
                } catch(final LockException e) {
                    LOG.error("LockException while removing documents from collection '" + collection.getURI() + "'.", e);
                    return false;
                }

                //now that the database has been updated, update the binary collections on disk
                final Path fsSourceDir = getCollectionFile(getFsDir(), collection.getURI(), false);
                if(fsJournalDir.isPresent()) {
                    final Path fsTargetDir = getCollectionFile(fsJournalDir.get(), transaction, collection.getURI(), true);

                    // remove child binary collections
                    if (Files.exists(fsSourceDir)) {
                        Files.createDirectories(fsTargetDir.getParent());

                        //TODO(DS) log first, rename second ???
                        //TODO(DW) not sure a Fatal is required here. Copy and delete maybe?
                        Files.move(fsSourceDir, fsTargetDir, StandardCopyOption.ATOMIC_MOVE);

                        if (logManager.isPresent()) {
                            final Loggable loggable = new RenameBinaryLoggable(this, transaction, fsSourceDir, fsTargetDir);
                            try {
                                logManager.get().journal(loggable);
                            } catch (final JournalException e) {
                                LOG.warn(e.getMessage(), e);
                            }
                        }
                    }
                } else {
                    FileUtils.delete(fsSourceDir);
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
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        pool.getCollectionsCache().add(collection);

        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.WRITE_LOCK);

            if(collection.getId() == Collection.UNKNOWN_COLLECTION_ID) {
                collection.setId(getNextCollectionId(transaction));
            }
            final Value name = new CollectionStore.CollectionKey(collection.getURI().toString());
            try(final VariableByteOutputStream os = new VariableByteOutputStream(8)) {
                collection.serialize(os);
                final long address = collectionsDb.put(transaction, name, os.data(), true);
                if (address == BFile.UNKNOWN_ADDRESS) {
                    //TODO : exception !!! -pb
                    LOG.warn("could not store collection data for '" + collection.getURI() + "'");
                    return;
                }
                collection.setAddress(address);
            }
        } catch(final ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()), e);
        } finally {
            lock.release(LockMode.WRITE_LOCK);
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
            lock.acquire(LockMode.WRITE_LOCK);
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
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        } finally {
            lock.release(LockMode.WRITE_LOCK);
        }
    }

    @Override
    public void reindexCollection(XmldbURI collectionName) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }
        collectionName = prepend(collectionName.toCollectionPathURI());
        final Collection collection = getCollection(collectionName);
        if(collection == null) {
            LOG.debug("collection " + collectionName + " not found!");
            return;
        }
        reindexCollection(collection, IndexMode.STORE);
    }

    public void reindexCollection(final Collection collection, final IndexMode mode) throws PermissionDeniedException {
        final TransactionManager transact = pool.getTransactionManager();

        final long start = System.currentTimeMillis();

        try(final Txn transaction = transact.beginTransaction()) {
            LOG.info(String.format("Start indexing collection %s", collection.getURI().toString()));
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REINDEX_COLLECTION, collection.getURI());
            reindexCollection(transaction, collection, mode);
            transact.commit(transaction);

        } catch(final Exception e) {
            LOG.warn("An error occurred during reindex: " + e.getMessage(), e);

        } finally {
            pool.getProcessMonitor().endJob();
            LOG.info(String.format("Finished indexing collection %s in %s ms.",
                collection.getURI().toString(), System.currentTimeMillis() - start));
        }
    }

    public void reindexCollection(final Txn transaction, final Collection collection, final IndexMode mode) throws PermissionDeniedException, IOException {
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
            }
            LOG.debug("Reindexing collection " + collection.getURI());
            if(mode == IndexMode.STORE) {
                dropCollectionIndex(transaction, collection, true);
            }
            try {
                for (final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                    final DocumentImpl next = i.next();
                    reindexXMLResource(transaction, next, mode);
                }
            } catch(final LockException e) {
                LOG.error("LockException while reindexing documents of collection '" + collection.getURI() + ". Skipping...", e);
            }

            try {
                for (final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                    final XmldbURI next = i.next();
                    //TODO : resolve URIs !!! (collection.getURI().resolve(next))
                    final Collection child = getCollection(collection.getURI().append(next));
                    if (child == null) {
                        LOG.warn("Collection '" + next + "' not found");
                    } else {
                        reindexCollection(transaction, child, mode);
                    }
                }
            } catch(final LockException e) {
                LOG.error("LockException while reindexing child collections of collection '" + collection.getURI() + ". Skipping...", e);
            }
        }
    }

    public void dropCollectionIndex(final Txn transaction, final Collection collection) throws PermissionDeniedException, IOException {
        dropCollectionIndex(transaction, collection, false);
    }

    public void dropCollectionIndex(final Txn transaction, final Collection collection, final boolean reindex) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }
        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
        }
        notifyDropIndex(collection);
        indexController.removeCollection(collection, this, reindex);
        try {
            for (final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
                LOG.debug("Dropping index for document " + doc.getFileURI());
                new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
                    @Override
                    public Object start() {
                        try {
                            final Value ref = new NodeRef(doc.getDocId());
                            final IndexQuery query =
                                    new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                            domDb.remove(transaction, query, null);
                            domDb.flush();
                        } catch (final BTreeException e) {
                            LOG.warn("btree error while removing document", e);
                        } catch (final DBException e) {
                            LOG.warn("db error while removing document", e);
                        } catch (final IOException e) {
                            LOG.warn("io error while removing document", e);
                        } catch (final TerminatedException e) {
                            LOG.warn("method terminated", e);
                        }
                        return null;
                    }
                }.run();
            }
        } catch(final LockException e) {
            LOG.error("LockException while removing index of collection '" + collection.getURI(), e);
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
    public DocumentImpl storeTempResource(final org.exist.dom.memtree.DocumentImpl doc)
        throws EXistException, PermissionDeniedException, LockException {

        try {
            //elevate getUser() to DBA_USER
            pushSubject(pool.getSecurityManager().getSystemSubject());

            //start a transaction
            final TransactionManager transact = pool.getTransactionManager();
            //create a name for the temporary document
            final XmldbURI docName = XmldbURI.create(MessageDigester.md5(Thread.currentThread().getName() + Long.toString(System.currentTimeMillis()), false) + ".xml");

            //get the temp collection
            try (final Txn transaction = transact.beginTransaction()) {
                Tuple2<Boolean, Collection> tuple = getOrCreateTempCollection(transaction);
                Collection temp = tuple._2;
                if (!tuple._1) {
                    transaction.acquireLock(temp.getLock(), LockMode.WRITE_LOCK);
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
                final DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc);
                indexer.scan();
                indexer.store();

                //store the temporary document
                temp.addDocument(transaction, this, targetDoc);

                storeXMLResource(transaction, targetDoc);

                saveCollection(transaction, temp);

                flush();
                closeDocument();
                //commit the transaction
                transact.commit(transaction);
                return targetDoc;
            } catch (final Exception e) {
                LOG.warn("Failed to store temporary fragment: " + e.getMessage(), e);
                //abort the transaction
            }
        } finally {
            //restore the user
            popSubject();
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
        try(final Txn transaction = transact.beginTransaction()) {
            removeCollection(transaction, temp);
            transact.commit(transaction);
        } catch(final Exception e) {
            LOG.warn("Failed to remove temp collection: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentImpl getResourceById(final int collectionId, final byte resourceType, final int documentId) throws PermissionDeniedException {
        XmldbURI uri = null;
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.READ_LOCK);
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
            LOG.error("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
            return null;
        } catch(final IOException e) {
            LOG.error("IOException while reading resource data", e);
            return null;
        } finally {
            lock.release(LockMode.READ_LOCK);
        }

        return getResource(uri, Permission.READ);
    }

    /**
     * store Document entry into its collection.
     */
    @Override
    public void storeXMLResource(final Txn transaction, final DocumentImpl doc) {


        final Lock lock = collectionsDb.getLock();
        try(final VariableByteOutputStream os = new VariableByteOutputStream(8)) {
            lock.acquire(LockMode.WRITE_LOCK);
            doc.write(os);
            final Value key = new CollectionStore.DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            collectionsDb.put(transaction, key, os.data(), true);
            //} catch (ReadOnlyException e) {
            //LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException e) {
            LOG.warn("IOException while writing document data", e);
        } finally {
            lock.release(LockMode.WRITE_LOCK);
        }
    }

    public void storeMetadata(final Txn transaction, final DocumentImpl doc) throws TriggerException {
        final Collection col = doc.getCollection();
        final DocumentTrigger trigger = new DocumentTriggers(this, col);

        trigger.beforeUpdateDocumentMetadata(this, transaction, doc);

        storeXMLResource(transaction, doc);

        trigger.afterUpdateDocumentMetadata(this, transaction, doc);
    }

    protected Path getCollectionFile(final Path dir, final XmldbURI uri, final boolean create) throws IOException {
        return getCollectionFile(dir, null, uri, create);
    }

    public Path getCollectionBinaryFileFsPath(final XmldbURI uri) {
        String suri = uri.getURI().toString();
        if(suri.startsWith("/")) {
            suri = suri.substring(1);
        }
        return getFsDir().resolve(suri);
    }

    private Path getCollectionFile(Path dir, final Txn transaction, final XmldbURI uri, final boolean create)
            throws IOException {
        if(transaction != null) {
            dir = dir.resolve("txn." + transaction.getId());
            if(create && !Files.exists(dir)) {
                dir = Files.createDirectory(dir);
            }

            //XXX: replace by transaction operation id/number from Txn
            //add unique id for operation in transaction
            dir = dir.resolve("oper." + UUID.randomUUID().toString());
            if(create && !Files.exists(dir)) {
                dir = Files.createDirectory(dir);
            }
        }

        //TODO(AR) consider just using Files.createDirectories to create the entire path in one atomic go
        final XmldbURI[] segments = uri.getPathSegments();
        Path binFile = dir;
        final int last = segments.length - 1;
        for(int i = 0; i < segments.length; i++) {
            binFile = binFile.resolve(segments[i].toString());
            if(create && i != last && !Files.exists(binFile)) {
                Files.createDirectory(binFile);
            }
        }
        return binFile;
    }

    @Deprecated
    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data)
            throws IOException {
        storeBinaryResource(transaction, blob, dest -> {
            try(final InputStream is = new ByteArrayInputStream(data)) {
                Files.copy(is, dest);
            }
        });
    }

    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final InputStream is)
            throws IOException {
        storeBinaryResource(transaction, blob, dest -> Files.copy(is, dest));
    }

    /**
     * @param transaction
     * @param blob The binary document to store
     * @param fWriteData A function that given the destination path, writes the document data to that path
     */
    private void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final ConsumerE<Path, IOException> fWriteData) throws IOException {
        blob.setPage(Page.NO_PAGE);
        final Path binFile = getCollectionFile(getFsDir(), blob.getURI(), true);
        final boolean exists = Files.exists(binFile);

        final Optional<Function<Path, Loggable>> fLoggable;
        if(fsJournalDir.isPresent()) {
            if (exists) {
                final Path backupFile = getCollectionFile(fsJournalDir.get(), transaction, blob.getURI(), true);
                Files.move(binFile, backupFile, StandardCopyOption.ATOMIC_MOVE);
                fLoggable = Optional.of(original -> new UpdateBinaryLoggable(this, transaction, original, backupFile));
            } else {
                fLoggable = Optional.of(original -> new CreateBinaryLoggable(this, transaction, original));
            }
        } else {
            Files.deleteIfExists(binFile);
            fLoggable = Optional.empty();
        }

        fWriteData.accept(binFile);

        if(logManager.isPresent() && fLoggable.isPresent()) {
            final Loggable loggable = fLoggable.get().apply(binFile);
            try {
                logManager.get().journal(loggable);
            } catch (final JournalException e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public Document getXMLResource(final XmldbURI fileName) throws PermissionDeniedException {
        return getResource(fileName, Permission.READ);
    }

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

        //if(!collection.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
        //throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getCurrentSubject().getName());
        //}

        final DocumentImpl doc = collection.getDocument(this, docUri);
        if(doc == null) {
            LOG.debug("document '" + fileName + "' not found!");
            return null;
        }

        if(!doc.getPermissions().validate(getCurrentSubject(), accessType)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' not allowed requested access to document '" + fileName + "'");
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
    public DocumentImpl getXMLResource(XmldbURI fileName, final LockMode lockMode) throws PermissionDeniedException {
        if(fileName == null) {
            return null;
        }
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !
        final XmldbURI collUri = fileName.removeLastSegment();
        final XmldbURI docUri = fileName.lastSegment();
        Collection collection = null;
        try {
            collection = openCollection(collUri, LockMode.READ_LOCK);
            if(collection == null) {
                LOG.debug("Collection '" + collUri + "' not found!");
                return null;
            }
            //if (!collection.getPermissions().validate(getCurrentSubject(), Permission.EXECUTE)) {
            //    throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getCurrentSubject().getName());
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
            if(collection != null) {
                collection.release(LockMode.READ_LOCK);
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
        final Path binFile = getCollectionFile(getFsDir(), blob.getURI(), false);
        return Files.size(binFile);
    }

    @Override
    public Path getBinaryFile(final BinaryDocument blob) throws IOException {
        return getCollectionFile(getFsDir(), blob.getURI(), false);
    }

    @Override
    public InputStream getBinaryResource(final BinaryDocument blob)
        throws IOException {
        return Files.newInputStream(getCollectionFile(getFsDir(), blob.getURI(), false));
    }

    //TODO : consider a better cooperation with Collection -pb
    @Override
    public void getCollectionResources(final Collection.InternalAccess collectionInternalAccess) {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.READ_LOCK);
            final Value key = new CollectionStore.DocumentKey(collectionInternalAccess.getId());
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);

            collectionsDb.query(query, new DocumentCallback(collectionInternalAccess));
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException | TerminatedException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(LockMode.READ_LOCK);
        }
    }

    @Override
    public void getResourcesFailsafe(final BTreeCallback callback, final boolean fullScan) throws TerminatedException {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.READ_LOCK);
            final Value key = new CollectionStore.DocumentKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            if(fullScan) {
                collectionsDb.rawScan(query, callback);
            } else {
                collectionsDb.query(query, callback);
            }
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(LockMode.READ_LOCK);
        }
    }

    @Override
    public void getCollectionsFailsafe(final BTreeCallback callback) throws TerminatedException {
        final Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(LockMode.READ_LOCK);
            final Value key = new CollectionStore.CollectionKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, callback);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException e) {
            LOG.warn("Exception while reading document data", e);
        } finally {
            lock.release(LockMode.READ_LOCK);
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
                && doc.getCollection().getPermissionsNoLock().validate(getCurrentSubject(), Permission.READ)
                && doc.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
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
            rootCollection = openCollection(XmldbURI.ROOT_COLLECTION_URI, LockMode.READ_LOCK);
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
                rootCollection.release(LockMode.READ_LOCK);
            }
        }
    }

    /**
     * @param doc         src document
     * @param destination destination collection
     * @param newName     the new name for the document
     */
    @Override
    public void copyResource(final Txn transaction, final DocumentImpl doc, final Collection destination, XmldbURI newName) throws PermissionDeniedException, LockException, EXistException, IOException {

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        final Collection collection = doc.getCollection();

        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' has insufficient privileges to copy the resource '" + doc.getFileURI() + "'.");
        }

        if(!doc.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' has insufficient privileges to copy the resource '" + doc.getFileURI() + "'.");
        }

        if(newName == null) {
            newName = doc.getFileURI();
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            final Lock lock = collectionsDb.getLock();
            try {
                lock.acquire(LockMode.WRITE_LOCK);
                final DocumentImpl oldDoc = destination.getDocument(this, newName);

                if(!destination.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                    throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' does not have execute access on the destination collection '" + destination.getURI() + "'.");
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
                    if(!destination.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' does not have write access on the destination collection '" + destination.getURI() + "'.");
                    }
                } else {
                    //overwrite existing document

                    if(doc.getDocId() == oldDoc.getDocId()) {
                        throw new EXistException("Cannot copy resource to itself '" + doc.getURI() + "'.");
                    }

                    if(!oldDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
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
                    newDoc.getUpdateLock().acquire(LockMode.WRITE_LOCK);
                    try {
                        copyXMLResource(transaction, doc, newDoc);
                        destination.addDocument(transaction, this, newDoc);
                        storeXMLResource(transaction, newDoc);
                    } finally {
                        newDoc.getUpdateLock().release(LockMode.WRITE_LOCK);
                    }
                    newDocument = newDoc;
                }

                trigger.afterCopyDocument(this, transaction, newDocument, oldUri);

            } catch(final IOException e) {
                LOG.warn("An error occurred while copying resource", e);
            } catch(final TriggerException e) {
                throw new PermissionDeniedException(e.getMessage(), e);
            } finally {
                lock.release(LockMode.WRITE_LOCK);
            }
        }
    }

    private void copyXMLResource(final Txn transaction, final DocumentImpl oldDoc, final DocumentImpl newDoc) throws IOException {
        if (LOG.isDebugEnabled())
            LOG.debug("Copying document " + oldDoc.getFileURI() + " to " + newDoc.getURI());
        final long start = System.currentTimeMillis();
        final StreamListener listener = indexController.getStreamListener(newDoc, ReindexMode.STORE);
        final NodeList nodes = oldDoc.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
            try(final INodeIterator iterator = getNodeIterator(node)) {
                iterator.next();
                copyNodes(transaction, iterator, node, new NodePath(), newDoc, false, listener);
            }
        }
        flush();
        closeDocument();
        if (LOG.isDebugEnabled())
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

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        final Account docUser = doc.getUserLock();
        if(docUser != null) {
            if(!(getCurrentSubject().getName()).equals(docUser.getName())) {
                throw new PermissionDeniedException("Cannot move '" + doc.getFileURI() + " because is locked by getUser() '" + docUser.getName() + "'");
            }
        }

        final Collection collection = doc.getCollection();

        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on source Collection to move resource " + doc.getFileURI());
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
//        if(!((doc.getPermissions().getOwner().getId() != getCurrentSubject().getId()) | (doc.getPermissions().validate(getCurrentSubject(), Permission.EXECUTE)))) {
//            throw new PermissionDeniedException("Account "+getCurrentSubject().getName()+" have insufficient privileges on destination Collection to move resource " + doc.getFileURI());
//        }

        if(!destination.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on destination Collection to move resource " + doc.getFileURI());
        }
        
        
        /* Copy reference to original document */
        final Path fsOriginalDocument = getCollectionFile(getFsDir(), doc.getURI(), true);


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
                /* if(!oldDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Resource with same name exists in target collection and write is denied");
                }
                */

                removeResource(transaction, oldDoc);
            }

            boolean renameOnly = collection.getId() == destination.getId();

            final XmldbURI oldURI = doc.getURI();
            final XmldbURI newURI = destination.getURI().append(newName);

            trigger.beforeMoveDocument(this, transaction, doc, newURI);

            if(doc.getResourceType() == DocumentImpl.XML_FILE) {
                if (!renameOnly) {
                    dropIndex(transaction, doc);
                }
            }

            collection.unlinkDocument(this, doc);
            if(!renameOnly) {
                saveCollection(transaction, collection);
            }

            removeResourceMetadata(transaction, doc);

            doc.setFileURI(newName);
            doc.setCollection(destination);
            destination.addDocument(transaction, this, doc);

            if(doc.getResourceType() == DocumentImpl.XML_FILE) {
                if(!renameOnly) {
                    // reindexing
                    reindexXMLResource(transaction, doc, IndexMode.REPAIR);
                }
            } else {
                // binary resource
                final Path colDir = getCollectionFile(getFsDir(), destination.getURI(), true);
                final Path binFile = colDir.resolve(newName.lastSegment().toString());
                final Path sourceFile = getCollectionFile(getFsDir(), doc.getURI(), false);

                /* Create required directories */
                Files.createDirectories(binFile.getParent());

                /* Rename original file to new location */
                Files.move(fsOriginalDocument, binFile, StandardCopyOption.ATOMIC_MOVE);

                if(logManager.isPresent()) {
                    final Loggable loggable = new RenameBinaryLoggable(this, transaction, sourceFile, binFile);
                    try {
                        logManager.get().journal(loggable);
                    } catch (final JournalException e) {
                        LOG.warn(e.getMessage(), e);
                    }
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
    public void removeXMLResource(final Txn transaction, final DocumentImpl document, boolean freeDocId) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
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
                    new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
                        @Override
                        public Object start() {
                            final NodeHandle node = (NodeHandle) document.getFirstChild();
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
            new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
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
        final StreamListener listener = indexController.getStreamListener(document, ReindexMode.REMOVE_ALL_NODES);
        listener.startIndexDocument(transaction);
        final NodeList nodes = document.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
            try(final INodeIterator iterator = getNodeIterator(node)) {
                iterator.next();
                scanNodes(transaction, iterator, node, new NodePath(), IndexMode.REMOVE, listener);
            } catch(final IOException ioe) {
                LOG.warn("Unable to close node iterator", ioe);
            }
        }
        listener.endIndexDocument(transaction);
        notifyDropIndex(document);
        indexController.flush();
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final BinaryDocument blob) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("removing binary resource " + blob.getDocId() + "...");
        }

        final Path binFile = getCollectionFile(getFsDir(), blob.getURI(), false);
        if(Files.exists(binFile)) {
            if(fsJournalDir.isPresent()) {
	            final Path binBackupFile = getCollectionFile(fsJournalDir.get(), transaction, blob.getURI(), true);
    	        Files.move(binFile, binBackupFile, StandardCopyOption.ATOMIC_MOVE);

                if (logManager.isPresent()) {
                    final Loggable loggable = new RenameBinaryLoggable(this, transaction, binFile, binBackupFile);
                    try {
                        logManager.get().journal(loggable);
                    } catch (final JournalException e) {
                        LOG.warn(e.getMessage(), e);
                    }
                }
            } else {
                Files.delete(binFile);
            }
        }
        removeResourceMetadata(transaction, blob);

        getIndexController().setDocument(blob, ReindexMode.REMOVE_BINARY);
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
            lock.acquire(LockMode.WRITE_LOCK);
            if(LOG.isDebugEnabled()) {
                LOG.debug("Removing resource metadata for " + document.getDocId());
            }
            final Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
        } catch(final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()));
        } finally {
            lock.release(LockMode.WRITE_LOCK);
        }
    }

    public void removeResource(Txn tx, DocumentImpl doc) throws IOException, PermissionDeniedException {
        if (doc instanceof BinaryDocument) {
            removeBinaryResource(tx, (BinaryDocument) doc);
        } else {
            removeXMLResource(tx, doc);
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
            lock.acquire(LockMode.WRITE_LOCK);
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
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()), e);
            //TODO : rethrow ? -pb
        } finally {
            lock.release(LockMode.WRITE_LOCK);
        }
        return nextDocId;
    }

    @Override
    public void reindexXMLResource(final Txn txn, final DocumentImpl doc) {
        reindexXMLResource(txn, doc, IndexMode.REPAIR);
    }

    /**
     * Reindex the nodes in the document. This method will either reindex all
     * descendant nodes of the passed node, or all nodes below some level of
     * the document if node is null.
     */
    @Override
    public void reindexXMLResource(final Txn transaction, final DocumentImpl doc, final IndexMode mode) {
        if(doc.isCollectionConfig()) {
            doc.getCollection().setCollectionConfigEnabled(false);
        }
        final StreamListener listener = indexController.getStreamListener(doc, ReindexMode.STORE);
        indexController.startIndexDocument(transaction, listener);
        try {
            final NodeList nodes = doc.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
                try (final INodeIterator iterator = getNodeIterator(node)) {
                    iterator.next();
                    scanNodes(transaction, iterator, node, new NodePath(), mode, listener);
                } catch (final IOException ioe) {
                    LOG.warn("Unable to close node iterator", ioe);
                }
            }
        } finally {
            indexController.endIndexDocument(transaction, listener);
        }
        flush();
        if(doc.isCollectionConfig()) {
            doc.getCollection().setCollectionConfigEnabled(true);
        }
    }

    @Override
    public void defragXMLResource(final Txn transaction, final DocumentImpl doc) {
        //TODO : use dedicated function in XmldbURI
        if (LOG.isDebugEnabled())
            LOG.debug("============> Defragmenting document " + doc.getURI());
        final long start = System.currentTimeMillis();
        try {
            final long firstChild = doc.getFirstChildAddress();
            // dropping old structure index
            dropIndex(transaction, doc);
            // dropping dom index
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
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
            final StreamListener listener = indexController.getStreamListener(doc, ReindexMode.STORE);
            // copy the nodes
            final NodeList nodes = doc.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
                try(final INodeIterator iterator = getNodeIterator(node)) {
                    iterator.next();
                    copyNodes(transaction, iterator, node, new NodePath(), tempDoc, true, listener);
                }
            }
            flush();
            // remove the old nodes
            new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
                @Override
                public Object start() {
                    domDb.removeAll(transaction, firstChild);
                    try {
                        domDb.flush();
                    } catch(final DBException e) {
                        LOG.warn("start() - error while removing doc", e);
                    }
                    return null;
                }
            }.run();
            doc.copyChildren(tempDoc);
            doc.getMetadata().setSplitCount(0);
            doc.getMetadata().setPageCount(tempDoc.getMetadata().getPageCount());
            storeXMLResource(transaction, doc);
            closeDocument();
            if (LOG.isDebugEnabled())
                LOG.debug("Defragmentation took " + (System.currentTimeMillis() - start) + "ms.");
        } catch(final ReadOnlyException e) {
            LOG.warn(DATABASE_IS_READ_ONLY, e);
        } catch(final IOException e) {
            LOG.error(e);
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
            new DOMTransaction(this, domDb, LockMode.READ_LOCK) {
                @Override
                public Object start() throws ReadOnlyException {
                    LOG.debug("Pages used: " + domDb.debugPages(doc, false));
                    return null;
                }
            }.run();
            final NodeList nodes = doc.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                final IStoredNode node = (IStoredNode) nodes.item(i);
                try(final INodeIterator iterator = getNodeIterator(node)) {
                    iterator.next();
                    final StringBuilder buf = new StringBuilder();
                    //Pass buf to the following method to get a dump of all node ids in the document
                    if(!checkNodeTree(iterator, node, buf)) {
                        LOG.debug("node tree: " + buf.toString());
                        throw new RuntimeException("Error in document tree structure");
                    }
                } catch(final IOException e) {
                    LOG.error(e);
                }
            }
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, LockMode.READ_LOCK) {
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
     *                    element-parent or to itself if it is an element.
     */
    @Override
    public <T extends IStoredNode> void storeNode(final Txn transaction, final IStoredNode<T> node, final NodePath currentPath, final IndexSpec indexSpec) {
        checkAvailableMemory();
        final DocumentImpl doc = node.getOwnerDocument();
        final short nodeType = node.getNodeType();
        final byte data[] = node.serialize();
        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK, doc) {
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
        nodeProcessor.reset(transaction, node, currentPath, indexSpec);
        nodeProcessor.doIndex();
    }

    @Override
    public <T extends IStoredNode> void updateNode(final Txn transaction, final IStoredNode<T> node, final boolean reindex) {
        try {
            final DocumentImpl doc = node.getOwnerDocument();
            final long internalAddress = node.getInternalAddress();
            final byte[] data = node.serialize();
            new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
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
            
            //TODO what can we do about abstracting this out?
            final IStoredNode old = StoredNode.deserialize(oldVal.data(),
                oldVal.start(), oldVal.getLength(),
                node.getOwnerDocument(), false);
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
    public void insertNodeAfter(final Txn transaction, final NodeHandle previous, final IStoredNode node) {
        final byte data[] = node.serialize();
        final DocumentImpl doc = previous.getOwnerDocument();
        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK, doc) {
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

    private <T extends IStoredNode> void copyNodes(final Txn transaction, final INodeIterator iterator, final IStoredNode<T> node,
                           final NodePath currentPath, final DocumentImpl newDoc, final boolean defragment,
                           final StreamListener listener) {
        copyNodes(transaction, iterator, node, currentPath, newDoc, defragment, listener, null);
    }

    private <T extends IStoredNode> void copyNodes(final Txn transaction, INodeIterator iterator, final IStoredNode<T> node,
                           final NodePath currentPath, final DocumentImpl newDoc, final boolean defragment,
                           final StreamListener listener, NodeId oldNodeId) {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.addComponent(node.getQName());
        }
        final DocumentImpl doc = node.getOwnerDocument();
        final long oldAddress = node.getInternalAddress();
        node.setOwnerDocument(newDoc);
        node.setInternalAddress(BFile.UNKNOWN_ADDRESS);
        storeNode(transaction, node, currentPath, null);
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
            newDoc.appendChild((NodeHandle)node);
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
        if(node.hasChildNodes() || node.hasAttributes()) {
            final int count = node.getChildCount();
            NodeId nodeId = node.getNodeId();
            for(int i = 0; i < count; i++) {
                final IStoredNode child = iterator.next();
                oldNodeId = child.getNodeId();
                if(defragment) {
                    if(i == 0) {
                        nodeId = nodeId.newChild();
                    } else {
                        nodeId = nodeId.nextSibling();
                    }
                    child.setNodeId(nodeId);
                }
                copyNodes(transaction, iterator, child, currentPath, newDoc, defragment, listener, oldNodeId);
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
    public <T extends IStoredNode> void removeNode(final Txn transaction, final IStoredNode<T> node, final NodePath currentPath,
                           final String content) {
        final DocumentImpl doc = node.getOwnerDocument();
        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK, doc) {
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
        final QName qname;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE:
                qname = new QName(node.getQName(), ElementValue.ELEMENT);
                node.setQName(qname);
                final GeneralRangeIndexSpec spec1 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec1 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, spec1.getType(), NativeValueIndex.IndexType.GENERIC, false);
                }
                QNameRangeIndexSpec qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpec != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, qnSpec.getType(),
                        NativeValueIndex.IndexType.QNAME, false);
                }
                break;

            case Node.ATTRIBUTE_NODE:
                qname = new QName(node.getQName(), ElementValue.ATTRIBUTE);
                node.setQName(qname);
                currentPath.addComponent(qname);
                //Strange : does it mean that the node is added 2 times under 2 different identities ?
                AttrImpl attr;
                attr = (AttrImpl) node;
                switch(attr.getType()) {
                    case AttrImpl.ID:
                        valueIndex.setDocument(doc);
                        valueIndex.storeAttribute(attr, attr.getValue(), Type.ID, NativeValueIndex.IndexType.GENERIC, false);
                        break;
                    case AttrImpl.IDREF:
                        valueIndex.setDocument(doc);
                        valueIndex.storeAttribute(attr, attr.getValue(), Type.IDREF, NativeValueIndex.IndexType.GENERIC, false);
                        break;
                    case AttrImpl.IDREFS:
                        valueIndex.setDocument(doc);
                        final StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
                        while(tokenizer.hasMoreTokens()) {
                            valueIndex.storeAttribute(attr, tokenizer.nextToken(),Type.IDREF, NativeValueIndex.IndexType.GENERIC, false);
                        }
                        break;
                    default:
                        // do nothing special
                }
                final RangeIndexSpec spec2 = doc.getCollection().getIndexByPathConfiguration(this, currentPath);
                if(spec2 != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute(attr, null, spec2, false);
                }
                qnSpec = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpec != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute(attr, null, qnSpec, false);
                }
                currentPath.removeLastComponent();
                break;

            case Node.TEXT_NODE:
                break;
        }
    }

    @Override
    public void removeAllNodes(final Txn transaction, final IStoredNode node, final NodePath currentPath,
                               final StreamListener listener) {

        try(final INodeIterator iterator = getNodeIterator(node)) {
            iterator.next();

            final Stack<RemovedNode> stack = new Stack<>();
            collectNodesForRemoval(transaction, stack, iterator, listener, node, currentPath);
            while(!stack.isEmpty()) {
                final RemovedNode next = stack.pop();
                removeNode(transaction, next.node, next.path, next.content);
            }
        } catch(final IOException ioe) {
            LOG.warn("Unable to close node iterator", ioe);
        }
    }

    private <T extends IStoredNode> void collectNodesForRemoval(final Txn transaction, final Stack<RemovedNode> stack,
                                        final INodeIterator iterator, final StreamListener listener, final IStoredNode<T> node, final NodePath currentPath) {
        RemovedNode removed;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE:
                final DocumentImpl doc = node.getOwnerDocument();
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
                if(node.hasChildNodes() || node.hasAttributes()) {
                    final int childCount = node.getChildCount();
                    for(int i = 0; i < childCount; i++) {
                        final IStoredNode child = iterator.next();
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
    public void indexNode(final Txn transaction, final IStoredNode node, final NodePath currentPath) {
        indexNode(transaction, node, currentPath, IndexMode.STORE);
    }

    public void indexNode(final Txn transaction, final IStoredNode node, final NodePath currentPath, final IndexMode repairMode) {
        nodeProcessor.reset(transaction, node, currentPath, null);
        nodeProcessor.setIndexMode(repairMode);
        nodeProcessor.index();
    }

    private boolean checkNodeTree(final INodeIterator iterator, final IStoredNode node, final StringBuilder buf) {
        if(buf != null) {
            if(buf.length() > 0) {
                buf.append(", ");
            }
            buf.append(node.getNodeId());
        }
        boolean docIsValid = true;
        if(node.hasChildNodes() || node.hasAttributes()) {
            final int count = node.getChildCount();
            if(buf != null) {
                buf.append('[').append(count).append(']');
            }
            IStoredNode previous = null;
            for(int i = 0; i < count; i++) {
                IStoredNode child = iterator.next();
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
    private void scanNodes(final Txn transaction, final INodeIterator iterator, final IStoredNode node,
                           final NodePath currentPath, final IndexMode mode, final StreamListener listener) {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.addComponent(node.getQName());
        }
        indexNode(transaction, node, currentPath, mode);
        if(listener != null) {
            switch(node.getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    listener.characters(transaction, (AbstractCharacterData) node, currentPath);
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
        if(node.hasChildNodes() || node.hasAttributes()) {
            final int count = node.getChildCount();
            for(int i = 0; i < count; i++) {
                final IStoredNode child = iterator.next();
                if(child == null) {
                    LOG.fatal("child " + i + " not found for node: " + node.getNodeName() +
                        "; children = " + node.getChildCount());
                } else {
                    scanNodes(transaction, iterator, child, currentPath, mode, listener);
                }
            }
        }
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            endElement(node, currentPath, null, mode == IndexMode.REMOVE);
            if(listener != null) {
                listener.endElement(transaction, (ElementImpl) node, currentPath);
            }
            currentPath.removeLastComponent();
        }
    }

    @Override
    public String getNodeValue(final IStoredNode node, final boolean addWhitespace) {
        return (String) new DOMTransaction(this, domDb, LockMode.READ_LOCK) {
            @Override
            public Object start() {
                return domDb.getNodeValue(NativeBroker.this, node, addWhitespace);
            }
        }.run();
    }

    @Override
    public IStoredNode objectWith(final Document doc, final NodeId nodeId) {
        return (IStoredNode<?>) new DOMTransaction(this, domDb, LockMode.READ_LOCK) {
            @Override
            public Object start() {
                final Value val = domDb.get(NativeBroker.this, new NodeProxy((DocumentImpl) doc, nodeId));
                if(val == null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Node " + nodeId + " not found. This is usually not an error.");
                    }
                    return null;
                }
                final IStoredNode node = StoredNode.deserialize(val.getData(), 0, val.getLength(), (DocumentImpl) doc);
                node.setOwnerDocument((DocumentImpl) doc);
                node.setInternalAddress(val.getAddress());
                return node;
            }
        }.run();
    }

    @Override
    public IStoredNode objectWith(final NodeProxy p) {
        if(!StorageAddress.hasAddress(p.getInternalAddress())) {
            return objectWith(p.getOwnerDocument(), p.getNodeId());
        }
        return (IStoredNode<?>) new DOMTransaction(this, domDb, LockMode.READ_LOCK) {
            @Override
            public Object start() {
                // DocumentImpl sets the nodeId to DOCUMENT_NODE when it's trying to find its top-level
                // children (for which it doesn't persist the actual node ids), so ignore that.  Nobody else
                // should be passing DOCUMENT_NODE into here.
                final boolean fakeNodeId = p.getNodeId().equals(NodeId.DOCUMENT_NODE);
                final Value val = domDb.get(p.getInternalAddress(), false);
                if(val == null) {
                    LOG.debug("Node " + p.getNodeId() + " not found in document " + p.getOwnerDocument().getURI() +
                        "; docId = " + p.getOwnerDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()));
                    if(fakeNodeId) {
                        return null;
                    }
                } else {
                    final IStoredNode<? extends IStoredNode> node = StoredNode.deserialize(val.getData(), 0, val.getLength(), p.getOwnerDocument());
                    node.setOwnerDocument(p.getOwnerDocument());
                    node.setInternalAddress(p.getInternalAddress());
                    if(fakeNodeId) {
                        return node;
                    }
                    if(p.getOwnerDocument().getDocId() == node.getOwnerDocument().getDocId() &&
                        p.getNodeId().equals(node.getNodeId())) {
                        return node;
                    }
                    LOG.debug(
                        "Node " + p.getNodeId() + " not found in document " + p.getOwnerDocument().getURI() +
                            "; docId = " + p.getOwnerDocument().getDocId() + ": " + StorageAddress.toString(p.getInternalAddress()) +
                            "; found node " + node.getNodeId() + " instead"
                    );
                }
                // retry based on node id
                final IStoredNode node = objectWith(p.getOwnerDocument(), p.getNodeId());
                if(node != null) {
                    p.setInternalAddress(node.getInternalAddress());
                }  // update proxy with correct address
                return node;
            }
        }.run();
    }

    @Override
    public void repair() throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
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
            this.valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
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
        reindexCollection(null, getCollection(XmldbURI.ROOT_COLLECTION_URI), IndexMode.REPAIR);
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
            lock.acquire(LockMode.WRITE_LOCK);

            LOG.info("Rebuilding index " + FileUtils.fileName(btree.getFile()));
            btree.rebuild();
            LOG.info("Index " + FileUtils.fileName(btree.getFile()) + " was rebuilt.");
        } catch(LockException | IOException | TerminatedException | DBException e) {
            LOG.warn("Caught error while rebuilding core index " + FileUtils.fileName(btree.getFile()) + ": " + e.getMessage(), e);
        } finally {
            lock.release(LockMode.WRITE_LOCK);
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

    long nextReportTS = System.currentTimeMillis();

    @Override
    public void sync(final Sync syncEvent) {
        if(isReadOnly()) {
            return;
        }
        try {
            new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
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
            if(syncEvent == Sync.MAJOR) {
                final Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(LockMode.WRITE_LOCK);
                    collectionsDb.flush();
                } catch(final LockException e) {
                    LOG.warn("Failed to acquire lock on " + FileUtils.fileName(collectionsDb.getFile()), e);
                } finally {
                    lock.release(LockMode.WRITE_LOCK);
                }
                notifySync();
                pool.getIndexManager().sync();

                if (System.currentTimeMillis() > nextReportTS) {
	                final NumberFormat nf = NumberFormat.getNumberInstance();
    	            LOG_STATS.info("Memory: " + nf.format(run.totalMemory() / 1024) + "K total; " +
        	                nf.format(run.maxMemory() / 1024) + "K max; " +
            	            nf.format(run.freeMemory() / 1024) + "K free");
               		domDb.printStatistics();
                	collectionsDb.printStatistics();
                	notifyPrintStatistics();

                    nextReportTS = System.currentTimeMillis() + (10 * 60 * 1000); // occurs after 10 minutes from now
                }
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
            sync(Sync.MAJOR);
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
                    flush();
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
        new DOMTransaction(this, domDb, LockMode.WRITE_LOCK) {
            @Override
            public Object start() {
                domDb.closeDocument();
                return null;
            }
        }.run();
    }

    public final static class NodeRef extends Value {

        public static final int OFFSET_DOCUMENT_ID = 0;
        public static final int OFFSET_NODE_ID = OFFSET_DOCUMENT_ID + DocumentImpl.LENGTH_DOCUMENT_ID;

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
        final IStoredNode node;
        final String content;
        final NodePath path;

        RemovedNode(final IStoredNode node, final NodePath path, final String content) {
            this.node = node;
            this.path = path;
            this.content = content;
        }
    }

    /**
     * Delegate for Node Processing : indexing
     */
    private class NodeProcessor {
        private Txn transaction;
        private IStoredNode<? extends IStoredNode> node;
        private NodePath currentPath;

        /**
         * work variables
         */
        private DocumentImpl doc;
        private long address;

        private IndexSpec idxSpec;
        private int level;
        private IndexMode indexMode = IndexMode.STORE;

        NodeProcessor() {
            //ignore
        }

        public <T extends IStoredNode> void reset(final Txn transaction, final IStoredNode<T> node, final NodePath currentPath, IndexSpec indexSpec) {
            if(node.getNodeId() == null) {
                LOG.warn("illegal node: " + node.getNodeName());
            }
            //TODO : why continue processing ? return ? -pb
            this.transaction = transaction;
            this.node = node;
            this.currentPath = currentPath;
            this.indexMode = IndexMode.STORE;
            doc = node.getOwnerDocument();
            address = node.getInternalAddress();
            if(indexSpec == null) {
                indexSpec = doc.getCollection().getIndexConfiguration(NativeBroker.this);
            }
            idxSpec = indexSpec;
            level = node.getNodeId().getTreeLevel();
        }

        public void setIndexMode(final IndexMode indexMode) {
            this.indexMode = indexMode;
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
                    break;

                case Node.ATTRIBUTE_NODE:
                    final QName qname = new QName(node.getQName());
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
                            valueIndex.setDocument(node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath,
                                rangeSpec, indexMode == IndexMode.REMOVE);
                        }
                        final QNameRangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
                        if(qnIdx != null) {
                            indexType |= RangeIndexSpec.QNAME_INDEX;
                            if(!RangeIndexSpec.hasRangeIndex(indexType)) {
                                indexType |= qnIdx.getIndexType();
                            }
                            valueIndex.setDocument(node.getOwnerDocument());
                            //Oh dear : is it the right semantics then ?
                            valueIndex.storeAttribute((AttrImpl) node, currentPath,
                                qnIdx, indexMode == IndexMode.REMOVE);
                        }
                    }
                    node.setQName(new QName(qname, ElementValue.ATTRIBUTE));
                    final AttrImpl attr = (AttrImpl) node;
                    attr.setIndexType(indexType);
                    switch(attr.getType()) {
                        case AttrImpl.ID:
                            valueIndex.setDocument(doc);
                            valueIndex.storeAttribute(attr, attr.getValue(), Type.ID, NativeValueIndex.IndexType.GENERIC, indexMode == IndexMode.REMOVE);
                            break;

                        case AttrImpl.IDREF:
                            valueIndex.setDocument(doc);
                            valueIndex.storeAttribute(attr, attr.getValue(), Type.IDREF, NativeValueIndex.IndexType.GENERIC, indexMode == IndexMode.REMOVE);
                            break;

                        case AttrImpl.IDREFS:
                            valueIndex.setDocument(doc);
                            final StringTokenizer tokenizer = new StringTokenizer(attr.getValue(), " ");
                            while(tokenizer.hasMoreTokens()) {
                                valueIndex.storeAttribute(attr, tokenizer.nextToken(), Type.IDREF, NativeValueIndex.IndexType.GENERIC, indexMode == IndexMode.REMOVE);
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
                    notifyStoreText((TextImpl) node, currentPath);
                    break;
            }
        }

        /**
         * Stores this node into the database, if it's an element
         */
        public void store() {
            final DocumentImpl doc = node.getOwnerDocument();
            if(indexMode == IndexMode.STORE && node.getNodeType() == Node.ELEMENT_NODE && level <= defaultIndexDepth) {
                //TODO : used to be this, but NativeBroker.this avoids an owner change
                new DOMTransaction(NativeBroker.this, domDb, LockMode.WRITE_LOCK) {
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
            if(indexMode != IndexMode.REMOVE && nodesCount > DEFAULT_NODES_BEFORE_MEMORY_CHECK) {
                if(run.totalMemory() >= run.maxMemory() && run.freeMemory() < pool.getReservedMem()) {
                    flush();
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
