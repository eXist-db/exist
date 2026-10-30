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
package org.exist.storage.lock;

import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.exist.storage.lock.LockManager.NO_OPTIMISTIC_DOCUMENT_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LockManager#tryOptimisticDocumentRead(XmldbURI)} and {@link LockManager#validateOptimisticDocumentRead(XmldbURI, long)}.
 */
public class OptimisticDocumentReadTest {

    private static final int CONCURRENCY_LEVEL = 10;
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/test");
    private static final XmldbURI DOCUMENT = XmldbURI.create("/db/test/doc.xml");
    private static final XmldbURI SECOND_DOCUMENT = XmldbURI.create("/db/test/doc2.xml");
    private static final XmldbURI OTHER_DOCUMENT = XmldbURI.create("/db/other/doc.xml");

    @Test
    void validWithoutDocumentWrites() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, stamp);
        assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
    }

    @Test
    void notPossibleWhileAWriteLockIsHeldOnADocumentInTheCollection() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock writeLock = lockManager.acquireDocumentWriteLock(DOCUMENT);
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
        writeLock.close();

        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, stamp);
        assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
    }

    @Test
    void invalidatedByAWriteLockOnADocumentInTheCollectionDuringTheRead() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        final ManagedDocumentLock writeLock = lockManager.acquireDocumentWriteLock(SECOND_DOCUMENT);
        assertFalse(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
        writeLock.close();
        assertFalse(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
    }

    @Test
    void notAffectedByAWriteLockOnADocumentInAnotherCollection() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(OTHER_DOCUMENT)) {
            assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
            assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
        }
    }

    @Test
    void notInvalidatedByDocumentReadLocks() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        final ManagedDocumentLock readLock = lockManager.acquireDocumentReadLock(DOCUMENT);
        assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
        readLock.close();
        assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
    }

    @Test
    void notAffectedByAWriteLockWhichIsStillWaiting() throws LockException, InterruptedException, ExecutionException, TimeoutException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final AtomicReference<Thread> writer = new AtomicReference<>();
        final CompletableFuture<Void> written;
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentReadLock(DOCUMENT)) {
            written = CompletableFuture.runAsync(() -> {
                writer.set(Thread.currentThread());
                try (final ManagedDocumentLock unused = lockManager.acquireDocumentWriteLock(DOCUMENT)) {
                    // nothing to do
                } catch (final LockException e) {
                    throw new IllegalStateException(e);
                }
            });
            awaitWaiting(writer);

            final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
            assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, stamp);
            assertTrue(lockManager.validateOptimisticDocumentRead(COLLECTION, stamp));
        }
        written.get(10, TimeUnit.SECONDS);
        assertEquals(0, lockManager.getDocumentWriteLocksHeld());
    }

    @Test
    void notPossibleWhileANestedDocumentWriteLockIsHeld() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock outer = lockManager.acquireDocumentWriteLock(DOCUMENT);
        final ManagedDocumentLock inner = lockManager.acquireDocumentWriteLock(DOCUMENT);
        assertEquals(2, lockManager.getDocumentWriteLocksHeld());
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
        inner.close();
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
        outer.close();
        assertEquals(0, lockManager.getDocumentWriteLocksHeld());
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
    }

    @Test
    void closingAWriteLockTwiceReleasesItOnce() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock first = lockManager.acquireDocumentWriteLock(DOCUMENT);
        final ManagedDocumentLock second = lockManager.acquireDocumentWriteLock(SECOND_DOCUMENT);
        second.close();
        second.close();
        assertEquals(1, lockManager.getDocumentWriteLocksHeld());
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
        first.close();
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
    }

    @Test
    void anInvalidStampNeverValidates() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        assertFalse(lockManager.validateOptimisticDocumentRead(COLLECTION, NO_OPTIMISTIC_DOCUMENT_READ));
    }

    @Test
    void countsValidAndFailedReads() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        lockManager.validateOptimisticDocumentRead(COLLECTION, lockManager.tryOptimisticDocumentRead(COLLECTION));
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(DOCUMENT)) {
            lockManager.tryOptimisticDocumentRead(COLLECTION);
        }
        final long stamp = lockManager.tryOptimisticDocumentRead(COLLECTION);
        lockManager.acquireDocumentWriteLock(DOCUMENT).close();
        lockManager.validateOptimisticDocumentRead(COLLECTION, stamp);

        assertEquals(1, lockManager.getOptimisticDocumentReadsValid());
        assertEquals(2, lockManager.getOptimisticDocumentReadsFailed());
    }

    @Test
    void notPossibleWithPathLocksForDocuments() throws DatabaseConfigurationException {
        final LockManager lockManager = pathLockManager();
        assertTrue(lockManager.isUsingPathLocksForDocuments());
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead(COLLECTION));
    }

    /**
     * With path locks for documents, {@link org.exist.collections.MutableCollection} reads its documents'
     * permissions without their locks while it holds its READ_LOCK, as that excludes any WRITE_LOCK on its documents.
     */
    @Test
    void withPathLocksACollectionReadLockExcludesDocumentWriteLocks() throws DatabaseConfigurationException, LockException, InterruptedException, ExecutionException, TimeoutException {
        final LockManager lockManager = pathLockManager();
        final AtomicReference<Thread> writer = new AtomicReference<>();
        final CompletableFuture<Void> written;
        try (final ManagedCollectionLock ignored = lockManager.acquireCollectionReadLock(COLLECTION)) {
            written = CompletableFuture.runAsync(() -> {
                writer.set(Thread.currentThread());
                try (final ManagedDocumentLock unused = lockManager.acquireDocumentWriteLock(DOCUMENT)) {
                    // nothing to do
                } catch (final LockException e) {
                    throw new IllegalStateException(e);
                }
            });
            awaitWaiting(writer);
            assertThrows(TimeoutException.class, () -> written.get(200, TimeUnit.MILLISECONDS));
        }
        written.get(10, TimeUnit.SECONDS);
    }

    private static LockManager pathLockManager() throws DatabaseConfigurationException {
        final Configuration configuration = new Configuration();
        configuration.setProperty(LockManager.CONFIGURATION_PATH_LOCKS_FOR_DOCUMENTS, true);
        return new LockManager(configuration, CONCURRENCY_LEVEL);
    }

    private static void awaitWaiting(final AtomicReference<Thread> thread) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.get() == null || (thread.get().getState() != Thread.State.WAITING && thread.get().getState() != Thread.State.TIMED_WAITING)) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("The thread did not wait for the lock");
            }
            Thread.sleep(10);
        }
    }
}
