/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.management.impl;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

/**
 * Simple bean to hold JMX info and a Binary Input Stream's cache
 */
public class BinaryInputStreamCacheInfo {

    private final long created;
    private final CacheType cacheType;
    private final Optional<Path> file;
    private final long size;

    public BinaryInputStreamCacheInfo(final CacheType cacheType, final long created, final Optional<Path> file,
            final long size) {
        this.created = created;
        this.cacheType = cacheType;
        this.file = file;
        this.size = size;
    }

    /**
     * Get the time that the Cache was created.
     *
     * @return the time the Cache was created
     */
    public Date getCreated() {
        return new Date(created);
    }

    /**
     * Get the type of the Cache.
     *
     * @return the type of the Cache
     */
    public CacheType getCacheType() {
        return cacheType;
    }

    /**
     * Get the path of the file backing the cache.
     *
     * @return The path of the file backing the cache (if there is one)
     */
    @Nullable
    public String getFile() {
        return file
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .orElse(null);
    }

    /**
     * Get the size of the cache.
     *
     * @return the size of the cache.
     */
    public long getSize() {
        return size;
    }

    enum CacheType {
        FILE,
        MEMORY_MAPPED_FILE,
        MEMORY
    }
}
