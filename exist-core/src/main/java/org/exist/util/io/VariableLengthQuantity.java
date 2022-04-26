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
package org.exist.util.io;

import java.nio.ByteBuffer;

/**
 * Variable-length Quantity encoding and decoding.
 *
 * The encoding scheme for a number is as follows:
 *
 * The number is decomposed into one or more VLQ octets (8-bit bytes),
 * where 7-bits within each block are used for the number.
 *
 * In each VLQ octet written, the MSB (Most-Significant-Bit)
 * is the "sign bit", the remaining 7-bits are used for the number.
 * When the sign bit is set to 1 it indicates that another VLQ octet
 * follows, when it is set to 0 it indicates that it is the last VLQ octet.
 *
 * The VLQ octets are arranged most significant first in a stream.
 *
 * NOTE: This class is subject to a bug in the
 * JDK, see: <a href="https://github.com/adamretter/vbe-test/blob/main/README.md">vbe-test</a>,
 * and: <a href="https://bugs.openjdk.java.net/browse/JDK-8253191">JDK-8253191</a>.
 *
 * This encoding scheme is designed to be efficient for Unsigned Integers,
 * if you are using Signed Integers, then consider using {@link ZigZag} encoding/decoding
 * in front of this VLQ encoding/decoding.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class VariableLengthQuantity {

    /**
     * Writes an int as a variable-length quantity (VLQ).
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  -2147483648 and -1, 5 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and 2097151, 3 bytes
     *  2097152 and 268435455, is 4 bytes
     *  268435456 and 2147483647, 5 bytes
     *
     * @param buf the buffer to write to
     * @param i the integer to write
     */
    public static void writeInt(final ByteBuffer buf, int i) {
        while ((i & ~0x7F) != 0) {
            buf.put((byte) ((i & 0x7F) | 0x80));
            i >>>= 7;
        }
        buf.put((byte) i);
    }

    /**
     * Writes a long as a variable-length quantity (VLQ).
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  -9223372036854775808 and -1, 10 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and 2097151, 3 bytes
     *  2097152 and 268435455, is 4 bytes
     *  268435456 and 34359738367, 5 bytes
     *  34359738368 and 4398046511103, 6 bytes
     *  4398046511104 and 562949953421311, 7 bytes
     *  562949953421312 and 72057594037927935, 8 bytes
     *  72057594037927936 and 9223372036854775807, 9 bytes
     *
     * @param buf the buffer to write to
     * @param l the long to write
     */
    public static void writeLong(final ByteBuffer buf, long l) {
        while ((l & ~0x7FL) != 0L) {
            buf.put((byte) ((l & 0x7FL) | 0x80L));
            l >>>= 7;
        }
        buf.put((byte) l);
    }

    /**
     * Reads an int as a variable-length quantity (VLQ).
     *
     * @param buf the buffer to read from
     *
     * @return the read integer
     */
    public static int readInt(final ByteBuffer buf) {
        byte b = buf.get();
        int i = b & 0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            b = buf.get();
            i |= (b & 0x7F) << shift;
        }
        return i;
    }

    /**
     * Reads a long as a variable-length quantity (VLQ).
     *
     * @param buf the buffer to read from
     *
     * @return the read long
     */
    public static long readLong(final ByteBuffer buf) {
        byte b = buf.get();
        long l = b & 0x7F;
        for (int shift = 7; (b & 0x80) != 0; shift += 7) {
            b = buf.get();
            l |= (b & 0x7FL) << shift;
        }
        return l;
    }
}
