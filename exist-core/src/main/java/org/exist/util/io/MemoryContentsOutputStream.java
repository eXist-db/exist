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

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
final class MemoryContentsOutputStream extends OutputStream {
    private final byte[] singleByteBuffer = new byte[1];
    private final MemoryContents memoryContents;

    MemoryContentsOutputStream(MemoryContents memoryContents) {
        this.memoryContents = memoryContents;
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (singleByteBuffer) {
            singleByteBuffer[0] = (byte) b;
            write(singleByteBuffer, 0, 1);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.memoryContents.writeAtEnd(b, off, len);
    }
}
