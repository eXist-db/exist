package org.exist.collections;

import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRDCache;
import org.exist.util.Lock;
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

	/**
	 * Overwritten to lock collections before they are removed.
	 */
	protected Cacheable removeOne(Cacheable item) {
		Collection old;
		Lock lock;
		double rd = 0, minRd = -1;
		int bucket = -1;
		boolean unloadable;
		for (int i = 0; i < items.length; i++) {
			old = (Collection)items[i];
			if (old == null) {
				bucket = i;
				break;
			} else {
				lock = old.getLock(); 
					// calculate the reference density
					rd =
						old.getReferenceCount()
							/ (double)(totalReferences - old.getTimestamp());
					// attempt to acquire a read lock on the collection.
					// the collection is not considered for removal if the lock 
					// cannot be acquired immediately.
					if(lock.attempt(Lock.READ_LOCK)) {
						if ((minRd < 0 || rd < minRd) && old.allowUnload()) {
							minRd = rd;
							bucket = i;
						}
						lock.release();
					}
			}
		}
		old = (Collection)items[bucket];
		if (old != null) {
			map.remove(old.getKey());
			names.remove(old.getName());
			old.sync();
		}
		items[bucket] = item;
		map.put(item.getKey(), item);
		return old;
	}

    public void remove(Cacheable item) {
        super.remove(item);
        names.remove(((Collection)item).getName());
    }
}
