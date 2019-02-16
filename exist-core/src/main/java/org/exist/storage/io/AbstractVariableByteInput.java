/*
 * eXist Open Source Native XML Database Copyright (C) 2001-04, Wolfgang M.
 * Meier (meier@ifs.tu-darmstadt.de)
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
 * Abstract base class for implementations of VariableByteInput.
 * 
 * @author wolf
 */
public abstract class AbstractVariableByteInput implements VariableByteInput {

    @Override
    public byte readByte() throws IOException {
        final int i = read();
        if (i < 0) {throw new EOFException();}
        return (byte) i;
    }

    @Override
    public short readShort() throws IOException {
        byte b = readByte();
        short i = (short) (b & 0177);
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            b = readByte();
            i |= (b & 0177L) << shift;
        }
        return i;
    }

    @Override
    public int readInt() throws IOException {
        byte b = readByte();
        int i = b & 0177;
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            b = readByte();
            i |= (b & 0177L) << shift;
        }
        return i;
    }

    @Override
    public int readFixedInt() throws IOException {
        return ( readByte() & 0xff ) |
        ( ( readByte() & 0xff ) << 8 ) |
        ( ( readByte() & 0xff ) << 16 ) |
        ( ( readByte() & 0xff ) << 24 );
    }

    @Override
    public long readLong() throws IOException {
        byte b = readByte();
        long i = b & 0177;
        for (int shift = 7; (b & 0200) != 0; shift += 7) {
            b = readByte();
            i |= (b & 0177L) << shift;
        }
        return i;
    }

    @Override
    public String readUTF() throws IOException {
        final int len = readInt();
        final byte data[] = new byte[len];

        read(data);

        return new String(data, UTF_8);
    }

    @Override
    public void skip(final int count) throws IOException {
        for (int i = 0; i < count && available() > 0; i++) {
            while ((readByte() & 0200) > 0)
                ;
        }
    }

    @Override
    public void skipBytes(final long count) throws IOException {
        for(long i = 0; i < count; i++)
            readByte();
    }

    @Override
    public int read(final byte[] data) throws IOException {
        return read(data, 0, data.length);
    }

    @Override
    public int read(final byte b[], final int off, final int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0)
                || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) { return 0; }

        int c = read();
        if (c == -1) { return -1; }
        b[off] = (byte) c;

        int i = 1;
        try {
            for (; i < len; i++) {
                c = read();
                if (c == -1) {
                    break;
                }
                if (b != null) {
                    b[off + i] = (byte) c;
                }
            }
        } catch (final IOException ee) {
            //Nothing to do
        }
        return i;
    }

    public void copyTo(final VariableByteOutputStream os) throws IOException {
        int more;
        do {
            more = read();
            os.write((byte) more);
            more &= 0200;
        } while (more > 0);
    }

    @Override
    public void copyTo(final VariableByteOutputStream os, final int count)
            throws IOException {
        int more;
        for (int i = 0; i < count; i++) {
            do {
                more = read();
                os.write((byte)more);
                more &= 0200;
            } while (more > 0);
        }
    }

    @Override
    public void copyRaw(final VariableByteOutputStream os, final int count) throws IOException {
        final byte buf[] = new byte[count];
        int totalRead = 0;
        int read;
        while((read = read(buf, 0, count - totalRead)) > 0) {
            os.write(buf, 0, read);
            totalRead += read;
        }
    }

    public void release() {
        //Nothing to do
    }
}