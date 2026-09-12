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
package org.exist.xquery.xquf;

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
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.util.concurrent.TimeUnit;

/**
 * Measures W3C XQuery Update Facility 3.0 operations against their legacy
 * eXist-db equivalents, and in-memory copy-modify (transform) expressions,
 * at various data sizes.
 *
 * <p>The persistent benchmarks come in pairs: each XQUF operation ({@code insert node},
 * {@code delete node}, {@code replace value of node}, {@code rename node},
 * {@code replace node}) has a {@code legacy*} counterpart using the deprecated
 * eXist update extension over an identical workload, so comparing the pair
 * quantifies XQUF's cost relative to the legacy implementation. Each persistent
 * benchmark body re-stores the benchmark document and then runs the update, so
 * the measured time includes the same store cost on both sides of a pair.</p>
 *
 * <p>The {@code copyModify*} benchmarks measure in-memory copy-modify expressions
 * only — no persistent store is involved in the measured body.</p>
 *
 * <p>Run via the unshaded jar plus the runtime classpath; the shaded benchmark jar
 * trips a log4j2 caller-class assertion when booting a {@code BrokerPool}
 * (see {@code AxisBenchmark}):</p>
 * <pre>{@code
 *   mvn -pl exist-core-jmh dependency:build-classpath \
 *       -Dmdep.outputFile=$PWD/exist-core-jmh/target/classpath.txt
 *   CP="$PWD/exist-core-jmh/target/classes:\
 *   $PWD/exist-core-jmh/target/exist-core-jmh-*-SNAPSHOT.jar:\
 *   $(cat $PWD/exist-core-jmh/target/classpath.txt)"
 *   java -cp "$CP" org.openjdk.jmh.Main XQUFBenchmark
 * }</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class XQUFBenchmark {

    private static final String COLLECTION_NAME = "benchmark-xquf";
    private static final String COLLECTION_PATH = "/db/" + COLLECTION_NAME;
    private static final String DOC_PATH = COLLECTION_PATH + "/bench.xml";

    @Param({"10", "50", "200"})
    public int size;

    private LifecycleEmbeddedServer server;
    private XQueryService queryService;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        server = new LifecycleEmbeddedServer();
        server.before();

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        cms.createCollection(COLLECTION_NAME);
        queryService = server.getRoot().getService(XQueryService.class);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws XMLDBException {
        if (server != null) {
            final CollectionManagementService cms =
                    server.getRoot().getService(CollectionManagementService.class);
            cms.removeCollection(COLLECTION_NAME);
            server.after();
            server = null;
        }
    }

    // ---- Persistent XQUF updates (each body re-stores the document, then updates) ----

    @Benchmark
    public void insertInto(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $item in doc('" + DOC_PATH + "')//item " +
                "return insert node <added/> into $item"));
    }

    @Benchmark
    public void deleteNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return delete node $v"));
    }

    @Benchmark
    public void replaceValue(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $item in doc('" + DOC_PATH + "')//item " +
                "return replace value of node $item/@id with concat('new-', $item/@id)"));
    }

    @Benchmark
    public void renameNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return rename node $v as 'renamed'"));
    }

    @Benchmark
    public void replaceNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return replace node $v with <new-value>{string($v)}</new-value>"));
    }

    // ---- Legacy persistent updates (deprecated eXist update extension) ----

    @Benchmark
    public void legacyInsertInto(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $item in doc('" + DOC_PATH + "')//item " +
                "return update insert <added/> into $item"));
    }

    @Benchmark
    public void legacyDeleteNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return update delete $v"));
    }

    @Benchmark
    public void legacyReplaceValue(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $item in doc('" + DOC_PATH + "')//item " +
                "return update value $item/@id with concat('new-', $item/@id)"));
    }

    @Benchmark
    public void legacyRenameNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return update rename $v as 'renamed'"));
    }

    @Benchmark
    public void legacyReplaceNode(final Blackhole bh) throws XMLDBException {
        storeDocument();
        bh.consume(queryService.query(
                "for $v in doc('" + DOC_PATH + "')//item/value " +
                "return update replace $v with <new-value>{string($v)}</new-value>"));
    }

    // ---- In-memory copy-modify (transform) expressions ----

    @Benchmark
    public void copyModifySingle(final Blackhole bh) throws XMLDBException {
        bh.consume(queryService.query(
                "let $doc := <root>{ for $i in 1 to " + size + " return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                "return copy $c := $doc modify ( replace value of node $c//item[@id = '1']/value with 'modified' ) " +
                "return $c//item[@id = '1']/value/string()"));
    }

    @Benchmark
    public void copyModifyMultiple(final Blackhole bh) throws XMLDBException {
        bh.consume(queryService.query(
                "let $doc := <root>{ for $i in 1 to " + size + " return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                "return copy $c := $doc modify ( " +
                "  for $v in $c//item/value return replace value of node $v with concat('m-', $v) " +
                ") return count($c//item)"));
    }

    @Benchmark
    public void copyModifyInsertDelete(final Blackhole bh) throws XMLDBException {
        bh.consume(queryService.query(
                "let $doc := <root>{ for $i in 1 to " + size + " return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                "return copy $c := $doc modify ( " +
                "  insert node <added/> into $c, " +
                "  for $v in $c//item[@id = ('1','2','3')]/value return delete node $v " +
                ") return count($c//item)"));
    }

    @Benchmark
    public void copyModifyDeepTree(final Blackhole bh) throws XMLDBException {
        // Nested structure: root > section > subsection > item (depth=4)
        bh.consume(queryService.query(
                "let $doc := <root>{ " +
                "  for $s in 1 to " + size + " " +
                "  return <section id='{$s}'>{ " +
                "    for $ss in 1 to 3 " +
                "    return <subsection id='{$s}-{$ss}'><item>data</item></subsection> " +
                "  }</section> " +
                "}</root> " +
                "return copy $c := $doc modify ( " +
                "  for $item in $c//item return replace value of node $item with 'updated' " +
                ") return count($c//item[. = 'updated'])"));
    }

    // ---- Helpers ----

    private void storeDocument() throws XMLDBException {
        final Collection col = server.getRoot().getChildCollection(COLLECTION_NAME);
        final StringBuilder sb = new StringBuilder("<root>\n");
        for (int i = 1; i <= size; i++) {
            sb.append("  <item id='").append(i).append("'><value>val-").append(i).append("</value></item>\n");
        }
        sb.append("</root>");

        final XMLResource res = col.createResource("bench.xml", XMLResource.class);
        res.setContent(sb.toString());
        col.storeResource(res);
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
