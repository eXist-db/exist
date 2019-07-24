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

import java.util.Arrays;

import static org.exist.util.HexEncoder.bytesToHex;

/**
 * Identifier for a BLOB.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public final class BlobId {
    private final byte[] id;

    /**
     * @param id the raw identifier
     */
    public BlobId(final byte[] id) {
        this.id = id;
    }

    /**
     * Gets the raw identifier.
     *
     * @return the raw identifier.
     */
    public byte[] getId() {
        return id;
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BlobId blobId = (BlobId) o;
        return Arrays.equals(id, blobId.id);
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public String toString() {
        return bytesToHex(id);
    }
}
