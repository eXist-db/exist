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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
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
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Measures what scope-based binary value lifetimes cost the query engine.
 *
 * <p>A {@code BinaryValue} is owned by the scope that created it, so every scope - every FLWOR, every
 * function call - opens and closes a frame in {@link XQueryContext}'s binary value registry. That is
 * the hot path of query evaluation, and almost every query never creates a binary value at all, which
 * this benchmark exists to defend:</p>
 *
 * <ul>
 *   <li>{@code noBinary*} - queries with no binary value anywhere. The registry is never allocated for
 *     these, so pushing and popping a frame is a counter update; they must stay at parity with a build
 *     that does no frame bookkeeping at all. Run with {@code -prof gc}: the allocation rate must not
 *     move either.</li>
 *   <li>{@code binaryPassedToFunction} - the issue #6725 shape, a value passed through nested calls.</li>
 *   <li>{@code binaryInElementConstructors} - several values live across repeated element constructors.
 *     Entering a constructor used to walk every registered binary value to take a reference on it, so
 *     this cost O(live values) per constructor; a frame makes it O(1).</li>
 * </ul>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BinaryValueLifetimeBenchmark {

    private static final String TEST_COLLECTION_NAME = "binary-value-lifetime-benchmark";
    private static final String BIN_FILENAME = "data.bin";
    private static final byte[] BIN_CONTENT = "0123456789abcdef".repeat(64).getBytes(StandardCharsets.UTF_8);

    /** Iterations of the scope-heavy loops; high enough that per-scope bookkeeping dominates. */
    @Param({"100000"})
    public int iterations;

    /** Binary values live at once in the element-constructor benchmark. */
    @Param({"8"})
    public int liveBinaries;

    private LifecycleEmbeddedServer server;
    private String binPath;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();

        final Collection testCollection = server.createCollection(server.getRoot(), TEST_COLLECTION_NAME);
        try (final EXistResource resource = (EXistResource) testCollection.createResource(BIN_FILENAME, BinaryResource.class)) {
            resource.setContent(BIN_CONTENT);
            testCollection.storeResource(resource);
        }
        testCollection.close();

        binPath = "/db/" + TEST_COLLECTION_NAME + "/" + BIN_FILENAME;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws XMLDBException {
        if (server != null) {
            final CollectionManagementService cms = server.getRoot().getService(CollectionManagementService.class);
            cms.removeCollection(TEST_COLLECTION_NAME);
            server.after();
            server = null;
        }
    }

    // --- no binary value anywhere: the path (almost) every query takes ---

    @Benchmark
    public void noBinaryFunctionCalls(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery("""
                declare function local:f($i) { $i + 1 };
                count(for $i in 1 to %d return local:f($i))""".formatted(iterations)));
    }

    @Benchmark
    public void noBinaryNestedFlwor(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(for $i in 1 to %d return (let $j := $i * 2 let $k := $j + 1 return $k))".formatted(iterations)));
    }

    // --- the shape from the issue: one value, passed through nested calls ---

    @Benchmark
    public void binaryPassedToFunction(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery("""
                declare function local:inner($b) { 1 };
                declare function local:outer($b) { local:inner($b) };
                let $b := util:binary-doc('%s')
                return count(for $i in 1 to %d return local:outer($b))""".formatted(binPath, iterations)));
    }

    // --- several live values across repeated element constructors ---

    @Benchmark
    public void binaryInElementConstructors(final Blackhole bh) throws XMLDBException {
        final StringBuilder lets = new StringBuilder();
        for (int i = 0; i < liveBinaries; i++) {
            lets.append("let $b%d := util:binary-doc('%s')%n".formatted(i, binPath));
        }
        bh.consume(server.executeQuery(
                lets + "return count(for $i in 1 to %d return <a>{$b0}</a>)".formatted(iterations / 100)));
    }

    /**
     * Widens {@link ExistXmldbEmbeddedServer#before()} / {@code after()} from protected to public so
     * JMH's {@code @Setup} / {@code @TearDown} can drive the lifecycle directly (same pattern as
     * {@code ArrowOperatorBenchmark}).
     */
    private static final class LifecycleEmbeddedServer extends ExistXmldbEmbeddedServer {
        LifecycleEmbeddedServer() {
            super(false, true, true);
        }

        @Override
        public void before() throws Throwable {
            super.before();
        }

        @Override
        public void after() {
            super.after();
        }
    }
}
