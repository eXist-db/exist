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
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

/**
 * Performance benchmark for GeneralComparison predicate evaluation.
 *
 * <p>This benchmark is guarded by the system property {@code exist.run.benchmarks}
 * and will be skipped during normal test runs. Class name ends in "Benchmark"
 * (not "Test") so Surefire will not discover it automatically.</p>
 *
 * <p>Run with:</p>
 * <pre>
 * mvn test -pl exist-core -Dtest=GeneralComparisonBenchmark \
 *     -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
public class GeneralComparisonBenchmark {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION_NAME = "benchmark-general-comparison";
    private static final String COLLECTION_PATH = "/db/" + COLLECTION_NAME;

    private static final int[] DATA_SIZES = {100, 500, 2000};
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;

    @BeforeClass
    public static void assumeBenchmarks() {
        Assume.assumeTrue("Benchmarks are disabled. Set -Dexist.run.benchmarks=true to enable.",
                Boolean.getBoolean("exist.run.benchmarks"));
    }

    @BeforeClass
    public static void setUp() throws XMLDBException {
        if (!Boolean.getBoolean("exist.run.benchmarks")) {
            return;
        }

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        final Collection col = cms.createCollection(COLLECTION_NAME);

        for (final int size : DATA_SIZES) {
            final StringBuilder sb = new StringBuilder();
            sb.append("<data>");
            for (int i = 0; i < size; i++) {
                final String key = "k" + (i % 26);
                final String value = "v" + (i % 10);
                sb.append(String.format("<item k=\"%s\" v=\"%s\"/>", key, value));
            }
            sb.append("</data>");

            final XMLResource res = col.createResource("data-" + size + ".xml", XMLResource.class);
            res.setContent(sb.toString());
            col.storeResource(res);
        }
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        if (!Boolean.getBoolean("exist.run.benchmarks")) {
            return;
        }

        final CollectionManagementService cms =
                server.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(COLLECTION_NAME);
    }

    @Test
    public void predicateAttrEqLiteral() throws XMLDBException {
        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "count(doc('%s/data-%d.xml')//item[@v = 'v0'])",
                    COLLECTION_PATH, size);
            runBenchmark("predicate-attr-eq-literal", size, query);
        }
    }

    @Test
    public void predicateAttrEqVar() throws XMLDBException {
        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $x := 'v0' return count(doc('%s/data-%d.xml')//item[@v = $x])",
                    COLLECTION_PATH, size);
            runBenchmark("predicate-attr-eq-var", size, query);
        }
    }

    @Test
    public void predicateMapGetCtxAttr() throws XMLDBException {
        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $map := map { 'k0': 'v0', 'k1': 'v1', 'k2': 'v2', 'k3': 'v3', 'k4': 'v4' } " +
                    "return count(doc('%s/data-%d.xml')//item[@v = map:get($map, @k)])",
                    COLLECTION_PATH, size);
            runBenchmark("predicate-map-get-ctx-attr", size, query);
        }
    }

    @Test
    public void predicateLocalFnCtxAttr() throws XMLDBException {
        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "declare function local:get($k as xs:string) as xs:string? { " +
                    "  let $map := map { 'k0': 'v0', 'k1': 'v1', 'k2': 'v2', 'k3': 'v3', 'k4': 'v4' } " +
                    "  return $map($k) " +
                    "}; " +
                    "count(doc('%s/data-%d.xml')//item[@v = local:get(@k)])",
                    COLLECTION_PATH, size);
            runBenchmark("predicate-local-fn-ctx-attr", size, query);
        }
    }

    private void runBenchmark(final String label, final int size, final String query) throws XMLDBException {
        final XQueryService queryService = server.getRoot().getService(XQueryService.class);

        // warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            queryService.query(query);
        }

        // measure
        long totalNs = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            final long start = System.nanoTime();
            final ResourceSet result = queryService.query(query);
            final long elapsed = System.nanoTime() - start;
            totalNs += elapsed;

            // verify result is valid
            if (result.getSize() != 1) {
                throw new AssertionError(label + " (size=" + size + "): expected 1 result, got " + result.getSize());
            }
        }

        final double avgMs = (totalNs / (double) MEASURE_ITERATIONS) / 1_000_000.0;
        System.out.printf("[%s] size=%d  avg=%.2f ms%n", label, size, avgMs);
    }
}
