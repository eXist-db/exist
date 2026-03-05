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
import org.junit.*;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assume.assumeTrue;

/**
 * Performance benchmark for fn:not() predicate evaluation.
 *
 * <p>Verifies that the set-difference optimization for fn:not() on persistent
 * node sets is preserved after the fixes for issues #2159 and #2308, and that
 * the new boolean-per-item fallback path for atomic sequences performs
 * acceptably.</p>
 *
 * <p>Skipped by default. Run with:</p>
 * <pre>
 * mvn test -pl exist-core -Dtest=FunNotBenchmark \
 *     -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
public class FunNotBenchmark {

    private static final String COLLECTION_NAME = "bench-fn-not";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int MEASURED_ITERATIONS = 500;

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void setUp() throws XMLDBException {
        assumeTrue("Benchmark skipped (pass -Dexist.run.benchmarks=true to enable)",
                Boolean.getBoolean("exist.run.benchmarks"));

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        final var col = cms.createCollection(COLLECTION_NAME);

        // Generate test document: 200 items with varying children and attributes
        final StringBuilder sb = new StringBuilder();
        sb.append("<root>\n");
        for (int i = 1; i <= 200; i++) {
            sb.append("  <item id=\"").append(i).append("\"");
            if (i % 3 == 0) {
                sb.append(" attr=\"val").append(i).append("\"");
            }
            sb.append(">\n");
            if (i % 2 == 0) {
                sb.append("    <child>child-").append(i).append("</child>\n");
            }
            if (i % 5 == 0) {
                sb.append("    <x>descendant-").append(i).append("</x>\n");
            }
            sb.append("  </item>\n");
        }
        sb.append("</root>");

        final XMLResource res = col.createResource("data.xml", XMLResource.class);
        res.setContent(sb.toString());
        col.storeResource(res);
        col.close();
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        try {
            final CollectionManagementService cms =
                    server.getRoot().getService(CollectionManagementService.class);
            cms.removeCollection(COLLECTION_NAME);
        } catch (final XMLDBException e) {
            // ignore cleanup failures
        }
    }

    // --- Benchmark queries ---

    private static final String DOC = "doc('/db/" + COLLECTION_NAME + "/data.xml')";

    /** Set-difference optimization: child axis */
    @Test
    public void notChild() throws XMLDBException {
        runBenchmark("not(child)",
                DOC + "//item[not(child)]");
    }

    /** Set-difference optimization: attribute axis */
    @Test
    public void notAttribute() throws XMLDBException {
        runBenchmark("not(@attr)",
                DOC + "//item[not(@attr)]");
    }

    /** Set-difference optimization: descendant axis */
    @Test
    public void notDescendant() throws XMLDBException {
        runBenchmark("not(descendant::x)",
                DOC + "//item[not(descendant::x)]");
    }

    /** Boolean fallback: general comparison inside not() */
    @Test
    public void notComparison() throws XMLDBException {
        runBenchmark("not(@id > 100)",
                DOC + "//item[not(@id > 100)]");
    }

    /** Boolean fallback: not(.) on in-memory nodes */
    @Test
    public void notDotOnNodes() throws XMLDBException {
        runBenchmark("not(.) on nodes",
                DOC + "//item[not(.)]");
    }

    // --- Benchmark harness ---

    private void runBenchmark(final String label, final String xquery) throws XMLDBException {
        final XQueryService queryService = server.getRoot().getService(XQueryService.class);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            queryService.query(xquery);
        }

        // Measured
        final long[] timings = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            final long start = System.nanoTime();
            queryService.query(xquery);
            timings[i] = System.nanoTime() - start;
        }

        // Stats
        long total = 0;
        long min = Long.MAX_VALUE;
        for (final long t : timings) {
            total += t;
            if (t < min) {
                min = t;
            }
        }
        final double avgMs = (total / (double) MEASURED_ITERATIONS) / 1_000_000.0;
        final double minMs = min / 1_000_000.0;
        final double opsPerSec = MEASURED_ITERATIONS / (total / 1_000_000_000.0);

        System.out.printf("| %-25s | %8.3f ms | %8.3f ms | %10.1f ops/s |%n",
                label, avgMs, minMs, opsPerSec);
    }
}
