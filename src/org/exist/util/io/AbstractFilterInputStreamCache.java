/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author zwobit
 */
public abstract class AbstractFilterInputStreamCache extends FilterInputStream implements FilterInputStreamCache {
    private int sharedReferenceCount = 0;
    
    private int srcOffset = 0;

    private final InputStream src;
    private boolean srcClosed = false;

    public AbstractFilterInputStreamCache(InputStream src) {
        super(src);
        this.src = src;
        incrementSharedReferences();

        //if src is CachingFilterInputStream also register there so it can keep track of stream which rely on cache
        if(src instanceof CachingFilterInputStream) {
            final FilterInputStreamCache otherCache = ((CachingFilterInputStream) src).getCache();
            otherCache.incrementSharedReferences();
        }
    }
        
    public int getSrcOffset() {
        return this.srcOffset;
    }

    public boolean isSrcClosed() {
        return srcClosed;
    }

    @Override
    public int available() throws IOException {
        if (this.srcClosed) {
            return 0;
        }

        return src.available() + getLength();
    }

    @Override
    /**
     * Closes the src InputStream and empties the cache
     */
    public void close() throws IOException {
        decrementSharedReferences();
        if(sharedReferenceCount <= 0) {
            if (!srcClosed) {
                try {
//                    if(src instanceof CachingFilterInputStream) {
//                        ((CachingFilterInputStream) src).decrementSharedReferences();
//                    } else {
//                        src.close();
//                    }
                    src.close();
                } finally {
                    srcClosed = true;
                }
            }
            this.invalidate(); //empty the cache
            FilterInputStreamCacheMonitor.getInstance().deregister(this); // deregister with the monitor
        }
    }

    @Override
    public int read() throws IOException {
        if (srcClosed) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        }

        final int data = src.read();
        if( data == FilterInputStreamCache.END_OF_STREAM) {
            return FilterInputStreamCache.END_OF_STREAM;
        }
        
        this.write(data);
        this.srcOffset++;
        return data;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (srcClosed) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        }

        int srcLen = src.read(b, off, len);

        if (srcLen == FilterInputStreamCache.END_OF_STREAM) {
            return FilterInputStreamCache.END_OF_STREAM;
        }

        this.write(b, off, srcLen);
        this.srcOffset += srcLen;
        return srcLen;
    }

    @Override
    public long skip(long n) throws IOException {
        if (srcClosed) {
            throw new IOException(FilterInputStreamCache.INPUTSTREAM_CLOSED);
        } else if (n < 1) {
            return 0;
        }

        if (srcOffset < n) {
            final byte skipped[] = new byte[(int) (n - srcOffset)];
            int srcLen = src.read(skipped);

            //have we reached the end of the stream?
            if (srcLen == FilterInputStreamCache.END_OF_STREAM) {
                return srcOffset;
            }

            //increase srcOffset due to the read operation above
            srcOffset += srcLen;

            //store data in cache
            this.write(skipped, 0, srcLen);

            return srcOffset;
        } else {

            final byte skipped[] = new byte[(int) n];  //TODO could overflow
            int actualLen = src.read(skipped);

            //increase srcOffset due to read operation above
            srcOffset += actualLen;

            //store data in the cache
            this.write(skipped, 0, actualLen);

            return actualLen;
        }
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readlimit) {
    }

    

    @Override
    public void reset() throws IOException {
        throw new IOException("reset() not supported.");
    }
    
    @Override
    public boolean srcIsFilterInputStreamCache() {
        return src instanceof CachingFilterInputStream;
    }

    @Override
    public void incrementSharedReferences() {
        sharedReferenceCount++;
    }

    @Override
    public void decrementSharedReferences() {
        sharedReferenceCount--;
    }
}
