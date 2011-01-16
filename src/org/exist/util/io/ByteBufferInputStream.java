package org.exist.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of an InputStream which reads from a ByteBuffer
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ByteBufferInputStream extends InputStream {

    private final ByteBufferAccessor bufAccessor;
    private boolean closed = false;
    private final static int END_OF_STREAM = -1;

    public ByteBufferInputStream(ByteBufferAccessor bufAccessor) {
        this.bufAccessor = bufAccessor;
    }

    @Override
    public int available() throws IOException {
        int available = 0;

        if(!closed) {
            available = bufAccessor.getBuffer().capacity() - bufAccessor.getBuffer().position();
        }

        return available;
    }
    
    @Override
    public int read() throws IOException {
        isClosed();
        
        if(available() == 0) {
            return END_OF_STREAM;
        }

        return bufAccessor.getBuffer().get();
    }

    @Override
    public int read(byte[] b) throws IOException {
        isClosed();

        if(available() == 0) {
            return END_OF_STREAM;
        } else if(b.length > available()) {
            return read(b, 0, available());
        } else {
            return bufAccessor.getBuffer().get(b).position();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        isClosed();

        if(available() == 0) {
            return END_OF_STREAM;
        } else if(b.length > available()) {
            len = available();
        }

        return bufAccessor.getBuffer().get(b, off, len).position();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int i) {
        bufAccessor.getBuffer().mark();
    }

    @Override
    public synchronized void reset() throws IOException {
        bufAccessor.getBuffer().reset();
    }

    @Override
    public long skip(long l) throws IOException {

        if(l > available()) {
            l = available();
        }

        long newPosition = bufAccessor.getBuffer().position();
        newPosition += l;
        try {
            bufAccessor.getBuffer().position((int)newPosition);
        } catch(IllegalArgumentException iae) {
            throw new IOException("Unable to skip " + l + " bytes", iae);
        }

        return l;
    }

    @Override
    public void close() throws IOException {

        isClosed();

        bufAccessor.getBuffer().clear();
        closed = true;
    }

    private void isClosed() throws IOException {
        if(closed) {
            throw new IOException("The stream was previously closed");
        }
    }

}