/*
 * Copyright (c) 2012, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Adam Retter Consulting nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of an InputStream which reads from a ByteBuffer
 *
 * @version 1.0
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ByteBufferInputStream extends InputStream {

    private final ByteBufferAccessor bufAccessor;
    private boolean closed = false;
    private final static int END_OF_STREAM = -1;

    public ByteBufferInputStream(final ByteBufferAccessor bufAccessor) {
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
    public int read(final byte[] b) throws IOException {
        isClosed();

        if(available() == 0) {
            return END_OF_STREAM;
        } else if(b.length > available()) {
            return read(b, 0, available());
        } else {
            final int currentPosition = bufAccessor.getBuffer().position();
            return bufAccessor.getBuffer().get(b).position() - currentPosition;
        }
    }

    @Override
    public int read(final byte[] b, final int off, int len) throws IOException {
        isClosed();

        if(available() == 0) {
            return END_OF_STREAM;
        }
        
        if(len > available()) {
            len = available();
        }

        final int currentPosition = bufAccessor.getBuffer().position();
        return bufAccessor.getBuffer().get(b, off, len).position() - currentPosition;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(final int i) {
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
        } catch(final IllegalArgumentException iae) {
            throw new IOException("Unable to skip " + l + " bytes", iae);
        }

        return l;
    }

    @Override
    public void close() throws IOException {

        isClosed();

        ((java.nio.Buffer) bufAccessor.getBuffer()).clear();
        closed = true;
    }

    private void isClosed() throws IOException {
        if(closed) {
            throw new IOException("The stream was previously closed");
        }
    }

}