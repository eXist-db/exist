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
package org.exist.storage;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.util.ByteConversion;
import org.w3c.dom.Node;

/**
 *  Static methods to deal with the signature of a node stored
 *  in the first byte of the node data in the persistent DOM.
 *  
 *  The bits in the signature are used as follows:
 *  
 *  <pre>
 *  8 4 2 1 8 4 2 1
 *  T T T N 0 0 I I
 *  </pre>
 *  
 *   where T = node type, N = has-namespace flag, I = number of bytes used 
 *   to store the name of the node (local name for elements and attributes).
 */
public final class Signatures {

    private final static Logger LOG = LogManager.getLogger(Signatures.class);

    public final static int Char = 0x0;
    public final static int Elem = 0x1;
    public final static int Proc = 0x2;
    public final static int Comm = 0x3;
    public final static int Attr = 0x4;
    public final static int Cdata = 0x5;

    public final static int intContent = 0x1;
    public final static int byteContent = 0x3;
    public final static int noContent = 0x0;
    public final static int shortContent = 0x2;

    /**
     *  @return the storage size of the given type as
     *  number of bytes required.
     * @param type given type
     */
    public final static int getLength(int type) {
        return switch (type) {
            case intContent -> 4;
            case shortContent -> 2;
            case byteContent -> 1;
            default ->
                //TODO : throw an exception there ? -pb
                    0;
        };
    }


    /**
     *  Returns one of IntContent, ShortContent, ByteContent
     *  or NoContent based on the number of bytes required
     *  to store the integer value given in length.
     * @param length number of bytes required
     * @return one of IntContent, ShortContent, ByteContent or NoContent
     */
    public final static byte getSizeType( int length ) {
        if (length > Short.MAX_VALUE)
            {return intContent;}
        else if (length > Byte.MAX_VALUE)
            {return shortContent;}
        else if (length > 0)
            {return byteContent;}
        else
            {return noContent;}
    }


    /**
     *  From the signature in byte 0 of the node data,
     *  extract the node type and return a constant
     *  as defined in {@link Node}.
     *
     * @param signature in byte 0 of the node data
     * @return a constant as defined in {@link Node}.
     */
    public final static short getType(byte signature) {
        final byte type = (byte)((signature & 0xE0) >> 0x5);
        switch (type) {
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
        //TODO : thorw exception here -pb
        LOG.error("Unknown node type : {}", type);
        return -1;
    }
    
    public final static int read(int type, byte[] data, int pos) {
        return switch (type) {
            case intContent -> (int) ByteConversion.byteToInt(data, pos);
            case shortContent -> (int) ByteConversion.byteToShort(data, pos);
            case byteContent -> (int) data[pos];
            default -> 0;
        };
    }

    public final static void write( int type, int size, byte[] data, int pos ) {
        switch ( type ) {
        case intContent:
            ByteConversion.intToByte( size, data, pos );
            break;
        case shortContent:
            ByteConversion.shortToByte( (short) size, data, pos );
            break;
        case byteContent:
            data[pos] = (byte) size;
            break;
        }
        //TODO : throw exception here ? -pb
    }
}

