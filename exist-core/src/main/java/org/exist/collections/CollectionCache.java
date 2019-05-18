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
package org.exist.collections;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.LRUCache;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
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
@NotThreadSafe
public class CollectionCache extends LRUCache<Collection> implements BrokerPoolService {
    private final static Logger LOG = LogManager.getLogger(CollectionCache.class);

    private final BrokerPool pool;
    private Object2LongMap<String> names;

    public CollectionCache(final BrokerPool pool, final int blockBuffers, final double growthThreshold) {
        super("collection cache", blockBuffers, 2.0, growthThreshold, CacheManager.DATA_CACHE);
        this.pool = pool;
        this.names = new Object2LongOpenHashMap<>(blockBuffers);
        this.names.defaultReturnValue(-1);
    }

    @Override
    public void add(final Collection collection) {
        add(collection, 1);
    }

    @Override
    public void add(final Collection collection, final int initialRefCount) {
        // don't cache the collection during initialization: SecurityManager is not yet online
        if(!pool.isOperational()) {
            return;
        }

        super.add(collection, initialRefCount);
        final String name = collection.getURI().getRawCollectionPath();
        names.put(name, collection.getKey());
    }

    @Override
    public Collection get(final Collection collection) {
        return get(collection.getKey());
    }

    public Collection get(final XmldbURI name) {
        final long key = names.getLong(name.getRawCollectionPath());
        if (key < 0) {
            return null;
        }
        return get(key);
    }

    // TODO(AR) we have a mix of concerns here, we should not involve collection locking in the operation of the cache  or invalidating the collectionConfiguration
    /**
     * Overwritten to lock collections before they are removed.
     */
    @Override
    protected void removeOne(final Collection item) {
        boolean removed = false;
        SequencedLongHashMap.Entry<Collection> next = map.getFirstEntry();
        int tries = 0;
        do {
            final Collection cached = next.getValue();
            if(cached.getKey() != item.getKey()) {
                final Lock lock = cached.getLock();
                if (lock.attempt(LockMode.READ_LOCK)) {
                    try {
                        if (cached.allowUnload()) {
                            if(pool.getConfigurationManager() != null) { // might be null during db initialization
                                pool.getConfigurationManager().invalidate(cached.getURI(), null);
                            }
                            names.removeLong(cached.getURI().getRawCollectionPath());
                            cached.sync(true);
                            map.remove(cached.getKey());
                            removed = true;
                        }
                    } finally {
                        lock.release(LockMode.READ_LOCK);
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

    @Override
    public void remove(final Collection item) {
        super.remove(item);
        names.removeLong(item.getURI().getRawCollectionPath());

        // might be null during db initialization
        if(pool.getConfigurationManager() != null) {
            pool.getConfigurationManager().invalidate(item.getURI(), null);
        }
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
        for (final LongIterator i = names.values().iterator(); i.hasNext(); ) {
            final Collection collection = get(i.nextLong());
            if (collection != null) {
                size += collection.getMemorySizeNoLock();
            }
        }
        return size;
    }

    @Override
    public void resize(final int newSize) {
        if (newSize < max) {
            shrink(newSize);
        } else {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Growing collection cache to " + newSize);
            }
            final SequencedLongHashMap<Collection> newMap = new SequencedLongHashMap<>(newSize * 2);
            final Object2LongMap<String> newNames = new Object2LongOpenHashMap<>(newSize);
            newNames.defaultReturnValue(-1);
            for(SequencedLongHashMap.Entry<Collection> next = map.getFirstEntry(); next != null; next = next.getNext()) {
                final Collection cacheable = next.getValue();
                newMap.put(cacheable.getKey(), cacheable);
                newNames.put(cacheable.getURI().getRawCollectionPath(), cacheable.getKey());
            }
            max = newSize;
            map = newMap;
            names = newNames;
            accounting.reset();
            accounting.setTotalSize(max);
        }
    }

    @Override
    protected void shrink(final int newSize) {
        super.shrink(newSize);
        names = new Object2LongOpenHashMap<>(newSize);
        names.defaultReturnValue(-1);
    }
}
