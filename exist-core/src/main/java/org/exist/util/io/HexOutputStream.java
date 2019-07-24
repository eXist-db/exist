/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * Hexadecimal encoding OutputStream
 *
 * Based on org.apache.commons.codec.binary.Base64OutputStream
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class HexOutputStream extends FilterOutputStream {

    private final Hex hex = new Hex();
    private boolean doEncode = false;

    /**
     * Creates a HexOutputStream such that all data written is Hex-encoded to the original provided OutputStream.
     *
     * @param out the OutputStream to wrap.
     * @param doEncode true to encode
     */
    public HexOutputStream(final OutputStream out, final boolean doEncode) {
        super(out);
        this.doEncode = doEncode;
    }

    /**
     * Writes the specified <code>byte</code> to this output stream.
     *
     * @param i source byte
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(final int i) throws IOException {
        final byte singleByte[] = new byte[]{ (byte) i };
        write(singleByte, 0, 1);
    }

    /**
     * Writes <code>len</code> bytes from the specified <code>b</code> array starting at <code>offset</code> to this
     * output stream.
     *
     * @param b
     *            source byte array
     * @param offset
     *            where to start reading the bytes
     * @param len
     *            maximum number of bytes to write
     *
     * @throws IOException
     *             if an I/O error occurs.
     * @throws NullPointerException
     *             if the byte array parameter is null
     * @throws IndexOutOfBoundsException
     *             if offset, len or buffer size are invalid
     */
    @Override
    public void write(final byte b[], final int offset, final int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (offset < 0 || len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (offset > b.length || offset + len > b.length) {
            throw new IndexOutOfBoundsException();
        } else if (len > 0) {

            final byte data[] = new byte[len];
            System.arraycopy(b, offset, data, 0, len);
            write(data);
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }

        if(doEncode) {
            out.write(hex.encode(b));
        } else {
            try {
                out.write(hex.decode(b));
            } catch(final DecoderException de) {
                throw new IOException("Unable to decode: " + de.getMessage(), de);
            }
        }
    }
}
