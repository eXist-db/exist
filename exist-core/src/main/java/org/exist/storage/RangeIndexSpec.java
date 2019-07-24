/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
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

import java.util.Arrays;

import org.exist.dom.QName;
import org.exist.xquery.value.Type;

public abstract class RangeIndexSpec {

	/*
	 * Constants to define the type of the index.
	 * These constants are used to encode the
	 * index type in the storage pointer.
	 */
	
	/** No index specified **/
	public static final int NO_INDEX = 0;
	public static final int STRING = 1;
	public static final int INTEGER = 2;
	public static final int DOUBLE = 3;
	public static final int FLOAT = 4;
	public static final int BOOLEAN = 5;
	public static final int DATE_TIME = 6;
        public static final int DATE = 7;
        public static final int DECIMAL = 8;

        public static final int QNAME_INDEX = 0x10; // Indicates that the node has a qname-value index defined on it.
	
	/**
	 * Bit mask to extract the type of the range index.
	 */
	private static final int RANGE_INDEX_MASK = 0x0F;

        private static final int HAS_VALUE_OR_MIXED_INDEX_MASK = 0x18;

    private static final int[] xpathTypes = {
            Type.ITEM,
            Type.STRING,
            Type.INTEGER,
            Type.DOUBLE,
            Type.FLOAT,
            Type.BOOLEAN,
            Type.DATE_TIME,
            Type.DATE,
            Type.DECIMAL
	};
	
	protected static final int[] indexTypes = new int[64];
	static {
        Arrays.fill(indexTypes, NO_INDEX);
        indexTypes[Type.STRING] = STRING;
        indexTypes[Type.INTEGER] = INTEGER;
        indexTypes[Type.DOUBLE] = DOUBLE;
        indexTypes[Type.FLOAT] = FLOAT;
        indexTypes[Type.BOOLEAN] = BOOLEAN;
        indexTypes[Type.DATE_TIME] = DATE_TIME;
        indexTypes[Type.DATE] = DATE;
        indexTypes[Type.DECIMAL] = DECIMAL;
    }

	/**
	 * For a given index type bit, return the corresponding
	 * atomic XPath type (as defined in {@link org.exist.xquery.value.Type}).
	 * 
	 * @param type a bit set indicating the type
	 * @return atomic XPath type
	 */
	public static final int indexTypeToXPath(int type) {
	    return xpathTypes[type & RANGE_INDEX_MASK];
	}

	/**
	 * Returns the index type bit mask corresponding to a given
	 * XPath type (as defined in {@link org.exist.xquery.value.Type}).
	 * 
	 * @param type XPath type
	 * @return the index type bit mask
	 */
	public static final int xpathTypeToIndex(int type) {
	    return indexTypes[type];
	}

	/**
	 * Returns true if the index type bit mask has a range index
	 * bit set.
	 * 
	 * @param type a bit set indicating the type
	 * @return True if the index type bit mask has a range index bit set.
	 */
	public static final boolean hasRangeIndex(int type) {
		return (type & RANGE_INDEX_MASK) > 0 && !hasQNameIndex(type);
	}

	public static final boolean hasQNameIndex(int type) {
		return (type & QNAME_INDEX) != 0;
	}
	
	public static final boolean hasQNameOrValueIndex(int type) {
		return (type & HAS_VALUE_OR_MIXED_INDEX_MASK) > 0;
	}

    /**
     * Replace subtypes of string and integer with their
     * base type for the index. Using xs:int instead of xs:integer
     * for an index does not have any advantage.
	 *
	 * @param type type to search super type for
	 * @return the super type
     */
    protected static final int getSuperType(int type) {
        if (Type.subTypeOf(type, Type.INTEGER))
            {return Type.INTEGER;}
        if (Type.subTypeOf(type, Type.STRING))
            {return Type.STRING;}
        return type;
    }

    protected int type;

	protected RangeIndexSpec() {
	}
	
	/**
	 * Returns the XPath type code for this index
	 * (as defined in {@link org.exist.xquery.value.Type}).
	 * 
	 * @return XPath type code
	 */
	public int getType() {
	    return type;
	}

	/**
	 * Returns the index type for this index, corresponding
	 * to the constants defined in this class.
	 * 
	 * @return index type
	 */
	public int getIndexType() {
	    return indexTypes[type];
	}

    /**
     * Returns the QName for which this index is created. Might be
     * null if it is a generic index.
     * 
     * @return qname
     */
    public QName getQName() {
        return null;
    }
}
