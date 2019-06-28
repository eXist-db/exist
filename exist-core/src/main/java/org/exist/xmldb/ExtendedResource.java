/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 * $Id$
 */

package org.exist.xmldb;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import org.xmldb.api.base.XMLDBException;

/**
 * An extension to BinaryResource interface, which adds the
 * common methods needed by LocalBinaryResource and RemoteBinaryResource,
 * so they can be streamlined.
 *
 * @author jmfernandez
 */
public interface ExtendedResource {

    /**
     * It returns an object representing the content, in the representation
     * which needs less memory.
     *
     * @return the content.
     *
     * @throws XMLDBException if an error occurs whilst retriveing the extended content.
     */
    Object getExtendedContent() throws XMLDBException;

    /**
     * It returns an stream to the content, whichever it is its origin.
     *
     * @return the content.
     *
     * @throws XMLDBException if an error occurs whilst streaming the content.
     */
    InputStream getStreamContent() throws XMLDBException;

    /**
     * It returns the length of the content, if known.
     *
     * @return The content length, or -1 if not known.
     *
     * @throws XMLDBException if an error occurs whilst getting the stream's length.
     */
    long getStreamLength() throws XMLDBException;

    /**
     * It saves the resource to the local file given as input parameter.
     * Do NOT confuse with set content.
     *
     * @param localfile the file.
     *
     * @throws XMLDBException if an error occurs whilst getting the content.
     */
    void getContentIntoAFile(Path localfile) throws XMLDBException;

    /**
     * It saves the resource to the local stream given as input parameter.
     * Do NOT confuse with set content.
     *
     * @param os the stream.
     *
     * @throws XMLDBException if an error occurs whilst getting the content.
     */
    void getContentIntoAStream(OutputStream os) throws XMLDBException;
}
