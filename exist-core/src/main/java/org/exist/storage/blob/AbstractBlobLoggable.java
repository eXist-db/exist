/*
 * Copyright (C) 2018 Adam Retter
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
