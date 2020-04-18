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

import java.io.OutputStream;

/**
 * Base64 encoding OutputStream.
 *
 * Same as {@link org.apache.commons.codec.binary.Base64OutputStream#Base64OutputStream(OutputStream, boolean)}
 * but disables chunking of output by default by setting the {@code lineLength} to 0.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Base64OutputStream extends org.apache.commons.codec.binary.Base64OutputStream {

    public Base64OutputStream(final OutputStream out, final boolean doEncode) {
        super(out, doEncode, 0, null);
    }
}