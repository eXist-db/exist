
package org.exist.storage.sync;

import org.exist.storage.BrokerPool;

/**
 * This class is registered with the {@link org.exist.storage.sync.SyncDaemon}.
 * It will periodically trigger a cache sync to write cached pages to disk. 
 */
public class Sync implements Runnable {

	public final static int MINOR_SYNC = 0;
	public final static int MAJOR_SYNC = 1;
	
	private long majorSyncPeriod;
	private long lastMajorSync = System.currentTimeMillis();
	private BrokerPool pool;
	
	public Sync(BrokerPool pool, long majorSyncPeriod) {
		this.pool = pool;
		this.majorSyncPeriod = majorSyncPeriod;
	}

	public void run() {
		if(System.currentTimeMillis() - lastMajorSync > majorSyncPeriod) {
			pool.triggerSync(MAJOR_SYNC);
			lastMajorSync = System.currentTimeMillis(); 
		} else {
			pool.triggerSync(MINOR_SYNC);
		}
	}
    
    public void restart() {
        lastMajorSync = System.currentTimeMillis();
    }
}
