/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * \$Id\$
 */

package org.exist.storage.cache;

import org.exist.util.hashtable.SequencedLongHashMap;

/**
 * This cache implementation always tries to keep the inner btree pages in
 * cache, while the leaf pages can be removed.
 */
public class BTreeCache extends LRUCache {

    public BTreeCache(int size, double growthFactor, double growthThreshold, String type) {
        super(size, growthFactor, growthThreshold, type);
    }

    public void add(Cacheable item, int initialRefCount) {
        add(item);
    }

    public void add(Cacheable item) {
        map.put(item.getKey(), item);
        if (map.size() >= max + 1) {
            removeNext((BTreeCacheable) item);
        }
    }

    protected void removeNext(BTreeCacheable item) {
        boolean removed = false;
        boolean mustRemoveInner = false;
        SequencedLongHashMap.Entry<Cacheable> next = map.getFirstEntry();
        do {
            final BTreeCacheable cached = (BTreeCacheable)next.getValue();
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
