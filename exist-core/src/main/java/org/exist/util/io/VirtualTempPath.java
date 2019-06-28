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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.StampedLock;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exist.util.FileUtils;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
@ThreadSafe
public final class VirtualTempPath implements ContentFile {
    public static final int DEFAULT_IN_MEMORY_SIZE = 4 * 1024 * 1024; // 4 MB

    private static final byte[] EMPTY_BUFFER = new byte[0];
    private static final Log LOG = LogFactory.getLog(VirtualTempPath.class);

    private final int inMemorySize;
    private final StampedLock lock;
    private final TemporaryFileManager tempFileManager;

    @GuardedBy("lock")
    private MemoryContents content;
    @GuardedBy("lock")
    private Path contentFile;

    public VirtualTempPath(int inMemorySize, TemporaryFileManager tempFileManager) {
        this.inMemorySize = inMemorySize;
        this.lock = new StampedLock();
        this.tempFileManager = tempFileManager;
    }

    private OutputStream initOverflowOutputStream() throws IOException {
        long stamp = lock.writeLock();
        try {
            if (contentFile == null) {
                contentFile = tempFileManager.getTemporaryFile();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Initializing overflow to " + contentFile.toAbsolutePath());
            }
            return Files.newOutputStream(contentFile);

        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public OutputStream newOutputStream() throws IOException {
        long stamp = lock.writeLock();
        try {
            if (inMemorySize <= 0 && contentFile == null) {
                contentFile = tempFileManager.getTemporaryFile();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("In memory buffering disabled writing to " + contentFile.toAbsolutePath());
                }
            }
            if (contentFile != null) {
                return Files.newOutputStream(contentFile);
            }
            if (content == null) {
                // initial blocks are 10 % of the specified in memory size but minimum 1
                content = MemoryContentsImpl.createWithInMemorySize(inMemorySize);
            }
            return new OverflowToDiskStream(inMemorySize, content, this::initOverflowOutputStream);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public InputStream newInputStream() throws IOException {
        long stamp = lock.readLock();
        try {
            if (contentFile != null) {
                return Files.newInputStream(contentFile);
            }
            if (content != null) {
                return new MemoryContentsInputStream(content);
            }
            return new ByteArrayInputStream(EMPTY_BUFFER);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public void close() {
        long stamp = lock.writeLock();
        try {
            if (contentFile != null) {
                tempFileManager.returnTemporaryFile(contentFile);
                contentFile = null;
            }
            if (content != null) {
                content.reset();
                content = null;
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public long size() {
        long stamp = lock.readLock();
        try {
            if (contentFile != null) {
                return FileUtils.sizeQuietly(contentFile);
            }
            return content == null ? 0 : content.size();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public byte[] getBytes() {
        long stamp = lock.readLock();
        try {
            if (contentFile != null) {
                return Files.readAllBytes(contentFile);
            }
            if (content != null) {
                byte[] buffer = new byte[(int) content.size()];
                content.read(buffer, 0L, 0, buffer.length);
                return buffer;
            }
        } catch (IOException e) {
            LOG.error("Unable to get content", e);
        } finally {
            lock.unlockRead(stamp);
        }
        return EMPTY_BUFFER;
    }
}