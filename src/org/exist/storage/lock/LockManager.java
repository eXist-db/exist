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
import org.exist.storage.lock.Lock.LockType;
import org.exist.util.LockException;
import org.exist.util.WeakLazyStripes;
import org.exist.xmldb.XmldbURI;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Lock Manager for Locks that are used across
 * database instance functions
 *
 * There is a unique lock for each ID, and calls with the same
 * ID will always return the same lock. Different IDs will always
 * receive different locks.
 *
 * The locking protocol for Collection locks is taken from the paper:
 *     Concurrency of Operations on B-Trees - Bayer and Schkolnick 1977
 *     {@see https://link.springer.com/article/10.1007/BF00263762}
 * specifically we have adopted Solution 2 presented in Section 3 of the paper
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockManager {
    public final static String PROP_UPGRADE_CHECK = "exist.lockmanager.upgrade.check";

    private static final Logger LOG = LogManager.getLogger(LockManager.class);
    private static final boolean USE_FAIR_SCHEDULER = true;  //Java's ReentrantReadWriteLock must use the Fair Scheduler to get FIFO like ordering
    private static final LockTable lockTable = LockTable.getInstance();

    /**
     * Set to true to enable checking for lock upgrading within the same
     * thread, i.e. READ_LOCK -> WRITE_LOCK
     */
    private volatile boolean upgradeCheck = Boolean.getBoolean(PROP_UPGRADE_CHECK);

    private final WeakLazyStripes<String, ReentrantReadWriteLock> collectionLocks;
    private final WeakLazyStripes<String, ReentrantReadWriteLock> documentLocks;


    public LockManager(final int concurrencyLevel) {
        this.collectionLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createCollectionLock);
        this.documentLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createDocumentLock);
        LOG.info("Configured LockManager with concurrencyLevel={}", concurrencyLevel);
    }

    /**
     * Creates a new lock for a Collection
     * will be Striped by the collectionPath
     */
    private static ReentrantReadWriteLock createCollectionLock(final String collectionPath) {
        return new ReentrantReadWriteLock(USE_FAIR_SCHEDULER);
    }

    /**
     * Creates a new lock for a Document
     * will be Striped by the collectionPath
     */
    private static ReentrantReadWriteLock createDocumentLock(final String documentPath) {
        return new ReentrantReadWriteLock();
    }

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
    ReentrantReadWriteLock getCollectionLock(final String collectionPath) {
        return collectionLocks.get(collectionPath);
    }

    //See Concurrency of Operations on B-Trees - Bayer and Schkolnick 1977 - Solution 2
    public ManagedCollectionLock acquireCollectionReadLock(final XmldbURI collectionPath) throws LockException {
        final XmldbURI[] segments = collectionPath.getPathSegments();

        final long groupId = System.nanoTime();
        String path = '/' + segments[0].toString();

        final ReentrantReadWriteLock root = getCollectionLock(path);
        try {
            lockTable.attempt(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

            root.readLock().lockInterruptibly();

            lockTable.acquired(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            throw new LockException("Unable to acquire READ_LOCK for: " + path, e);
        }

        ReentrantReadWriteLock current = root;
        String currentPath = path;

        for(int i = 1; i < segments.length; i++) {
            path += '/' + segments[i].toString();
            final ReentrantReadWriteLock son = getCollectionLock(path);

            try {
                lockTable.attempt(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

                son.readLock().lockInterruptibly();

                lockTable.acquired(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            } catch(final InterruptedException e) {
                lockTable.attemptFailed(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

                current.readLock().unlock();
                lockTable.released(groupId, currentPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

                throw new LockException("Unable to acquire READ_LOCK for: " + path, e);
            }

            current.readLock().unlock();
            lockTable.released(groupId, currentPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

            current = son;
            currentPath = path;
        }

        final ReentrantReadWriteLock collectionReadLock = current;
        final String collectionReadLockPath = currentPath;

        return new ManagedCollectionLock(collectionPath, Either.Left(collectionReadLock.readLock()), () -> {
            collectionReadLock.readLock().unlock();
            lockTable.released(groupId, collectionReadLockPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
        });
    }

    /**
     * Similar to {@link #acquireCollectionReadLock(XmldbURI)} but non-waiting.
     * We only acquire the read lock if the write lock is not held by
     * another thread at the time of invocation.
     */
    public ManagedCollectionLock tryCollectionReadLock(final XmldbURI collectionPath) throws LockException {
        final XmldbURI[] segments = collectionPath.getPathSegments();

        final long groupId = System.nanoTime();
        String path = '/' + segments[0].toString();
        final ReentrantReadWriteLock root = getCollectionLock(path);

        lockTable.attempt(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
        boolean hasLock = root.readLock().tryLock();
        if(hasLock) {
            lockTable.acquired(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
        } else {
            lockTable.attemptFailed(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            throw new LockException("Unable to acquire READ_LOCK for: " + path);
        }

        ReentrantReadWriteLock current = root;
        String currentPath = path;

        for(int i = 1; i < segments.length; i++) {
            path += '/' + segments[i].toString();
            final ReentrantReadWriteLock son = getCollectionLock(path);

            lockTable.attempt(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            hasLock = son.readLock().tryLock();

            if(hasLock) {
                lockTable.acquired(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
            } else {
                lockTable.attemptFailed(groupId, path, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

                current.readLock().unlock();
                lockTable.released(groupId, currentPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

                throw new LockException("Unable to acquire READ_LOCK for: " + path);
            }

            current.readLock().unlock();
            lockTable.released(groupId, currentPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);

            current = son;
            currentPath = path;
        }

        final ReentrantReadWriteLock collectionReadLock = current;
        final String collectionReadLockPath = currentPath;

        return new ManagedCollectionLock(collectionPath, Either.Left(collectionReadLock.readLock()), () -> {
            collectionReadLock.readLock().unlock();
            lockTable.released(groupId, collectionReadLockPath, LockType.COLLECTION, Lock.LockMode.READ_LOCK);
        });
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

        final long groupId = System.nanoTime();
        String path = '/' + segments[0].toString();

        final ReentrantReadWriteLock root = getCollectionLock(path);

        final Lock.LockMode rootMode;
        final java.util.concurrent.locks.Lock rootModeLock;
        if(segments.length == 1 || (segments.length == 2 && lockParent)) {
            rootMode = Lock.LockMode.WRITE_LOCK;
            rootModeLock = root.writeLock();
        } else {
            rootMode = Lock.LockMode.READ_LOCK;
            rootModeLock = root.readLock();
        }

        if(upgradeCheck && rootMode == Lock.LockMode.WRITE_LOCK && root.getReadHoldCount() > 0) {
            throw new LockException("Lock upgrading would lead to a self-deadlock: " + path);
        }

        try {
            lockTable.attempt(groupId, path, LockType.COLLECTION, rootMode);

            rootModeLock.lockInterruptibly();

            lockTable.acquired(groupId, path, LockType.COLLECTION, rootMode);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, path, LockType.COLLECTION, rootMode);
            throw new LockException("Unable to acquire " + rootMode.name() + " for: " + path, e);
        }

        java.util.concurrent.locks.Lock currentModeLock = rootModeLock;
        Lock.LockMode currentMode = rootMode;
        String currentModePath = path;

        java.util.concurrent.locks.Lock parentModeLock = null;
        Lock.LockMode parentMode = null;
        String parentPath = null;

        final int lastSegmentIdx = segments.length - 1;

        for(int i = 1; i < segments.length; i++) {
            path += '/' + segments[i].toString();

            final ReentrantReadWriteLock son = getCollectionLock(path);

            final Lock.LockMode sonMode;
            final java.util.concurrent.locks.Lock sonModeLock;
            if(i == lastSegmentIdx || (i == segments.length - 2 && lockParent)) {
                sonMode = Lock.LockMode.WRITE_LOCK;
                sonModeLock = son.writeLock();
            } else {
                sonMode = Lock.LockMode.READ_LOCK;
                sonModeLock = son.readLock();
            }

            if(upgradeCheck && sonMode == Lock.LockMode.WRITE_LOCK && son.getReadHoldCount() > 0) {
                currentModeLock.unlock();
                lockTable.released(groupId, currentModePath, LockType.COLLECTION, currentMode);

                throw new LockException("Lock upgrading would lead to a self-deadlock: " + path);
            }

            try {
                lockTable.attempt(groupId, path, LockType.COLLECTION, sonMode);

                sonModeLock.lockInterruptibly();

                lockTable.acquired(groupId, path, LockType.COLLECTION, sonMode);
            } catch(final InterruptedException e) {
                lockTable.attemptFailed(groupId, path, LockType.COLLECTION, sonMode);

                currentModeLock.unlock();
                lockTable.released(groupId, currentModePath, LockType.COLLECTION, currentMode);

                throw new LockException("Unable to acquire " + sonMode.name() + " for: " + path, e);
            }

            if(!(i == lastSegmentIdx && lockParent)) {
                currentModeLock.unlock();
                lockTable.released(groupId, currentModePath, LockType.COLLECTION, currentMode);
            } else {
                parentModeLock = currentModeLock;
                parentMode = currentMode;
                parentPath = currentModePath;
            }

            currentModeLock = sonModeLock;
            currentMode = sonMode;
            currentModePath = path;
        }

        final String collectionPathStr = path;

        if(lockParent && parentModeLock != null) {
            //we return two locks as a single managed lock, the first lock is the parent collection and the second is the actual collection
            final java.util.concurrent.locks.Lock parentCollectionLock = parentModeLock;
            final Lock.LockMode parentCollectionMode = parentMode;
            final String parentCollectionString = parentPath;

            final java.util.concurrent.locks.Lock collectionLock = currentModeLock;
            final Lock.LockMode collectionMode = currentMode;

            return new ManagedCollectionLock(collectionPath, Either.Right(new Tuple2<>(parentCollectionLock, collectionLock)), () -> {
                //TODO(AR) should this unlock order be inverted?
                collectionLock.unlock();
                lockTable.released(groupId, collectionPathStr, LockType.COLLECTION, collectionMode);

                parentCollectionLock.unlock();
                lockTable.released(groupId, parentCollectionString, LockType.COLLECTION, parentCollectionMode);
            });
        } else {
            final java.util.concurrent.locks.Lock collectionLock = currentModeLock;
            return new ManagedCollectionLock(collectionPath, Either.Left(collectionLock), () -> {
                collectionLock.unlock();
                lockTable.released(groupId, collectionPathStr, LockType.COLLECTION, Lock.LockMode.WRITE_LOCK);
            });
        }
    }

    /**
     * Optimized locking method for acquiring a WRITE_LOCK lock on a Collection, when we already hold a WRITE_LOCK lock on the
     * parent collection. In that instance we can avoid descending the locking tree again
     */
    public ManagedCollectionLock acquireCollectionWriteLock(final ManagedCollectionLock parentLock, final XmldbURI collectionPath) throws LockException {
        return acquireCollectionLock(parentLock, collectionPath, Lock.LockMode.WRITE_LOCK);
    }

    /**
     * Optimized locking method for acquiring a READ_LOCK lock on a Collection, when we already hold a READ_LOCK lock on the
     * parent collection. In that instance we can avoid descending the locking tree again
     */
    public ManagedCollectionLock acquireCollectionReadLock(final ManagedCollectionLock parentLock, final XmldbURI collectionPath) throws LockException {
        return acquireCollectionLock(parentLock, collectionPath, Lock.LockMode.READ_LOCK);
    }

    /**
     * Optimized locking method for acquiring a lock on a Collection, when we already hold a lock on the
     * parent collection. In that instance we can avoid descending the locking tree again
     */
    private ManagedCollectionLock acquireCollectionLock(final ManagedCollectionLock parentLock, final XmldbURI collectionPath, final Lock.LockMode lockMode) throws LockException {
        if(Objects.isNull(parentLock)) {
            throw new LockException("Cannot acquire a sub-Collection lock without a lock for the parent");
        }
        if(parentLock.isReleased()) {
            throw new LockException("Cannot acquire a sub-Collection lock without a lock on the parent");
        }

        final XmldbURI parentCollectionUri = collectionPath.removeLastSegment();
        if(!parentLock.getPath().equals(parentCollectionUri)) {
            throw new LockException("Cannot acquire a lock on sub-Collection '" + collectionPath + "' as provided parent lock '" + parentCollectionUri + "' is not the parent");
        }

        final long groupId = System.nanoTime();
        final String collectionPathStr = collectionPath.toString();

        //TODO(AR) is this correct do we need to unlock the parentLock's parent, and what about on LockException? -- need tests
        final ReentrantReadWriteLock subCollectionLock = getCollectionLock(collectionPath.getCollectionPath());
        final java.util.concurrent.locks.Lock lock;
        switch(lockMode) {
            case WRITE_LOCK:
                lock = subCollectionLock.writeLock();
                break;

            case READ_LOCK:
                lock = subCollectionLock.readLock();
                break;

            default:
                throw new IllegalArgumentException("Unsupported lock mode: " + lockMode);
        }

        try {
            lockTable.attempt(groupId, collectionPathStr, LockType.COLLECTION, lockMode);

            lock.lockInterruptibly();

            lockTable.acquired(groupId, collectionPathStr, LockType.COLLECTION, lockMode);

        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, collectionPathStr, LockType.COLLECTION, lockMode);
            throw new LockException("Unable to acquire " + lockMode + " for: " + collectionPath, e);
        }

        return new ManagedCollectionLock(collectionPath, Either.Left(lock), () -> {
            lock.unlock();
            lockTable.released(groupId, collectionPathStr, LockType.COLLECTION, lockMode);
        });
    }

    /**
     * Retrieves a lock for a Document
     *
     * This function is concerned with just the lock object
     * and has no knowledge of the state of the lock. The only
     * guarantee is that if this lock has not been requested before
     * then it will be provided in the unlocked state
     *
     * @param documentPath The path of the Document for which a lock is requested
     *
     * @return A lock for the Document
     */
    ReentrantReadWriteLock getDocumentLock(final String documentPath) {
        return documentLocks.get(documentPath);
    }

    /**
     * Acquire a READ_LOCK on a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return the lock for the Document
     *
     * @throws LockException if the lock could not be acquired
     */
    public ManagedDocumentLock acquireDocumentReadLock(final XmldbURI documentPath) throws LockException {
        final long groupId = System.nanoTime();
        final String path = documentPath.toString();

        final ReentrantReadWriteLock lock = getDocumentLock(path);
        try {
            lockTable.attempt(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);

            lock.readLock().lockInterruptibly();

            lockTable.acquired(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
            throw new LockException("Unable to acquire READ_LOCK for: " + path, e);
        }

        return new ManagedDocumentLock(documentPath, lock.readLock(), () -> {
            lock.readLock().unlock();
            lockTable.released(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
        });
    }

    /**
     * Acquire a WRITE_LOCK on a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return the lock for the Document
     *
     * @throws LockException if the lock could not be acquired
     */
    public ManagedDocumentLock acquireDocumentWriteLock(final XmldbURI documentPath) throws LockException {
        final long groupId = System.nanoTime();
        final String path = documentPath.toString();

        final ReentrantReadWriteLock lock = getDocumentLock(path);
        try {
            lockTable.attempt(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);

            lock.writeLock().lockInterruptibly();

            lockTable.acquired(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
            throw new LockException("Unable to acquire WRITE_LOCK for: " + path, e);
        }

        return new ManagedDocumentLock(documentPath, lock.writeLock(), () -> {
            lock.writeLock().unlock();
            lockTable.released(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
        });
    }

    /**
     * Returns true if a WRITE_LOCK is held for a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return true if a WRITE_LOCK is held
     */
    public boolean isDocumentLockedForWrite(final XmldbURI documentPath) {
        final ReentrantReadWriteLock existingLock = getDocumentLock(documentPath.toString());
        return existingLock.isWriteLocked();
    }

    /**
     * Returns true if a READ_LOCK is held for a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return true if a READ_LOCK is held
     */
    public boolean isDocumentLockedForRead(final XmldbURI documentPath) {
        final ReentrantReadWriteLock existingLock = getDocumentLock(documentPath.toString());
        return existingLock.getReadLockCount() > 0;
    }
}
