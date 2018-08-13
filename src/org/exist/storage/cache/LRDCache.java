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

/**
 * A cache implementation based on a Least Reference Density (LRD)
 * replacement policy.
 * 
 * The class maintains a global reference counter, containing the sum of all
 * references in the cache. Each object has a timestamp, which is equal to
 * the number of global references at the time, the object has been added to
 * the cache.
 * 
 * If the cache is full, the object with the least reference density is removed.
 * The reference density is computed as the ratio between the object's reference
 * counter and the number of references added since the object has been included
 * into the cache, i.e. RC(i) / (GR - TS(i)).
 * 
 * @author wolf
 */
@NotThreadSafe
public class LRDCache<T extends Cacheable> extends GClockCache<T> {
	private final static Logger LOG = LogManager.getLogger(LRDCache.class);
	
	private final int maxReferences;
	private final int ageingPeriod;
    private int totalReferences = 0;
    private int nextCleanup;
	
	public LRDCache(final String name, final Class<T> cacheableClazz, final int size, final double growthFactor, final double growthThreshold, final CacheType type) {
		super(name, cacheableClazz, size, growthFactor, growthThreshold, type);
		maxReferences = size * 10000;
		ageingPeriod = size * 5000;
	}
	
	@Override
	public void add(final T item, final int initialRefCount) {
		final T old = map.get(item.getKey());
		if (old != null) {
			old.incReferenceCount();
			totalReferences++;
		} else {
			item.setReferenceCount(initialRefCount);
			item.setTimestamp(totalReferences);
			if (count < size) {
				items[count++] = item;
				map.put(item.getKey(), item);
				used++;
			} else {
			    removeOne(item);
			}
			totalReferences += initialRefCount;
		}
		if(totalReferences > maxReferences) {
		    cleanup();
		} else if (totalReferences > nextCleanup) {
		    ageReferences();
		}
	}

	@Override
	protected T removeOne(final T item) {
		T old;
		double rd = 0;
		double minRd = -1;
		int bucket = -1;
        final int len = items.length;
		for (int i = 0; i < len; i++) {
			old = items[i];
			if (old == null) {
				bucket = i;
				break;
			} else {
				// calculate the reference density
				rd =
					old.getReferenceCount()
						/ (double)(totalReferences - old.getTimestamp());
				if ((minRd < 0 || rd < minRd) && old.allowUnload()) {
					minRd = rd;
					bucket = i;
				}
			}
		}
		if (bucket < 0) {
		    bucket = 0;
		}
		old = items[bucket];
		if (old != null) {
			map.remove(old.getKey());
			old.sync(true);
		} else {
			used++;
		}
		items[bucket] = item;
		map.put(item.getKey(), item);
        
        if (old != null) {
            accounting.replacedPage(item);
            if (cacheManager != null && accounting.resizeNeeded()) {
//                accounting.stats();
                cacheManager.requestMem(this);
            }
        }
		return old;
	}
	
	/**
	 * Periodically adjust items with large reference counts to give
	 * younger items a chance to survive.
	 */
	private void ageReferences() {
		final int limit = ageingPeriod / 10;
        for(int i = 0; i < count; i++) {
            final T item = items[i];
			if(item != null) {
				final int refCount = item.getReferenceCount();
				if(refCount > limit) {
					item.setReferenceCount(refCount - limit);
				} else {
				    item.setReferenceCount(1);
				}
			}
		}
		nextCleanup += ageingPeriod;
	}
	
	/**
	 * Periodically reset all reference counts to 1.
	 */
	protected void cleanup() {
	    if(LOG.isDebugEnabled()) {
            LOG.debug("totalReferences = " + totalReferences + "; maxReferences = " + maxReferences);
        }
		totalReferences = count;
        for(int i = 0; i < count; i++) {
            final T item = items[i];
			if(item != null) {
				item.setReferenceCount(1);
				item.setTimestamp(1);
			}
		}
		nextCleanup = totalReferences + ageingPeriod;
	}
}
