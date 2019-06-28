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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
final class OverflowToDiskStream extends OutputStream {
    private static final Logger LOG = LogManager.getLogger(OverflowToDiskStream.class);

    private final int inMemorySize;
    private final OutputStreamSupplier overflowStreamSupplier;

    private long count;
    private byte[] singleByteBuffer;
    private MemoryContents memoryContents;
    private OutputStream overflowOutputStream;

    OverflowToDiskStream(int inMemorySize, MemoryContents memoryContents, OutputStreamSupplier overflowStreamSupplier) {
        this.inMemorySize = inMemorySize;
        this.memoryContents = memoryContents;
        this.overflowStreamSupplier = overflowStreamSupplier;
    }

    private OutputStream switchToOverflow() throws IOException {
        if (overflowOutputStream == null) {
            overflowOutputStream = overflowStreamSupplier.get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing in memory buffered {} bytes to overflow stream", Long.valueOf(count));
            }
            memoryContents.transferTo(overflowOutputStream, 0);
            memoryContents.reset();
        }
        return overflowOutputStream;
    }

    @Override
    public void write(int b) throws IOException {
        if (overflowOutputStream != null) {
            /*
             * if we have a overflow do no more checks
             */
            overflowOutputStream.write(b);
            return;
        }
        if (count >= inMemorySize) {
            switchToOverflow().write(b);
            count++;
            return;
        }
        if (singleByteBuffer == null) {
            singleByteBuffer = new byte[1];
        }
        singleByteBuffer[0] = (byte) b;
        count += memoryContents.writeAtEnd(singleByteBuffer, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (overflowOutputStream != null) {
            /*
             * if we have a overflow do no more checks
             */
            overflowOutputStream.write(b, off, len);
            count += len;
            return;
        }
        if (len >= inMemorySize) {
            /*
             * if the request length exceeds the size of the output buffer, flush the output
             * buffer and then write the data directly. In this way buffered streams will
             * cascade harmlessly.
             */
            switchToOverflow().write(b, off, len);
            return;
        }
        if (len > inMemorySize - count) {
            switchToOverflow().write(b, off, len);
            count += len;
            return;
        }
        count += memoryContents.writeAtEnd(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        if (overflowOutputStream != null) {
            overflowOutputStream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (overflowOutputStream != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing overflow stream after writing {} bytes", Long.valueOf(count));
            }
            overflowOutputStream.close();
        }
    }
}