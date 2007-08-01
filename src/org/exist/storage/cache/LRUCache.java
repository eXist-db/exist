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

import org.exist.storage.CacheManager;
import org.exist.util.hashtable.SequencedLongHashMap;

/**
 * A simple cache implementing a Last Recently Used policy. This
 * cache implementation is based on a 
 * {@link org.exist.util.hashtable.SequencedLongHashMap}. Contrary
 * to the other {@link org.exist.storage.cache.Cache} implementations,
 * LRUCache ignores reference counts or timestamps.
 * 
 * @author wolf
 */
public class LRUCache implements Cache {
    
	private int max;
	private SequencedLongHashMap map;
	
    private Accounting accounting;
	
    private int hitsOld = -1;
	
    private double growthFactor;
    
    private String fileName;
    
    private CacheManager cacheManager = null;

    private String type;

    public LRUCache(int size, double growthFactor, double growthThreshold, String type) {
		max = size;
        this.growthFactor = growthFactor;
		map = new SequencedLongHashMap(size * 2);
        accounting = new Accounting(growthThreshold);
        accounting.setTotalSize(max);
        this.type = type;
    }
	
	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable, int)
	 */
	public void add(Cacheable item, int initialRefCount) {
		add(item);
	}

    public String getType() {
        return type;
    }

    /* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable)
	 */
	public void add(Cacheable item) {
		if(map.size() == max) {
			removeOne(item);
		}
		map.put(item.getKey(), item);
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(org.exist.storage.cache.Cacheable)
	 */
	public Cacheable get(Cacheable item) {
		return get(item.getKey());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(long)
	 */
	public Cacheable get(long key) {
		Cacheable obj = (Cacheable) map.get(key);
		if(obj == null)
			accounting.missesIncrement();
		else
			accounting.hitIncrement();
		return obj;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#remove(org.exist.storage.cache.Cacheable)
	 */
	public void remove(Cacheable item) {
		map.remove(item.getKey());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#flush()
	 */
	public boolean flush() {
		boolean flushed = false;
		Cacheable cacheable;
		SequencedLongHashMap.Entry next = map.getFirstEntry();
		while(next != null) {
			cacheable = (Cacheable)next.getValue();
			if(cacheable.isDirty()) {
				flushed = flushed | cacheable.sync(false);
			}
			next = next.getNext();
		}
		return flushed;
	}

	
    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cache#hasDirtyItems()
     */
    public boolean hasDirtyItems() {
        Cacheable cacheable;
        SequencedLongHashMap.Entry next = map.getFirstEntry();
        while(next != null) {
        	cacheable = (Cacheable)next.getValue();
        	if(cacheable.isDirty())
        		return true;
        	next = next.getNext();
        }
		return false;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getBuffers()
	 */
	public int getBuffers() {
		return max;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getUsedBuffers()
	 */
	public int getUsedBuffers() {
		return map.size();
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getHits()
	 */
	public int getHits() {
		return accounting.getHits();
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getFails()
	 */
	public int getFails() {
		return accounting.getMisses();
	}
 
    public int getThrashing() {
        return accounting.getThrashing();
    }
    
	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#setFileName(java.lang.String)
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
    public String getFileName() {
        return fileName;
    }
    
    public SequencedLongHashMap.Entry getFirst() {
        return map.getFirstEntry();
    }
    
	private final void removeOne(Cacheable item) {
		boolean removed = false;
		SequencedLongHashMap.Entry next = map.getFirstEntry();
		do {
			Cacheable cached = (Cacheable)next.getValue();
			if(cached.allowUnload() && cached.getKey() != item.getKey()) {
				cached.sync(true);
				map.remove(next.getKey());
				removed = true;
			} else {
				next = next.getNext();
				if(next == null) {
					LOG.debug("Unable to remove entry");
					next = map.getFirstEntry();
				}
			}
		} while(!removed);
        accounting.replacedPage(item);
        if (growthFactor > 1.0 && accounting.resizeNeeded()) {
            cacheManager.requestMem(this);
        }
	}

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cache#getGrowthFactor()
     */
    public double getGrowthFactor() {
        return growthFactor;
    }

    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cache#setCacheManager(org.exist.storage.CacheManager)
     */
    public void setCacheManager(CacheManager manager) {
        this.cacheManager = manager;
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.cache.Cache#resize(int)
     */
    public void resize(int newSize) {
        if (newSize < max) {
            shrink(newSize);
        } else {
            SequencedLongHashMap newMap = new SequencedLongHashMap(newSize * 2);
            SequencedLongHashMap.Entry next = map.getFirstEntry();
            Cacheable cacheable;
            while(next != null) {
                cacheable = (Cacheable)next.getValue();
                newMap.put(cacheable.getKey(), cacheable);
                next = next.getNext();
            }
            max = newSize;
            map = newMap;
            accounting.reset();
            accounting.setTotalSize(max);
        }
    }
    
    private void shrink(int newSize) {
        flush();
        this.map = new SequencedLongHashMap(newSize);
        this.max = newSize;
        accounting.reset();
        accounting.setTotalSize(max);
    }

    public int getLoad() {
        if (hitsOld == 0) {
            hitsOld = accounting.getHits();
            return Integer.MAX_VALUE;
        }
        int load = accounting.getHits() - hitsOld;
        hitsOld = accounting.getHits();
        return load;
    }
}
