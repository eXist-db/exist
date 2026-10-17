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
import org.openjdk.jmh.infra.Blackhole;
import org.xmldb.api.base.XMLDBException;

import java.util.concurrent.TimeUnit;

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

    @Param({"2500", "10000"})
    public int collections;

    private LifecycleEmbeddedServer server;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
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
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.after();
            server = null;
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
