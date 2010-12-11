package org.exist.util.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Cache implementation for CachingFilterInputStream
 * Backed by an in-memory byte array
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class MemoryFilterInputStreamCache implements FilterInputStreamCache {

    private ByteArrayOutputStream cache = new ByteArrayOutputStream();

    public MemoryFilterInputStreamCache() {
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        cache.write(b, off, len);
    }

    @Override
    public void write(int i) throws IOException {
        cache.write(i);
    }

    @Override
    public byte get(int off) {
        return cache.toByteArray()[off];
    }

    @Override
    public int getLength() {
        return cache.size();
    }

    @Override
    public void copyTo(int cacheOffset, byte[] b, int off, int len) {
        System.arraycopy(cache.toByteArray(), cacheOffset, b, off, len);
    }

    @Override
    public void invalidate() throws IOException {
        cache.close();
        cache = null;
    }
}