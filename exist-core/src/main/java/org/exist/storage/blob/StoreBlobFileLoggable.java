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
import org.exist.storage.journal.LogException;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class StoreBlobFileLoggable extends AbstractBlobLoggable {
    private String stagedUuid;

    public StoreBlobFileLoggable(final long transactionId, final BlobId blobId, final String stagedUuid) {
        super(LOG_STORE_BLOB_FILE, transactionId, blobId);
        this.stagedUuid = stagedUuid;
    }

    public StoreBlobFileLoggable(final DBBroker broker, final long transactionId) {
        super(LOG_STORE_BLOB_FILE, broker, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
        super.write(out);
        final byte[] strUuidBytes = stagedUuid.getBytes(UTF_8);
        out.putInt(strUuidBytes.length);
        out.put(strUuidBytes);
    }

    @Override
    public void read(final ByteBuffer in) {
        super.read(in);
        final int strUuidBytesLen = in.getInt();
        final byte[] strUuidBytes = new byte[strUuidBytesLen];
        in.get(strUuidBytes);
        this.stagedUuid = new String(strUuidBytes, UTF_8);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 4 + stagedUuid.getBytes(UTF_8).length;
    }

    @Override
    public void redo() throws LogException {
        final BlobStore blobStore = broker.getBrokerPool().getBlobStore();
        blobStore.redo(this);
    }

    @Override
    public void undo() throws LogException {
        final BlobStore blobStore = broker.getBrokerPool().getBlobStore();
        blobStore.undo(this);
    }

    /**
     * Get the UUID of the staged file
     *
     * @return the UUID of the staged file
     */
    public String getStagedUuid() {
        return stagedUuid;
    }
}
