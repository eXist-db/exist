/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.storage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A Lock Manager for Locks that are used across
 * database instance functions
 *
 * Maintains Maps of {@link WeakReference<Lock>} by ID.
 * There is a unique lock for each ID, and calls with the same
 * ID will always return the same lock. Different IDs will always
 * receive different locks.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockManager {

    private static final Logger LOG = LogManager.getLogger(LockManager.class);
    private static final int INITIAL_COLLECTION_LOCK_CAPACITY = 1000;
    private static final float COLLECTION_LOCK_LOAD_FACTOR = 0.75f;

    private final ReferenceQueue<ReentrantReadWriteLock> collectionLockReferences;
    private final ConcurrentMap<String, WeakReference<ReentrantReadWriteLock>> collectionLocks;

    public LockManager(final int concurrencyLevel) {
        this.collectionLocks = new ConcurrentHashMap<>(INITIAL_COLLECTION_LOCK_CAPACITY, COLLECTION_LOCK_LOAD_FACTOR, concurrencyLevel);
        this.collectionLockReferences = new ReferenceQueue<>();

        LOG.info("Configured LockManager with concurrencyLevel={}", concurrencyLevel);
    }

    //TODO(AR) abstract getCollectionLock out as a StripedLock<T> where T is a String or other thing

    /**
     * Retrieves a lock for a Collection
     *
     * This function is concerned with just the lock object
     * and has no knowledge of the state of the lock. The only
     * guarantee is that if this lock has not been requested before
     * then it will be provided in the unlocked state
     *
     * @param collectionPath The path of the Collection for which a lock is requested
     *
     * @return A lock for the Collection
     */
    public ReentrantReadWriteLock getCollectionLock(final String collectionPath) {
        // calculate a value if not present or if the weak reference has expired
        final WeakReference<ReentrantReadWriteLock> collectionLockRef =
                collectionLocks.compute(collectionPath, (key, value) -> {
                    if(value == null || value.get() == null) {
                        drainClearedReferences(collectionLockReferences, collectionLocks);
                        return new WeakReference<>(new ReentrantReadWriteLock(key), collectionLockReferences);
                    } else {
                        return value;
                    }
                });

        // check the weak reference before returning!
        final ReentrantReadWriteLock collectionLock = collectionLockRef.get();
        if(collectionLock != null) {
            return collectionLock;
        }

        // weak reference has expired in the mean time, regenerate
        return getCollectionLock(collectionPath);
    }

    /**
     * Removes any cleared references from a map of weak references
     *
     * @param referenceQueue The queue that holds notification of cleared references
     * @param map The map from which to remove the cleared references
     *
     * @param <K> The key type of the map
     * @param <V> The value type inside the {@link WeakReference<V>}
     */
    private <K, V> void drainClearedReferences(final ReferenceQueue<V> referenceQueue, final ConcurrentMap<K, WeakReference<V>> map) {
        Reference<? extends V> ref = null;
        while ((ref = referenceQueue.poll()) != null) {
            final WeakReference<? extends V> lockRef = (WeakReference<? extends V>)ref;
            map.values().remove(lockRef);
        }
    }
}
