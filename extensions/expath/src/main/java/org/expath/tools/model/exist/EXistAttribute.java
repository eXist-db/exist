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
        return "true".equalsIgnoreCase(attribute.getValue());
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
