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

import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;

import static org.exist.storage.lock.LockManager.NO_OPTIMISTIC_DOCUMENT_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LockManager#tryOptimisticDocumentRead()} and {@link LockManager#validateOptimisticDocumentRead(long)}.
 */
public class OptimisticDocumentReadTest {

    private static final int CONCURRENCY_LEVEL = 10;
    private static final XmldbURI DOCUMENT = XmldbURI.create("/db/test/doc.xml");
    private static final XmldbURI OTHER_DOCUMENT = XmldbURI.create("/db/other/doc.xml");

    @Test
    void validWithoutDocumentWrites() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead();
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, stamp);
        assertTrue(lockManager.validateOptimisticDocumentRead(stamp));
    }

    @Test
    void notPossibleWhileADocumentWriteLockIsHeld() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock writeLock = lockManager.acquireDocumentWriteLock(DOCUMENT);
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
        writeLock.close();

        final long stamp = lockManager.tryOptimisticDocumentRead();
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, stamp);
        assertTrue(lockManager.validateOptimisticDocumentRead(stamp));
    }

    @Test
    void invalidatedByADocumentWriteLockDuringTheRead() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead();
        final ManagedDocumentLock writeLock = lockManager.acquireDocumentWriteLock(OTHER_DOCUMENT);
        assertFalse(lockManager.validateOptimisticDocumentRead(stamp));
        writeLock.close();
        assertFalse(lockManager.validateOptimisticDocumentRead(stamp));
    }

    @Test
    void notInvalidatedByDocumentReadLocks() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final long stamp = lockManager.tryOptimisticDocumentRead();
        final ManagedDocumentLock readLock = lockManager.acquireDocumentReadLock(DOCUMENT);
        assertTrue(lockManager.validateOptimisticDocumentRead(stamp));
        readLock.close();
        assertTrue(lockManager.validateOptimisticDocumentRead(stamp));
    }

    @Test
    void notPossibleWhileANestedDocumentWriteLockIsHeld() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock outer = lockManager.acquireDocumentWriteLock(DOCUMENT);
        final ManagedDocumentLock inner = lockManager.acquireDocumentWriteLock(DOCUMENT);
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
        inner.close();
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
        outer.close();
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
    }

    @Test
    void closingAWriteLockTwiceReleasesItOnce() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final ManagedDocumentLock first = lockManager.acquireDocumentWriteLock(DOCUMENT);
        final ManagedDocumentLock second = lockManager.acquireDocumentWriteLock(OTHER_DOCUMENT);
        second.close();
        second.close();
        assertEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
        first.close();
        assertNotEquals(NO_OPTIMISTIC_DOCUMENT_READ, lockManager.tryOptimisticDocumentRead());
    }

    @Test
    void anInvalidStampNeverValidates() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        assertFalse(lockManager.validateOptimisticDocumentRead(NO_OPTIMISTIC_DOCUMENT_READ));
    }
}
