/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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

import java.util.Arrays;
import java.util.Map;

import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;

/**
 * Used to specify a range index on a node path. Contrary to the
 * fulltext index, a range index indexes the value of nodes according
 * to a predefined type.
 * 
 * @author wolf
 */
public class ValueIndexSpec {

    /*
     * Constants to define the type of the index.
     */
    
    /** No index specified **/
    public final static int NO_INDEX = 0;
    
    public final static int STRING = 1;
    
    public final static int INTEGER = 2;
    
    public final static int DOUBLE = 3;
    
    public final static int FLOAT = 4;
    
    public final static int BOOLEAN = 5;
    
    // special flag to indicate that the node has mixed
    // content
    public final static int MIXED_CONTENT = 0x40;
    
    // special flag which is set if the node has
    // been fulltext indexed
    public final static int TEXT = 0x80;
    
	public final static int RANGE_INDEX_MASK = 0x3F;
	
    // Maps the type constants above to the corresponding
    // XPath atomic types
    private final static int[] xpathTypes = {
            Type.ITEM,
            Type.STRING,
            Type.INTEGER,
            Type.DOUBLE,
            Type.FLOAT,
            Type.BOOLEAN
    };
    
    private final static int[] indexTypes = new int[64];
    static {
        Arrays.fill(indexTypes, NO_INDEX);
        indexTypes[Type.STRING] = STRING;
        indexTypes[Type.INTEGER] = INTEGER;
        indexTypes[Type.DOUBLE] = DOUBLE;
        indexTypes[Type.FLOAT] = FLOAT;
        indexTypes[Type.BOOLEAN] = BOOLEAN;
    }
    
    /**
     * For a given index type specifier, return the corresponding
     * atomic XPath type (as defined in {@link org.exist.xquery.value.Type}).
     * 
     * @param type
     * @return
     */
    public final static int indexTypeToXPath(int type) {
        return xpathTypes[type & RANGE_INDEX_MASK];
    }
    
    /**
     * Returns true if the index type specifier has the fulltext index flag
     * set.
     * 
     * @param type
     * @return
     */
    public final static boolean hasFulltextIndex(int type) {
        return (type & TEXT) != 0;
    }
    
    /**
     * Returns true if the index type specifier has the mixed content
     * flag set.
     * 
     * @param type
     * @return
     */
    public final static boolean hasMixedContent(int type) {
        return (type & MIXED_CONTENT) != 0;
    }
    
    /**
     * Returns the index type specifier corresponding to a given
     * XPath type (as defined in {@link org.exist.xquery.value.Type}).
     * 
     * @param type
     * @return
     */
    public final static int xpathTypeToIndex(int type) {
        return indexTypes[type];
    }
    
	public final static boolean hasRangeIndex(int type) {
		return (type & RANGE_INDEX_MASK) > 0;
	}
	
    private NodePath path;
    private int type;
    
    public ValueIndexSpec(Map namespaces, String pathStr, String typeStr) throws DatabaseConfigurationException {
        if(pathStr.length() == 0)
            throw new DatabaseConfigurationException("The path attribute is required in index.create");
        path = new NodePath(namespaces, pathStr, false);
        try {
            type = Type.getType(typeStr);
        } catch (XPathException e) {
            throw new DatabaseConfigurationException("Unknown type: " + typeStr);
        }
    }
    
    /**
     * Returns the path corresponding to this index.
     * 
     * @return
     */
    public NodePath getPath() {
        return path;
    }
    
    /**
     * Returns the XPath type code for this index
     * (as defined in {@link org.exist.xquery.value.Type}).
     * 
     * @return
     */
    public int getType() {
        return type;
    }
    
    /**
     * Returns the index type for this index, corresponding
     * to the constants defined in this class.
     * 
     * @return
     */
    public int getIndexType() {
        return indexTypes[type];
    }
    
    /**
     * Check if the path argument matches the path
     * of this index spec.
     * 
     * @param otherPath
     * @return
     */
    protected boolean matches(NodePath otherPath) {
        return path.match(otherPath);
    }
}