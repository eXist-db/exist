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

import java.io.IOException;
import java.io.InputStream;

/**
 * Cache implementation for CachingFilterInputStream Backed by an in-memory byte
 * array
 *
 * @version 1.1
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author <a href="tobi.krebsATgmail.com">Tobi Krebs</a>
 */
public class MemoryFilterInputStreamCache extends AbstractFilterInputStreamCache {

    private FastByteArrayOutputStream cache = new FastByteArrayOutputStream();

    public MemoryFilterInputStreamCache(InputStream src) {
        super(src);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        cache.write(b, off, len);
    }

    @Override
    public void write(final int i) throws IOException {
        cache.write(i);
    }

    @Override
    public byte get(final int off) {
        return cache.toByteArray()[off];
    }

    @Override
    public int getLength() {
        return cache.size();
    }

    @Override
    public void copyTo(final int cacheOffset, final byte[] b, final int off, final int len) {
        System.arraycopy(cache.toByteArray(), cacheOffset, b, off, len);
    }

    @Override
    public void invalidate() throws IOException {
        if (cache != null) {
            cache.close();
            cache = null;
        }
    }

    /**
     * Updates to the cache are not reflected in the underlying input stream
     */
    //TODO refactor this so that updates to the cache are reflected
    /*@Override
     public InputStream getIndependentInputStream() {
     return new ByteArrayInputStream(cache.toByteArray());
     }*/
}
