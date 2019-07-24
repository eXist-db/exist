/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util.io;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedLock;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public final class MemoryContentsImpl implements MemoryContents {
    private static final Logger LOG = LogManager.getLogger(MemoryContentsImpl.class);

    /**
     * The object header size of an array. Two words (flags &amp; class oop) plus
     * array size (2 *64 bit + 32 bit on 64 bit, 2 *32 bit + 32 bit on 32 bit).
     */
    private static final int ARRAY_HEADER = 8 + 8 + 4;

    private static final int BLOCK_SIZE = 4096 - ARRAY_HEADER; // make sure it fits into a 4k memory region
    private static final int NUMBER_OF_BLOCKS = BLOCK_SIZE;

    private final int initialBlocks;
    private final ReadWriteLock lock;

    /**
     * To store the contents efficiently we store the first {@value #BLOCK_SIZE}
     * bytes in a {@value #BLOCK_SIZE} direct {@code byte[]}. The next
     * {@value #NUMBER_OF_BLOCKS} * {@value #BLOCK_SIZE} bytes go into a indirect
     * {@code byte[][]} that is lazily allocated.
     */
    private byte[] directBlock;
    private byte[][] indirectBlocks;

    private long size;
    private int indirectBlocksAllocated;

    public static MemoryContents createWithInitialBlocks(int initialBlocks) {
        return new MemoryContentsImpl(initialBlocks);
    }

    public static MemoryContents createWithInMemorySize(int inMemorySize) {
        return createWithInitialBlocks(max(inMemorySize / 10 / MemoryContentsImpl.BLOCK_SIZE, 1));
    }

    private MemoryContentsImpl(int initialBlocks) {
        this.initialBlocks = initialBlocks;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing with {} initial blocks", Integer.valueOf(initialBlocks));
        }
        lock = new ReentrantReadWriteLock();
        initialize();
    }

    private void initialize() {
        directBlock = new byte[BLOCK_SIZE];
        if (initialBlocks > 1) {
            indirectBlocks = new byte[BLOCK_SIZE][];
            for (int i = 0; i < initialBlocks - 1; ++i) {
                indirectBlocks[i] = new byte[BLOCK_SIZE];
            }
            indirectBlocksAllocated = initialBlocks - 1;
        }
        size = 0L;
    }

    private byte[] getBlock(int currentBlock) {
        if (currentBlock == 0) {
            return directBlock;
        } else {
            return indirectBlocks[currentBlock - 1];
        }
    }

    private void ensureCapacity(long capacity) {
        // if direct block is enough do nothing
        if (capacity <= BLOCK_SIZE) {
            return;
        }
        // lazily allocate indirect blocks
        if (indirectBlocks == null) {
            indirectBlocks = new byte[NUMBER_OF_BLOCKS][];
        }
        // consider already present direct block, don't add + 1
        int blocksRequired = (int) ((capacity - 1L) / BLOCK_SIZE);
        if (blocksRequired > NUMBER_OF_BLOCKS) {
            throw new AssertionError("memory values bigger than 16MB not supported");
        }
        if (blocksRequired > indirectBlocksAllocated) {
            for (int i = indirectBlocksAllocated; i < blocksRequired; ++i) {
                indirectBlocks[i] = new byte[BLOCK_SIZE];
                indirectBlocksAllocated += 1;
            }
        }
    }

    private ManagedLock readLock() {
        return ManagedLock.acquire(lock, Lock.LockMode.READ_LOCK);
    }

    private ManagedLock writeLock() {
        return ManagedLock.acquire(lock, Lock.LockMode.WRITE_LOCK);
    }

    @Override
    public void reset() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Reset content");
        }
        try (ManagedLock lock = writeLock()) {
            initialize();
        }
    }

    @Override
    public long size() {
        try (ManagedLock lock = readLock()) {
            return size;
        }
    }

    @Override
    public int read(byte[] dst, long position, int off, int len) {
        try (ManagedLock lock = readLock()) {
            if (position >= size) {
                return -1;
            }
            int toRead = (int) min(min(size - position, len), Integer.MAX_VALUE);
            int currentBlock = (int) (position / BLOCK_SIZE);
            int startIndexInBlock = (int) (position - (currentBlock * (long) BLOCK_SIZE));
            int read = 0;
            while (read < toRead) {
                int lengthInBlock = min(BLOCK_SIZE - startIndexInBlock, toRead - read);
                byte[] block = getBlock(currentBlock);
                System.arraycopy(block, startIndexInBlock, dst, off + read, lengthInBlock);
                read += lengthInBlock;
                startIndexInBlock = 0;
                currentBlock += 1;
            }
            return read;
        }
    }

    @Override
    public long transferTo(OutputStream target, long position) throws IOException {
        try (ManagedLock lock = readLock()) {
            long transferred = 0L;
            long toTransfer = size - position;
            int currentBlock = (int) (position / BLOCK_SIZE);
            int startIndexInBlock = (int) (position - (currentBlock * (long) BLOCK_SIZE));
            while (transferred < toTransfer) {
                int lengthInBlock = (int) min(BLOCK_SIZE - startIndexInBlock, toTransfer - transferred);
                byte[] block = getBlock(currentBlock);
                target.write(block, startIndexInBlock, lengthInBlock);
                transferred += lengthInBlock;
                startIndexInBlock = 0;
                currentBlock += 1;
            }

            return transferred;
        }
    }

    @Override
    public int write(byte[] src, long position, int off, int len) {
        try (ManagedLock lock = writeLock()) {
            ensureCapacity(position + len);
            int toWrite = min(len, Integer.MAX_VALUE);
            int currentBlock = (int) (position / BLOCK_SIZE);
            int startIndexInBlock = (int) (position - (currentBlock * (long) BLOCK_SIZE));
            int written = 0;
            while (written < toWrite) {
                int lengthInBlock = min(BLOCK_SIZE - startIndexInBlock, toWrite - written);
                byte[] block = getBlock(currentBlock);
                System.arraycopy(src, off + written, block, startIndexInBlock, lengthInBlock);
                written += lengthInBlock;
                startIndexInBlock = 0;
                currentBlock += 1;
            }
            // REVIEW, possibility to fill with random data
            size = max(size, position + written);
            return written;
        }
    }

    @Override
    public int writeAtEnd(byte[] src, int off, int len) {
        try (ManagedLock lock = writeLock()) {
            return write(src, size, off, len);
        }
    }
}
