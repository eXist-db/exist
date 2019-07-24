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

import net.jcip.annotations.NotThreadSafe;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of an Input Stream Filter that extends any InputStream with
 * mark() and reset() capabilities by caching the read data for later
 * re-reading.
 *
 * NOTE - Only supports reading data up to 2GB as the cache index uses an 'int'
 * index
 *
 * @version 1.1
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author <a href="tobi.krebsATgmail.com">Tobi Krebs</a>
 */
@NotThreadSafe
public class CachingFilterInputStream extends FilterInputStream {

    //TODO what about if the underlying stream supports marking
    //then we could just use its capabilities?
    private final FilterInputStreamCache cache;

    private int srcOffset = 0;
    private int mark = 0;

    /**
     * Constructor which uses an existing Cache from a CachingFilterInputStream,
     * if inputStream is a CachingFilterInputStream.
     *
     * @param inputStream the input stream
     *
     * @throws InstantiationException if the construction fails
     */
    public CachingFilterInputStream(InputStream inputStream) throws InstantiationException {
        super(null);

        if (inputStream instanceof CachingFilterInputStream) {
            this.cache = ((CachingFilterInputStream) inputStream).shareCache();     // must be #shareCache not #getCache() to increment references
        } else {
            throw new InstantiationException("Only CachingFilterInputStream are supported as InputStream");
        }
    }

    public CachingFilterInputStream(final FilterInputStreamCache cache) {
        super(null);
        this.cache = cache;
    }

    /**
     * Gets the cache implementation directly.
     */
    FilterInputStreamCache getCache() {
        return cache;
    }

    /**
     * Gets the cache implementation for
     * sharing with another source. This is done
     * by incrementing its shared reference count.
     *
     * @return the cache implementation
     */
    FilterInputStreamCache shareCache() {
        cache.incrementSharedReferences();
        return cache;
    }

    @Override
    public int available() throws IOException {
        return getCache().available() - srcOffset;
    }

    @Override
    public synchronized void mark(final int readLimit) {
        mark = srcOffset;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void reset() throws IOException {
        srcOffset = mark;
    }

    @Override
    public int read() throws IOException {

        if (getCache().isSrcClosed()) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        }

        //Read from cache
        if (useCache()) {
            final int data = getCache().get(srcOffset++);
            return data;
        } else {
            final int data = getCache().read();
            
            if(data == FileFilterInputStreamCache.END_OF_STREAM) {
                return FilterInputStreamCache.END_OF_STREAM;
            }
            
            srcOffset++;
            return data;
        }
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {

        if (getCache().isSrcClosed()) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        }

        if (useCache()) {

            //copy data from the cache
            int actualLen = (len > getCache().getLength() - this.srcOffset ? getCache().getLength() - this.srcOffset : len);
            getCache().copyTo(this.srcOffset, b, off, actualLen);
            this.srcOffset += actualLen;

            //if the requested bytes were more than what is present in the cache, then also read from the src
            if (actualLen < len) {
                int srcLen = getCache().read(b, off + actualLen, len - actualLen);

                //have we reached the end of the stream?
                if (srcLen == FilterInputStreamCache.END_OF_STREAM) {
                    return actualLen;
                }

                //increase srcOffset due to the read opertaion above
                srcOffset += srcLen;

                actualLen += srcLen;
            }

            return actualLen;

        } else {
            int actualLen = getCache().read(b, off, len);

            //have we reached the end of the stream?
            if (actualLen == FilterInputStreamCache.END_OF_STREAM) {
                return actualLen;
            }

            //increase srcOffset due to read operation above
            srcOffset += actualLen;

            return actualLen;
        }
    }

    public boolean isClosed() {
        return getCache().isSrcClosed();
    }

    /**
     * Closes the src InputStream and empties the cache
     */
    @Override
    public void close() throws IOException {
        if(!getCache().isSrcClosed()) {
            getCache().close();
        }    
    }

    /**
     * Determine the current offset
     *
     * @return The current offset of this stream
     */
    public int offset() {
        return srcOffset;
    }

    /**
     * Similar to {@link #skip(long)} but travels backwards
     *
     * @param len The number of bytes to skip backwards
     *
     * @return The actual number of bytes skipped backwards
     */
    public long skipBackwards(final long len) {
        if(len == 0) {
            return 0;
        }

        // can only skip back to zero
        final long actualLen = Math.min(srcOffset, len);

        srcOffset = srcOffset - (int)actualLen;

        return actualLen;
    }

    /**
     * We cant actually skip as we need to read so that we can cache the data,
     * however apart from the potentially increased I/O and Memory, the end
     * result is the same
     */
    @Override
    public long skip(final long len) throws IOException {

        if (getCache().isSrcClosed()) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        } else if (len < 1) {
            return 0;
        }

        if (useCache()) {

            //skip data from the cache
            long actualLen = (len > getCache().getLength() - this.srcOffset ? getCache().getLength() - this.srcOffset : len);

            //if the requested bytes were more than what is present in the cache, then also read from the src
            if (actualLen < len) {

                // we can't skip directly on the src otherwise it will never be read into the cache, so we read over the amount of bytes we want to skip instead

                final int toReadFromSrc = (int) (len - actualLen);
                final byte skipped[] = new byte[toReadFromSrc];

                //read some data from the source (and into the cache)
                int toRead = toReadFromSrc;
                while(toRead > 0) {
                    final int read = getCache().read(skipped, 0, toRead);

                    //have we reached the end of the stream?
                    if(read == FilterInputStreamCache.END_OF_STREAM) {
                        break;
                    }

                    toRead -= read;
                    actualLen += read;
                }
            }

            //increase srcOffset due to the read operation above
            srcOffset += (int)actualLen;

            return actualLen;

        } else {

            final byte skipped[] = new byte[(int) len];  //TODO could overflow
            int toRead = (int)len;

            int totalRead = 0;
            while(toRead > 0) {
                final int read = getCache().read(skipped, 0, toRead);

                //have we reached the end of the stream?
                if(read == FilterInputStreamCache.END_OF_STREAM) {
                    break;
                }

                toRead -= read;
                totalRead += read;
            }

            //increase srcOffset due to read operation above
            srcOffset += totalRead;

            return totalRead;
        }
    }

    private boolean useCache() {
        //If cache hasRead and srcOffset is still in cache useCache
        return getCache().getSrcOffset() > 0 && getCache().getLength() > srcOffset;
    }

    /**
     * Increments the number of shared references to the cache.
     */
    public void incrementSharedReferences() {
        getCache().incrementSharedReferences();
    }

    /**
     * Decrements the number of shared references to the cache.
     */
    public void decrementSharedReferences() {
        getCache().decrementSharedReferences();
    }
}
