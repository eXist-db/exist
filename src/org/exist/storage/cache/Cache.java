/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.storage.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.CacheManager;

/**
 * Base interface for all cache implementations that are used for
 * buffering btree and data pages.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public interface Cache {

    /**
     * Returns the type of this cache. Should be one of the
     * constants defined in {@link org.exist.storage.CacheManager}.
     *
     * @return the type of this cache
     */
    public String getType();

    /**
     * Add the item to the cache. If it is already in the cache,
     * update the references.
     * 
     * @param item
     */
    public void add(Cacheable item);

    /**
     * Add the item to the cache. If it is already in the cache,
     * update the references.
     * 
     * @param item
     * @param initialRefCount the initial reference count for the item
     */
    public void add(Cacheable item, int initialRefCount);

    /**
     * Retrieve an item from the cache.
     * 
     * @param item
     * @return the item in the cache or null if it does not exist.
     */
    public Cacheable get(Cacheable item);

    /**
     * Retrieve an item by its key.
     * 
     * @param key a unique key, usually the page number
     * @return the item in the cache or null if it does not exist.
     */
    public Cacheable get(long key);

    /**
     * Remove an item from the cache.
     * 
     * @param item
     */
    public void remove(Cacheable item);

    /**
     * Returns true if the cache contains any dirty
     * items that need to be written to disk.
     * 
     */
    public boolean hasDirtyItems();

    /**
     * Call release on all items, but without
     * actually removing them from the cache.
     * 
     * This gives the items a chance to write all
     * unwritten data to disk.
     */
    public boolean flush();

    /**
     * Get the size of this cache.
     * 
     * @return size
     */
    public int getBuffers();

    /**
     * Returns the factor by which the cache should grow
     * if it can be resized. The returned factor f will be
     * between 0 and 2. A value smaller or equal to 1 means the cache
     * can't grow, 1.5 means it grows by 50 percent. A cache with
     * growth factor &lt;= 1.0 can also not be shrinked.
     * 
     * A cache is resized by the {@link org.exist.storage.DefaultCacheManager}.
     * 
     * @return growth factor
     */
    public double getGrowthFactor();

    /**
     * Resize the cache. This method is called by the
     * {@link org.exist.storage.DefaultCacheManager}. The newSize parameter
     * can either be larger or smaller than the current
     * cache size.
     * 
     * @param newSize the new size of the cache.
     */
    public void resize(int newSize);

    /**
     * Set the CacheManager object that controls this cache.
     * 
     * @param manager
     */
    public void setCacheManager(CacheManager manager);

    /**
     * Get the number of buffers currently used.
     * 
     */
    public int getUsedBuffers();

    /**
     * Get the number of times where an object has been successfully
     * loaded from the cache.
     */
    public int getHits();

    /**
     * Get the number of times where an object could not be
     * found in the cache.
     * 
     * @return number of times where an object could not be
     * found in the cache
     */
    public int getFails();

    public int getLoad();

    public void setFileName(String fileName);

    public String getFileName();

    public final static Logger LOG = LogManager.getLogger(Cache.class);
}