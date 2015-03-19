package org.exist.storage.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ByteArray;
import org.exist.util.FastByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A byte array output stream using variable byte encoding.
 * 
 * @author wolf
 */
public class VariableByteOutputStream extends OutputStream {

    private static final int MAX_BUFFER_SIZE = 65536;
    protected FastByteBuffer buf;	
    private final byte[] temp = new byte[5];
    
    private static final Logger LOG = LogManager.getLogger(VariableByteArrayInput.class);
    
    public VariableByteOutputStream() {
        super();
        buf = new FastByteBuffer(9);
    }

    public VariableByteOutputStream(int size) {
        super();
        buf = new FastByteBuffer(size);
    }

    public void clear() {
        if (buf.size() > MAX_BUFFER_SIZE)
            {buf = new FastByteBuffer(9);}
        else
            {buf.setLength(0);}
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
    public void write(int b) throws IOException {
        buf.append((byte) b);
    }

    @Override
    public void write(byte[] b) {
        buf.append(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buf.append(b, off, len);
    }

    public void write(ByteArray b) {
        b.copyTo(buf);
    }

    public void writeByte(byte b) {
        buf.append(b);
    }

    public void writeShort(int s) {
        while ((s & ~0177) != 0) {
            buf.append((byte) ((s & 0177) | 0200));
            s >>>= 7;
        }
        buf.append((byte) s);
    }

    public void writeInt(int i) {
        int count = 0;
        while ((i & ~0177) != 0) {
            temp[count++] = (byte) ((i & 0177) | 0200);
            i >>>= 7;
        }
        temp[count++] = (byte) i;
        buf.append(temp, 0, count);
    }

    public void writeFixedInt(int i) {
        temp[0] = (byte) ( ( i >>> 0 ) & 0xff );
        temp[1] = (byte) ( ( i >>> 8 ) & 0xff );
        temp[2] = (byte) ( ( i >>> 16 ) & 0xff );
        temp[3] = (byte) ( ( i >>> 24 ) & 0xff );
        buf.append(temp, 0, 4);
    }
    
    public void writeFixedInt(int position, int i) {
        buf.set(position, (byte) ( ( i >>> 0 ) & 0xff ));
        buf.set(position + 1, (byte) ( ( i >>> 8 ) & 0xff ));
        buf.set(position + 2, (byte) ( ( i >>> 16 ) & 0xff ));
        buf.set(position + 3, (byte) ( ( i >>> 24 ) & 0xff ));
    }
    
    public void writeInt(int position, int i) {
        while ((i & ~0177) != 0) {
            buf.set(position++, (byte) ((i & 0177) | 0200));
            i >>>= 7;
        }
        buf.set(position, (byte) i);
    }

    public void writeLong(long l) {
        while ((l & ~0177) != 0) {
            buf.append((byte) ((l & 0177) | 0200));
            l >>>= 7;
        }
        buf.append((byte) l);
    }

    public void writeFixedLong(long l) {
        buf.append((byte) ((l >>> 56) & 0xff));
        buf.append((byte) ((l >>> 48) & 0xff));
        buf.append((byte) ((l >>> 40) & 0xff));
        buf.append((byte) ((l >>> 32) & 0xff));
        buf.append((byte) ((l >>> 24) & 0xff));
        buf.append((byte) ((l >>> 16) & 0xff));
        buf.append((byte) ((l >>> 8) & 0xff));
        buf.append((byte) ((l >>> 0) & 0xff));
    }

    public void writeUTF(String s) throws IOException {
        byte[] data = s.getBytes(UTF_8);

        writeInt(data.length);
        write(data, 0, data.length);
    }
}
