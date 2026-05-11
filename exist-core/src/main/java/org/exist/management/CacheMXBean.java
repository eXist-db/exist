/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.management;

import org.exist.management.impl.PerInstanceMBean;
import org.exist.storage.cache.Cache;

/**
 * Provides access to some properties of the internal page caches
 * ({@link org.exist.storage.cache.Cache}).
 */
public interface CacheMXBean extends PerInstanceMBean {

    /**
     * Get the type of this cache.
     *
     * @return the cache type
     */
    Cache.CacheType getType();

    /**
     * Get the total number of buffers allocated to this cache.
     *
     * @return total buffer count
     */
    int getSize();

    /**
     * Get the number of buffers currently in use.
     *
     * @return used buffer count
     */
    int getUsed();

    /**
     * Get the number of cache hits since the last reset.
     *
     * @return cache hit count
     */
    int getHits();

    /**
     * Get the number of cache misses since the last reset.
     *
     * @return cache miss count
     */
    int getFails();

    /**
     * Get the name of this cache.
     *
     * @return the cache name
     */
    String getCacheName();
}