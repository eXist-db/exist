
package org.exist.util;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

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
    public final static long decode( byte[] d, int offset ) {
        long r = 0;
        int shift = 0;
        long more;
        int i = 0;
        do {
            r |= ( ( more = d[offset + i++] ) & 0177 ) << shift;
            more &= 0200;
            shift += 7;
        } while ( more > 0 );
        return r;
    }


    /**
     *  Decode a variable-byte encoded sequence
     *
     *@param  is  ByteArrayInputStream to read the variable-byte
     *	encoded data from
     *@return     the decoded value
     */
    public final static long decode( ByteArrayInputStream is ) {
        long r = 0;
        int shift = 0;
        long more;
        do {
            r |= ( ( more = is.read() ) & 0177 ) << shift;
            if( more < 0 )
                throw new ArrayIndexOutOfBoundsException();
            more &= 0200;
            shift += 7;
        } while ( more > 0 );
        return r;
    }


    /**
     *  Encode a long integer to a variable-byte encoded
     * sequence of bytes.
     *
     *@param  l  The long integer value to encode
     *@return    The coded byte sequence
     */
    public final static byte[] encode( long l ) {
        byte[] buf = new byte[9];
        int i = 0;
        while ( l > 0177 ) {
            buf[i++] = (byte) ( ( ( l & 0xff ) & 0177 ) | 0200 );
            l >>= 7;
        }
        buf[i++] = (byte) ( ( l & 0xff ) & 0177 );
        byte[] data = new byte[i];
        System.arraycopy( buf, 0, data, 0, i );
        return data;
    }


    /**
     * Encode a long integer to a variable-byte encoded sequence of bytes.
     * 
     *@param  l       Description of the Parameter
     *@param  data    Description of the Parameter
     *@param  offset  Description of the Parameter
     */
    public final static void encode( long l, byte[] data, int offset ) {
        int i = 0;
        while ( l > 0177 ) {
            data[offset + i++] = (byte) ( ( l & 0177 ) | 0200 );
            l >>= 7;
        }
        data[offset + i++] = (byte) ( l & 0177 );
    }


    /**
     *  Encode  a long integer to a variable-byte encoded sequence of bytes.
     * Write output to a FastByteBuffer.
     *
     *@param  buf  Description of the Parameter
     *@param  l    Description of the Parameter
     */
    public final static void encode( FastByteBuffer buf, long l ) {
        while ( l > 0177 ) {
            buf.append( (byte) ( ( l & 0177 ) | 0200 ) );
            l >>= 7;
        }
        buf.append( (byte) ( l & 0177 ) );
    }

	public final static void encode( ByteBuffer buf, long l ) {
		while ( l > 0177 ) {
			buf.put( (byte) ( ( l & 0177 ) | 0200 ) );
			l >>= 7;
		}
		buf.put( (byte) ( l & 0177 ) );
	}
	
    /**
     *  Get the size of the variable-byte encoded sequence for a
     * given long.
     *
     *@param  l  Description of the Parameter
     *@return    The size value
     */
    public final static int getSize( long l ) {
        int i = 0;
        while ( l > 0177 ) {
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
    public static void main( String args[] ) {
        long t0 = 4340;
        long t1 = 123;
        System.out.println("t0:" + t0);
        System.out.println( getSize( t0 ) );
        byte[] d0 = encode( t0 );
        ByteArrayInputStream is = new ByteArrayInputStream( d0 );
        System.out.println( decode( is ) );
    }
}

