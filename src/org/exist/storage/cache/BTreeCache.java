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
package org.exist.storage.cache;

import net.jcip.annotations.NotThreadSafe;
import org.exist.util.hashtable.SequencedLongHashMap;

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
        SequencedLongHashMap.Entry<T> next = map.getFirstEntry();
        do {
            final T cached = next.getValue();
            if(cached.allowUnload() && cached.getKey() != item.getKey() &&
                    (mustRemoveInner || !cached.isInnerPage())) {
                cached.sync(true);
                map.remove(next.getKey());
                removed = true;
            } else {
                next = next.getNext();
                if(next == null) {
                    next = map.getFirstEntry();
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
