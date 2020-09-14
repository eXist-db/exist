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
package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Used to serialize attribute nodes, which are written as a simple
 * "property": "value" pair.
 * 
 * @author wolf
 *
 */
public class JSONSimpleProperty extends JSONNode {

    private final String value;

    public JSONSimpleProperty(final String name, final String value) {
        this(name, value, false);
    }

    public JSONSimpleProperty(final String name, final String value, final boolean isLiteral) {
        super(Type.SIMPLE_PROPERTY_TYPE, name);
        this.value = JSONValue.escape(value);
        if(isLiteral) {
            setSerializationDataType(SerializationDataType.AS_LITERAL);
        }
    }

    @Override
    public void serialize(final Writer writer, final boolean isRoot) throws IOException {
        writer.write('"');
        writer.write(getName());
        writer.write("\"");
        if(isIndent()) {
            writer.write(' ');
        }
        writer.write(':');
        if(isIndent()) {
            writer.write(' ');
        }
        
        if(getSerializationDataType() != SerializationDataType.AS_LITERAL) {
            writer.write('"');
        }
        
        writer.write(value);
        
        if(getSerializationDataType() != SerializationDataType.AS_LITERAL) {
            writer.write('"');
        }
    }

    @Override
    public void serializeContent(final Writer writer) throws IOException {
    }
}
