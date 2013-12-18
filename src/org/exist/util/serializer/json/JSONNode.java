/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public abstract class JSONNode {

    protected final static String ANONYMOUS_OBJECT = "#anonymous";

    public static enum SerializationType { AS_STRING, AS_ARRAY, AS_LITERAL };

    public static enum Type { OBJECT_TYPE, VALUE_TYPE, SIMPLE_PROPERTY_TYPE };

    private Type type;
    private String name;
    private SerializationType writeAs = SerializationType.AS_STRING;

    private JSONNode next = null;
    private JSONNode nextOfSame = null;

    public JSONNode(final Type type, final String name) {
        this.type = type;
        this.name = name;
    }

    public abstract void serialize(final Writer writer, final boolean isRoot) throws IOException;

    public abstract void serializeContent(final Writer writer) throws IOException;

    public Type getType() {
        return type;
    }

    public boolean isNamed() {
        return !getName().equals(ANONYMOUS_OBJECT);
    }

    public boolean isArray() {
        return getNextOfSame() != null || getSerializationType() == SerializationType.AS_ARRAY;
    }

    public SerializationType getSerializationType() {
        return writeAs;
    }

    public void setSerializationType(SerializationType type) {
        writeAs = type;
    }

    public JSONNode getNextOfSame() {
        return nextOfSame;
    }

    public void setNextOfSame(JSONNode nextOfSame) {
        if(this.nextOfSame == null) {
            this.nextOfSame = nextOfSame;
        } else {
            JSONNode current = this.nextOfSame;
            while(current.nextOfSame != null) {
                current = current.nextOfSame;
            }
            current.nextOfSame = nextOfSame;
        }
    }

    public void setNext(final JSONNode next) {
        this.next = next;
    }

    public JSONNode getNext() {
        return next;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}