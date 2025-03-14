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

import com.evolvedbinary.j8fu.function.RunnableE;
import net.jcip.annotations.ThreadSafe;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

/**
 * Tests to ensure the correct behaviour of the LockManager
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(Parameterized.class)
public class LockManagerTest {

    private static final int CONCURRENCY_LEVEL = 100;
    private static String previousLockEventsState = null;
    private static String previousPathsMultiWriterState = null;

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "Collection single-writer/multi-reader", false},
                { "Collection multi-writer/multi-reader", true }
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public boolean enablePathsMultiWriterState;

    @Before
    public void enableLockEventsState() {
        previousLockEventsState = System.setProperty(LockTable.PROP_DISABLE, "false");
        previousPathsMultiWriterState = System.setProperty(LockManager.PROP_ENABLE_PATHS_MULTI_WRITER, Boolean.toString(enablePathsMultiWriterState));
    }

    @After
    public void restoreLockEventsState() {
        restorePreviousPropertyState(LockTable.PROP_DISABLE, previousLockEventsState);
        restorePreviousPropertyState(LockManager.PROP_ENABLE_PATHS_MULTI_WRITER, previousPathsMultiWriterState);
    }

    private static void restorePreviousPropertyState(final String propertyName, final String previousValue) {
        if(previousValue != null) {
            System.setProperty(propertyName, previousValue);
        } else {
            System.clearProperty(propertyName);
        }
    }

    @Test
    public void getCollectionLock_isStripedByPath() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        final MultiLock dbLock1 = lockManager.getPathLock("/db");
        assertNotNull(dbLock1);

        final MultiLock dbLock2 = lockManager.getPathLock("/db");
        assertNotNull(dbLock2);

        assertTrue(dbLock1 == dbLock2);

        final MultiLock abcLock = lockManager.getPathLock("/db/a/b/c");
        assertNotNull(abcLock);
        assertFalse(dbLock1 == abcLock);

        final MultiLock defLock = lockManager.getPathLock("/db/d/e/f");
        assertNotNull(defLock);
        assertFalse(dbLock1 == defLock);

        assertFalse(abcLock == defLock);
    }

    /**
     * When acquiring a READ lock on the root Collection
     * ensure that we only take a single READ lock on the
     * root Collection
     */
    @Test
    public void acquireCollectionReadLock_root() throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try(final ManagedCollectionLock rootLock
                        = lockManager.acquireCollectionReadLock(XmldbURI.ROOT_COLLECTION_URI)) {
                assertNotNull(rootLock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);
    }

    /**
     * When acquiring a READ lock on a sub-collection of the root
     * Collection ensure that we hold a READ lock on the
     * sub-collection and READ_INTENTION locks on the ancestors,
     * and that we have performed top-down locking on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionReadLock_depth2() throws LockException {
        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedCollectionLock colALock
                         = lockManager.acquireCollectionReadLock(XmldbURI.create(collectionPath))) {
                assertNotNull(colALock);
            }
        });

        assertEquals(6, events.size());
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.READ_LOCK, event4.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event5.lockEventType);
        assertEquals(collectionPath, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event6.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event6.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event6.mode);
    }

    /**
     * When acquiring a READ lock on a descendant-collection of the root
     * Collection ensure that we hold a READ lock on the
     * descendant-collection and READ_INTENTION locks on the ancestors,
     * and that we have performed top-down locking on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionReadLock_depth3() throws LockException {
        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedCollectionLock colBLock
                         = lockManager.acquireCollectionReadLock(XmldbURI.create(collectionBPath))) {
                assertNotNull(colBLock);
            }
        });

        assertEquals(9, events.size());
        final LockAction event9 = events.pop();
        final LockAction event8 = events.pop();
        final LockAction event7 = events.pop();
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionAPath, event3.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionAPath, event4.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event4.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockEventType.Attempt, event5.lockEventType);
        assertEquals(collectionBPath, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        assertEquals(LockTable.LockEventType.Acquired, event6.lockEventType);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.READ_LOCK, event6.mode);

        // we now expect to release the lock on /db/colA/colB (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event7.lockEventType);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.READ_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event8.lockEventType);
        assertEquals(collectionAPath, event8.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event8.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event9.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event9.id);
        assertEquals(Lock.LockMode.INTENTION_READ, event9.mode);
    }

    /**
     * When acquiring a WRITE lock on the root Collection
     * ensure that we only take a single WRITE lock on the
     * root Collection
     */
    @Test
    public void acquireCollectionWriteLock_root_withoutLockParent() throws LockException {
        acquireCollectionWriteLock_root(false);
    }

    /**
     * When acquiring a WRITE lock on the root Collection
     * ensure that we only take a single WRITE lock on the
     * root Collection... even when lockParent is set
     */
    @Test
    public void acquireCollectionWriteLock_root_withLockParent() throws LockException {
        acquireCollectionWriteLock_root(true);
    }

    private void acquireCollectionWriteLock_root(final boolean lockParent) throws LockException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedCollectionLock rootLock
                         = lockManager.acquireCollectionWriteLock(XmldbURI.ROOT_COLLECTION_URI, lockParent)) {
                assertNotNull(rootLock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);
    }

    /**
     * When acquiring a WRITE lock on a sub-collection of the root (without locking the parent)
     * Collection ensure that we hold a single WRITE lock on the
     * sub-collection and perform top-down locking with INTENTION_WRITE locks on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth2_withoutLockParent() throws LockException {
        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            final boolean lockParent = false;
            try (final ManagedCollectionLock colALock
                         = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionPath), lockParent)) {
                assertNotNull(colALock);
            }
        });

        assertEquals(6, events.size());
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertIntentionWriteOrWriteMode(event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertIntentionWriteOrWriteMode(event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event5.lockEventType);
        assertEquals(collectionPath, event5.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event5.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event6.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event6.id);
        assertIntentionWriteOrWriteMode(event6.mode);
    }

    /**
     * When acquiring a WRITE lock on a sub-collection of the root (with parent locking)
     * Collection ensure that we hold a single WRITE lock on the
     * sub-collection and INTENTION_WRITE lock on the parent, by performing top-down lock-coupling
     * with READ locks (unless as in this-case the parent is the root, then WRITE locks) on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth2_withLockParent() throws LockException {
        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            final boolean lockParent = true;
            try (final ManagedCollectionLock colALock
                         = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionPath), lockParent)) {
                assertNotNull(colALock);
            }
        });

        assertEquals(6, events.size());
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to release the lock on /db/colA and then /db (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockEventType.Released, event5.lockEventType);
        assertEquals(collectionPath, event5.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event5.mode);

        assertEquals(LockTable.LockEventType.Released, event6.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);
    }

    /**
     * When acquiring a WRITE lock on a descendant-collection of the root (without locking the parent)
     * Collection ensure that we hold a single WRITE lock on the
     * descendant-collection and perform top-down locking with INTENTION_WRITE locks on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth3_withoutLockParent() throws LockException {
        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            final boolean lockParent = false;
            try (final ManagedCollectionLock colBLock
                         = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionBPath), lockParent)) {
                assertNotNull(colBLock);
            }

        });

        assertEquals(9, events.size());
        final LockAction event9 = events.pop();
        final LockAction event8 = events.pop();
        final LockAction event7 = events.pop();
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertIntentionWriteOrWriteMode(event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertIntentionWriteOrWriteMode(event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionAPath, event3.id);
        assertIntentionWriteOrWriteMode(event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionAPath, event4.id);
        assertIntentionWriteOrWriteMode(event4.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockEventType.Attempt, event5.lockEventType);
        assertEquals(collectionBPath, event5.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event5.mode);

        assertEquals(LockTable.LockEventType.Acquired, event6.lockEventType);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);

        // we now expect to release the lock on /db/colA/colB (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event7.lockEventType);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event8.lockEventType);
        assertEquals(collectionAPath, event8.id);
        assertIntentionWriteOrWriteMode(event8.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event9.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event9.id);
        assertIntentionWriteOrWriteMode(event9.mode);
    }

    /**
     * When acquiring a WRITE lock on a descendant-collection of the root (with parent locking)
     * Collection ensure that we hold a single WRITE lock on the
     * descendant-collection and a single WRITE lock on the parent, and that we perform top-down locking
     * with INTENTION_WRITE locks (apart from the parent which takes a WRITE lock) on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth3_withLockParent() throws LockException {
        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            final boolean lockParent = true;
            try (final ManagedCollectionLock colBLock
                         = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionBPath), lockParent)) {
                assertNotNull(colBLock);
            }

        });

        assertEquals(9, events.size());
        final LockAction event9 = events.pop();
        final LockAction event8 = events.pop();
        final LockAction event7 = events.pop();
        final LockAction event6 = events.pop();
        final LockAction event5 = events.pop();
        final LockAction event4 = events.pop();
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertIntentionWriteOrWriteMode(event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertIntentionWriteOrWriteMode(event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockEventType.Attempt, event3.lockEventType);
        assertEquals(collectionAPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockEventType.Acquired, event4.lockEventType);
        assertEquals(collectionAPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockEventType.Attempt, event5.lockEventType);
        assertEquals(collectionBPath, event5.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event5.mode);

        assertEquals(LockTable.LockEventType.Acquired, event6.lockEventType);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);

        // we now expect to release the lock on /db/colA/colB and then /db/colA (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockEventType.Released, event7.lockEventType);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA  (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockEventType.Released, event8.lockEventType);
        assertEquals(collectionAPath, event8.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event8.mode);

        // we now expect to release the lock on /db  (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockEventType.Released, event9.lockEventType);
        assertEquals(XmldbURI.ROOT_COLLECTION, event9.id);
        assertIntentionWriteOrWriteMode(event9.mode);
    }

    @Test
    public void getDocumentLock_isStripedByPath() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        final MultiLock doc1Lock1 = lockManager.getDocumentLock("/db/1.xml");
        assertNotNull(doc1Lock1);

        final MultiLock doc1Lock2 = lockManager.getDocumentLock("/db/1.xml");
        assertNotNull(doc1Lock2);

        assertTrue(doc1Lock1 == doc1Lock2);

        final MultiLock doc2Lock = lockManager.getDocumentLock("/db/a/b/c/2.xml");
        assertNotNull(doc2Lock);
        assertFalse(doc1Lock1 == doc2Lock);

        final MultiLock doc3Lock = lockManager.getDocumentLock("/db/d/e/f/3.xml");
        assertNotNull(doc3Lock);
        assertFalse(doc1Lock1 == doc3Lock);

        assertFalse(doc2Lock == doc3Lock);
    }

    /**
     * When acquiring a READ lock on a Document
     * ensure that we only take a single READ lock on the
     * Document
     */
    @Test
    public void acquireDocumentReadLock() throws LockException {
        final XmldbURI docUri = XmldbURI.create("/db/a/b/c/1.xml");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedDocumentLock doc1Lock
                         = lockManager.acquireDocumentReadLock(docUri)) {
                assertNotNull(doc1Lock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(docUri, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(docUri, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to release the lock on the document (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(docUri, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);
    }

    /**
     * When acquiring a WRITE lock on a Document
     * ensure that we only take a single WRITE lock on the
     * Document
     */
    @Test
    public void acquireDocumentWriteLock() throws LockException {
        final XmldbURI docUri = XmldbURI.create("/db/a/b/c/1.xml");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedDocumentLock doc1Lock
                         = lockManager.acquireDocumentWriteLock(docUri)) {
                assertNotNull(doc1Lock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(docUri, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(docUri, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to release the lock on the document (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(docUri, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);
    }

    @Test
    public void getBtreeLock_isStripedByPath() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        final ReentrantLock btree1Lock1 = lockManager.getBTreeLock("btree1.dbx");
        assertNotNull(btree1Lock1);

        final ReentrantLock btree1Lock2 = lockManager.getBTreeLock("btree1.dbx");
        assertNotNull(btree1Lock2);

        assertTrue(btree1Lock1 == btree1Lock2);

        final ReentrantLock btree2Lock = lockManager.getBTreeLock("btree2.dbx");
        assertNotNull(btree2Lock);
        assertFalse(btree1Lock1 == btree2Lock);

        final ReentrantLock btree3Lock = lockManager.getBTreeLock("btree3.dbx");
        assertNotNull(btree3Lock);
        assertFalse(btree1Lock1 == btree3Lock);

        assertFalse(btree2Lock == btree3Lock);
    }

    /**
     * When acquiring a READ lock on a BTree
     * ensure that we only take a single BTree lock on the
     * Document
     */
    @Test
    public void acquireBTreeReadLock() throws LockException {
        final String btree1Name = "btree1.dbx";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedLock<ReentrantLock> btree1Lock
                         = lockManager.acquireBtreeReadLock(btree1Name)) {
                assertNotNull(btree1Lock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(btree1Name, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(btree1Name, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to release the lock on the document (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(btree1Name, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);
    }

    /**
     * When acquiring a WRITE lock on a BTree
     * ensure that we only take a single WRITE lock on the
     * BTree
     */
    @Test
    public void acquireBTreeWriteLock() throws LockException {
        final String btree1Name = "btree1.dbx";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Stack<LockAction> events = recordLockEvents(lockManager, () -> {
            try (final ManagedLock<ReentrantLock> btree1Lock
                         = lockManager.acquireBtreeWriteLock(btree1Name)) {
                assertNotNull(btree1Lock);
            }
        });

        assertEquals(3, events.size());
        final LockAction event3 = events.pop();
        final LockAction event2 = events.pop();
        final LockAction event1 = events.pop();

        assertEquals(LockTable.LockEventType.Attempt, event1.lockEventType);
        assertEquals(btree1Name, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockEventType.Acquired, event2.lockEventType);
        assertEquals(btree1Name, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to release the lock on the document (as the managed lock was closed)
        assertEquals(LockTable.LockEventType.Released, event3.lockEventType);
        assertEquals(btree1Name, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);
    }


    private Stack<LockAction> recordLockEvents(final LockManager lockManager, final RunnableE<LockException> runnable) throws LockException{
        final LockTable lockTable = lockManager.getLockTable();
        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        // wait for the listener to be registered
        while(!lockEventRecordingListener.isRegistered()) {}

        runnable.run();

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        return lockEventRecordingListener.getEvents();
    }

    private void assertIntentionWriteOrWriteMode(final Lock.LockMode lockMode) {
        final Lock.LockMode writeMode = enablePathsMultiWriterState ? Lock.LockMode.INTENTION_WRITE : Lock.LockMode.WRITE_LOCK;
        assertEquals(writeMode, lockMode);
    }

    @ThreadSafe
    private static class LockEventRecordingListener implements LockTable.LockEventListener {
        private final Stack<LockAction> events = new Stack<>();
        private final AtomicBoolean registered = new AtomicBoolean();

        @Override
        public void registered() {
            registered.compareAndSet(false, true);
        }

        @Override
        public void unregistered() {
            registered.compareAndSet(true, false);
        }

        public boolean isRegistered() {
            return registered.get();
        }

        @Override
        public void accept(final LockTable.LockEventType lockEventType, final long timestamp, final long groupId,
                final LockTable.Entry entry) {
            // read count first to ensure memory visibility from volatile!
            final int localCount = entry.getCount();
            events.push(new LockAction(lockEventType, groupId, entry.getId(), entry.getLockType(), entry.getLockMode(),
                    entry.getOwner(), localCount, timestamp, entry.stackTraces == null ? null : entry.stackTraces.getFirst()));
        }

        public Stack<LockAction> getEvents() {
            return events;
        }
    }

    private static class LockAction {
        public final LockTable.LockEventType lockEventType;
        public final long groupId;
        public final String id;
        public final Lock.LockType lockType;
        public final Lock.LockMode mode;
        public final String threadName;
        public final int count;
        /**
         * System#nanoTime()
         */
        public final long timestamp;
        @Nullable
        public final StackTraceElement[] stackTrace;

        private LockAction(final LockTable.LockEventType lockEventType, final long groupId, final String id,
                final Lock.LockType lockType, final Lock.LockMode mode, final String threadName, final int count, final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
            this.lockEventType = lockEventType;
            this.groupId = groupId;
            this.id = id;
            this.lockType = lockType;
            this.mode = mode;
            this.threadName = threadName;
            this.count = count;
            this.timestamp = timestamp;
            this.stackTrace = stackTrace;
        }
    }
}
