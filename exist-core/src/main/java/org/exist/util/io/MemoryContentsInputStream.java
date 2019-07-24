/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

import static java.lang.Math.min;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
final class MemoryContentsInputStream extends InputStream {

    /**
     * The maximum number of bytes to skip.
     */
    private static final int MAX_SKIP_SIZE = 2048;

    static final AtomicLongFieldUpdater<MemoryContentsInputStream> POSITION_UPDATER = AtomicLongFieldUpdater
            .newUpdater(MemoryContentsInputStream.class, "position");

    private final MemoryContents memoryContents;
    private byte[] singleByteBuffer;

    @SuppressWarnings("unused") // POSITION_UPDATER
    private volatile long position;

    MemoryContentsInputStream(MemoryContents memoryContents) {
        this.memoryContents = memoryContents;
        singleByteBuffer = new byte[1];
        POSITION_UPDATER.set(this, 0L);
    }

    @Override
    public int available() throws IOException {
        long available = this.memoryContents.size() - POSITION_UPDATER.get(this);
        if (available > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else if (available > 1L) {
            // introduce a subtle bug in code that assumes #available() returns
            // everything until the end
            return (int) (available - 1);
        } else {
            return (int) available;
        }
    }

    @Override
    public int read() throws IOException {
        synchronized (singleByteBuffer) {
            int read = this.read(singleByteBuffer);
            if (read < 1) {
                return read;
            } else {
                return singleByteBuffer[0] & 0xff;
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        boolean success = false;
        int read = 0;
        while (!success) {
            long positionBefore = POSITION_UPDATER.get(this);
            read = this.memoryContents.read(b, positionBefore, off, len);
            if (read < 1) {
                return read;
            }
            success = POSITION_UPDATER.compareAndSet(this, positionBefore, positionBefore + read);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long fileSize = this.memoryContents.size();
        long skipped = 0L;
        boolean success = false;
        while (!success) {
            long positionBefore = POSITION_UPDATER.get(this);
            // do not skip more than MAX_SKIP_SIZE
            // this intentionally introduces a subtle bug in code that doesn't check
            // for the return value of #skip
            skipped = min(min(n, fileSize - positionBefore), MAX_SKIP_SIZE);
            if (skipped < 0L) {
                // file size changed due to concurrent access
                fileSize = this.memoryContents.size();
                continue;
            }
            success = POSITION_UPDATER.compareAndSet(this, positionBefore, positionBefore + skipped);
        }
        return skipped;
    }

    // Java 9 method, has to compile under Java 1.7 so no @Override
    public long transferTo(OutputStream out) throws IOException {
        long positionBefore = POSITION_UPDATER.get(this);
        long written = this.memoryContents.transferTo(out, positionBefore);
        POSITION_UPDATER.set(this, this.memoryContents.size());
        return written;
    }

}
