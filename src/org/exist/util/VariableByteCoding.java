package org.exist.util;

import java.io.IOException;
import java.io.InputStream;

/**
 *  This class encodes integer values using variable-byte coding.
 * In variable-byte coding, the value is split into a sequence
 * of 7-bit chunks. Bit 8 is used to indicate if more bytes follow.
 * If bit 8 is 0, all bytes have been read.
 * 
 * Variable-byte coding usually achieves good compression ratios for
 * a sequence of random integer values. Compression ratio is bad for
 * very small and very large values.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class VariableByteCoding {

	/**
	 *  Decode a variable-byte encoded sequence
	 *
	 *@param  d       the variable-byte encoded sequence of bytes
	 *@param  offset  the offset at which decoding should start
	 *@return         the decoded value
	 */
	public final static long decode(byte[] d, int offset) {
		long r = 0;
		int shift = 0;
		long more;
		int i = 0;
		do {
			r |= ((more = d[offset + i++]) & 0177) << shift;
			if(more < 0)
				throw new ArrayIndexOutOfBoundsException();
			more &= 0200;
			shift += 7;
		} while (more > 0);
		return r;
	}

	/**
	 *  Decode a variable-byte encoded sequence
	 *
	 *@param  is  ByteArrayInputStream to read the variable-byte
	 *	encoded data from
	 *@return     the decoded value
	 */
	public final static long decode(InputStream is) throws IOException {
		long r = 0;
		int shift = 0;
		long more;
		do {
			r |= ((more = is.read()) & 0177) << shift;
			if(more < 0)
				throw new ArrayIndexOutOfBoundsException();
			shift += 7;
			more &= 0200;
		} while (more > 0);
		return r;
	}

	public final static void skipNext(InputStream is) throws IOException {
		while ((is.read() & 0200) > 0) {
		}
	}
	
	public final static void copyTo(InputStream in, FastByteBuffer out) throws IOException {
		int more;
		do {
			more = in.read();
			out.append((byte) more);
			more &= 0200;
		} while (more > 0);
	}

	public final static void copyTo(InputStream in, FastByteBuffer out, int count) 
	throws IOException {
		int more;
		for(int i = 0; i < count; i++) {
			do {
				more = in.read();
				out.append((byte) more);
				more &= 0200;
			} while (more > 0);
		}
	}
	
	/**
	 *  Encode a long integer to a variable-byte encoded
	 * sequence of bytes.
	 *
	 *@param  l  The long integer value to encode
	 *@return    The coded byte sequence
	 */
	public final static byte[] encode(long l) {
		byte[] buf = new byte[9];
		int i = 0;
		while (l > 0177) {
			buf[i++] = (byte) (((l & 0xff) & 0177) | 0200);
			l >>= 7;
		}
		buf[i++] = (byte) ((l & 0xff) & 0177);
		byte[] data = new byte[i];
		System.arraycopy(buf, 0, data, 0, i);
		return data;
	}

	/**
	 * Encode a long integer to a variable-byte encoded sequence of bytes.
	 * 
	 *@param  l       Description of the Parameter
	 *@param  data    Description of the Parameter
	 *@param  offset  Description of the Parameter
	 */
	public final static void encode(long l, byte[] data, int offset) {
		int i = 0;
		while (l > 0177) {
			data[offset + i++] = (byte) ((l & 0177) | 0200);
			l >>= 7;
		}
		data[offset + i++] = (byte) (l & 0177);
	}

	/**
	 *  Encode  a long integer to a variable-byte encoded sequence of bytes.
	 * Write output to a FastByteBuffer.
	 *
	 *@param  buf  Description of the Parameter
	 *@param  l    Description of the Parameter
	 */
	public final static void encode(FastByteBuffer buf, long l) {
		while (l > 0177) {
			buf.append((byte) ((l & 0177) | 0200));
			l >>= 7;
		}
		buf.append((byte) (l & 0177));
	}

	public final static void encodeFixed(FastByteBuffer buf, long l) {
		buf.append((byte) ( ( l >>> 56 ) & 0xff ));
		buf.append((byte) ( ( l >>> 48 ) & 0xff ));
		buf.append((byte) ( ( l >>> 40 ) & 0xff ));
		buf.append((byte) ( ( l >>> 32 ) & 0xff ));
		buf.append((byte) ( ( l >>> 24 ) & 0xff ));
		buf.append((byte) ( ( l >>> 16 ) & 0xff ));
		buf.append((byte) ( ( l >>> 8 ) & 0xff ));
		buf.append((byte) ( ( l >>> 0 ) & 0xff ));
	}
	
	public final static long decodeFixed(InputStream is) throws IOException {
		return ( ( ( (long) is.read() ) & 0xffL ) << 56 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 48 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 40 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 32 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 24 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 16 ) |
		( ( ( (long) is.read() ) & 0xffL ) << 8 ) |
		( ( (long) is.read() ) & 0xffL );
	}
	
	/**
	 *  Get the size of the variable-byte encoded sequence for a
	 * given long.
	 *
	 *@param  l  Description of the Parameter
	 *@return    The size value
	 */
	public final static int getSize(long l) {
		int i = 0;
		while (l > 0177) {
			i++;
			l >>= 7;
		}
		return i + 1;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  args  Description of the Parameter
	 */
	public static void main(String args[]) {
		int t0 = 11;
		int t1 = 12;
		byte[] d0 = encode(t0);
		byte[] d1 = encode(t1);
		System.out.println(StringUtil.hexDump(d0));
		System.out.println(StringUtil.hexDump(d1));
	}
}
