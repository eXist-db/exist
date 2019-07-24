/*
Copyright (c) 2015, Adam Retter
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
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Cache implementation for CachingFilterInputStream Backed by a Memory Mapped
 * File
 *
 * @version 1.1
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author <a href="tobi.krebsATgmail.com">Tobi Krebs</a>
 */
public class MemoryMappedFileFilterInputStreamCache extends AbstractFilterInputStreamCache {

    private final static long DEFAULT_MEMORY_MAP_SIZE = 64 * 1024 * 1024; //64MB

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private MappedByteBuffer buf;
    private Path tempFile = null;
    private final long memoryMapSize = DEFAULT_MEMORY_MAP_SIZE;

    private boolean externalFile = true;

    public MemoryMappedFileFilterInputStreamCache(final InputStream src) throws IOException {
        this(src, null);
    }

    public MemoryMappedFileFilterInputStreamCache(final InputStream src, final Path f) throws IOException {
        super(src);

        if(f == null) {
            tempFile = TemporaryFileManager.getInstance().getTemporaryFile();
            externalFile = false;
        } else {
            tempFile = f;
            externalFile = true;
        }

        /**
         * Check the applicability of these bugs to this code:
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4724038
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6417205 (fixed in
         * 1.6)
         */

        this.raf = new RandomAccessFile(tempFile.toFile(), "rw");   //TODO(AR) consider moving to Files.newByteChannel(tempFile)
        this.channel = raf.getChannel();
        this.buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, getMemoryMapSize());
    }

    private long getMemoryMapSize() {
        return memoryMapSize;
    }

    private void increaseSize(final long bytes) throws IOException {

        long factor = (bytes / getMemoryMapSize());
        if (factor == 0 || bytes % getMemoryMapSize() > 0) {
            factor++;
        }

        buf.force();

        //TODO revisit this based on the comment below, I now believe setting position in map does work, but you have to have the correct offset added in as well! Adam
        final int position = buf.position();
        buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, buf.capacity() + (getMemoryMapSize() * factor));
        buf.position(position); //setting the position in the map() call above does not seem to work!
        //bufAccessor.refresh();
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {

        if (buf.remaining() < len) {
            //we need to remap the file
            increaseSize(len - buf.remaining());
        }

        buf.put(b, off, len);
    }

    @Override
    public void write(final int i) throws IOException {

        if (buf.remaining() < 1) {
            //we need to remap the file
            increaseSize(1);
        }

        buf.put((byte) i);
    }

    @Override
    public byte get(final int off) throws IOException {

        if (off > buf.capacity()) {
            //we need to remap the file
            increaseSize(off - buf.capacity());
        }

        return buf.get(off);
    }

    @Override
    public int getLength() {
        return buf.capacity() - buf.remaining();
    }

    @Override
    public void copyTo(final int cacheOffset, final byte[] b, final int off, final int len) throws IOException {

        if (off + len > buf.capacity()) {
            //we need to remap the file
            increaseSize(off + len - buf.capacity());
        }

        //get the current position
        final int position = buf.position();

        try {
            //move to the offset
            buf.position(cacheOffset);

            //read the data;
            final byte data[] = new byte[len];
            buf.get(data, 0, len);

            System.arraycopy(data, 0, b, off, len);
        } finally {
            //reset the position
            buf.position(position);
        }
    }

    @Override
    public void invalidate() throws IOException {
        buf.force();
        channel.close();
        raf.close();
        //System.gc();

        if (tempFile != null && (!externalFile)) {
            TemporaryFileManager.getInstance().returnTemporaryFile(tempFile);
        }
    }

    /**
     * Get the path of the file backing the cache.
     *
     * @return the path of the file backing the cache.
     */
    public Path getFilePath() {
        return tempFile;
    }
}
