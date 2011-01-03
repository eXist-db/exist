package org.exist.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of an Input Stream Filter that extends
 * any InputStream with mark() and reset() capabilities
 * by caching the read data for later re-reading.
 *
 * NOTE - Only supports reading data up to 2GB as the cache index uses an 'int' index
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class CachingFilterInputStream extends FilterInputStream {

    private final static int END_OF_STREAM = -1;
    private final static String INPUTSTREAM_CLOSED = "The underlying InputStream has been closed";

    private boolean srcClosed = false;
    private int srcOffset = 0;
    private int mark = 0;
    private boolean useCache = false;
    private int cacheOffset = 0;
    private final InputStream src;
    private final FilterInputStreamCache cache;

    /**
     * @param cache The cache implementation
     * @param src The source InputStream to cache reads for
     */
    public CachingFilterInputStream(FilterInputStreamCache cache, InputStream src) {
        super(src);
        this.src = src;
        this.cache = cache;
    }

    /**
     * Gets the cache implementation
     */
    private FilterInputStreamCache getCache() {
        return cache;
    }

    @Override
    public int available() throws IOException {

        int available = 0;

        if(!srcClosed) {

            available = src.available();

            if(useCache && cacheOffset < srcOffset) {
                available += getCache().getLength() - cacheOffset;
            }
        }

        return available;
    }



    @Override
    public synchronized void mark(int readLimit) {
        mark = srcOffset;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void reset() throws IOException {
        useCache = true;
        cacheOffset = mark;
    }

    @Override
    public int read() throws IOException {

        if(srcClosed) {
            throw new IOException(INPUTSTREAM_CLOSED);
        }

        if(useCache && cacheOffset < srcOffset) {
            int data = getCache().get(cacheOffset++);

            //are we outside the cache
            if(cacheOffset >= srcOffset) {
                useCache = false;
            }
            return data;

        } else {
            int data = src.read();

            //have we reached the end of the stream?
            if(data == END_OF_STREAM) {
                return END_OF_STREAM;
            }

            //increment srcOffset due to read operation above
            srcOffset++;
            
            //store data in cache
            getCache().write(data);

            return data;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        if(srcClosed) {
            throw new IOException(INPUTSTREAM_CLOSED);
        }

        if(useCache && cacheOffset < srcOffset) {

            //copy data from the cache
            int actualLen = (len > getCache().getLength() - cacheOffset ? getCache().getLength() - cacheOffset : len);
            getCache().copyTo(cacheOffset, b, off, actualLen);
            cacheOffset += actualLen;

            //if the requested bytes were more than what is present in the cache, then also read from the src
            if(actualLen < len) {
                useCache = false;
                int srcLen = src.read(b, off + actualLen, len - actualLen);

                //have we reached the end of the stream?
                if(srcLen == END_OF_STREAM) {
                    return actualLen;
                }

                //increase srcOffset due to the read opertaion above
                srcOffset += srcLen;

                //store data in cache
                getCache().write(b, off + actualLen, srcLen);

                actualLen += srcLen;
            }

            return actualLen;

        } else {
            int actualLen = src.read(b, off, len);

            //have we reached the end of the stream?
            if(actualLen == END_OF_STREAM) {
                return actualLen;
            }

            //increase srcOffset due to read operation above
            srcOffset += actualLen;

            //store data in cache
            getCache().write(b, off, actualLen);

            return actualLen;
        }
    }

    /**
     * Closes the src InputStream and empties the cache
     */
    @Override
    public void close() throws IOException {
        if(!srcClosed) {
            try {
                src.close();
            } finally {
                srcClosed = true;
            }
        }
        getCache().invalidate(); //empty the cache
    }


    /**
     * We cant actually skip as we need to read so that we can cache the data,
     * however apart from the potentially increased I/O
     * and Memory, the end result is the same
     */
    @Override
    public long skip(long n) throws IOException {

        if(srcClosed) {
            throw new IOException(INPUTSTREAM_CLOSED);
        } else if(n < 1) {
            return 0;
        }

        if(useCache && cacheOffset < srcOffset) {

            //skip data from the cache
            long actualLen = (n > getCache().getLength() - cacheOffset ? getCache().getLength() - cacheOffset : n);
            cacheOffset += actualLen;

            //if the requested bytes were more than what is present in the cache, then also read from the src
            if(actualLen < n) {
                useCache = false;
                
                byte skipped[] = new byte[(int)(n - actualLen)];
                int srcLen = src.read(skipped);

                //have we reached the end of the stream?
                if(srcLen == END_OF_STREAM) {
                    return actualLen;
                }

                //increase srcOffset due to the read opertaion above
                srcOffset += srcLen;

                //store data in cache
                getCache().write(skipped, 0, srcLen);
                
                actualLen += srcLen;
            }
            return actualLen;

        } else {

            byte skipped[] = new byte[(int)n];  //TODO could overflow
            int actualLen = src.read(skipped);

            //increase srcOffset due to read operation above
            srcOffset += actualLen;

            //store data in the cache
            getCache().write(skipped, 0, actualLen);

            return actualLen;
        }
    }
}