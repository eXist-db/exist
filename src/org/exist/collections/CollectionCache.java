package org.exist.collections;

import org.exist.storage.BrokerPool;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRDCache;
import org.exist.storage.lock.Lock;
import org.exist.util.hashtable.Long2ObjectHashMap;
import org.exist.util.hashtable.Object2LongHashMap;
import org.exist.xmldb.XmldbURI;

/**
 * Global cache for {@link org.exist.collections.Collection} objects. The
 * cache is owned by {@link org.exist.storage.index.CollectionStore}. It is not
 * synchronized. Thus a lock should be obtained on the collection store before
 * accessing the cache.
 * 
 * @author wolf
 */
public class CollectionCache extends LRDCache {

	private Object2LongHashMap names;
	private BrokerPool pool;

	public CollectionCache(BrokerPool pool, int blockBuffers, double growthThreshold) {
		super(blockBuffers, 1.8, growthThreshold, CacheManager.DATA_CACHE);
        this.names = new Object2LongHashMap(blockBuffers);
		this.pool = pool;
        setFileName("collections.dbx");
    }
	
	public void add(Collection collection) {
		add(collection, 1);
	}

	public void add(Collection collection, int initialRefCount) {
		super.add(collection, initialRefCount);
        String name = collection.getURI().getRawCollectionPath();
        names.put(name, collection.getKey());
	}

	public Collection get(Collection collection) {
		return (Collection) get(collection.getKey());
	}

	public Collection get(XmldbURI name) {
		long key = names.get(name.getRawCollectionPath());
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
						lock.release(Lock.READ_LOCK);
					}
			}
		}
		if (bucket < 0)
			bucket = 0;
		old = (Collection)items[bucket];
		if (old != null) {
			if(pool.getConfigurationManager()!=null){ // might be null during db initialization
				pool.getConfigurationManager().invalidate(old.getURI());
            }
			map.remove(old.getKey());
			names.remove(old.getURI().getRawCollectionPath());
			old.sync(true);
		}
		items[bucket] = item;
		map.put(item.getKey(), item);

        if (cacheManager != null) {
            cacheManager.requestMem(this);
        }
		return old;
	}

    public void remove(Cacheable item) {
    	final Collection col = (Collection) item;
        super.remove(item);
        names.remove(col.getURI().getRawCollectionPath());
        if(pool.getConfigurationManager() != null) // might be null during db initialization
           pool.getConfigurationManager().invalidate(col.getURI());
    }

    /**
     * Compute and return the in-memory size of all collections
     * currently contained in this cache.
     *
     * @see org.exist.storage.CollectionCacheManager
     * @return in-memory size in bytes.
     */
    public int getRealSize() {
        int size = 0;
        for (int i = 0; i < items.length; i++) {
            Collection item = (Collection) items[i];
            if (item != null)
                size += item.getMemorySize();
        }
        return size;
    }

    public void resize(int newSize) {
        if (newSize < size) {
            shrink(newSize);
            names = new Object2LongHashMap(newSize);
        } else {
            LOG.debug("Growing cache from " + size + " to " + newSize);
            Cacheable[] newItems = new Cacheable[newSize];
            Long2ObjectHashMap newMap = new Long2ObjectHashMap(newSize);
            Object2LongHashMap newNames = new Object2LongHashMap(newSize);
            for (int i = 0; i < count; i++) {
                newItems[i] = items[i];
                if (items[i] != null) {
                    newMap.put(items[i].getKey(), items[i]);
                    newNames.put(((Collection) items[i]).getURI().getRawCollectionPath(), items[i].getKey());
                }
            }
            this.size = newSize;
            this.map = newMap;
            this.names = newNames;
            this.items = newItems;
            accounting.reset();
            accounting.setTotalSize(size);
        }
    }
}
