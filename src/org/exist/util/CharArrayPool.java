package org.exist.util;

/**
 * A pool for char arrays.
 * 
 * This pool is used by class XMLString. Whenever an XMLString needs to
 * reallocate the backing char[], the old array is released into the pool. However,
 * only char[] with length &lt; MAX are kept in the pool. Larger char[] are rarely reused.
 * 
 * The pool is bound to the current thread.
 */
public class CharArrayPool {

	public static final int POOL_SIZE = 128;
	public static final int MAX = 128;
	public static final ThreadLocal pools_ = new PoolThreadLocal();
	private static int slot_ = 0;
	
	public CharArrayPool() {
	}

	public static char[] getCharArray(int size) {
		if(MAX > size) {
			final char[][] pool = (char[][])pools_.get();
			for(int i = 0; i < pool.length; i++) {
				if(pool[i] != null && pool[i].length == size) {
						char[] b = pool[i];
						pool[i] = null;
						return b;
				}
			}
		}
		//System.out.println("creating new char[" + size + "]");
		return new char[size];
	}
	
	public static void releaseCharArray(final char[] b) {
		if(b == null || b.length > MAX)
			return;
		final char[][] pool = (char[][]) pools_.get();
		for(int i = 0; i < pool.length; i++) {
			if(pool[i] == null) {
				pool[i] = b;
				return;
			}
		}
		
		int s = slot_++;
		if (s < 0)
			s = -s;
		pool[s % pool.length] = b;
	}
	
	private static final class PoolThreadLocal extends ThreadLocal {
		
		protected Object initialValue() {
			return new char[POOL_SIZE][];
		}
	}
}
