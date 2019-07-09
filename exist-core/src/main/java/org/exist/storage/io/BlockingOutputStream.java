/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: BlockingOutputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.storage.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream adapter for a BlockingInputStream.
 *
 * @author Chris Offerman
 */
public class BlockingOutputStream extends OutputStream {

    private BlockingInputStream bis;
    
    /** Create a new BlockingOutputStream adapter.
     *
     *@param stream  The BlockingInputStream to adapt.
     */
    public BlockingOutputStream(BlockingInputStream stream) {
        bis = stream;
    }

    /**
     * @return BlockingInputStream of this BlockingOutputStream.
     */
    public BlockingInputStream getInputStream() {
        return bis;
    }

    /**
     * Writes the specified byte to this output stream. The general 
     * contract for <code>write</code> is that one byte is written 
     * to the output stream. The byte to be written is the eight 
     * low-order bits of the argument <code>b</code>. The 24 
     * high-order bits of <code>b</code> are ignored.
     * 
     * 
     * @param b   the <code>byte</code>.
     * @throws IOException  if an I/O error occurs. In particular,
     *             an <code>ExistIOException</code> may be thrown if the 
     *             output stream has been closed.
     */
    @Override
    public void write(int b) throws IOException {
        bis.writeOutputStream(b);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array 
     * starting at offset <code>off</code> to this output stream. 
     * The general contract for <code>write(b, off, len)</code> is that 
     * some of the bytes in the array <code>b</code> are written to the 
     * output stream in order; element <code>b[off]</code> is the first 
     * byte written and <code>b[off+len-1]</code> is the last byte written 
     * by this operation.
     * 
     * 
     * @param b     the data.
     * @param off   the start offset in the data.
     * @param len   the number of bytes to write.
     * @throws IOException  if an I/O error occurs. In particular, 
     *             an <code>IOException</code> is thrown if the output 
     *             stream is closed.
     */
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        bis.writeOutputStream(b, off, len);
    }

    /**
     * Closes this output stream.
     * A closed stream cannot perform output operations and cannot be reopened.
     *
     * This method blocks its caller until the corresponding input stream is
     * closed or an exception occurs.
     * 
     * @throws IOException  if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        bis.closeOutputStream();
    }

    /**
     * Closes this output stream, specifying that an exception has occurred.
     * This will cause all consumer calls to be unblocked and throw an
     * IOException with this exception as its cause.
     * <code>BlockingInputStream</code> specific method.
     * @param ex the occurred exception
     * @throws IOException  if an I/O error occurs.
     */
    public void close(Exception ex) throws IOException {
        bis.closeOutputStream(ex);
    }

    /**
     * Flushes this output stream and forces any buffered output bytes 
     * to be written out.
     *
     * This methods blocks its caller until all buffered bytes are actually
     * read by the consuming threads.
     * 
     * 
     * @throws IOException  if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        bis.flushOutputStream();
    }
}
