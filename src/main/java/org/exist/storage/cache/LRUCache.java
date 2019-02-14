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

import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
@NotThreadSafe
public class LRUCache<T extends Cacheable> implements Cache<T> {
    private final static Logger LOG = LogManager.getLogger(LRUCache.class);

	private final String name;
	protected int max;
    protected final double growthFactor;
    protected final Accounting accounting;
	protected SequencedLongHashMap<T> map;
    private final CacheType type;
    private int hitsOld = -1;
    protected CacheManager cacheManager = null;

    public LRUCache(final String name, final int size, final double growthFactor, final double growthThreshold, final CacheType type) {
    	this.name = name;
		this.max = size;
        this.growthFactor = growthFactor;
        this.accounting = new Accounting(growthThreshold);
        this.accounting.setTotalSize(max);
        this.map = new SequencedLongHashMap<>(size * 2);
        this.type = type;
    }

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void add(final T item, final int initialRefCount) {
		_add(item);
	}

	@Override
    public CacheType getType() {
        return type;
    }

    @Override
	public void add(final T item) {
        _add(item);
	}

	/**
     * Avoids StackOverflow through
     * base/sub-class overriding add(T)
     */
	private void _add(final T item) {
        if(map.size() == max) {
            removeOne(item);
        }
        map.put(item.getKey(), item);
    }

	@Override
	public T get(final T item) {
		return get(item.getKey());
	}

	@Override
	public T get(final long key) {
		final T obj = map.get(key);
		if(obj == null) {
		    accounting.missesIncrement();
		} else {
		    accounting.hitIncrement();
		}
		return obj;
	}

	@Override
	public void remove(final T item) {
		map.remove(item.getKey());
	}

	@Override
	public boolean flush() {
		boolean flushed = false;
		for(SequencedLongHashMap.Entry<T> next = map.getFirstEntry(); next != null; next = next.getNext()) {
			final T cacheable = next.getValue();
			if(cacheable.isDirty()) {
				flushed = flushed | cacheable.sync(false);
			}
		}
		return flushed;
	}

	
    @Override
    public boolean hasDirtyItems() {
        for(SequencedLongHashMap.Entry<T> next = map.getFirstEntry(); next != null; next = next.getNext()) {
            final T cacheable = next.getValue();
            if(cacheable.isDirty()) {
                return true;
            }
        }
        return false;
    }
    
	@Override
	public int getBuffers() {
		return max;
	}

	@Override
	public int getUsedBuffers() {
		return map.size();
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

	protected void removeOne(final T item) {
        boolean removed = false;
        SequencedLongHashMap.Entry<T> next = map.getFirstEntry();
        do {
            final T cached = next.getValue();
            if(cached.allowUnload() && cached.getKey() != item.getKey()) {
                cached.sync(true);
                map.remove(next.getKey());
                removed = true;
            } else {
                next = next.getNext();
                if(next == null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Unable to remove entry");
                    }
                    next = map.getFirstEntry();
                }
            }
        } while(!removed);
        accounting.replacedPage(item);
        if (growthFactor > 1.0 && accounting.resizeNeeded()) {
            cacheManager.requestMem(this);
        }
	}

    @Override
    public double getGrowthFactor() {
        return growthFactor;
    }

    @Override
    public void setCacheManager(final CacheManager manager) {
        this.cacheManager = manager;
    }
    
    @Override
    public void resize(final int newSize) {
        if (newSize < max) {
            shrink(newSize);
        } else {
            final SequencedLongHashMap<T> newMap = new SequencedLongHashMap<>(newSize * 2);
            for(SequencedLongHashMap.Entry<T> next = map.getFirstEntry(); next != null; next = next.getNext()) {
                final T cacheable = next.getValue();
                newMap.put(cacheable.getKey(), cacheable);
            }
            max = newSize;
            map = newMap;
            accounting.reset();
            accounting.setTotalSize(max);
        }
    }

    protected void shrink(final int newSize) {
        flush();
        map = new SequencedLongHashMap<>(newSize);
        max = newSize;
        accounting.reset();
        accounting.setTotalSize(max);
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
