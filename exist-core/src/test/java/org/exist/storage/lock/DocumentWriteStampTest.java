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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LockManager#stampDocumentWrites()} and {@link LockManager#validateDocumentWrites(LockManager.DocumentWriteStamp, Iterable)}.
 */
public class DocumentWriteStampTest {

    private static final int CONCURRENCY_LEVEL = 10;
    private static final XmldbURI DOCUMENT = XmldbURI.create("/db/test/doc.xml");
    private static final XmldbURI SECOND_DOCUMENT = XmldbURI.create("/db/test/doc2.xml");

    @Test
    void validWithoutWrites() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        assertNotNull(stamp);
        assertTrue(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT)));
        assertTrue(lockManager.validateDocumentWrites(stamp));
    }

    @Test
    void invalidatedByAnotherThreadsWriteLockOnTheDocument() throws Exception {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        writeInAnotherThread(lockManager, DOCUMENT);
        assertFalse(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT)));
        assertFalse(lockManager.validateDocumentWrites(stamp));
    }

    @Test
    void notInvalidatedByAnotherThreadsWriteLockOnAnotherDocument() throws Exception {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        writeInAnotherThread(lockManager, SECOND_DOCUMENT);
        assertTrue(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT)));
        assertFalse(lockManager.validateDocumentWrites(stamp));
    }

    @Test
    void invalidWhileAnotherThreadHeldAWriteLockOnTheDocument() throws Exception {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(DOCUMENT)) {
                locked.countDown();
                release.await();
            } catch (final LockException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        locked.await();
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertFalse(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT)));
        assertTrue(lockManager.validateDocumentWrites(stamp, List.of(SECOND_DOCUMENT)));
    }

    @Test
    void notInvalidatedByTheThreadsOwnWriteLocks() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        final long ownBefore = lockManager.documentWriteLocksAcquiredByCurrentThread();
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(DOCUMENT);
                final ManagedDocumentLock unused = lockManager.acquireDocumentWriteLock(SECOND_DOCUMENT)) {
            assertEquals(2, lockManager.documentWriteLocksAcquiredByCurrentThread() - ownBefore);
            assertTrue(lockManager.currentThreadMayHoldDocumentWriteLock());
            assertTrue(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT, SECOND_DOCUMENT)));
            assertTrue(lockManager.validateDocumentWrites(stamp));
        }
        assertFalse(lockManager.currentThreadMayHoldDocumentWriteLock());
    }

    @Test
    void notPossibleWithPathLocksForDocuments() throws DatabaseConfigurationException {
        final Configuration configuration = new Configuration();
        configuration.setProperty(LockManager.CONFIGURATION_PATH_LOCKS_FOR_DOCUMENTS, true);
        final LockManager lockManager = new LockManager(configuration, CONCURRENCY_LEVEL);
        final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
        assertNull(stamp);
        assertFalse(lockManager.validateDocumentWrites(stamp, List.of(DOCUMENT)));
        assertTrue(lockManager.currentThreadMayHoldDocumentWriteLock());
    }

    private static void writeInAnotherThread(final LockManager lockManager, final XmldbURI document) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.runAsync(() -> {
            try {
                lockManager.acquireDocumentWriteLock(document).close();
            } catch (final LockException e) {
                throw new IllegalStateException(e);
            }
        }).get(10, TimeUnit.SECONDS);
    }
}
