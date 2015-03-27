/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmldb;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import org.exist.util.EXistInputSource;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * @author wolf
 */
public class RemoteBinaryResource
        extends AbstractRemoteResource
        implements BinaryResource {
    public RemoteBinaryResource(final RemoteCollection parent, final XmldbURI documentName) throws XMLDBException {
        super(parent, documentName, MimeType.BINARY_TYPE.getName());

    }

    @Override
    public String getId() throws XMLDBException {
        return path.lastSegment().toString();
    }

    @Override
    public String getResourceType() throws XMLDBException {
        return BinaryResource.RESOURCE_TYPE;
    }

    @Override
    public Object getExtendedContent() throws XMLDBException {
        return getExtendedContentInternal(null, false, -1, -1);
    }

    @Override
    public long getStreamLength()
            throws XMLDBException {
        return getStreamLengthInternal(null);
    }

    @Override
    public InputStream getStreamContent()
            throws XMLDBException {
        return getStreamContentInternal(null, false, -1, -1);
    }

    @Override
    public void getContentIntoAStream(OutputStream os)
            throws XMLDBException {
        getContentIntoAStreamInternal(os, null, false, -1, -1);
    }

    protected String getStreamSymbolicPath() {
        String retval = "<streamunknown>";

        if (vfile != null) {
            final Object content = vfile.getContent();
            if (content instanceof File) {
                retval = ((File) content).getAbsolutePath();
            }
        } else if (inputSource != null && inputSource instanceof EXistInputSource) {
            retval = ((EXistInputSource) inputSource).getSymbolicPath();
        }

        return retval;
    }

    @Override
    public void setContent(final Object obj) throws XMLDBException {
        if (!super.setContentInternal(obj)) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    "don't know how to handle value of type " + obj.getClass().getName());
        }
    }

    @Override
    public void setLexicalHandler(final LexicalHandler handler) {
    }

    @Override
    public DocumentType getDocType() throws XMLDBException {
        return null;
    }

    @Override
    public void setDocType(final DocumentType doctype) throws XMLDBException {

    }
}
