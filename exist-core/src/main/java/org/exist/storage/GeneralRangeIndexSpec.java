/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.util.Map;

import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;

/**
 * Used to specify a range index on a node path.
 * The range index indexes the value of nodes according
 * to a predefined type.
 * 
 * @author wolf
 */
public class GeneralRangeIndexSpec extends RangeIndexSpec {
    
    private NodePath path;
    
    public GeneralRangeIndexSpec(Map<String, String> namespaces, String pathStr, String typeStr) throws DatabaseConfigurationException {
        if(pathStr.length() == 0)
            {throw new DatabaseConfigurationException("The path attribute is required in index.create");}
        path = new NodePath(namespaces, pathStr, false);
        try {
            type = getSuperType(Type.getType(typeStr));
        } catch (final XPathException e) {
            throw new DatabaseConfigurationException("Unknown type: " + typeStr);
        }
    }
    
    /**
     * @return the path corresponding to this index.
     */
    public NodePath getPath() {
        return path;
    }
    
    /**
     * Check if the path argument matches the path
     * of this index spec.
     * 
     * @param otherPath path argument to check
     * @return Whether or not the 2 paths match
     */
    protected boolean matches(NodePath otherPath) {
        return path.match(otherPath);
    }
    
    public String toString() {
        return "General range index\n" + "\ttype : " + Type.getTypeName(this.type) + '\n' +
                "\tpath : " + path.toString() + '\n' +
                "\thas Qname index : " + hasQNameIndex(this.type) + '\n' +
                "\thas Qname or value index : " + hasQNameOrValueIndex(this.type) + '\n' +
                "\thas range index : " + hasRangeIndex(this.type) + '\n';
    }
}