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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.storage.DBBroker;
import org.exist.storage.dom.RawNodeIterator;

import javax.xml.stream.XMLStreamException;

/**
 * Lazy implementation of a StAX {@link javax.xml.stream.XMLStreamReader}, which directly reads
 * information from the persistent DOM. The class is optimized to support fast scanning of the DOM, where only
 * a few selected node properties are requested. Node properties are extracted on demand. For example, the QName of
 * an element will not be read unless {@link #getText()} is called.
 */
public class EmbeddedXMLStreamReader extends AbstractEmbeddedXMLStreamReader<RawNodeIterator> {

    /**
     * Construct an EmbeddedXMLStreamReader.
     *
     * @param doc              the document to which the start node belongs.
     * @param iterator         a RawNodeIterator positioned on the start node.
     * @param origin           an optional NodeHandle whose nodeId should match the first node in the stream
     *                         (or null if no need to check)
     * @param reportAttributes if set to true, attributes will be reported as top-level events.
     * @throws XMLStreamException
     */
    public EmbeddedXMLStreamReader(final DBBroker broker, final DocumentImpl doc, final RawNodeIterator iterator, final NodeHandle origin, final boolean reportAttributes)
        throws XMLStreamException {
        super(broker, doc, iterator, origin, reportAttributes);
    }


    /**
     * Returns the (internal) address of the node at the cursor's current
     * position.
     *
     * @return internal address of node
     */
    public long getCurrentPosition() {
        return iterator.currentAddress();
    }

    @Override
    protected void verifyOriginNodeId() throws XMLStreamException {
        if(!nodeId.equals(origin.getNodeId())) {
            super.verifyOriginNodeId();
            origin.setInternalAddress(iterator.currentAddress());
        }
    }
}