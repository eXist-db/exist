
package org.exist.storage;

import org.w3c.dom.Node;
import org.exist.util.*;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    11. September 2002
 */
public final class Signatures {
    public final static int Attr = 0x4;
	public final static int Proc = 0x2;
    public final static int Char = 0x0;
    public final static int Comm = 0x3;
    public final static int Elem = 0x1;
    public final static int IntContent = 0x1;
	public final static int ByteContent = 0x3;
    public final static int NoContent = 0x0;
    public final static int ShortContent = 0x2;


    /**
     *  Gets the length attribute of the Signatures class
     *
     *@param  type  Description of the Parameter
     *@return       The length value
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
     *  Gets the sizeType attribute of the Signatures class
     *
     *@param  length  Description of the Parameter
     *@return         The sizeType value
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
     *  Gets the type attribute of the Signatures class
     *
     *@param  signature  Description of the Parameter
     *@return            The type value
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
        }
        System.err.println( "unknown node type" );
        return -1;
    }


    /**
     *  Description of the Method
     *
     *@param  type  Description of the Parameter
     *@param  data  Description of the Parameter
     *@param  pos   Description of the Parameter
     *@return       Description of the Return Value
     */
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


    /**
     *  Description of the Method
     *
     *@param  type  Description of the Parameter
     *@param  size  Description of the Parameter
     *@param  data  Description of the Parameter
     *@param  pos   Description of the Parameter
     */
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

