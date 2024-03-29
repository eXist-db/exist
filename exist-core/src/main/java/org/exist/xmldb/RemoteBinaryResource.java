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
package org.exist.xmldb;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.exist.storage.blob.BlobId;
import org.exist.util.EXistInputSource;
import org.exist.util.MimeType;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class RemoteBinaryResource
        extends AbstractRemoteResource
        implements EXistBinaryResource {

    private String type = null;
    private byte[] content = null;  // only used for binary results from an XQuery execution, where we have been sent the result
    private BlobId blobId = null;
    private MessageDigest contentDigest = null;

    public RemoteBinaryResource(final RemoteCollection parent, final XmldbURI documentName) throws XMLDBException {
        super(parent, documentName, MimeType.BINARY_TYPE.getName());
    }

    public RemoteBinaryResource(final RemoteCollection parent, final XmldbURI documentName, final String type, final byte[] content) throws XMLDBException {
        super(parent, documentName, MimeType.BINARY_TYPE.getName());
        this.type = type;
        this.content = content;
    }

    @Override
    public String getId() throws XMLDBException {
        return path.lastSegment().toString();
    }

    @Override
    public Object getExtendedContent() throws XMLDBException {
        return getExtendedContentInternal(content, false, -1, -1);
    }

    @Override
    public long getStreamLength()
            throws XMLDBException {
        return getStreamLengthInternal(content);
    }

    @Override
    public InputStream getStreamContent()
            throws XMLDBException {
        return getStreamContentInternal(content, false, -1, -1);
    }

    @Override
    public void getContentIntoAStream(final OutputStream os)
            throws XMLDBException {
        getContentIntoAStreamInternal(os, content, false, -1, -1);
    }

    @Override
    public void getContentAsStream(OutputStream os) throws XMLDBException {
        getContentIntoAStream(os);
    }

    protected String getStreamSymbolicPath() {
        String retval = "<streamunknown>";

        if (file != null) {
            retval = file.toAbsolutePath().toString();
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

    @Override
    public BlobId getBlobId() {
        return blobId;
    }

    public void setBlobId(final BlobId blobId) {
        this.blobId = blobId;
    }

    @Override
    public MessageDigest getContentDigest(final DigestType digestType) throws XMLDBException {
        if (contentDigest != null && contentDigest.getDigestType().equals(digestType)) {
            return contentDigest;
        }

        final List<String> params = new ArrayList<>();
        params.add(path.toString());
        params.add(digestType.getCommonNames()[0]);

        final Map result = (Map) collection.execute("getContentDigest", params);
        final String digestAlgorithm = (String)result.get("digest-algorithm");
        final byte[] digest = (byte[])result.get("digest");

        final MessageDigest messageDigest = new MessageDigest(DigestType.forCommonName(digestAlgorithm), digest);
        if (this.contentDigest == null) {
            this.contentDigest = messageDigest;
        }
        return messageDigest;

    }

    public void setContentDigest(final MessageDigest contentDigest) {
        this.contentDigest = contentDigest;
    }

    @Override
    @Nullable public void setProperties(final Properties properties) {
    }
}
