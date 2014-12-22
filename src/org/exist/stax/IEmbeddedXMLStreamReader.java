/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist team
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.stax;

import org.exist.dom.persistent.IStoredNode;
import org.exist.util.XMLString;

import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;

public interface IEmbeddedXMLStreamReader extends ExtendedXMLStreamReader {

    /**
     * Deserialize the node at the current position of the cursor and return
     * it as a {@link org.exist.dom.persistent.IStoredNode}.
     *
     * @return the node at the current position.
     */
    IStoredNode getNode();

    /**
     * Returns the last node in document sequence that occurs before the
     * current node. Usually used to find the last child before an END_ELEMENT
     * event.
     *
     * @return the last node in document sequence before the current node
     */
    IStoredNode getPreviousNode();

    /**
     * Iterates over each node until
     * the filter returns false
     *
     * @param filter
     */
    void filter(StreamFilter filter) throws XMLStreamException;

    /**
     * Get the Node Type
     * as used in the persistent
     * DOM {@see org.exist.storage.Signatures}
     */
    short getNodeType();

    /**
     * Returns the current value of the parse event as an XMLString,
     * this returns the string value of a CHARACTERS event,
     * returns the value of a COMMENT, the replacement value
     * the string value of a CDATA section or
     * the string value for a SPACE event.
     *
     * @return the current text or the empty text
     */
    XMLString getXMLText();
}
