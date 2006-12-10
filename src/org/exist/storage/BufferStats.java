/*
 * BufferStats.java - Apr 5, 2003
 * 
 * @author wolf
 */
package org.exist.storage;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class BufferStats {
	
	public final static int INDEX = 0;
	public final static int DATA = 1;

	//private int type = 0;
	private int size = 0;
	private int used = 0;
	private int pageFails = 0;
	private int pageHits = 0;
	
	/**
	 * 
	 */
	public BufferStats(int size, int used, int hits, int fails) {
		this.size = size;
		this.used = used;
		this.pageHits = hits;
		this.pageFails = fails;
	}

	/**
	 * @return int
	 */
	public int getPageFails() {
		return pageFails;
	}

	/**
	 * @return int
	 */
	public int getPageHits() {
		return pageHits;
	}

	/**
	 * @return int
	 */
	public int getSize() {
		return size;
	}

	/**
	 * @return int
	 */
	public int getUsed() {
		return used;
	}

}
