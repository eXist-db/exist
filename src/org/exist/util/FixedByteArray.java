/*
 * FixedByteArray.java - Jun 3, 2003
 * 
 * @author wolf
 */
package org.exist.util;

import java.nio.ByteBuffer;

public class FixedByteArray implements ByteArray {

	private byte[] data;
	private int start;
	private int len;
	
	public FixedByteArray(byte[] data, int start, int len) {
		this.data = data;
		this.start = start;
		this.len = len;
	}

    public FixedByteArray(byte[] data) {
        this(data, 0, data.length);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#setLength(int)
	 */
	public void setLength(int len) {
		throw new RuntimeException("cannot modify fixed byte array");
	}

    public String toString() {
        return new String(data, start, len);
    }

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#copyTo(byte[], int)
	 */
	public void copyTo(byte[] b, int offset) {
		System.arraycopy(data, start, b, offset, len);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#copyTo(int, byte[], int, int)
	 */
	public void copyTo(int startOffset, byte[] newBuf, int offset, int count) {
		System.arraycopy(data, start + startOffset, newBuf, offset, count);
	}

	public void copyTo(ByteArray other) {
	    other.append(data, start, len);
	}
	
    public void copyTo(ByteBuffer buf) {
        buf.put(data, start, len);
    }
    
	public void copyTo(int startOffset, ByteBuffer buf, int count) {
		buf.put(data, start + startOffset, count);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#append(byte)
	 */
	public void append(byte b) {
		throw new RuntimeException("cannot modify fixed byte array");
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#append(byte[])
	 */
	public void append(byte[] b) {
		throw new RuntimeException("cannot modify fixed byte array");
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#append(byte[], int, int)
	 */
	public void append(byte[] b, int offset, int length) {
		throw new RuntimeException("cannot modify fixed byte array");
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#size()
	 */
	public int size() {
		return len;
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#release()
	 */
	public void release() {
	}

}
