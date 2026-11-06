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
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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

/**
 * Measures what one thread that takes collection WRITE_LOCKs, as storing or removing a document does, costs
 * {@value #READERS} threads that read documents at the same time, with {@code paths-multi-writer} false (the
 * default) and true.
 *
 * <p>The readers access documents in {@code /db/corpus/c<n>} the way {@code NativeBroker.getXMLResource} does:
 * the collection's READ_LOCK, then the document's READ_LOCK, then the collection's lock is released. The writer
 * takes a WRITE_LOCK on a collection, holds it while it does {@code writerWork} tokens of work
 * ({@link Blackhole#consumeCPU(long)}), as a store holds it while it parses and indexes, and releases it:
 * <ul>
 *     <li>{@code writer=none}: the writer only does the work, without a lock;</li>
 *     <li>{@code writer=other}: in another collection, {@code /db/other/w<n>};</li>
 *     <li>{@code writer=corpus}: on {@code /db/corpus}, the parent of the readers' collections, as adding or
 *     removing a sub-collection there does.</li>
 * </ul>
 * With {@code paths-multi-writer} false a collection WRITE_LOCK also takes a WRITE_LOCK on every ancestor,
 * {@code /db} included; with true, an INTENTION_WRITE lock.
 *
 * <pre>
 * java -jar exist-core-jmh/target/exist-core-jmh-*-benchmarks.jar CollectionWriterBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx2g"})
public class CollectionWriterBenchmark {

    private static final int READERS = 7;
    private static final int COLLECTIONS = 10_000;
    private static final int DOCUMENTS_PER_COLLECTION = 20;
    private static final int DOCUMENTS = COLLECTIONS * DOCUMENTS_PER_COLLECTION;
    private static final int OTHER_COLLECTIONS = 1_000;

    @State(Scope.Benchmark)
    public static class Db {

        @Param({"false", "true"})
        public boolean pathsMultiWriter;

        @Param({"none", "other", "corpus"})
        public String writer;

        @Param({"0", "20000"})
        public long writerWork;

        LockManager lockManager;
        XmldbURI[] documentUris;
        XmldbURI[] collectionUris;
        XmldbURI[] otherCollectionUris;
        XmldbURI corpusUri;

        @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
        private List<MultiLock> keep;

        @Setup(Level.Trial)
        public void setUp() {
            // read by the LockManager's and the LockTable's constructors
            System.setProperty(LockManager.PROP_ENABLE_PATHS_MULTI_WRITER, Boolean.toString(pathsMultiWriter));
            System.setProperty(LockTable.PROP_DISABLE, "true");
            lockManager = new LockManager(Math.max(20, 2 * Runtime.getRuntime().availableProcessors()));

            keep = new ArrayList<>();
            keep.add(lockManager.getPathLock("/db"));
            keep.add(lockManager.getPathLock("/db/corpus"));
            keep.add(lockManager.getPathLock("/db/other"));
            corpusUri = XmldbURI.create("/db/corpus");
            collectionUris = new XmldbURI[COLLECTIONS];
            for (int i = 0; i < COLLECTIONS; i++) {
                collectionUris[i] = XmldbURI.create("/db/corpus/c" + i);
                keep.add(lockManager.getPathLock(collectionUris[i].toString()));
            }
            documentUris = new XmldbURI[DOCUMENTS];
            for (int i = 0; i < DOCUMENTS; i++) {
                documentUris[i] = XmldbURI.create("/db/corpus/c" + (i / DOCUMENTS_PER_COLLECTION) + "/doc" + (i % DOCUMENTS_PER_COLLECTION) + ".xml");
                keep.add(lockManager.getDocumentLock(documentUris[i].toString()));
            }
            otherCollectionUris = new XmldbURI[OTHER_COLLECTIONS];
            for (int i = 0; i < OTHER_COLLECTIONS; i++) {
                otherCollectionUris[i] = XmldbURI.create("/db/other/w" + i);
                keep.add(lockManager.getPathLock(otherCollectionUris[i].toString()));
            }
        }
    }

    @State(Scope.Thread)
    public static class Cursor {
        int next;

        int next(final int bound) {
            if (++next >= bound) {
                next = 0;
            }
            return next;
        }
    }

    /**
     * Accesses one document, the way {@code NativeBroker.getXMLResource} does.
     */
    @Benchmark
    @Group("readersAndWriter")
    @GroupThreads(READERS)
    public void reader(final Db db, final Cursor cursor) throws LockException {
        final int i = cursor.next(DOCUMENTS);
        final ManagedDocumentLock documentLock;
        try (final ManagedCollectionLock collectionLock = db.lockManager.acquireCollectionReadLock(db.collectionUris[i / DOCUMENTS_PER_COLLECTION])) {
            documentLock = db.lockManager.acquireDocumentReadLock(db.documentUris[i]);
        }
        documentLock.close();
    }

    /**
     * Takes one collection WRITE_LOCK and does {@code writerWork} while holding it, or only does the work.
     */
    @Benchmark
    @Group("readersAndWriter")
    @GroupThreads(1)
    public void writer(final Db db, final Cursor cursor) throws LockException {
        final XmldbURI collectionUri = switch (db.writer) {
            case "other" -> db.otherCollectionUris[cursor.next(OTHER_COLLECTIONS)];
            case "corpus" -> db.corpusUri;
            default -> null;
        };
        if (collectionUri == null) {
            Blackhole.consumeCPU(db.writerWork);
            return;
        }
        try (final ManagedCollectionLock collectionLock = db.lockManager.acquireCollectionWriteLock(collectionUri)) {
            Blackhole.consumeCPU(db.writerWork);
        }
    }
}
