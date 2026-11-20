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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures what document and collection locks cost when several threads take locks at the same time, as
 * concurrent queries and updates do: looking a lock up, and acquiring and releasing READ_LOCKs and WRITE_LOCKs.
 *
 * <p>There are {@value #DOCUMENTS} documents, {@value #DOCUMENTS_PER_COLLECTION} in each of
 * {@value #COLLECTIONS} collections {@code /db/corpus/c<n>}. For READ_LOCKs every thread passes over all of
 * them, so the locks are shared but not contended for: READ_LOCKs don't exclude each other. For WRITE_LOCKs
 * each thread passes over its own {@value #SLICES}th of them, so a document WRITE_LOCK is never contended for
 * either. A collection WRITE_LOCK is: by default ({@code paths-multi-writer} is false) it also takes a
 * WRITE_LOCK on every ancestor, {@code /db} included, so collection writers run one at a time.
 * Results are per lock and per thread; run with {@code -t 1} and {@code -t 8} and compare. The locks are kept
 * strongly reachable, so none is created again between passes.
 *
 * <p>{@link #nanoTime()} is the cost of one {@link System#nanoTime()} call under the same number of threads,
 * which the lock table reads for each lock event. The lock table can be disabled, as the default
 * {@code conf.xml} recommends in production.
 *
 * <pre>
 * java -jar exist-core-jmh/target/exist-core-jmh-*-benchmarks.jar LockManagerBenchmark -t 1
 * java -jar exist-core-jmh/target/exist-core-jmh-*-benchmarks.jar LockManagerBenchmark -t 8
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx2g"})
public class LockManagerBenchmark {

    private static final int COLLECTIONS = 10_000;
    private static final int DOCUMENTS_PER_COLLECTION = 20;
    private static final int DOCUMENTS = COLLECTIONS * DOCUMENTS_PER_COLLECTION;

    /**
     * The number of slices the documents and collections are divided into for WRITE_LOCKs, one per thread;
     * with more threads than slices, threads share slices.
     */
    private static final int SLICES = 8;
    private static final int DOCUMENTS_PER_SLICE = DOCUMENTS / SLICES;
    private static final int COLLECTIONS_PER_SLICE = COLLECTIONS / SLICES;

    @State(Scope.Benchmark)
    public static class Locks {

        @Param({"enabled", "disabled"})
        public String lockTable;

        LockManager lockManager;
        XmldbURI[] documentUris;
        String[] documentPaths;
        XmldbURI[] collectionUris;

        @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
        private List<MultiLock> keep;

        @Setup(Level.Trial)
        public void setUp() {
            // read by the LockTable's constructor
            System.setProperty(LockTable.PROP_DISABLE, Boolean.toString("disabled".equals(lockTable)));
            // the concurrency level of a default BrokerPool: max(maxBrokers = 20, 2 × processors)
            lockManager = new LockManager(Math.max(20, 2 * Runtime.getRuntime().availableProcessors()));

            documentUris = new XmldbURI[DOCUMENTS];
            documentPaths = new String[DOCUMENTS];
            collectionUris = new XmldbURI[COLLECTIONS];
            keep = new ArrayList<>(DOCUMENTS + COLLECTIONS + 2);
            keep.add(lockManager.getPathLock("/db"));
            keep.add(lockManager.getPathLock("/db/corpus"));
            for (int i = 0; i < COLLECTIONS; i++) {
                collectionUris[i] = XmldbURI.create("/db/corpus/c" + i);
                keep.add(lockManager.getPathLock(collectionUris[i].toString()));
            }
            for (int i = 0; i < DOCUMENTS; i++) {
                documentUris[i] = XmldbURI.create("/db/corpus/c" + (i / DOCUMENTS_PER_COLLECTION) + "/doc" + (i % DOCUMENTS_PER_COLLECTION) + ".xml");
                documentPaths[i] = documentUris[i].toString();
                keep.add(lockManager.getDocumentLock(documentPaths[i]));
            }
        }
    }

    @State(Scope.Thread)
    public static class Slice {
        private static final AtomicInteger THREADS = new AtomicInteger();

        int index;

        @Setup(Level.Trial)
        public void setUp() {
            index = THREADS.getAndIncrement() % SLICES;
        }
    }

    /**
     * Looks up a document's lock, as {@link LockManager#acquireDocumentReadLock(XmldbURI)} does before locking it.
     */
    @Benchmark
    @OperationsPerInvocation(DOCUMENTS)
    public void documentLockLookup(final Locks locks, final Blackhole blackhole) {
        for (final String documentPath : locks.documentPaths) {
            blackhole.consume(locks.lockManager.getDocumentLock(documentPath));
        }
    }

    /**
     * Acquires and releases a document's READ_LOCK.
     */
    @Benchmark
    @OperationsPerInvocation(DOCUMENTS)
    public void documentReadLock(final Locks locks) throws LockException {
        for (final XmldbURI documentUri : locks.documentUris) {
            try (final ManagedDocumentLock documentLock = locks.lockManager.acquireDocumentReadLock(documentUri)) {
                // held
            }
        }
    }

    /**
     * Accesses a document the way {@code NativeBroker.getXMLResource} does: acquires its collection's READ_LOCK
     * (and with it an INTENTION_READ lock on each ancestor), then the document's READ_LOCK, releases the
     * collection's lock, and then the document's.
     */
    @Benchmark
    @OperationsPerInvocation(DOCUMENTS)
    public void documentAccess(final Locks locks) throws LockException {
        for (int i = 0; i < DOCUMENTS; i++) {
            final ManagedDocumentLock documentLock;
            try (final ManagedCollectionLock collectionLock = locks.lockManager.acquireCollectionReadLock(locks.collectionUris[i / DOCUMENTS_PER_COLLECTION])) {
                documentLock = locks.lockManager.acquireDocumentReadLock(locks.documentUris[i]);
            }
            documentLock.close();
        }
    }

    /**
     * Acquires and releases a collection's READ_LOCK, and with it an INTENTION_READ lock on each ancestor.
     */
    @Benchmark
    @OperationsPerInvocation(COLLECTIONS)
    public void collectionReadLock(final Locks locks) throws LockException {
        for (final XmldbURI collectionUri : locks.collectionUris) {
            try (final ManagedCollectionLock collectionLock = locks.lockManager.acquireCollectionReadLock(collectionUri)) {
                // held
            }
        }
    }

    /**
     * Acquires and releases a WRITE_LOCK on a document of this thread's slice.
     */
    @Benchmark
    @OperationsPerInvocation(DOCUMENTS_PER_SLICE)
    public void documentWriteLock(final Locks locks, final Slice slice) throws LockException {
        final int first = slice.index * DOCUMENTS_PER_SLICE;
        for (int i = first; i < first + DOCUMENTS_PER_SLICE; i++) {
            try (final ManagedDocumentLock documentLock = locks.lockManager.acquireDocumentWriteLock(locks.documentUris[i])) {
                // held
            }
        }
    }

    /**
     * Acquires and releases a WRITE_LOCK on a collection of this thread's slice, and with it, by default, a
     * WRITE_LOCK on each ancestor: collection writers run one at a time.
     */
    @Benchmark
    @OperationsPerInvocation(COLLECTIONS_PER_SLICE)
    public void collectionWriteLock(final Locks locks, final Slice slice) throws LockException {
        final int first = slice.index * COLLECTIONS_PER_SLICE;
        for (int i = first; i < first + COLLECTIONS_PER_SLICE; i++) {
            try (final ManagedCollectionLock collectionLock = locks.lockManager.acquireCollectionWriteLock(locks.collectionUris[i])) {
                // held
            }
        }
    }

    @Benchmark
    public long nanoTime() {
        return System.nanoTime();
    }
}
