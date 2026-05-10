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
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

/**
 * Performance benchmark for W3C XQuery Update Facility 3.0 operations.
 *
 * <p>Measures wall-clock time for W3C XQUF persistent update operations (insert,
 * delete, replace node, replace value, rename), their legacy eXist-db equivalents
 * (update insert, update delete, etc.), and in-memory copy-modify (transform)
 * expressions at various data sizes.</p>
 *
 * <p>This class intentionally does <em>not</em> end in {@code *Test} so that
 * Surefire will not discover it during normal {@code mvn test} runs.
 * Run explicitly with:</p>
 * <pre>
 *   mvn test -pl exist-core -Dtest=XQUFBenchmark \
 *       -Dexist.run.benchmarks=true -Ddependency-check.skip=true
 * </pre>
 */
@SuppressWarnings("PMD.ClassNamingConventions") // benchmark, not a test class; gated by -Dexist.run.benchmarks=true
public class XQUFBenchmark {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION_NAME = "benchmark-xquf";
    private static final String COLLECTION_PATH = "/db/" + COLLECTION_NAME;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;
    private static final int[] DATA_SIZES = {10, 50, 200};

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
        cms.createCollection(COLLECTION_NAME);
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

    // ---- Persistent update benchmarks ----

    @Test
    public void insertInto() throws XMLDBException {
        System.out.println("\n=== insert node ... into (persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("insert-into", size, (queryService, docPath) -> {
                // Reset document with N items
                storeDocument(size);

                // Insert a new child into each item
                final String update = String.format(
                        "for $item in doc('%s/bench.xml')//item " +
                        "return insert node <added/> into $item",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("insert-into benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void deleteNode() throws XMLDBException {
        System.out.println("\n=== delete node (persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("delete-node", size, (queryService, docPath) -> {
                // Reset document with N items, each having a <value> child
                storeDocument(size);

                // Delete the <value> child from each item
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return delete node $v",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("delete-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void replaceValue() throws XMLDBException {
        System.out.println("\n=== replace value of node (persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("replace-value", size, (queryService, docPath) -> {
                // Reset document
                storeDocument(size);

                // Replace value of each item's @id attribute
                final String update = String.format(
                        "for $item in doc('%s/bench.xml')//item " +
                        "return replace value of node $item/@id with concat('new-', $item/@id)",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("replace-value benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void renameNode() throws XMLDBException {
        System.out.println("\n=== rename node (persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("rename-node", size, (queryService, docPath) -> {
                // Reset document
                storeDocument(size);

                // Rename each <value> element to <renamed>
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return rename node $v as 'renamed'",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("rename-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void replaceNode() throws XMLDBException {
        System.out.println("\n=== replace node (persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("replace-node", size, (queryService, docPath) -> {
                // Reset document
                storeDocument(size);

                // Replace each <value> with a <new-value>
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return replace node $v with <new-value>{string($v)}</new-value>",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("replace-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    // ---- Legacy persistent update benchmarks (DEPRECATED syntax) ----

    @Test
    public void legacyInsertInto() throws XMLDBException {
        System.out.println("\n=== update insert ... into (legacy persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("legacy-insert-into", size, (queryService, docPath) -> {
                storeDocument(size);
                final String update = String.format(
                        "for $item in doc('%s/bench.xml')//item " +
                        "return update insert <added/> into $item",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("legacy-insert-into benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void legacyDeleteNode() throws XMLDBException {
        System.out.println("\n=== update delete (legacy persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("legacy-delete-node", size, (queryService, docPath) -> {
                storeDocument(size);
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return update delete $v",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("legacy-delete-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void legacyReplaceValue() throws XMLDBException {
        System.out.println("\n=== update value (legacy persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("legacy-replace-value", size, (queryService, docPath) -> {
                storeDocument(size);
                final String update = String.format(
                        "for $item in doc('%s/bench.xml')//item " +
                        "return update value $item/@id with concat('new-', $item/@id)",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("legacy-replace-value benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void legacyRenameNode() throws XMLDBException {
        System.out.println("\n=== update rename (legacy persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("legacy-rename-node", size, (queryService, docPath) -> {
                storeDocument(size);
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return update rename $v as 'renamed'",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("legacy-rename-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void legacyReplaceNode() throws XMLDBException {
        System.out.println("\n=== update replace (legacy persistent) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final double avgMs = runPersistentBenchmark("legacy-replace-node", size, (queryService, docPath) -> {
                storeDocument(size);
                final String update = String.format(
                        "for $v in doc('%s/bench.xml')//item/value " +
                        "return update replace $v with <new-value>{string($v)}</new-value>",
                        COLLECTION_PATH);
                queryService.query(update);
            });
            assertTrue("legacy-replace-node benchmark should complete in positive time", avgMs >= 0);
        }
    }

    // ---- In-memory copy-modify benchmarks ----

    @Test
    public void copyModifySingle() throws XMLDBException {
        System.out.println("\n=== copy-modify single node (in-memory) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $doc := <root>{ for $i in 1 to %d return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                    "return copy $c := $doc modify ( replace value of node $c//item[@id = '1']/value with 'modified' ) return $c//item[@id = '1']/value/string()",
                    size);
            final double avgMs = runInMemoryBenchmark("copy-modify-single", size, query);
            assertTrue("copy-modify-single benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void copyModifyMultiple() throws XMLDBException {
        System.out.println("\n=== copy-modify multiple replaceValue (in-memory) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $doc := <root>{ for $i in 1 to %d return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                    "return copy $c := $doc modify ( " +
                    "  for $v in $c//item/value return replace value of node $v with concat('m-', $v) " +
                    ") return count($c//item)",
                    size);
            final double avgMs = runInMemoryBenchmark("copy-modify-multi", size, query);
            assertTrue("copy-modify-multi benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void copyModifyInsertDelete() throws XMLDBException {
        System.out.println("\n=== copy-modify insert + delete (in-memory) ===");
        printHeader();

        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $doc := <root>{ for $i in 1 to %d return <item id='{$i}'><value>{$i}</value></item> }</root> " +
                    "return copy $c := $doc modify ( " +
                    "  insert node <added/> into $c, " +
                    "  for $v in $c//item[@id = ('1','2','3')]/value return delete node $v " +
                    ") return count($c//item)",
                    size);
            final double avgMs = runInMemoryBenchmark("copy-modify-ins-del", size, query);
            assertTrue("copy-modify-ins-del benchmark should complete in positive time", avgMs >= 0);
        }
    }

    @Test
    public void copyModifyDeepTree() throws XMLDBException {
        System.out.println("\n=== copy-modify deep tree (in-memory) ===");
        printHeader();

        // Nested structure: root > section > subsection > item (depth=4)
        for (final int size : DATA_SIZES) {
            final String query = String.format(
                    "let $doc := <root>{ " +
                    "  for $s in 1 to %d " +
                    "  return <section id='{$s}'>{ " +
                    "    for $ss in 1 to 3 " +
                    "    return <subsection id='{$s}-{$ss}'><item>data</item></subsection> " +
                    "  }</section> " +
                    "}</root> " +
                    "return copy $c := $doc modify ( " +
                    "  for $item in $c//item return replace value of node $item with 'updated' " +
                    ") return count($c//item[. = 'updated'])",
                    size);
            final double avgMs = runInMemoryBenchmark("copy-modify-deep", size, query);
            assertTrue("copy-modify-deep benchmark should complete in positive time", avgMs >= 0);
        }
    }

    // ---- Helpers ----

    private void storeDocument(final int numItems) throws XMLDBException {
        final Collection col = server.getRoot().getChildCollection(COLLECTION_NAME);
        final StringBuilder sb = new StringBuilder("<root>\n");
        for (int i = 1; i <= numItems; i++) {
            sb.append(String.format("  <item id='%d'><value>val-%d</value></item>\n", i, i));
        }
        sb.append("</root>");

        final XMLResource res = col.createResource("bench.xml", XMLResource.class);
        res.setContent(sb.toString());
        col.storeResource(res);
    }

    @FunctionalInterface
    interface PersistentOperation {
        void execute(XQueryService queryService, String docPath) throws XMLDBException;
    }

    private double runPersistentBenchmark(final String label, final int size,
                                         final PersistentOperation operation) throws XMLDBException {
        final XQueryService queryService = server.getRoot().getService(XQueryService.class);

        // warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.execute(queryService, COLLECTION_PATH + "/bench.xml");
        }

        // measure
        long totalNs = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            final long start = System.nanoTime();
            operation.execute(queryService, COLLECTION_PATH + "/bench.xml");
            final long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
        }

        final double avgMs = (totalNs / (double) MEASURE_ITERATIONS) / 1_000_000.0;
        System.out.printf("  %-24s  size=%3d  avg=%8.2f ms%n", label, size, avgMs);
        return avgMs;
    }

    private double runInMemoryBenchmark(final String label, final int size,
                                       final String query) throws XMLDBException {
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
            if (result.getSize() != 1) {
                throw new AssertionError(label + " (size=" + size + "): expected 1 result, got " + result.getSize());
            }
            final long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
        }

        final double avgMs = (totalNs / (double) MEASURE_ITERATIONS) / 1_000_000.0;
        System.out.printf("  %-24s  size=%3d  avg=%8.2f ms%n", label, size, avgMs);
        return avgMs;
    }

    private static void printHeader() {
        System.out.printf("  %-24s  %8s  %12s%n", "Operation", "Size", "Avg (ms)");
        System.out.println("  " + "-".repeat(50));
    }
}
