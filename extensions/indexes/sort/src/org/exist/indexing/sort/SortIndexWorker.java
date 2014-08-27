package org.exist.indexing.sort;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.Lock;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SortIndexWorker implements IndexWorker {

    private int mode = 0;
    private DocumentImpl document = null;
    private SortIndex index;

    public SortIndexWorker(SortIndex index) {
        this.index = index;
    }

    public void setDocument(DocumentImpl doc, int mode) {
        this.document = doc;
        this.mode = mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getIndexId() {
        return SortIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    public void flush() {
        switch (mode) {
            case StreamListener.REMOVE_ALL_NODES:
                remove(document);
                break;
        }
    }

    /**
     * Create a new sort index identified by a name. The method iterates through all items in
     * the items list and adds the nodes to the index. It assumes that the list is already ordered.
     *
     * @param name the name by which the index will be identified
     * @param items ordered list of items to store
     *
     * @throws EXistException
     * @throws LockException
     */
    public void createIndex(String name, List<SortItem> items) throws EXistException, LockException {
        // get an id for the new index
        short id = getOrRegisterId(name);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            long idx = 0;
            for (SortItem item : items) {
                byte[] key = computeKey(id, item.getNode());
                index.btree.addValue(new Value(key), idx++);
            }
        } catch (LockException e) {
            throw new EXistException("Exception caught while creating sort index: " + e.getMessage(), e);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while creating sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while creating sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    public boolean hasIndex(String name) throws EXistException, LockException {
        return getId(name) > 0;
    }
    /**
     * Looks up the given node in the specified index and returns its original position
     * in the ordered set as a long integer.
     *
     * @param name the name of the index
     * @param proxy the node
     * @return the original position of the node in the ordered set
     * @throws EXistException
     * @throws LockException
     */
    public long getIndex(String name, NodeProxy proxy) throws EXistException, LockException {
        short id = getId(name);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            byte[] key = computeKey(id, proxy);
            return index.btree.findValue(new Value(key));
        } catch (LockException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * Completely remove the index identified by its name.
     *
     * @param name the name of the index
     *
     * @throws EXistException
     * @throws LockException
     */
    public void remove(String name) throws EXistException, LockException {
        short id = getId(name);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            byte[] fromKey = computeKey(id);
            byte[] toKey = computeKey((short) (id + 1));
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            index.btree.remove(query, null);

            removeId(name);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } catch (TerminatedException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    public void remove(String name, DocumentImpl doc) throws EXistException, LockException {
        short id = getId(name);
        remove(doc, id);
    }

    private void remove(DocumentImpl doc, short id) throws LockException, EXistException {
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            byte[] fromKey = computeKey(id, doc.getDocId());
            byte[] toKey = computeKey(id, doc.getDocId() + 1);
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            index.btree.remove(query, null);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } catch (TerminatedException e) {
            throw new EXistException("Exception caught while deleting sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    public void remove(DocumentImpl doc) {
        if (index.btree == null)
            return;
        byte[] fromKey = new byte[] { 1 };
        byte[] endKey = new byte[] { 2 };

        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(endKey));
            FindIdCallback callback = new FindIdCallback(true);
            index.btree.query(query, callback);

            for (long id : callback.allIds) {
                remove(doc, (short) id);
            }

        } catch (BTreeException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (TerminatedException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (LockException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (EXistException e) {
            SortIndex.LOG.debug("Exception caught while reading sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * Register the given index name and return a short id for it.
     *
     * @param name the name of the index
     * @return a unique id to be used for the index entries
     *
     * @throws EXistException
     * @throws LockException
     */
    private short getOrRegisterId(String name) throws EXistException, LockException {
        short id = getId(name);
        if (id < 0) {
            byte[] fromKey = { 1 };
            byte[] endKey = { 2 };
            IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(endKey));
            final Lock lock = index.btree.getLock();
            try {
                lock.acquire(Lock.READ_LOCK);
                FindIdCallback callback = new FindIdCallback(false);
                index.btree.query(query, callback);
                id = (short)(callback.max + 1);
                registerId(id, name);
            } catch (IOException e) {
                throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
            } catch (BTreeException e) {
                throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
            } catch (TerminatedException e) {
                throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
            } finally {
                lock.release(Lock.READ_LOCK);
            }
        }
        return id;
    }

    private final static class FindIdCallback implements BTreeCallback {
        long max = 0;
        List<Long> allIds = null;

        private FindIdCallback(boolean findIds) {
            if (findIds)
                allIds = new ArrayList<Long>(10);
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            max = Math.max(max, pointer);
            if (allIds != null) {
                allIds.add(pointer);
            }
            return true;
        }
    }

    private void registerId(short id, String name) throws EXistException {
        byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            index.btree.addValue(new Value(key), id);
        } catch (LockException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    private void removeId(String name) throws EXistException {
        byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            index.btree.removeValue(new Value(key));
        } catch (LockException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    private short getId(String name) throws EXistException, LockException {
        byte[] key = new byte[1 + UTF8.encoded(name)];
        key[0] = 1;
        UTF8.encode(name, key, 1);
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            return (short) index.btree.findValue(new Value(key));
        } catch (BTreeException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new EXistException("Exception caught while reading sort index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    private byte[] computeKey(short id, NodeProxy proxy) {
        byte[] data = new byte[7 + proxy.getNodeId().size()];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        ByteConversion.intToByteH(proxy.getDocument().getDocId(), data, 3);
        proxy.getNodeId().serialize(data, 7);
        return data;
    }

    private byte[] computeKey(short id, int docId) {
        byte[] data = new byte[7];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        ByteConversion.intToByteH(docId, data, 3);
        return data;
    }

    private byte[] computeKey(short id) {
        byte[] data = new byte[3];
        data[0] = 0;
        ByteConversion.shortToByteH(id, data, 1);
        return data;
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        return null;
    }

    public void setDocument(DocumentImpl doc) {
        this.document = doc;
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public int getMode() {
        return mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        return insert ? null : node;
    }

    public StreamListener getListener() {
        return null;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return null;
    }

    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
    }

    public boolean checkIndex(DBBroker broker) {
        return false;
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?,?> hints) {
        return new Occurrences[0];
    }

	@Override
	public void indexCollection(Collection col) {
	}

    @Override
    public void indexBinary(BinaryDocument doc) {
    }

    @Override
    public void removeIndex(XmldbURI url) {
    }
}
