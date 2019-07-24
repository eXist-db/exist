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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;

public class Compressor {

    /**
     * Compress the byte array using GZip compression.
     *
     * GZip compression has some overhead for headers etc,
     * so it does not make sense to use this with perfectly
     * compressible buffers smaller than 23 bytes.
     * In reality buffers are unlikely to be perfectly compressible,
     * so you likely want to only use it with large buffers.
     *
     * @param buf the data to compress.
     *
     * @return the compressed data.
     *
     * @throws IOException if an error occurs
     */
    public static byte[] compress(final byte[] buf) throws IOException {
        return compress(buf, buf.length);
    }
    
    /**
     * Compress the byte array using GZip compression.
     *
     * GZip compression has some overhead for headers etc,
     * so it does not make sense to use this with perfectly
     * compressible buffers smaller than 23 bytes.
     * In reality buffers are unlikely to be perfectly compressible,
     * so you likely want to only use it with large buffers.
     *
     * @param buf the data to compress.
     * @param len the number of bytes from buf to compress.
     *
     * @return the compressed data.
     *
     * @throws IOException if an error occurs
     */
    public static byte[] compress(final byte[] buf, final int len) throws IOException {
        try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream(len);
                final GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(buf, 0, len);
            gzos.finish();
            return baos.toByteArray();
        }
    }

    /**
     * Uncompress the byte array using GZip compression.
     *
     * @param buf the data to uncompress.
     *
     * @return the uncompressed data.
     *
     * @throws IOException if an error occurs
     */
    public static byte[] uncompress(final byte[] buf) throws IOException {
        try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
            uncompress(buf, baos);
            return baos.toByteArray();
        }
    }

    /**
     * Uncompress the byte array using GZip compression.
     *
     * @param buf the data to uncompress.
     * @param os the destination for the uncompressed data;
     *
     * @return the number of bytes uncompressed
     *
     * @throws IOException if an error occurs
     */
    public static int uncompress(final byte[] buf, final OutputStream os) throws IOException {
        int written = 0;
        try (final FastByteArrayInputStream bais = new FastByteArrayInputStream(buf);
             final InputStream gzis = new GZIPInputStream(bais)) {
            final byte[] tmp = new byte[4096];
            int read;
            while ((read = gzis.read(tmp)) != -1) {
                os.write(tmp, 0, read);
                written += read;
            }
        }
        return written;
    }
}
