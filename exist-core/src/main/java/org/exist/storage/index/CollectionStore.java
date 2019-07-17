/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.index;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles access to the central collection storage file (collections.dbx). 
 * 
 * @author wolf
 */
public class CollectionStore extends BFile {

    public static final short FILE_FORMAT_VERSION_ID = 16;

    public static final String FILE_NAME = "collections.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection.collections";
    
    public final static String FREE_DOC_ID_KEY = "__free_doc_id";
    public final static String NEXT_DOC_ID_KEY = "__next_doc_id";  
    public final static String FREE_COLLECTION_ID_KEY = "__free_collection_id";
    public final static String NEXT_COLLECTION_ID_KEY = "__next_collection_id";  

    public final static byte KEY_TYPE_COLLECTION = 0;
    public final static byte KEY_TYPE_DOCUMENT = 1;

    private Deque<Integer> freeResourceIds = new ArrayDeque<>();
    private Deque<Integer> freeCollectionIds = new ArrayDeque<>();

    /**
     * @param pool the broker pool
     * @param id the if of the collection store
     * @param dataDir the data directory for the collection store
     * @param config the database configuration
     *
     * @throws DBException if the collection store cannot be constructed.
     */
    public CollectionStore(BrokerPool pool, byte id, Path dataDir, Configuration config) throws DBException {
        super(pool, id, FILE_FORMAT_VERSION_ID, true, dataDir.resolve(getFileName()),
                pool.getCacheManager(), 1.25, 0.03);
        config.setProperty(getConfigKeyForFile(), this);
    }

    public static String getFileName() {
        return FILE_NAME;
    }
    
    public static String getConfigKeyForFile() {
        return FILE_KEY_IN_CONFIG;
    }

    @Override
    protected long getDataSyncPeriod() {
        return 1000;
    }

    @Override
    public boolean flush() throws DBException {
        boolean flushed = false;
        if (!BrokerPool.FORCE_CORRUPTION) {
            flushed = flushed | dataCache.flush();
            flushed = flushed | super.flush();
        }
        return flushed;
    }

    public void freeResourceId(int id) {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(getLockName())) {
            freeResourceIds.push(id);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(getFile()), e);
        }
    }

    public int getFreeResourceId() {
        int freeDocId = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(getLockName())) {
            if (!freeResourceIds.isEmpty()) {
                freeDocId = freeResourceIds.pop();
            }
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(getFile()), e);
            return DocumentImpl.UNKNOWN_DOCUMENT_ID;
            //TODO : rethrow ? -pb
        }
        return freeDocId;
    }

    public void freeCollectionId(int id) {
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(getLockName())) {
            freeCollectionIds.push(id);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(getFile()), e);
        }
    }

    public int getFreeCollectionId() {
        int freeCollectionId = Collection.UNKNOWN_COLLECTION_ID;
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(getLockName())) {
            if (!freeCollectionIds.isEmpty()) {
                freeCollectionId = freeCollectionIds.pop();
            }
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock on " + FileUtils.fileName(getFile()), e);
            return Collection.UNKNOWN_COLLECTION_ID;
            //TODO : rethrow ? -pb
        }
        return freeCollectionId;
    }

    protected void dumpValue(Writer writer, Value value) throws IOException {
        //TODO : what does this 5 stand for ?
        if (value.getLength() == 5 + Collection.LENGTH_COLLECTION_ID) {
            final short collectionId = ByteConversion.byteToShort(value.data(), value.start());
            //TODO : what does this 1 stand for ?
            final int docId = ByteConversion.byteToInt(value.data(), value.start() + 1 + Collection.LENGTH_COLLECTION_ID);
            writer.write('[');
            writer.write("Document: collection = ");
            writer.write(collectionId);
            writer.write(", docId = ");
            writer.write(docId);
            writer.write(']');
        } else {
            writer.write('[');
            writer.write("Collection: ");
            writer.write(new String(value.data(), value.start(), value.getLength(), "UTF-8"));
            writer.write(']');
        }
    }

    public static class DocumentKey extends Value {
        
        public static final int OFFSET_TYPE = 0;
        public static final int LENGTH_TYPE = 1; //sizeof byte
        public static final int OFFSET_COLLECTION_ID = OFFSET_TYPE + LENGTH_TYPE; //1
        public static final int LENGTH_TYPE_DOCUMENT = 2; //sizeof short
        public static final int OFFSET_DOCUMENT_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //3
        public static final int LENGTH_DOCUMENT_TYPE = 1; //sizeof byte
        public static final int OFFSET_DOCUMENT_ID = OFFSET_DOCUMENT_TYPE + LENGTH_DOCUMENT_TYPE; //4

        public DocumentKey() {
            data = new byte[LENGTH_TYPE];
            data[OFFSET_TYPE] = KEY_TYPE_DOCUMENT;
            len = LENGTH_TYPE;
        }

        public DocumentKey(int collectionId) {
            data = new byte[LENGTH_TYPE + Collection.LENGTH_COLLECTION_ID];
            data[OFFSET_TYPE] = KEY_TYPE_DOCUMENT;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            len = LENGTH_TYPE + Collection.LENGTH_COLLECTION_ID;
            pos = OFFSET_TYPE;
        }

        public DocumentKey(int collectionId, byte type, int docId) {
            data = new byte[LENGTH_TYPE + Collection.LENGTH_COLLECTION_ID + LENGTH_DOCUMENT_TYPE + 
                            DocumentImpl.LENGTH_DOCUMENT_ID];
            data[OFFSET_TYPE] = KEY_TYPE_DOCUMENT;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            data[OFFSET_DOCUMENT_TYPE] = type;
            ByteConversion.intToByte(docId, data, OFFSET_DOCUMENT_ID);
            len = LENGTH_TYPE + Collection.LENGTH_COLLECTION_ID + LENGTH_DOCUMENT_TYPE + 
            	DocumentImpl.LENGTH_DOCUMENT_ID;
            pos = OFFSET_TYPE;
        }

        public static int getCollectionId(Value key) {
            return ByteConversion.byteToInt(key.data(), key.start() + OFFSET_COLLECTION_ID);
        }

        public static int getDocumentId(Value key) {
            return ByteConversion.byteToInt(key.data(), key.start() + OFFSET_DOCUMENT_ID);
        }
    }

    public static class CollectionKey extends Value {
        
        public static final int OFFSET_TYPE = 0;
        public static final int LENGTH_TYPE = 1; //sizeof byte
        public static final int OFFSET_VALUE = OFFSET_TYPE + LENGTH_TYPE; //1

        public CollectionKey() {
            data = new byte[LENGTH_TYPE];
            data[OFFSET_TYPE] = KEY_TYPE_COLLECTION;
            len = LENGTH_TYPE;
        }

        public CollectionKey(String name) {
            len = LENGTH_TYPE + UTF8.encoded(name);
            data = new byte[len];
            data[OFFSET_TYPE] = KEY_TYPE_COLLECTION;
            UTF8.encode(name, data, OFFSET_VALUE);
            pos = OFFSET_TYPE;
        }
    }
}
