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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public enum DigestType {
    MD_2((byte)0x01, 128, MD2StreamableDigest::new, "MD2"),
    MD_4((byte)0x02, 128, MD4StreamableDigest::new, "MD4"),
    MD_5((byte)0x03, 128, MD5StreamableDigest::new, "MD5"),

    SHA_1(  (byte)0x10,  160, SHA1StreamableDigest::new, "SHA-1"),
    SHA_256((byte)0x11,  256, SHA256StreamableDigest::new, "SHA-256"),
    SHA_512((byte)0x12, 512, SHA512StreamableDigest::new, "SHA-512"),

    RIPEMD_160((byte)0x20, 160, RIPEMD160StreamableDigest::new, "RIPEMD-160", "RIPEMD160"),
    RIPEMD_256((byte)0x21, 256, RIPEMD256StreamableDigest::new, "RIPEMD-256", "RIPEMD256"),

    BLAKE_160((byte)0x30, 160, Blake160StreamableDigest::new, "BLAKE2B-160", "BLAKE-160"),
    BLAKE_256((byte)0x31, 256, Blake256StreamableDigest::new, "BLAKE2B-256", "BLAKE-256"),
    BLAKE_512((byte)0x31, 512, Blake512StreamableDigest::new, "BLAKE2B-512", "BLAKE-512");


    private final byte id;
    private final int bits;
    private final Supplier<StreamableDigest> streamableFactory;
    private final String[] commonNames;

    DigestType(final byte id, final int bits, final Supplier<StreamableDigest> streamableFactory, final String... commonNames) {
        this.id = id;
        this.bits = bits;
        this.streamableFactory = streamableFactory;
        this.commonNames = commonNames;
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
     * @return the digest type
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

    /**
     * Get the common names for the digest type.
     *
     * @return the common names.
     */
    public String[] getCommonNames() {
        return commonNames;
    }

    /**
     * Get the digest type by common name.
     *
     * @param commonName the common name of the digest type
     *
     * @return the digest type
     *
     * @throws IllegalArgumentException if the common name is invalid.
     */
    public static DigestType forCommonName(final String commonName) {
        for (final DigestType digestType : values()) {
            for (final String cn : digestType.commonNames) {
                if (cn.equals(commonName)) {
                    return digestType;
                }
            }
        }

        throw new IllegalArgumentException("Unknown digest type common name: " + commonName);
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
