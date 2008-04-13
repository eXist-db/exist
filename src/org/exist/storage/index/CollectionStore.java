/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.index;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.Value;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.UTF8;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

/**
 * Handles access to the central collection storage file (collections.dbx). 
 * 
 * @author wolf
 */
public class CollectionStore extends BFile {
	
    public static final String FILE_NAME = "collections.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection.collections";
    
    public final static String FREE_DOC_ID_KEY = "__free_doc_id";
    public final static String NEXT_DOC_ID_KEY = "__next_doc_id";  
    public final static String FREE_COLLECTION_ID_KEY = "__free_collection_id";
    public final static String NEXT_COLLECTION_ID_KEY = "__next_collection_id";  

    public final static byte KEY_TYPE_COLLECTION = 0;
    public final static byte KEY_TYPE_DOCUMENT = 1;


	/**
	 * @param pool
	 * @param id
	 * @param dataDir
	 * @param config
	 * @throws DBException
	 */
	public CollectionStore(BrokerPool pool, byte id, String dataDir, Configuration config) throws DBException {
		super(pool, id, true, new File(dataDir + File.separatorChar + getFileName()), 
				pool.getCacheManager(), 1.25, 0.01, 0.03);		
		config.setProperty(getConfigKeyForFile(), this);				
	}

    public static String getFileName() {
    	return FILE_NAME;      
    }
    
    public static String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    }
	
    /* (non-Javadoc)
     * @see org.dbxml.core.filer.BTree#getBTreeSyncPeriod()
     */
    protected long getBTreeSyncPeriod() {
        return 1000;
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.storage.store.BFile#getDataSyncPeriod()
     */
    protected long getDataSyncPeriod() {
        return 1000;
    }
    
    public boolean flush() throws DBException {
    	boolean flushed = false;
        if (!BrokerPool.FORCE_CORRUPTION) {
            flushed = flushed | dataCache.flush();
            flushed = flushed | super.flush();
        }
        return flushed;
    }
    
    protected void dumpValue(Writer writer, Value value) throws IOException {
    	//TODO : what does this 5 stand for ?
        if (value.getLength() == 5 + Collection.LENGTH_COLLECTION_ID) {
            short collectionId = ByteConversion.byteToShort(value.data(), value.start());
            //TODO : what does this 1 stand for ?
            int docId = ByteConversion.byteToInt(value.data(), value.start() + 1 + Collection.LENGTH_COLLECTION_ID);
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
    	
		public static int OFFSET_TYPE = 0;
		public static int LENGTH_TYPE = 1; //sizeof byte
		public static int OFFSET_COLLECTION_ID = OFFSET_TYPE + LENGTH_TYPE; //1
		public static int LENGTH_TYPE_DOCUMENT = 2; //sizeof short
		public static int OFFSET_DOCUMENT_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //3
		public static int LENGTH_DOCUMENT_TYPE = 1; //sizeof byte
		public static int OFFSET_DOCUMENT_ID = OFFSET_DOCUMENT_TYPE + LENGTH_DOCUMENT_TYPE; //4

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
    	
    	public static int OFFSET_TYPE = 0;
		public static int LENGTH_TYPE = 1; //sizeof byte
		public static int OFFSET_VALUE = OFFSET_TYPE + LENGTH_TYPE; //1

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
