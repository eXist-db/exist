/*
 * ByteArray.java - Jun 3, 2003
 * 
 * @author wolf
 */
package org.exist.util;

public interface ByteArray {

	void setLength(int len);
	void copyTo(byte[] b, int offset, int length);
	void copyTo(byte[] b, int offset);
	void copyTo(int start, byte[] newBuf, int offset, int len);
	void copyTo(ByteArray other);
	void append(byte b);
	void append(byte[] b);
	void append(byte[] b, int offset, int length);
	int size();
}
