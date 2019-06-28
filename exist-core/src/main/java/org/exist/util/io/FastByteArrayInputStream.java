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

import net.jcip.annotations.NotThreadSafe;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static java.lang.Math.min;

/**
 * This is a replacement for {@link java.io.ByteArrayInputStream}
 * which removes the synchronization overhead for non-concurrent
 * access; as such this class is not thread-safe.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class FastByteArrayInputStream extends InputStream {

    public static final int END_OF_STREAM = -1;

    /**
     * The underlying data buffer.
     */
    private final byte[] data;

    /**
     * End Of Data.
     *
     * Similar to data.length,
     * i.e. the last readable offset + 1.
     */
    private final int eod;

    /**
     * Current offset in the data buffer.
     */
    private int offset;

    /**
     * The current mark (if any).
     */
    private int markedOffset;

    public FastByteArrayInputStream(final byte[] data) {
        Objects.requireNonNull(data);
        this.data = data;
        this.offset = 0;
        this.eod = data.length;
        this.markedOffset = this.offset;
    }

    public FastByteArrayInputStream(final byte[] data, final int offset) {
        Objects.requireNonNull(data);
        if(offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        this.data = data;
        this.offset = min(offset, data.length > 0 ? data.length: offset);
        this.eod = data.length;
        this.markedOffset = this.offset;
    }

    public FastByteArrayInputStream(final byte[] data, final int offset, final int length) {
        Objects.requireNonNull(data);
        if(offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if(length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        this.data = data;
        this.offset = min(offset, data.length > 0 ? data.length : offset);
        this.eod = min(this.offset + length, data.length);
        this.markedOffset = this.offset;
    }

    @Override
    public int available() {
        return offset < eod ? eod - offset : 0;
    }

    @Override
    public int read() throws IOException {
        return offset < eod ? data[offset++] & 0xff : END_OF_STREAM;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        Objects.requireNonNull(b);
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }

        if (offset >= eod) {
            return END_OF_STREAM;
        }

        int actualLen = eod - offset;
        if (len < actualLen) {
            actualLen = len;
        }
        if (actualLen <= 0) {
            return 0;
        }
        System.arraycopy(data, offset, b, off, actualLen);
        offset += actualLen;
        return actualLen;
    }

    @Override
    public long skip(final long n) {
        if(n < 0) {
            throw new IllegalArgumentException("Skipping backward is not supported");
        }

        long actualSkip = eod - offset;
        if (n < actualSkip) {
            actualSkip = n;
        }

        offset += actualSkip;
        return actualSkip;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(final int readlimit) {
        this.markedOffset = this.offset;
    }

    @Override
    public synchronized void reset() {
        this.offset = this.markedOffset;
    }
}
