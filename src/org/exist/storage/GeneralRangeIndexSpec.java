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
public class GeneralRangeIndexSpec extends RangeIndexSpec {
    
    private NodePath path;
    
    public GeneralRangeIndexSpec(Map namespaces, String pathStr, String typeStr) throws DatabaseConfigurationException {
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
     */
    public NodePath getPath() {
        return path;
    }
    
    /**
     * Check if the path argument matches the path
     * of this index spec.
     * 
     * @param otherPath
     * @return Whether or not the 2 paths match
     */
    protected boolean matches(NodePath otherPath) {
        return path.match(otherPath);
    }
    
    public String toString() {
		StringBuilder result = new StringBuilder("General range index\n");
		result.append("\ttype : ").append(Type.getTypeName(this.type)).append('\n');
		result.append("\tpath : ").append(path.toString()).append('\n');
		result.append("\thas full text index : ").append(hasFulltextIndex(this.type)).append('\n');
		result.append("\thas mixed content : ").append(hasMixedContent(this.type)).append('\n');
		result.append("\thas Qname index : ").append(hasQNameIndex(this.type)).append('\n');
		result.append("\thas Qname or value index : ").append(hasQNameOrValueIndex(this.type)).append('\n');
		result.append("\thas range index : ").append(hasRangeIndex(this.type)).append('\n');			
  	 	return result.toString();
    }    
}