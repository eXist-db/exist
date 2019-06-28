/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.util.crypto.digest;

/**
 * Interface for a Streamable Digest implementation.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface StreamableDigest {

    /**
     * Update the message digest calculation with more data.
     *
     * @param b the data
     */
    void update(final byte b);

    /**
     * Update the message digest calculation with more data.
     *
     * @param buf the data
     * @param offset the position in the {@code buf} to start reading from
     * @param len the number of bytes to read from the {@code offset}
     */
    void update(final byte[] buf, final int offset, final int len);

    /**
     * Updates the message digest calculation with more data.
     *
     * @param buf the data
     */
    default void update(final byte[] buf) {
        update(buf, 0, buf.length);
    }

    /**
     * Gets the type of the message digest
     *
     * @return the type of the message digest
     */
    DigestType getDigestType();

    /**
     * Gets the current message digest.
     *
     * NOTE this does not produce a copy of the digest,
     * calls to {@link #reset()} or {@code #update} will
     * modify the returned value!
     *
     * @return the message digest
     */
    byte[] getMessageDigest();

    /**
     * Gets the current message digest as a {@code Message Digest}.
     *
     * The underlying byte array will be copied.
     *
     * @return a copy of the message digest.
     */
    MessageDigest copyMessageDigest();

    /**
     * Reset the digest function so that it can be reused
     * for a new stream.
     */
    void reset();
}
