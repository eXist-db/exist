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
package org.exist.xquery.update;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.xmldb.api.base.XMLDBException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures the throughput of {@code update value} expressions which increment a counter that they read: from
 * one thread, from 8 threads each with its own document, and from 8 threads sharing one document.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx2g")
public class UpdateValueBenchmark {

    private static final String COLLECTION = "/db/update-value-benchmark";
    private static final int DOCUMENTS = 8;

    private LifecycleEmbeddedServer server;
    private final AtomicInteger nextDocument = new AtomicInteger();

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();
        server.executeQuery("""
                let $collection := xmldb:create-collection("/db", "update-value-benchmark")
                for $i in 0 to %d
                return xmldb:store($collection, "counter" || $i || ".xml", <root n="0"/>)
                """.formatted(DOCUMENTS - 1));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.after();
            server = null;
        }
    }

    /**
     * Each benchmark thread's own query service and document.
     */
    @State(Scope.Thread)
    public static class ThreadState {
        private EXistXQueryService service;
        private String ownDocument;

        @Setup(Level.Trial)
        public void setUp(final UpdateValueBenchmark benchmark) throws XMLDBException {
            service = benchmark.server.getRoot().getService(EXistXQueryService.class);
            ownDocument = COLLECTION + "/counter" + (benchmark.nextDocument.getAndIncrement() % DOCUMENTS) + ".xml";
        }
    }

    @Benchmark
    @Threads(1)
    public Object oneThread(final ThreadState thread) throws XMLDBException {
        return increment(thread, thread.ownDocument);
    }

    @Benchmark
    @Threads(DOCUMENTS)
    public Object threadsOnTheirOwnDocuments(final ThreadState thread) throws XMLDBException {
        return increment(thread, thread.ownDocument);
    }

    @Benchmark
    @Threads(DOCUMENTS)
    public Object threadsOnOneDocument(final ThreadState thread) throws XMLDBException {
        return increment(thread, COLLECTION + "/counter0.xml");
    }

    private static Object increment(final ThreadState thread, final String document) throws XMLDBException {
        return thread.service.query("update value doc('" + document + "')/root/@n with xs:integer(doc('" + document + "')/root/@n) + 1");
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
