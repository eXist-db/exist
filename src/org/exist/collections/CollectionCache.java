package org.exist.collections;

import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRDCache;
import org.exist.util.hashtable.Object2LongHashMap;

/**
 * Global cache for {@link org.exist.collections.Collection} objects. The
 * cache is owned by {@link org.exist.storage.store.CollectionStore}. It is not
 * synchronized. Thus a lock should be obtained on the collection store before
 * accessing the cache.
 * 
 * @author wolf
 */
public class CollectionCache extends LRDCache {

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
		return (Collection) get(collection.getKey());
	}

	public Collection get(String name) {
		long key = names.get(name);
		if (key < 0)
			return null;
		return (Collection) get(key);
	}

	protected Cacheable removeOne(Cacheable item) {
		Cacheable old = super.removeOne(item);
		if(old != null) {
			names.remove(((Collection)old).getName());
		}
		return old;
	}

    public void remove(Cacheable item) {
        super.remove(item);
        names.remove(((Collection)item).getName());
    }
}
