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
import org.xmldb.api.base.XMLDBException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory-overhead benchmark for the {@code order by} clause's tuple-capture approach
 * introduced in PR #6073.
 *
 * <p>Measures the heap cost of the new {@code OrderByTuple} / {@code LinkedHashMap} storage
 * at various data sizes and variable counts, and compares measured values against theoretical
 * estimates.</p>
 *
 * <p>Not included in the normal test suite — the class name does not match Surefire's
 * default {@code *Test} pattern.  Run explicitly with:
 * <pre>
 *   mvn test -pl exist-core -Dtest=OrderByClauseMemoryBenchmark \
 *       -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
public class OrderByClauseMemoryBenchmark {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    /** Number of GC-and-measure repetitions; we take the median. */
    private static final int SAMPLES = 7;

    /**
     * Theoretical per-tuple overhead in bytes for the new approach (parallel-arrays optimization).
     *
     * <p>Breakdown per OrderByTuple:
     * <ul>
     *   <li>Object header: ~16 bytes</li>
     *   <li>3 fields (variableValues ref, sortKeys ref, originalPos int): ~24 bytes</li>
     *   <li>Sequence[] array: ~16 + 8*V bytes (header + one ref per variable)</li>
     *   <li>AtomicValue[] array: ~16 + 8*K bytes (header + one ref per key)</li>
     * </ul>
     * Total ≈ 72 + 8*V + 8*K bytes per tuple.
     */
    private static long theoreticalBytesPerTupleNew(final int numVars, final int numKeys) {
        return 72L + 8L * numVars + 8L * numKeys;
    }

    /**
     * Theoretical per-tuple overhead in bytes for the old approach (OrderedValueSequence.Entry).
     *
     * <p>Breakdown per Entry:
     * <ul>
     *   <li>Object header: ~16 bytes</li>
     *   <li>5 fields (refs to lists, item, pos int, values list ref): ~40 bytes</li>
     *   <li>ArrayList for values: ~40 + 8*K bytes</li>
     * </ul>
     * Total ≈ 96 + 8*K bytes per tuple (but this only captures the result item, not variable
     * bindings — it cannot support post-order-by clauses correctly).
     */
    private static long theoreticalBytesPerTupleOld(final int numKeys) {
        return 96L + 8L * numKeys;
    }

    @Test
    public void memoryBenchmark() throws XMLDBException {
        Assume.assumeTrue(
                "Benchmark skipped by default. Re-run with -Dexist.run.benchmarks=true",
                Boolean.getBoolean("exist.run.benchmarks"));

        // Query matrix: label -> { query template (use %d for N), expected V, expected K }
        final Map<String, Object[]> queryMatrix = new LinkedHashMap<>();
        queryMatrix.put("simple",
                new Object[]{"for $x in 1 to %d order by $x return $x", 1, 1});
        queryMatrix.put("withCount",
                new Object[]{"for $x in 1 to %d order by $x count $rank return $rank", 2, 1});
        queryMatrix.put("twoVars",
                new Object[]{"for $x in 1 to %d for $y in 1 to 3 order by $x * 3 + $y return ($x, $y)", 2, 1});
        queryMatrix.put("threeVarsLet",
                new Object[]{"for $x in 1 to %d for $y in 1 to 3 let $z := $x + $y order by $z return ($x, $y, $z)", 3, 1});

        final int[] sizes = {100, 1_000, 10_000, 100_000};

        System.out.println("\n=== OrderByClause Memory Benchmark ===\n");

        // Header
        System.out.printf("%-16s  %7s  %4s  %4s  %12s  %12s  %12s  %8s%n",
                "Query", "N", "V", "K",
                "Measured(KB)", "Theory(KB)", "OldTheory(KB)", "Ratio");
        System.out.println("-".repeat(90));

        for (final Map.Entry<String, Object[]> entry : queryMatrix.entrySet()) {
            final String label = entry.getKey();
            final String queryTemplate = (String) entry.getValue()[0];
            final int expectedK = (int) entry.getValue()[2];

            for (final int n : sizes) {
                // For twoVars and threeVarsLet, effective N is n*3 tuples
                final int effectiveN;
                if (label.equals("twoVars") || label.equals("threeVarsLet")) {
                    effectiveN = n * 3;
                } else {
                    effectiveN = n;
                }

                final String query = "count(" + queryTemplate.formatted(n) + ")";

                // Warm up (also initializes any lazy structures)
                for (int w = 0; w < 3; w++) {
                    server.executeQuery(query);
                }

                // Measure heap delta across SAMPLES runs, take median
                final long[] deltas = new long[SAMPLES];
                for (int s = 0; s < SAMPLES; s++) {
                    // Force GC and measure baseline
                    forceGC();
                    final long before = usedHeap();

                    // Execute query (this will populate OrderByTuples in eval, sort in postEval)
                    server.executeQuery(query);

                    // The tuples are transient and GC'd after postEval, but we can measure
                    // the peak by reading instrumentation and computing theoretical size.
                    // For measured delta, we capture right after execution (tuples may already
                    // be GC'd, so this is a lower bound — theoretical is more reliable).
                    final long after = usedHeap();
                    deltas[s] = after - before;
                }
                Arrays.sort(deltas);
                final long measuredDelta = deltas[SAMPLES / 2]; // median

                // Read instrumentation
                final int tupleCount = OrderByClause.lastTupleCount;
                final int varCount = OrderByClause.lastVarCount;

                // Theoretical estimates
                final long theoryNew = (long) tupleCount * theoreticalBytesPerTupleNew(varCount, expectedK);
                final long theoryOld = (long) tupleCount * theoreticalBytesPerTupleOld(expectedK);
                final double ratio = theoryOld > 0 ? (double) theoryNew / theoryOld : 0;

                System.out.printf("%-16s  %7d  %4d  %4d  %12.1f  %12.1f  %12.1f  %8.1fx%n",
                        label, effectiveN, varCount, expectedK,
                        measuredDelta / 1024.0,
                        theoryNew / 1024.0,
                        theoryOld / 1024.0,
                        ratio);
            }
            System.out.println();
        }

        // Summary
        System.out.println("=== Summary ===");
        System.out.println("- 'Measured' is the median heap delta (lower bound — tuples are transient).");
        System.out.println("- 'Theory' is the estimated per-tuple overhead × tuple count for the new approach.");
        System.out.println("- 'OldTheory' is the estimated per-tuple overhead for the old OrderedValueSequence.Entry approach.");
        System.out.println("- 'Ratio' = Theory / OldTheory (overhead multiplier of new vs old).");
        System.out.println("- All tuple memory is transient: freed after postEval() completes.");
        System.out.println("- Variable values are references, not copies — no item data is duplicated.");
        System.out.println();
    }

    /**
     * Best-effort GC: call System.gc() twice with a short sleep to encourage finalization.
     */
    private static void forceGC() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    /**
     * Returns the currently used heap in bytes.
     */
    private static long usedHeap() {
        final Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
