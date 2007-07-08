/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  \$Id\$
 */
package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.collections.CollectionCache;
import org.exist.storage.cache.Cache;
import org.exist.util.Configuration;

public class CollectionCacheManager implements CacheManager {

    private static final Logger LOG = Logger.getLogger(CollectionCacheManager.class);

    public static final String CACHE_SIZE_ATTRIBUTE = "collectionCache";
    public static final String PROPERTY_CACHE_SIZE = "db-connection.collection-cache-mem";

    private static final int DEFAULT_CACHE_SIZE = 8;

    private int maxCacheSize;

    private CollectionCache collectionCache;

    public CollectionCacheManager(Configuration config, CollectionCache cache) {
        int cacheSize;
        if ((cacheSize = config.getInteger(PROPERTY_CACHE_SIZE)) < 0)
            cacheSize = DEFAULT_CACHE_SIZE;
        this.maxCacheSize = cacheSize * 1024 * 1024;
        if (LOG.isDebugEnabled())
            LOG.debug("collection collectionCache will be using " + this.maxCacheSize + " bytes max.");
        this.collectionCache = cache;
        this.collectionCache.setCacheManager(this);
    }

    public void registerCache(Cache cache) {
    }

    public void deregisterCache(Cache cache) {
        this.collectionCache = null;
    }

    public int requestMem(Cache cache) {
        int realSize = collectionCache.getRealSize();
        if (realSize < maxCacheSize) {
            synchronized (this) {
                int newCacheSize = (int)(collectionCache.getBuffers() * collectionCache.getGrowthFactor());
                if (LOG.isDebugEnabled())
                    LOG.debug("Growing cache " + collectionCache.getFileName() + " (a " + collectionCache.getClass().getName() +
                        ") from " + collectionCache.getBuffers() + " to " + newCacheSize + ". Current memory usage = " + realSize);
                collectionCache.resize(newCacheSize);
                return newCacheSize;
            }
        }
        LOG.debug("Cache has reached max. size: " + realSize);
        return -1;
    }

    public void checkCaches() {
    }

    public void checkDistribution() {
    }

    public long getMaxTotal() {
        return maxCacheSize;
    }

    public long getMaxSingle() {
        return maxCacheSize;
    }

    public long getCurrentSize() {
        return collectionCache.getRealSize();
    }
}
