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
public class GClockCache implements Cache {

	protected Cacheable[] items;
	protected int count = 0;
	protected int size;
	protected Long2ObjectHashMap map;
	protected int hits = 0, fails = 0;

	public GClockCache(int size) {
		this.size = size;
		this.items = new Cacheable[size];
		this.map = new Long2ObjectHashMap(size);
		//this.map = new Long2ObjectOpenHashMap(size);
	}

	public void add(Cacheable item) {
		add(item, 1);
	}

	public void add(Cacheable item, int initialRefCount) {
		Cacheable old = (Cacheable) map.get(item.getKey());
		if (old != null) {
			old.incReferenceCount();
			return;
		}
		item.setReferenceCount(initialRefCount);
		if (count < size) {
			items[count++] = item;
			map.put(item.getKey(), item);
		} else
			removeOne(item);
	}

	public Cacheable get(Cacheable item) {
		return get(item.getKey());
	}

	public Cacheable get(long key) {
		Cacheable item = (Cacheable) map.get(key);
		if (item == null) {
			fails++;
		} else
			hits++;
		return item;
	}

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

	public void flush() {
		for (int i = 0; i < count; i++) {
			if (items[i] != null)
				items[i].sync();
		}
	}

	protected void removeOne(Cacheable item) {
		Cacheable old;
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
					//LOG.debug(fileName + " replacing " + old.getKey() + " for " + item.getKey());
					map.remove(old.getKey());
					old.sync();
				}
				items[bucket] = item;
				map.put(item.getKey(), item);
				removed = true;
			}
		} while (!removed);
	}

	public int getBuffers() {
		return size;
	}

	public int getUsedBuffers() {
		return count;
	}

	public int getHits() {
		return hits;
	}

	public int getFails() {
		return fails;
	}

	public void setFileName(String fileName) {
	}
}
