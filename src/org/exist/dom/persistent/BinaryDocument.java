/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.security.Permission;
import org.exist.security.SimpleACLPermission;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.Paged.Page;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xmldb.XmldbURI;

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

    private long pageNr = Page.NO_PAGE;
    private long realSize = 0L;

    public BinaryDocument(final BrokerPool pool, final int id, final Collection collection, final XmldbURI fileURI) {
        super(pool, id, collection, fileURI);
    }

    private BinaryDocument(
        final BrokerPool db,
        final int id,
        final XmldbURI fileURI,
        final Permission permissions,
        final DocumentMetadata metadata,
        long pageNr,
        long realSize
    ) {
        super(db, id, fileURI, permissions, metadata);

        this.pageNr = pageNr;
        this.realSize = realSize;
    }

    @Override
    public byte getResourceType() {
        return BINARY_FILE;
    }

    public void setPage(final long page) {
        this.pageNr = page;
    }

    public long getPage() {
        return pageNr;
    }

    @Override
    public long getContentLength() {
        return realSize;
    }

    public void setContentLength(final long length) {
        this.realSize = length;
    }

    @Override
    public void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeInt(getDocId());
        ostream.writeUTF(getFileURI().toString());
        ostream.writeLong(pageNr);

        getPermissions().write(ostream);

        ostream.writeLong(realSize);
        getMetadata().write(getBrokerPool().getSymbols(), ostream);
    }

    public static BinaryDocument read(final BrokerPool db, final VariableByteInput stream) throws IOException {
        int docId = stream.readInt();
        XmldbURI fileURI = XmldbURI.createInternal(stream.readUTF());

        long pageNr = stream.readLong();

        SimpleACLPermission permissions = SimpleACLPermission.read(db.getSecurityManager(), stream);

        long realSize = stream.readLong();

        DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(db.getSymbols(), stream);

        return new BinaryDocument(db, docId, fileURI, permissions, metadata, pageNr, realSize);
    }
}
