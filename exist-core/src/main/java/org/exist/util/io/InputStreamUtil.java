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

package org.exist.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class InputStreamUtil {

    /**
     * Default size of the buffer. 64 KB
     */
    public static final int DEFAULT_BUF_SIZE = 65536;   // 64KB

    /**
     * Copy the InputStream to the OutputStream.
     *
     * @param is the input stream
     * @param os the output stream
     *
     * @throws IOException if an I/O error occurs.
     */
    public static void copy(final InputStream is, final OutputStream os) throws IOException {
        copy(is, os, DEFAULT_BUF_SIZE);
    }

    /**
     * Copy the InputStream to the OutputStream.
     *
     * @param is the input stream
     * @param os the output stream
     * @param bufferSize the size of the in-memory buffer to use when copying.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static void copy(final InputStream is, final OutputStream os, final int bufferSize) throws IOException {
        int read;
        final byte buffer[] = new byte[bufferSize];
        while ((read = is.read(buffer)) > -1) {
            os.write(buffer, 0, read);
        }
    }

    /**
     * Read all bytes from the InputStream.
     *
     * @param is the input stream
     *
     * @return the bytes read from the input stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static byte[] readAll(final InputStream is) throws IOException {
        try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
            os.write(is);
            return os.toByteArray();
        }
    }

    /**
     * Read all bytes from the InputStream into a String.
     *
     * @param is the input stream
     * @param charset the charset encoding of the byte string.
     *
     * @return the String read from the input stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static String readString(final InputStream is, final Charset charset) throws IOException {
        try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
            os.write(is);
            return new String(os.toByteArray(), charset);
        }
    }
}
