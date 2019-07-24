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

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface for Cache Implementations for use by the CachingFilterInputStream
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author <a href="tobi.krebsATgmail.com">Tobi Krebs</a>
 * @version 1.1
 */
public interface FilterInputStreamCache extends Closeable {

    public final static int END_OF_STREAM = -1;
    public final static String INPUTSTREAM_CLOSED = "The underlying InputStream has been closed";

    //TODO ensure that FilterInputStreamCache implementations are enforced thread-safe
    /**
     * Writes len bytes from the specified byte array starting at offset off to
     * the cache. The general contract for write(b, off, len) is that some of
     * the bytes in the array b are written to the output stream in order;
     * element b[off] is the first byte written and b[off+len-1] is the last
     * byte written by this operation.
     *
     * If b is null, a NullPointerException is thrown.
     *
     * If off is negative, or len is negative, or off+len is greater than the
     * length of the array b, then an IndexOutOfBoundsException is thrown.
     *
     * @param b the data.
     * @param off the start offset in the data.
     * @param len - the number of bytes to write.
     *
     * @throws IOException - if an I/O error occurs. In particular, an
     * IOException is thrown if the cache is invalidated.
     */
    public void write(byte b[], int off, int len) throws IOException;

    /**
     * Writes the specified byte to the cache. The general contract for write is
     * that one byte is written to the cache.
     *
     * @param i - the byte.
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException
     * may be thrown if cache is invalidated.
     */
    public void write(int i) throws IOException;

    /**
     * Gets the length of the cache
     *
     * @return The length of the cache
     */
    public int getLength();

    /**
     * Retrieves the byte at offset off from the cache
     *
     * @param off The offset to read from
     * @return The byte read from the offset
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException
     * may be thrown if cache is invalidated.
     */
    public byte get(int off) throws IOException;

    /**
     * Copies data from the cache to a buffer
     *
     * @param cacheOffset The offset in the cache to start copying data from
     * @param b The buffer to write to
     * @param off The offset in the buffer b at which to start writing
     * @param len The length of data to copy
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException
     * may be thrown if cache is invalidated.
     */
    public void copyTo(int cacheOffset, byte b[], int off, int len) throws IOException;

    /**
     * Invalidates the cache
     *
     * Destroys the cache and releases any underlying resources
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException
     * may be thrown if cache is already invalidated.
     */
    public void invalidate() throws IOException;

    public int available() throws IOException;

    public void mark(int readlimit);

    public boolean markSupported();

    public abstract int read() throws IOException;

    public int read(byte[] b) throws IOException;

    public int read(byte[] b, int off, int len) throws IOException;

    public void reset() throws IOException;

    public long skip(long n) throws IOException;

    public int getSrcOffset();

    public boolean isSrcClosed();
    
    public boolean srcIsFilterInputStreamCache();

    /**
     * Increments the number of shared references to the cache.
     */
    void incrementSharedReferences();

    /**
     * Decrements the number of shared references to the cache.
     */
    void decrementSharedReferences();
}
