
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000/01,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.util;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    11. September 2002
 */
public class ByteConversion {

    /**  Description of the Field */
    public final static int BYTE = 0x0;
    /**  Description of the Field */
    public final static int INT = 0x2;
    /**  Description of the Field */
    public final static int LONG = 0x3;
    /**  Description of the Field */
    public final static int SHORT = 0x1;


    /**
     *  Description of the Method
     *
     *@param  data     Description of the Parameter
     *@param  pointer  Description of the Parameter
     *@param  len      Description of the Parameter
     *@return          Description of the Return Value
     */
    public final static String UTF82String( byte[] data, int pointer, int len ) {
        //int len = byteToShort( data, pointer );
        pointer += 2;
        char[] cdata = new char[len];
        int c;
        int d;
        int e;

        for ( int pos = 0; pos < len; pos++ ) {
            c = (int) data[pointer];
            pointer++;
            if ( c == 0 )
                break;
            if ( c < 0x80 )
                cdata[pos] = (char) c;
            else
                if ( c > 0xDF ) {
                d = (int) data[pointer];
                pointer++;
                e = (int) data[pointer];
                pointer++;
                cdata[pos] = (char) ( ( ( c & 0x0F ) << 12 ) | ( ( d & 0x3F ) << 6 ) | ( e & 0x3F ) );
            }
            else {
                d = (int) data[pointer];
                pointer++;
                cdata[pos] = (char) ( ( ( c & 0x1F ) << 6 ) | ( d & 0x3F ) );
            }

        }
        return new String( cdata );
    }


    /**
     *  Description of the Method
     *
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static int byteToInt( byte data[], int start ) {
        return ( data[start] & 0xff ) |
            ( ( data[start + 1] & 0xff ) << 8 ) |
            ( ( data[start + 2] & 0xff ) << 16 ) |
            ( ( data[start + 3] & 0xff ) << 24 );
    }


    /**
     *  Description of the Method
     *
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    /*
     *  public final static long byteToLong( byte data[], int start ) {
     *  long la[] = new long[8];
     *  for ( short i = 0; i < 8; i++ )
     *  la[i] = ( data[start + i] & 0xff );
     *  return la[7] | ( la[6] << 8 ) | ( la[5] << 16 ) |
     *  ( la[4] << 24 ) | ( la[3] << 32 ) |
     *  ( la[2] << 40 ) | ( la[1] << 48 ) |
     *  ( la[0] << 56 );
     *  }
     */
    /**
     *  Description of the Method
     *
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static long byteToLong( byte[] data, int start ) {
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
     *  Description of the Method
     *
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static short byteToShort( byte[] data, int start ) {
        return (short) ( ( ( data[start + 1] & 0xff ) << 8 ) |
            ( data[start] & 0xff ) );
    }


    /**
     *  Description of the Method
     *
     *@param  l         Description of the Parameter
     *@param  sizeType  Description of the Parameter
     *@return           Description of the Return Value
     */
    public static byte[] compress( int sizeType, long l ) {
        byte[] data = null;
        switch ( sizeType ) {
            case LONG:
                data = longToByte( l );
                break;
            case INT:
                data = intToByte( (int) l );
                break;
            case SHORT:
                data = shortToByte( (short) l );
                break;
            case BYTE:
                data = new byte[1];
                data[0] = (byte) l;
        }
        return data;
    }

    public static byte[] compress( int sizeType, int i ) {
	byte[] data = null;
	switch ( sizeType ) {
            case INT:
                data = intToByte( i );
                break;
            case SHORT:
                data = shortToByte( (short) i );
                break;
            case BYTE:
                data = new byte[1];
                data[0] = (byte) i;
        }
        return data;
    }

    /**
     *  Description of the Method
     *
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static int decodeLength( byte[] data, int start ) {
        return (int) ( data[start] & 0xf ) + (int) ( ( data[start] >>> 4 ) & 0xff );
    }


    /**
     *  Description of the Method
     *
     *@param  sizeType  Description of the Parameter
     *@param  data      Description of the Parameter
     *@param  start     Description of the Parameter
     *@return           Description of the Return Value
     */
    public static long decompress( int sizeType, byte[] data, int start ) {
        switch ( sizeType ) {
            case LONG:
                return byteToLong( data, start );
            case INT:
                return (long) byteToInt( data, start );
            case SHORT:
                return (long) byteToShort( data, start );
            case BYTE:
                return (long) data[start];
        }
        return -1;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public final static String dumpToHex( byte[] data ) {
        StringBuffer buf = new StringBuffer();
        for ( short i = 0; i < data.length; i++ ) {
            byte upper = (byte) ( ( data[i] >>> 4 ) & 0xf );
            if ( upper < 10 )
                buf.append( (char) ( 0x30 + upper ) );
            else
                buf.append( (char) ( 0x61 + upper - 10 ) );
            byte lower = (byte) ( ( data[i] ) & 0xf );
            if ( lower < 10 )
                buf.append( (char) ( 0x30 + lower ) );
            else
                buf.append( (char) ( 0x61 + lower - 10 ) );
        }
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  docId  Description of the Parameter
     *@param  gid    Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static byte[] encodeNodeP( int docId, long gid ) {
        byte[] idata = trunc( intToByte( docId ) );
        byte[] ldata = trunc( longToByte( gid ) );
        byte[] buf = new byte[idata.length + ldata.length + 1];
        buf[0] = len2byte( idata.length, ldata.length );
        System.arraycopy( idata, 0, buf, 1, idata.length );
        System.arraycopy( ldata, 0, buf, idata.length + 1, ldata.length );
        return buf;
    }


    /**
     *  Gets the size attribute of the ByteConversion class
     *
     *@param  sizeType  Description of the Parameter
     *@return           The size value
     */
    public static int getSize( int sizeType ) {
        switch ( sizeType ) {
            case LONG:
                return 8;
            case INT:
                return 4;
            case SHORT:
                return 2;
            case BYTE:
                return 1;
        }
        return -1;
    }


    /**
     *  Gets the type attribute of the ByteConversion class
     *
     *@param  l  Description of the Parameter
     *@return    The type value
     */
    public static int getSizeType( long l ) {
        if ( l > Integer.MAX_VALUE )
            return LONG;
        else if ( l > Short.MAX_VALUE )
            return INT;
        else if ( l > Byte.MAX_VALUE )
            return SHORT;
        else
            return BYTE;
    }


    /**
     *  Description of the Method
     *
     *@param  v      Description of the Parameter
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static byte[] intToByte( int v, byte[] data, int start ) {
        data[start] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 8 ) & 0xff );
        data[start + 2] = (byte) ( ( v >>> 16 ) & 0xff );
        data[start + 3] = (byte) ( ( v >>> 24 ) & 0xff );
        return data;
    }


    /**
     *  Description of the Method
     *
     *@param  v  Description of the Parameter
     *@return    Description of the Return Value
     */
    public final static byte[] intToByte( int v ) {
        byte[] data = new byte[4];
        data[0] = (byte) ( ( v >>> 0 ) & 0xff );
        data[1] = (byte) ( ( v >>> 8 ) & 0xff );
        data[2] = (byte) ( ( v >>> 16 ) & 0xff );
        data[3] = (byte) ( ( v >>> 24 ) & 0xff );
        return data;
    }


    private final static byte len2byte( int len1, int len2 ) {
        byte l1 = (byte) ( len1 & 0xf );
        byte l2 = (byte) ( len2 & 0xf );
        return (byte) ( l1 | ( l2 << 4 ) );
    }


    /**
     *  Description of the Method
     *
     *@param  v      Description of the Parameter
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static byte[] longToByte( long v, byte[] data, int start ) {
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
     *  Description of the Method
     *
     *@param  v  Description of the Parameter
     *@return    Description of the Return Value
     */
    public final static byte[] longToByte( long v ) {
        byte[] data = new byte[8];
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
     *  Description of the Method
     *
     *@param  v      Description of the Parameter
     *@param  data   Description of the Parameter
     *@param  start  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final static byte[] shortToByte( short v, byte[] data, int start ) {
        data[start] = (byte) ( ( v >>> 0 ) & 0xff );
        data[start + 1] = (byte) ( ( v >>> 8 ) & 0xff );
        return data;
    }


    /**
     *  Description of the Method
     *
     *@param  v  Description of the Parameter
     *@return    Description of the Return Value
     */
    public final static byte[] shortToByte( short v ) {
        byte[] data = new byte[2];
        data[0] = (byte) ( ( v >>> 0 ) & 0xff );
        data[1] = (byte) ( ( v >>> 8 ) & 0xff );
        return data;
    }


    /**
     *  Description of the Method
     *
     *@param  s  Description of the Parameter
     *@return    Description of the Return Value
     */
    public final static byte[] string2UTF8( String s ) {
        int sp = 0;
        int slen = s.length();
        int c;
        FastByteBuffer buf = new FastByteBuffer();
        while ( sp < slen ) {
            c = (int) ( s.charAt( sp ) );
            sp++;
            if ( c < 0x80 )
                buf.append( (byte) c );
            else
                if ( c > 0x07FF ) {
                buf.append( (byte) ( ( ( c >>> 12 ) & 0x0F ) | 0xE0 ) );
                buf.append( (byte) ( ( ( c >>> 6 ) & 0x3F ) | 0x80 ) );
                buf.append( (byte) ( ( c & 0x3F ) | 0x80 ) );
            }
            else {
                buf.append( (byte) ( ( ( c >>> 6 ) & 0x1F ) | 0xC0 ) );
                buf.append( (byte) ( ( c & 0x3F ) | 0x80 ) );
            }

        }
        byte[] data = new byte[buf.size()];
        buf.copyTo( data, 0 );
        return data;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public final static byte[] trunc( byte[] data ) {
        int len = data.length;
        for ( int i = data.length - 1; i > -1; i-- )
            if ( data[i] == 0 )
                --len;
            else
                break;
        byte[] ndata = new byte[len + 1];
        ndata[0] = (byte) len;
        System.arraycopy( data, 0, ndata, 1, len );
        return ndata;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@return       Description of the Return Value
     */
    public final static byte[] truncate( byte[] data ) {
        int len = data.length;
        for ( int i = data.length - 1; i > -1; i-- )
            if ( data[i] == 0 )
                --len;
            else
                break;
        if ( len == 0 )
            return null;
        byte[] ndata = new byte[len];
        System.arraycopy( data, 0, ndata, 0, len );
        return ndata;
    }
}

