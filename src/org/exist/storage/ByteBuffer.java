package org.exist.storage;

import org.exist.util.ByteConversion;

public final class ByteBuffer {

    byte[] buffer;
    int len = 0;

    public ByteBuffer(int initialSize) {
	this(initialSize, 0);
    }

    public ByteBuffer(int initialSize, int initialLen) {
	buffer = new byte[initialSize];
	len = initialLen;
    }
    
    public void writeInt(int i) {
	if(len + 4 > buffer.length)
	    resize();
	ByteConversion.intToByte(i, buffer, len);
	len += 4;
    }
    
    public void writeLong(long l) {
	if(len + 8 > buffer.length)
	    resize();
	ByteConversion.longToByte(l, buffer, len);
	len += 8;
    }
    
    public int size() { return len; }
    
    public void copyTo(byte[] newBuf, int offset) {
	System.arraycopy(buffer, 0, newBuf, offset, len);
    }
    
    public byte[] getBuffer() {
	return buffer;
    }

    private void resize() {
	byte[] old = buffer;
	buffer = new byte[old.length * 2];
	System.arraycopy(old, 0, buffer, 0, len);
	old = null;
    }
}
