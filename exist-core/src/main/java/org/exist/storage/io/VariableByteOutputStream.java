/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.io;

import java.io.IOException;
import java.io.OutputStream;

import org.exist.util.ByteArray;
import org.exist.util.FixedByteArray;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

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
 * @author <a href="mailto:adam@evolvedbinary.com>Adam Retter</a>
 */
public class VariableByteOutputStream extends OutputStream {

    /**
     * The choice of the backing buffer is quite tricky, we have two easy options:
     *
     * 1. org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream
     *    This allocates multiple underlying buffers in sequence, which means that appends to the buffer always allocate
     *    a new buffer, and so there is no GC overhead for appending. However, for serialization #toArray() involves
     *    allocating a new array and copying data from those multiple buffers into the new array, this requires 2x
     *    memory.
     *    NOTE: Previously this classes {@link VariableByteOutputStream#toByteArray()} made a copy anyway, and so
     *    previously required 2x memory.
     *
     * 2. it.unimi.dsi.fastutil.io.UnsynchronizedByteArrayOutputStream
     *    This allocates a single underlying buffer, appends that
     *    would overflow the underlying buffer cause a new buffer to be allocated, data copied, and the old buffer left
     *    to GC. This means that appends which require resizing the buffer can be expensive. However, #toArray() is not
     *    needed as access to the underlying array is permitted, so this is very cheap for serializing.
     *
     * Likely there are different scenarios where each is more appropriate.
     */
    private final UnsynchronizedByteArrayOutputStream buf;
    
    public VariableByteOutputStream() {
        super();
         buf = new UnsynchronizedByteArrayOutputStream(512);
    }

    public VariableByteOutputStream(final int bytes) {
        super();
        buf = new UnsynchronizedByteArrayOutputStream(bytes);
    }

    public void clear() {
        buf.reset();
    }

    @Override
    public void close() throws IOException {
        buf.close();
    }

    public int size() {
        return buf.size();
    }

    @Override
    public void flush() throws IOException {
        buf.flush();
    }

    public byte[] toByteArray() {
        return buf.toByteArray();
    }

    public ByteArray data() {
        return new FixedByteArray(buf.toByteArray());
    }

    @Override
    public void write(final int b) throws IOException {
        buf.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        buf.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        buf.write(b, off, len);
    }

//    public void write(final ByteArray b) {
//        b.copyTo(buf);
//    }

    public void writeByte(final byte b) {
        buf.write(b);
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
            buf.write((byte) ((s & 0177) | 0200));
            s >>>= 7;
        }
        buf.write((byte) s);
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
        while ((i & ~0177) != 0) {
            buf.write((byte) ((i & 0177) | 0200));
            i >>>= 7;
        }
        buf.write((byte) i);
    }

    public void writeFixedInt(final int i) {
        buf.write((byte) ( ( i >>> 0 ) & 0xff ));
        buf.write((byte) ( ( i >>> 8 ) & 0xff ));
        buf.write((byte) ( ( i >>> 16 ) & 0xff ));
        buf.write((byte) ( ( i >>> 24 ) & 0xff ));
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
            buf.write((byte) ((l & 0177) | 0200));
            l >>>= 7;
        }
        buf.write((byte) l);
    }

    public void writeFixedLong(final long l) {
        buf.write((byte) ((l >>> 56) & 0xff));
        buf.write((byte) ((l >>> 48) & 0xff));
        buf.write((byte) ((l >>> 40) & 0xff));
        buf.write((byte) ((l >>> 32) & 0xff));
        buf.write((byte) ((l >>> 24) & 0xff));
        buf.write((byte) ((l >>> 16) & 0xff));
        buf.write((byte) ((l >>> 8) & 0xff));
        buf.write((byte) ((l >>> 0) & 0xff));
    }

    public void writeUTF(final String s) throws IOException {
        final byte[] data = s.getBytes(UTF_8);
        writeInt(data.length);
        write(data, 0, data.length);
    }
}
