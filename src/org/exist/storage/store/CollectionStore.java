/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.store;

import java.io.File;

public class CollectionStore extends BFile {

	/**
	 * @param file
	 * @param btreeBuffers
	 * @param dataBuffers
	 */
	public CollectionStore(File file, int btreeBuffers, int dataBuffers) {
		super(file, btreeBuffers, dataBuffers);
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
