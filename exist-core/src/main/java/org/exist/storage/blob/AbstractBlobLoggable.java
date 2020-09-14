/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage.blob;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.AbstractLoggable;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractBlobLoggable extends AbstractLoggable implements BlobLoggable {
    protected DBBroker broker;
    private BlobId blobId;

    public AbstractBlobLoggable(final byte type, final long transactionId, final BlobId blobId) {
        super(type, transactionId);
        this.blobId = blobId;
    }

    public AbstractBlobLoggable(final byte type, final DBBroker broker, final long transactionId) {
        super(type, transactionId);
        this.broker = broker;
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt(blobId.getId().length);
        out.put(blobId.getId());
    }

    @Override
    public void read(final ByteBuffer in) {
        final int idLen = in.getInt();
        final byte[] id = new byte[idLen];
        in.get(id);
        this.blobId = new BlobId(id);
    }

    @Override
    public int getLogSize() {
        return 4 + blobId.getId().length;
    }

    /**
     * Get the Blob id
     *
     * @return the blob id
     */
    public BlobId getBlobId() {
        return blobId;
    }
}
