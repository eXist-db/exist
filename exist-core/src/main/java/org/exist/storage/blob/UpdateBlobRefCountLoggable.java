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

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class UpdateBlobRefCountLoggable extends AbstractBlobLoggable {

    private Integer currentCount;
    private Integer newCount;

    public UpdateBlobRefCountLoggable(final long transactionId, final BlobId blobId, final int currentCount, final int newCount) {
        super(LOG_UPDATE_BLOB_REF_COUNT, transactionId, blobId);
        this.currentCount = currentCount;
        this.newCount = newCount;
    }

    public UpdateBlobRefCountLoggable(final DBBroker broker, final long transactionId) {
        super(LOG_UPDATE_BLOB_REF_COUNT, broker, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
        super.write(out);
        out.putInt(currentCount);
        out.putInt(newCount);
    }

    @Override
    public void read(final ByteBuffer in) {
        super.read(in);
        currentCount = in.getInt();
        newCount = in.getInt();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 4 + 4;
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
     * Get the current count
     *
     * @return the current count
     */
    @Nullable public Integer getCurrentCount() {
        return currentCount;
    }

    /**
     * Get the new count
     *
     * @return the new count
     */
    @Nullable public Integer getNewCount() {
        return newCount;
    }
}
