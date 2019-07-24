package org.exist.indexing.sort;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.*;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.*;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class SortIndexWorker implements IndexWorker {

    private ReindexMode mode = ReindexMode.STORE;
    private DocumentImpl document = null;
    private SortIndex index;
    private final LockManager lockManager;

    public SortIndexWorker(final SortIndex index) {
        this.index = index;
        this.lockManager = index.getBrokerPool().getLockManager();
    }

    public void setDocument(final DocumentImpl doc, final ReindexMode mode) {
        this.document = doc;
        this.mode = mode;
    }

    public String getIndexId() {
        return SortIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    @Override
    public QueryRewriter getQueryRewriter(final XQueryContext context) {
        return null;
    }

    @Override
    public void flush() {
        switch (mode) {
            case REMOVE_ALL_NODES:
                remove(document);
                break;
        }
    }

    /**
     * Create a new sort index identified by a name. The method iterates through all items in
     * the items list and adds the nodes to the index. It assumes that the list is already ordered.
     *
     * @param name  the name by which the index will be identified
     * @param items ordered list of items to store
     *
     * @throws EXistException if an error occurs with the database
     * @throws LockException if a locking error occurs
     */
    public void createIndex(final String name, final List<SortItem> items) throws EXistException, LockException {
        // get an id for the new index
        final short id = getOrRegisterId(name);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            long idx = 0;
            for (final SortItem item : items) {
                final byte[] key = computeKey(id, item.getNode());
                index.btree.addValue(new Value(key), idx++);
            }
        } catch (final LockException | IOException | BTreeException e) {
            throw new EXistException("Exception caught while creating sort index: " + e.getMessage(), e);
        }
    }

    public boolean hasIndex(final String name) throws EXistException, LockException {
        return getId(name) > 0;
    }

    /**
     * Looks up the given node in the specified index and returns its original position
     * in the ordered set as a long integer.
     *
     * @param name  the name of the index
     * @param proxy the node
     *
     * @return the original position of the node in the ordered set
     *
     * @throws EXistException if an error occurs with the database
     * @throws LockException if a locking error occurs
     */
    public long getIndex(final String name, final NodeProxy proxy) throws EXistException, LockException {
        final short id = getId(name);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeReadLock(index.btree.getLockName())) {
            final byte[] key = computeKey(id, proxy);
            return index.btree.findValue(new Value(key));
        } catch (final LockException | IOException | BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        }
    }

    /**
     * Completely remove the index identified by its name.
     *
     * @param name the name of the index
     *
     * @throws EXistException if an error occurs with the database
     * @throws LockException if a locking error occurs
     */
    public void remove(final String name) throws EXistException, LockException {
        final short id = getId(name);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            final byte[] fromKey = computeKey(id);
            final byte[] toKey = computeKey((short) (id + 1));
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            index.btree.remove(query, null);

            removeId(name);
        } catch (final BTreeException | TerminatedException | IOException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        }
    }

    public void remove(final String name, final DocumentImpl doc) throws EXistException, LockException {
        final short id = getId(name);
        remove(doc, id);
    }

    private void remove(final DocumentImpl doc, final short id) throws LockException, EXistException {
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            final byte[] fromKey = computeKey(id, doc.getDocId());
            final byte[] toKey = computeKey(id, doc.getDocId() + 1);
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            index.btree.remove(query, null);
        } catch (final BTreeException | TerminatedException | IOException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        }
    }

    public void remove(final DocumentImpl doc) {
        if (index.btree == null)
            return;
        final byte[] fromKey = new byte[]{1};
        final byte[] endKey = new byte[]{2};

        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(endKey));
            final FindIdCallback callback = new FindIdCallback(true);
            index.btree.query(query, callback);

            for (final long id : callback.allIds) {
                remove(doc, (short) id);
            }

        } catch (final BTreeException | EXistException | LockException | TerminatedException | IOException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        }
    }

    /**
     * Register the given index name and return a short id for it.
     *
     * @param name the name of the index
     *
     * @return a unique id to be used for the index entries
     *
     * @throws EXistException if an error occurs with the database
     * @throws LockException if a locking error occurs
     */
    private short getOrRegisterId(final String name) throws EXistException, LockException {
        short id = getId(name);
        if (id < 0) {
            final byte[] fromKey = {1};
            final byte[] endKey = {2};
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(endKey));
            try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
                final FindIdCallback callback = new FindIdCallback(false);
                index.btree.query(query, callback);
                id = (short) (callback.max + 1);
                registerId(id, name);
            } catch (final IOException | TerminatedException | BTreeException e) {
                throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
            }
        }
        return id;
    }

    private void registerId(final short id, final String name) throws EXistException {
        final byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            index.btree.addValue(new Value(key), id);
        } catch (final LockException | IOException | BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        }
    }

    private void removeId(final String name) throws EXistException {
        final byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(index.btree.getLockName())) {
            index.btree.removeValue(new Value(key));
        } catch (final LockException | IOException | BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        }
    }

    private short getId(final String name) throws EXistException, LockException {
        final byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeReadLock(index.btree.getLockName())) {
            return (short) index.btree.findValue(new Value(key));
        } catch (final BTreeException | IOException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        }
    }

    private byte[] computeKey(final short id, final NodeProxy proxy) {
        final byte[] data = new byte[7 + proxy.getNodeId().size()];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        ByteConversion.intToByteH(proxy.getOwnerDocument().getDocId(), data, 3);
        proxy.getNodeId().serialize(data, 7);
        return data;
    }

    private byte[] computeKey(final short id, final int docId) {
        final byte[] data = new byte[7];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        ByteConversion.intToByteH(docId, data, 3);
        return data;
    }

    private byte[] computeKey(final short id) {
        final byte[] data = new byte[3];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        return data;
    }

    public Object configure(final IndexController controller, final NodeList configNodes, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        return null;
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public void setDocument(final DocumentImpl doc) {
        this.document = doc;
    }

    @Override
    public ReindexMode getMode() {
        return mode;
    }

    public void setMode(final ReindexMode mode) {
        this.mode = mode;
    }

    public IStoredNode getReindexRoot(final IStoredNode node, final NodePath path, final boolean insert, final boolean includeSelf) {
        return insert ? null : node;
    }

    public StreamListener getListener() {
        return null;
    }

    public MatchListener getMatchListener(final DBBroker broker, final NodeProxy proxy) {
        return null;
    }

    public void removeCollection(final Collection collection, final DBBroker broker, final boolean reindex) {
    }

    public boolean checkIndex(final DBBroker broker) {
        return false;
    }

    public Occurrences[] scanIndex(final XQueryContext context, final DocumentSet docs, final NodeSet contextSet, final Map hints) {
        return new Occurrences[0];
    }

    private final static class FindIdCallback implements BTreeCallback {
        long max = 0;
        List<Long> allIds = null;

        private FindIdCallback(final boolean findIds) {
            if (findIds)
                allIds = new ArrayList<>(10);
        }

        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException {
            max = Math.max(max, pointer);
            if (allIds != null) {
                allIds.add(pointer);
            }
            return true;
        }
    }
}
