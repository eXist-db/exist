/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.exist.util.hashtable.Long2ObjectHashMap;

/**
 * Simple clock implementation of a cache.
 * 
 * Implements a replacement strategy similar to LRU, but uses a
 * single bit flag in each page. On every access to a page, the flag
 * is set to 1. If the cache is full, the class iterates through all pages
 * in the cache. The first page with flag = 0 is removed. Otherwise, if a page 
 * with flag = 1 is found, the flag is set to 0. Thus, each page survives
 * at least two iterations through the array.
 * 
 * @author wolf
 */
public class ClockCache implements Cache {

	protected Long2ObjectHashMap map;
	protected Cacheable[] items;
	protected int size;
	protected int count = 0;
	protected int hits = 0, fails = 0;
	
	private long lastSync = System.currentTimeMillis();
	private long syncPeriod = 15000;
	
	public ClockCache(int size) {
		this.size = size;
		items = new Cacheable[size];
		map = new Long2ObjectHashMap(size);
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable, int)
	 */
	public void add(Cacheable item, int initialRefCount) {
		add(item);
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable)
	 */
	public void add(Cacheable item) {
		Cacheable old = (Cacheable) map.get(item.getKey());
		if (old != null) {
			old.setReferenceCount(1);
		} else {
			item.setReferenceCount(1);
			if (count < size) {
				items[count++] = item;
				map.put(item.getKey(), item);
			} else
				removeOne(item);
		}
		//if(System.currentTimeMillis() - lastSync > syncPeriod)
		//	flush();
	}

	protected Cacheable removeOne(Cacheable item) {
		int bucket = -1;
		Cacheable old;
		do {
			for (int i = 0; i < count; i++) {
				old = items[i];
				if (old == null) {
					bucket = i;
				} else {
					if (old.getReferenceCount() == 0) {
						if (bucket < 0)
							bucket = i;
					} else
						old.setReferenceCount(0);
				}
			}
		} while (bucket < 0);

		old = items[bucket];
		if (old != null) {
			map.remove(old.getKey());
			old.sync();
		}
		items[bucket] = item;
		map.put(item.getKey(), item);
		return old;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(org.exist.storage.cache.Cacheable)
	 */
	public Cacheable get(long key) {
		Cacheable item = (Cacheable) map.get(key);
		if (item == null) {
			fails++;
		} else
			hits++;
		return item;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(long)
	 */
	public Cacheable get(Cacheable item) {
		return get(item.getKey());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#remove(org.exist.storage.cache.Cacheable)
	 */
	public void remove(Cacheable item) {
		long key = item.getKey();
		Cacheable cacheable = (Cacheable) map.remove(key);
		if (cacheable == null)
			return;
		for (int i = 0; i < count; i++) {
			if (items[i] != null && items[i].getKey() == key) {
				items[i] = null;
				return;
			}
		}
		LOG.error("item not found in list");
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#flush()
	 */
	public void flush() {
		for(int i = 0; i < count; i++)
			if(items[i] != null)
				items[i].sync();
		lastSync = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getBuffers()
	 */
	public int getBuffers() {
		return size;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getUsedBuffers()
	 */
	public int getUsedBuffers() {
		return count;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getHits()
	 */
	public int getHits() {
		return hits;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getFails()
	 */
	public int getFails() {
		return fails;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#setFileName(java.lang.String)
	 */
	public void setFileName(String fileName) {
	}

}
