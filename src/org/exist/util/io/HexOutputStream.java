package org.exist.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.codec.binary.Hex;

/**
 * Based on org.apache.commons.codec.binary.Base64OutputStream
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class HexOutputStream extends FilterOutputStream {

    private final Hex hex = new Hex();

    /**
     * Creates a HexOutputStream such that all data written is Hex-encoded to the original provided OutputStream.
     *
     * @param out
     *            OutputStream to wrap.
     */
    public HexOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Writes the specified <code>byte</code> to this output stream.
     *
     * @param i
     *            source byte
     * @throws IOException
     *             if an I/O error occurs.
     */
    @Override
    public void write(int i) throws IOException {
        byte singleByte[] = new byte[]{ (byte) i };
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
    public void write(byte b[], int offset, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (offset < 0 || len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (offset > b.length || offset + len > b.length) {
            throw new IndexOutOfBoundsException();
        } else if (len > 0) {

            byte data[] = new byte[len];
            System.arraycopy(b, offset, data, 0, len);
            write(data);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }

        out.write(hex.encode(b));
    }
}
