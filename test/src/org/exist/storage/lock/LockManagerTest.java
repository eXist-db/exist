package org.exist.storage.lock;

import net.jcip.annotations.ThreadSafe;
import org.junit.Test;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by aretter on 05/01/2017.
 */
public class LockManagerTest {

    private static final int CONCURRENCY_LEVEL = 100;

    @Test
    public void getCollectionLock_isStripedByPath() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        final ReentrantReadWriteLock dbLock1 = lockManager.getCollectionLock("/db");
        assertNotNull(dbLock1);

        final ReentrantReadWriteLock dbLock2 = lockManager.getCollectionLock("/db");
        assertNotNull(dbLock2);

        assertTrue(dbLock1 == dbLock2);

        final ReentrantReadWriteLock abcLock = lockManager.getCollectionLock("/db/a/b/c");
        assertNotNull(abcLock);
        assertFalse(dbLock1 == abcLock);

        final ReentrantReadWriteLock defLock = lockManager.getCollectionLock("/db/d/e/f");
        assertNotNull(defLock);
        assertFalse(dbLock1 == defLock);

        assertFalse(abcLock == defLock);
    }
}
