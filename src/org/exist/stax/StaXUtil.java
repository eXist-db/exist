/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * \$Id\$
 */

package org.exist.stax;

import javax.xml.stream.XMLStreamConstants;

import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

public class StaXUtil {

    public static int streamType2Type(int type) {
        int xpathType;
        switch (type) {
            case XMLStreamConstants.START_ELEMENT :
                xpathType = Type.ELEMENT;
                break;
            case XMLStreamConstants.ATTRIBUTE :
                xpathType = Type.ATTRIBUTE;
                break;
            case XMLStreamConstants.CHARACTERS :
            case XMLStreamConstants.CDATA :
                xpathType = Type.TEXT;
                break;
            case XMLStreamConstants.COMMENT :
                xpathType = Type.COMMENT;
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION :
                xpathType = Type.PROCESSING_INSTRUCTION;
                break;
            default:
                xpathType = Type.UNTYPED;
        }
        return xpathType;
    }

    public static short streamType2DOM(int type) {
        short domType;
        switch (type) {
            case XMLStreamConstants.START_ELEMENT :
                domType = Node.ELEMENT_NODE;
                break;
            case XMLStreamConstants.ATTRIBUTE :
                domType = Node.ATTRIBUTE_NODE;
                break;
            case XMLStreamConstants.CHARACTERS :
                domType = Node.TEXT_NODE;
                break;
            case XMLStreamConstants.CDATA :
                domType = Node.CDATA_SECTION_NODE;
                break;
            case XMLStreamConstants.COMMENT :
                domType = Node.COMMENT_NODE;
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION :
                domType = Node.PROCESSING_INSTRUCTION_NODE;
                break;
            default:
                domType = -1;
        }
        return domType;
    }
}
