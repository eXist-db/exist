/*
 * IndexStats.java - Apr 4, 2003
 * 
 * @author wolf
 */
package org.exist.storage;

import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;



public class IndexStats {

	private BufferStats indexBufferStats = null;
	private BufferStats dataBufferStats = null;
	
	public IndexStats(BFile db) {
		indexBufferStats = db.getIndexBufferStats();
		dataBufferStats = db.getDataBufferStats();
	}
	
	public IndexStats(DOMFile db) {
		indexBufferStats = db.getIndexBufferStats();
		dataBufferStats = db.getDataBufferStats();
	}
	
	public BufferStats getIndexBufferStats() {
		return indexBufferStats;
	}
	
	public BufferStats getDataBufferStats() {
		return dataBufferStats;
	}
}
