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
import com.ibm.icu.text.Collator;
import net.jcip.annotations.GuardedBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.TypedQNameComparator;
import org.exist.dom.persistent.*;
import org.exist.dom.QName;
import org.exist.collections.Collection;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexUtils;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.util.*;

import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maintains an index on typed node values (optionally by QName).
 *
 * Algorithm:
 * When a node is stored, an entry is added or updated in the {@link #pendingGeneric} and/or {@link #pendingQName}
 * maps, with either {@link SimpleValue#SimpleValue(int, Indexable)} or
 * {@link QNameValue#QNameValue(int, QName, Indexable, SymbolTable)} respectively as the key.
 * This way, the index entries are easily put in the persistent BFile storage by {@link #flush()}.
 *
 *
 * There are two types of key/value pairs stored into the Value Index:
 *
 * 1) SimpleValue, which represents the classic path based range index:
 *  key =&gt; [indexType, collectionId, atomicValue]
 *  value =&gt; [documentNodes+]
 *
 * 2) QNameValue, which represents the qname based reange index:
 *  key =&gt; [indexType, collectionId, qNameType, nsSymbolId, localPartSymbolId, atomicValue]
 *  Value =&gt; [documentNodes+]
 *
 *
 * indexType - 0x0 = Generic, 0x1 = QName
 *   Generic type is used with ValueSimpleIdx and QName is used with ValueQNameIdx
 *
 * collectionId: 4 bytes i.e. int
 *
 * atomicValue: [valueType, value]
 * valueType: 1 byte (the XQuery value type defined in {@link org.exist.xquery.value.Type}
 * value: n bytes, fixed length encoding of the value of the atomic value
 *
 * qNameType: 0x0 = {@link org.exist.storage.ElementValue#ELEMENT} 0x1 = {@link org.exist.storage.ElementValue#ATTRIBUTE}
 *
 * nsSymbolId: 2 byte short, The id from the Symbol Table
 * localPartSymbolId: 2 byte short, The id from the Symbol Table
 *
 * documentNodes: [docId, nodeIdCount, nodeIdsLength, nodeIdDelta+]
 *
 * docId: variable width encoded integer, the id of the document
 * nodeIdCount: variable width encoded integer, The number of following nodeIds
 *
 * nodeIdsLength: 4 bytes, i.e. int, The number of following bytes that hold the nodeIds
 *
 * nodeIdDelta: [deltaOffset, units, nodeIdDeltaData]
 * deltaOffset: 1 byte, Number of bits this DLN is offset from the previous DLN
 * units: variable with encoded short, The number of units of this DLN
 * nodeIdDeltaData: byte[], The delta bits of this DLN from `deltaOffset` of the previous DLN
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class NativeValueIndex implements ContentLoadingObserver {

    private final static Logger LOG = LogManager.getLogger(NativeValueIndex.class);

    public static final String FILE_NAME = "values.dbx";
    public static final short FILE_FORMAT_VERSION_ID = 14;
    public static final String FILE_KEY_IN_CONFIG = "db-connection.values";

    private static final double DEFAULT_VALUE_CACHE_GROWTH = 1.25;
    private static final double DEFAULT_VALUE_VALUE_THRESHOLD = 0.04;

    private static final int LENGTH_VALUE_TYPE = 1; //sizeof byte
    private static final int LENGTH_NODE_IDS = 4; //sizeof int

    public static final int OFFSET_COLLECTION_ID = 0;
    public static final int OFFSET_VALUE_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2
    public static final int OFFSET_DATA = OFFSET_VALUE_TYPE + NativeValueIndex.LENGTH_VALUE_TYPE; //3

    public final static String INDEX_CASE_SENSITIVE_ATTRIBUTE = "caseSensitive";
    public final static String PROPERTY_INDEX_CASE_SENSITIVE = "indexer.case-sensitive";

    /**
     * The broker that is using this value index.
     */
    private final DBBroker broker;

    /**
     * The data-store for this value index.
     */
    @GuardedBy("dbValues#getLock()") final BFile dbValues;
    private final Configuration config;

    /**
     * A collection of key-value pairs that pending modifications for this value index.
     * The keys are {@link org.exist.xquery.value.AtomicValue atomic values}
     * that implement {@link Indexable Indexable}.
     * The values are {@link org.exist.util.LongLinkedList lists} containing the nodes GIDs
     * (global identifiers).
     */
    private final PendingChanges<AtomicValue> pendingGeneric = new PendingChanges<>(IndexType.GENERIC);
    private final PendingChanges<QNameKey> pendingQName = new PendingChanges<>(IndexType.QNAME);

    private final LockManager lockManager;

    /**
     * The current document.
     */
    private DocumentImpl doc;

    /**
     * Work output Stream that should be cleared before every use.
     */
    private VariableByteOutputStream os = new VariableByteOutputStream();

    private final boolean caseSensitive;

    public NativeValueIndex(final DBBroker broker, final byte id, final Path dataDir, final Configuration config) throws DBException {
        this.broker = broker;
        this.lockManager = broker.getBrokerPool().getLockManager();
        this.config = config;
        final double cacheGrowth = NativeValueIndex.DEFAULT_VALUE_CACHE_GROWTH;
        final double cacheValueThresHold = NativeValueIndex.DEFAULT_VALUE_VALUE_THRESHOLD;
        BFile nativeFile = (BFile) config.getProperty(getConfigKeyForFile());
        if (nativeFile == null) {
            //use inheritance
            final Path file = dataDir.resolve(getFileName());
            LOG.debug("Creating '{}'...", FileUtils.fileName(file));
            nativeFile = new BFile(broker.getBrokerPool(), id, FILE_FORMAT_VERSION_ID, false, file,
                    broker.getBrokerPool().getCacheManager(), cacheGrowth,
                    cacheValueThresHold);
            config.setProperty(getConfigKeyForFile(), nativeFile);
        }
        this.dbValues = nativeFile;
        this.caseSensitive = Optional.ofNullable((Boolean) config.getProperty(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE)).orElse(false);

        broker.addContentLoadingObserver(getInstance());
    }

    private String getFileName() {
        return (FILE_NAME);
    }

    private String getConfigKeyForFile() {
        return (FILE_KEY_IN_CONFIG);
    }

    private NativeValueIndex getInstance() {
        return (this);
    }

    @Override
    public void setDocument(final DocumentImpl document) {
        final boolean documentChanged = (this.doc == null && document != null) || this.doc.getDocId() != document.getDocId();
        if((!pendingGeneric.changes.isEmpty() || !pendingQName.changes.isEmpty()) && documentChanged) {
            LOG.error("Document changed, but there were {} Generic and {} QName changes pending. These have been dropped!", pendingGeneric.changes.size(), pendingQName.changes.size());
            pendingGeneric.changes.clear();
            pendingQName.changes.clear();
        }
        this.doc = document;
    }

    /**
     * Store the given element's value in the value index.
     *
     * @param node      The element to add to the index
     * @param content   The string representation of the value
     * @param xpathType The value type
     * @param indexType The type of the index to place the element value in
     * @param remove    Whether the element should be removed from the index
     */
    public void storeElement(final ElementImpl node, final String content, final int xpathType, final IndexType indexType, final boolean remove) {
        if (doc.getDocId() != node.getOwnerDocument().getDocId()) {
            throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + node.getOwnerDocument().getDocId() + "') differ !");
        }

        final AtomicValue atomic = convertToAtomic(xpathType, content);
        if (atomic == null) {
            //Ignore if the value can't be successfully atomized
            //(this is logged elsewhere)
            return;
        }

        switch (indexType) {
            case GENERIC:
                store(pendingGeneric, atomic, node.getNodeId(), remove);
                break;

            case QNAME:
                store(pendingQName, new QNameKey(node.getQName(), atomic), node.getNodeId(), remove);
                break;

            default:
                throw new IllegalArgumentException();
        }
    }

    private <T> void store(final PendingChanges<T> pending, final T key, final NodeId value, final boolean remove) {
        if (!remove) {
            final List<NodeId> buf;
            //Is this indexable value already pending ?
            if (pending.changes.containsKey(key)) {
                buf = pending.changes.get(key);
            } else {
                //Create a NodeId list
                buf = new ArrayList<>(8);
                pending.changes.put(key, buf);
            }
            //Add node's NodeId to the list
            buf.add(value);
        } else {
            if (!pending.changes.containsKey(key)) {
                pending.changes.put(key, null);
            }
        }
    }

    @Override
    public void storeAttribute(final AttrImpl attr, final NodePath currentPath, final RangeIndexSpec spec, final boolean remove) {
        storeAttribute(attr, attr.getValue(), spec.getType(), (spec.getQName() == null) ? IndexType.GENERIC : IndexType.QNAME, remove);
    }

    public void storeAttribute(final AttrImpl attr, final String value, final int xpathType, final IndexType indexType, final boolean remove) {
        if (doc != null && doc.getDocId() != attr.getOwnerDocument().getDocId()) {
            throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + attr.getOwnerDocument().getDocId() + "') differ !");
        }

        final AtomicValue atomic = convertToAtomic(xpathType, value);
        if (atomic == null) {
            //Ignore if the value can't be successfully atomized
            //(this is logged elsewhere)
            return;
        }

        switch(indexType) {
            case GENERIC:
                store(pendingGeneric, atomic, attr.getNodeId(), remove);
                break;

            case QNAME:
                store(pendingQName, new QNameKey(attr.getQName(), atomic), attr.getNodeId(), remove);
                break;

            default:
                throw new IllegalArgumentException();
        }
    }

    public <T extends IStoredNode> IStoredNode getReindexRoot(final IStoredNode<T> node, final NodePath nodePath) {
        this.doc = node.getOwnerDocument();
        final NodePath path = new NodePath(nodePath);
        IStoredNode root = null;
        IStoredNode currentNode = (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.ATTRIBUTE_NODE) ? node : node.getParentStoredNode();

        while (currentNode != null) {
            final GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            final QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, currentNode.getQName());

            if ((rSpec != null) || (qSpec != null)) {
                root = currentNode;
            }

            if (doc.getCollection().isTempCollection() && (currentNode.getNodeId().getTreeLevel() == 2)) {
                break;
            }
            currentNode = currentNode.getParentStoredNode();
            path.removeLastComponent();
        }
        return root;
    }

    public void reindex(final IStoredNode node) {
        if (node == null) {
            return;
        }
        final StreamListener listener = new ValueIndexStreamListener();
        IndexUtils.scanNode(broker, null, node, listener);
    }

    @Override
    public void storeText(final TextImpl node, final NodePath currentPath) {
        //no-op
    }

    @Override
    public void removeNode(final NodeHandle node, final NodePath currentPath, final String content) {
        //no-op
    }

    @Override
    public void sync() {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {
            dbValues.flush();
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
            //TODO : throw an exception ? -pb
        } catch (final DBException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        }
    }

    @Override
    public void flush() {
        if (doc == null || (pendingGeneric.changes.isEmpty() && pendingQName.changes.isEmpty())) {
            return;
        }
        final int collectionId = this.doc.getCollection().getId();

        flush(pendingGeneric, key -> new SimpleValue(collectionId, (Indexable) key));
        flush(pendingQName, key -> new QNameValue(collectionId, key.qname, key.value, broker.getBrokerPool().getSymbols()));
    }

    private <T> void flush(final PendingChanges<T> pending, final FunctionE<T, Value, EXistException> dbKeyFn) {
        final VariableByteOutputStream nodeIdOs = new VariableByteOutputStream();

        for (final Map.Entry<T, List<NodeId>> entry : pending.changes.entrySet()) {
            final T key = entry.getKey();

            final List<NodeId> gids = entry.getValue();
            final int gidsCount = gids.size();

            //Don't forget this one
            FastQSort.sort(gids, 0, gidsCount - 1);
            os.clear();
            os.writeInt(this.doc.getDocId());
            os.writeInt(gidsCount);

            //Compute the GID list
            try {
                NodeId previous = null;
                for (final NodeId nodeId : gids) {
                    previous = nodeId.write(previous, nodeIdOs);
                }

                final byte[] nodeIdsData = nodeIdOs.toByteArray();

                // clear the buf for the next iteration
                nodeIdOs.clear();

                // Write length of node IDs (bytes)
                os.writeFixedInt(nodeIdsData.length);

                // write the node IDs
                os.write(nodeIdsData);

            } catch (final IOException e) {
                LOG.warn("IO error while writing range index: {}", e.getMessage(), e);
                //TODO : throw exception?
            }

            try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {
                final Value v = dbKeyFn.apply(key);

                if (dbValues.append(v, os.data()) == BFile.UNKNOWN_ADDRESS) {
                    LOG.warn("Could not append index data for key '{}'", key);
                    //TODO : throw exception ?
                }
            } catch (final EXistException | IOException e) {
                LOG.error(e.getMessage(), e);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                //TODO : return ?
            } catch (final ReadOnlyException e) {
                LOG.warn(e.getMessage(), e);

                //Return without clearing the pending entries
                return;
            } finally {
                os.clear();
            }
        }
        pending.changes.clear();
    }

    @Override
    public void remove() {
        if (doc == null || (pendingGeneric.changes.isEmpty() && pendingQName.changes.isEmpty())) {
            return;
        }

        final int collectionId = this.doc.getCollection().getId();

        remove(pendingGeneric, key -> new SimpleValue(collectionId, (Indexable) key));
        remove(pendingQName, key -> new QNameValue(collectionId, key.qname, key.value, broker.getBrokerPool().getSymbols()));
    }

    private <T> void remove(final PendingChanges<T> pending, final FunctionE<T, Value, EXistException> dbKeyFn) {
        final VariableByteOutputStream nodeIdOs = new VariableByteOutputStream();
        for (final Map.Entry<T, List<NodeId>> entry : pending.changes.entrySet()) {
            final T key = entry.getKey();
            final List<NodeId> storedGIDList = entry.getValue();
            final List<NodeId> newGIDList = new ArrayList<>();
            os.clear();

            try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {

                //Compute a key for the value
                final Value searchKey = dbKeyFn.apply(key);
                final Value value = dbValues.get(searchKey);

                //Does the value already has data in the index ?
                if (value != null) {

                    //Add its data to the new list
                    final VariableByteArrayInput is = new VariableByteArrayInput(value.getData());

                    while (is.available() > 0) {
                        final int storedDocId = is.readInt();
                        final int gidsCount = is.readInt();
                        final int size = is.readFixedInt();

                        if (storedDocId != this.doc.getDocId()) {

                            // data are related to another document:
                            // append them to any existing data
                            os.writeInt(storedDocId);
                            os.writeInt(gidsCount);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
                        } else {

                            // data are related to our document:
                            // feed the new list with the GIDs
                            NodeId previous = null;

                            for (int j = 0; j < gidsCount; j++) {
                                final NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                previous = nodeId;

                                // add the node to the new list if it is not
                                // in the list of removed nodes
                                if (!containsNode(storedGIDList, nodeId)) {
                                    newGIDList.add(nodeId);
                                }
                            }
                        }
                    }

                    //append the data from the new list
                    if (newGIDList.size() > 0) {
                        final int gidsCount = newGIDList.size();

                        //Don't forget this one
                        FastQSort.sort(newGIDList, 0, gidsCount - 1);
                        os.writeInt(this.doc.getDocId());
                        os.writeInt(gidsCount);

                        //Compute the new GID list
                        try {
                            NodeId previous = null;
                            for (final NodeId nodeId : newGIDList) {
                                previous = nodeId.write(previous, nodeIdOs);
                            }

                            final byte[] nodeIdsData = nodeIdOs.toByteArray();

                            // clear the buf for the next iteration
                            nodeIdOs.clear();

                            // Write length of node IDs (bytes)
                            os.writeFixedInt(nodeIdsData.length);

                            // write the node IDs
                            os.write(nodeIdsData);
                        } catch (final IOException e) {
                            LOG.warn("IO error while writing range index: {}", e.getMessage(), e);
                            //TODO : throw exception?
                        }
                    }

//                        if(os.data().size() == 0)
//                            dbValues.remove(value);
                    if (dbValues.update(value.getAddress(), searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not update index data for value '{}'", searchKey);
                        //TODO: throw exception ?
                    }
                } else {

                    if (dbValues.put(searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for value '{}'", searchKey);
                        //TODO : throw exception ?
                    }
                }
            } catch (final EXistException | IOException e) {
                LOG.error(e.getMessage(), e);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                //TODO : return ?
            } finally {
                os.clear();
            }
        }
        pending.changes.clear();
    }

    private static boolean containsNode(final List<NodeId> list, final NodeId nodeId) {
        return list.stream().anyMatch(nodeId::equals);
    }

    @Override
    public void dropIndex(final Collection collection) {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {

            flush();

            // remove generic index
            Value ref = new SimpleValue(collection.getId());
            dbValues.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, ref));

            // remove QName index
            ref = new QNameValue(collection.getId());
            dbValues.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, ref));
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
        } catch (final BTreeException | IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public void dropIndex(final DocumentImpl document) {
        final int collectionId = document.getCollection().getId();
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {
            dropIndex(document.getDocId(), pendingGeneric, key -> new SimpleValue(collectionId, (Indexable) key));
            dropIndex(document.getDocId(), pendingQName, key -> new QNameValue(collectionId, key.qname, key.value, broker.getBrokerPool().getSymbols()));
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
        } catch (final EXistException e) {
            LOG.warn("Exception while removing range index: {}", e.getMessage(), e);
        } finally {
            os.clear();
        }
    }

    private <T> void dropIndex(final int docId, final PendingChanges<T> pending, final FunctionE<T, Value, EXistException> dbKeyFn) throws EXistException, IOException {
        for (final Map.Entry<T, List<NodeId>> entry : pending.changes.entrySet()) {
            final T key = entry.getKey();

            //Compute a key for the indexed value in the collection
            final Value v = dbKeyFn.apply(key);
            final Value value = dbValues.get(v);

            if (value == null) {
                continue;
            }

            final VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
            boolean changed = false;
            os.clear();

            while (is.available() > 0) {
                final int storedDocId = is.readInt();
                final int gidsCount = is.readInt();
                final int size = is.readFixedInt();

                if (storedDocId != docId) {

                    // data are related to another document:
                    // copy them (keep them)
                    os.writeInt(storedDocId);
                    os.writeInt(gidsCount);
                    os.writeFixedInt(size);
                    is.copyRaw(os, size);
                } else {

                    // data are related to our document:
                    // skip them (remove them)
                    is.skipBytes(size);
                    changed = true;
                }
            }

            //Store new data, if relevant
            if (changed) {

                if (os.data().size() == 0) {

                    // nothing to store:
                    // remove the existing key/value pair
                    dbValues.remove(v);
                } else {

                    // still something to store:
                    // modify the existing value for the key
                    if (dbValues.put(v, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for key '{}'", v);
                        //TODO : throw exception ?
                    }
                }
            }
        }
        pending.changes.clear();
    }

    public NodeSet find(final XQueryWatchDog watchDog, final Comparison comparison, final DocumentSet docs, final NodeSet contextSet, final int axis, final QName qname, final Indexable value) throws TerminatedException {
        return find(watchDog, comparison, docs, contextSet, axis, qname, value, false);
    }

    public NodeSet find(final XQueryWatchDog watchDog, final Comparison comparison, final DocumentSet docs, final NodeSet contextSet, final int axis, final QName qname, final Indexable value, final boolean mixedIndex) throws TerminatedException {
        final NodeSet result = new NewArrayNodeSet();

        if (qname == null) {
            findAll(watchDog, comparison, docs, contextSet, axis, null, value, result);
        } else {
            final List<QName> qnames = Collections.singletonList(qname);
            findAll(watchDog, comparison, docs, contextSet, axis, qnames, value, result);
            if (mixedIndex) {
                findAll(watchDog, comparison, docs, contextSet, axis, null, value, result);
            }
        }
        return result;
    }

    public NodeSet findAll(final XQueryWatchDog watchDog, final Comparison comparison, final DocumentSet docs, final NodeSet contextSet, final int axis, final Indexable value) throws TerminatedException {
        final NodeSet result = new NewArrayNodeSet();
        findAll(watchDog, comparison, docs, contextSet, axis, getDefinedIndexes(docs), value, result);
        findAll(watchDog, comparison, docs, contextSet, axis, null, value, result);
        return result;
    }

    /**
     * find.
     *
     * @param comparison The type of comparison the search is performing
     * @param docs       The documents to search for matches within
     * @param contextSet DOCUMENT ME!
     * @param axis       DOCUMENT ME!
     * @param qnames     DOCUMENT ME!
     * @param value      right hand comparison value
     * @param result     DOCUMENT ME!
     * @return DOCUMENT ME!
     * @throws TerminatedException DOCUMENT ME!
     */
    private NodeSet findAll(final XQueryWatchDog watchDog, final Comparison comparison, final DocumentSet docs, final NodeSet contextSet, final int axis, final List<QName> qnames, final Indexable value, final NodeSet result) throws TerminatedException {
        final SearchCallback cb = new SearchCallback(docs, contextSet, result, axis == NodeSet.ANCESTOR);

        final int idxOp = toIndexQueryOp(comparison);

        for (final Iterator<Collection> iter = docs.getCollectionIterator(); iter.hasNext(); ) {
            final int collectionId = iter.next().getId();

            watchDog.proceed(null);

            if (qnames == null) {
                try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {
                    final Value searchKey = new SimpleValue(collectionId, value);
                    final IndexQuery query = new IndexQuery(idxOp, searchKey);

                    if (idxOp == IndexQuery.EQ) {
                        dbValues.query(query, cb);
                    } else {
                        final Value prefixKey = new SimplePrefixValue(collectionId, value.getType());
                        dbValues.query(query, prefixKey, cb);
                    }
                } catch (final EXistException | BTreeException | IOException e) {
                    LOG.error(e.getMessage(), e);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                }
            } else {
                for (final QName qname : qnames) {
                    try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {

                        //Compute a key for the value in the collection
                        final Value searchKey = new QNameValue(collectionId, qname, value, broker.getBrokerPool().getSymbols());

                        final IndexQuery query = new IndexQuery(idxOp, searchKey);
                        if (idxOp == IndexQuery.EQ) {
                            dbValues.query(query, cb);
                        } else {
                            final Value prefixKey = new QNamePrefixValue(collectionId, qname, value.getType(), broker.getBrokerPool().getSymbols());
                            dbValues.query(query, prefixKey, cb);
                        }
                    } catch (final EXistException | BTreeException | IOException e) {
                        LOG.error(e.getMessage(), e);
                    } catch (final LockException e) {
                        LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                    }
                }
            }
        }
        return result;
    }

    public NodeSet match(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final QName qname, final int type) throws TerminatedException, EXistException {
        return match(watchDog, docs, contextSet, axis, expr, qname, type, null, StringTruncationOperator.RIGHT);
    }

    public NodeSet match(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final QName qname, final int type, final Collator collator, final StringTruncationOperator truncation) throws TerminatedException, EXistException {
        return match(watchDog, docs, contextSet, axis, expr, qname, type, 0, true, collator, truncation);
    }

    public NodeSet match(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final QName qname, final int type, final int flags, final boolean caseSensitiveQuery) throws TerminatedException, EXistException {
        return match(watchDog, docs, contextSet, axis, expr, qname, type, flags, caseSensitiveQuery, null, StringTruncationOperator.RIGHT);
    }

    public NodeSet match(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final QName qname, final int type, final int flags, final boolean caseSensitiveQuery, final Collator collator, final StringTruncationOperator truncation) throws TerminatedException, EXistException {
        final NodeSet result = new NewArrayNodeSet();
        if (qname == null) {
            matchAll(watchDog, docs, contextSet, axis, expr, null, type, flags, caseSensitiveQuery, result, collator, truncation);
        } else {
            final List<QName> qnames = Collections.singletonList(qname);
            matchAll(watchDog, docs, contextSet, axis, expr, qnames, type, flags, caseSensitiveQuery, result, collator, truncation);
        }
        return result;
    }

    public NodeSet matchAll(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final int type, final int flags, final boolean caseSensitiveQuery) throws TerminatedException, EXistException {
        return matchAll(watchDog, docs, contextSet, axis, expr, type, flags, caseSensitiveQuery, null, StringTruncationOperator.RIGHT);
    }

    public NodeSet matchAll(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final int type, final int flags, final boolean caseSensitiveQuery, final Collator collator, final StringTruncationOperator truncation) throws TerminatedException, EXistException {
        final NodeSet result = new NewArrayNodeSet();
        matchAll(watchDog, docs, contextSet, axis, expr, getDefinedIndexes(docs), type, flags, caseSensitiveQuery, result, collator, truncation);
        matchAll(watchDog, docs, contextSet, axis, expr, null, type, flags, caseSensitiveQuery, result, collator, truncation);
        return result;
    }

    /**
     * Regular expression search.
     *
     * @param docs               DOCUMENT ME!
     * @param contextSet         DOCUMENT ME!
     * @param axis               DOCUMENT ME!
     * @param expr               DOCUMENT ME!
     * @param qnames             DOCUMENT ME!
     * @param type               like type argument for {@link org.exist.storage.RegexMatcher} constructor
     * @param flags              like flags argument for {@link org.exist.storage.RegexMatcher} constructor
     * @param caseSensitiveQuery DOCUMENT ME!
     * @param result             DOCUMENT ME!
     * @param collator           DOCUMENT ME!
     * @param truncation         The type of string truncation to apply
     * @param watchDog  the watchdog
     * @return DOCUMENT ME!
     * @throws TerminatedException DOCUMENT ME!
     * @throws EXistException      DOCUMENT ME!
     */
    public NodeSet matchAll(final XQueryWatchDog watchDog, final DocumentSet docs, final NodeSet contextSet, final int axis, final String expr, final List<QName> qnames, final int type, final int flags, final boolean caseSensitiveQuery, final NodeSet result, final Collator collator, final StringTruncationOperator truncation) throws TerminatedException, EXistException {
        // if the match expression starts with a char sequence, we restrict the index scan to entries starting with
        // the same sequence. Otherwise, we have to scan the whole index.

        final StringValue startTerm;

        if (type == DBBroker.MATCH_REGEXP && expr.startsWith("^") && caseSensitiveQuery == caseSensitive) {
            final StringBuilder term = new StringBuilder();
            for (int j = 1; j < expr.length(); j++) {
                if (Character.isLetterOrDigit(expr.charAt(j))) {
                    term.append(expr.charAt(j));
                } else {
                    break;
                }
            }

            if (term.length() > 0) {
                startTerm = new StringValue(term.toString());
                LOG.debug("Match will begin index scan at '{}'", startTerm);
            } else {
                startTerm = null;
            }
        } else if (collator == null && (type == DBBroker.MATCH_EXACT || type == DBBroker.MATCH_STARTSWITH)) {
            startTerm = new StringValue(expr);
            LOG.debug("Match will begin index scan at '{}'", startTerm);
        } else {
            startTerm = null;
        }

        // Select appropriate matcher/comparator
        final TermMatcher matcher;
        if (collator == null) {
            matcher = switch (type) {
                case DBBroker.MATCH_EXACT -> new ExactMatcher(expr);
                case DBBroker.MATCH_CONTAINS -> new ContainsMatcher(expr);
                case DBBroker.MATCH_STARTSWITH -> new StartsWithMatcher(expr);
                case DBBroker.MATCH_ENDSWITH -> new EndsWithMatcher(expr);
                default -> new RegexMatcher(expr, flags);
            };
        } else {
            matcher = new CollatorMatcher(expr, truncation, collator);
        }

        final MatcherCallback cb = new MatcherCallback(docs, contextSet, result, matcher, axis == NodeSet.ANCESTOR);

        for (final Iterator<Collection> iter = docs.getCollectionIterator(); iter.hasNext(); ) {
            final int collectionId = iter.next().getId();

            watchDog.proceed(null);
            if (qnames == null) {
                try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {

                    final Value searchKey;
                    if (startTerm != null) {
                        //Compute a key for the start term in the collection
                        searchKey = new SimpleValue(collectionId, startTerm);
                    } else {
                        //Compute a key for an arbitrary string in the collection
                        searchKey = new SimplePrefixValue(collectionId, Type.STRING);
                    }
                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, searchKey);
                    dbValues.query(query, cb);
                } catch (final IOException | BTreeException e) {
                    LOG.error(e.getMessage(), e);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                }
            } else {
                for (final QName qname : qnames) {
                    try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {

                        final Value searchKey;
                        if (startTerm != null) {
                            searchKey = new QNameValue(collectionId, qname, startTerm, broker.getBrokerPool().getSymbols());
                        } else {
                            LOG.debug("Searching with QName prefix");
                            searchKey = new QNamePrefixValue(collectionId, qname, Type.STRING, broker.getBrokerPool().getSymbols());
                        }
                        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, searchKey);
                        dbValues.query(query, cb);
                    } catch (final IOException | BTreeException e) {
                        LOG.error(e.getMessage(), e);
                    } catch (final LockException e) {
                        LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                    }
                }
            }
        }
        return result;
    }

    public ValueOccurrences[] scanIndexKeys(final DocumentSet docs, final NodeSet contextSet, final Indexable start) {
        final int type = start.getType();
        final boolean stringType = Type.subTypeOf(type, Type.STRING);
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, type, false);

        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {

            try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {
                final Collection c = i.next();
                final int collectionId = c.getId();

                //Compute a key for the start value in the collection
                final Value startKey = new SimpleValue(collectionId, start);
                if (stringType) {
                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
                    dbValues.query(query, cb);
                } else {
                    final Value prefixKey = new SimplePrefixValue(collectionId, start.getType());
                    final IndexQuery query = new IndexQuery(IndexQuery.GEQ, startKey);
                    dbValues.query(query, prefixKey, cb);
                }
            } catch (final EXistException | IOException | TerminatedException | BTreeException e) {
                LOG.error(e.getMessage(), e);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
            }
        }
        final Map<AtomicValue, ValueOccurrences> map = cb.map;
        final ValueOccurrences[] result = new ValueOccurrences[map.size()];
        return map.values().toArray(result);
    }

    /**
     * Scan all index keys indexed by the given QName. Return {@link org.exist.util.ValueOccurrences} for those index entries pointing to descendants
     * of the specified context set. The first argument specifies the set of documents to include in the scan. Nodes which are not in this document
     * set will be ignored.
     *
     * @param docs       set of documents to scan
     * @param contextSet if != null, return only index entries pointing to nodes which are descendants of nodes in the context set
     * @param qnames     an array of QNames: defines the index entries to be scanned.
     * @param start      an optional start value: only index keys starting with or being greater than this start value (depends on the type of the
     *                   index key) will be scanned
     * @return a list of ValueOccurrences
     */
    public ValueOccurrences[] scanIndexKeys(final DocumentSet docs, final NodeSet contextSet, QName[] qnames, final Indexable start) {
        if (qnames == null) {
            final List<QName> qnlist = getDefinedIndexes(docs);
            qnames = new QName[qnlist.size()];
            qnames = qnlist.toArray(qnames);
        }
        final int type = start.getType();
        final boolean stringType = Type.subTypeOf(type, Type.STRING);
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, type, true);

        for (final QName qname : qnames) {

            for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
                try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(dbValues.getLockName())) {
                    final int collectionId = i.next().getId();

                    //Compute a key for the start value in the collection
                    final Value startKey = new QNameValue(collectionId, qname, start, broker.getBrokerPool().getSymbols());
                    if (stringType) {
                        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
                        dbValues.query(query, cb);
                    } else {
                        final Value prefixKey = new QNamePrefixValue(collectionId, qname, start.getType(), broker.getBrokerPool().getSymbols());
                        final IndexQuery query = new IndexQuery(IndexQuery.GEQ, startKey);
                        dbValues.query(query, prefixKey, cb);
                    }
                } catch (final EXistException | BTreeException | IOException | TerminatedException e) {
                    LOG.error(e.getMessage(), e);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
                }
            }
        }
        final Map<AtomicValue, ValueOccurrences> map = cb.map;
        final ValueOccurrences[] result = new ValueOccurrences[map.size()];
        return map.values().toArray(result);
    }

    private List<QName> getDefinedIndexes(final DocumentSet docs) {
        final List<QName> qnames = new ArrayList<>();

        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            final IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                qnames.addAll(idxConf.getIndexedQNames());
            }
        }
        return qnames;
    }

    private int toIndexQueryOp(final Comparison comparison) {
        final int indexOp = switch (comparison) {
            case LT -> IndexQuery.LT;
            case LTEQ -> IndexQuery.LEQ;
            case GT -> IndexQuery.GT;
            case GTEQ -> IndexQuery.GEQ;
            case NEQ -> IndexQuery.NEQ;
            default -> IndexQuery.EQ;
        };

        return indexOp;
    }


    /**
     * Converts a Value to an AtomicValue
     *
     * @param xpathType The type to convert the value to
     * @param value     The value to atomize
     * @return The converted value, or <code>null</null> if atomization failed or if the atomic value is not indexable
     */
    static @Nullable AtomicValue convertToAtomic(final int xpathType, @Nullable final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        final AtomicValue atomic;
        if (Type.subTypeOf(xpathType, Type.STRING)) {
            try {
                atomic = new StringValue(value, xpathType, false);
            } catch (final XPathException e) {
                LOG.error(e);
                return null;
            }
        } else {
            try {
                atomic = new StringValue(value).convertTo(xpathType);
            } catch (final XPathException e) {
                LOG.warn("Node value '{}' cannot be converted to {}", value, Type.getTypeName(xpathType));
                return null;
            }
        }

        return atomic;
    }

    @Override
    public void closeAndRemove() throws DBException {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {
            config.setProperty(getConfigKeyForFile(), null);
            dbValues.closeAndRemove();
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
        }
    }

    @Override
    public void close() throws DBException {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(dbValues.getLockName())) {
            config.setProperty(getConfigKeyForFile(), null);
            dbValues.close();
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(dbValues.getFile()), e);
        }
    }

    @Override
    public void printStatistics() {
        dbValues.printStatistics();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " at " + FileUtils.fileName(dbValues.getFile()) + " owned by " + broker.toString() + " (case sensitive = " + caseSensitive + ")";
    }

    //***************************************************************************
    //*
    //*     Private Matcher Classes
    //*
    //***************************************************************************/

    private final static class ExactMatcher implements TermMatcher {
        private final String expr;

        ExactMatcher(final String expr) throws EXistException {
            this.expr = expr;
        }

        @Override
        public boolean matches(final CharSequence term) {
            return term != null && term.toString().equals(expr);
        }
    }


    private final static class ContainsMatcher implements TermMatcher {
        private final String expr;

        ContainsMatcher(final String expr) throws EXistException {
            this.expr = expr;
        }

        @Override
        public boolean matches(final CharSequence term) {
            return term != null && term.toString().contains(expr);
        }
    }


    private final static class StartsWithMatcher implements TermMatcher {
        private final String expr;

        StartsWithMatcher(final String expr) throws EXistException {
            this.expr = expr;
        }

        @Override
        public boolean matches(final CharSequence term) {
            return term != null && term.toString().startsWith(expr);
        }
    }

    private final static class EndsWithMatcher implements TermMatcher {
        private final String expr;

        EndsWithMatcher(final String expr) throws EXistException {
            this.expr = expr;
        }

        @Override
        public boolean matches(final CharSequence term) {
            return term != null && term.toString().endsWith(expr);
        }
    }

    private final static class CollatorMatcher implements TermMatcher {
        private final String expr;
        private final StringTruncationOperator truncation;
        private final Collator collator;

        CollatorMatcher(final String expr, final StringTruncationOperator truncation, final Collator collator) throws EXistException {
            if (collator == null) {
                throw new EXistException("Collator must be non-null");
            }

            this.expr = expr;
            this.truncation = truncation;
            this.collator = collator;
        }

        @Override
        public boolean matches(final CharSequence term) {
            final boolean matches = switch (truncation) {
                case LEFT -> Collations.endsWith(collator, term.toString(), expr);
                case RIGHT -> Collations.startsWith(collator, term.toString(), expr);
                case BOTH -> Collations.contains(collator, term.toString(), expr);
                default -> Collations.equals(collator, term.toString(), expr);
            };

            return matches;
        }
    }

    //***************************************************************************
    //*
    //*     Private Callback Classes
    //*
    //***************************************************************************/

    private class SearchCallback implements BTreeCallback {
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final NodeSet result;
        private final boolean returnAncestor;

        public SearchCallback(final DocumentSet docs, final NodeSet contextSet, final NodeSet result, boolean returnAncestor) {
            this.docs = docs;
            this.contextSet = contextSet;
            this.result = result;
            this.returnAncestor = returnAncestor;
        }

        @Override
        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException {
            final VariableByteInput is;
            try {
                is = dbValues.getAsStream(pointer);
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                return (true);
            }

            try {
                while (is.available() > 0) {
                    final int storedDocId = is.readInt();
                    final int gidsCount = is.readInt();
                    final int size = is.readFixedInt();
                    final DocumentImpl storedDocument = docs.getDoc(storedDocId);

                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;
                    }

                    //Process the nodes
                    NodeId previous = null;

                    for (int j = 0; j < gidsCount; j++) {
                        final NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        final NodeProxy storedNode = new NodeProxy(null, storedDocument, nodeId);

                        // if a context set is specified, we can directly check if the
                        // matching node is a descendant of one of the nodes
                        // in the context set.
                        if (contextSet != null) {
                            final int sizeHint = contextSet.getSizeHint(storedDocument);

                            if (returnAncestor) {
                                final NodeProxy parentNode = contextSet.get(storedNode);
                                if (parentNode != null) {
                                    result.add(parentNode, sizeHint);
                                }
                            } else {
                                result.add(storedNode, sizeHint);
                            }

                            // otherwise, we add all nodes without check
                        } else {
                            result.add(storedNode, Constants.NO_SIZE_HINT);
                        }
                    }
                }
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
            }

            return false;
        }
    }

    private final class MatcherCallback extends SearchCallback {
        private final TermMatcher matcher;
        private final XMLString key = new XMLString(128);

        MatcherCallback(final DocumentSet docs, final NodeSet contextSet, final NodeSet result, final TermMatcher matcher, final boolean returnAncestor) {
            super(docs, contextSet, result, returnAncestor);
            this.matcher = matcher;
        }

        @Override
        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException {
            final int offset;
            if (value.data()[value.start()] == IndexType.GENERIC.val) {
                offset = SimpleValue.OFFSET_VALUE + NativeValueIndex.LENGTH_VALUE_TYPE;
            } else {
                offset = QNameValue.OFFSET_VALUE + NativeValueIndex.LENGTH_VALUE_TYPE;
            }
            UTF8.decode(value.data(), value.start() + offset, value.getLength() - offset, key);

            if (matcher.matches(key)) {
                super.indexInfo(value, pointer);
            }

            key.reuse();

            return true;
        }
    }

    private final class IndexScanCallback implements BTreeCallback {
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final int type;
        private final boolean byQName;
        private final Map<AtomicValue, ValueOccurrences> map = new TreeMap<>();

        IndexScanCallback(final DocumentSet docs, final NodeSet contextSet, final int type, final boolean byQName) {
            this.docs = docs;
            this.contextSet = contextSet;
            this.type = type;
            this.byQName = byQName;
        }

        @Override
        public boolean indexInfo(final Value key, final long pointer) throws TerminatedException {
            final AtomicValue atomic;
            try {
                if (byQName) {
                    atomic = (AtomicValue) QNameValue.deserialize(key.data(), key.start(), key.getLength());
                } else {
                    atomic = (AtomicValue) SimpleValue.deserialize(key.data(), key.start(), key.getLength());
                }

                if (atomic.getType() != type) {
                    return false;
                }
            } catch (final EXistException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }

            final VariableByteInput is;
            try {
                is = dbValues.getAsStream(pointer);
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }

            ValueOccurrences oc = map.get(atomic);
            try {
                while (is.available() > 0) {
                    boolean docAdded = false;
                    final int storedDocId = is.readInt();
                    final int gidsCount = is.readInt();
                    final int size = is.readFixedInt();
                    final DocumentImpl storedDocument = docs.getDoc(storedDocId);

                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;
                    }
                    NodeId lastParentId = null;

                    NodeId previous = null;
                    for (int j = 0; j < gidsCount; j++) {
                        final NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;

                        final NodeProxy parentNode;
                        if (contextSet != null) {
                            parentNode = contextSet.get(storedDocument, nodeId);
                        } else {
                            parentNode = new NodeProxy(null, storedDocument, nodeId);
                        }

                        if (parentNode != null) {

                            if (oc == null) {
                                oc = new ValueOccurrences(atomic);
                                map.put(atomic, oc);
                            }

                            //Handle this very special case : /item[foo = "bar"] vs. /item[@foo = "bar"]
                            //Same value, same parent but different nodes !
                            //Not sure if we should track the contextSet's parentId... (just like we do)
                            //... or the way the contextSet is created (thus keeping track of the NodeTest)
                            if (lastParentId == null || !lastParentId.equals(parentNode.getNodeId())) {
                                oc.addOccurrences(1);
                            }

                            if (!docAdded) {
                                oc.addDocument(storedDocument);
                                docAdded = true;
                            }
                            lastParentId = parentNode.getNodeId();
                        }
                        //TODO : what if contextSet == null ? -pb
                        //See above where we have this behaviour :
                        //otherwise, we add all nodes without check
                    }
                }
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
            }

            return true;
        }
    }


    //***************************************************************************
    //*
    //*     Other Private Classes
    //*
    //***************************************************************************/

    private static class QNameKey implements Comparable<QNameKey> {
        private final static TypedQNameComparator comparator = new TypedQNameComparator();

        private final QName qname;
        private final AtomicValue value;

        public QNameKey(final QName qname, final AtomicValue atomic) {
            this.qname = qname;
            this.value = atomic;
        }

        @Override
        public int compareTo(final QNameKey other) {
            final int cmp = comparator.compare(qname, other.qname);

            if (cmp == 0) {
                return value.compareTo(other.value);
            } else {
                return cmp;
            }
        }
    }

    private final static class SimpleValue extends Value {
        static final int OFFSET_IDX_TYPE = 0;
        static final int LENGTH_IDX_TYPE = 1; //sizeof byte
        static final int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + LENGTH_IDX_TYPE; //1
        static final int OFFSET_VALUE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; // 3

        SimpleValue(final int collectionId) {
            len = LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
            data = new byte[len];
            data[OFFSET_IDX_TYPE] = IndexType.GENERIC.val;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            pos = OFFSET_IDX_TYPE;
        }

        SimpleValue(final int collectionId, final Indexable atomic) throws EXistException {
            data = atomic.serializeValue(OFFSET_VALUE);
            len = data.length;
            pos = OFFSET_IDX_TYPE;
            data[OFFSET_IDX_TYPE] = IndexType.GENERIC.val;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        }

        public static Indexable deserialize(final byte[] data, final int start, final int len) throws EXistException {
            return ValueIndexFactory.deserialize(data, start + OFFSET_VALUE, len - OFFSET_VALUE);
        }
    }

    private static class SimplePrefixValue extends Value {
        static final int LENGTH_VALUE_TYPE = 1; //sizeof byte

        SimplePrefixValue(final int collectionId, final int type) {
            len = SimpleValue.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID + LENGTH_VALUE_TYPE;
            data = new byte[len];
            data[SimpleValue.OFFSET_IDX_TYPE] = IndexType.GENERIC.val;
            ByteConversion.intToByte(collectionId, data, SimpleValue.OFFSET_COLLECTION_ID);
            data[SimpleValue.OFFSET_VALUE] = (byte) type;  // NOTE(AR) the XDM type from org.exist.xquery.value.Type will always fit within a single byte
            pos = SimpleValue.OFFSET_IDX_TYPE;
        }
    }

    private static class QNameValue extends Value {
        static final int LENGTH_IDX_TYPE = 1; //sizeof byte
        static final int LENGTH_QNAME_TYPE = 1; //sizeof byte

        static final int OFFSET_IDX_TYPE = 0;
        static final int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + LENGTH_IDX_TYPE; //1
        static final int OFFSET_QNAME_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //3
        static final int OFFSET_NS_URI = OFFSET_QNAME_TYPE + LENGTH_QNAME_TYPE; //4
        static final int OFFSET_LOCAL_NAME = OFFSET_NS_URI + SymbolTable.LENGTH_NS_URI; //6
        static final int OFFSET_VALUE = OFFSET_LOCAL_NAME + SymbolTable.LENGTH_LOCAL_NAME; //8

        public QNameValue(final int collectionId) {
            len = LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
            data = new byte[len];
            data[OFFSET_IDX_TYPE] = IndexType.QNAME.val;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            pos = OFFSET_IDX_TYPE;
        }

        public QNameValue(final int collectionId, final QName qname, final Indexable atomic, final SymbolTable symbols) throws EXistException {
            data = atomic.serializeValue(OFFSET_VALUE);
            len = data.length;
            pos = OFFSET_IDX_TYPE;
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalPart());
            data[OFFSET_IDX_TYPE] = IndexType.QNAME.val;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            data[OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
            ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);
        }

        public static Indexable deserialize(final byte[] data, final int start, final int len) throws EXistException {
            return ValueIndexFactory.deserialize(data, start + OFFSET_VALUE, len - OFFSET_VALUE);
        }

        public static byte getType(final byte[] data, final int start) {
            return data[start + OFFSET_QNAME_TYPE];
        }
    }

    private static class QNamePrefixValue extends Value {
        static final int LENGTH_VALUE_TYPE = 1; //sizeof byte

        QNamePrefixValue(final int collectionId, final QName qname, final int type, final SymbolTable symbols) {
            len = QNameValue.OFFSET_VALUE + LENGTH_VALUE_TYPE;
            data = new byte[len];
            data[QNameValue.OFFSET_IDX_TYPE] = IndexType.QNAME.val;
            ByteConversion.intToByte(collectionId, data, QNameValue.OFFSET_COLLECTION_ID);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalPart());
            data[QNameValue.OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, QNameValue.OFFSET_NS_URI);
            ByteConversion.shortToByte(localNameId, data, QNameValue.OFFSET_LOCAL_NAME);
            data[QNameValue.OFFSET_VALUE] = (byte) type;  // NOTE(AR) the XDM type from org.exist.xquery.value.Type will always fit within a single byte
            pos = QNameValue.OFFSET_IDX_TYPE;
        }
    }

    private class ValueIndexStreamListener extends AbstractStreamListener {
        private Deque<XMLString> contentStack = null;

        ValueIndexStreamListener() {
            super();
        }

        @Override
        public void startElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            final GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            final QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, element.getQName());

            if (rSpec != null || qSpec != null) {
                if (contentStack == null) {
                    contentStack = new ArrayDeque<>();
                }
                final XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        @Override
        public void attribute(final Txn transaction, final AttrImpl attrib, final NodePath path) {
            final GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            final QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, attrib.getQName());

            if (rSpec != null) {
                storeAttribute(attrib, path, rSpec, false);
            }

            if (qSpec != null) {
                storeAttribute(attrib, path, qSpec, false);
            }

            switch (attrib.getType()) {
                case AttrImpl.ID:
                    storeAttribute(attrib, attrib.getValue(), Type.ID, IndexType.GENERIC, false);
                    break;

                case AttrImpl.IDREF:
                    storeAttribute(attrib, attrib.getValue(), Type.IDREF, IndexType.GENERIC, false);
                    break;

                case AttrImpl.IDREFS:
                    final StringTokenizer tokenizer = new StringTokenizer(attrib.getValue(), " ");
                    while (tokenizer.hasMoreTokens()) {
                        storeAttribute(attrib, tokenizer.nextToken(), Type.IDREF, IndexType.GENERIC, false);
                    }
                    break;

                default:
                    // do nothing special
            }
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void endElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            final GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            final QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, element.getQName());

            if (rSpec != null || qSpec != null) {
                final XMLString content = contentStack.pop();

                if (rSpec != null) {
                    storeElement(element, content.toString(), RangeIndexSpec.indexTypeToXPath(rSpec.getIndexType()), IndexType.GENERIC, false);
                }

                if (qSpec != null) {
                    storeElement(element, content.toString(), RangeIndexSpec.indexTypeToXPath(qSpec.getIndexType()), IndexType.QNAME, false);
                }

                content.reset();
            }
            super.endElement(transaction, element, path);
        }

        @Override
        public void characters(final Txn transaction, final AbstractCharacterData text, final NodePath path) {
            final XMLString xmlString = text.getXMLString();
            if (contentStack != null) {
                contentStack.forEach(next -> next.append(xmlString));
            }
            super.characters(transaction, text, path);
        }

        @Override
        public IndexWorker getWorker() {
            return null;
        }
    }

    public enum IndexType {
        GENERIC((byte)0x0),
        QNAME((byte)0x1);
        final byte val;

        IndexType(final byte val) {
            this.val = val;
        }
    }

    private static class PendingChanges<K> {
        final IndexType indexType;
        final Map<K, List<NodeId>> changes = new TreeMap<>();

        PendingChanges(final IndexType indexType) {
            this.indexType = indexType;
        }
    }
}
