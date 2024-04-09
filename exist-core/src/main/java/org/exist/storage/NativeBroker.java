/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DOMIndexer;
import org.exist.dom.persistent.*;
import org.exist.dom.QName;
import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection.SubCollectionEntry;
import org.exist.collections.triggers.*;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.StructuralIndex;
import org.exist.numbering.NodeId;
import org.exist.security.*;
import org.exist.security.internal.aider.ACEAider;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.blob.BlobId;
import org.exist.storage.blob.BlobStore;
import org.exist.storage.btree.*;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.DOMTransaction;
import org.exist.storage.dom.NodeIterator;
import org.exist.storage.dom.RawNodeIterator;
import org.exist.storage.index.BFile;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.journal.*;
import org.exist.storage.lock.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.serializers.NativeSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.XmlSerializerPool;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.storage.dom.INodeIterator;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.security.Permission.DEFAULT_TEMPORARY_COLLECTION_PERM;
import static org.exist.util.io.InputStreamUtil.copy;

/**
 * Main class for the native XML storage backend.
 * By "native" it is meant file-based, embedded backend.
 *
 * Provides access to all low-level operations required by
 * the database. Extends {@link DBBroker}.
 *
 * This class dispatches the various events (defined by the methods
 * of {@link org.exist.storage.ContentLoadingObserver}) to indexing classes.
 *
 * @author Wolfgang Meier
 */
public class NativeBroker extends DBBroker {

    public final static String EXIST_STATISTICS_LOGGER = "org.exist.statistics";

    protected final static Logger LOG_STATS = LogManager.getLogger(EXIST_STATISTICS_LOGGER);

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

    public static final int FIRST_COLLECTION_ID = 1;

    public static final int OFFSET_COLLECTION_ID = 0;

    public final static String INIT_COLLECTION_CONFIG = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE + ".init";

    /** in-memory buffer size to use when copying binary resources */
    private final static int BINARY_RESOURCE_BUF_SIZE = 65536;

    private final static DigestType BINARY_RESOURCE_DIGEST_TYPE = DigestType.BLAKE_256;

    /** the database files */
    private final CollectionStore collectionsDb;
    private final DOMFile domDb;

    /** the index processors */
    private NativeValueIndex valueIndex;

    private final IndexSpec indexConfiguration;

    private int defaultIndexDepth;

    private final XmlSerializerPool xmlSerializerPool;

    /** used to count the nodes inserted after the last memory check */
    private int nodesCount = 0;

    private int nodesCountThreshold = DEFAULT_NODES_BEFORE_MEMORY_CHECK;

    private final Path dataDir;

    private final byte prepend;

    private final Runtime run = Runtime.getRuntime();

    private final NodeProcessor nodeProcessor = new NodeProcessor();

    private IEmbeddedXMLStreamReader streamReader;

    private final LockManager lockManager;
    private final Optional<JournalManager> logManager;

    // initialize database; read configuration, etc.
    public NativeBroker(final BrokerPool pool, final Configuration config) throws EXistException {
        super(pool, config);
        this.lockManager = pool.getLockManager();
        this.logManager = pool.getJournalManager();
        LOG.debug("Initializing broker {}", hashCode());

        final String prependDB = (String) config.getProperty("db-connection.prepend-db");
        if("always".equalsIgnoreCase(prependDB)) {
            this.prepend = PREPEND_DB_ALWAYS;
        } else if("never".equalsIgnoreCase(prependDB)) {
            this.prepend = PREPEND_DB_NEVER;
        } else {
            this.prepend = PREPEND_DB_AS_NEEDED;
        }

        this.dataDir = config.getProperty(BrokerPool.PROPERTY_DATA_DIR, Paths.get(DEFAULT_DATA_DIR));

        nodesCountThreshold = config.getInteger(BrokerPool.PROPERTY_NODES_BUFFER);
        if(nodesCountThreshold > 0) {
            nodesCountThreshold = nodesCountThreshold * 1000;
        }

        defaultIndexDepth = config.getInteger(PROPERTY_INDEX_DEPTH);
        if(defaultIndexDepth < 0) {
            defaultIndexDepth = DEFAULT_INDEX_DEPTH;
        }

        this.indexConfiguration = (IndexSpec) config.getProperty(Indexer.PROPERTY_INDEXER_CONFIG);
        this.xmlSerializerPool = new XmlSerializerPool(this, config, 5);

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
                LOG.warn("{} is read-only!", FileUtils.fileName(domDb.getFile()));
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
                LOG.warn("{} is read-only!", FileUtils.fileName(collectionsDb.getFile()));
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

    @Override
    public ElementIndex getElementIndex() {
        return null;
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

    private void notifyDropIndex(final DocumentImpl doc) {
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
                LOG.error(e);
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

    private void notifyCloseAndRemove() throws DBException {
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
                LOG.warn("Storage file is null: {}", i);
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
        pool.getBlobStore().backupToArchive(backup);
        pool.getIndexManager().backupToArchive(backup);
        //TODO backup counters
        //TODO USE zip64 or tar to create snapshots larger then 4Gb
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
            LOG.error("failed to create node iterator", e);
        }
        return null;
    }

    @Override
    public Serializer borrowSerializer() {
        return xmlSerializerPool.borrowObject();
    }

    @Override
    public void returnSerializer(final Serializer serializer) {
        xmlSerializerPool.returnObject(serializer);
    }

    @Override
    @Deprecated
    public Serializer getSerializer() {
        return newSerializer();
    }

    @Override
    @Deprecated
    public Serializer newSerializer() {
        return new NativeSerializer(this, getConfiguration());
    }

    @Override
    @Deprecated
    public Serializer newSerializer(final List<String> chainOfReceivers) {
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
    private @EnsureUnlocked Tuple2<Boolean, Collection> getOrCreateTempCollection(final Txn transaction)
        throws LockException, PermissionDeniedException, IOException, TriggerException {
        try {
            pushSubject(pool.getSecurityManager().getSystemSubject());
            final Tuple2<Boolean, Collection> temp = getOrCreateCollectionExplicit(transaction, XmldbURI.TEMP_COLLECTION_URI, Optional.empty(), true);
            if (temp._1) {
                temp._2.setPermissions(this, DEFAULT_TEMPORARY_COLLECTION_PERM);
                saveCollection(transaction, temp._2);
            }
            return temp;
        } finally {
            popSubject();
        }
    }

    private @Nullable String readInitCollectionConfig() {
        try {

            // 1) try and load from etc/ dir
            final Path fInitCollectionConfig = pool.getConfiguration().getExistHome()
                    .map(h -> h.resolve("etc").resolve(INIT_COLLECTION_CONFIG))
                    .orElse(Paths.get("etc").resolve(INIT_COLLECTION_CONFIG));
            if (Files.exists(fInitCollectionConfig)) {
                return new String(Files.readAllBytes(fInitCollectionConfig), UTF_8);
            }

            // 2) fallback to attempting to load from classpath
            try (final InputStream is = pool.getClassLoader().getResourceAsStream(INIT_COLLECTION_CONFIG)) {
                if (is == null) {
                    return null;
                }

                return InputStreamUtil.readString(is, UTF_8);
            }
        } catch(final IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
        }

        // 3) could not load!
        return null;
    }

    @Override
    public Collection getOrCreateCollection(final Txn transaction, final XmldbURI name) throws PermissionDeniedException, IOException, TriggerException {
        return getOrCreateCollectionExplicit(transaction, name, Optional.empty(), true)._2;
    }

    @Override
    public Collection getOrCreateCollection(final Txn transaction, final XmldbURI name, final Optional<Tuple2<Permission, Long>> creationAttributes) throws PermissionDeniedException, IOException, TriggerException {
        return getOrCreateCollectionExplicit(transaction, name, creationAttributes, true)._2;
    }

    /**
     * Gets the database Collection identified by the specified path.
     * If the Collection does not yet exist, it is created - including all ancestors.
     * The Collection is identified by its absolute path, e.g. /db/shakespeare.
     * The returned Collection will NOT HAVE a lock.
     *
     * The caller should take care to release any associated resource by
     * calling {@link Collection#close()}
     *
     * @param transaction The current transaction
     * @param path The Collection's URI
     * @param creationAttributes the attributes to use if the collection needs to be created,
     *                           the first item is a Permission (or null for default),
     *                           the second item is a Creation Date.
     * @param fireTrigger Indicates whether the CollectionTrigger should be fired.
     *                    Typically true, but can be set to false when creating a collection is
     *                    part of a composite operation like `copy`.
     *
     * @return A tuple whose first boolean value is set to true if the
     * collection was created, or false if the collection already existed. The
     * second value is the existing or created Collection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws IOException If an error occurs whilst reading (get) or writing (create) a Collection to disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     */
    private Tuple2<Boolean, Collection> getOrCreateCollectionExplicit(final Txn transaction, final XmldbURI path, final Optional<Tuple2<Permission, Long>> creationAttributes, final boolean fireTrigger) throws PermissionDeniedException, IOException, TriggerException {
        final XmldbURI collectionUri = prepend(path.toCollectionPathURI().normalizeCollectionPath());
        final XmldbURI parentCollectionUri = collectionUri.removeLastSegment();

        final CollectionCache collectionsCache = pool.getCollectionsCache();

        try {

            // 1) optimize for the existence of the Collection in the cache
            try (final ManagedCollectionLock collectionLock = readLockCollection(collectionUri)) {
                final Collection collection = collectionsCache.getIfPresent(collectionUri);
                if (collection != null) {
                    return new Tuple2<>(false, collection);
                }
            }

            // 2) try and read the Collection from disk, if not on disk then create it
            try (final ManagedCollectionLock parentCollectionLock = writeLockCollection(parentCollectionUri.numSegments() == 0 ? XmldbURI.ROOT_COLLECTION_URI : parentCollectionUri)) {       // we write lock the parent (as we may need to add a new Collection to it)

                // check for preemption between READ -> WRITE lock, is the Collection now in the cache?
                final Collection collection = collectionsCache.getIfPresent(collectionUri);
                if (collection != null) {
                    return new Tuple2<>(false, collection);
                }

                // is the parent Collection in the cache?
                if (parentCollectionUri == XmldbURI.EMPTY_URI) {
                    // no parent... so, this is the root collection!
                    return getOrCreateCollectionExplicit_rootCollection(transaction, collectionUri, collectionsCache, fireTrigger);
                } else {
                    final Collection parentCollection = collectionsCache.getIfPresent(parentCollectionUri);
                    if (parentCollection != null) {
                        // parent collection is in cache, is our Collection present on disk?
                        final Collection loadedCollection = loadCollection(collectionUri);

                        if (loadedCollection != null) {
                            // loaded it from disk

                            // add it to the cache and return it
                            collectionsCache.put(loadedCollection);
                            return new Tuple2<>(false, loadedCollection);

                        } else {
                            // not on disk, create the collection
                            return new Tuple2<>(true, createCollection(transaction, parentCollection, collectionUri, collectionsCache, creationAttributes, fireTrigger));
                        }

                    } else {
                        /*
                         * No parent Collection in the cache so that needs to be loaded/created
                         * (or will be read from cache if we are pre-empted) before we can create this Collection.
                         * However to do this, we need to yield the collectionLock, so we will continue outside
                         * the ManagedCollectionLock at (3)
                         */
                    }
                }
            }

            //TODO(AR) below, should we just fall back to recursive descent creating the collection hierarchy in the same manner that getOrCreateCollection used to do?

            // 3) No parent collection was previously found in cache so we need to call this function for the parent Collection and then ourselves
            final Tuple2<Boolean, Collection> newOrExistingParentCollection = getOrCreateCollectionExplicit(transaction, parentCollectionUri, creationAttributes, fireTrigger);
            return getOrCreateCollectionExplicit(transaction, collectionUri, creationAttributes, fireTrigger);

        } catch(final ReadOnlyException e) {
            throw new PermissionDeniedException(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            throw new IOException(e);
        }
    }

    private Tuple2<Boolean, Collection> getOrCreateCollectionExplicit_rootCollection(final Txn transaction, final XmldbURI collectionUri, final CollectionCache collectionsCache, final boolean fireTrigger) throws PermissionDeniedException, IOException, LockException, ReadOnlyException, TriggerException {
        // this is the root collection, so no parent, is the Collection present on disk?

        final Collection loadedRootCollection = loadCollection(collectionUri);

        if (loadedRootCollection != null) {
            // loaded it from disk

            // add it to the cache and return it
            collectionsCache.put(loadedRootCollection);
            return new Tuple2<>(false, loadedRootCollection);
        } else {
            // not on disk, create the root collection
            final Collection rootCollection = createCollection(transaction, null, collectionUri, collectionsCache, Optional.empty(), fireTrigger);

            //import an initial collection configuration
            try {
                final String initCollectionConfig = readInitCollectionConfig();
                if(initCollectionConfig != null) {
                    CollectionConfigurationManager collectionConfigurationManager = pool.getConfigurationManager();
                    if(collectionConfigurationManager == null) {
                        if(pool.getConfigurationManager() == null) {
                            throw new IllegalStateException();
                            //might not yet have been initialised
                            //pool.initCollectionConfigurationManager(this, transaction);
                        }
                        collectionConfigurationManager = pool.getConfigurationManager();
                    }

                    if(collectionConfigurationManager != null) {
                        collectionConfigurationManager.addConfiguration(transaction, this, rootCollection, initCollectionConfig);
                    }
                }
            } catch(final CollectionConfigurationException cce) {
                LOG.error("Could not load initial collection configuration for /db: {}", cce.getMessage(), cce);
            }

            return new Tuple2<>(true, rootCollection);
        }
    }

    /**
     * NOTE - When this is called there must be a WRITE_LOCK on collectionUri
     * and a WRITE_LOCK on parentCollection (if it is not null)
     */
    private @EnsureUnlocked Collection createCollection(final Txn transaction,
            @Nullable @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection parentCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) final XmldbURI collectionUri,
            final CollectionCache collectionCache, final Optional<Tuple2<Permission, Long>> creationAttributes, final boolean fireTrigger)
            throws TriggerException, ReadOnlyException, PermissionDeniedException, LockException, IOException {

        if(parentCollection != null && !parentCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)){
            throw new PermissionDeniedException("No write permissions for " + parentCollection.getURI().getCollectionPath());
        }

        final CollectionTrigger trigger;
        if (fireTrigger) {
            if (parentCollection == null) {
                trigger = new CollectionTriggers(this, transaction);
            } else {
                trigger = new CollectionTriggers(this, transaction, parentCollection);
            }
            trigger.beforeCreateCollection(this, transaction, collectionUri);
        } else {
            trigger = null;
        }

        final Collection collectionObj = createCollectionObject(transaction, parentCollection, collectionUri, creationAttributes);
        saveCollection(transaction, collectionObj);

        if(parentCollection != null) {
            parentCollection.addCollection(this, collectionObj);
            saveCollection(transaction, parentCollection);
        }

        collectionCache.put(collectionObj);

        if (fireTrigger) {
            trigger.afterCreateCollection(this, transaction, collectionObj);
        }

        return collectionObj;
    }

    /**
     * NOTE - When this is called there must be a WRITE_LOCK on collectionUri
     * and at least a READ_LOCK on parentCollection (if it is not null)
     */
    private Collection createCollectionObject(final Txn transaction,
            @Nullable @EnsureLocked(mode=LockMode.READ_LOCK) final Collection parentCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) final XmldbURI collectionUri,
            final Optional<Tuple2<Permission, Long>> creationAttributes)
            throws ReadOnlyException, PermissionDeniedException, LockException {

        final int collectionId = getNextCollectionId(transaction);
        final Collection collection = creationAttributes.map(attrs -> new MutableCollection(this, collectionId, collectionUri, attrs._1, attrs._2)).orElseGet(() -> new MutableCollection(this, collectionId, collectionUri));

        //inherit the group to collection if parent-collection is setGid
        if(parentCollection != null) {
            final Permission parentPermissions = parentCollection.getPermissionsNoLock();
            if(parentPermissions.isSetGid()) {
                final Permission collectionPermissions = collection.getPermissionsNoLock();
                collectionPermissions.setGroupFrom(parentPermissions); //inherit group
                collectionPermissions.setSetGid(true); //inherit setGid bit
            }
        }

        return collection;
    }

    /**
     * Loads a Collection from disk
     *
     * @param collectionUri The URI of the Collection to load
     *
     * @return The Collection object loaded from disk, or null if the record does not exist on disk
     */
    private @Nullable @EnsureLocked(mode=LockMode.READ_LOCK, type=LockType.COLLECTION) Collection loadCollection(
            @EnsureLocked(mode=LockMode.READ_LOCK, type=LockType.COLLECTION) final XmldbURI collectionUri)
            throws PermissionDeniedException, LockException, IOException {
        try (final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.CollectionKey(collectionUri.toString());
            final VariableByteInput is = collectionsDb.getAsStream(key);
            return is == null ? null : MutableCollection.load(this, collectionUri, is);
        }
    }

    @Override
    public Collection getCollection(final XmldbURI uri) throws PermissionDeniedException {
        return openCollection(uri, LockMode.NO_LOCK);
    }

    @Override
    public Collection openCollection(final XmldbURI uri, final LockMode lockMode) throws PermissionDeniedException {
        final XmldbURI collectionUri = prepend(uri.toCollectionPathURI().normalizeCollectionPath());

        final ManagedCollectionLock collectionLock;
        final Runnable unlockFn;    // we unlock on error, or if there is no Collection
        try {
            switch (lockMode) {
                case WRITE_LOCK:
                    collectionLock = writeLockCollection(collectionUri);
                    unlockFn = collectionLock::close;
                    break;

                case READ_LOCK:
                    collectionLock = readLockCollection(collectionUri);
                    unlockFn = collectionLock::close;
                    break;

                case NO_LOCK:
                default:
                    collectionLock = ManagedCollectionLock.notLocked(collectionUri);
                    unlockFn = () -> {};
            }
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on Collection: {}", collectionUri);
            return null;
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        final Collection collection;
        try {
            // NOTE: getCollectionForOpen will perform the Permission.EXECUTE security check on Collection at collectionUri
            collection = getCollectionForOpen(collectionsCache, collectionUri);
            if (collection == null) {
                unlockFn.run();
                return null;
            }
        } catch (final IllegalStateException | PermissionDeniedException e) {
            unlockFn.run();
            throw e;
        }

        // Must ALSO perform a security check up the collection hierarchy to ensure that we have Permission.EXECUTE all the way
        try {
            checkCollectionAncestorPermissions(collectionsCache, collection);
        } catch (final IllegalStateException | PermissionDeniedException e) {
            unlockFn.run();
            throw e;
        } catch (final LockException e) {
            unlockFn.run();
            LOG.error("Failed to acquire lock on Collection: {}", collectionUri);
            return null;
        }

        return new LockedCollection(collectionLock, collection);
    }

    // NOTE: READ_LOCK in the @EnsureLocked parameter annotation here means "at least" READ
    private @Nullable Collection getCollectionForOpen(final CollectionCache collectionsCache,
            @EnsureLocked(type=LockType.COLLECTION, mode=LockMode.READ_LOCK) final XmldbURI collectionUri)
            throws IllegalStateException, PermissionDeniedException {

        // 1) optimize for reading from the Collection from the cache
        final Collection collection = collectionsCache.getIfPresent(collectionUri);
        if (collection != null) {

            // sanity check
            if(!collection.getURI().equalsInternal(collectionUri)) {
                LOG.error("openCollection: The Collection received from the cache: {} is not the requested: {}", collection.getURI(), collectionUri);
                throw new IllegalStateException();
            }

            // does the user have permission to access THIS Collection
            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                throw new PermissionDeniedException("Permission denied to open collection: " + collection.getURI().toString() + " by " + getCurrentSubject().getName());
            }

            return collection;

        } else {

            // 2) if not in the cache, load from disk
            final Collection loadedCollection;
            try {
                // NOTE: loadCollection via. MutableCollection's constructor will perform the Permission.EXECUTE security check
                loadedCollection = loadCollection(collectionUri);
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                return null;
            } catch (final LockException e) {
                LOG.error("Failed to acquire lock on: {}", FileUtils.fileName(collectionsDb.getFile()));
                return null;
            }

            // if we loaded a Collection add it to the cache (if it isn't already there)
            if (loadedCollection != null) {
                return collectionsCache.getOrCreate(collectionUri, key -> loadedCollection);
            } else {
                return null;
            }
        }
    }

    // NOTE: READ_LOCK in the @EnsureLocked parameter annotation here means "at least" READ
    private void checkCollectionAncestorPermissions(final CollectionCache collectionsCache,
            @EnsureLocked(type=LockType.COLLECTION, mode=LockMode.READ_LOCK) final Collection collection)
            throws IllegalStateException, PermissionDeniedException, LockException {

        /*
            When we are called we hold either a READ or WRITE Lock on the Collection.
            As we are using hierarchical locking for Collections we can
            assume that we also hold either an INTENTION_READ or INTENTION_WRITE Lock
            on each ancestor Collection up to the root,
            therefore we don't really need to acquire any more locks.

            The permissions are checked bottom-up on the Collection hierarchy as we
            assume that the more specific/restrictive permissions are likely to be
            closer to the target Collection.
         */

        Collection c = collection;
        XmldbURI parentUri = c.getParentURI();
        while (parentUri != null) {
            // this will throw a PermissionDeniedException if the user does not have Permission.EXECUTE on the Collection at the parentUri
            c = getCollectionForOpen(collectionsCache, parentUri);
            if (c == null) {
                LOG.error("Parent collection {} was null for collection {} ", parentUri, collection.getURI());
                throw new IllegalStateException();
            }

            parentUri = c.getParentURI();
        }
    }

    @Override
    public List<String> findCollectionsMatching(final String regexp) {

        final List<String> collections = new ArrayList<>();

        final Pattern p = Pattern.compile(regexp);
        final Matcher m = p.matcher("");

        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {

            //TODO write a regexp lookup for key data in BTree.query
            //final IndexQuery idxQuery = new IndexQuery(IndexQuery.REGEXP, regexp);
            //List<Value> keys = collectionsDb.findKeysByCollectionName(idxQuery);

            final List<Value> keys = collectionsDb.getKeys();
            for(final Value key : keys) {
                final byte[] data = key.getData();
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
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
            //return null;
        } catch(final TerminatedException | IOException | BTreeException e) {
            LOG.error(e.getMessage(), e);
            //return null;
        }

        return collections;
    }

    @Override
    public void readCollectionEntry(final SubCollectionEntry entry) throws IOException, LockException {
        final XmldbURI uri = prepend(entry.getUri().toCollectionPathURI());

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        final Collection collection = collectionsCache.getIfPresent(uri);
        if(collection == null) {
            try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {

                final Value key = new CollectionStore.CollectionKey(uri.toString());
                final VariableByteInput is = collectionsDb.getAsStream(key);
                if(is == null) {
                    throw new IOException("Could not find collection entry for: " + uri);
                }

                //read the entry details
                entry.read(is);
            }
        } else {

            if(!collection.getURI().equalsInternal(uri)) {
                throw new IOException(String.format("readCollectionEntry: The Collection received from the cache: %s is not the requested: %s", collection.getURI(), uri));
            }

            entry.read(collection);
        }
    }

    @Override
    public void copyCollection(final Txn transaction, final Collection collection, final Collection destination, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        copyCollection(transaction, collection, destination, newName, PreserveType.DEFAULT);
    }

    @Override
    public void copyCollection(final Txn transaction, final Collection sourceCollection, final Collection targetCollection, final XmldbURI newName, final PreserveType preserve) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        assert(sourceCollection != null);
        assert(targetCollection != null);
        assert(newName != null);

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(newName.numSegments() != 1) {
            throw new IOException("newName name must be just a name i.e. an XmldbURI with one segment!");
        }

        final XmldbURI sourceCollectionUri = sourceCollection.getURI();
        final XmldbURI targetCollectionUri = targetCollection.getURI();
        final XmldbURI destinationCollectionUri = targetCollectionUri.append(newName);

        if(sourceCollection.getId() == targetCollection.getId()) {
            throw new PermissionDeniedException("Cannot copy collection to itself '" + sourceCollectionUri + "'.");
        }
        if(sourceCollectionUri.equals(destinationCollectionUri)) {
            throw new PermissionDeniedException("Cannot copy collection to itself '" + sourceCollectionUri + "'.");
        }
        if(isSubCollection(sourceCollectionUri, targetCollectionUri)) {
            throw new PermissionDeniedException("Cannot copy collection '" + sourceCollectionUri + "' inside itself  '" + targetCollectionUri + "'.");
        }

        if(!sourceCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " has insufficient privileges on collection to copy collection " + sourceCollectionUri);
        }
        if(!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " has insufficient privileges on target collection " + targetCollectionUri + " to copy collection " + sourceCollectionUri);
        }

        /*
         * At this point this thread should hold:
         *   READ_LOCK on:
         *     1) sourceCollection
         *
         *   WRITE_LOCK on:
         *     1) targetCollection
         *
         *  Remember a lock on a node in the Collection tree,
         *  implies locking the entire sub-tree, therefore
         *  we don't need to explicitly lock sub-collections (just documents).
         */

        pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_COPY_COLLECTION, sourceCollection.getURI());
        try {

            final XmldbURI sourceCollectionParentUri = sourceCollection.getParentURI();
            // READ_LOCK the parent of the source Collection for the triggers
            try(final Collection sourceCollectionParent = sourceCollectionParentUri == null ? sourceCollection : openCollection(sourceCollectionParentUri, LockMode.READ_LOCK)) {
                // fire before copy collection triggers
                final CollectionTrigger trigger = new CollectionTriggers(this, transaction, sourceCollectionParent);
                trigger.beforeCopyCollection(this, transaction, sourceCollection, destinationCollectionUri);

                final DocumentTrigger docTrigger = new DocumentTriggers(this, transaction);

                // pessimistically obtain READ_LOCKs on all descendant documents of sourceCollection, and WRITE_LOCKs on all target documents
                final Collection newCollection;
                try(final ManagedLocks<ManagedDocumentLock> sourceDocLocks = new ManagedLocks(lockDescendantDocuments(sourceCollection, lockManager::acquireDocumentReadLock));
                        final ManagedLocks<ManagedDocumentLock> targetDocLocks = new ManagedLocks(lockTargetDocuments(sourceCollectionUri, sourceDocLocks, destinationCollectionUri, lockManager::acquireDocumentWriteLock))) {

                    // check all permissions in the tree to ensure a copy operation will succeed before starting copying
                    checkPermissionsForCopy(sourceCollection, targetCollection, newName);
                    newCollection = doCopyCollection(transaction, docTrigger, sourceCollection, targetCollection, destinationCollectionUri, true, preserve);
                }
                // fire after copy collection triggers
                trigger.afterCopyCollection(this, transaction, newCollection, sourceCollectionUri);
            }

        } finally {
            pool.getProcessMonitor().endJob();
        }
    }

    /**
     * Checks all permissions in the tree to ensure that a copy operation
     * will not fail due to a lack of rights
     *
     * @param sourceCollection The Collection to copy
     * @param targetCollection The target Collection to copy the sourceCollection into
     * @param newName The new name the sourceCollection should have in the targetCollection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     */
    protected void checkPermissionsForCopy(@EnsureLocked(mode=LockMode.READ_LOCK) final Collection sourceCollection,
            @EnsureLocked(mode=LockMode.READ_LOCK) @Nullable final Collection targetCollection, final XmldbURI newName)
            throws PermissionDeniedException, LockException {

        if(!sourceCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.READ)) {
            throw new PermissionDeniedException("Permission denied to copy collection " + sourceCollection.getURI() + " by " + getCurrentSubject().getName());
        }

        final XmldbURI destinationCollectionUri = targetCollection == null ? null : targetCollection.getURI().append(newName);
        final Collection destinationCollection = destinationCollectionUri == null ? null : getCollection(destinationCollectionUri);  // NOTE: we already have a WRITE_LOCK on destinationCollectionUri

        if(targetCollection != null) {
            if(!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.WRITE)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + sourceCollection.getURI() + " to " + targetCollection.getURI() + " by " + getCurrentSubject().getName());
            }

            if(destinationCollection != null) {
                if(!destinationCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE | Permission.WRITE)) {
                    throw new PermissionDeniedException("Permission denied to copy collection " + sourceCollection.getURI() + " to " + destinationCollection.getURI() + " by " + getCurrentSubject().getName());
                }
            }
        }

        // check document permissions
        for(final Iterator<DocumentImpl> itSrcSubDoc = sourceCollection.iteratorNoLock(this); itSrcSubDoc.hasNext(); ) {        // NOTE: we already have a READ lock on sourceCollection implicitly
            final DocumentImpl srcSubDoc = itSrcSubDoc.next();
            if(!srcSubDoc.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
                throw new PermissionDeniedException("Permission denied to copy collection " + sourceCollection.getURI() + " for resource " + srcSubDoc.getURI() + " by " + getCurrentSubject().getName());
            }

            //if the destination resource exists, we must have write access to replace it's metadata etc. (this follows the Linux convention)
            if(destinationCollection != null && !destinationCollection.isEmpty(this)) {
                final DocumentImpl newDestSubDoc = destinationCollection.getDocument(this, srcSubDoc.getFileURI()); //TODO check this uri is just the filename!
                if(newDestSubDoc != null) {
                    if(!newDestSubDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("Permission denied to copy collection " + sourceCollection.getURI() + " for resource " + newDestSubDoc.getURI() + " by " + getCurrentSubject().getName());
                    }
                }
            }
        }

        // descend into sub-collections
        for(final Iterator<XmldbURI> itSrcSubColUri = sourceCollection.collectionIteratorNoLock(this); itSrcSubColUri.hasNext(); ) {        // NOTE: we already have a READ lock on sourceCollection implicitly
            final XmldbURI srcSubColUri = itSrcSubColUri.next();
            final Collection srcSubCol = getCollection(sourceCollection.getURI().append(srcSubColUri));  // NOTE: we already have a READ_LOCK on destinationCollectionUri

            checkPermissionsForCopy(srcSubCol, destinationCollection, srcSubColUri);
        }
    }


    /**
     * Copy a collection and all its sub-Collections.
     *
     * @param transaction The current transaction
     * @param documentTrigger The trigger to use for document events
     * @param sourceCollection The Collection to copy
     * @param destinationCollectionUri The destination Collection URI for the sourceCollection copy
     * @param copyCollectionMode false on the first call, true on recursive calls
     *
     * @return A reference to the Collection, no additional locks are held on the Collection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     * @throws EXistException If no more Document IDs are available
     */
    private Collection doCopyCollection(final Txn transaction, final DocumentTrigger documentTrigger,
            @EnsureLocked(mode=LockMode.READ_LOCK) final Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection destinationParentCollection, 
            @EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) final XmldbURI destinationCollectionUri,
            final boolean copyCollectionMode, final PreserveType preserve)
            throws PermissionDeniedException, IOException, EXistException, TriggerException, LockException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Copying collection to '{}'", destinationCollectionUri);
        }

        // permissions and attributes for the destCollection (if we have to create it)
        final Permission createCollectionPerms = PermissionFactory.getDefaultCollectionPermission(getBrokerPool().getSecurityManager());
        copyModeAndAcl(sourceCollection.getPermissions(), createCollectionPerms);
        final long created;
        if (preserveOnCopy(preserve)) {
            // only copy the owner and group from the source if we are creating a new collection and we are the DBA
            if (getCurrentSubject().hasDbaRole()) {
                PermissionFactory.chown(this, createCollectionPerms, Optional.of(sourceCollection.getPermissions().getOwner().getName()), Optional.of(sourceCollection.getPermissions().getGroup().getName()));
            }

            created = sourceCollection.getCreated();
        } else {
            created = 0;
        }

        final Tuple2<Boolean, Collection> destinationCollection = getOrCreateCollectionExplicit(transaction, destinationCollectionUri, Optional.of(new Tuple2<>(createCollectionPerms, created)), false);

        // if we didn't create destCollection but we need to preserve the attributes
        if((!destinationCollection._1) && preserveOnCopy(preserve)) {
            copyModeAndAcl(sourceCollection.getPermissions(), destinationCollection._2.getPermissions());
        }

        // inherit the group to the destinationCollection if parent is setGid
        if (destinationParentCollection != null && destinationParentCollection.getPermissions().isSetGid()) {
            destinationCollection._2.getPermissions().setGroupFrom(destinationParentCollection.getPermissions()); //inherit group
            destinationCollection._2.getPermissions().setSetGid(true); //inherit setGid bit
        }

        doCopyCollectionDocuments(transaction, documentTrigger, sourceCollection, destinationCollection._2, preserve);

        final XmldbURI sourceCollectionUri = sourceCollection.getURI();
        for(final Iterator<XmldbURI> i = sourceCollection.collectionIterator(this); i.hasNext(); ) {
            final XmldbURI childName = i.next();
            final XmldbURI childUri = sourceCollectionUri.append(childName);
            try (final Collection child = getCollection(childUri)) {        // NOTE: we already have a READ lock on child implicitly
                if (child == null) {
                    throw new IOException("Child collection " + childUri + " not found");
                } else {
                    doCopyCollection(transaction, documentTrigger, child, destinationCollection._2, destinationCollection._2.getURI().append(childName), true, preserve);
                }
            }
        }

        return destinationCollection._2;
    }

    /**
     * Copy the documents in one Collection to another (non-recursive)
     *
     * @param transaction The current transaction
     * @param documentTrigger The trigger to use for document events
     * @param sourceCollection The Collection to copy documents from
     * @param targetCollection The Collection to copy documents to
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     * @throws EXistException If no more Document IDs are available
     */
    private void doCopyCollectionDocuments(final Txn transaction, final DocumentTrigger documentTrigger,
            @EnsureLocked(mode=LockMode.READ_LOCK) final Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection targetCollection,
            final PreserveType preserve)
            throws LockException, PermissionDeniedException, IOException, TriggerException, EXistException {
        for (final Iterator<DocumentImpl> i = sourceCollection.iterator(this); i.hasNext(); ) {
            final DocumentImpl sourceDocument = i.next();

            if(LOG.isDebugEnabled()) {
                LOG.debug("Copying resource: '{}'", sourceDocument.getURI());
            }

            final XmldbURI newDocName = sourceDocument.getFileURI();
            final XmldbURI targetCollectionUri = targetCollection.getURI();

            try(final LockedDocument oldLockedDoc = targetCollection.getDocumentWithLock(this, newDocName, LockMode.WRITE_LOCK)) {
                final DocumentImpl oldDoc = oldLockedDoc == null ? null : oldLockedDoc.getDocument();
                doCopyDocument(transaction, documentTrigger, sourceDocument, targetCollection, newDocName, oldDoc, preserve);
            }
        }
    }

    /**
     * Copies just the mode and ACL from the src to the dest
     *
     * @param srcPermission The source to copy from
     * @param destPermission The destination to copy to
     */
    private void copyModeAndAcl(final Permission srcPermission, final Permission destPermission) throws PermissionDeniedException {
        final List<ACEAider> aces = new ArrayList<>();
        if(srcPermission instanceof SimpleACLPermission && destPermission instanceof SimpleACLPermission) {
            final SimpleACLPermission srcAclPermission = (SimpleACLPermission) srcPermission;
            for (int i = 0; i < srcAclPermission.getACECount(); i++) {
                aces.add(new ACEAider(srcAclPermission.getACEAccessType(i), srcAclPermission.getACETarget(i), srcAclPermission.getACEWho(i), srcAclPermission.getACEMode(i)));
            }
        }
        PermissionFactory.chmod(this, destPermission, Optional.of(srcPermission.getMode()), Optional.of(aces));
    }

    private boolean isSubCollection(@EnsureLocked(mode=LockMode.READ_LOCK) final Collection col,
            @EnsureLocked(mode=LockMode.READ_LOCK) final Collection sub) {
        return isSubCollection(col.getURI(), sub.getURI());
    }

    private boolean isSubCollection(final XmldbURI col, final XmldbURI sub) {
        return sub.startsWith(col);
    }

    @Override
    public void moveCollection(final Txn transaction, final Collection sourceCollection,
            final Collection targetCollection, final XmldbURI newName)
            throws PermissionDeniedException, LockException, IOException, TriggerException {
        assert(sourceCollection != null);
        assert(targetCollection != null);
        assert(newName != null);

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(newName.numSegments() != 1) {
            throw new IOException("newName name must be just a name i.e. an XmldbURI with one segment!");
        }

        final XmldbURI sourceCollectionUri = sourceCollection.getURI();
        final XmldbURI targetCollectionUri = targetCollection.getURI();
        final XmldbURI destinationCollectionUri = targetCollectionUri.append(newName);

        if(sourceCollection.getId() == targetCollection.getId()) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + sourceCollectionUri + "'.");
        }
        if(sourceCollectionUri.equals(destinationCollectionUri)) {
            throw new PermissionDeniedException("Cannot move collection to itself '" + sourceCollectionUri + "'.");
        }
        if(sourceCollectionUri.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            throw new PermissionDeniedException("Cannot move the db root collection /db");
        }
        if(isSubCollection(sourceCollectionUri, targetCollectionUri)) {
            throw new PermissionDeniedException("Cannot move collection '" + sourceCollectionUri + "' inside itself '" + targetCollectionUri + "'.");
        }

        if(!sourceCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " has insufficient privileges on collection to move collection " + sourceCollectionUri);
        }
        if(!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " has insufficient privileges on destination collection " + destinationCollectionUri + " to move collection " + sourceCollectionUri);
        }



        // WRITE LOCK the parent of the sourceCollection (as we will want to remove the sourceCollection from it eventually)
        final XmldbURI sourceCollectionParentUri = sourceCollectionUri.removeLastSegment();
        try (final Collection sourceCollectionParent = openCollection(sourceCollectionParentUri, LockMode.WRITE_LOCK)) {

            if(!sourceCollectionParent.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
                throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + sourceCollectionParentUri + " to move collection " + sourceCollectionUri);
            }

            /*
             * If replacing another collection in the move
             * i.e. sourceCollection=/db/col1/A, targetCollection=/db/col2, newName=A
             * where /db/col2/A already exists we have to make sure the permissions to
             * remove /db/col2/A are okay!
             *
             * So we must call removeCollection on /db/col2/A
             * Which will ensure that collection can be removed and then remove it.
             */
            try(final Collection existingDestinationCollection = getCollection(destinationCollectionUri)) { // NOTE: we already have a WRITE lock on destinationCollection (implicitly as targetCollection is locked)
                if(existingDestinationCollection != null) {
                    if (!removeCollection(transaction, existingDestinationCollection)) {
                        throw new IOException("Destination collection '" + destinationCollectionUri + "' already exists and cannot be removed");
                    }
                }
            }

            /*
             * At this point this thread should hold WRITE_LOCKs on:
             *   1) parent of sourceCollection
             *   2) sourceCollection
             *   3) targetCollection
             *
             *  Remember a lock on a node in the Collection tree,
             *  implies locking the entire sub-tree, therefore
             *  we don't need to explicitly lock sub-collections (just documents).
             */

            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_MOVE_COLLECTION, sourceCollection.getURI());
            try {
                final CollectionTrigger trigger = new CollectionTriggers(this, transaction, sourceCollectionParent);
                trigger.beforeMoveCollection(this, transaction, sourceCollection, destinationCollectionUri);

                // pessimistically obtain WRITE_LOCKs on all descendant documents of sourceCollection, and WRITE_LOCKs on all target documents
                // we do this as whilst the document objects won't change, their method getURI() will return a different URI after the move
                try(final ManagedLocks<ManagedDocumentLock> sourceDocLocks = new ManagedLocks(lockDescendantDocuments(sourceCollection, lockManager::acquireDocumentWriteLock));
                        final ManagedLocks<ManagedDocumentLock> targetDocLocks = new ManagedLocks(lockTargetDocuments(sourceCollectionUri, sourceDocLocks, destinationCollectionUri, lockManager::acquireDocumentWriteLock))) {

                    // Need to move each collection in the source tree individually, so recurse.
                    moveCollectionRecursive(transaction, trigger, sourceCollectionParent, sourceCollection, targetCollection, newName, false);

                }
                trigger.afterMoveCollection(this, transaction, sourceCollection, sourceCollectionUri);
            } finally {
                pool.getProcessMonitor().endJob();
            }
        }
    }

    /**
     * Acquires locks on all descendant Collections of a specific Collection
     *
     * Locks are acquired in a top-down, left-to-right order
     *
     * NOTE: It is assumed that the caller holds a lock on the
     *     `collection` of the same mode as those that we should acquire on the descendants
     *
     * @param collection The Collection whose descendant locks should be acquired
     * @param lockFn A function for acquiring a lock
     *
     * @return A list of locks in the same order as collectionUris. Note that these should be released in reverse order
     */
    private List<ManagedDocumentLock> lockDescendantDocuments(final Collection collection, final FunctionE<XmldbURI, ManagedDocumentLock, LockException> lockFn) throws LockException, PermissionDeniedException {
        final List<ManagedDocumentLock> locks = new ArrayList<>();

        try {
            final Iterator<DocumentImpl> itDoc = collection.iteratorNoLock(this);
            while(itDoc.hasNext()) {
                final DocumentImpl doc = itDoc.next();
                final ManagedDocumentLock docLock = lockFn.apply(doc.getURI());
                locks.add(docLock);
            }

            final XmldbURI collectionUri = collection.getURI();
            final Iterator<XmldbURI> it = collection.collectionIteratorNoLock(this);    // NOTE: we should already have a lock on collection
            while (it.hasNext()) {
                final XmldbURI childCollectionName = it.next();
                final XmldbURI childCollectionUri = collectionUri.append(childCollectionName);
                final Collection childCollection = getCollection(childCollectionUri);  // NOTE: we don't need to lock the collection as we should already implicitly have a lock on the collection sub-tree
                final List<ManagedDocumentLock> descendantLocks = lockDescendantDocuments(childCollection, lockFn);
                locks.addAll(descendantLocks);
            }
        } catch (final PermissionDeniedException | LockException e) {
            // unlock in reverse order
            try {
                ManagedLocks.closeAll(locks);
            } catch (final RuntimeException re) {
                LOG.error(re);
            }

            throw e;
        }

        return locks;
    }

    /**
     * Locks target documents (useful for copy/move operations).
     *
     * @param sourceCollectionUri The source collection URI root of the copy/move operation
     * @param sourceDocumentLocks Locks on the source documents, for which target document locks should be acquired
     * @param targetCollectionUri The target collection URI root of the copy/move operation
     * @param lockFn The function for locking the target document.
     *
     * @return A list of locks on the target documents.
     */
    private List<ManagedDocumentLock> lockTargetDocuments(final XmldbURI sourceCollectionUri, final ManagedLocks<ManagedDocumentLock> sourceDocumentLocks, final XmldbURI targetCollectionUri, final FunctionE<XmldbURI, ManagedDocumentLock, LockException> lockFn) throws LockException {
        final List<ManagedDocumentLock> locks = new ArrayList<>();
        try {
            for (final ManagedDocumentLock sourceDocumentLock : sourceDocumentLocks) {
                final XmldbURI sourceDocumentUri = sourceDocumentLock.getPath();
                final URI relativeDocumentUri = sourceCollectionUri.relativizeCollectionPath(sourceDocumentUri.getURI());
                final XmldbURI targetDocumentUri = XmldbURI.create(targetCollectionUri.resolveCollectionPath(relativeDocumentUri));


                final ManagedDocumentLock documentLock = lockFn.apply(targetDocumentUri);
                locks.add(documentLock);

            }
        } catch(final LockException e) {
            // unlock in reverse order
            try {
                ManagedLocks.closeAll(locks);
            } catch (final RuntimeException re) {
                LOG.error(re);
            }

            throw e;
        }

        return locks;
    }


    //TODO bug the trigger param is reused as this is a recursive method, but in the current design triggers are only meant to be called once for each action and then destroyed!
    /**
     * Recursive-descent Collection move, only meant to be
     * called from {@link #moveCollection(Txn, Collection, Collection, XmldbURI)}
     *
     * @param transaction The current transaction
     * @param trigger The trigger to fire on Collection events
     * @param sourceCollection The Collection to move
     * @param targetCollection The target Collection to move the sourceCollection into
     * @param newName The new name the sourceCollection should have in the targetCollection
     * @param fireTrigger Indicates whether the CollectionTrigger should be fired
     *     on the Collection the first time this function is called. Triggers will always
     *     be fired for recursive calls of this function.
     */
    private void moveCollectionRecursive(final Txn transaction, final CollectionTrigger trigger,
            @Nullable @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection sourceCollectionParent,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection targetCollection, final XmldbURI newName,
            final boolean fireTrigger) throws PermissionDeniedException, IOException, LockException, TriggerException {
        final XmldbURI sourceCollectionUri = sourceCollection.getURI();
        final XmldbURI destinationCollectionUri = targetCollection.getURI().append(newName);

        if(fireTrigger) {
            trigger.beforeMoveCollection(this, transaction, sourceCollection, destinationCollectionUri);
        }

        // de-reference any existing binaries in the destination from the blob store
        try (final Collection dst = openCollection(destinationCollectionUri, LockMode.WRITE_LOCK)) {
            if (dst != null) {
                final Iterator<DocumentImpl> itDoc = dst.iterator(this);
                while (itDoc.hasNext()) {
                    final DocumentImpl dstDoc = itDoc.next();
                    if (dstDoc instanceof BinaryDocument) {
                        final BinaryDocument binDstDoc = (BinaryDocument)dstDoc;
                        try (final ManagedDocumentLock dstDocLock = lockManager.acquireDocumentWriteLock(dstDoc.getURI())) {
                            removeBinaryResource(transaction, binDstDoc);
                            binDstDoc.setBlobId(null);
                        }
                    }
                }
            }
        }

        // remove source from parent
        if (sourceCollectionParent != null) {
            final XmldbURI sourceCollectionName = sourceCollectionUri.lastSegment();
            sourceCollectionParent.removeCollection(this, sourceCollectionName);

            // if this is a rename, the save will happen after we "add the destination to the target" below...
            if (!sourceCollectionParent.getURI().equals(targetCollection)) {
                saveCollection(transaction, sourceCollectionParent);
            }
        }

        // remove source from cache
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        collectionsCache.invalidate(sourceCollection.getURI());

        // remove source from disk
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.CollectionKey(sourceCollectionUri.toString());
            collectionsDb.remove(transaction, key);
        }

        // set source path to destination... source is now the destination
        sourceCollection.setPath(destinationCollectionUri, true);
        saveCollection(transaction, sourceCollection);

        // add destination to target
        targetCollection.addCollection(this, sourceCollection);
        saveCollection(transaction, targetCollection);

        if(fireTrigger) {
            trigger.afterMoveCollection(this, transaction, sourceCollection, sourceCollectionUri);
        }

        // move the descendants
        for(final Iterator<XmldbURI> i = sourceCollection.collectionIteratorNoLock(this); i.hasNext(); ) {  // NOTE: we already have a WRITE lock on sourceCollection
            final XmldbURI childName = i.next();
            final XmldbURI childUri = sourceCollectionUri.append(childName);

            final Collection child = getCollectionForOpen(collectionsCache, childUri);        // NOTE: we have a write lock on the sourceCollection, which means we don't need to lock sub-collections in the tree
            if (child == null) {
                throw new IOException("Child collection " + childUri + " not found");
            } else {
                moveCollectionRecursive(transaction, trigger, null, child, sourceCollection, childName, true);
            }
        }
    }

    @Override
    public boolean removeCollection(final Txn transaction, final Collection collection) throws PermissionDeniedException, IOException, TriggerException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        // WRITE LOCK the collection's parent (as we will remove this collection from it)
        final XmldbURI parentCollectionUri = collection.getParentURI() == null ? XmldbURI.ROOT_COLLECTION_URI : collection.getParentURI();
        try(final ManagedCollectionLock parentCollectionLock = writeLockCollection(parentCollectionUri)) {
            return _removeCollection(transaction, collection);
        } catch(final LockException e) {
            LOG.error("Unable to lock Collection: {}", collection.getURI(), e);
            return false;
        }
    }

    private boolean _removeCollection(final Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection collection) throws PermissionDeniedException, TriggerException, IOException {
        final XmldbURI collectionUri = collection.getURI();

        getBrokerPool().getProcessMonitor().startJob(ProcessMonitor.ACTION_REMOVE_COLLECTION, collectionUri);

        try {

            @Nullable final Collection parentCollection = collection.getParentURI() == null ? null : getCollection(collection.getParentURI());  // NOTE: we already have a WRITE lock on the parent of the Collection we set out to remove

            // 1) check the current user has permission to delete the Collection
            //TODO(AR) the below permissions check could be optimised when descending the tree so we don't check the same collection(s) twice in some cases
            if(!checkRemoveCollectionPermissions(parentCollection, collection)) {
                throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' is not allowed to remove collection '" + collection.getURI() + "'");
            }

            final CollectionTrigger colTrigger = new CollectionTriggers(this, transaction, parentCollection == null ? collection : parentCollection);
            colTrigger.beforeDeleteCollection(this, transaction, collection);

            // 2) remove descendant collections
            for (final Iterator<XmldbURI> subCollectionName = collection.collectionIteratorNoLock(this); subCollectionName.hasNext(); ) {   // NOTE: we already have a WRITE lock on the parent of the Collection we set out to remove
                final XmldbURI subCollectionUri = collectionUri.append(subCollectionName.next());
                final boolean removedSubCollection = _removeCollection(transaction, getCollection(subCollectionUri));   // NOTE: we already have a WRITE lock on the parent of the Collection we set out to remove
                if(!removedSubCollection) {
                    LOG.error("Unable to remove Collection: {}", subCollectionUri);
                    return false;
                }
            }

            //TODO(AR) this can be executed asynchronously as a task, Do we need to await the completion before unlocking the collection? or just await completion before returning from the first call to _removeCollection?
            // 3) drop indexes for this Collection
            notifyDropIndex(collection);
            getIndexController().removeCollection(collection, this, false);

            // 4) remove this Collection from the parent Collection
            if(parentCollection != null) {
                parentCollection.removeCollection(this, collectionUri.lastSegment());
                saveCollection(transaction, parentCollection);
            }

            // 5) remove Collection from collections.dbx
            if(parentCollection != null) {
                try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
                    final Value key = new CollectionStore.CollectionKey(collectionUri.getRawCollectionPath());
                    collectionsDb.remove(transaction, key);

                    //TODO(AR) is this the correct place to invalidate the config?
                    // Notify the collection configuration manager
                    final CollectionConfigurationManager manager = pool.getConfigurationManager();
                    if(manager != null) {
                        manager.invalidate(collectionUri, getBrokerPool());
                    }
                }

                // invalidate the cache entry
                final CollectionCache collectionsCache = pool.getCollectionsCache();
                collectionsCache.invalidate(collection.getURI());
            } else {
                // if this is the root collection we just have to save
                // it to persist the removal of any subCollections to collections.dbx
                saveCollection(transaction, collection);
            }

            //TODO(AR) this could possibly be executed asynchronously as a task, we don't need to know when it completes (this is because access to documents is through a Collection, and the Collection was removed above), however we cannot recycle the collectionId until all docs are gone
            // 6) unlink all documents from the Collection
            try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
                final Value docKey = new CollectionStore.DocumentKey(collection.getId());
                final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, docKey);
                collectionsDb.removeAll(transaction, query);
                if(parentCollection != null) {  // we must not free the root collection id!
                    collectionsDb.freeCollectionId(collection.getId());
                }
            } catch(final BTreeException | IOException e) {
                LOG.error("Unable to unlink documents from the Collection: {}", collectionUri, e);
            }

            //TODO(AR) this can be executed asynchronously as a task, we need to await the completion before unlocking the collection
            // 7) remove the documents nodes and binary documents of the Collection from dom.dbx
            removeCollectionsDocumentNodes(transaction, collection);

            colTrigger.afterDeleteCollection(this, transaction, collectionUri);

            return true;

        } catch(final LockException e) {
            LOG.error("Unable to lock Collection: {}", collectionUri, e);
            return false;
        } finally {
            getBrokerPool().getProcessMonitor().endJob();
        }
    }

    private void removeCollectionsDocumentNodes(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection collection)
            throws TriggerException, PermissionDeniedException, LockException, IOException {
        final DocumentTrigger docTrigger = new DocumentTriggers(this, transaction, collection);

        for (final Iterator<DocumentImpl> itDocument = collection.iteratorNoLock(this); itDocument.hasNext(); ) {       // NOTE: we already have a WRITE_LOCK on the collection
            final DocumentImpl doc = itDocument.next();

            docTrigger.beforeDeleteDocument(this, transaction, doc);

            //Remove doc's metadata
            // WM: now removed in one step. see above.
            //removeResourceMetadata(transaction, doc);
            //Remove document nodes' index entries
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        final Value ref = new NodeRef(doc.getDocId());
                        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                        domDb.remove(transaction, query, null);
                    } catch (final BTreeException e) {
                        LOG.error("btree error while removing document", e);
                    } catch (final IOException e) {
                        LOG.error("io error while removing document", e);
                    } catch (final TerminatedException e) {
                        LOG.error("method terminated", e);
                    }
                    return null;
                }
            }.run();

            //Remove nodes themselves
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    if (doc.getResourceType() == DocumentImpl.XML_FILE) {
                        final NodeHandle node = (NodeHandle) doc.getFirstChild();
                        domDb.removeAll(transaction, node.getInternalAddress());
                    }
                    return null;
                }
            }.run();

            // if it is a binary document remove the content from disk
            if (doc instanceof BinaryDocument) {
                removeCollectionBinary(transaction, (BinaryDocument)doc);
            }

            docTrigger.afterDeleteDocument(this, transaction, doc.getURI());

            //Make doc's id available again
            collectionsDb.freeResourceId(doc.getDocId());
        }
    }

    private void removeCollectionBinary(final Txn transaction, final BinaryDocument doc) throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        blobStore.remove(transaction, doc.getBlobId());
    }

    /**
     * Checks that the current user has permissions to remove the Collection
     *
     * @param parentCollection The parent Collection or null if we are testing the root Collection
     * @param collection The Collection to check permissions for removal
     *
     * @return true if the current user is allowed to remove the Collection
     */
    private boolean checkRemoveCollectionPermissions(
            @Nullable @EnsureLocked(mode=LockMode.READ_LOCK) final Collection parentCollection,
            @EnsureLocked(mode=LockMode.READ_LOCK) final Collection collection) throws PermissionDeniedException {
        // parent collection permissions
        if(parentCollection != null) {
            if (!parentCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                return false;
            }
            if (!parentCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                return false;
            }
        }

        // collection permissions
        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.READ)) {
            return false;
        }

        if(!collection.isEmpty(this)) {
            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                return false;
            }

            if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Acquires a write lock on a Collection
     *
     * @param collectionUri The uri of the collection to lock
     *
     * @return A managed lock for the Collection
     */
    private ManagedCollectionLock writeLockCollection(final XmldbURI collectionUri) throws LockException {
        return lockManager.acquireCollectionWriteLock(collectionUri);
    }

    /**
     * Acquires a READ lock on a Collection
     *
     * @param collectionUri The uri of the collection to lock
     *
     * @return A managed lock for the Collection
     */
    private ManagedCollectionLock readLockCollection(final XmldbURI collectionUri) throws LockException {
        return lockManager.acquireCollectionReadLock(collectionUri);
    }

    @Override
    public void saveCollection(final Txn transaction, final Collection collection) throws IOException {
        if(collection == null) {
            LOG.error("NativeBroker.saveCollection called with collection == null! Aborting.");
            return;
        }

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        final CollectionCache collectionsCache = pool.getCollectionsCache();
        collectionsCache.put(collection);

        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
            final Value name = new CollectionStore.CollectionKey(collection.getURI().toString());
            try(final VariableByteOutputStream os = new VariableByteOutputStream(256)) {
                collection.serialize(os);
                final long address = collectionsDb.put(transaction, name, os.data(), true);
                if (address == BFile.UNKNOWN_ADDRESS) {
                    throw new IOException("Could not store collection data for '" + collection.getURI() + "', address=BFile.UNKNOWN_ADDRESS");
                }
            }
        } catch(final LockException e) {
            throw new IOException(e);
        }
    }

    /**
     * Get the next available unique collection id.
     * @param transaction the transaction
     * @return next available unique collection id
     * @throws ReadOnlyException in response to an readonly error
     * @throws LockException in case of a lock error
     */
    public int getNextCollectionId(final Txn transaction) throws ReadOnlyException, LockException {
        int nextCollectionId = collectionsDb.getFreeCollectionId();
        if(nextCollectionId != Collection.UNKNOWN_COLLECTION_ID) {
            return nextCollectionId;
        }

        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.CollectionKey(CollectionStore.NEXT_COLLECTION_ID_KEY);
            final Value data = collectionsDb.get(key);
            if(data != null) {
                nextCollectionId = ByteConversion.byteToInt(data.getData(), OFFSET_COLLECTION_ID);
                ++nextCollectionId;
            } else {
                nextCollectionId = FIRST_COLLECTION_ID;
            }
            final byte[] d = new byte[Collection.LENGTH_COLLECTION_ID];
            ByteConversion.intToByte(nextCollectionId, d, OFFSET_COLLECTION_ID);
            collectionsDb.put(transaction, key, d, true);
            return nextCollectionId;
        }
    }

    @Override
    public void reindexCollection(final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, LockException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        final XmldbURI fqUri = prepend(collectionUri.toCollectionPathURI());
        final long start = System.currentTimeMillis();
        try(final Collection collection = openCollection(fqUri, LockMode.READ_LOCK)) {
            if (collection == null) {
                LOG.warn("Collection {} not found!", fqUri);
                return;
            }

            LOG.info("Start indexing collection {}", collection.getURI().toString());
            pool.getProcessMonitor().startJob(ProcessMonitor.ACTION_REINDEX_COLLECTION, collection.getURI());
            reindexCollection(transaction, collection, IndexMode.STORE);
        } catch(final PermissionDeniedException | IOException e) {
            LOG.error("An error occurred during reindex: {}", e.getMessage(), e);
        } finally {
            pool.getProcessMonitor().endJob();
            LOG.info("Finished indexing collection {} in {} ms.", fqUri, System.currentTimeMillis() - start);
        }
    }

    private void reindexCollection(final Txn transaction,
            @EnsureLocked(mode=LockMode.READ_LOCK) final Collection collection, final IndexMode mode)
            throws PermissionDeniedException, IOException, LockException {
        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
        }

        LOG.debug("Reindexing collection {}", collection.getURI());
        if(mode == IndexMode.STORE) {
            dropCollectionIndex(transaction, collection, true);
        }

        // reindex documents
        try {
            for (final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
                final DocumentImpl next = i.next();
                reindexXMLResource(transaction, next, mode);
            }
        } catch(final LockException e) {
            LOG.error("LockException while reindexing documents of collection '{}'. Skipping...", collection.getURI(), e);
        }

        // descend into child collections
        try {
            for (final Iterator<XmldbURI> i = collection.collectionIterator(this); i.hasNext(); ) {
                final XmldbURI childName = i.next();
                final XmldbURI childUri = collection.getURI().append(childName);
                try(final Collection child = openCollection(childUri, LockMode.READ_LOCK)) {
                    if (child == null) {
                        throw new IOException("Collection '" + childUri + "' not found");
                    } else {
                        reindexCollection(transaction, child, mode);
                    }
                }
            }
        } catch(final LockException e) {
            LOG.error("LockException while reindexing child collections of collection '{}'. Skipping...", collection.getURI(), e);
        }
    }

    private void dropCollectionIndex(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection collection)
            throws PermissionDeniedException, IOException, LockException {
        dropCollectionIndex(transaction, collection, false);
    }

    private void dropCollectionIndex(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final Collection collection, final boolean reindex)
            throws PermissionDeniedException, IOException, LockException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }
        if(!collection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on collection " + collection.getURI());
        }
        notifyDropIndex(collection);
        getIndexController().removeCollection(collection, this, reindex);
        for (final Iterator<DocumentImpl> i = collection.iterator(this); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            LOG.debug("Dropping index for document {}", doc.getFileURI());
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        final Value ref = new NodeRef(doc.getDocId());
                        final IndexQuery query =
                                new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                        domDb.remove(transaction, query, null);
                        domDb.flush();
                    } catch (final TerminatedException | IOException | DBException e) {
                        LOG.error("Error while removing Document '{}' from Collection index: {}", doc.getURI().lastSegment(), collection.getURI(), e);
                    }
                    return null;
                }
            }.run();
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
            final XmldbURI docName = XmldbURI.create(MessageDigester.md5(Thread.currentThread().getName() + System.currentTimeMillis(), false) + ".xml");

            //get the temp collection
            try(final Txn transaction = transact.beginTransaction();
                    final ManagedCollectionLock tempCollectionLock = lockManager.acquireCollectionWriteLock(XmldbURI.TEMP_COLLECTION_URI)) {

                // if temp collection does not exist, creates temp collection (with write lock in Txn)
                final Tuple2<Boolean, Collection> createdOrExistingTemp = getOrCreateTempCollection(transaction);
                if (createdOrExistingTemp == null) {
                    LOG.error("Failed to create temporary collection");
                    transact.abort(transaction);
                    return null;
                }

                final Collection temp = createdOrExistingTemp._2;

                //create a temporary document
                try (final ManagedDocumentLock docLock = lockManager.acquireDocumentWriteLock(temp.getURI().append(docName))) {
                    final int tmpDocId = getNextResourceId(transaction);
                    final Permission permission = PermissionFactory.getDefaultResourcePermission(getBrokerPool().getSecurityManager());
                    permission.setMode(Permission.DEFAULT_TEMPORARY_DOCUMENT_PERM);
                    final DocumentImpl targetDoc = new DocumentImpl(null, pool, temp, tmpDocId, docName, permission, 0, null, System.currentTimeMillis(), null, null, null);

                    //index the temporary document
                    final DOMIndexer indexer = new DOMIndexer(this, transaction, doc, targetDoc);
                    indexer.scan();
                    indexer.store();
                    //store the temporary document
                    temp.addDocument(transaction, this, targetDoc);

                    storeXMLResource(transaction, targetDoc);

                    saveCollection(transaction, temp);

                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    temp.close();

                    flush();
                    closeDocument();
                    //commit the transaction
                    transact.commit(transaction);
                    return targetDoc;
                }
            } catch (final Exception e) {
                LOG.error("Failed to store temporary fragment: {}", e.getMessage(), e);
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
        try (final Collection temp = openCollection(XmldbURI.TEMP_COLLECTION_URI, LockMode.WRITE_LOCK)) {
            if (temp == null) {
                return;
            }
            final TransactionManager transact = pool.getTransactionManager();
            try (final Txn transaction = transact.beginTransaction()) {
                removeCollection(transaction, temp);
                transact.commit(transaction);
            } catch (final Exception e) {
                LOG.error("Failed to remove temp collection: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public DocumentImpl getResourceById(final int collectionId, final byte resourceType, final int documentId) throws PermissionDeniedException {
        XmldbURI uri;
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {

            //get the collection uri
            String collectionUri = null;
            if (collectionId == FIRST_COLLECTION_ID) {
                collectionUri = "/db";
            } else {
                for(final Value collectionDbKey : collectionsDb.getKeys()) {
                    final byte[] data = collectionDbKey.data();
                    if (data[0] == CollectionStore.KEY_TYPE_COLLECTION) {
                        //Value collectionDbValue = collectionsDb.get(collectionDbKey);

                        final VariableByteInput vbi = collectionsDb.getAsStream(collectionDbKey);
                        final int id = vbi.readInt();
                        //check if the collection id matches (first 4 bytes)
                        if (collectionId == id) {
                            collectionUri = new String(Arrays.copyOfRange(data, 1, data.length));
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
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
            return null;
        } catch(final IOException e) {
            LOG.error("IOException while reading resource data", e);
            return null;
        }

        return getResource(uri, Permission.READ);
    }

    @Override
    public void storeDocument(final Txn transaction, final XmldbURI name, final InputSource source, final @Nullable MimeType mimeType, final Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, this, name, source, mimeType);
    }

    @Override
    public void storeDocument(final Txn transaction, final XmldbURI name, final InputSource source, final @Nullable MimeType mimeType, final @Nullable Date createdDate, final @Nullable Date lastModifiedDate, final @Nullable Permission permission, final @Nullable DocumentType documentType, final @Nullable XMLReader xmlReader, final Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, this, name, source, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader);
    }

    @Override
    public void storeDocument(final Txn transaction, final XmldbURI name, final Node node, final @Nullable MimeType mimeType, final Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, this, name, node, mimeType);
    }

    @Override
    public void storeDocument(final Txn transaction, final XmldbURI name, final Node node, final @Nullable MimeType mimeType, final @Nullable Date createdDate, final @Nullable Date lastModifiedDate, final @Nullable Permission permission, final @Nullable DocumentType documentType, final @Nullable XMLReader xmlReader, final Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, this, name, node, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader);
    }

    /**
     * store Document entry into its collection.
     */
    @Override
    public void storeXMLResource(final Txn transaction, final DocumentImpl doc) {
        try(final VariableByteOutputStream os = new VariableByteOutputStream(256);
                final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
            doc.write(os);
            final Value key = new CollectionStore.DocumentKey(doc.getCollection().getId(), doc.getResourceType(), doc.getDocId());
            collectionsDb.put(transaction, key, os.data(), true);
            //} catch (ReadOnlyException e) {
            //LOG.warn(DATABASE_IS_READ_ONLY);
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException e) {
            LOG.error("IOException while writing document data: {}", doc.getURI(), e);
        }
    }

    @Override
    public void storeMetadata(final Txn transaction, final DocumentImpl doc) throws TriggerException {
        final Collection col = doc.getCollection();
        final DocumentTrigger trigger = new DocumentTriggers(this, transaction, col);

        trigger.beforeUpdateDocumentMetadata(this, transaction, doc);

        storeXMLResource(transaction, doc);

        trigger.afterUpdateDocumentMetadata(this, transaction, doc);
    }

    @Deprecated
    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final byte[] data)
            throws IOException {
        try(final InputStream is = new UnsynchronizedByteArrayInputStream(data)) {
                storeBinaryResource(transaction, blob, is);
        }
    }

    @Override
    public void storeBinaryResource(final Txn transaction, final BinaryDocument blob, final InputStream is)
            throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        final Tuple2<BlobId, Long> blobIdLen = blobStore.add(transaction, is);

        blob.setBlobId(blobIdLen._1);
        blob.setContentLength(blobIdLen._2);
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
        try(final Collection collection = openCollection(collUri, LockMode.READ_LOCK)) {
            if (collection == null) {
                LOG.debug("collection '{}' not found!", collUri);
                return null;
            }

            //if(!collection.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
            //throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getCurrentSubject().getName());
            //}

            try(final LockedDocument lockedDocument = collection.getDocumentWithLock(this, docUri, LockMode.READ_LOCK)) {

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

                if (lockedDocument == null) {
                    LOG.debug("document '{}' not found!", fileName);
                    return null;
                }

                final DocumentImpl doc = lockedDocument.getDocument();
                if (!doc.getPermissions().validate(getCurrentSubject(), accessType)) {
                    throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' not allowed requested access to document '" + fileName + "'");
                }

                return doc;
            } catch(final LockException e) {
                throw new PermissionDeniedException(e);
            }
        }
    }

    @Override
    public LockedDocument getXMLResource(XmldbURI fileName, final LockMode lockMode) throws PermissionDeniedException {
        if(fileName == null) {
            return null;
        }
        fileName = prepend(fileName.toCollectionPathURI());
        //TODO : resolve URIs !
        final XmldbURI collUri = fileName.removeLastSegment();
        final XmldbURI docUri = fileName.lastSegment();
        final LockMode collectionLockMode = lockManager.relativeCollectionLockMode(LockMode.READ_LOCK, lockMode);
        try(final Collection collection = openCollection(collUri, collectionLockMode)) {
            if (collection == null) {
                LOG.debug("Collection '{}' not found!", collUri);
                return null;
            }
            try {
                //if (!collection.getPermissions().validate(getCurrentSubject(), Permission.EXECUTE)) {
                //    throw new PermissionDeniedException("Permission denied to read collection '" + collUri + "' by " + getCurrentSubject().getName());
                //}
                final LockedDocument lockedDocument = collection.getDocumentWithLock(this, docUri, lockMode);

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

                if (lockedDocument == null) {
                    //LOG.debug("document '" + fileName + "' not found!");
                    return null;
                }
                //if (!doc.getMode().validate(getUser(), Permission.READ))
                //throw new PermissionDeniedException("not allowed to read document");
                final DocumentImpl doc = lockedDocument.getDocument();
                return lockedDocument;
            } catch (final LockException e) {
                LOG.error("Could not acquire lock on document {}", fileName, e);
                //TODO : exception ? -pb
            }
        }
        return null;
    }

    @Override
    public void readBinaryResource(final BinaryDocument blob, final OutputStream os)
            throws IOException {
        try (final Txn transaction = continueOrBeginTransaction()) {
            readBinaryResource(transaction, blob, os);
            transaction.commit();
        } catch (final TransactionException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void readBinaryResource(final Txn transaction, final BinaryDocument blob, final OutputStream os)
            throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        try (final InputStream is = blobStore.get(transaction, blob.getBlobId())) {
            if (is != null) {
                if (os instanceof UnsynchronizedByteArrayOutputStream) {
                    ((UnsynchronizedByteArrayOutputStream)os).write(is);
                } else {
                    copy(is, os);
                }
            }
        }
    }

    @Override
    public long getBinaryResourceSize(final BinaryDocument blob)
            throws IOException {
        return blob.getContentLength();
    }

    @Override
    public MessageDigest getBinaryResourceContentDigest(final Txn transaction, final BinaryDocument binaryDocument,
        final DigestType digestType) throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        return blobStore.getDigest(transaction, binaryDocument.getBlobId(), digestType);
    }

    @Override
    public Path getBinaryFile(final BinaryDocument blob) {
        throw new UnsupportedOperationException(
                "No longer supported, use DBBroker#withBinaryFile(Txn, BinaryDocument, Function)");
    }

    @Override
    public <T> T withBinaryFile(final Txn transaction, final BinaryDocument binaryDocument,
            final Function<Path, T> fnFile) throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        return blobStore.with(transaction, binaryDocument.getBlobId(), fnFile);
    }

    @Override
    public InputStream getBinaryResource(final BinaryDocument blob)
            throws IOException {
        // TODO(AR) how best to get the transaction?
        try (final Txn transaction = continueOrBeginTransaction()) {
            final InputStream is = getBinaryResource(transaction, blob);

            transaction.commit();

            return is;
        } catch (final TransactionException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public InputStream getBinaryResource(final Txn transaction, final BinaryDocument blob)
            throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        return blobStore.get(transaction, blob.getBlobId());
    }

    //TODO : consider a better cooperation with Collection -pb
    @Override
    public void getCollectionResources(final Collection.InternalAccess collectionInternalAccess) {
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.DocumentKey(collectionInternalAccess.getId());
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);

            collectionsDb.query(query, new DocumentCallback(collectionInternalAccess));
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException | TerminatedException e) {
            LOG.error("Exception while reading document data", e);
        }
    }

    @Override
    public void getResourcesFailsafe(final Txn transaction, final BTreeCallback callback, final boolean fullScan) throws TerminatedException {
        assert(transaction != null && transaction.getState() == Txn.State.STARTED);
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.DocumentKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            if(fullScan) {
                collectionsDb.rawScan(query, callback);
            } else {
                collectionsDb.query(query, callback);
            }
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException e) {
            LOG.error("Exception while reading document data", e);
        }
    }

    @Override
    public void getCollectionsFailsafe(final Txn transaction, final BTreeCallback callback) throws TerminatedException {
        assert(transaction != null && transaction.getState() == Txn.State.STARTED);
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeReadLock(collectionsDb.getLockName())) {
            final Value key = new CollectionStore.CollectionKey();
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
            collectionsDb.query(query, callback);
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
        } catch(final IOException | BTreeException e) {
            LOG.error("Exception while reading document data", e);
        }
    }

    @Override
    public MutableDocumentSet getXMLResourcesByDoctype(final String doctypeName, final MutableDocumentSet result) throws PermissionDeniedException, LockException {
        final MutableDocumentSet docs = getAllXMLResources(new DefaultDocumentSet());
        for(final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            try(final ManagedDocumentLock documentLock = lockManager.acquireDocumentReadLock(doc.getURI())) {
                final DocumentType doctype = doc.getDoctype();
                if (doctype == null) {
                    continue;
                }
                if (doctypeName.equals(doctype.getName())
                        && doc.getCollection().getPermissionsNoLock().validate(getCurrentSubject(), Permission.READ)
                        && doc.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
                    result.add(doc);
                }
            }
        }
        return result;
    }

    @Override
    public MutableDocumentSet getAllXMLResources(final MutableDocumentSet docs) throws PermissionDeniedException, LockException {
        final long start = System.currentTimeMillis();
        try(final Collection rootCollection = openCollection(XmldbURI.ROOT_COLLECTION_URI, LockMode.READ_LOCK)) {
            rootCollection.allDocs(this, docs, true);
            if(LOG.isDebugEnabled()) {
                LOG.debug("getAllDocuments(DocumentSet) - end - loading {} documents took {} ms.",
                        docs.getDocumentCount(), (System.currentTimeMillis() - start));
            }
            return docs;
        }
    }

    @Override
    public void copyResource(final Txn transaction, final DocumentImpl sourceDocument, final Collection targetCollection, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        copyResource(transaction, sourceDocument, targetCollection, newName, PreserveType.DEFAULT);
    }

    @Override
    public void copyResource(final Txn transaction, final DocumentImpl sourceDocument, final Collection targetCollection, final XmldbURI newDocName, final PreserveType preserve) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        assert(sourceDocument != null);
        assert(targetCollection != null);
        assert(newDocName != null);
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(newDocName.numSegments() != 1) {
            throw new IOException("newName name must be just a name i.e. an XmldbURI with one segment!");
        }

        final XmldbURI sourceDocumentUri = sourceDocument.getURI();
        final XmldbURI targetCollectionUri = targetCollection.getURI();
        final XmldbURI targetDocumentUri = targetCollectionUri.append(newDocName);

        if(!sourceDocument.getPermissions().validate(getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' has insufficient privileges to copy the resource '" + sourceDocumentUri + "'.");
        }

        // we assume the caller holds a READ_LOCK (or better) on sourceDocument#getCollection()
        final Collection sourceCollection = sourceDocument.getCollection();
        if (!sourceCollection.getPermissions().validate(getCurrentSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' has insufficient privileges to copy the resource '" + sourceDocumentUri + "'.");
        }

        if(!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' does not have execute access on the destination collection '" + targetCollectionUri + "'.");
        }

        if(targetCollection.hasChildCollection(this, newDocName.lastSegment())) {
            throw new EXistException("The collection '" + targetCollectionUri + "' already has a sub-collection named '" + newDocName.lastSegment() + "', you cannot create a Document with the same name as an existing collection.");
        }

        try(final LockedDocument oldLockedDoc = targetCollection.getDocumentWithLock(this, newDocName, LockMode.WRITE_LOCK)) {
            final DocumentTrigger trigger = new DocumentTriggers(this, transaction, targetCollection);

            final DocumentImpl oldDoc = oldLockedDoc == null ? null : oldLockedDoc.getDocument();
            if (oldDoc == null) {
                if (!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("Account '" + getCurrentSubject().getName() + "' does not have write access on the destination collection '" + targetCollectionUri + "'.");
                }
            } else {
                //overwrite existing document

                if (sourceDocument.getDocId() == oldDoc.getDocId()) {
                    throw new PermissionDeniedException("Cannot copy resource to itself '" + sourceDocumentUri + "'.");
                }

                if (!oldDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
                    throw new PermissionDeniedException("A resource with the same name already exists in the target collection '" + oldDoc.getURI() + "', and you do not have write access on that resource.");
                }

                trigger.beforeDeleteDocument(this, transaction, oldDoc);
                trigger.afterDeleteDocument(this, transaction, targetDocumentUri);
            }

            doCopyDocument(transaction, trigger, sourceDocument, targetCollection, newDocName, oldDoc, preserve);
        }
    }

    /**
     * Creates a new Document object for the destination document
     * - copies the nodes from the source document to the destination document
     * - if no existing document in the destination:
     *      - adds the destination document to the destination collection
     *   else, switches the existing document object for the new document in the destination collection
     *
     *   asynchronously deletes the nodes of the old existing document
     */
    private void doCopyDocument(final Txn transaction, final DocumentTrigger trigger,
            final DocumentImpl sourceDocument, final Collection targetCollection, final XmldbURI newDocName,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) @Nullable final DocumentImpl oldDoc, final PreserveType preserve)
            throws TriggerException, LockException, PermissionDeniedException, IOException, EXistException {

        final XmldbURI sourceDocumentUri = sourceDocument.getURI();
        final XmldbURI targetCollectionUri = targetCollection.getURI();
        final XmldbURI targetDocumentUri = targetCollectionUri.append(newDocName);

        trigger.beforeCopyDocument(this, transaction, sourceDocument, targetDocumentUri);

        final DocumentImpl newDocument;
        final LockManager lockManager = getBrokerPool().getLockManager();
        try (final ManagedDocumentLock newDocLock = lockManager.acquireDocumentWriteLock(targetDocumentUri)) {
            final int copiedDocId = getNextResourceId(transaction);
            if (sourceDocument.getResourceType() == DocumentImpl.BINARY_FILE) {
                final BinaryDocument newDoc;
                if (oldDoc != null) {
                    newDoc = new BinaryDocument(null, copiedDocId, oldDoc);
                } else {
                    newDoc = new BinaryDocument(null, getBrokerPool(), targetCollection, copiedDocId, newDocName);
                }

                newDoc.copyOf(this, sourceDocument, oldDoc);

                if (preserveOnCopy(preserve)) {
                    copyResource_preserve(this, sourceDocument, newDoc, oldDoc != null);
                }

                copyBinaryResource(transaction, (BinaryDocument)sourceDocument, newDoc);
                newDocument = newDoc;
            } else {
                final DocumentImpl newDoc;
                if (oldDoc != null) {
                    newDoc = new DocumentImpl(null, copiedDocId, oldDoc);
                } else {
                    newDoc = new DocumentImpl(null, pool, targetCollection, copiedDocId, newDocName);
                }

                newDoc.copyOf(this, sourceDocument, oldDoc);

                copyXMLResource(transaction, sourceDocument, newDoc);
                if (preserveOnCopy(preserve)) {
                    copyResource_preserve(this, sourceDocument, newDoc, oldDoc != null);
                }

                newDocument = newDoc;
            }

            /*
             * Stores the document entry for newDstDoc,
             * or overwrites the document entry for currentDstDoc with
             * the entry for newDstDoc, in collections.dbx.
             */
            storeXMLResource(transaction, newDocument);

            // must be the last action (before cleanup), as this will make newDstDoc available to other threads!
            targetCollection.addDocument(transaction, this, newDocument);

            // NOTE: copied document is now live!


            // TODO (AR) this could be done asynchronously in future perhaps?
            // cleanup the old destination doc (if present)
            if (oldDoc != null) {
                if (oldDoc.getResourceType() == DocumentImpl.XML_FILE) {
                    // drop the index and dom nodes of the old document
                    dropIndex(transaction, oldDoc);
                    dropDomNodes(transaction, oldDoc);

                } else {
                    // remove the blob of the old document
                    final BlobStore blobStore = pool.getBlobStore();
                    blobStore.remove(transaction, ((BinaryDocument)oldDoc).getBlobId());
                }

                // remove oldDoc entry from collections.dbx
                removeResourceMetadata(transaction, oldDoc);

                // TODO(AR) do we need a freeId flag to control this?
                // recycle the id
                collectionsDb.freeResourceId(oldDoc.getDocId());

                // The Collection object oldDstDoc is now an empty husk which is
                // not available or referenced from anywhere, it will be subject
                // to garbage collection
            }
        }

        trigger.afterCopyDocument(this, transaction, newDocument, sourceDocumentUri);
    }

    /**
     * Preserves attributes when copying a resource.
     * e.g. `cp --preserve`
     *
     * @param broker the eXist-db DBBroker
     * @param srcDocument The source document.
     * @param destDocument The destination document.
     * @param overwrittingDest if true it overwrites the destination resource
     * @throws PermissionDeniedException if user does not have sufficient rights
     *
     */
    public static void copyResource_preserve(final DBBroker broker, final DocumentImpl srcDocument, final DocumentImpl destDocument, final boolean overwrittingDest) throws PermissionDeniedException {
        final Permission srcPermissions = srcDocument.getPermissions();
        final Permission destPermissions = destDocument.getPermissions();

        // only copy the owner and group from the source if we are creating a new file and we are the DBA
        if ((!overwrittingDest) && broker.getCurrentSubject().hasDbaRole()) {
            PermissionFactory.chown(broker, destPermissions, Optional.of(srcPermissions.getOwner().getName()), Optional.of(srcPermissions.getGroup().getName()));
        }

        copyModeAcl(broker, srcPermissions, destPermissions);

        // btime (birth time)
        if (!overwrittingDest) {
            destDocument.setCreated(srcDocument.getLastModified());  // Indeed! ...the birth time of the dest file is the last modified time of the source file
        }

        // mtime (modified time)
        destDocument.setLastModified(srcDocument.getLastModified());

    }

    /**
     * Copies the Mode and ACL (if present) from one
     * object to another.
     *
     * @param srcPermissions The permissions of the source object.
     * @param destPermissions The permissions of the destination object.
     */
    private static void copyModeAcl(final DBBroker broker, final Permission srcPermissions, final Permission destPermissions) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, destPermissions, Optional.of(srcPermissions.getMode()), Optional.empty());
        if (srcPermissions instanceof SimpleACLPermission && destPermissions instanceof SimpleACLPermission) {
            PermissionFactory.chacl(destPermissions, newAcl ->
                ((SimpleACLPermission)newAcl).copyAclOf((SimpleACLPermission)srcPermissions)
            );
        }
    }

    private void copyXMLResource(final Txn transaction,
            @EnsureLocked(mode=LockMode.READ_LOCK) final DocumentImpl oldDoc,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl newDoc) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Copying document {} to {}", oldDoc.getFileURI(), newDoc.getURI());
        }
        final long start = System.currentTimeMillis();
        final StreamListener listener = getIndexController().getStreamListener(newDoc, ReindexMode.STORE);
        final NodeList nodes = oldDoc.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
            try(final INodeIterator iterator = getNodeIterator(node)) {
                iterator.next();
                copyNodes(transaction, iterator, node, new NodePath2(), newDoc, false, listener);
            }
        }
        flush();
        closeDocument();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Copy took {} ms.", (System.currentTimeMillis() - start));
        }
    }

    private void copyBinaryResource(final Txn transaction, final BinaryDocument srcDoc, final BinaryDocument dstDoc) throws IOException {
        final BlobStore blobStore = pool.getBlobStore();
        final BlobId dstBlobId = blobStore.copy(transaction, srcDoc.getBlobId());

        dstDoc.setBlobId(dstBlobId);
        dstDoc.setContentLength(srcDoc.getContentLength());
    }


    @Override
    public void moveResource(final Txn transaction, final DocumentImpl sourceDocument, final Collection targetCollection, final XmldbURI newName) throws PermissionDeniedException, LockException, IOException, TriggerException {
        assert(sourceDocument != null);
        assert(targetCollection != null);
        assert(newName != null);

        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(newName.numSegments() != 1) {
            throw new IOException("newName name must be just a name i.e. an XmldbURI with one segment!");
        }

        final XmldbURI sourceDocumentUri = sourceDocument.getURI();
        final XmldbURI targetCollectionUri = targetCollection.getURI();
        final XmldbURI destinationDocumentUri = targetCollectionUri.append(newName);

        final Account docUser = sourceDocument.getUserLock();
        if(docUser != null) {
            if(!getCurrentSubject().getName().equals(docUser.getName())) {
                throw new PermissionDeniedException("Cannot move '" + sourceDocumentUri + " because is locked by getUser() '" + docUser.getName() + "'");
            }
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

        // we assume the caller holds a WRITE_LOCK on sourceDocument#getCollection()
        final Collection sourceCollection = sourceDocument.getCollection();
        if(!sourceCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on source Collection to move resource: " + sourceDocumentUri);
        }

        if(!targetCollection.getPermissionsNoLock().validate(getCurrentSubject(), Permission.WRITE | Permission.EXECUTE)) {
            throw new PermissionDeniedException("Account " + getCurrentSubject().getName() + " have insufficient privileges on destination Collection '" + targetCollectionUri + "' to move resource: " + sourceDocumentUri);
        }

        if(targetCollection.hasChildCollection(this, newName.lastSegment())) {
            throw new PermissionDeniedException(
                "The Collection '" + targetCollectionUri + "' has a sub-collection '" + newName + "'; cannot create a Document with the same name!"
            );
        }

        final DocumentTrigger trigger = new DocumentTriggers(this, transaction, sourceCollection);

        // check if the move would overwrite a document
        final DocumentImpl oldDoc = targetCollection.getDocument(this, newName);
        if(oldDoc != null) {

            if(sourceDocument.getDocId() == oldDoc.getDocId()) {
                throw new PermissionDeniedException("Cannot move resource to itself '" + sourceDocumentUri + "'.");
            }

            // GNU mv command would prompt for Confirmation here, you can say yes or pass the '-f' flag. As we cant prompt for confirmation we assume OK
            /* if(!oldDoc.getPermissions().validate(getCurrentSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("Resource with same name exists in target collection and write is denied");
            }
            */

            // remove the existing document
            removeResource(transaction, oldDoc);
        }

        final boolean renameOnly = sourceCollection.getId() == targetCollection.getId();

        trigger.beforeMoveDocument(this, transaction, sourceDocument, destinationDocumentUri);

        if(sourceDocument.getResourceType() == DocumentImpl.XML_FILE) {
            if (!renameOnly) {
                dropIndex(transaction, sourceDocument);
            }
        }

        sourceCollection.unlinkDocument(this, sourceDocument);
        if(!renameOnly) {
            saveCollection(transaction, sourceCollection);
        }

        removeResourceMetadata(transaction, sourceDocument);

        sourceDocument.setFileURI(newName);
        sourceDocument.setCollection(targetCollection);
        targetCollection.addDocument(transaction, this, sourceDocument);

        if(sourceDocument.getResourceType() == DocumentImpl.XML_FILE) {
            if(!renameOnly) {
                // reindexing
                reindexXMLResource(transaction, sourceDocument, IndexMode.REPAIR);
            }
        }

            // NOTE: nothing needs to be done for binary resources as the reference to the Blob does not change

        storeXMLResource(transaction, sourceDocument);
        saveCollection(transaction, targetCollection);

        trigger.afterMoveDocument(this, transaction, sourceDocument, sourceDocumentUri);
    }

    @Override
    public void removeXMLResource(final Txn transaction, final DocumentImpl document, final boolean freeDocId) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }
        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Removing document {} ({}) ...", document.getFileURI(), document.getDocId());
            }

            final DocumentTrigger trigger = new DocumentTriggers(this, transaction);

            if(freeDocId) {
                trigger.beforeDeleteDocument(this, transaction, document);
            }

            dropIndex(transaction, document);
            if(LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - removing dom");
            }
            dropDomNodes(transaction, document);
            removeResourceMetadata(transaction, document);

            if(freeDocId) {
                collectionsDb.freeResourceId(document.getDocId());
                trigger.afterDeleteDocument(this, transaction, document.getURI());
            }
        } catch(final TriggerException e) {
            LOG.error(e);
        }
    }

    private void dropIndex(final Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl document) {
        final StreamListener listener = getIndexController().getStreamListener(document, ReindexMode.REMOVE_ALL_NODES);
        listener.startIndexDocument(transaction);
        final NodeList nodes = document.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
            try(final INodeIterator iterator = getNodeIterator(node)) {
                iterator.next();
                scanNodes(transaction, iterator, node, new NodePath2(), IndexMode.REMOVE, listener);
            } catch(final IOException ioe) {
                LOG.error("Unable to close node iterator", ioe);
            }
        }
        listener.endIndexDocument(transaction);
        notifyDropIndex(document);
        getIndexController().flush();
    }

    private void dropDomNodes(final Txn transaction, final DocumentImpl document) {
        try {
            if(!document.isReferenced()) {
                new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                    @Override
                    public Object start() {
                        final NodeHandle node = (NodeHandle) document.getFirstChild();
                        domDb.removeAll(transaction, node.getInternalAddress());
                        return null;
                    }
                }.run();
            }
        } catch(final NullPointerException npe0) {
            LOG.error("Caught NPE in DOMTransaction to actually be able to remove the document.");
        }

        final NodeRef ref = new NodeRef(document.getDocId());
        final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
            @Override
            public Object start() {
                try {
                    domDb.remove(transaction, idx, null);
                } catch(final BTreeException | IOException e) {
                    LOG.error("start() - " + "error while removing doc", e);
                } catch(final TerminatedException e) {
                    LOG.error("method terminated", e);
                }
                return null;
            }
        }.run();
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final BinaryDocument blob) throws PermissionDeniedException, IOException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("removing binary resource {}...", blob.getDocId());
        }

        if (blob.getBlobId() == null) {
            LOG.warn("Trying to delete binary document: {}, but blobId was null", blob.getURI());
            return;
        }

        final BlobStore blobStore = pool.getBlobStore();
        blobStore.remove(transaction, blob.getBlobId());

        // remove the file from the database metadata and indexes
        removeResourceMetadata(transaction, blob);
        getIndexController().setDocument(blob, ReindexMode.REMOVE_BINARY);
        getIndexController().flush();
    }

    @Override
    public void removeResourceMetadata(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl document) {
        // remove document metadata
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Removing resource metadata for {}", document.getDocId());
            }
            final Value key = new CollectionStore.DocumentKey(document.getCollection().getId(), document.getResourceType(), document.getDocId());
            collectionsDb.remove(transaction, key);
        } catch(final LockException e) {
            LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()));
        }
    }

    @Override
    public void removeResource(final Txn tx, final DocumentImpl doc) throws IOException, PermissionDeniedException {
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
    public int getNextResourceId(final Txn transaction) throws EXistException, LockException {
        int nextDocId = collectionsDb.getFreeResourceId();
        if(nextDocId != DocumentImpl.UNKNOWN_DOCUMENT_ID) {
            return nextDocId;
        }
        nextDocId = 1;
        try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
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
        final StreamListener listener = getIndexController().getStreamListener(doc, ReindexMode.STORE);
        getIndexController().startIndexDocument(transaction, listener);
        try {
            final NodeList nodes = doc.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
                try (final INodeIterator iterator = getNodeIterator(node)) {
                    iterator.next();
                    scanNodes(transaction, iterator, node, new NodePath2(), mode, listener);
                } catch (final IOException ioe) {
                    LOG.error("Unable to close node iterator", ioe);
                }
            }
        } finally {
            getIndexController().endIndexDocument(transaction, listener);
        }
        flush();
    }

    @Override
    public void defragXMLResource(final Txn transaction, final DocumentImpl doc) {
        //TODO : use dedicated function in XmldbURI
        if (LOG.isDebugEnabled())
            LOG.debug("============> Defragmenting document {}", doc.getURI());
        final long start = System.currentTimeMillis();
        try {
            final long firstChild = doc.getFirstChildAddress();
            // dropping old structure index
            dropIndex(transaction, doc);
            // dropping dom index
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        domDb.remove(transaction, idx, null);
                        domDb.flush();
                    } catch(final IOException | DBException e) {
                        LOG.error("start() - " + "error while removing doc", e);
                    } catch(final TerminatedException e) {
                        LOG.error("method terminated", e);
                    }
                    return null;
                }
            }.run();
            // create a copy of the old doc to copy the nodes into it
            final DocumentImpl tempDoc = new DocumentImpl(null, pool, doc.getCollection(), doc.getDocId(), doc.getFileURI());
            tempDoc.copyOf(this, doc, doc);
            final StreamListener listener = getIndexController().getStreamListener(doc, ReindexMode.STORE);
            // copy the nodes
            final NodeList nodes = doc.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                final IStoredNode<?> node = (IStoredNode<?>) nodes.item(i);
                try(final INodeIterator iterator = getNodeIterator(node)) {
                    iterator.next();
                    copyNodes(transaction, iterator, node, new NodePath2(), tempDoc, true, listener);
                }
            }
            flush();
            // remove the old nodes
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    domDb.removeAll(transaction, firstChild);
                    try {
                        domDb.flush();
                    } catch(final DBException e) {
                        LOG.error("start() - error while removing doc", e);
                    }
                    return null;
                }
            }.run();
            doc.copyChildren(tempDoc);
            doc.setSplitCount(0);
            doc.setPageCount(tempDoc.getPageCount());
            storeXMLResource(transaction, doc);
            closeDocument();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Defragmentation took {} ms.", (System.currentTimeMillis() - start));
            }
        } catch(final PermissionDeniedException | IOException e) {
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
            xupdateConsistencyChecks = (Boolean) property;
        }
        if(xupdateConsistencyChecks) {
            LOG.debug("Checking document {}", doc.getFileURI());
            checkXMLResourceTree(doc);
        }
    }

    /**
     * consistency Check of the database; useful after XUpdates;
     * called by {@link #checkXMLResourceConsistency(DocumentImpl)}
     */
    @Override
    public void checkXMLResourceTree(final DocumentImpl doc) {
        LOG.debug("Checking DOM tree for document {}", doc.getFileURI());
        boolean xupdateConsistencyChecks = false;
        final Object property = pool.getConfiguration().getProperty(PROPERTY_XUPDATE_CONSISTENCY_CHECKS);
        if(property != null) {
            xupdateConsistencyChecks = (Boolean) property;
        }
        if(xupdateConsistencyChecks) {
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
                @Override
                public Object start() throws ReadOnlyException {
                    LOG.debug("Pages used: {}", domDb.debugPages(doc, false));
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
                        LOG.debug("node tree: {}", buf.toString());
                        throw new RuntimeException("Error in document tree structure");
                    }
                } catch(final IOException e) {
                    LOG.error(e);
                }
            }
            final NodeRef ref = new NodeRef(doc.getDocId());
            final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        domDb.findKeys(idx);
                    } catch(final BTreeException | IOException e) {
                        LOG.error("start() - " + "error while removing doc", e);
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
        final byte[] data = node.serialize();
        new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName()), doc) {
            @Override
            public Object start() throws ReadOnlyException {
                final long address;
                if(nodeType == Node.TEXT_NODE
                    || nodeType == Node.ATTRIBUTE_NODE
                    || nodeType == Node.CDATA_SECTION_NODE
                    || node.getNodeId().getTreeLevel() > defaultIndexDepth) {
                    address = domDb.add(transaction, data);
                } else {
                    address = domDb.put(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), data);
                }
                if(address == BFile.UNKNOWN_ADDRESS) {
                    LOG.error("address is missing");
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
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
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
            final Value oldVal = new DOMTransaction<Value>(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
                @Override
                public Value start() {
                    return domDb.get(node.getInternalAddress());
                }
            }.run();

            //TODO what can we do about abstracting this out?
            final IStoredNode old = StoredNode.deserialize(oldVal.data(),
                oldVal.start(), oldVal.getLength(),
                node.getOwnerDocument(), false);
            LOG.error(
                "Exception while storing {}; gid = {}; old = {}",  node.getNodeName(), node.getNodeId(), old.getNodeName(), e);
        }
    }

    /**
     * Physically insert a node into the DOM storage.
     */
    @Override
    public void insertNodeAfter(final Txn transaction, final NodeHandle previous, final IStoredNode node) {
        final byte[] data = node.serialize();
        final DocumentImpl doc = previous.getOwnerDocument();
        new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName()), doc) {
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
        ByteArrayPool.releaseByteArray(data);
    }

    private <T extends IStoredNode> void copyNodes(final Txn transaction, final INodeIterator iterator, final IStoredNode<T> node,
                           final NodePath currentPath, @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl newDoc, final boolean defragment,
                           final StreamListener listener) {
        copyNodes(transaction, iterator, node, currentPath, newDoc, defragment, listener, null);
    }

    private <T extends IStoredNode> void copyNodes(final Txn transaction, final INodeIterator iterator, final IStoredNode<T> node,
                                                   final NodePath currentPath, @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl newDoc, final boolean defragment,
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
                    LOG.debug("Unhandled node type: {}", node.getNodeType());
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
    public <T extends IStoredNode> void removeNode(final Txn transaction, final IStoredNode<T> node,
            final NodePath currentPath, final String content) {
        final DocumentImpl doc = node.getOwnerDocument();
        new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName()), doc) {
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
                final QNameRangeIndexSpec qnSpecElement = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpecElement != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeElement((ElementImpl) node, content, qnSpecElement.getType(),
                        NativeValueIndex.IndexType.QNAME, false);
                }
                break;

            case Node.ATTRIBUTE_NODE:
                qname = new QName(node.getQName(), ElementValue.ATTRIBUTE);
                node.setQName(qname);
                currentPath.addComponent(qname);
                //Strange : does it mean that the node is added 2 times under 2 different identities ?
                final AttrImpl attr;
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
                final QNameRangeIndexSpec qnSpecAttribute = doc.getCollection().getIndexByQNameConfiguration(this, qname);
                if(qnSpecAttribute != null) {
                    valueIndex.setDocument(doc);
                    valueIndex.storeAttribute(attr, null, qnSpecAttribute, false);
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

            final Deque<RemovedNode> stack = new ArrayDeque<>();
            collectNodesForRemoval(transaction, stack, iterator, listener, node, currentPath);
            while(!stack.isEmpty()) {
                final RemovedNode next = stack.pop();
                removeNode(transaction, next.node, next.path, next.content);
            }
        } catch(final IOException ioe) {
            LOG.error("Unable to close node iterator", ioe);
        }
    }

    private <T extends IStoredNode> void collectNodesForRemoval(final Txn transaction, final Deque<RemovedNode> stack,
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
                final IStoredNode child = iterator.next();
                if(i > 0 && !(child.getNodeId().isSiblingOf(previous.getNodeId()) &&
                    child.getNodeId().compareTo(previous.getNodeId()) > 0)) {
                    LOG.fatal("node {} cannot be a sibling of {}; node read from {}", child.getNodeId(),
                            previous.getNodeId(), StorageAddress.toString(child.getInternalAddress()));
                    docIsValid = false;
                }
                previous = child;
                if(child == null) {
                    LOG.fatal("child {} not found for node: {}: {}; children = {}", i, node.getNodeName(),  node.getNodeId(), node.getChildCount());
                    docIsValid = false;
                    //TODO : emergency exit ?
                }
                final NodeId parentId = child.getNodeId().getParentId();
                if(!parentId.equals(node.getNodeId())) {
                    LOG.fatal("{} is not a child of {}", child.getNodeId(), node.getNodeId());
                    docIsValid = false;
                }
                final boolean check = checkNodeTree(iterator, child, buf);
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
                           final NodePath2 currentPath, final IndexMode mode, final StreamListener listener) {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            currentPath.addNode(node);
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
                    LOG.debug("Unhandled node type: {}", node.getNodeType());
            }
        }
        if(node.hasChildNodes() || node.hasAttributes()) {
            final int count = node.getChildCount();
            for(int i = 0; i < count; i++) {
                final IStoredNode child = iterator.next();
                if(child == null) {
                    LOG.fatal("child {} not found for node: {}; children = {}", i, node.getNodeName(), node.getChildCount());
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
            currentPath.removeLastNode();
        }
    }

    @Override
    public String getNodeValue(final IStoredNode node, final boolean addWhitespace) {
        return new DOMTransaction<String>(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
            @Override
            public String start() {
                return domDb.getNodeValue(NativeBroker.this, node, addWhitespace);
            }
        }.run();
    }

    @Override
    public IStoredNode objectWith(final Document doc, final NodeId nodeId) {
        return new DOMTransaction<IStoredNode<?>>(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
            @Override
            public IStoredNode<?> start() {
                final Value val = domDb.get(NativeBroker.this, new NodeProxy(null, (DocumentImpl) doc, nodeId));
                if(val == null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Node {} not found. This is usually not an error.", nodeId);
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
        return new DOMTransaction<IStoredNode<?>>(this, domDb, () -> lockManager.acquireBtreeReadLock(domDb.getLockName())) {
            @Override
            public IStoredNode<?> start() {
                // DocumentImpl sets the nodeId to DOCUMENT_NODE when it's trying to find its top-level
                // children (for which it doesn't persist the actual node ids), so ignore that.  Nobody else
                // should be passing DOCUMENT_NODE into here.
                final boolean fakeNodeId = p.getNodeId().equals(NodeId.DOCUMENT_NODE);
                final Value val = domDb.get(p.getInternalAddress(), false);
                if(val == null) {
                    LOG.debug("Node {} not found in document {}; docId = {}: {}", p.getNodeId(), p.getOwnerDocument().getURI(),
                            p.getOwnerDocument().getDocId(), StorageAddress.toString(p.getInternalAddress()));
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
                        "Node {} not found in document {}; docId = {}: {}; found node {} instead", p.getNodeId(), p.getOwnerDocument().getURI(),
                            p.getOwnerDocument().getDocId(), StorageAddress.toString(p.getInternalAddress()), node.getNodeId()
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
    public void repair() throws PermissionDeniedException, IOException, LockException {
        if(isReadOnly()) {
            throw new IOException(DATABASE_IS_READ_ONLY);
        }

        LOG.info("Removing index files ...");
        try {
            notifyCloseAndRemove();
            pool.getIndexManager().removeIndexes();
        } catch(final DBException e) {
            LOG.error("Failed to remove index files during repair: {}", e.getMessage(), e);
        }

        LOG.info("Recreating index files ...");
        try {
            this.valueIndex = new NativeValueIndex(this, VALUES_DBX_ID, dataDir, config);
        } catch(final DBException e) {
            LOG.error("Exception during repair: {}", e.getMessage(), e);
        }

        try {
            pool.getIndexManager().reopenIndexes();
        } catch(final DatabaseConfigurationException e) {
            LOG.error("Failed to reopen index files after repair: {}", e.getMessage(), e);
        }

        loadIndexModules();
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
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(btree.getLockName())) {
            LOG.info("Rebuilding index {}", FileUtils.fileName(btree.getFile()));
            btree.rebuild();
            LOG.info("Index {} was rebuilt.", FileUtils.fileName(btree.getFile()));
        } catch(final LockException | IOException | TerminatedException | DBException e) {
            LOG.error("Caught error while rebuilding core index {}: {}", FileUtils.fileName(btree.getFile()), e.getMessage(), e);
        }
    }

    @Override
    public void flush() {
        notifyFlush();
        try {
            pool.getSymbols().flush();
        } catch(final EXistException e) {
            LOG.error(e);
        }
        getIndexController().flush();
        nodesCount = 0;
    }

    long nextReportTS = System.currentTimeMillis();

    @Override
    public void sync(final Sync syncEvent) {
        if(isReadOnly()) {
            return;
        }
        try {
            new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        domDb.flush();
                    } catch(final DBException e) {
                        LOG.error("error while flushing dom.dbx", e);
                    }
                    return null;
                }
            }.run();
            if(syncEvent == Sync.MAJOR) {
                try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
                    collectionsDb.flush();
                } catch(final LockException e) {
                    LOG.error("Failed to acquire lock on {}", FileUtils.fileName(collectionsDb.getFile()), e);
                }
                notifySync();
                pool.getIndexManager().sync();

                if (System.currentTimeMillis() > nextReportTS) {
	                final NumberFormat nf = NumberFormat.getNumberInstance();
    	            LOG_STATS.info("Memory: {}K total; {}K max; {}K free", nf.format(run.totalMemory() / 1024),
                            nf.format(run.maxMemory() / 1024), nf.format(run.freeMemory() / 1024));
               		domDb.printStatistics();
                	collectionsDb.printStatistics();
                	notifyPrintStatistics();

                    nextReportTS = System.currentTimeMillis() + (10 * 60 * 1000); // occurs after 10 minutes from now
                }
            }
        } catch(final DBException dbe) {
            dbe.printStackTrace();
            LOG.error(dbe);
        }
    }

    @Override
    public void shutdown() {
        try {
            flush();
            sync(Sync.MAJOR);

            new DOMTransaction(NativeBroker.this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                @Override
                public Object start() {
                    try {
                        domDb.close();
                    } catch(final DBException e) {
                        LOG.error(e.getMessage(), e);
                    }
                    return null;
                }
            }.run();

            try(final ManagedLock<ReentrantLock> collectionsDbLock = lockManager.acquireBtreeWriteLock(collectionsDb.getLockName())) {
                collectionsDb.close();
            }

            notifyClose();
        } catch(final Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {
            xmlSerializerPool.close();
        }
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
        new DOMTransaction(this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
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
                LOG.error("illegal node: {}", node.getNodeName());
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
            // we store all nodes at level 1 (see - https://github.com/eXist-db/exist/issues/1691), and only element nodes after!
            if(indexMode == IndexMode.STORE && (level == 1 || (node.getNodeType() == Node.ELEMENT_NODE && level <= defaultIndexDepth))) {
                //TODO : used to be this, but NativeBroker.this avoids an owner change
                new DOMTransaction(NativeBroker.this, domDb, () -> lockManager.acquireBtreeWriteLock(domDb.getLockName())) {
                    @Override
                    public Object start() throws ReadOnlyException {
                        try {
                            domDb.addValue(transaction, new NodeRef(doc.getDocId(), node.getNodeId()), address);
                        } catch(final BTreeException | IOException e) {
                            LOG.error(EXCEPTION_DURING_REINDEX, e);
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
                if (type == DocumentImpl.BINARY_FILE) {
                    doc = BinaryDocument.read(pool, is);
                } else {
                    doc = DocumentImpl.read(pool, is);
                }

                collectionInternalAccess.addDocument(doc);
            } catch(final EXistException | IOException e) {
                LOG.error("Exception while reading document data", e);
            }

            return true;
        }
    }
}
