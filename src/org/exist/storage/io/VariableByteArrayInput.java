/*
 * eXist Open Source Native XML Database Copyright (C) 2001, Wolfgang M. Meier
 * (meier@ifs.tu-darmstadt.de)
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.storage.io;

import java.io.EOFException;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implements VariableByteInput on top of a byte array.
 * 
 * @author wolf
 */
public class VariableByteArrayInput extends AbstractVariableByteInput {

    private byte[] data;
    protected int position;
    private int end;

    public VariableByteArrayInput() {
        super();
    }

    public VariableByteArrayInput(final byte[] data) {
        super();
        this.data = data;
        this.position = 0;
        this.end = data.length;
    }

    public VariableByteArrayInput(final byte[] data, final int offset, final int length) {
        super();
        this.data = data;
        this.position = offset;
        this.end = offset + length;
    }

    public void initialize(final byte[] data, final int offset, final int length) {
        this.data = data;
        this.position = offset;
        this.end = offset + length;
    }

    @Override
    public byte readByte() throws IOException {
        if (position == end) {
            throw new EOFException();
        }
        return data[position++];
    }

    @Override
    public int read() throws IOException {
        if (position == end) {
            return -1;
        }
        return data[position++] & 0xFF;
    }

    @Override
    public int available() throws IOException {
        return end - position;
    }

    @Override
    public short readShort() throws IOException {
        if (position == end) {
            throw new EOFException();
        }
        byte b = data[position++];
        short i = (short) (b & 0177);
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            if (position == end) {
                throw new EOFException();
            }
            b = data[position++];
            i |= (b & 0177) << shift;
        }
        return i;
    }

    @Override
    public int readInt() throws IOException {
        if (position == end) {
            throw new EOFException();
        }
        byte b = data[position++];
        int i = b & 0177;
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            if (position == end) {
                throw new EOFException();
            }
            b = data[position++];
            i |= (b & 0177) << shift;
        }
        return i;
    }

    @Override
    public int readFixedInt() throws IOException {
        return ( data[position++] & 0xff ) |
            ( ( data[position++] & 0xff ) << 8 ) |
            ( ( data[position++] & 0xff ) << 16 ) |
            ( ( data[position++] & 0xff ) << 24 );
    }

    @Override
    public long readLong() throws IOException {
        if (position == end) {
            throw new EOFException();
        }
        byte b = data[position++];
        long i = b & 0177L;
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            if (position == end) {
                throw new EOFException();
            }
            b = data[position++];
            i |= (b & 0177L) << shift;
        }
        return i;
    }

    @Override
    public String readUTF() throws IOException {
        final int len = readInt();
        final String s = new String(data, position, len, UTF_8);
        position += len;
    	return s;
    }

    @Override
    public void copyTo(final VariableByteOutputStream os, final int count) throws IOException {
        byte more;
        for (int i = 0; i < count; i++) {
            do {
                more = data[position++];
                os.write(more);
            } while ((more & 0x200) > 0);
        }
    }

    @Override
    public void copyRaw(final VariableByteOutputStream os, final int count) throws IOException {
        os.write(data, position, count);
        position += count;
    }

    @Override
    public void skip(final int count) throws IOException {
        for (int i = 0; i < count; i++) {
            while (position < end && (data[position++] & 0200) > 0) {
                //Nothing to do
            }
        }
    }

    @Override
    public void skipBytes(final long count) throws IOException {
        for(long i = 0; i < count && position < end; i++) {
            position++;
        }
    }

    public String toString(final int len) {
        final byte[] subArray = new byte[len];
        System.arraycopy(data, position, subArray, 0, len);
        final StringBuilder buf = new StringBuilder("[");
        for (int i = 0 ; i < len; i++) {
            if (i > 0) {
                buf.append(" ");
            }
            buf.append(subArray[i]);
        }
        buf.append("]");
        return buf.toString();
    }
}