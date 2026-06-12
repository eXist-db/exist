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
 * Measures the overhead of the XQuery 3.1 arrow operator against the equivalent direct call.
 *
 * <p>{@code EXPR => f(args)} is, per spec, exactly the call {@code f(EXPR, args)}. Each
 * {@code arrow*} benchmark below has a matching {@code direct*} benchmark over an identical workload;
 * comparing the pair quantifies what the arrow costs over a plain call. The loop applies a named
 * function {@link #ITERATIONS} times so per-application dispatch overhead dominates the measurement.</p>
 *
 * <p>This is the companion guard for the change that compiles a statically-named arrow to a direct
 * {@code FunctionCall} (instead of routing through a dynamic {@code FunctionReference} that allocated
 * and re-analyzed a function reference per evaluation): {@code arrow*} should be at parity with
 * {@code direct*}, and must not regress.</p>
 *
 * <p>Run via the unshaded jar plus the runtime classpath; the shaded benchmark jar trips a log4j2
 * caller-class assertion when booting a {@code BrokerPool} (see {@code AxisBenchmark}):</p>
 * <pre>{@code
 *   mvn -pl exist-core-jmh dependency:build-classpath \
 *       -Dmdep.outputFile=$PWD/exist-core-jmh/target/classpath.txt
 *   CP="$PWD/exist-core-jmh/target/classes:\
 *   $PWD/exist-core-jmh/target/exist-core-jmh-*-SNAPSHOT.jar:\
 *   $(cat $PWD/exist-core-jmh/target/classpath.txt)"
 *   java -cp "$CP" org.openjdk.jmh.Main ArrowOperatorBenchmark
 * }</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ArrowOperatorBenchmark {

    @Param({"100000"})
    public int iterations;

    private LifecycleEmbeddedServer server;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.after();
            server = null;
        }
    }

    // --- single named call: concat($i, "-x") ---

    @Benchmark
    public void directSingleCall(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(for $i in 1 to " + iterations + " return concat($i, \"-x\"))"));
    }

    @Benchmark
    public void arrowSingleCall(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(for $i in 1 to " + iterations + " return $i => concat(\"-x\"))"));
    }

    // --- chained calls: upper-case + string-length, the idiom arrows are written for ---

    @Benchmark
    public void directChain(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(for $i in 1 to " + iterations + " return string-length(upper-case(concat($i, \"-x\"))))"));
    }

    @Benchmark
    public void arrowChain(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(for $i in 1 to " + iterations + " return $i => concat(\"-x\") => upper-case() => string-length())"));
    }

    /**
     * Widens {@link ExistXmldbEmbeddedServer#before()} / {@code after()} from protected to public so
     * JMH's {@code @Setup} / {@code @TearDown} can drive the lifecycle directly (same pattern as
     * {@code AxisBenchmark}).
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
