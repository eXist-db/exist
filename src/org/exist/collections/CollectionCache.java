/*
 * CollectionCache.java - Mar 11, 2003
 * 
 * @author wolf
 */
package org.exist.collections;

import org.exist.storage.cache.ClockCache;
import org.exist.util.hashtable.Object2LongHashMap;


public class CollectionCache extends ClockCache {

	private Object2LongHashMap names;
	
	public CollectionCache(int blockBuffers) {
		super(blockBuffers);
		names = new Object2LongHashMap(blockBuffers);
	}

	public void add(Collection collection) {
		add(collection, 1);
	}

	public void add(Collection collection, int initialRefCount) {
		super.add(collection, initialRefCount);
		names.put(collection.getName(), collection.getKey());
	}

	public Collection get(Collection collection) {
		return (Collection)get(collection.getKey());
	}

	public Collection get(String name) {
		long key = names.get(name);
		if(key < 0)
			return null;
		return (Collection)get(key);
	}
	
	public void remove(Collection collection) {
		super.remove(collection);
	}
	
}
