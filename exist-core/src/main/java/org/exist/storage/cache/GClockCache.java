/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.CacheManager;

import java.lang.reflect.Array;

/**
 * Cache implementation based on the GClock algorithm. 
 * 
 * Implements a mixture between LFU (Last Frequently Used) and LRU 
 * (Last Recently Used) replacement policies. The class 
 * uses reference counts to track references to cached objects. Each call to the add 
 * method increments the reference count of the object.
 * 
 * If the cache is full, the object to be removed is determined by decrementing the
 * reference count for each object until an object with reference count = 0 is found. 
 * 
 * The implementation tends to replace younger objects first.
 * 
 * @author wolf
 */
@NotThreadSafe
public class GClockCache<T extends Cacheable> implements Cache<T> {
	private final static Logger LOG = LogManager.getLogger(GClockCache.class);

	private final String name;
    private final Class<T> cacheableClazz;
    protected int size;
    private final double growthFactor;
    Accounting accounting;
    private final CacheType type;
	protected T[] items;
    protected Long2ObjectMap<T> map;
	protected int count = 0;
	protected int used = 0;
    private int hitsOld = 0;
	protected CacheManager cacheManager = null;

    public GClockCache(final String name, final Class<T> cacheableClazz, final int size, final double growthFactor, final double growthThreshold, final CacheType type) {
		this.name = name;
		this.cacheableClazz = cacheableClazz;
    	this.size = size;
        this.growthFactor = growthFactor;
        accounting = new Accounting(growthThreshold);
        accounting.setTotalSize(size);
        this.type = type;
        this.items = createArray(cacheableClazz, size);
		this.map = new Long2ObjectOpenHashMap<>(size * 2);
    }

    @SuppressWarnings("unchecked")
    private T[] createArray(final Class<T> clazz, final int size) {
        return (T[])Array.newInstance(clazz, size);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CacheType getType() {
        return type;
    }

    @Override
    public void add(final T item) {
		add(item, 1);
	}

	@Override
	public void add(final T item, final int initialRefCount) {
		final T old = map.get(item.getKey());
		if (old != null) {
			old.incReferenceCount();
			return;
		}
		item.setReferenceCount(initialRefCount);
		if (count < size) {
			items[count++] = item;
			map.put(item.getKey(), item);
			used++;
		} else {
			removeOne(item);
		}
	}

	@Override
	public T get(final T item) {
		return get(item.getKey());
	}

	@Override
	public T get(final long key) {
		final T item = map.get(key);
		if (item == null) {
			accounting.missesIncrement();
		} else {
		    accounting.hitIncrement();
		}
		return item;
	}

	@Override
	public void remove(final T item) {
		final long key = item.getKey();
		final T cacheable = map.remove(key);
		if (cacheable == null) {
		    return;
		}
		for (int i = 0; i < count; i++) {
			if (items[i] != null && items[i].getKey() == key) {
				items[i] = null;
				used--;
				return;
			}
		}
		LOG.error("item not found in list");
	}

	@Override
	public boolean flush() {
		boolean flushed = false;
	    int written = 0;
		for (int i = 0; i < count; i++) {
			if (items[i] != null && items[i].sync(false)) {
			    ++written;
			    flushed = true;
			}
		}
		if(LOG.isTraceEnabled()) {
            LOG.trace(written + " pages written to disk");
        }
		return flushed;
	}

	@Override
	public boolean hasDirtyItems() {
	    for(int i = 0; i < count; i++) {
			if(items[i] != null && items[i].isDirty()) {
			    return true;
			}
		}
	    return false;
	}

	protected T removeOne(final T item) {
		T old = null;
		boolean removed = false;
		int bucket;
		do {
			bucket = -1;
			// decrease all reference counts by 1
			for (int i = 0; i < count; i++) {
				old = items[i];
				if (old == null) {
					bucket = i;
				} else if (old.decReferenceCount() < 1 && bucket < 0) {
					bucket = i;
				}
			}

			if (bucket > -1) {
				old = items[bucket];
				if (old != null) {
				    if(LOG.isTraceEnabled()) {
                        LOG.trace(name + " replacing " + old.getKey() + " for " + item.getKey());
                    }
					map.remove(old.getKey());
					old.sync(true);
				} else {
					used++;
				}
				items[bucket] = item;
				map.put(item.getKey(), item);
				removed = true;
			}
		} while (!removed);
        
        if (old != null) {
            accounting.replacedPage(item);
            if (cacheManager != null && accounting.resizeNeeded()) {
                cacheManager.requestMem(this);
            }
        }
		return old;
	}

	@Override
	public int getBuffers() {
		return size;
	}

    @Override
	public int getUsedBuffers() {
		return used;
	}

    @Override
    public double getGrowthFactor() {
        return growthFactor;
    }

    @Override
	public int getHits() {
		return accounting.getHits();
	}

    @Override
	public int getFails() {
		return accounting.getMisses();
	}

    public int getThrashing() {
        return accounting.getThrashing();
    }

    @Override
    public void setCacheManager(final CacheManager manager) {
        this.cacheManager = manager;
    }

    @Override
    public void resize(final int newSize) {
        if (newSize < size) {
            shrink(newSize);
        } else {
            final T[] newItems = createArray(cacheableClazz, newSize);
            final Long2ObjectMap<T> newMap = new Long2ObjectOpenHashMap<>(newSize * 2);
            for (int i = 0; i < count; i++) {
                newItems[i] = items[i];
                newMap.put(items[i].getKey(), items[i]);
            }
            this.size = newSize;
            this.map = newMap;
            this.items = newItems;
            accounting.reset();
            accounting.setTotalSize(size);
        }
    }
    
    private void shrink(final int newSize) {
        flush();
        items = createArray(cacheableClazz, newSize);
        map = new Long2ObjectOpenHashMap<>(newSize * 2);
        size = newSize;
        count = 0;
        used = 0;
        accounting.reset();
        accounting.setTotalSize(size);
    }

    @Override
    public int getLoad() {
        if (hitsOld == 0) {
            hitsOld = accounting.getHits();
            return Integer.MAX_VALUE;
        }
        final int load = accounting.getHits() - hitsOld;
        hitsOld = accounting.getHits();
        return load;
    }
}
