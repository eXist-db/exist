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

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exist.util.FileUtils;

/**
 * @author Patrick Reinhart <patrick@reini.net>
 */
@ThreadSafe
public final class VirtualTempPath implements AutoCloseable {
    private static final Log LOG = LogFactory.getLog(VirtualTempPath.class);
    private static final byte[] EMPTY_BUFFER = new byte[0];

    private final int inMemorySize;
    private final TemporaryFileManager tempFileManager;

    private MemoryContents content;
    private Path contentFile;

    public VirtualTempPath(int inMemorySize, TemporaryFileManager tempFileManager) {
        this.tempFileManager = tempFileManager;
        this.inMemorySize = inMemorySize;
    }

    private OutputStream initOverflowOutputStream() throws IOException {
        if (contentFile == null) {
            contentFile = tempFileManager.getTemporaryFile();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing overflow to " + contentFile.toAbsolutePath());
        }
        return Files.newOutputStream(contentFile);
    }

    public OutputStream newOutputStream() throws IOException {
        if (inMemorySize <= 0) {
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
    }

    public InputStream newInputStream() throws IOException {
        if (contentFile != null) {
            return Files.newInputStream(contentFile);
        }
        if (content != null) {
            return new MemoryContentsInputStream(content);
        }
        return new ByteArrayInputStream(EMPTY_BUFFER);
    }

    @Override
    public void close() {
        if (contentFile != null) {
            tempFileManager.returnTemporaryFile(contentFile);
            contentFile = null;
        }
        if (content != null) {
            content.reset();
            content = null;
        }
    }

    public long size() {
        if (contentFile != null) {
            return FileUtils.sizeQuietly(contentFile);
        }
        return content == null ? 0 : content.size();
    }

    public byte[] getBytes() {
        try {
            if (content != null) {
                byte[] buffer = new byte[(int) content.size()];
                content.read(buffer, 0L, 0, buffer.length);
                return buffer;
            } else if (contentFile != null) {
                return Files.readAllBytes(contentFile);
            }
        } catch (IOException e) {
            LOG.error("Unable to get content", e);
        }
        return EMPTY_BUFFER;
    }
}