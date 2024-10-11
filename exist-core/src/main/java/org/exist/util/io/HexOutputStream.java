/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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

import org.apache.commons.codec.CodecPolicy;

import java.io.OutputStream;

/**
 * Hexadecimal encoding OutputStream.
 *
 * Same as {@link org.apache.commons.codec.binary.Base16OutputStream#Base16OutputStream(OutputStream, boolean)}
 * but uses lower-case and a strict policy by default.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class HexOutputStream extends org.apache.commons.codec.binary.Base16OutputStream {

    /**
     * Creates a HexOutputStream such that all data written is Hex-encoded to the original provided OutputStream.
     *
     * @param out the OutputStream to wrap.
     * @param doEncode true to encode.
     */
    public HexOutputStream(final OutputStream out, final boolean doEncode) {
        this(out, doEncode, true);
    }

    /**
     * Creates a HexOutputStream such that all data written is Hex-encoded to the original provided OutputStream.
     *
     * @param out the OutputStream to wrap.
     * @param doEncode true to encode.
     * @param lowerCase true to use lower case, or false for upper case.
     */
    public HexOutputStream(final OutputStream out, final boolean doEncode, final boolean lowerCase) {
        super(out, doEncode, lowerCase, CodecPolicy.STRICT);
    }
}
