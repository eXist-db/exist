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

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
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
    //TODO(AR) make package private / protected
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

    //TODO(AR) Collection locks should be switched to Java's ReentrantReadWriteLock and must use the Fair Scheduler to get FIFO like ordering

    //See Concurrency of Operations on B-Trees - Bayer and Schkolnick 1977 - Solution 2
    public ManagedCollectionLock acquireCollectionReadLock(final XmldbURI collectionPath) throws LockException {
        final XmldbURI[] segments = collectionPath.getPathSegments();

        String path = '/' + segments[0].toString();
        final ReentrantReadWriteLock root = getCollectionLock(path);
        if(!root.acquire(Lock.LockMode.READ_LOCK)) {
            throw new LockException("Unable to acquire READ_LOCK for: " + path);
        }

        ReentrantReadWriteLock current = root;

        for(int i = 1; i < segments.length; i++) {
            path += '/' + segments[i].toString();
            final ReentrantReadWriteLock son = getCollectionLock(path);
            if(!son.acquire(Lock.LockMode.READ_LOCK)) {
                current.release(Lock.LockMode.READ_LOCK);
                throw new LockException("Unable to acquire READ_LOCK for: " + path);
            }

            current.release(Lock.LockMode.READ_LOCK);
            current = son;
        }

        final ReentrantReadWriteLock collectionReadLock = current;

        return new ManagedCollectionLock(Either.Left(collectionReadLock), () -> collectionReadLock.release(Lock.LockMode.READ_LOCK));
    }

    //TODO(AR) there are several reasons we might lock a collection for writes
        // 1) When we also need to modify its parent:
            // 1.1) to remove a collection (which requires also modifying its parent)
            // 1.2) to add a new collection (which also requires modifying its parent)
            // 1.3) to rename a collection (which also requires modifying its parent)
            //... So we take read locks all the way down, util the parent collection which we write lock, and then we write lock the collection

        // 2) When we just need to modify its properties:
            // 2.1) to add/remove/rename the child documents of the collection
            // 2.2) to modify the collections metadata (permissions, timestamps etc)
            //... So we read lock all the way down until the actual collection which we write lock

    public ManagedCollectionLock acquireCollectionWriteLock(final XmldbURI collectionPath, final boolean lockParent) throws LockException {
        final XmldbURI[] segments = collectionPath.getPathSegments();

        String path = '/' + segments[0].toString();
        final Lock.LockMode rootMode = segments.length == 1 || (segments.length == 2 && lockParent) ? Lock.LockMode.WRITE_LOCK : Lock.LockMode.READ_LOCK;

        final ReentrantReadWriteLock root = getCollectionLock(path);
        if(!root.acquire(rootMode)) {
            throw new LockException("Unable to acquire " + rootMode.name() + " for: " + path);
        }

        Lock.LockMode currentMode = rootMode;
        ReentrantReadWriteLock current = root;
        ReentrantReadWriteLock parent = null;

        final int lastSegmentIdx = segments.length - 1;

        for(int i = 1; i < segments.length; i++) {
            path += '/' + segments[i].toString();
            final Lock.LockMode sonMode = i == lastSegmentIdx || (i == segments.length - 2 && lockParent) ? Lock.LockMode.WRITE_LOCK : Lock.LockMode.READ_LOCK;
            final ReentrantReadWriteLock son = getCollectionLock(path);
            if(!son.acquire(sonMode)) {
                current.release(currentMode);
                throw new LockException("Unable to acquire " + currentMode.name() + " for: " + path);
            }

            if(!(i == lastSegmentIdx && lockParent)) {
                current.release(currentMode);
            } else {
                parent = current;
            }

            currentMode = sonMode;
            current = son;
        }

        if(lockParent && parent != null) {
            //we return two locks as a single managed lock, the first lock is the parent collection and the second is the actual collection
            final ReentrantReadWriteLock parentCollectionLock = parent;
            final ReentrantReadWriteLock collectionLock = current;
            return new ManagedCollectionLock(Either.Right(new Tuple2<>(parentCollectionLock, collectionLock)), () -> {
                    //TODO(AR) should this order be inverted?
                    collectionLock.release(Lock.LockMode.WRITE_LOCK);
                    parentCollectionLock.release(Lock.LockMode.WRITE_LOCK);
            });
        } else {
            final ReentrantReadWriteLock collectionLock = current;
            return new ManagedCollectionLock(Either.Left(collectionLock), () -> collectionLock.release(Lock.LockMode.WRITE_LOCK));
        }
    }

    /**
     * Optimized locking method for acquiring a lock on a Collection, when we already hold a lock on the
     * parent collection. In that instance we can avoid descending the locking tree again
     */
    public ManagedCollectionLock acquireCollectionWriteLock(final ManagedCollectionLock parentLock, final XmldbURI collectionPath) throws LockException {
        if(Objects.isNull(parentLock)) {
            throw new LockException("Cannot acquire a sub-Collection lock without a lock for the parent");
        }
        if(parentLock.isReleased()) {
            throw new LockException("Cannot acquire a sub-Collection lock without a lock on the parent");
        }
        if(!parentLock.getPath().equals(collectionPath.removeLastSegment())) {
            throw new LockException("Cannot acquire a lock on sub-Collection, as provided parent lock is not the parent");
        }

        final ReentrantReadWriteLock subCollectionLock = getCollectionLock(collectionPath.getCollectionPath());
        if(!subCollectionLock.acquire(Lock.LockMode.WRITE_LOCK)) {
            throw new LockException("Unable to acquire WRITE_LOCK for: " + collectionPath);
        }
        return new ManagedCollectionLock(Either.Left(subCollectionLock), () -> subCollectionLock.release(Lock.LockMode.WRITE_LOCK));
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
