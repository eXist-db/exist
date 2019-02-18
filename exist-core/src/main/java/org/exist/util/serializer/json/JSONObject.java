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
import java.io.StringWriter;
import java.io.Writer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JSONObject extends JSONNode {

    private final static Logger LOG = LogManager.getLogger(JSONObject.class);
    
    protected JSONNode firstChild = null;
    private boolean asSimpleValue = false;

    public JSONObject() {
        super(Type.OBJECT_TYPE, ANONYMOUS_OBJECT);
    }

    public JSONObject(final String name) {
        super(Type.OBJECT_TYPE, name);
    }

    public JSONObject(final String name, final boolean asSimpleValue) {
        super(Type.OBJECT_TYPE, name);
        this.asSimpleValue = asSimpleValue;
    }

    public void addObject(final JSONNode node) {
        JSONNode childNode = findChild(node.getName());
        if(childNode == null) {
            childNode = getLastChild();
            if(childNode == null) {
                firstChild = node;
            } else {
                childNode.setNext(node);
            }
        } else {
            childNode.setNextOfSame(node);
        }
    }
	
    public JSONNode findChild(final String nameToFind) {
        JSONNode nextNode = firstChild;
        while(nextNode != null) {
            if(nextNode.getName().equals(nameToFind)) {
                return nextNode;
            }
            nextNode = nextNode.getNext();
        }
        return null;
    }

    public JSONNode getLastChild() {
        JSONNode nextNode = firstChild;
        JSONNode currentNode = null;
        while(nextNode != null) {
            currentNode = nextNode;
            nextNode = currentNode.getNext();
        }
        return currentNode;
    }
	
    public int getChildCount() {
        int count = 0;
        JSONNode nextNode = firstChild;
        while(nextNode != null) {
            count++;
            nextNode = nextNode.getNext();
        }
        return count;
    }
	
    public void serialize(final Writer writer, final boolean isRoot) throws IOException {
        if(!isRoot && isNamed()) {
            writer.write('"');
            writer.write(getName());
            writer.write('"');
            if(isIndent()) {
                writer.write(' ');
            }
            writer.write(':');
            if(isIndent()) {
                writer.write(' ');
            }
        }
        
        if(getNextOfSame() != null || getSerializationType() == SerializationType.AS_ARRAY) {
            writer.write('[');
            JSONNode next = this;
            while(next != null) {
                next.serializeContent(writer);
                next = next.getNextOfSame();
                if (next != null) {
                    writer.write(',');
                    if(isIndent()) {
                        writer.write(' ');
                    }
                }
            }
            writer.write(']');
        } else {
            serializeContent(writer);
        }
    }
	
    @Override
    public void serializeContent(final Writer writer) throws IOException {
        if(firstChild == null) {
            // an empty node gets a null value, unless its a specified array
            if(getSerializationType() != SerializationType.AS_ARRAY) {
                writer.write("null");
            }
        } else if(firstChild.getNext() == null
                && (firstChild.getType() == Type.VALUE_TYPE || (firstChild.isArray() && !firstChild.isNamed()))) {
                // if there's only one child and if it is text, it is serialized as simple value
                firstChild.serialize(writer, false);
        } else {
            // complex object
            writer.write('{');
            if(isIndent()) {
                writer.write(' ');
            }

            JSONNode next = firstChild;
//            boolean allowText = false;
//            boolean skipMixedContentText = false;
            while(next != null) {
                if(next.getType() == Type.VALUE_TYPE) {
                    /*
                     if an element has attributes and text content, the text
                     node is serialized as property "#text". 
                     Text in mixed content nodes is ignored though.
                    */
//                    if(allowText) {
                        writer.write("\"" + next.getName() + "\"");     // next.getName() returns "#text"
                        if(isIndent()) {
                            writer.write(' ');
                        }
                        writer.write(':');
                        if(isIndent()) {
                            writer.write(' ');
                        }
                        next.serialize(writer, false);
//                        allowText = false;
//                    } else {
//                        //writer.write("\"#mixed-content-text\" : ");
//                        skipMixedContentText = true;
//                    }
                } else {
                    next.serialize(writer, false);
                }

//                if(next.getType() == Type.SIMPLE_PROPERTY_TYPE) {
//                    allowText = true;
//                }

                next = next.getNext();

                if(next != null /*&& !skipMixedContentText && !isMixedContentTextLast(next, allowText)*/) {
                    writer.write(',');
                    if(isIndent()) {
                        writer.write(' ');
                    }
                }

//                skipMixedContentText = false;
            }
            if(isIndent()) {
                writer.write(' ');
            }
            writer.write('}');
        }
    }
 
//    private boolean isMixedContentTextLast(final JSONNode node, final boolean allowText) {
//        return node.getType() == Type.VALUE_TYPE && !allowText && node.equals(getLastChild());
//    }

    @Override
    public String toString() {
        final StringWriter writer = new StringWriter();
        try {
            serialize(writer, false);
        } catch(final IOException e) {
            LOG.warn(e);
        }
        return writer.toString();
    }
}
