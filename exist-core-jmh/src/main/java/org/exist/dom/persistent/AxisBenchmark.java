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
package org.exist.dom.persistent;

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
 * Axis-evaluator benchmark covering the four sibling and non-sibling axis
 * directions, intended as a regression-detection signal for any future change
 * to {@link NewArrayNodeSet}'s axis-selection methods.
 *
 * <p>Companion to {@code AxisPerformanceRegressionTest} (PR #6302). The JUnit
 * test catches a pathological re-regression with a coarse 5x threshold; this
 * benchmark provides ns/op resolution suitable for measuring smaller changes
 * and for comparing alternative implementations.
 *
 * <p>Three corpus shapes (sized so every benchmark finishes within JMH's
 * default per-iteration timeout on a recent laptop):
 * <ul>
 *   <li>{@code 1500_20} - 1,500 parents x 20 b-children (matches the corpus
 *       in {@code AxisPerformanceRegressionTest}; the GH-2697 reproducer)</li>
 *   <li>{@code 500_100} - 500 parents x 100 b-children (balanced)</li>
 *   <li>{@code 100_500} - 100 parents x 500 b-children (high fan-out per
 *       parent, the case most sensitive to per-context sibling-walk cost)</li>
 * </ul>
 *
 * <p>Run via the unshaded jar plus the runtime classpath; the shaded
 * benchmark jar trips a log4j2 caller-class assertion when booting a
 * {@code BrokerPool} (the existing pure-Java benchmarks do not exercise this
 * path). PR #6296 proposes the proper module-level fix; until that lands:
 * <pre>{@code
 *   mvn -pl exist-core-jmh dependency:build-classpath \
 *       -Dmdep.outputFile=$PWD/exist-core-jmh/target/classpath.txt
 *   CP="$PWD/exist-core-jmh/target/classes:\
 *   $PWD/exist-core-jmh/target/exist-core-jmh-*-SNAPSHOT.jar:\
 *   $(cat $PWD/exist-core-jmh/target/classpath.txt)"
 *   java -cp "$CP" org.openjdk.jmh.Main AxisBenchmark
 * }</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AxisBenchmark {

    @Param({"1500_20", "500_100", "100_500"})
    public String shape;

    private LifecycleEmbeddedServer server;
    private String docPath;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();

        final String[] dims = shape.split("_");
        final int parents = Integer.parseInt(dims[0]);
        final int children = Integer.parseInt(dims[1]);
        final String docName = "jmh-axis-" + shape + ".xml";
        docPath = "/db/" + docName;

        // Same constructor pattern as AxisPerformanceRegressionTest: each <a> wraps
        // {children} <b> elements, giving {parents x children} b-elements total. The
        // sibling-axis predicate count is parents * (children - 1) on each side.
        final String store = """
                let $doc :=
                    <test>{(1 to %d) ! <a>{(1 to %d) ! <b>{.}</b>}</a>}</test>
                return xmldb:store("/db", "%s", $doc)
                """.formatted(parents, children, docName);
        server.executeQuery(store);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.after();
            server = null;
        }
    }

    /*
     * Sibling-axis benchmarks use the {@code //b[axis::b]} predicate form: every
     * b-element is a context node, and the predicate walks its sibling axis. This
     * is the same shape as AxisPerformanceRegressionTest and is the one that
     * exposes per-context bugs like the PR #6302 regression (without the fix,
     * each context's following-sibling walk crossed the parent boundary; with
     * the fix, each terminates after at most {@code children-1} siblings).
     */
    @Benchmark
    public void followingSibling(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(doc(\"" + docPath + "\")//b[following-sibling::b])"));
    }

    @Benchmark
    public void precedingSibling(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count(doc(\"" + docPath + "\")//b[preceding-sibling::b])"));
    }

    /*
     * Non-sibling axes are measured from a single context node ({@code (//b)[1]}
     * for following::, the last b for preceding::) because evaluating
     * unbounded following::/preceding:: from every b would be O(N^2) in the
     * total b count and dominate the benchmark. A single-context measurement
     * still detects regressions in the axis-walk implementation (issue #2129's
     * wildcard-vs-typed dispatch concern) without the N^2 blowup.
     */
    @Benchmark
    public void following(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "count((doc(\"" + docPath + "\")//b)[1]/following::b)"));
    }

    @Benchmark
    public void preceding(final Blackhole bh) throws XMLDBException {
        bh.consume(server.executeQuery(
                "let $bs := doc(\"" + docPath + "\")//b return count($bs[last()]/preceding::b)"));
    }

    /**
     * Widens {@link ExistXmldbEmbeddedServer#before()} / {@code after()} from
     * protected (JUnit's {@code ExternalResource} contract) to public so JMH's
     * {@code @Setup} / {@code @TearDown} can drive the lifecycle directly.
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
