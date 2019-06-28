/*
 *  eXist EXPath
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.tools.model.exist;

import org.expath.tools.ToolsException;
import org.expath.tools.model.Attribute;
import org.w3c.dom.Attr;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class EXistAttribute implements Attribute {

    final Attr attribute;
    
    public EXistAttribute(Attr attribute) {
        this.attribute = attribute;
    }
    
    @Override
    public String getLocalName() {
        return attribute.getLocalName();
    }

    @Override
    public String getNamespaceUri() {
        return attribute.getNamespaceURI();
    }

    @Override
    public String getValue() {
        return attribute.getValue();
    }

    @Override
    public boolean getBoolean() throws ToolsException {
        return attribute.getValue().equalsIgnoreCase("true");
    }

    @Override
    public int getInteger() throws ToolsException {
        String s = attribute.getValue();
        try {
            return Integer.parseInt(s);
        }
        catch ( NumberFormatException ex ) {
            throw new ToolsException("@" + getLocalName() + " is not an integer");
        }
    }
}
