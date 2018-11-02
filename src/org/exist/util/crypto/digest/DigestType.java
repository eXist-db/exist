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

import java.util.function.Supplier;

/**
 * An enumeration of message digest types
 * used by eXist-db.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public enum DigestType {
//    MD_5(       (byte)0x1,  128),
//
//    SHA_1(      (byte)0x8,  128),
//    SHA_256(    (byte)0x9,  256),
//
//    RIPEMD_160( (byte)0x18, 160),
//    RIPEMD_256( (byte)0x19, 256),

    BLAKE_256(  (byte)0x32, 256, Blake256StreamableDigest::new);

    private final byte id;
    private final int bits;
    private final Supplier<StreamableDigest> streamableFactory;

    DigestType(final byte id, final int bits, final Supplier<StreamableDigest> streamableFactory) {
        this.id = id;
        this.bits = bits;
        this.streamableFactory = streamableFactory;
    }

    /**
     * Get the id of the message digest.
     *
     * @return the id of the message digest
     */
    public byte getId() {
        return id;
    }

    /**
     * Get the digest type by id.
     *
     * @param id the id of the digest type
     *
     * @throws IllegalArgumentException if the id is invalid.
     */
    public static DigestType forId(final byte id) {
        for (final DigestType digestType : values()) {
            if (id == digestType.getId()) {
                return digestType;
            }
        }
        throw new IllegalArgumentException("Unknown digest type id: " + id);
    }

    /***
     * The length of the generated message digest
     *
     * @return the message digest length in bits
     */
    public int getDigestLength() {
        return bits;
    }

    /***
     * The length of the generated message digest
     *
     * @return the message digest length in bytes
     */
    public int getDigestLengthBytes() {
        return bits / 8;
    }

    public StreamableDigest newStreamableDigest() {
        return streamableFactory.get();
    }
}
