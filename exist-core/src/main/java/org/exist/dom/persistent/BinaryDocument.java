/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.blob.BlobId;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xmldb.XmldbURI;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Represents a binary resource. Binary resources are just stored
 * as binary data in a single overflow page. However, class BinaryDocument
 * extends {@link org.exist.dom.persistent.DocumentImpl} and thus provides the
 * same interface.
 *
 * @author wolf
 */
public class BinaryDocument extends DocumentImpl {
    private BlobId blobId;
    private long realSize = 0L;

    /**
     * Creates a new persistent binary Document instance.
     *
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     */
    public BinaryDocument(final BrokerPool pool, final Collection collection, final int docId, final XmldbURI fileURI) {
        super(pool, collection, docId, fileURI);
    }

    /**
     * Creates a new persistent binary Document instance to replace an existing document instance.
     *
     * @param docId the id of the document
     * @param prevDoc The previous binary Document object that we are overwriting
     */
    public BinaryDocument(final int docId, final DocumentImpl prevDoc) {
        super(docId, prevDoc);
    }

    /**
     * Creates a new persistent binary Document instance.
     *
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param docId the id of the document
     * @param fileURI The name of the document
     * @param blobId the id of the blob in the blob store
     * @param permissions the permissions of the document
     * @param realSize the real size of the binary document
     * @param metadata the metadata for the document
     */
    private BinaryDocument(final BrokerPool pool, final Collection collection, final int docId, final XmldbURI fileURI,
            final BlobId blobId, final Permission permissions, final long realSize, final DocumentMetadata metadata) {
        super(pool, collection, docId, fileURI, permissions, 0, null, metadata);
        this.blobId = blobId;
        this.realSize = realSize;
    }

    @Override
    public byte getResourceType() {
        return BINARY_FILE;
    }

    @Override
    public long getContentLength() {
        return realSize;
    }

    public void setContentLength(final long length) {
        this.realSize = length;
    }

    /**
     * Get the Blob Store id for the
     * content of this document.
     *
     * @return the Blob Store id for the content of this document.
     */
    @Nullable public BlobId getBlobId() {
        return blobId;
    }

    public void setBlobId(final BlobId blobId) {
        this.blobId = blobId;
    }

    @Override
    public void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeInt(getDocId());
        ostream.writeUTF(getFileURI().toString());

        ostream.writeInt(blobId.getId().length);
        ostream.write(blobId.getId());

        getPermissions().write(ostream);

        ostream.writeLong(realSize);
        getMetadata().write(getBrokerPool().getSymbols(), ostream);
    }

    /**
     * Deserialize the document object from bytes.
     *
     * @param pool the database
     * @param istream the byte stream to read
     *
     * @return the document object.
     * @throws IOException in case of an I/O error
     */
    public static BinaryDocument read(final BrokerPool pool, final VariableByteInput istream) throws IOException {
        final int docId = istream.readInt();
        final XmldbURI fileURI = XmldbURI.create(istream.readUTF());

        final byte[] blobIdRaw = new byte[istream.readInt()];
        istream.read(blobIdRaw);
        final BlobId blobId = new BlobId(blobIdRaw);

        final Permission permissions = PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager());
        permissions.read(istream);

        final long realSize = istream.readLong();

        final DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(pool.getSymbols(), istream);

        return new BinaryDocument(pool, null, docId, fileURI, blobId, permissions, realSize, metadata);
    }
}
