package org.exist.storage.io;

import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.exist.util.ByteArray;
import org.exist.util.FastByteBuffer;

/**
 * A byte array output stream using variable byte encoding.
 * 
 * @author wolf
 */
public class VariableByteOutputStream extends OutputStream {

	protected ByteArray buf;

	public VariableByteOutputStream() {
		super();
		buf = new FastByteBuffer(9);
	}

	public VariableByteOutputStream(int size) {
		super();
		buf = new FastByteBuffer(size);
	}

	public void clear() {
		buf.setLength(0);
	}

	public void close() throws IOException {
		buf = null;
	}

	public void flush() throws IOException {
	}

	public byte[] toByteArray() {
		byte[] b = new byte[buf.size()];
		buf.copyTo(b, 0);
		return b;
	}

	public ByteArray data() {
		return buf;
	}

	public void write(int b) throws IOException {
		buf.append((byte) b);
	}

	public void write(byte[] b) {
		buf.append(b);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		buf.append(b, off, len);
	}

	public void write(ByteArray b) {
	    b.copyTo(buf);
	}
	
	public void writeByte(byte b) {
		buf.append(b);
	}

	public void writeShort(int s) {
		while ((s & ~0177) != 0) {
			buf.append((byte) ((s & 0177) | 0200));
			s >>>= 7;
		}
		buf.append((byte) s);
	}

	public void writeInt(int i) {
		while ((i & ~0177) != 0) {
			buf.append((byte) ((i & 0177) | 0200));
			i >>>= 7;
		}
		buf.append((byte) i);
	}

	public void writeLong(long l) {
		while ((l & ~0177) != 0) {
			buf.append((byte) ((l & 0177) | 0200));
			l >>>= 7;
		}
		buf.append((byte) l);
	}

	public void writeFixedLong(long l) {
		buf.append((byte) ((l >>> 56) & 0xff));
		buf.append((byte) ((l >>> 48) & 0xff));
		buf.append((byte) ((l >>> 40) & 0xff));
		buf.append((byte) ((l >>> 32) & 0xff));
		buf.append((byte) ((l >>> 24) & 0xff));
		buf.append((byte) ((l >>> 16) & 0xff));
		buf.append((byte) ((l >>> 8) & 0xff));
		buf.append((byte) ((l >>> 0) & 0xff));
	}

	public void writeUTF(String s) throws IOException {
		byte[] data = null;
		try {
			data = s.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			data = s.getBytes();
		}
		writeInt(data.length);
		write(data, 0, data.length);
	}
}
