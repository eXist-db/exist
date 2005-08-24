/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  $Id$
 */
package org.exist.storage;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.storage.cache.Cache;
import org.exist.util.Configuration;

/**
 * CacheManager maintains a global memory pool available
 * to all page caches. All caches start with a low default
 * setting, but CacheManager can grow individual caches 
 * until the total memory is reached. Caches can also be
 * shrinked if their "load" remains below a given threshold
 * between check intervals.The check interval is determined
 * by the global sync background thread.
 * 
 * The class computes the available memory in terms of
 * pages.
 * 
 * @author wolf
 *
 */
public class CacheManager {
    
    private final static Logger LOG = Logger.getLogger(CacheManager.class);
    
    /**
     * The maximum fraction of the total memory that can
     * be used by a single cache.
     */
    public final static double MAX_MEM_USE = 0.75;
    
    /**
     * The minimum size a cache needs to have to be
     * considered for shrinking, defined in terms of a fraction
     * of the overall memory.  
     */
    public final static double SHRINK_FACTOR = 0.2;
    
    /**
     * The minimum number of pages that must be read from a
     * cache between check intervals to be not considered for 
     * shrinking. This is a measure for the "load" of the cache. Caches
     * with high load will never be shrinked.
     */
    public final static int SHRINK_THRESHOLD = 10000;
        
    /** Caches maintained by this class */
    private List caches = new ArrayList();
    
    /**
     * The total maximum amount of pages shared between
     * all caches. 
     */
    private int totalPageCount;
    
    /**
     * The number of pages currently used by the active caches.
     */
    private int currentPageCount = 0;
    
    /**
     * The maximum number of pages that can be allocated by a
     * single cache.
     */
    private int maxCacheSize;
    
    public CacheManager(Configuration config) {
        int pageSize, cacheSize;
        if ((pageSize = config.getInteger("db-connection.page-size")) < 0)
            pageSize = 4096;
        if ((cacheSize = config.getInteger("db-connection.cache-size")) < 0) {
            cacheSize = 64;
        }
        long totalMem = cacheSize * 1024 * 1024;
        int buffers = (int) (totalMem / pageSize);
        
        this.totalPageCount = buffers;
        this.maxCacheSize = (int) (totalPageCount * MAX_MEM_USE);
        LOG.info("Cache settings: totalPages: " + totalPageCount + "; maxCacheSize: " + maxCacheSize);
    }

    /**
     * Register a cache, i.e. put it under control of
     * the cache manager.
     * 
     * @param cache
     */
    public void registerCache(Cache cache) {
        currentPageCount += cache.getBuffers();
        caches.add(cache);
        cache.setCacheManager(this);
    }
    
    public void deregisterCache(Cache cache) {
    	Cache next;
    	for (int i = 0; i < caches.size(); i++) {
            next = (Cache) caches.get(i);
            if (cache == next) {
            	caches.remove(i);
            	break;
            }
    	}
    	currentPageCount -= cache.getBuffers();
    }
    
    /**
     * Called by a cache if it wants to grow. The cache manager
     * will either deny the request, for example, if there are no spare
     * pages left, or calculate a new cache size and call the cache's
     * {@link Cache#resize(int)} method to resize the cache. The amount
     * of pages by which the cache will grow is determined by the cache's
     * growthFactor: {@link Cache#getGrowthFactor()}.
     * 
     * @param cache
     * @return
     */
    public int requestMem(Cache cache) {
        if (currentPageCount >= totalPageCount) {
            // no free pages available
            return -1;
        }
        if (cache.getGrowthFactor() > 1.0
                && cache.getBuffers() < maxCacheSize) {
            synchronized (this) {
                if (currentPageCount >= totalPageCount)
                    // another cache has been resized. Give up
                    return -1;
                // calculate new cache size
                int newCacheSize = (int)(cache.getBuffers() * cache.getGrowthFactor());
                if (newCacheSize > maxCacheSize)
                    // new cache size is too large: adjust
                    newCacheSize = maxCacheSize;
                if (currentPageCount + newCacheSize > totalPageCount)
                    // new cache size exceeds total: adjust
                    newCacheSize = cache.getBuffers() + (totalPageCount - currentPageCount);
                currentPageCount -= cache.getBuffers();
                // resize the cache
                cache.resize(newCacheSize);
                currentPageCount += newCacheSize;
//                LOG.debug("currentPageCount = " + currentPageCount + "; max = " + totalPageCount);
                return newCacheSize;
            }
        }
        return -1;
    }
    
    /**
     * Called from the global sync event to check if caches can
     * be shrinked. To be shrinked, the size of a cache needs to be
     * larger than the factor defined by {@link #SHRINK_FACTOR} 
     * and its load needs to be lower than {@link #SHRINK_THRESHOLD}.
     *
     * If shrinked, the cache will be reset to the default initial cache size.
     */
    public void checkCaches() {
        int minSize = (int) (totalPageCount * SHRINK_FACTOR);
        Cache cache;
        int load;
        for (int i = 0; i < caches.size(); i++) {
            cache = (Cache) caches.get(i);
            if (cache.getGrowthFactor() > 1.0) {
                load = cache.getLoad();
                if (cache.getBuffers() > minSize && load < SHRINK_THRESHOLD) {
                    LOG.debug("Shrinking cache: " + cache.getBuffers());
                    currentPageCount -= cache.getBuffers();
                    cache.resize(getDefaultInitialSize());
                    currentPageCount += getDefaultInitialSize();
                }
            }
        }
    }
    
    /**
     * Returns the default initial size for all caches.
     * 
     * @return
     */
    public int getDefaultInitialSize() {
        return 64;
    }
}
