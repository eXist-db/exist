/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage.lock;

import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.Lock.LockType;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.WeakLazyStripes;
import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A Lock Manager for Locks that are used across
 * database instance functions.
 *
 * There is a unique lock for each ID, and calls with the same
 * ID will always return the same lock. Different IDs will always
 * receive different locks.
 *
 * The locking protocol for Collection locks is taken from the paper:
 *     <a href="https://pdfs.semanticscholar.org/5acd/43c51fa5e677b0c242b065a64f5948af022c.pdf">Granularity of Locks in a Shared Data Base - Gray, Lorie and Putzolu 1975</a>.
 * specifically we have adopted the acquisition algorithm from Section 3.2 of the paper.
 *
 * Our adaptions enable us to specify either a multi-writer/multi-reader approach between Collection
 * sub-trees or a single-writer/multi-reader approach on the entire Collection tree.
 *
 * The uptake is that locking a Collection, also implicitly implies locking all descendant
 * Collections with the same mode. This reduces the amount of locks required for
 * manipulating Collection sub-trees.
 *
 * The locking protocol for Documents is entirely flat, and is unrelated to Collection locking.
 * Deadlocks can still occur between Collections and Documents in eXist-db (as they could in the past).
 * If it becomes necessary to eliminate such Collection/Document deadlock scenarios, Document locks
 * could be acquired using the same protocol as Collection locks (as really they are all just URI paths in a hierarchy)!
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LockManager {

    // org.exist.util.Configuration properties
    public final static String CONFIGURATION_UPGRADE_CHECK = "lock-manager.upgrade-check";
    public final static String CONFIGURATION_WARN_WAIT_ON_READ_FOR_WRITE = "lock-manager.warn-wait-on-read-for-write";
    public final static String CONFIGURATION_PATH_LOCKS_FOR_DOCUMENTS = "lock-manager.document.use-path-locks";
    public final static String CONFIGURATION_PATHS_MULTI_WRITER = "lock-manager.paths-multi-writer";

    //TODO(AR) remove eventually!
    // legacy properties for overriding the config
    public final static String PROP_ENABLE_PATHS_MULTI_WRITER = "exist.lockmanager.paths-multiwriter";
    public final static String PROP_UPGRADE_CHECK = "exist.lockmanager.upgrade.check";
    public final static String PROP_WARN_WAIT_ON_READ_FOR_WRITE = "exist.lockmanager.warn.waitonreadforwrite";

    private static final Logger LOG = LogManager.getLogger(LockManager.class);

    /**
     * Set to true to use the path Hierarchy for document locks
     * as opposed to separating Collection and Document locks
     */
    private final boolean usePathLocksForDocuments;

    /**
     * Set to true to enable Multi-Writer/Multi-Reader semantics for
     * the path Hierarchy as opposed to the default Single-Writer/Multi-Reader
     */
    private final boolean pathsMultiWriter;

    /**
     * Set to true to enable checking for lock upgrading within the same
     * thread, i.e. READ_LOCK -> WRITE_LOCK
     */
    private final boolean upgradeCheck;

    /**
     * Set to true to enable warning when a thread wants to acquire the WRITE_LOCK
     * but another thread holds the READ_LOCK
     */
    private final boolean warnWaitOnReadForWrite;


    private final LockTable lockTable;
    private final WeakLazyStripes<String, MultiLock> pathLocks;
    private final WeakLazyStripes<String, MultiLock> documentLocks;
    private final WeakLazyStripes<String, ReentrantLock> btreeLocks;

    /**
     * @param configuration database configuration
     * @param concurrencyLevel Concurrency Level of the lock table.
     */
    public LockManager(final Configuration configuration, final int concurrencyLevel) {
        // set configuration
        this.usePathLocksForDocuments = getConfigPropertyBool(configuration, CONFIGURATION_PATH_LOCKS_FOR_DOCUMENTS, false);
        this.pathsMultiWriter = getLegacySystemPropertyOrConfigPropertyBool(PROP_ENABLE_PATHS_MULTI_WRITER, configuration, CONFIGURATION_PATHS_MULTI_WRITER, false);
        this.upgradeCheck = getLegacySystemPropertyOrConfigPropertyBool(PROP_UPGRADE_CHECK, configuration, CONFIGURATION_UPGRADE_CHECK, false);
        this.warnWaitOnReadForWrite = getLegacySystemPropertyOrConfigPropertyBool(PROP_WARN_WAIT_ON_READ_FOR_WRITE, configuration, CONFIGURATION_WARN_WAIT_ON_READ_FOR_WRITE, false);

        this.lockTable = new LockTable(configuration);
        this.pathLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createCollectionLock);
        if (!usePathLocksForDocuments) {
            this.documentLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createDocumentLock);
        } else {
            this.documentLocks = null;
        }
        this.btreeLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createBtreeLock);

        LOG.info("Configured LockManager with concurrencyLevel={} use-path-locks-for-documents={} paths-multi-writer={}", concurrencyLevel, usePathLocksForDocuments, pathsMultiWriter);
    }

    /**
     * Reserved for testing!
     *
     * @param concurrencyLevel Concurrency Level of the lock table.
     */
    LockManager(final int concurrencyLevel) {
        this(null, concurrencyLevel);
    }

    /**
     * Get the lock table.
     *
     * @return the lock table.
     */
    public LockTable getLockTable() {
        return lockTable;
    }

    /**
     * Creates a new lock for a Collection
     * will be Striped by the collectionPath.
     *
     * @param collectionPath the collection path
     *
     * @return the document lock
     */
    private static MultiLock createCollectionLock(final String collectionPath) {
        return new MultiLock();
    }

    /**
     * Creates a new MultiLock lock for a Document
     * will be Striped by the documentPath.
     *
     * @param documentPath the document path
     *
     * @return the document lock
     */
    private static MultiLock createDocumentLock(final String documentPath) {
        return new MultiLock();
    }

    /**
     * Creates a new lock for a {@link org.exist.storage.btree.BTree}
     * will be Striped by the btreeFileName
     */
    private static ReentrantLock createBtreeLock(final String btreeFileName) {
        return new ReentrantLock();
    }

    /**
     * Retrieves a lock for a Path
     *
     * This function is concerned with just the lock object
     * and has no knowledge of the state of the lock. The only
     * guarantee is that if this lock has not been requested before
     * then it will be provided in the unlocked state
     *
     * @param path The path for which a lock is requested
     *
     * @return A lock for the path
     */
    MultiLock getPathLock(final String path) {
        return pathLocks.get(path);
    }

    /**
     * Acquires a READ_LOCK on a Collection (and implicitly all descendant Collections).
     *
     * @param collectionPath The path of the Collection for which a lock is requested.
     *
     * @return A READ_LOCK on the Collection.
     * @throws LockException if a lock error occurs
     */
    public ManagedCollectionLock acquireCollectionReadLock(final XmldbURI collectionPath) throws LockException {
        final LockGroup lockGroup = acquirePathReadLock(LockType.COLLECTION, collectionPath);
        return new ManagedCollectionLock(
                collectionPath,
                Arrays.stream(lockGroup.locks).map(Tuple3::get_1).toArray(MultiLock[]::new),
                () -> unlockAll(lockGroup.locks, l -> lockTable.released(lockGroup.groupId, l._3, LockType.COLLECTION, l._2))
        );
    }

    private static class LockGroup {
        final long groupId;
        final Tuple3<MultiLock, Lock.LockMode, String>[] locks;

        private LockGroup(final long groupId, final Tuple3<MultiLock, Lock.LockMode, String>[] locks) {
            this.groupId = groupId;
            this.locks = locks;
        }
    }

    /**
     * Acquires a READ_LOCK on a database path (and implicitly all descendant paths).
     *
     * @param lockType The type of the lock
     * @param path The path for which a lock is requested.
     *
     * @return A READ_LOCK on the Collection.
     *
     * @throws LockException if a lock error occurs
     */
    public LockGroup acquirePathReadLock(final LockType lockType, final XmldbURI path) throws LockException {
        final XmldbURI[] segments = path.getPathSegments();

        final long groupId = System.nanoTime();

        String pathStr = "";
        final Tuple3<MultiLock, Lock.LockMode, String>[] locked = new Tuple3[segments.length];
        for (int i = 0; i < segments.length; i++) {
            pathStr += '/' + segments[i].toString();

            final Lock.LockMode lockMode;
            if (i + 1 == segments.length) {
                lockMode = Lock.LockMode.READ_LOCK; //leaf
            } else {
                lockMode = Lock.LockMode.INTENTION_READ; //ancestor
            }

            final MultiLock lock = getPathLock(pathStr);

            lockTable.attempt(groupId, pathStr, lockType, lockMode);
            if (lock(lock, lockMode)) {
                locked[i] = new Tuple3<>(lock, lockMode, pathStr);
                lockTable.acquired(groupId, pathStr, lockType, lockMode);
            } else {
                lockTable.attemptFailed(groupId, pathStr, lockType, lockMode);

                unlockAll(locked, l -> lockTable.released(groupId, l._3, lockType, l._2));

                throw new LockException("Unable to acquire " + lockType + " " + lockMode + " for: " + pathStr);
            }
        }

        return new LockGroup(groupId, locked);
    }

    /**
     * Locks a lock object.
     *
     * @param lock the lock object to lock.
     * @param lockMode the mode of the {@code lock} to acquire.
     *
     * @return true, if we were able to lock with the mode.
     */
    private boolean lock(final MultiLock lock, final Lock.LockMode lockMode) {
        switch (lockMode) {
            case INTENTION_READ:
                lock.intentionReadLock();
                break;

            case INTENTION_WRITE:
                lock.intentionWriteLock();
                break;

            case READ_LOCK:
                lock.readLock();
                break;

            case WRITE_LOCK:
                lock.writeLock();
                break;

            default:
                throw new UnsupportedOperationException(); // TODO(AR) implement the other modes
        }

        return true;  //TODO(AR) switch to lock interruptibly above!
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

    /**
     * Unlocks a lock object.
     *
     * @param lock The lock object to unlock.
     * @param lockMode The mode of the {@code lock} to release.
     */
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

    /**
     * Acquires a WRITE_LOCK on a Collection (and implicitly all descendant Collections).
     *
     * @param collectionPath The path of the Collection for which a lock is requested.
     *
     * @return A WRITE_LOCK on the Collection.
     * @throws LockException if a lock error occurs
     */
    public ManagedCollectionLock acquireCollectionWriteLock(final XmldbURI collectionPath) throws LockException {
        return acquireCollectionWriteLock(collectionPath, false);
    }

    /**
     * Acquires a WRITE_LOCK on a Collection (and implicitly all descendant Collections).
     *
     * @param collectionPath The path of the Collection for which a lock is requested.
     * @param lockParent true if we should also explicitly write lock the parent Collection.
     *
     * @return A WRITE_LOCK on the Collection.
     */
    ManagedCollectionLock acquireCollectionWriteLock(final XmldbURI collectionPath, final boolean lockParent) throws LockException {
        final LockGroup lockGroup = acquirePathWriteLock(LockType.COLLECTION, collectionPath, lockParent);
        return new ManagedCollectionLock(
                collectionPath,
                Arrays.stream(lockGroup.locks).map(Tuple3::get_1).toArray(MultiLock[]::new),
                () -> unlockAll(lockGroup.locks, l -> lockTable.released(lockGroup.groupId, l._3, LockType.COLLECTION, l._2))
        );
    }

    /**
     * Acquires a WRITE_LOCK on a path (and implicitly all descendant paths).
     *
     * @param path The path for which a lock is requested.
     * @param lockParent true if we should also explicitly write lock the parent path.
     *
     * @return A WRITE_LOCK on the path.
     */
    LockGroup acquirePathWriteLock(final LockType lockType, final XmldbURI path,
            final boolean lockParent) throws LockException {
        final XmldbURI[] segments = path.getPathSegments();

        final long groupId = System.nanoTime();

        String pathStr = "";
        final Tuple3<MultiLock, Lock.LockMode, String>[] locked = new Tuple3[segments.length];
        for (int i = 0; i < segments.length; i++) {
            pathStr += '/' + segments[i].toString();

            final Lock.LockMode lockMode;
            if (lockParent && i + 2 == segments.length) {
                lockMode = Lock.LockMode.WRITE_LOCK;    // parent
            } else if(i + 1 == segments.length) {
                lockMode = Lock.LockMode.WRITE_LOCK;    // leaf
            } else {
                // ancestor

                if (!pathsMultiWriter) {
                    // single-writer/multi-reader
                    lockMode = Lock.LockMode.WRITE_LOCK;
                } else {
                    // multi-writer/multi-reader
                    lockMode = Lock.LockMode.INTENTION_WRITE;
                }
            }

            final MultiLock lock = getPathLock(pathStr);

            if (upgradeCheck && lockMode == Lock.LockMode.WRITE_LOCK && (lock.getIntentionReadHoldCount() > 0  || lock.getReadHoldCount() > 0)) {
                throw new LockException("Lock upgrading would lead to a self-deadlock: " + pathStr);
            }

            if (warnWaitOnReadForWrite && lockMode == Lock.LockMode.WRITE_LOCK) {
                if (lock.getIntentionReadLockCount() > 0) {
                    LOG.warn("About to acquire WRITE_LOCK for: {}, but INTENTION_READ_LOCK held by other thread(s): ", pathStr);
                } else if(lock.getReadLockCount() > 0) {
                    LOG.warn("About to acquire WRITE_LOCK for: {}, but READ_LOCK held by other thread(s): ", pathStr);
                }
            }

            lockTable.attempt(groupId, pathStr, lockType, lockMode);
            if (lock(lock, lockMode)) {
                locked[i] = new Tuple3<>(lock, lockMode, pathStr);
                lockTable.acquired(groupId, pathStr, lockType, lockMode);
            } else {
                lockTable.attemptFailed(groupId, pathStr, lockType, lockMode);

                unlockAll(locked, l -> lockTable.released(groupId, l._3, lockType, l._2));

                throw new LockException("Unable to acquire " + lockType + " " + lockMode + " for: " + pathStr);
            }
        }

        return new LockGroup(groupId, locked);
    }

    /**
     * Returns true if a WRITE_LOCK is held for a Collection
     *
     * @param collectionPath The URI of the Collection within the database
     *
     * @return true if a WRITE_LOCK is held
     */
    public boolean isCollectionLockedForWrite(final XmldbURI collectionPath) {
        final MultiLock existingLock = getPathLock(collectionPath.toString());
        return existingLock.getWriteLockCount() > 0;
    }

    /**
     * Returns true if a READ_LOCK is held for a Collection
     *
     * @param collectionPath The URI of the Collection within the database
     *
     * @return true if a READ_LOCK is held
     */
    public boolean isCollectionLockedForRead(final XmldbURI collectionPath) {
        final MultiLock existingLock = getPathLock(collectionPath.toString());
        return existingLock.getReadLockCount() > 0;
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
    MultiLock getDocumentLock(final String documentPath) {
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
        if (usePathLocksForDocuments) {
            final LockGroup lockGroup = acquirePathReadLock(LockType.DOCUMENT, documentPath);
            return new ManagedDocumentLock(
                    documentPath,
                    Arrays.stream(lockGroup.locks).map(Tuple3::get_1).toArray(MultiLock[]::new),
                    () -> unlockAll(lockGroup.locks, l -> lockTable.released(lockGroup.groupId, l._3, LockType.DOCUMENT, l._2))
            );
        } else {
            final long groupId = System.nanoTime();
            final String path = documentPath.toString();

            final MultiLock lock = getDocumentLock(path);
            lockTable.attempt(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);

            if (lock(lock, Lock.LockMode.READ_LOCK)) {

                lockTable.acquired(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
            } else {
                lockTable.attemptFailed(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
                throw new LockException("Unable to acquire READ_LOCK for: " + path);
            }

            return new ManagedDocumentLock(documentPath, lock, () -> {
                lock.asReadLock().unlock();
                lockTable.released(groupId, path, LockType.DOCUMENT, Lock.LockMode.READ_LOCK);
            });
        }
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
        if (usePathLocksForDocuments) {
            final LockGroup lockGroup = acquirePathWriteLock(LockType.DOCUMENT, documentPath, false);
            return new ManagedDocumentLock(
                    documentPath,
                    Arrays.stream(lockGroup.locks).map(Tuple3::get_1).toArray(MultiLock[]::new),
                    () -> unlockAll(lockGroup.locks, l -> lockTable.released(lockGroup.groupId, l._3, LockType.DOCUMENT, l._2))
            );
        } else {
            final long groupId = System.nanoTime();
            final String path = documentPath.toString();

            final MultiLock lock = getDocumentLock(path);
            lockTable.attempt(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);

            if (lock(lock, Lock.LockMode.WRITE_LOCK)) {
                lockTable.acquired(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
            } else {
                lockTable.attemptFailed(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
                throw new LockException("Unable to acquire WRITE_LOCK for: " + path);
            }

            return new ManagedDocumentLock(documentPath, lock, () -> {
                lock.asWriteLock().unlock();
                lockTable.released(groupId, path, LockType.DOCUMENT, Lock.LockMode.WRITE_LOCK);
            });
        }
    }

    /**
     * Returns true if a WRITE_LOCK is held for a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return true if a WRITE_LOCK is held
     */
    public boolean isDocumentLockedForWrite(final XmldbURI documentPath) {
        final MultiLock existingLock;
        if (!usePathLocksForDocuments) {
            existingLock = getDocumentLock(documentPath.toString());
        } else {
            existingLock = getPathLock(documentPath.toString());
        }
        return existingLock.getWriteLockCount() > 0;
    }

    /**
     * Returns true if a READ_LOCK is held for a Document
     *
     * @param documentPath The URI of the Document within the database
     *
     * @return true if a READ_LOCK is held
     */
    public boolean isDocumentLockedForRead(final XmldbURI documentPath) {
        final MultiLock existingLock;
        if (!usePathLocksForDocuments) {
            existingLock = getDocumentLock(documentPath.toString());
        } else {
            existingLock = getPathLock(documentPath.toString());
        }
        return existingLock.getReadLockCount() > 0;
    }

    /**
     * Returns the LockMode that should be used for accessing
     * a Collection when that access is just for the purposes
     * or accessing a document(s) within the Collection.
     *
     * When Path Locks are enabled for Documents, both Collections and
     * Documents share the same lock domain, a path based tree hierarchy.
     * With a shared lock-hierarchy, if we were to READ_LOCK a Collection
     * and then request a WRITE_LOCK for a Document in that Collection we
     * would reach a dead-lock situation. To avoid this, if this method is
     * called like
     * {@code relativeCollectionLockMode(LockMode.READ_LOCK, LockMode.WRITE_LOCK)}
     * it will return a WRITE_LOCK. That is to say that to aid dealock-avoidance,
     * this function may return a stricter locking mode than the {@code desiredCollectionLockMode}.
     *
     * When Path Locks are disabled (the default) for Documents, Collection and Documents
     * have independent locking domains. In this case this function will always return
     * the {@code desiredCollectionLockMode}.
     *
     * @param desiredCollectionLockMode The desired lock mode for the Collection.
     * @param documentLockMode The lock mode that will be used for subsequent document operations in the Collection.
     *
     * @return The lock mode that should be used for accessing the Collection.
     */
    public Lock.LockMode relativeCollectionLockMode(final Lock.LockMode desiredCollectionLockMode,
            final Lock.LockMode documentLockMode) {
        if (!usePathLocksForDocuments) {
            return desiredCollectionLockMode;

        } else {
            switch (documentLockMode) {
                case NO_LOCK:
                case INTENTION_READ:
                case READ_LOCK:
                    return Lock.LockMode.READ_LOCK;

                default:
                    return Lock.LockMode.WRITE_LOCK;
            }
        }
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

    /**
     * Returns true if the BTree for the file name is locked.
     *
     * @param btreeFileName The name of the .dbx file.
     *
     * @return true if the Btree is locked.
     *
     * @deprecated Just a place holder until we can make the BTree reader/writer safe
     */
    @Deprecated
    public boolean isBtreeLocked(final String btreeFileName) {
        final ReentrantLock lock = getBTreeLock(btreeFileName);
        return lock.isLocked();
    }

    /**
     * Returns true if the BTree for the file name is locked for writes.
     *
     * @param btreeFileName The name of the .dbx file.
     *
     * @return true if the Btree is locked for writes.
     */
    public boolean isBtreeLockedForWrite(final String btreeFileName) {
        return isBtreeLocked(btreeFileName);
    }

    /**
     * Gets a configuration option from a (legacy) System Property
     * or if that is not set, then from an eXist-db Configuration file
     * property.
     *
     * @param legacyPropertyName name of the legacy system property
     * @param configuration eXist-db configuration
     * @param configProperty name of an eXist-db configuration property
     * @param defaultValue the default value if no system property of config property is found.
     *
     * @return the value of the property
     */
    static boolean getLegacySystemPropertyOrConfigPropertyBool(final String legacyPropertyName,
            final Configuration configuration, final String configProperty, final boolean defaultValue) {
        final String legacyPropertyValue = System.getProperty(legacyPropertyName);
        if (legacyPropertyValue != null && !legacyPropertyValue.isEmpty()) {
            return Boolean.getBoolean(legacyPropertyName);
        } else {
            return getConfigPropertyBool(configuration, configProperty, defaultValue);
        }
    }

    /**
     * Gets a configuration option from an eXist-db Configuration file
     * property.
     *
     * @param configuration eXist-db configuration
     * @param configProperty name of an eXist-db configuration property
     * @param defaultValue the default value if no system property of config property is found.
     *
     * @return the value of the property
     */
    static boolean getConfigPropertyBool(final Configuration configuration, final String configProperty,
            final boolean defaultValue) {
        if (configuration != null) {
            return configuration.getProperty(configProperty, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Gets a configuration option from a (legacy) System Property
     * or if that is not set, then from an eXist-db Configuration file
     * property.
     *
     * @param legacyPropertyName name of the legacy system property
     * @param configuration eXist-db configuration
     * @param configProperty name of an eXist-db configuration property
     * @param defaultValue the default value if no system property of config property is found.
     *
     * @return the value of the property
     */
    static int getLegacySystemPropertyOrConfigPropertyInt(final String legacyPropertyName,
            final Configuration configuration, final String configProperty, final int defaultValue) {
        final String legacyPropertyValue = System.getProperty(legacyPropertyName);
        if (legacyPropertyValue != null && !legacyPropertyValue.isEmpty()) {
            return Integer.getInteger(legacyPropertyName);
        } else if (configuration != null) {
            return configuration.getProperty(configProperty, defaultValue);
        } else {
            return defaultValue;
        }
    }
}
