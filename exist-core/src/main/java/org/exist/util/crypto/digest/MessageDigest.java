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

import java.util.Arrays;
import java.util.Objects;

import static org.exist.util.HexEncoder.bytesToHex;

/**
 * Message Digest.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Reter</a>
 */
public class MessageDigest {
    private final DigestType digestType;
    private final byte[] value;

    /**
     * @param digestType the type of the message digest
     * @param value the message digest value
     */
    public MessageDigest(final DigestType digestType, final byte[] value) {
        this.digestType = digestType;
        this.value = value;
    }

    /**
     * Get the message digest type.
     *
     * @return the message digest type.
     */
    public DigestType getDigestType() {
        return digestType;
    }

    /**
     * Get the message digest value.
     *
     * @return the message digest value.
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * Get the hex string of the message digest value.
     *
     * @return the hex string of the message digest value;
     */
    public String toHexString() {
        return bytesToHex(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageDigest that = (MessageDigest) o;
        return digestType == that.digestType &&
                Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(digestType);
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return digestType.getCommonNames()[0] + "{" + toHexString() + '}';
    }
}
