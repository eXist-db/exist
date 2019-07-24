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
package org.exist.storage.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.hashtable.SequencedLongHashMap;

/**
 * Keeps track of various cache parameters. Most important,
 * this class is used to determine if a cache should be grown by
 * computing the cache efficiency, which is expressed as the amount 
 * of trashing that occurs during a certain
 * period of time. Trashing occurs if a page is replaced from the cache
 * and is reloaded shortly after. For the B+-tree pages, we normally
 * don't want any trashing at all.
 * 
 * @author wolf
 *
 */
public class Accounting {
    
    private final static Logger LOG = LogManager.getLogger(Accounting.class);

    private final static Object DUMMY = new Object();
    
    /** the period (in milliseconds) for which trashing is recorded. */
    private int checkPeriod = 30000;
    
    /** start of the last check period */
    private long checkPeriodStart = System.currentTimeMillis();
    
    /** max. entries to keep in the table of replaced pages */
    private int maxEntries = 5000;
    
    /** total cache hits during the lifetime of the cache*/
    private int hits = 0;
    
    /** total cache misses during the lifetime of the cache */
    private int misses = 0;
    
    /** the current size of the cache */
    private int totalSize = 0;
    
    /** the number of pages replaced and reloaded during the check period */
    private int thrashing = 0;
    
    /** determines the amount of allowed trashing before a cache resize will
     * be requested. This is expressed as a fraction of the total cache size.
     */
    private double thrashingFactor;
    
    /** the map used to track replaced page numbers */
    private SequencedLongHashMap<Object> map;
    
    public Accounting(double thrashingFactor) {
        map = new SequencedLongHashMap<Object>((maxEntries * 3) / 2);
        this.thrashingFactor = thrashingFactor;
    }
    
    /**
     * Set the current size of the cache. Should be called by the
     * cache whenever it changes its size.
     * 
     * @param totalSize of the cache
     */
    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }
    
    /**
     * Increment the number of total cache hits by one.
     */
    public void hitIncrement() {
        ++hits;
    }
    
    /**
     * Returns the number of total cache hits during the
     * lifetime of the cache.
     * 
     * @return number of total cache hits
     */
    public int getHits() {
        return hits;
    }
    
    /**
     * Increment the number of total cache faults by one.
     */
    public void missesIncrement() {
        ++misses;
    }
    
    /**
     * Returns the number of total cache faults.
     * @return number of total cache faults
     */
    public int getMisses() {
        return misses;
    }
    
    /**
     * Called by the cache to signal that a page was replaced
     * in order to store the Cacheable object passed.
     * 
     * @param cacheable object
     */
    public void replacedPage(Cacheable cacheable) {
        if (System.currentTimeMillis() - checkPeriodStart > checkPeriod) {
            map.clear();
            thrashing = 0;
            checkPeriodStart = System.currentTimeMillis();
        }
    
        if (map.size() == maxEntries) {
            map.removeFirst();
        }
        
        if (map.get(cacheable.getKey()) != null) {
            ++thrashing;
        } else
            {map.put(cacheable.getKey(), DUMMY);}
    }
    
    /**
     * Return the current amount of trashing.
     * @return current amount of trashing
     */
    public int getThrashing() {
        return thrashing;
    }
    
    /**
     * Returns true if a cache resize would increase the
     * cache efficiency.
     * 
     * @return True if a cache resize would increase the
     * cache efficiency
     */
    public boolean resizeNeeded() {
        if (thrashingFactor == 0)
            {return thrashing > 0;}
        return thrashing > totalSize * thrashingFactor;
    }
    
    public void reset() {
        map.clear();
        thrashing = 0;
        checkPeriodStart = System.currentTimeMillis();
    }
    
    public void stats() {
        LOG.debug("hits: " + hits 
                + "; misses: " + misses 
                + "; thrashing: " + getThrashing() 
                + "; thrashing period: " + checkPeriod);
    }
}