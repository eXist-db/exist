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

import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.Lock.LockType;
import org.exist.util.LockException;
import org.exist.util.WeakLazyStripes;
import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * A Lock Manager for Locks that are used across
 * database instance functions
 *
 * There is a unique lock for each ID, and calls with the same
 * ID will always return the same lock. Different IDs will always
 * receive different locks.
 *
 * The locking protocol for Collection locks is taken from the paper:
 *     Granularity of Locks in a Shared Data Base - Gray, Lorie and Putzolu 1975
 *     {@see https://pdfs.semanticscholar.org/5acd/43c51fa5e677b0c242b065a64f5948af022c.pdf}
 * specifically we have adopted the acquisition algorithm from Section 3.2 of the paper
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockManager {

    public final static String PROP_ENABLE_COLLECTIONS_MULTI_WRITER = "exist.lockmanager.collections.multiwriter";
    public final static String PROP_UPGRADE_CHECK = "exist.lockmanager.upgrade.check";
    public final static String PROP_WARN_WAIT_ON_READ_FOR_WRITE = "exist.lockmanager.warn.waitonreadforwrite";

    private static final Logger LOG = LogManager.getLogger(LockManager.class);
    private static final boolean USE_FAIR_SCHEDULER = true;  //Java's ReentrantReadWriteLock must use the Fair Scheduler to get FIFO like ordering
    private static final LockTable lockTable = LockTable.getInstance();

    /**
     * Set to true to enable Multi-Writer/Multi-Reader semantics for
     * the Collection Hierarchy as opposed to the default Single-Writer/Multi-Reader
     */
    private volatile boolean collectionsMultiWriter = Boolean.getBoolean(PROP_ENABLE_COLLECTIONS_MULTI_WRITER);

    /**
     * Set to true to enable checking for lock upgrading within the same
     * thread, i.e. READ_LOCK -> WRITE_LOCK
     */
    private volatile boolean upgradeCheck = Boolean.getBoolean(PROP_UPGRADE_CHECK);

    /**
     * Set to true to enable warning when a thread wants to acquire the WRITE_LOCK
     * but another thread holds the READ_LOCK
     */
    private volatile boolean warnWaitOnReadForWrite = Boolean.getBoolean(PROP_WARN_WAIT_ON_READ_FOR_WRITE);



    private final WeakLazyStripes<String, MultiLock> collectionLocks;
    private final WeakLazyStripes<String, ReentrantReadWriteLock> documentLocks;
    private final WeakLazyStripes<String, ReentrantLock> btreeLocks;


    public LockManager(final int concurrencyLevel) {
        this.collectionLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createCollectionLock);
        this.documentLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createDocumentLock);
        this.btreeLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createBtreeLock);
        LOG.info("Configured LockManager with concurrencyLevel={}", concurrencyLevel);
    }

    /**
     * Creates a new lock for a Collection
     * will be Striped by the collectionPath
     */
    private static MultiLock createCollectionLock(final String collectionPath) {
        return new MultiLock(null);
    }

    /**
     * Creates a new lock for a Document
     * will be Striped by the collectionPath
     */
    private static ReentrantReadWriteLock createDocumentLock(final String documentPath) {
        return new ReentrantReadWriteLock();
    }

    /**
     * Creates a new lock for a {@link org.exist.storage.btree.BTree}
     * will be Striped by the btreeFileName
     */
    private static ReentrantLock createBtreeLock(final String btreeFileName) {
        return new ReentrantLock();
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
    MultiLock getCollectionLock(final String collectionPath) {
        return collectionLocks.get(collectionPath);
    }

    public ManagedCollectionLock acquireCollectionReadLock(final XmldbURI collectionPath) throws LockException {
        final XmldbURI[] segments = collectionPath.getPathSegments();

        final long groupId = System.nanoTime();

        String path = "";
        final Tuple3<MultiLock, Lock.LockMode, String>[] locked = new Tuple3[segments.length];
        for(int i = 0; i < segments.length; i++) {
            path += '/' + segments[i].toString();

            final Lock.LockMode lockMode;
            if(i + 1 == segments.length) {
                lockMode = Lock.LockMode.READ_LOCK; //leaf
            } else {
                lockMode = Lock.LockMode.INTENTION_READ; //ancestor
            }
            final MultiLock lock = getCollectionLock(path);

            lockTable.attempt(groupId, path, LockType.COLLECTION, lockMode);
            if(lock(lock, lockMode)) {
                locked[i] = new Tuple3<>(lock, lockMode, path);
                lockTable.acquired(groupId, path, LockType.COLLECTION, lockMode);
            } else {
                lockTable.attemptFailed(groupId, path, LockType.COLLECTION, lockMode);

                unlockAll(locked, l -> lockTable.released(groupId, l._3, LockType.COLLECTION, l._2));

                throw new LockException("Unable to acquire " + lockMode + " for: " + path);
            }
        }

        return new ManagedCollectionLock(
                collectionPath,
                Arrays.stream(locked).map(Tuple3::get_1).toArray(MultiLock[]::new),
                () -> unlockAll(locked, l -> lockTable.released(groupId, l._3, LockType.COLLECTION, l._2))
        );
    }

    private boolean lock(final MultiLock lock, final Lock.LockMode lockMode) {
        switch(lockMode) {
            case INTENTION_READ:
                return lock.lockIntentionRead();

            case INTENTION_WRITE:
                return lock.lockIntentionWrite();

            case READ_LOCK:
                return lock.lockRead();

            case WRITE_LOCK:
                return lock.lockWrite();

            default:
                throw new UnsupportedOperationException(); // TODO(AR) implement the other modes
        }
    }

    /**
     * Releases an array of locked locks for the modes with which they were locked
     *
     * Locks are released in the opposite to their acquisition order
     *
     * @param locked An array of locks in acquisition order
     */
    private void unlockAll(final Tuple3<MultiLock, Lock.LockMode, String>[] locked, final Consumer<Tuple3<MultiLock, Lock.LockMode, String>> unlockListener) {
        for(int i = locked.length - 1; i >= 0; i--) {
            final Tuple3<MultiLock, Lock.LockMode, String> lock = locked[i];
            unlock(lock._1, lock._2);
            unlockListener.accept(lock);
        }
    }

    private void unlock(final MultiLock lock, final Lock.LockMode lockMode) {
        switch(lockMode) {
            case INTENTION_READ:
                lock.unlockIntentionRead();
                break;

            case INTENTION_WRITE:
                lock.unlockIntentionWrite();
                break;

            case READ_LOCK:
                lock.unlockRead();
                break;

            case WRITE_LOCK:
                lock.unlockWrite();
                break;

            default:
                throw new UnsupportedOperationException(); // TODO(AR) implement the other modes
        }
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

        String path = "";
        final Tuple3<MultiLock, Lock.LockMode, String>[] locked = new Tuple3[segments.length];
        for(int i = 0; i < segments.length; i++) {
            path += '/' + segments[i].toString();

            final Lock.LockMode lockMode;
            if(lockParent && i + 2 == segments.length) {
                lockMode = Lock.LockMode.WRITE_LOCK;    // parent
            } else if(i + 1 == segments.length) {
                lockMode = Lock.LockMode.WRITE_LOCK;    // leaf
            } else {
                // ancestor

                if(!collectionsMultiWriter) {
                    // single-writer/multi-reader
                    lockMode = Lock.LockMode.WRITE_LOCK;
                } else {
                    // multi-writer/multi-reader
                    lockMode = Lock.LockMode.INTENTION_WRITE;
                }
            }
            final MultiLock lock = getCollectionLock(path);

            if(upgradeCheck && lockMode == Lock.LockMode.WRITE_LOCK && (lock.getIntentionReadHoldCount() > 0  || lock.getReadHoldCount() > 0)) {
                throw new LockException("Lock upgrading would lead to a self-deadlock: " + path);
            }

            if(warnWaitOnReadForWrite && lockMode == Lock.LockMode.WRITE_LOCK) {
                if(lock.getIntentionReadLockCount() > 0) {
                    LOG.warn("About to acquire WRITE_LOCK for: {}, but INTENTION_READ_LOCK held by other thread(s): ", path);
                } else if(lock.getReadLockCount() > 0) {
                    LOG.warn("About to acquire WRITE_LOCK for: {}, but READ_LOCK held by other thread(s): ", path);
                }
            }

            lockTable.attempt(groupId, path, LockType.COLLECTION, lockMode);
            if(lock(lock, lockMode)) {
                locked[i] = new Tuple3<>(lock, lockMode, path);
                lockTable.acquired(groupId, path, LockType.COLLECTION, lockMode);
            } else {
                lockTable.attemptFailed(groupId, path, LockType.COLLECTION, lockMode);

                unlockAll(locked, l -> lockTable.released(groupId, l._3, LockType.COLLECTION, l._2));

                throw new LockException("Unable to acquire " + lockMode + " for: " + path);
            }
        }

        return new ManagedCollectionLock(
                collectionPath,
                Arrays.stream(locked).map(Tuple3::get_1).toArray(MultiLock[]::new),
                () -> unlockAll(locked, l -> lockTable.released(groupId, l._3, LockType.COLLECTION, l._2))
        );
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
        final MultiLock subCollectionLock = getCollectionLock(collectionPath.getCollectionPath());

        lockTable.attempt(groupId, collectionPathStr, LockType.COLLECTION, lockMode);

        if(lock(subCollectionLock, lockMode)) {
            lockTable.acquired(groupId, collectionPathStr, LockType.COLLECTION, lockMode);
        } else {
            lockTable.attemptFailed(groupId, collectionPathStr, LockType.COLLECTION, lockMode);
            throw new LockException("Unable to acquire " + lockMode + " for: " + collectionPath);
        }

        return new ManagedCollectionLock(
                collectionPath,
                new MultiLock[] { subCollectionLock },
                () -> {
                    unlock(subCollectionLock, lockMode);
                    lockTable.released(groupId, collectionPathStr, LockType.COLLECTION, lockMode);
                }
        );
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

    /**
     * Retrieves a lock for a {@link org.exist.storage.dom.DOMFile}
     *
     * This function is concerned with just the lock object
     * and has no knowledge of the state of the lock. The only
     * guarantee is that if this lock has not been requested before
     * then it will be provided in the unlocked state
     *
     * @param domFileName The path of the Document for which a lock is requested
     *
     * @return A lock for the DOMFile
     */
    ReentrantLock getBTreeLock(final String domFileName) {
        return btreeLocks.get(domFileName);
    }

    /**
     * Acquire a WRITE_LOCK on a {@link org.exist.storage.btree.BTree}
     *
     * @param btreeFileName the filename of the BTree
     *
     * @return the lock for the BTree
     *
     * @throws LockException if the lock could not be acquired
     */
    public ManagedLock<ReentrantLock> acquireBtreeReadLock(final String btreeFileName) throws LockException {
        final long groupId = System.nanoTime();

        final ReentrantLock lock = getBTreeLock(btreeFileName);
        try {
            lockTable.attempt(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.READ_LOCK);

            lock.lockInterruptibly();

            lockTable.acquired(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.READ_LOCK);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.READ_LOCK);
            throw new LockException("Unable to acquire READ_LOCK for: " + btreeFileName, e);
        }

        return new ManagedLock(lock, () -> {
            lock.unlock();
            lockTable.released(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.READ_LOCK);
        });
    }

    /**
     * Acquire a WRITE_LOCK on a {@link org.exist.storage.btree.BTree}
     *
     * @param btreeFileName the filename of the BTree
     *
     * @return the lock for the BTree
     *
     * @throws LockException if the lock could not be acquired
     */
    public ManagedLock<ReentrantLock> acquireBtreeWriteLock(final String btreeFileName) throws LockException {
        final long groupId = System.nanoTime();

        final ReentrantLock lock = getBTreeLock(btreeFileName);
        try {
            lockTable.attempt(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.WRITE_LOCK);

            lock.lockInterruptibly();

            lockTable.acquired(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.WRITE_LOCK);
        } catch(final InterruptedException e) {
            lockTable.attemptFailed(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.WRITE_LOCK);
            throw new LockException("Unable to acquire WRITE_LOCK for: " + btreeFileName, e);
        }

        return new ManagedLock(lock, () -> {
            lock.unlock();
            lockTable.released(groupId, btreeFileName, LockType.BTREE, Lock.LockMode.WRITE_LOCK);
        });
    }

    public boolean isBtreeLocked(final String domFileName) {
        final ReentrantLock lock = getBTreeLock(domFileName);
        return lock.isLocked();
    }

    /**
     * @deprecated Just a place holder until we can make the BTree reader/writer safe
     */
    public boolean isBtreeLockedForWrite(final String btreeFileName) {
        return isBtreeLocked(btreeFileName);
    }

}
