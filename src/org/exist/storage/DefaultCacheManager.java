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

import org.apache.log4j.Logger;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.storage.cache.Cache;
import org.exist.util.DatabaseConfigurationException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

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
public class DefaultCacheManager implements CacheManager {
    
    private final static Logger LOG = Logger.getLogger(DefaultCacheManager.class);
    
    /**
     * The maximum fraction of the total memory that can
     * be used by a single cache.
     */
    public final static double MAX_MEM_USE = 0.9;
    
    /**
     * The minimum size a cache needs to have to be
     * considered for shrinking, defined in terms of a fraction
     * of the overall memory.  
     */
    public final static double MIN_SHRINK_FACTOR = 0.5;
    
    /**
     * The amount by which a large cache will be shrinked if
     * other caches request a resize.
     */
    public final static double SHRINK_FACTOR = 0.7;
     
    /**
     * The minimum number of pages that must be read from a
     * cache between check intervals to be not considered for 
     * shrinking. This is a measure for the "load" of the cache. Caches
     * with high load will never be shrinked.
     */
    public final static int SHRINK_THRESHOLD = 10000;
    
    public static int DEFAULT_CACHE_SIZE = 64;
    public static final String CACHE_SIZE_ATTRIBUTE = "cacheSize";
    public static final String PROPERTY_CACHE_SIZE = "db-connection.cache-size";
    
    /** Caches maintained by this class */
    private List caches = new ArrayList();

    private long totalMem;

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

    private int pageSize;

    /**
     * Signals that a resize had been requested by a cache, but
     * the request could not be accepted during normal operations.
     * The manager might try to shrink the largest cache during the
     * next sync event.
     */
    private Cache lastRequest = null;

    private String instanceName;

    public DefaultCacheManager(BrokerPool pool) {
        this.instanceName = pool.getId();
        int cacheSize;
        if ((pageSize = pool.getConfiguration().getInteger(BrokerPool.PROPERTY_PAGE_SIZE)) < 0)
        	//TODO : should we share the page size with the native broker ?
            pageSize = BrokerPool.DEFAULT_PAGE_SIZE;
        if ((cacheSize = pool.getConfiguration().getInteger(PROPERTY_CACHE_SIZE)) < 0) {
            cacheSize = DEFAULT_CACHE_SIZE;
        }
        totalMem = cacheSize * 1024 * 1024;
        long max = Runtime.getRuntime().maxMemory();
        long maxCache = max >= 768 * 1024 * 1024 ? max / 2 : max / 3;
        if (totalMem > maxCache) {
            totalMem = maxCache;
            LOG.warn("The cacheSize=\"" + cacheSize + "\" setting in conf.xml is too large. Java has only " +
                (max / 1024) + "k available. Cache manager will not use more than " + (totalMem / 1024) + "k " +
                "to avoid memory issues which may lead to database corruptions.");
        }
        int buffers = (int) (totalMem / pageSize);
        
        this.totalPageCount = buffers;
        this.maxCacheSize = (int) (totalPageCount * MAX_MEM_USE);
        NumberFormat nf = NumberFormat.getNumberInstance();
        LOG.info("Cache settings: " + nf.format(totalMem / 1024) + "k; totalPages: " + nf.format(totalPageCount) + 
        		"; maxCacheSize: " + nf.format(maxCacheSize));
        registerMBean();
    }

    public void registerCache(Cache cache) {
        currentPageCount += cache.getBuffers();
        caches.add(cache);
        cache.setCacheManager(this);
        registerMBean(cache);
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
    
    public int requestMem(Cache cache) {
        if (currentPageCount >= totalPageCount) {
            if (cache.getBuffers() < maxCacheSize)
                lastRequest = cache;
            // no free pages available
//            LOG.debug("Cache " + cache.getFileName() + " cannot be resized");
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
                if (LOG.isDebugEnabled()) {
	                NumberFormat nf = NumberFormat.getNumberInstance();
	                LOG.debug("Growing cache " + cache.getFileName() + " (a " + cache.getClass().getName() +  
	                        ") from " + nf.format(cache.getBuffers()) + 
	                        " to " + nf.format(newCacheSize));
                }
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
     * Called from the global major sync event to check if caches can
     * be shrinked. To be shrinked, the size of a cache needs to be
     * larger than the factor defined by {@link #MIN_SHRINK_FACTOR}
     * and its load needs to be lower than {@link #SHRINK_THRESHOLD}.
     *
     * If shrinked, the cache will be reset to the default initial cache size.
     */
    public void checkCaches() {
        int minSize = (int) (totalPageCount * MIN_SHRINK_FACTOR);
        Cache cache;
        int load;
        for (int i = 0; i < caches.size(); i++) {
            cache = (Cache) caches.get(i);
            if (cache.getGrowthFactor() > 1.0) {
                load = cache.getLoad();
                if (cache.getBuffers() > minSize && load < SHRINK_THRESHOLD) {
                	if (LOG.isDebugEnabled()) {
	                	NumberFormat nf = NumberFormat.getNumberInstance();
	                    LOG.debug("Shrinking cache: " + cache.getFileName() + " (a " + cache.getClass().getName() + 
	                        ") to " + nf.format(cache.getBuffers()));
                	}
                    currentPageCount -= cache.getBuffers();
                    cache.resize(getDefaultInitialSize());
                    currentPageCount += getDefaultInitialSize();
                }
            }
        }
    }
    
    public void checkDistribution() {
        if (lastRequest == null)
            return;
        int minSize = (int) (totalPageCount * MIN_SHRINK_FACTOR);
        Cache cache;
        for (int i = 0; i < caches.size(); i++) {
            cache = (Cache) caches.get(i);
            if (cache.getBuffers() >= minSize) {
                int newSize = (int) (cache.getBuffers() * SHRINK_FACTOR);
                if (LOG.isDebugEnabled()) {
	                NumberFormat nf = NumberFormat.getNumberInstance();
	                LOG.debug("Shrinking cache: " + cache.getFileName() + " (a " + cache.getClass().getName() + 
	                        ") to " + nf.format(newSize));
	            }
                currentPageCount -= cache.getBuffers();
                cache.resize(newSize);
                currentPageCount += newSize;
                break;
            }
        }
        lastRequest = null;
    }

    public long getMaxTotal() {
        return totalPageCount;
    }

    public long getCurrentSize() {
        return currentPageCount;
    }

    public long getSizeInBytes() {
        return currentPageCount * pageSize;
    }

    public long getMaxSingle() {
        return maxCacheSize;
    }

    public long getTotalMem() {
        return totalMem;
    }
    
    /**
     * Returns the default initial size for all caches.
     * 
     * @return Default initial size 64.
     */
    public int getDefaultInitialSize() {
        return DEFAULT_CACHE_SIZE;
    }

    private void registerMBean() {
        Agent agent = AgentFactory.getInstance();
        try {
            agent.addMBean(instanceName, "org.exist.management." + instanceName +
                ":type=CacheManager", new org.exist.management.CacheManager(this));
        } catch (DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }

    private void registerMBean(Cache cache) {
        Agent agent = AgentFactory.getInstance();
        try {
            agent.addMBean(instanceName, "org.exist.management." + instanceName + ":type=CacheManager.Cache,name=" +
                cache.getFileName() + ",cache-type=" + cache.getType(),
                    new org.exist.management.Cache(cache));
        } catch (DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }
}