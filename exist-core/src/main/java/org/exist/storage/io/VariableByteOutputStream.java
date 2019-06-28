/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.io;

import java.io.IOException;
import java.io.OutputStream;

import org.exist.util.ByteArray;
import org.exist.util.FastByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A byte array output stream using VBE (Variable Byte Encoding).
 *
 * Note that the VBE scheme used by this class
 * does not offer any advantage for negative numbers, in fact
 * it requires significantly more storage for those; see the javadoc
 * on the appropriate encoding method for details.
 *
 * If support for negative numbers is desired then, the reader
 * should look to zig-zag encoding as used in the varint's of
 * Google's Protocol Buffers https://developers.google.com/protocol-buffers/docs/encoding#signed-integers
 * or Hadoop's VarInt encoding, see org.apache.hadoop.io.file.tfile.Utils#writeVInt(java.io.DataOutput, int).
 *
 * VBE is never an alternative to having advance knowledge of number
 * ranges and using fixed size byte arrays to represent them.
 *
 * Rather, for example, it is useful when you have an int that could be
 * in any range between 0 and {@link Integer#MAX_VALUE}, but is likely
 * less than 2,097,151, in that case you would save at least 1 byte for
 * each int value that is written to the output stream that is
 * less than 2,097,151.
 *
 * @author wolf
 */
public class VariableByteOutputStream extends OutputStream {

    private static final int MAX_BUFFER_SIZE = 65536;
    private FastByteBuffer buf;
    private final byte[] temp = new byte[5];
    
    public VariableByteOutputStream() {
        super();
        buf = new FastByteBuffer(9);
    }

    public VariableByteOutputStream(final int size) {
        super();
        buf = new FastByteBuffer(size);
    }

    public void clear() {
        if (buf.size() > MAX_BUFFER_SIZE) {
            buf = new FastByteBuffer(9);
        } else {
            buf.setLength(0);
        }
    }

    @Override
    public void close() throws IOException {
        buf = null;
    }

    public int size() {
        return buf.length();
    }

    @Override
    public void flush() throws IOException {
        //Nothing to do
    }

    public int position() {
        return buf.size();
    }

    public byte[] toByteArray() {
        final byte[] b = new byte[buf.size()];
        buf.copyTo(b, 0);
        return b;
    }

    public ByteArray data() {
        return buf;
    }

    @Override
    public void write(final int b) throws IOException {
        buf.append((byte) b);
    }

    @Override
    public void write(final byte[] b) {
        buf.append(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        buf.append(b, off, len);
    }

    public void write(final ByteArray b) {
        b.copyTo(buf);
    }

    public void writeByte(final byte b) {
        buf.append(b);
    }

    /**
     * Writes a VBE short to the output stream
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  {@link Short#MIN_VALUE} and -1, 5 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and {@link Short#MAX_VALUE}, 3 bytes
     *
     *  @param s the short to write
     */
    public void writeShort(int s) {
        while ((s & ~0177) != 0) {
            buf.append((byte) ((s & 0177) | 0200));
            s >>>= 7;
        }
        buf.append((byte) s);
    }

    /**
     * Writes a VBE int to the output stream
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  {@link Integer#MIN_VALUE} and -1, 5 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and 2097151, 3 bytes
     *  2097152 and 268435455, is 4 bytes
     *  268435456 and {@link Integer#MAX_VALUE}, 5 bytes
     *
     *  @param i the integer to write
     */
    public void writeInt(int i) {
        int count = 0;
        while ((i & ~0177) != 0) {
            temp[count++] = (byte) ((i & 0177) | 0200);
            i >>>= 7;
        }
        temp[count++] = (byte) i;
        buf.append(temp, 0, count);
    }

    public void writeFixedInt(final int i) {
        temp[0] = (byte) ( ( i >>> 0 ) & 0xff );
        temp[1] = (byte) ( ( i >>> 8 ) & 0xff );
        temp[2] = (byte) ( ( i >>> 16 ) & 0xff );
        temp[3] = (byte) ( ( i >>> 24 ) & 0xff );
        buf.append(temp, 0, 4);
    }
    
    public void writeFixedInt(final int position, final int i) {
        buf.set(position, (byte) ( ( i >>> 0 ) & 0xff ));
        buf.set(position + 1, (byte) ( ( i >>> 8 ) & 0xff ));
        buf.set(position + 2, (byte) ( ( i >>> 16 ) & 0xff ));
        buf.set(position + 3, (byte) ( ( i >>> 24 ) & 0xff ));
    }

    /**
     * Writes a VBE int to the output stream
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  {@link Integer#MIN_VALUE} and -1, 5 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and 2097151, 3 bytes
     *  2097152 and 268435455, is 4 bytes
     *  268435456 and {@link Integer#MAX_VALUE}, 5 bytes
     *
     *  @param position the position in the output buffer to write the integer
     *  @param i the integer to write
     */
    public void writeInt(int position, int i) {
        while ((i & ~0177) != 0) {
            buf.set(position++, (byte) ((i & 0177) | 0200));
            i >>>= 7;
        }
        buf.set(position, (byte) i);
    }

    /**
     * Writes a VBE long to the output stream
     *
     * The encoding scheme requires the following storage
     * for numbers between (inclusive):
     *
     *  {@link Long#MIN_VALUE} and -1, 10 bytes
     *  0 and 127, 1 byte
     *  128 and 16383, 2 bytes
     *  16384 and 2097151, 3 bytes
     *  2097152 and 268435455, is 4 bytes
     *  268435456 and 34359738367, 5 bytes
     *  34359738368 and 4398046511103, 6 bytes
     *  4398046511104 and 562949953421311, 7 bytes
     *  562949953421312 and 72057594037927935, 8 bytes
     *  72057594037927936 and 9223372036854775807, 9 bytes
     *  9223372036854775808 and {@link Long#MAX_VALUE}, 10 bytes
     *
     * @param l the long to write
     */
    public void writeLong(long l) {
        while ((l & ~0177) != 0) {
            buf.append((byte) ((l & 0177) | 0200));
            l >>>= 7;
        }
        buf.append((byte) l);
    }

    public void writeFixedLong(final long l) {
        buf.append((byte) ((l >>> 56) & 0xff));
        buf.append((byte) ((l >>> 48) & 0xff));
        buf.append((byte) ((l >>> 40) & 0xff));
        buf.append((byte) ((l >>> 32) & 0xff));
        buf.append((byte) ((l >>> 24) & 0xff));
        buf.append((byte) ((l >>> 16) & 0xff));
        buf.append((byte) ((l >>> 8) & 0xff));
        buf.append((byte) ((l >>> 0) & 0xff));
    }

    public void writeUTF(final String s) throws IOException {
        final byte[] data = s.getBytes(UTF_8);
        writeInt(data.length);
        write(data, 0, data.length);
    }
}
