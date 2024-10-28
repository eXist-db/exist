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
    public final static byte[] intToByte( final int v, final byte[] data, final int start ) {
        data[start] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 8 ) & 0xff );
        data[start + 2] = (byte) ( ( v >>> 16 ) & 0xff );
        data[start + 3] = (byte) ( ( v >>> 24 ) & 0xff );
        return data;
    }

    /**
     * Write an int value to the specified byte array. The first byte is written
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
}

