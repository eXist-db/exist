/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import java.nio.ByteBuffer;

/**
 * A collection of static methods to write integer values from/to a
 * byte array.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class ByteConversion {

    /**
     * Read an integer value from the specified byte array, starting at start.
     *
     * @param data the input data
     * @param start the offset to start from in the input data.
     *
     * @return the integer
     *
     * @deprecated reads the lowest byte first. will be replaced with
     *     {@link #byteToIntH(byte[], int)} for consistency.
     */
    @Deprecated
    public final static int byteToInt(final byte[] data, final int start ) {
        return ( data[start] & 0xff ) |
            ( ( data[start + 1] & 0xff ) << 8 ) |
            ( ( data[start + 2] & 0xff ) << 16 ) |
            ( ( data[start + 3] & 0xff ) << 24 );
    }

    /**
     * Read an integer value from the specified byte array, starting at start.
     *
     * This version of the method reads the highest byte first.
     *
     * @param data the input data
     * @param start the offset to start from in the input data.
     *
     * @return the integer
     */
    public final static int byteToIntH(final byte[] data, final int start ) {
        return ( data[start + 3] & 0xff ) |
            ( ( data[start + 2] & 0xff ) << 8 ) |
            ( ( data[start + 1] & 0xff ) << 16 ) |
            ( ( data[start] & 0xff ) << 24 );
    }

    /**
     * Read an integer value from the specified byte buffer.
     *
     * This version of the method reads the highest byte first.
     *
     * @param buf the byte buffer to read from
     *
     * @return the integer
     */
    public final static int byteToIntH(final ByteBuffer buf) {
        final byte b0 = buf.get();
        final byte b1 = buf.get();
        final byte b2 = buf.get();
        final byte b3 = buf.get();

        return (b3 & 0xff) |
                ((b2 & 0xff) << 8) |
                ((b1 & 0xff) << 16) |
                ((b0 & 0xff) << 24);
    }

    /**
     *  Read a long value from the specified byte array, starting at start.
     *
     * @param data the input data
     * @param start the offset to start from in the input data.
     *
     * @return the long integer
     */
    public final static long byteToLong( final byte[] data, final int start ) {
        return ( ( ( (long) data[start] ) & 0xffL ) << 56 ) |
            ( ( ( (long) data[start + 1] ) & 0xffL ) << 48 ) |
            ( ( ( (long) data[start + 2] ) & 0xffL ) << 40 ) |
            ( ( ( (long) data[start + 3] ) & 0xffL ) << 32 ) |
            ( ( ( (long) data[start + 4] ) & 0xffL ) << 24 ) |
            ( ( ( (long) data[start + 5] ) & 0xffL ) << 16 ) |
            ( ( ( (long) data[start + 6] ) & 0xffL ) << 8 ) |
            ( ( (long) data[start + 7] ) & 0xffL );
    }

    /**
     *  Read a long value from the specified byte buffer.
     *
     * @param buf the byte buffer to read from
     *
     * @return the long integer
     */
    public final static long byteToLong(final ByteBuffer buf) {
        final byte b0 = buf.get();
        final byte b1 = buf.get();
        final byte b2 = buf.get();
        final byte b3 = buf.get();
        final byte b4 = buf.get();
        final byte b5 = buf.get();
        final byte b6 = buf.get();
        final byte b7 = buf.get();

        return ((((long) b0) & 0xffL) << 56) |
                ((((long) b1) & 0xffL) << 48) |
                ((((long) b2) & 0xffL) << 40) |
                ((((long) b3) & 0xffL) << 32) |
                ((((long) b4) & 0xffL) << 24) |
                ((((long) b5) & 0xffL) << 16) |
                ((((long) b6) & 0xffL) << 8) |
                (((long) b7) & 0xffL);
    }

    /**
     * Read a short value from the specified byte array, starting at start.
     *
     * @deprecated reads the lowest byte first. will be replaced with
     *     {@link #byteToShortH(byte[], int)} for consistency.
     *
     * @param data the input data
     * @param start the offset to start from in the input data.
     *
     * @return the short integer
     */
    @Deprecated
    public final static short byteToShort( final byte[] data, final int start ) {
        return (short) ( ( ( data[start + 1] & 0xff ) << 8 ) |
            ( data[start] & 0xff ) );
    }

    /**
     * Read a short value from the specified byte array, starting at start.
     *
     * This version of the method reads the highest byte first.
     *
     * @param data the input data
     * @param start the offset to start from in the input data.
     *
     * @return the short integer
     */
    public final static short byteToShortH( final byte[] data, final int start ) {
        return (short) ( ( ( data[start] & 0xff ) << 8 ) |
            ( data[start + 1] & 0xff ) );
    }

    /**
     * Read a short value from the specified byte array, starting at start.
     *
     * This version of the method reads the highest byte first.
     *
     * @param buf the byte buffer to read from
     *
     * @return the short integer
     */
    public final static short byteToShortH(final ByteBuffer buf) {
        final byte b0 = buf.get();
        final byte b1 = buf.get();
        return (short) (((b0 & 0xff) << 8) | (b1 & 0xff));
    }

    /**
     * Write an int value to the specified byte array. The first byte is written
     * into the location specified by start.
     *
     * @deprecated this version of the method writes the lowest byte first. It will
     * be replaced by {@link #intToByteH(int, byte[], int)} for consistency.
     * @param  v the value
     * @param  data  the byte array to write into
     * @param  start  the offset
     * @return   the byte array
     */
    @Deprecated
    public final static byte[] intToByte( final int v, final byte[] data, final int start ) {
        data[start] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 8 ) & 0xff );
        data[start + 2] = (byte) ( ( v >>> 16 ) & 0xff );
        data[start + 3] = (byte) ( ( v >>> 24 ) & 0xff );
        return data;
    }

    /**
     * Write an int value to the specified byte array.
     *
     * This version of the method writes the highest byte first.
     *
     * @param v the value
     * @param buf the byte buffer to write into
     */
    public final static void intToByteH(final int v, final ByteBuffer buf) {
        buf.put((byte) ((v >>> 24) & 0xff));
        buf.put((byte) ((v >>> 16) & 0xff));
        buf.put((byte) ((v >>> 8) & 0xff));
        buf.put((byte) ((v >>> 0) & 0xff));
    }

    /**
     * Write an int value to the specified byte buffer. The first byte is written
     * into the location specified by start.
     *
     * This version of the method writes the highest byte first.
     *
     *@param  v the value
     *@param  data  the byte array to write into
     *@param  start  the offset
     *@return   the byte array
     */
    public final static byte[] intToByteH( final int v, final byte[] data, final int start ) {
        data[start + 3] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 2] = (byte) ( ( v >>> 8 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 16 ) & 0xff );
        data[start] = (byte) ( ( v >>> 24 ) & 0xff );
        return data;
    }

    /**
     * Write a long value to the specified byte array. The first byte is written
     * into the location specified by start.
     *
     *@param  v the value
     *@param  data  the byte array to write into
     *@param  start  the offset
     *@return   the byte array
     */
    public final static byte[] longToByte( final long v, final byte[] data, final int start ) {
        data[start + 7] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 6] = (byte) ( ( v >>> 8 ) & 0xff );
        data[start + 5] = (byte) ( ( v >>> 16 ) & 0xff );
        data[start + 4] = (byte) ( ( v >>> 24 ) & 0xff );
        data[start + 3] = (byte) ( ( v >>> 32 ) & 0xff );
        data[start + 2] = (byte) ( ( v >>> 40 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 48 ) & 0xff );
        data[start] = (byte) ( ( v >>> 56 ) & 0xff );
        return data;
    }

    /**
     * Write a long value to the specified byte buffer.
     *
     * @param v the value
     * @param buf the byte buffer to write into
     */
    public final static void longToByte(final long v, final ByteBuffer buf) {
        buf.put((byte) ((v >>> 56) & 0xff));
        buf.put((byte) ((v >>> 48) & 0xff));
        buf.put((byte) ((v >>> 40) & 0xff));
        buf.put((byte) ((v >>> 32) & 0xff));
        buf.put((byte) ((v >>> 24) & 0xff));
        buf.put((byte) ((v >>> 16) & 0xff));
        buf.put((byte) ((v >>> 8) & 0xff));
        buf.put((byte) ((v >>> 0) & 0xff));
    }

    /**
     * Write an int value to a newly allocated byte array.
     *
     *@param  v the value
     *@return   the byte array
     */
    public final static byte[] longToByte( final long v ) {
        final byte[] data = new byte[8];
        data[7] = (byte) ( ( v >>> 0 ) & 0xff );
        data[6] = (byte) ( ( v >>> 8 ) & 0xff );
        data[5] = (byte) ( ( v >>> 16 ) & 0xff );
        data[4] = (byte) ( ( v >>> 24 ) & 0xff );
        data[3] = (byte) ( ( v >>> 32 ) & 0xff );
        data[2] = (byte) ( ( v >>> 40 ) & 0xff );
        data[1] = (byte) ( ( v >>> 48 ) & 0xff );
        data[0] = (byte) ( ( v >>> 56 ) & 0xff );
        return data;
    }


    /**
     * Write a short value to the specified byte array. The first byte is written
     * into the location specified by start.
     *
     * @deprecated this version of the method writes the lowest byte first. It will be replaced
     * by {@link #shortToByteH(short, byte[], int)} for consistency.
     *
     * @param  v the value
     * @param  data  the byte array to write into
     * @param  start  the offset
     * @return   the byte array
     */
    @Deprecated
    public final static byte[] shortToByte( final short v, final byte[] data, final int start ) {
        data[start] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 8 ) & 0xff );
        return data;
    }

    /**
     * Write a short value to the specified byte array. The first byte is written
     * into the location specified by start.
     *
     * This version writes the highest byte first.
     *
     * @param  v the value
     * @param  data  the byte array to write into
     * @param  start  the offset
     * @return   the byte array
     */
    public final static byte[] shortToByteH( final short v, final byte[] data, final int start ) {
        data[start + 1] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start] = (byte) ( ( v >>> 8 ) & 0xff );
        return data;
    }

    /**
     * Write a short value to the specified byte array.
     *
     * This version writes the highest byte first.
     *
     * @param v the value
     * @param buf the byte buffer to write into
     */
    public final static void shortToByteH(final short v, final ByteBuffer buf) {
        buf.put( (byte) ((v >>> 8) & 0xff));
        buf.put((byte) ((v >>> 0) & 0xff));

    }
}

