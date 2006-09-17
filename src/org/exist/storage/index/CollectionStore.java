/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.index;

import java.io.File;

import org.exist.storage.BrokerPool;
import org.exist.storage.CacheManager;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.DBException;

/**
 * Handles access to the central collection storage file (collections.dbx). 
 * 
 * @author wolf
 */
public class CollectionStore extends BFile {
    
    public final static String FREE_DOC_ID_KEY = "__free_doc_id";
    public final static String NEXT_DOC_ID_KEY = "__next_doc_id";  
    public final static String FREE_COLLECTION_ID_KEY = "__free_collection_id";
    public final static String NEXT_COLLECTION_ID_KEY = "__next_collection_id";  
    
    /**
     * 
     * 
     * @param pool 
     * @param cacheManager 
     * @param file 
     * @throws DBException 
     */
	public CollectionStore(BrokerPool pool, File file, CacheManager cacheManager) throws DBException {
		super(pool, NativeBroker.COLLECTIONS_DBX_ID, true, file, cacheManager, 1.25, 0.01, 0.03);
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
}
