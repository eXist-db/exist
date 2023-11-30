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
package org.exist.stax;

import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamConstants;

public class StaXUtil {

    public static int streamType2Type(final int type) {
        int xpathType = switch (type) {
            case XMLStreamConstants.START_ELEMENT -> Type.ELEMENT;
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> Type.PROCESSING_INSTRUCTION;
            case XMLStreamConstants.CHARACTERS -> Type.TEXT;
            case XMLStreamConstants.COMMENT -> Type.COMMENT;
            case XMLStreamConstants.START_DOCUMENT -> Type.DOCUMENT;
            case XMLStreamConstants.ATTRIBUTE -> Type.ATTRIBUTE;
            case XMLStreamConstants.CDATA -> Type.TEXT;
            default -> Type.UNTYPED;
        };
        return xpathType;
    }

    public static short streamType2DOM(final int type) {
        short domType = switch (type) {
            case XMLStreamConstants.START_ELEMENT -> Node.ELEMENT_NODE;
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> Node.PROCESSING_INSTRUCTION_NODE;
            case XMLStreamConstants.CHARACTERS -> Node.TEXT_NODE;
            case XMLStreamConstants.COMMENT -> Node.COMMENT_NODE;
            case XMLStreamConstants.START_DOCUMENT -> Node.DOCUMENT_NODE;
            case XMLStreamConstants.ENTITY_REFERENCE -> Node.ENTITY_REFERENCE_NODE;
            case XMLStreamConstants.ATTRIBUTE -> Node.ATTRIBUTE_NODE;
            case XMLStreamConstants.DTD -> Node.DOCUMENT_TYPE_NODE;
            case XMLStreamConstants.CDATA -> Node.CDATA_SECTION_NODE;
            case XMLStreamConstants.ENTITY_DECLARATION -> Node.ENTITY_NODE;
            default -> -1;
        };
        return domType;
    }
}
