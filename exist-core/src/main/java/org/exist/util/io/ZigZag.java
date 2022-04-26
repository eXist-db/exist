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

/**
 * ZigZag encoding and decoding.
 *
 * See Wikipedia: <a href="https://en.wikipedia.org/wiki/Variable-length_quantity#Zigzag_encoding">Zigzag encoding</a>.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ZigZag {

    /**
     * ZigZag encode the signed-int to an unsigned-int.
     *
     * @param i the signed-int to encode.
     *
     * @return the unsigned-int
     */
    public static int encode(final int i) {
        return (i << 1) ^ (i >> 31);
    }

    /**
     * ZigZag encode the signed-long to an unsigned-long.
     *
     * @param l the signed-long to encode.
     *
     * @return the unsigned-long
     */
    public static long encode(final long l) {
        return (l << 1) ^ (l >> 63);
    }

    /**
     * ZigZag decode the unsigned-int to a signed-int.
     *
     * @param i the unsigned-int to decode.
     *
     * @return the signed-int
     */
    public static int decode(final int i) {
        return (i >>> 1) ^ -(i & 1);
    }

    /**
     * ZigZag decode the unsigned-long to a signed-long.
     *
     * @param l the unsigned-long to decode.
     *
     * @return the signed-long
     */
    public static long decode(final long l) {
        return (l >>> 1) ^ -(l & 1);
    }
}
