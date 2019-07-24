/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage.io;

import java.io.EOFException;
import java.io.IOException;

/**
 * Interface for reading variable byte encoded values.
 * 
 * Variable byte encoding offers a good compression ratio if the stored
 * values are rather small, i.e. much smaller than the possible maximum for
 * the given type.
 * 
 * @author wolf
 */
public interface VariableByteInput {

    /**
     * Read a single byte and return as an int value.
     * 
     * @return the byte value as int or -1 if no more bytes are available.
     * @throws IOException in case of an I/O error
     */
    public int read() throws IOException;

    /**
     * Fill the provided byte array with data from the input.
     * 
     * @param data the buffer to read
     * @throws IOException in case of an I/O error
     * @return the number of bytes read
     */
    public int read(byte[] data) throws IOException;
    
    public int read(byte b[], int off, int len) throws IOException;
    
    /**
     * Returns a value &gt; 0 if more bytes can be read
     * from the input.
     *
     * @throws IOException in case of an I/O error
     * @return the number of bytes available
     */
    public int available() throws IOException;
    
    /**
     * Read a single byte. Throws EOFException if no
     * more bytes are available.
     *
     * @throws IOException in case of an I/O error
     * @return the byte read
     */
    public byte readByte() throws IOException;

    /**
     * Read a short value in variable byte encoding.
     *
     * @throws IOException in case of an I/O error
     * @return the short read
     */
    public short readShort() throws IOException;

    /**
     * Read an integer value in variable byte encoding.
     *
     * @throws IOException in case of an I/O error
     * @return the int read
     */
    public int readInt() throws IOException;

    public int readFixedInt() throws IOException;
    
    /**
     * Read a long value in variable byte encoding.
     *
     * @throws IOException in case of an I/O error
     * @return the long read
     */
    public long readLong() throws IOException;

    public String readUTF() throws IOException, EOFException;

    /**
     * Read the following count numeric values from the input
     * and drop them.
     * 
     * @param count the number of bytes to skip
     * @throws IOException in case of an I/O error
     */
    public void skip(int count) throws IOException;

    public void skipBytes(long count) throws IOException;
    
    /**
     * Copy the next numeric value from the input to the
     * specified output stream.
     * 
     * @param os the output stream to copy the data to
     * @throws IOException in case of an I/O error
     */
    public void copyTo(VariableByteOutputStream os) throws IOException;

    /**
     * Copy the count next numeric values from the input to
     * the specified output stream.
     * 
     * @param os the output stream to copy the data to
     * @param count the number of bytes to copy
     * @throws IOException in case of an I/O error
     */
    public void copyTo(VariableByteOutputStream os, int count)
            throws IOException;
    
    public void copyRaw(VariableByteOutputStream os, int bytes)
    	throws IOException;
}