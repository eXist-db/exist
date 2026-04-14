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
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Micro-benchmark for {@code order by} clause performance.
 *
 * <p>Compares wall-clock throughput (ops/s) between:
 * <ol>
 *   <li><b>simple</b>  — {@code for $x in 1 to N order by $x return $x} — the most
 *       common pattern; tests whether the new tuple-capture approach adds overhead
 *       compared to the old deferred-sort approach.</li>
 *   <li><b>withCount</b> — same query but with a {@code count $rank} clause after the
 *       {@code order by} — the newly-correct pattern.</li>
 *   <li><b>twoVars</b>  — nested for loops so two variables are in scope when
 *       {@code order by} fires; tests the cost of capturing more variable bindings.</li>
 *   <li><b>descending</b> — descending sort; used to verify the direction flag adds no
 *       extra overhead after removing {@code hasPreviousOrderByDescending()}.</li>
 * </ol>
 *
 * <p>Not included in the normal test suite — the class name does not match Surefire's
 * default {@code *Test} pattern, so {@code mvn test} will never discover it.
 * Run explicitly with:
 * <pre>
 *   mvn test -pl exist-core -Dtest=OrderByClauseBenchmark \
 *       -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
public class OrderByClauseBenchmark {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    /** Number of warm-up iterations before timing starts. */
    private static final int WARMUP = 200;
    /** Number of timed iterations. */
    private static final int ITERATIONS = 1_000;

    // Query variants keyed by a short label
    private static final Map<String, String[]> QUERIES = new LinkedHashMap<>();
    static {
        for (final int n : new int[]{10, 100, 1_000}) {
            QUERIES.put("simple       N=" + n,
                    new String[]{ "count(for $x in 1 to " + n + " order by $x return $x)" });
            QUERIES.put("withCount    N=" + n,
                    new String[]{ "count(for $x in 1 to " + n + " order by $x count $rank return $rank)" });
            QUERIES.put("twoVars      N=" + n,
                    new String[]{ "count(for $x in 1 to " + n + " for $y in 1 to 3 order by $x * 3 + $y return ($x, $y))" });
            QUERIES.put("descending   N=" + n,
                    new String[]{ "count(for $x in 1 to " + n + " order by $x descending return $x)" });
        }
    }

    @Test
    public void benchmark() throws XMLDBException {
        Assume.assumeTrue(
                "Benchmark skipped by default. Re-run with -Dexist.run.benchmarks=true",
                Boolean.getBoolean("exist.run.benchmarks"));

        System.out.println("\n=== OrderByClause benchmark ===");
        System.out.printf("%-24s  %8s  %8s  %8s%n", "Query variant", "avg (ms)", "min (ms)", "ops/s");
        System.out.println("-".repeat(58));

        for (final Map.Entry<String, String[]> entry : QUERIES.entrySet()) {
            final String label = entry.getKey();
            final String query = entry.getValue()[0];

            // warm up
            for (int i = 0; i < WARMUP; i++) {
                server.executeQuery(query);
            }

            // timed run
            final long[] times = new long[ITERATIONS];
            for (int i = 0; i < ITERATIONS; i++) {
                final long t0 = System.nanoTime();
                final ResourceSet rs = server.executeQuery(query);
                rs.getResource(0).getContent(); // force result materialisation
                times[i] = System.nanoTime() - t0;
            }

            long sum = 0;
            long min = Long.MAX_VALUE;
            for (final long t : times) {
                sum += t;
                if (t < min) min = t;
            }
            final double avgMs  = sum / (double) ITERATIONS / 1_000_000.0;
            final double minMs  = min / 1_000_000.0;
            final double opsPerSec = 1_000.0 / avgMs;

            System.out.printf("%-24s  %8.3f  %8.3f  %8.0f%n", label, avgMs, minMs, opsPerSec);
        }
        System.out.println();
    }
}
