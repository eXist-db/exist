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

import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class ValueIndexSpec {

    private NodePath path;
    private int type;
    
    public ValueIndexSpec(String pathStr, String typeStr) throws DatabaseConfigurationException {
        if(pathStr.length() == 0)
            throw new DatabaseConfigurationException("The path attribute is required in index.create");
        path = new NodePath(pathStr);
        try {
            type = Type.getType(typeStr);
        } catch (XPathException e) {
            throw new DatabaseConfigurationException("Unknown type: " + typeStr);
        }
    }
    
    public NodePath getPath() {
        return path;
    }
    
    public int getType() {
        return type;
    }
    
    protected boolean matches(NodePath otherPath) {
        return path.match(otherPath);
    }
}
