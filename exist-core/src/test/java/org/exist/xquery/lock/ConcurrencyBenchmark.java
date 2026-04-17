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
package org.exist.xquery.lock;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency benchmark for measuring the impact of preclaiming locks
 * on read, write, and mixed workloads.
 *
 * <p>Run on both the base branch (develop) and the preclaiming branch
 * to get before/after comparisons. Results are printed to stdout.</p>
 *
 * <p>This class intentionally does <em>not</em> end in {@code *Test} so that
 * Surefire will not discover it during normal {@code mvn test} runs.
 * Run explicitly with:</p>
 * <pre>
 *   mvn test -pl exist-core -Dtest=ConcurrencyBenchmark \
 *       -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
public class ConcurrencyBenchmark {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION_NAME = "benchmark-concurrency";
    private static final String COLLECTION_PATH = "/db/" + COLLECTION_NAME;

    private static final int NUM_DOCUMENTS = 8;
    private static final int DURATION_SECONDS = 5;
    private static final int[] THREAD_COUNTS = {2, 4, 8};

    @BeforeClass
    public static void assumeBenchmarks() {
        Assume.assumeTrue("Benchmarks are disabled. Set -Dexist.run.benchmarks=true to enable.",
                Boolean.getBoolean("exist.run.benchmarks"));
    }

    @BeforeClass
    public static void setUp() throws XMLDBException {
        if (!Boolean.getBoolean("exist.run.benchmarks")) {
            return;
        }

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        cms.createCollection(COLLECTION_NAME);

        final Collection col = server.getRoot().getChildCollection(COLLECTION_NAME);
        for (int i = 1; i <= NUM_DOCUMENTS; i++) {
            final XMLResource res = col.createResource("doc" + i + ".xml", XMLResource.class);
            res.setContent("<root><counter>0</counter><data>" + "x".repeat(100) + "</data></root>");
            col.storeResource(res);
        }
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        if (!Boolean.getBoolean("exist.run.benchmarks")) {
            return;
        }

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(COLLECTION_NAME);
    }

    // ---- Scenario 1: Concurrent reads ----

    @Test
    public void concurrentReads() throws Exception {
        System.out.println("\n=== Concurrent Reads (all threads read same document) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter/text()");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "read-same-doc", threads, opsPerSec);
        }
    }

    @Test
    public void concurrentReadsDistributed() throws Exception {
        System.out.println("\n=== Concurrent Reads (each thread reads different doc) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                final int docNum = (threadId % NUM_DOCUMENTS) + 1;
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/data/text()");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "read-diff-docs", threads, opsPerSec);
        }
    }

    // ---- Scenario 2: Concurrent writes ----

    @Test
    public void concurrentWritesSameDoc() throws Exception {
        System.out.println("\n=== Concurrent Writes (all threads update same document) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("update value doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter " +
                                "with string(number(doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter) + 1)");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "write-same-doc", threads, opsPerSec);
        }
    }

    @Test
    public void concurrentWritesDifferentDocs() throws Exception {
        System.out.println("\n=== Concurrent Writes (each thread updates its own document) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                final int docNum = (threadId % NUM_DOCUMENTS) + 1;
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("update value doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter " +
                                "with string(number(doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter) + 1)");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "write-diff-docs", threads, opsPerSec);
        }
    }

    // ---- Scenario 3: Mixed read/write ----

    @Test
    public void mixedReadWrite() throws Exception {
        System.out.println("\n=== Mixed Read/Write (half readers, half writers, same document) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                final boolean isWriter = (threadId % 2 == 0);
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        if (isWriter) {
                            qs.query("update value doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter " +
                                    "with string(number(doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter) + 1)");
                        } else {
                            qs.query("doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter/text()");
                        }
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "mixed-same-doc", threads, opsPerSec);
        }
    }

    @Test
    public void mixedReadWriteDistributed() throws Exception {
        System.out.println("\n=== Mixed Read/Write (half readers, half writers, different documents) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                final boolean isWriter = (threadId % 2 == 0);
                final int docNum = (threadId % NUM_DOCUMENTS) + 1;
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        if (isWriter) {
                            qs.query("update value doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter " +
                                    "with string(number(doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter) + 1)");
                        } else {
                            qs.query("doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter/text()");
                        }
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "mixed-diff-docs", threads, opsPerSec);
        }
    }

    // ---- Scenario 4: Write contention with XQUF (deferred PUL) ----

    @Test
    public void concurrentXQUFWritesSameDoc() throws Exception {
        System.out.println("\n=== Concurrent XQUF Writes (all threads update same document via PUL) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("replace value of node doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter " +
                                "with string(number(doc('" + COLLECTION_PATH + "/doc1.xml')/root/counter) + 1)");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "xquf-write-same-doc", threads, opsPerSec);
        }
    }

    @Test
    public void concurrentXQUFWritesDifferentDocs() throws Exception {
        System.out.println("\n=== Concurrent XQUF Writes (each thread updates its own document via PUL) ===");
        printHeader();

        for (final int threads : THREAD_COUNTS) {
            resetDocuments();
            final double opsPerSec = runBenchmark(threads, (threadId, barrier, opsCounter, errorCounter) -> {
                final XQueryService qs = server.getRoot().getService(XQueryService.class);
                final int docNum = (threadId % NUM_DOCUMENTS) + 1;
                barrier.await();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
                while (System.nanoTime() < deadline) {
                    try {
                        qs.query("replace value of node doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter " +
                                "with string(number(doc('" + COLLECTION_PATH + "/doc" + docNum + ".xml')/root/counter) + 1)");
                        opsCounter.incrementAndGet();
                    } catch (final XMLDBException e) {
                        errorCounter.incrementAndGet();
                    }
                }
            });
            System.out.printf("  %-24s  threads=%d  ops/sec=%8.0f%n", "xquf-write-diff-docs", threads, opsPerSec);
        }
    }

    // ---- Helpers ----

    @FunctionalInterface
    interface BenchmarkTask {
        void run(int threadId, CyclicBarrier barrier, AtomicInteger opsCounter, AtomicInteger errorCounter) throws Exception;
    }

    private double runBenchmark(final int numThreads, final BenchmarkTask task) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CyclicBarrier barrier = new CyclicBarrier(numThreads);
        final AtomicInteger totalOps = new AtomicInteger(0);
        final AtomicInteger totalErrors = new AtomicInteger(0);
        final List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                task.run(threadId, barrier, totalOps, totalErrors);
                return null;
            }));
        }

        for (final Future<?> f : futures) {
            f.get(DURATION_SECONDS + 10, TimeUnit.SECONDS);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        if (totalErrors.get() > 0) {
            System.out.printf("    (errors: %d)%n", totalErrors.get());
        }

        return totalOps.get() / (double) DURATION_SECONDS;
    }

    private void resetDocuments() throws XMLDBException {
        final Collection col = server.getRoot().getChildCollection(COLLECTION_NAME);
        for (int i = 1; i <= NUM_DOCUMENTS; i++) {
            final XMLResource res = col.createResource("doc" + i + ".xml", XMLResource.class);
            res.setContent("<root><counter>0</counter><data>" + "x".repeat(100) + "</data></root>");
            col.storeResource(res);
        }
    }

    private static void printHeader() {
        System.out.printf("  %-24s  %8s  %12s%n", "Scenario", "Threads", "Ops/sec");
        System.out.println("  " + "=".repeat(50));
    }
}
