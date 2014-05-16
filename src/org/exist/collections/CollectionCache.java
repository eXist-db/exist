/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.collections;

import java.util.Iterator;

import org.exist.storage.BrokerPool;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRUCache;
import org.exist.storage.lock.Lock;
import org.exist.util.hashtable.Object2LongHashMap;
import org.exist.util.hashtable.SequencedLongHashMap;
import org.exist.xmldb.XmldbURI;

/**
 * Global cache for {@link org.exist.collections.Collection} objects. The
 * cache is owned by {@link org.exist.storage.index.CollectionStore}. It is not
 * synchronized. Thus a lock should be obtained on the collection store before
 * accessing the cache.
 * 
 * @author wolf
 */
public class CollectionCache extends LRUCache {

    private Object2LongHashMap names;
    private BrokerPool pool;

    public CollectionCache(BrokerPool pool, int blockBuffers, double growthThreshold) {
        super(blockBuffers, 2.0, 0.000001, CacheManager.DATA_CACHE);
        this.names = new Object2LongHashMap(blockBuffers);
        this.pool = pool;
        setFileName("collection cache");
    }

    public void add(Collection collection) {
        add(collection, 1);
    }

    public void add(Collection collection, int initialRefCount) {
        super.add(collection, initialRefCount);
        final String name = collection.getURI().getRawCollectionPath();
        names.put(name, collection.getKey());
    }

    public Collection get(Collection collection) {
        return (Collection) get(collection.getKey());
    }

    public Collection get(XmldbURI name) {
        final long key = names.get(name.getRawCollectionPath());
        if (key < 0) {
            return null;
        }
        return (Collection) get(key);
    }

    /**
     * Overwritten to lock collections before they are removed.
     */
    protected void removeOne(Cacheable item) {
        boolean removed = false;
        SequencedLongHashMap.Entry<Cacheable> next = map.getFirstEntry();
        int tries = 0;
        do {
            final Cacheable cached = next.getValue();
            if(cached.getKey() != item.getKey()) {
                final Collection old = (Collection) cached;
                final Lock lock = old.getLock();
                if (lock.attempt(Lock.READ_LOCK)) {
                    try {
                        if (cached.allowUnload()) {
                            if(pool.getConfigurationManager()!=null) { // might be null during db initialization
                                pool.getConfigurationManager().invalidate(old.getURI(), null);
                            }
                            names.remove(old.getURI().getRawCollectionPath());
                            cached.sync(true);
                            map.remove(cached.getKey());
                            removed = true;
                        }
                    } finally {
                        lock.release(Lock.READ_LOCK);
                    }
                }
            }
            if (!removed) {
                next = next.getNext();
                if (next == null && tries < 2) {
                    next = map.getFirstEntry();
                    tries++;
                } else {
                    LOG.info("Unable to remove entry");
                    removed = true;
                }
            }
        } while(!removed);
        cacheManager.requestMem(this);
    }

    public void remove(Cacheable item) {
        final Collection col = (Collection) item;
        super.remove(item);
        names.remove(col.getURI().getRawCollectionPath());
        if(pool.getConfigurationManager() != null) // might be null during db initialization
           {pool.getConfigurationManager().invalidate(col.getURI(), null);}
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
        for (final Iterator<Long> i = names.valueIterator(); i.hasNext(); ) {
            final Collection collection = (Collection) get(i.next());
            if (collection != null) {
                size += collection.getMemorySize();
            }
        }
        return size;
    }

    public void resize(int newSize) {
        if (newSize < max) {
            shrink(newSize);
        } else {
            LOG.debug("Growing collection cache to " + newSize);
            SequencedLongHashMap<Cacheable> newMap = new SequencedLongHashMap<Cacheable>(newSize * 2);
            Object2LongHashMap newNames = new Object2LongHashMap(newSize);
            SequencedLongHashMap.Entry<Cacheable> next = map.getFirstEntry();
            Cacheable cacheable;
            while(next != null) {
                cacheable = next.getValue();
                newMap.put(cacheable.getKey(), cacheable);
                newNames.put(((Collection) cacheable).getURI().getRawCollectionPath(), cacheable.getKey());
                next = next.getNext();
            }
            max = newSize;
            map = newMap;
            names = newNames;
            accounting.reset();
            accounting.setTotalSize(max);
        }
    }

    @Override
    protected void shrink(int newSize) {
        super.shrink(newSize);
        names = new Object2LongHashMap(newSize);
    }
}
