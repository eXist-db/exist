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
import org.exist.storage.BrokerPool;
import org.exist.storage.blob.BlobId;
import org.exist.storage.btree.Paged.Page;
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

    public BinaryDocument(final BrokerPool pool) {
        super(pool);
    }

    public BinaryDocument(final BrokerPool pool, final Collection collection, final XmldbURI fileURI) {
        super(pool, collection, fileURI);
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
     * @return
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

    @Override
    public void read(final VariableByteInput istream) throws IOException {
        setDocId(istream.readInt());
        setFileURI(XmldbURI.create(istream.readUTF()));

        final byte[] blobIdRaw = new byte[istream.readInt()];
        istream.read(blobIdRaw);
        this.blobId = new BlobId(blobIdRaw);

        getPermissions().read(istream);

        this.realSize = istream.readLong();
        final DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(getBrokerPool().getSymbols(), istream);
        setMetadata(metadata);
    }
}
