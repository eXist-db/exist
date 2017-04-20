/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage.lock;

import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

/**
 * Tests for Collection Locks
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class CollectionLocksTest {

    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors() * 3;

    /**
     * This test makes sure that there can be multiple reader locks
     * held by different threads on the same Collection at the same time
     *
     * A {@link CountDownLatch} is used to ensure that all threads hold
     * a read lock at the same time
     */
    @Test
    public void multipleReaders() throws LockException, InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;
        final XmldbURI collectionUri = XmldbURI.create("/db/x/y/z");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final CountDownLatch continueLatch = new CountDownLatch(numberOfThreads);

        // thread definition
        final Supplier<Callable<Void>> readCollectionFn = () -> () -> {
            try(final ManagedCollectionLock collectionLock = lockManager.acquireCollectionReadLock(collectionUri)) {
                continueLatch.countDown();
                continueLatch.await();
            }
            return null;
        };

        // create threads
        final List<Callable<Void>> callables = new ArrayList<>();
        for(int i = 0; i < numberOfThreads; i++) {
            callables.add(readCollectionFn.get());
        }

        // execute threads
        final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        final List<Future<Void>> futures = executorService.invokeAll(callables);

        // await all threads to finish
        for(final Future<Void> future : futures) {
            future.get();
        }
        executorService.shutdown();

        assertEquals(0, continueLatch.getCount());
    }

    /**
     * This test makes sure that there can be only a single writer lock
     * held by any one thread on the same Collection
     * at the same time
     *
     * A {@link CountDownLatch} is used to ensure that the first thread
     * holds the write lock when the second thread attempts to acquire it
     */
    @Test
    public void singleWriter() throws LockException, InterruptedException, ExecutionException {
        singleWriter(false);
    }

    /**
     * This test makes sure that there can be only a single writer lock
     * held by any one thread on the same Collection (and parent Collection)
     * at the same time
     *
     * A {@link CountDownLatch} is used to ensure that the first thread
     * holds the write lock when the second thread attempts to acquire it
     */
    @Test
    public void singleWriter_lockParent() throws LockException, InterruptedException, ExecutionException {
        singleWriter(true);
    }

    /**
     * This test abstraction makes sure that there can be only a single writer lock
     * held by any one thread on the same Collection (and optionally parent Collection)
     * at the same time
     *
     * A {@link CountDownLatch} is used to ensure that the first thread
     * holds the write lock when the second thread attempts to acquire it
     */
    private void singleWriter(final boolean lockParent) throws LockException, InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;
        final XmldbURI collectionUri = XmldbURI.create("/db/x/y/z");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final CountDownLatch thread2StartLatch = new CountDownLatch(1);
        final AtomicReference firstWriteHolder = new AtomicReference();
        final AtomicReference lastWriteHolder = new AtomicReference();

        final Callable<Void> callable1 = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(collectionUri, lockParent)) {
                    thread2StartLatch.countDown();
                    firstWriteHolder.compareAndSet(null, this);

                    // make sure the second thread is waiting for the write lock before we continue
                    if(lockParent) {
                        while (!lockManager.getCollectionLock(collectionUri.removeLastSegment().getCollectionPath()).hasQueuedThreads()) {
                            Thread.sleep(10);
                        }
                    } else {
                        while (!lockManager.getCollectionLock(collectionUri.getCollectionPath()).hasQueuedThreads()) {
                            Thread.sleep(10);
                        }
                    }

                    lastWriteHolder.set(this);
                }
                return null;
            }
        };

        final Callable<Void> callable2 = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                thread2StartLatch.await();
                try (final ManagedCollectionLock collectionLock = lockManager.acquireCollectionWriteLock(collectionUri, lockParent)) {
                    firstWriteHolder.compareAndSet(null, this);

                    lastWriteHolder.set(this);
                }
                return null;
            }
        };

        // create threads
        final List<Callable<Void>> callables = Arrays.asList(callable2, callable1);

        // execute threads
        final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        final List<Future<Void>> futures = executorService.invokeAll(callables);

        // await all threads to finish
        for(final Future<Void> future : futures) {
            future.get();
        }
        executorService.shutdown();

        assertEquals(0, thread2StartLatch.getCount());
        assertEquals(callable1, firstWriteHolder.get());
        assertEquals(callable2, lastWriteHolder.get());
    }
}
