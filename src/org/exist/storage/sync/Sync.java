
package org.exist.storage.sync;

import org.exist.storage.BrokerPool;

/**
 * Sync open buffers to disk.
 */
public class Sync implements Runnable {

	private BrokerPool pool;
	
	public Sync(BrokerPool pool) {
		this.pool = pool;
	}

	public void run() {
		pool.triggerSync();
	}

}
