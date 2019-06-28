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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream which calculates a digest of the
 * data that is read.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class DigestInputStream extends FilterInputStream {

    private final StreamableDigest streamableDigest;

    /**
     * Creates an input stream filter which calculates a digest
     * as the underlying input stream is read.
     *
     * @param is the input stream
     * @param streamableDigest the streamable digest
     */
    public DigestInputStream(final InputStream is, final StreamableDigest streamableDigest) {
        super(is);
        this.streamableDigest = streamableDigest;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            streamableDigest.update((byte)(b & 0xFF));
        }
        return b;
    }

    @Override
    public int read(final byte[] buf, final int off, int len) throws IOException {
        len = in.read(buf, off, len);
        if (len != -1) {
            streamableDigest.update(buf, off, len);
        }
        return len;
    }

    @Override
    public long skip(final long n) throws IOException {
        final byte[] buf = new byte[512];
        long total = 0;
        while (total < n) {
            long len = n - total;
            len = read(buf, 0, len < buf.length ? (int)len : buf.length);
            if (len == -1) {
                return total;
            }
            total += len;
        }
        return total;
    }

    public StreamableDigest getStreamableDigest() {
        return streamableDigest;
    }
}
