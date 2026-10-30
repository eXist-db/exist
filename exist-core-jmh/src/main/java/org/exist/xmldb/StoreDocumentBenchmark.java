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
package org.exist.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
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
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures the throughput of storing a small XML document with the local XML:DB API, each in a transaction of its
 * own, from one thread, and from 8 threads each storing its own document.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = "-Xmx2g")
public class StoreDocumentBenchmark {

    private static final String COLLECTION = "store-document-benchmark";
    private static final int THREADS = 8;

    private LifecycleEmbeddedServer server;
    private Collection collection;
    private final AtomicInteger nextDocument = new AtomicInteger();

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();
        collection = server.getRoot().getService(CollectionManagementService.class).createCollection(COLLECTION);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.after();
            server = null;
        }
    }

    /**
     * Each benchmark thread's own document.
     */
    @State(Scope.Thread)
    public static class ThreadState {
        private String document;
        private long version;

        @Setup(Level.Trial)
        public void setUp(final StoreDocumentBenchmark benchmark) {
            document = "doc" + benchmark.nextDocument.getAndIncrement() + ".xml";
        }
    }

    @Benchmark
    @Threads(1)
    public void oneThread(final ThreadState thread) throws XMLDBException {
        store(thread);
    }

    @Benchmark
    @Threads(THREADS)
    public void threadsOnTheirOwnDocuments(final ThreadState thread) throws XMLDBException {
        store(thread);
    }

    private void store(final ThreadState thread) throws XMLDBException {
        final XMLResource resource = collection.createResource(thread.document, XMLResource.class);
        resource.setContent("<doc version=\"" + thread.version++ + "\"><title>A small document</title></doc>");
        collection.storeResource(resource);
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
