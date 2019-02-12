/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.dom.persistent.SymbolTable;
import org.exist.storage.btree.Value;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;

/**
 * @author wolf
 */
public class ElementValue extends Value {

    public static final byte UNKNOWN = -1;
    public static final byte ELEMENT = 0;
    public static final byte ATTRIBUTE = 1;

    public static final int LENGTH_TYPE = 1; //size of byte

    public static final int OFFSET_COLLECTION_ID = 0;
    public static final int OFFSET_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2
    public static final int OFFSET_SYMBOL = OFFSET_TYPE + ElementValue.LENGTH_TYPE; //3
    public static final int OFFSET_NSSYMBOL = OFFSET_SYMBOL + SymbolTable.LENGTH_LOCAL_NAME; //5
    public static final int OFFSET_ID_STRING_VALUE = OFFSET_TYPE + LENGTH_TYPE; //3

    public static final String[] type = { "element", "attribute", "id" };

    ElementValue(int collectionId) {
        len = Collection.LENGTH_COLLECTION_ID;
        data = new byte[len];
        ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        pos = OFFSET_COLLECTION_ID;
    }

    ElementValue(byte type, int collectionId) {
        len = Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE;
        data = new byte[len];
        ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        data[OFFSET_TYPE] = type;
        pos = OFFSET_COLLECTION_ID;
    }

    ElementValue(byte type, int collectionId, short symbol) {
        len = Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE + SymbolTable.LENGTH_LOCAL_NAME;
        data = new byte[len];
        ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        data[OFFSET_TYPE] = type;
        ByteConversion.shortToByte(symbol, data, OFFSET_SYMBOL);
        pos = OFFSET_COLLECTION_ID;
    }

    ElementValue(byte type, int collectionId, short symbol, short nsSymbol) {
        len = Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE + SymbolTable.LENGTH_LOCAL_NAME + OFFSET_NSSYMBOL;
        data = new byte[len];
        ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        data[OFFSET_TYPE] = type;
        ByteConversion.shortToByte(symbol, data, OFFSET_SYMBOL);
        ByteConversion.shortToByte(nsSymbol, data, OFFSET_NSSYMBOL);
        pos = OFFSET_COLLECTION_ID;
    }

    ElementValue(byte type, int collectionId, String idStringValue) {
        //Note that the type expected to be ElementValue.ATTRIBUTE_ID
        //TODO : add sanity check for this ?
        len = Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE + UTF8.encoded(idStringValue);
        data = new byte[len];
        ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        data[OFFSET_TYPE] = type;
        UTF8.encode(idStringValue, data, OFFSET_ID_STRING_VALUE);
        //TODO : reset pos, just like in other contructors ?
    }

    int getCollectionId() {
        return ByteConversion.byteToInt(data, OFFSET_COLLECTION_ID);
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Collection id : ").append(ByteConversion.byteToInt(data, OFFSET_COLLECTION_ID));
        if (len > OFFSET_COLLECTION_ID) {
            buf.append(" Type : ").append(type[data[OFFSET_TYPE]]);
            if (len == Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE + SymbolTable.LENGTH_LOCAL_NAME)
                {
                    buf.append(" Symbol id : ").append(ByteConversion.byteToShort(data, OFFSET_SYMBOL));}
            else if (len == Collection.LENGTH_COLLECTION_ID + ElementValue.LENGTH_TYPE + 
                    SymbolTable.LENGTH_LOCAL_NAME + SymbolTable.LENGTH_NS_URI) {
                buf.append(" Symbol id : ").append(ByteConversion.byteToShort(data, OFFSET_SYMBOL));
                buf.append(" NSSymbol id : ").append(ByteConversion.byteToShort(data, OFFSET_NSSYMBOL));
            } else 
                {buf.append("Invalid data length !!!");}
        }
        return buf.toString();
    }
}
