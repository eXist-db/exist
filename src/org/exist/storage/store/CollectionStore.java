/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.store;

import java.io.File;

import org.exist.collections.CollectionCache;
import org.exist.storage.BrokerPool;

public class CollectionStore extends BFile {

	//	size of the internal buffer for collection objects
	public static final int COLLECTION_BUFFER_SIZE = 128;
	
	private CollectionCache collectionsCache = new CollectionCache(COLLECTION_BUFFER_SIZE);

	/**
	 * @param file
	 * @param btreeBuffers
	 * @param dataBuffers
	 */
	public CollectionStore(BrokerPool pool, File file, int btreeBuffers, int dataBuffers) {
		super(pool, file, btreeBuffers, dataBuffers);
	}

	public CollectionCache getCollectionCache() {
		return collectionsCache;
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
}
