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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An input stream which calculates a digest of the
 * data that is written.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class DigestOutputStream extends FilterOutputStream {

    private final StreamableDigest streamableDigest;

    /**
     * Creates an output stream filter which calculates a digest
     * as the underlying output stream is written.
     *
     * @param os the input stream
     * @param streamableDigest the streamable digest
     */
    public DigestOutputStream(final OutputStream os, final StreamableDigest streamableDigest) {
        super(os);
        this.streamableDigest = streamableDigest;
    }

    @Override
    public void write(final int b) throws IOException {
        out.write(b);
        streamableDigest.update((byte) (b & 0xFF));
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        out.write(b, off, len);
        streamableDigest.update(b, off, len);
    }

    public StreamableDigest getStreamableDigest() {
        return streamableDigest;
    }
}
