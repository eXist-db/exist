/*
 * CollectionCache.java - Mar 11, 2003
 * 
 * @author wolf
 */
package org.exist.util;

import it.unimi.dsi.fastutil.Object2ObjectAVLTreeMap;

import java.util.Iterator;

import org.exist.dom.Collection;

public class CollectionCache {

	public final static int BUFFER_SIZE = 32;
	protected int buffers;

	protected int fails = 0;
	protected int hits = 0;
	protected Object2ObjectAVLTreeMap map;

	public CollectionCache(int blockBuffers) {
		this.buffers = blockBuffers;
		map = new Object2ObjectAVLTreeMap();
	}

	public CollectionCache() {
		this(BUFFER_SIZE);
	}

	public void add(Collection collection) {
		add(collection, 1);
	}

	public void add(Collection collection, int initialRefCount) {
		final String name = collection.getName();
		if (map.containsKey(name)) {
			collection.incRefCount();
			return;
		}
		collection.setRefCount(initialRefCount);
		map.put(name, collection);
		while (map.size() >= buffers)
			removeOne(collection);
	}

	public Collection get(Collection collection) {
		return get(collection.getName());
	}

	public Collection get(String name) {
		final Collection collection = (Collection)map.get(name);
		if (collection == null)
			fails++;
		else {
			collection.incRefCount();
			hits++;
		}
		return collection;
	}

	public void remove(Collection collection) {
		map.remove(collection.getName());
	}

	public void clear() {
		map.clear();
	}
	
	private final void removeOne(Collection collection) {
		Collection old;
		boolean removed = false;
		long oldId, id;
		while (!removed) {
			for (Iterator i = map.values().iterator(); i.hasNext();) {
				old = (Collection) i.next();
				old.decRefCount();
				// replace old page if it has reference count < 1,
				if (old.getRefCount() < 1) {
					i.remove();
					//map.remove(oldNum);
					removed = true;
					old = null;
					return;
				}
			}
		}
	}
}
