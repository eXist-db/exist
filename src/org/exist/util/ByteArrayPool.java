package org.exist.util;

/**
 * A pool for byte arrays.
 * 
 * This pool is primarily used while parsing documents: serializing the
 * DOM nodes generates a lot of small byte chunks. Only byte arrays
 * with length &lt; MAX are kept in the pool. Large arrays are rarely
 * reused.
 */
public class ByteArrayPool {

	public static final int POOL_SIZE = 32;
	public static final int MAX = 128;
	public static final ThreadLocal pools_ = new PoolThreadLocal();
	private static int slot_ = 0;
	
	public ByteArrayPool() {
	}

	public static byte[] getByteArray(int size) {
		byte[][] pool = (byte[][])pools_.get();
		if(size < MAX) {
			for(int i = pool.length; i-- > 0; ) {
				if(pool[i] != null && pool[i].length == size) {
					//System.out.println("found byte[" + size + "]");
					byte[] b = pool[i];
					pool[i] = null;
					return b;
				}
			}
		}
		return new byte[size];
	}
	
	public static void releaseByteArray(final byte[] b) {
		if(b == null || b.length > MAX)
			return;
		//System.out.println("releasing byte[" + b.length + "]");
		byte[][] pool = (byte[][]) pools_.get();
		for(int i = pool.length; i-- > 0;) {
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
			return new byte[POOL_SIZE][];
		}
	}
}
