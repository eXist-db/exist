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
public interface MemoryContents {

    /**
     * Returns the amount of bytes held in the memory.
     *
     * @return the total amount of data
     */
    long size();

    /**
     * Reads the from the memory data into the given {@code dst} buffer.
     *
     * @param dst      the destination data buffer to write to
     * @param position the position to start
     * @param off      the offset within the target destination buffer
     * @param len      the amount of bytes to write at maximum
     * @return the amount of data written into the target buffer
     * @throws IOException if the write of data failed
     */
    int read(byte[] dst, long position, int off, int len) throws IOException;

    /**
     * Writes the from the given {@code src} data buffer array into the memory.
     *
     * @param src the source data buffer to read from
     * @param off the offset within the source data buffer
     * @param len the total amount of bytes to read
     * @return the amount of actually read bytes from the buffer
     * @throws IOException if the read of data failed
     */
    int writeAtEnd(byte[] src, int off, int len) throws IOException;

    /**
     * Writes the from the given {@code src} data buffer array into the memory.
     *
     * @param src      the source data buffer to read from
     * @param position the position within the memory to start at
     * @param off      the offset within the source data buffer
     * @param len      the total amount of bytes to read
     * @return the amount of actually read bytes from the buffer
     * @throws IOException if the read of data failed
     */
    int write(byte[] src, long position, int off, int len) throws IOException;

    /**
     * Writes all available data from memory to the given {@code target} stream,
     * starting at the {@code position}.
     *
     * @param target   the target stream to write to
     * @param position the position to start read from
     * @return the amount of bytes written to the target
     * @throws IOException if the write of data failed
     */
    long transferTo(OutputStream target, long position) throws IOException;

    /**
     * Resets the memory contents to have the same state as it would be if newly constructed.
     */
    void reset();
}
