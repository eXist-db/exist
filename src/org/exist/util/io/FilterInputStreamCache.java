package org.exist.util.io;

import java.io.IOException;

/**
 * Interface for Cache Implementations for use by the CachingFilterInputStream
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 * @version 1.0
 */
public interface FilterInputStreamCache {

    /**
     * Writes len bytes from the specified byte array starting at offset off to the cache.
     * The general contract for write(b, off, len) is that some of the bytes in the array b
     * are written to the output stream in order; element b[off] is the first byte written
     * and b[off+len-1] is the last byte written by this operation.
     *
     * If b is null, a NullPointerException is thrown.
     *
     * If off is negative, or len is negative, or off+len is greater than the length of the array b, then an IndexOutOfBoundsException is thrown.
     *
     * @param b the data.
     * @param off the start offset in the data.
     * @param len - the number of bytes to write.
     *
     * @throws IOException - if an I/O error occurs. In particular, an IOException is thrown if the cache is invalidated.
     */
    public void write(byte b[], int off, int len) throws IOException;

    /**
     * Writes the specified byte to the cache.
     * The general contract for write is that one byte is written to the cache.
     *
     * @param i - the byte.
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException may be thrown if cache is invalidated.
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
     * @throws IOException if an I/O error occurs. In particular, an IOException may be thrown if cache is invalidated.
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
     * @throws IOException if an I/O error occurs. In particular, an IOException may be thrown if cache is invalidated.
     */
    public void copyTo(int cacheOffset, byte b[], int off, int len) throws IOException;

    /**
     * Invalidates the cache
     *
     * Destroys the cache and releases any underlying resources
     *
     * @throws IOException if an I/O error occurs. In particular, an IOException may be thrown if cache is already invalidated.
     */
    public void invalidate() throws IOException;
}