/*
 * FixedByteArray.java - Jun 3, 2003
 * 
 * @author wolf
 */
package org.exist.util;

public class FixedByteArray implements ByteArray {

	private byte[] data;
	
	public FixedByteArray(byte[] data) {
		this.data = data;
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#setLength(int)
	 */
	public void setLength(int len) {
		throw new RuntimeException("cannot modify fixed byte array");
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#copyTo(byte[], int, int)
	 */
	public void copyTo(byte[] b, int offset, int length) {
		System.arraycopy(data, 0, b, offset, length);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#copyTo(byte[], int)
	 */
	public void copyTo(byte[] b, int offset) {
		System.arraycopy(data, 0, b, offset, data.length);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#copyTo(int, byte[], int, int)
	 */
	public void copyTo(int start, byte[] newBuf, int offset, int len) {
		System.arraycopy(data, start, newBuf, offset, len);
	}

	public void copyTo(ByteArray other) {
	    other.append(data, 0, data.length);
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
		return data.length;
	}

	/* (non-Javadoc)
	 * @see org.exist.util.ByteArray#release()
	 */
	public void release() {
	}

}
