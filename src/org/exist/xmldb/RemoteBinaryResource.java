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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.storage.blob.BlobId;
import org.exist.util.EXistInputSource;
import org.exist.util.Leasable;
import org.exist.util.MimeType;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import javax.annotation.Nullable;

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

    public RemoteBinaryResource(final Leasable<XmlRpcClient>.Lease xmlRpcClientLease, final RemoteCollection parent, final XmldbURI documentName) throws XMLDBException {
        super(xmlRpcClientLease, parent, documentName, MimeType.BINARY_TYPE.getName());
    }

    public RemoteBinaryResource(final Leasable<XmlRpcClient>.Lease xmlRpcClientLease, final RemoteCollection parent, final XmldbURI documentName, final String type, final byte[] content) throws XMLDBException {
        super(xmlRpcClientLease, parent, documentName, MimeType.BINARY_TYPE.getName());
        this.type = type;
        this.content = content;
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

        try {
            final Map result = (Map) xmlRpcClientLease.get().execute("getContentDigest", params);
            final String digestAlgorithm = (String)result.get("digest-algorithm");
            final byte[] digest = (byte[])result.get("digest");

            final MessageDigest messageDigest = new MessageDigest(DigestType.forCommonName(digestAlgorithm), digest);
            if (this.contentDigest == null) {
                this.contentDigest = messageDigest;
            }
            return messageDigest;

        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
        }
    }

    public void setContentDigest(final MessageDigest contentDigest) {
        this.contentDigest = contentDigest;
    }

    @Override
    @Nullable public void setProperties(final Properties properties) {
    }
}
