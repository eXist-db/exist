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

    public BinaryDocument(final BrokerPool pool) {
        super(pool);
    }

    /**
     * Creates a new persistent binary Document instance.
     *
     * @param pool The broker pool
     * @param collection The Collection which holds this document
     * @param fileURI The name of the document
     */
    public BinaryDocument(final BrokerPool pool, final Collection collection, final XmldbURI fileURI) {
        super(pool, collection, fileURI);
    }

    /**
     * Creates a new persistent binary Document instance to replace an existing document instance.
     *
     * @param prevDoc The previous binary Document object that we are overwriting
     */
    public BinaryDocument(final DocumentImpl prevDoc) {
        super(prevDoc);
    }

    /**
     * Creates a new persistent binary Document instance to replace an existing document instance.
     *
     * @param collection The Collection which holds this document
     * @param prevDoc The previous Document object that we are overwriting
     * @param prevDoc The previous binary Document object that we are overwriting
     */
    public BinaryDocument(final BrokerPool pool, final Collection collection, final Collection.CollectionEntry prevDoc) {
        super(pool, collection, prevDoc);
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

    @Override
    public void read(final VariableByteInput istream) throws IOException {
        setDocId(istream.readInt());
        setFileURI(XmldbURI.create(istream.readUTF()));
        this.pageNr = istream.readLong();

        getPermissions().read(istream);

        this.realSize = istream.readLong();
        final DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(getBrokerPool().getSymbols(), istream);
        setMetadata(metadata);
    }
}
