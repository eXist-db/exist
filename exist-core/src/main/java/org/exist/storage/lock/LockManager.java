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
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.WeakLazyStripes;
import org.exist.xmldb.XmldbURI;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    public final static String CONFIGURATION_COLLECTION_MULTI_WRITER = "lock-manager.collection.multi-writer";
    public final static String CONFIGURATION_DOCUMENT_MULTI_LOCK = "lock-manager.document.multi-lock";

    //TODO(AR) remove eventually!
    // legacy properties for overriding the config
    public final static String PROP_ENABLE_COLLECTIONS_MULTI_WRITER = "exist.lockmanager.collections.multiwriter";
    public final static String PROP_UPGRADE_CHECK = "exist.lockmanager.upgrade.check";
    public final static String PROP_WARN_WAIT_ON_READ_FOR_WRITE = "exist.lockmanager.warn.waitonreadforwrite";
    public final static String PROP_USE_MULTILOCK_FOR_DOCUMENTS = "exist.lockmanager.documents.multilock";

    private static final Logger LOG = LogManager.getLogger(LockManager.class);
    private static final boolean USE_FAIR_SCHEDULER = true;  //Java's ReentrantReadWriteLock must use the Fair Scheduler to get FIFO like ordering

    /**
     * Set to true to use MultiLock instead of ReentrantReadWriteLock
     */
    private final boolean useMultiLockForDocuments;

    /**
     * Set to true to enable Multi-Writer/Multi-Reader semantics for
     * the Collection Hierarchy as opposed to the default Single-Writer/Multi-Reader
     */
    private final boolean collectionsMultiWriter;

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
    private final WeakLazyStripes<String, MultiLock> collectionLocks;
    private final WeakLazyStripes<String, DocumentLock> documentLocks;
    private final WeakLazyStripes<String, ReentrantLock> btreeLocks;

    /**
     * @param configuration database configuration
     * @param concurrencyLevel Concurrency Level of the lock table.
     */
    public LockManager(final Configuration configuration, final int concurrencyLevel) {
        // set configuration
        this.useMultiLockForDocuments = getLegacySystemPropertyOrConfigPropertyBool(PROP_USE_MULTILOCK_FOR_DOCUMENTS, configuration, CONFIGURATION_DOCUMENT_MULTI_LOCK, false);
        this.collectionsMultiWriter = getLegacySystemPropertyOrConfigPropertyBool(PROP_ENABLE_COLLECTIONS_MULTI_WRITER, configuration, CONFIGURATION_COLLECTION_MULTI_WRITER, false);
        this.upgradeCheck = getLegacySystemPropertyOrConfigPropertyBool(PROP_UPGRADE_CHECK, configuration, CONFIGURATION_UPGRADE_CHECK, false);
        this.warnWaitOnReadForWrite = getLegacySystemPropertyOrConfigPropertyBool(PROP_WARN_WAIT_ON_READ_FOR_WRITE, configuration, CONFIGURATION_WARN_WAIT_ON_READ_FOR_WRITE, false);

        this.lockTable = new LockTable(configuration);
        this.collectionLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createCollectionLock);
        if (useMultiLockForDocuments) {
            this.documentLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createDocumentMultiLock);
        } else {
            this.documentLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createDocumentReentrantLock);
        }
        this.btreeLocks = new WeakLazyStripes<>(concurrencyLevel, LockManager::createBtreeLock);

        LOG.info("Configured LockManager with concurrencyLevel={}", concurrencyLevel);
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
     * will be Striped by the collectionPath
     */
    private static MultiLock createCollectionLock(final String collectionPath) {
        return new MultiLock();
    }

    /**
     * Creates a new Reentrant lock for a Document
     * will be Striped by the collectionPath.
     *
     * @param documentPath the document path
     *
     * @return the document lock
     */
    private static DocumentLock createDocumentReentrantLock(final String documentPath) {
        return new ReentrantReadWriteLockDocumentLockAdapter(new ReentrantReadWriteLock());
    }

    /**
     * Creates a new MultiLock lock for a Document
     * will be Striped by the collectionPath.
     *
     * @param documentPath the document path
     *
     * @return the document lock
     */
    private static DocumentLock createDocumentMultiLock(final String documentPath) {
        return new MultiLockDocumentLockAdapter(new MultiLock());
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

    /**
     * Acquires a READ_LOCK on a Collection (and implicitly all descendant Collections).
     *
     * @param collectionPath The path of the Collection for which a lock is requested.
     *
     * @return A READ_LOCK on the Collection.
     * @throws LockException if a lock error occurs
     */
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

    /**
     * Locks a lock object.
     *
     * @param lock the lock object to lock.
     * @param lockMode the mode of the {@code lock} to acquire.
     *
     * @return true, if we were able to lock with the mode.
     */
    private boolean lock(final MultiLock lock, final Lock.LockMode lockMode) {
        switch(lockMode) {
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
     * Returns true if a WRITE_LOCK is held for a Collection
     *
     * @param collectionPath The URI of the Collection within the database
     *
     * @return true if a WRITE_LOCK is held
     */
    public boolean isCollectionLockedForWrite(final XmldbURI collectionPath) {
        final MultiLock existingLock = getCollectionLock(collectionPath.toString());
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
        final MultiLock existingLock = getCollectionLock(collectionPath.toString());
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
    DocumentLock getDocumentLock(final String documentPath) {
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

        final DocumentLock lock = getDocumentLock(path);
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

        final DocumentLock lock = getDocumentLock(path);
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
        final DocumentLock existingLock = getDocumentLock(documentPath.toString());
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
        final DocumentLock existingLock = getDocumentLock(documentPath.toString());
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

    /**
     * Returns true if the BTree for the file name is locked.
     *
     * @param btreeFileName The name of the .dbx file.
     *
     * @return true if the Btree is locked.
     */
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
     *
     * @deprecated Just a place holder until we can make the BTree reader/writer safe
     */
    @Deprecated
    public boolean isBtreeLockedForWrite(final String btreeFileName) {
        return isBtreeLocked(btreeFileName);
    }

    /**
     * Simple interface which describes
     * the minimum methods needed by LockManager
     * on a Document Lock.
     */
    interface DocumentLock {
        java.util.concurrent.locks.Lock readLock();
        java.util.concurrent.locks.Lock writeLock();
        boolean isWriteLocked();
        int getReadLockCount();
        boolean hasQueuedThreads();
    }

    /**
     * Adapts {@link MultiLock} to a {@link DocumentLock}.
     */
    private static class MultiLockDocumentLockAdapter implements DocumentLock {
        private final MultiLock multiLock;

        public MultiLockDocumentLockAdapter(final MultiLock multiLock) {
            this.multiLock = multiLock;
        }

        @Override
        public java.util.concurrent.locks.Lock readLock() {
            return multiLock.asReadLock();
        }

        @Override
        public java.util.concurrent.locks.Lock writeLock() {
            return multiLock.asWriteLock();
        }

        @Override
        public boolean isWriteLocked() {
            return multiLock.getWriteLockCount() > 0;
        }

        @Override
        public int getReadLockCount() {
            return multiLock.getReadLockCount();
        }

        @Override
        public boolean hasQueuedThreads() {
            return multiLock.hasQueuedThreads();
        }
    }

    /**
     * Adapts {@link ReentrantReadWriteLock} to a {@link DocumentLock}.
     */
    private static class ReentrantReadWriteLockDocumentLockAdapter implements DocumentLock {
        private final ReentrantReadWriteLock reentrantReadWriteLock;

        private ReentrantReadWriteLockDocumentLockAdapter(final ReentrantReadWriteLock reentrantReadWriteLock) {
            this.reentrantReadWriteLock = reentrantReadWriteLock;
        }

        @Override
        public java.util.concurrent.locks.Lock readLock() {
            return reentrantReadWriteLock.readLock();
        }

        @Override
        public java.util.concurrent.locks.Lock writeLock() {
            return reentrantReadWriteLock.writeLock();
        }

        @Override
        public boolean isWriteLocked() {
            return reentrantReadWriteLock.isWriteLocked();
        }

        @Override
        public int getReadLockCount() {
            return reentrantReadWriteLock.getReadLockCount();
        }

        @Override
        public boolean hasQueuedThreads() {
            return reentrantReadWriteLock.hasQueuedThreads();
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
    static boolean getLegacySystemPropertyOrConfigPropertyBool(final String legacyPropertyName,
            final Configuration configuration, final String configProperty, final boolean defaultValue) {
        final String legacyPropertyValue = System.getProperty(legacyPropertyName);
        if (legacyPropertyValue != null && !legacyPropertyValue.isEmpty()) {
            return Boolean.getBoolean(legacyPropertyName);
        } else if (configuration != null) {
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
