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

import com.evolvedbinary.j8fu.function.SupplierE;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for Collection Locks
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CollectionLocksTest {

    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors() * 3;
    private static final int TEST_DEADLOCK_TIMEOUT = 24_000; // 24 seconds

    /**
     * In the noDeadlock tests this is the maximum amount of time to wait for the second thread to acquire its lock
     *
     * Note: this must be greater than the period required to acquire a lock under a contention of two threads
     */
    private static final int AWAIT_OTHER_THREAD_TIMEOUT = TEST_DEADLOCK_TIMEOUT / 4;

    /**
     * In the stress deadlock tests, the maximum amount of time
     * a thread should sleep whilst pausing (in the effort to cause a deadlock on contended lock)
     *
     * Note: this should likely be greater than the period required to acquire a lock under contention of CONCURRENCY_LEVEL threads
     */
    private static final int STRESS_DEADLOCK_THREAD_SLEEP = 200;   // 200ms

    /**
     * The maximum amount of time we should allow deadlock stress
     * tests to execute their threads for, after which we assume
     * a deadlock was caused
     *
     * Default: Twice the (maximum) sleep time of the total number of threads involved in the test
     */
    private static int STRESS_DEADLOCK_TEST_TIMEOUT = (STRESS_DEADLOCK_THREAD_SLEEP * CONCURRENCY_LEVEL) * 2;
    /*
       macOS on various CI platforms seems to generally be slower
       than other OS, therefore we permit a longer time-out.
     */
    static {
        final String ciEnv = System.getenv("CI");
        if (ciEnv != null && "true".equalsIgnoreCase(ciEnv) &&
                System.getProperty("os.name").toLowerCase().contains("mac")) {
            STRESS_DEADLOCK_TEST_TIMEOUT = (STRESS_DEADLOCK_THREAD_SLEEP * CONCURRENCY_LEVEL) * 4;
        }
    }

    /**
     * In the single writer tests, the maximum amount of time
     * a thread should sleep whilst waiting for thread queueing
     *
     * Note: this should likely be greater than the period required to acquire a lock under contention of CONCURRENCY_LEVEL threads
     */
    private static final int SINGLE_WRITER_THREAD_SLEEP = 30;   // 30ms

    /**
     * The maximum amount of time we should allow singleWriter
     * tests to execute their threads for, after which we assume
     * a deadlock was caused
     *
     * Default: Quadruple the (maximum) sleep time of the total number of threads involved in the test
     */
    private static final int SINGLE_WRITER_TEST_TIMEOUT = (SINGLE_WRITER_THREAD_SLEEP * CONCURRENCY_LEVEL) * 4;

    /**
     * The maximum amount of time we should allow multiReaders
     * tests to execute their threads for, after which we assume
     * a deadlock was caused
     *
     * Default: 4 seconds
     */
    private static final int MULTI_READER_TEST_TIMEOUT = 4000; // 4 seconds

    private static final Random random = new Random();

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
        final List<Future<Void>> futures = executorService.invokeAll(callables, MULTI_READER_TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // await all threads to finish
        for(final Future<Void> future : futures) {
            if(future.isCancelled()) {
                fail("multipleReaders test likely showed a thread deadlock");
            }
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
                    Thread.sleep(SINGLE_WRITER_THREAD_SLEEP);
//                    if(lockParent) {
//                        while (!lockManager.getCollectionLock(collectionUri.removeLastSegment().getCollectionPath()).hasQueuedThreads()) {
//                            Thread.sleep(SINGLE_WRITER_THREAD_SLEEP);
//                        }
//                    } else {
//                        while (!lockManager.getCollectionLock(collectionUri.getCollectionPath()).hasQueuedThreads()) {
//                            Thread.sleep(SINGLE_WRITER_THREAD_SLEEP);
//                        }
//                    }

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
        final List<Future<Void>> futures = executorService.invokeAll(callables, SINGLE_WRITER_TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // await all threads to finish
        for(final Future<Void> future : futures) {
            if(future.isCancelled()) {
                fail("singleWriter test likely showed a thread deadlock");
            }
        }
        executorService.shutdown();

        assertEquals(0, thread2StartLatch.getCount());
        assertEquals(callable1, firstWriteHolder.get());
        assertEquals(callable2, lastWriteHolder.get());
    }

    /**
     * Scenario 1 - Two Writers, Same Subtree, Parent first
     *
     * t1,1 - request WRITE_LOCK /db/x/y
     * t2,1 - request WRITE_LOCK /db/x/y/z
     * t1,2 - request WRITE_LOCK /db/x/y/z
     * t2,2 - request WRITE_LOCK /db/x/y
     */
    @Test
    public void S1_noDeadlock_writeWrite_subtree_parentFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        noDeadlock_writeWrite(col1Uri, col2Uri);
    }

    /**
     * Scenario 2 - Two Writers, Same Subtree, Descendant first
     *
     * t1,1 - request WRITE_LOCK /db/x/y/z
     * t2,1 - request WRITE_LOCK /db/x/y
     * t1,2 - request WRITE_LOCK /db/x/y
     * t2,2 - request WRITE_LOCK /db/x/y/z
     */
    @Test
    public void S2_noDeadlock_writeWrite_subtree_descendantFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y/z");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y");

        noDeadlock_writeWrite(col1Uri, col2Uri);
    }

    /**
     * Scenario 3 - Two Writers, No common subtree, Left to right
     *
     * t1,1 - request WRITE_LOCK /db/a
     * t2,1 - request WRITE_LOCK /db/b
     * t1,2 - request WRITE_LOCK /db/b
     * t2,2 - request WRITE_LOCK /db/a
     */
    @Test
    public void S3_noDeadlock_writeWrite_leftRight() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        noDeadlock_writeWrite(col1Uri, col2Uri);
    }

    /**
     * Scenario 4 - Two Writers, No common subtree, Right to left
     *
     * t1,1 - request WRITE_LOCK /db/b
     * t2,1 - request WRITE_LOCK /db/a
     * t1,2 - request WRITE_LOCK /db/a
     * t2,2 - request WRITE_LOCK /db/b
     */
    @Test
    public void S4_noDeadlock_writeWrite_rightLeft() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/b");
        final XmldbURI col2Uri = XmldbURI.create("/db/a");

        noDeadlock_writeWrite(col1Uri, col2Uri);
    }

    /**
     * Scenario 5 - One Writer and One Reader, Same Subtree, Parent first
     *
     * t1,1 - request WRITE_LOCK /db/x/y
     * t2,1 - request READ_LOCK /db/x/y/z
     * t1,2 - request WRITE_LOCK /db/x/y/z
     * t2,2 - request READ_LOCK /db/x/y
     */
    @Test
    public void S5_noDeadlock_writeRead_subtree_parentFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        noDeadlock_writeRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 6 - One Writer and One Reader, Same Subtree, Descendant first
     *
     * t1,1 - request WRITE_LOCK /db/x/y/z
     * t2,1 - request READ_LOCK /db/x/y
     * t1,2 - request WRITE_LOCK /db/x/y
     * t2,2 - request READ_LOCK /db/x/y/z
     */
    @Test
    public void S6_noDeadlock_writeRead_subtree_descendantFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y/z");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y");

        noDeadlock_writeRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 7 - One Writer and One Reader, No common subtree, Left to right
     *
     * t1,1 - request WRITE_LOCK /db/a
     * t2,1 - request READ_LOCK /db/b
     * t1,2 - request WRITE_LOCK /db/b
     * t2,2 - request READ_LOCK /db/a
     */
    @Test
    public void S7_noDeadlock_writeRead_leftRight() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        noDeadlock_writeRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 8 - One Writer and One Reader, No common subtree, Right to left
     *
     * t1,1 - request WRITE_LOCK /db/b
     * t2,1 - request READ_LOCK /db/a
     * t1,2 - request WRITE_LOCK /db/a
     * t2,2 - request READ_LOCK /db/b
     */
    @Test
    public void S8_noDeadlock_writeRead_rightLeft() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/b");
        final XmldbURI col2Uri = XmldbURI.create("/db/a");

        noDeadlock_writeRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 9 - Two Readers, Same Subtree, Parent first
     *
     * t1,1 - request READ_LOCK /db/x/y
     * t2,1 - request READ_LOCK /db/x/y/z
     * t1,2 - request READ_LOCK /db/x/y/z
     * t2,2 - request READ_LOCK /db/x/y
     */
    @Test
    public void S9_noDeadlock_readRead_subtree_parentFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        noDeadlock_readRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 10 - Two Readers, Same Subtree, Descendant first
     *
     * t1,1 - request READ_LOCK /db/x/y/z
     * t2,1 - request READ_LOCK /db/x/y
     * t1,2 - request READ_LOCK /db/x/y
     * t2,2 - request READ_LOCK /db/x/y/z
     */
    @Test
    public void S10_noDeadlock_readRead_subtree_descendantFirst() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/x/y/z");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y");

        noDeadlock_readRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 11 - Two Readers, No common subtree, Left to right
     *
     * t1,1 - request READ_LOCK /db/a
     * t2,1 - request READ_LOCK /db/b
     * t1,2 - request READ_LOCK /db/b
     * t2,2 - request READ_LOCK /db/a
     */
    @Test
    public void S11_noDeadlock_readRead_leftRight() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        noDeadlock_readRead(col1Uri, col2Uri);
    }

    /**
     * Scenario 12 - Two Readers, No common subtree, Right to left
     *
     * t1,1 - request READ_LOCK /db/b
     * t2,1 - request READ_LOCK /db/a
     * t1,2 - request READ_LOCK /db/a
     * t2,2 - request READ_LOCK /db/b
     */
    @Test
    public void S12_noDeadlock_readRead_rightLeft() throws InterruptedException, ExecutionException {
        final XmldbURI col1Uri = XmldbURI.create("/db/b");
        final XmldbURI col2Uri = XmldbURI.create("/db/a");

        noDeadlock_readRead(col1Uri, col2Uri);
    }

    private void noDeadlock_writeWrite(final XmldbURI col1Uri, final XmldbURI col2Uri) throws InterruptedException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Tuple2<Callable<Void>, Callable<Void>> t1t2 = createInterleaved(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t1,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t1,2
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false)    //t2,2
        );
        noDeadlock(t1t2);
    }

    private void noDeadlock_writeRead(final XmldbURI col1Uri, final XmldbURI col2Uri) throws InterruptedException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Tuple2<Callable<Void>, Callable<Void>> t1t2 = createInterleaved(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),     //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),                       //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),     //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri)                        //t2,2
        );
        noDeadlock(t1t2);
    }

    private void noDeadlock_readRead(final XmldbURI col1Uri, final XmldbURI col2Uri) throws InterruptedException {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final Tuple2<Callable<Void>, Callable<Void>> t1t2 = createInterleaved(
                () -> lockManager.acquireCollectionReadLock(col1Uri),   //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t2,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri)    //t2,2
        );
        noDeadlock(t1t2);
    }

    private void noDeadlock(final Tuple2<Callable<Void>, Callable<Void>> t1t2) throws InterruptedException {
        // create threads
        final List<Callable<Void>> callables = Arrays.asList(t1t2._1, t1t2._2);

        // execute threads
        final ExecutorService executorService = Executors.newFixedThreadPool(callables.size());
        final List<Future<Void>> futures = executorService.invokeAll(callables, TEST_DEADLOCK_TIMEOUT, TimeUnit.MILLISECONDS);

        // await all threads to finish
        for(final Future<Void> future : futures) {
            if(future.isCancelled()) {
                fail("Test likely showed a thread deadlock");
            }
        }
        executorService.shutdown();
    }

    private Tuple2<Callable<Void>, Callable<Void>> createInterleaved(final SupplierE<ManagedCollectionLock, LockException> thread1Lock1, final SupplierE<ManagedCollectionLock, LockException> thread2Lock1, final SupplierE<ManagedCollectionLock, LockException> thread1Lock2, final SupplierE<ManagedCollectionLock, LockException> thread2Lock2) {
        final CountDownLatch thread2StartLatch = new CountDownLatch(1);
        final CountDownLatch thread1ContinueLatch = new CountDownLatch(1);
        final CountDownLatch thread2ContinueLatch = new CountDownLatch(1);
        final CountDownLatch thread1FinishLatch = new CountDownLatch(1);
        final CountDownLatch thread2FinishLatch = new CountDownLatch(1);

        final Callable<Void> callable1 = () -> {
            try (final ManagedCollectionLock col1Lock = thread1Lock1.get()) {
                thread2StartLatch.countDown();

                thread1ContinueLatch.await(AWAIT_OTHER_THREAD_TIMEOUT, TimeUnit.MILLISECONDS);

                try (final ManagedCollectionLock col2Lock = thread1Lock2.get()) {
                    thread2ContinueLatch.countDown();

                    thread1FinishLatch.await(AWAIT_OTHER_THREAD_TIMEOUT, TimeUnit.MILLISECONDS);
                }

                thread2FinishLatch.countDown();
            }
            return null;
        };

        final Callable<Void> callable2 = () -> {
            thread2StartLatch.await();
            try (final ManagedCollectionLock col2Lock = thread2Lock1.get()) {
                thread1ContinueLatch.countDown();

                thread2ContinueLatch.await();

                try (final ManagedCollectionLock col1Lock = thread2Lock2.get()) {
                    thread1FinishLatch.countDown();

                    thread2FinishLatch.await();
                }
            }
            return null;
        };

        return new Tuple2<>(callable1, callable2);
    }

    @Test
    public void stress_noDeadlock_writeWrite_subtree() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t1,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t1,2
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t2,2
                numberOfThreads
        );
    }

    @Test
    public void stress_noDeadlock_writeWrite() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t1,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t1,2
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t2,2
                numberOfThreads
        );
    }

    @Test
    public void stress_noDeadlock_writeRead_subtree() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),     //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),                       //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),     //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri),                       //t2,2
                numberOfThreads
        );
    }

    @Test
    public void stress_noDeadlock_writeRead() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionWriteLock(col1Uri, false),   //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),                     //t2,1
                () -> lockManager.acquireCollectionWriteLock(col2Uri, false),   //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri),                     //t2,2
                numberOfThreads
        );
    }

    @Test
    public void stress_noDeadlock_readRead_subtree() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/x/y");
        final XmldbURI col2Uri = XmldbURI.create("/db/x/y/z");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionReadLock(col1Uri),   //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t2,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri),   //t2,2
                numberOfThreads
        );
    }

    @Test
    public void stress_noDeadlock_readRead() throws InterruptedException, ExecutionException {
        final int numberOfThreads = CONCURRENCY_LEVEL;

        final XmldbURI col1Uri = XmldbURI.create("/db/a");
        final XmldbURI col2Uri = XmldbURI.create("/db/b");

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        stress_noDeadlock(
                () -> lockManager.acquireCollectionReadLock(col1Uri),   //t1,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t2,1
                () -> lockManager.acquireCollectionReadLock(col2Uri),   //t1,2
                () -> lockManager.acquireCollectionReadLock(col1Uri),   //t2,2
                numberOfThreads
        );
    }

    void stress_noDeadlock(final SupplierE<ManagedCollectionLock, LockException> thread1Lock1, final SupplierE<ManagedCollectionLock, LockException> thread2Lock1, final SupplierE<ManagedCollectionLock, LockException> thread1Lock2, final SupplierE<ManagedCollectionLock, LockException> thread2Lock2, final int numberOfThreads) throws InterruptedException, ExecutionException {
        final Supplier<Callable<Void>> col1ThenCol2_callable = () -> () -> {
            try(final ManagedCollectionLock col1 = thread1Lock1.get()) {
                sleep();

                try(final ManagedCollectionLock col2 = thread1Lock2.get()) {
                    sleep();
                }
            }
            return null;
        };

        final Supplier<Callable<Void>> col2ThenCol1_callable = () -> () -> {
            try(final ManagedCollectionLock col2 = thread2Lock1.get()) {
                sleep();

                try(final ManagedCollectionLock col1 = thread2Lock2.get()) {
                    sleep();
                }
            }
            return null;
        };

        // create threads
        final List<Callable<Void>> callables = new ArrayList<>();
        for(int i = 0; i < numberOfThreads / 2; i++) {
            callables.add(col1ThenCol2_callable.get());
            callables.add(col2ThenCol1_callable.get());
        }

        // execute threads
        final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        final List<Future<Void>> futures = executorService.invokeAll(callables, STRESS_DEADLOCK_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        try {
            // await all threads to finish
            for (final Future<Void> future : futures) {
                if (future.isCancelled()) {
                    fail("Stress test likely showed a thread deadlock");
                    future.get(); // get the result, so that if the future was cancelled due to an exception, the exception is thrown
                }
            }
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * Sleeps between 1 and STRESS_DEADLOCK_THREAD_SLEEP
     * milliseconds
     */
    private static void sleep() {
        try {
            Thread.sleep(1 + random.nextInt(STRESS_DEADLOCK_THREAD_SLEEP));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
