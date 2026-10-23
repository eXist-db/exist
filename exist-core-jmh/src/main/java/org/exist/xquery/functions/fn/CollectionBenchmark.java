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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.LockTable;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.EXistXQueryService;
import org.openjdk.jmh.infra.Blackhole;
import org.xmldb.api.base.XMLDBException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Measures {@code fn:collection()} over a collection tree of many small
 * documents, the cost every query against a large collection pays before any
 * index is used.
 *
 * <p>The tree has {@code collections} sub-collections of {@value #DOCUMENTS_PER_COLLECTION}
 * documents each. The collection cache is fixed at eXist's default of 64 MB: with 2,500
 * sub-collections (50,000 documents) every {@code Collection} stays cached, and with
 * 10,000 (200,000 documents) they no longer fit, so collections are reloaded as
 * {@code fn:collection()} walks the tree.
 *
 * <p>While it runs, a light writer may store a document every {@value #WRITER_PAUSE_MS} ms,
 * either in another collection ({@code writer=other}) or in one of the sub-collections that
 * {@code fn:collection()} reads ({@code writer=inside}); or a WRITE_LOCK on a document in another
 * collection may be held throughout ({@code writer=held}), as by a long update. The lock table, which records every lock
 * for diagnostics, may be disabled, as the default {@code conf.xml} recommends in production.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx3g", "-Dorg.exist.db-connection.collectionCache=64M"})
public class CollectionBenchmark {

    private static final int DOCUMENTS_PER_COLLECTION = 20;
    private static final int COLLECTIONS_PER_STORE_QUERY = 500;
    private static final String ROOT = "/db/collection-benchmark";
    private static final int WRITER_PAUSE_MS = 10;

    @Param({"2500", "10000"})
    public int collections;

    @Param({"none"})
    public String writer;

    @Param({"enabled"})
    public String lockTable;

    private LifecycleEmbeddedServer server;
    private final AtomicBoolean writing = new AtomicBoolean();
    private Thread writerThread;
    /** The failure that ended the writer thread, rethrown at the end of the trial so that it isn't measured without a writer. */
    private final AtomicReference<Throwable> writerFailure = new AtomicReference<>();

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        System.setProperty(LockTable.PROP_DISABLE, Boolean.toString("disabled".equals(lockTable)));
        server = new LifecycleEmbeddedServer();
        server.before();

        for (int from = 1; from <= collections; from += COLLECTIONS_PER_STORE_QUERY) {
            final int to = Math.min(collections, from + COLLECTIONS_PER_STORE_QUERY - 1);
            server.executeQuery("""
                    let $root := xmldb:create-collection("/db", "collection-benchmark")
                    for $c in %d to %d
                    let $collection := xmldb:create-collection("%s", "c" || $c)
                    for $i in 1 to %d
                    return xmldb:store($collection, "d" || $i || ".xml",
                        <doc n="{$i}"><title>Document {$i} of c{$c}</title><p>Some text for document {$i}.</p></doc>)
                    """.formatted(from, to, ROOT, DOCUMENTS_PER_COLLECTION));
        }

        final String writerCollection = switch (writer) {
            case "none" -> null;
            case "other" -> "/db/collection-benchmark-writes";
            case "inside" -> ROOT + "/c1";
            case "held" -> {
                holdDocumentWriteLock(XmldbURI.create("/db/collection-benchmark-writes/held.xml"));
                yield null;
            }
            default -> throw new IllegalArgumentException("Unknown writer: " + writer);
        };
        if (writerCollection != null) {
            startWriter(writerCollection);
        }
    }

    /**
     * Holds a WRITE_LOCK on the document until the trial ends.
     */
    private void holdDocumentWriteLock(final XmldbURI document) throws InterruptedException {
        final CountDownLatch locked = new CountDownLatch(1);
        writing.set(true);
        writerThread = new Thread(() -> {
            try (final ManagedDocumentLock ignored = BrokerPool.getInstance().getLockManager().acquireDocumentWriteLock(document)) {
                locked.countDown();
                while (writing.get()) {
                    Thread.sleep(WRITER_PAUSE_MS);
                }
            } catch (final EXistException | LockException e) {
                writerFailure.set(e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                locked.countDown();
            }
        }, "collection-benchmark-lock-holder");
        writerThread.start();
        locked.await();
        throwWriterFailure();
    }

    /**
     * Stores a document in the collection every {@value #WRITER_PAUSE_MS} ms, until the trial ends.
     */
    private void startWriter(final String collection) throws XMLDBException {
        final String parent = collection.substring(0, collection.lastIndexOf('/'));
        final String name = collection.substring(collection.lastIndexOf('/') + 1);
        final EXistXQueryService service = server.getRoot().getService(EXistXQueryService.class);
        service.query("xmldb:create-collection(\"" + parent + "\", \"" + name + "\")");
        writing.set(true);
        writerThread = new Thread(() -> {
            long i = 0;
            while (writing.get()) {
                try {
                    service.query("xmldb:store(\"" + collection + "\", \"written.xml\", <written n=\"" + i++ + "\"/>)");
                    Thread.sleep(WRITER_PAUSE_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final XMLDBException e) {
                    writerFailure.set(e);
                    return;
                }
            }
        }, "collection-benchmark-writer");
        writerThread.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        writing.set(false);
        if (writerThread != null) {
            writerThread.join();
            writerThread = null;
        }
        if (server != null) {
            server.after();
            server = null;
        }
        throwWriterFailure();
    }

    private void throwWriterFailure() {
        final Throwable failure = writerFailure.getAndSet(null);
        if (failure != null) {
            throw new IllegalStateException("The writer failed, so the trial ran without it", failure);
        }
    }

    @Benchmark
    public void countCollection(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery("count(collection(\"" + ROOT + "\"))"));
    }

    private static final class LifecycleEmbeddedServer extends ExistXmldbEmbeddedServer {
        LifecycleEmbeddedServer() {
            super(false, true, true);
        }

        @Override
        public void before() throws ReflectiveOperationException, XMLDBException {
            super.before();
        }

        @Override
        public void after() {
            super.after();
        }
    }
}
