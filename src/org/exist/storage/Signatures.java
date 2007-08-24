
package org.exist.storage;

import org.exist.util.ByteConversion;
import org.w3c.dom.Node;

/**
 *  Static methods to deal with the signature of a node stored
 *  in the first byte of the node data in the persistent dom.
 *  
 *  The bits in the signature are used as follows:
 *  
 *  <pre>
 *  8 4 2 1 8 4 2 1
 *  T T T N 0 0 I I
 *  </pre>
 *  
 *   where T = node type, N = has-namespace flag, I = no of bytes used to store
 *   the name of the node (local name for elements and attributes).
 */
public final class Signatures {
    
    public final static int Char = 0x0;
    public final static int Elem = 0x1;
	public final static int Proc = 0x2;
    public final static int Comm = 0x3;
    public final static int Attr = 0x4;
    public final static int Cdata = 0x5;
    
    public final static int IntContent = 0x1;
	public final static int ByteContent = 0x3;
    public final static int NoContent = 0x0;
    public final static int ShortContent = 0x2;
    
    /**
     *  Returns the storage size of the given type as
     *  number of bytes required.
     */
    public final static int getLength( int type ) {
        switch ( type ) {
            case IntContent:
                return 4;
            case ShortContent:
                return 2;
            case ByteContent:
                return 1;
        }
        return 0;
    }


    /**
     *  Returns one of IntContent, ShortContent, ByteContent
     *  or NoContent based on the number of bytes required
     *  to store the integer value given in length.
     */
    public final static byte getSizeType( int length ) {
        if ( length > Short.MAX_VALUE )
            return IntContent;
        else if ( length > Byte.MAX_VALUE )
            return ShortContent;
        else if ( length > 0 )
            return ByteContent;
        else
            return NoContent;
    }


    /**
     *  From the signature in byte 0 of the node data,
     *  extract the node type and return a constant
     *  as defined in {@link Node}.
     */
    public final static short getType( byte signature ) {
        byte type = (byte) ( ( signature & 0xE0 ) >> 0x5 );
        switch ( type ) {
            case Char:
                return Node.TEXT_NODE;
            case Elem:
                return Node.ELEMENT_NODE;
            case Attr:
                return Node.ATTRIBUTE_NODE;
            case Proc:
                return Node.PROCESSING_INSTRUCTION_NODE;
            case Comm:
                return Node.COMMENT_NODE;
            case Cdata:
                return Node.CDATA_SECTION_NODE;
        }
        System.err.println( "Unknown node type : " + type);
        return -1;
    }
    
    public final static int read( int type, byte[] data, int pos ) {
        switch ( type ) {
            case IntContent:
                return (int) ByteConversion.byteToInt( data, pos );
            case ShortContent:
                return (int) ByteConversion.byteToShort( data, pos );
            case ByteContent:
                return (int) data[pos];
        }
        return 0;
    }

    public final static void write( int type, int size, byte[] data, int pos ) {
        switch ( type ) {
            case IntContent:
                ByteConversion.intToByte( size, data, pos );
                break;
            case ShortContent:
                ByteConversion.shortToByte( (short) size, data, pos );
                break;
            case ByteContent:
                data[pos] = (byte) size;
                break;
        }
    }
}

