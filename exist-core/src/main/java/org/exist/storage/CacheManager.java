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
package org.exist.storage;

import org.exist.storage.cache.Cache;

public interface CacheManager {

    /**
     * Register a cache, i.e. put it under control of
     * the cache manager.
     *
     * @param cache cache to register
     */
    void registerCache(Cache cache);

    void deregisterCache(Cache cache);

    /**
     * Called by a cache if it wants to grow. The cache manager
     * will either deny the request, for example, if there are no spare
     * pages left, or calculate a new cache size and call the cache's
     * {@link org.exist.storage.cache.Cache#resize(int)} method to resize the cache. The amount
     * of pages by which the cache will grow is determined by the cache's
     * growthFactor: {@link org.exist.storage.cache.Cache#getGrowthFactor()}.
     *
     * @param cache cache to grow
     * @return new cache size, or -1 if no free pages available.
     */
    int requestMem(Cache cache);

    /**
     * Called from the global major sync event to check if caches can
     * be shrinked.
     *
     * If shrinked, the cache will be reset to the default initial cache size.
     */
    void checkCaches();

    /**
     * Called from the global minor sync event to check if a smaller
     * cache wants to be resized. If a huge cache is available, the method
     * might decide to shrink this cache by a certain amount to make
     * room for the smaller cache to grow.
     */
    void checkDistribution();

    /**
     * @return Maximum size of all Caches (unit of measurement is implementation defined)
     */
    long getMaxTotal();

    /**
     * @return Maximum size of a single Cache in bytes (unit of measurement is implementation defined)
     */
    long getMaxSingle();

    /**
     * @return Current size of all Caches in bytes (unit of measurement is implementation defined)
     */
    long getCurrentSize();

    /**
     * Returns the default initial size for all caches.
     *
     * @return  Default initial size in bytes.
     */
    int getDefaultInitialSize();
}
