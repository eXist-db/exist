/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.jcip.annotations.NotThreadSafe;

import java.util.Iterator;

/**
 * This cache implementation always tries to keep the inner btree pages in
 * cache, while the leaf pages can be removed.
 */
@NotThreadSafe
public class BTreeCache<T extends BTreeCacheable> extends LRUCache<T> {

    public BTreeCache(final String name, final int size, final double growthFactor, final double growthThreshold, final CacheType type) {
        super(name, size, growthFactor, growthThreshold, type);
    }

    @Override
    public void add(final T item, final int initialRefCount) {
        add(item);
    }

    @Override
    public void add(final T item) {
        map.put(item.getKey(), item);
        if (map.size() >= max + 1) {
            removeNext(item);
        }
    }

    private void removeNext(final T item) {
        boolean removed = false;
        boolean mustRemoveInner = false;
        Iterator<Long2ObjectMap.Entry<T>> iterator = map.fastEntrySetIterator();
        do {
            final Long2ObjectMap.Entry<T> next = iterator.next();
            final T cached = next.getValue();
            if(cached.allowUnload() && cached.getKey() != item.getKey() &&
                    (mustRemoveInner || !cached.isInnerPage())) {
                cached.sync(true);
                map.remove(next.getLongKey());
                removed = true;
            } else {
                if (!iterator.hasNext()) {
                    // reset the iterator to the beginning
                    iterator = map.fastEntrySetIterator();      // TODO(AR) this can cause a never ending loop potentially!

                    mustRemoveInner = true;
                }
            }
        } while(!removed);
        accounting.replacedPage(item);
        if (growthFactor > 1.0 && accounting.resizeNeeded()) {
            cacheManager.requestMem(this);
        }
    }
}
