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
import org.exist.xmldb.IndexQueryService;
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

import java.util.Random;

/**
 * End-to-end timing for {@code fn:matches} on each of its evaluation paths, so that a change of
 * regular-expression engine can be measured rather than guessed at.
 *
 * <p>Three paths are timed separately, because they are implemented separately: a predicate over a
 * range-indexed element (the index scan), a predicate over an element with no index (the node
 * scan), and a plain call over atomic values (the value path). Disabled unless
 * {@code -Dexist.run.benchmarks=true}, like the other benchmarks here.</p>
 */
public class MatchesRegexBenchmarkTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION_NAME = "benchmark-matches-regex";
    private static final String DOC = "/db/" + COLLECTION_NAME + "/data.xml";
    private static final int ELEMENTS = 200_000;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;

    /** {@code indexed} has a string range index; {@code plain} does not. */
    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index><create qname="indexed" type="xs:string"/></index>
            </collection>
            """;

    private static final String[][] PATTERNS = {
        {"anchored-prefix", "^HAM"},
        {"wildcard", "a.*c"},
        {"digits", "\\d{3}"},
        {"unicode-class", "\\p{Lu}+ \\d+"},
        {"alternation", "(HAM|OPH)[A-Z]+"},
        {"class-subtraction", "[A-Z-[AEIOU]]{3,}"},
    };

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
        final Collection root = server.getRoot();
        final Collection col = root.getService(CollectionManagementService.class).createCollection(COLLECTION_NAME);
        col.getService(IndexQueryService.class).configureCollection(COLLECTION_CONFIG);

        final String[] words = {"HAMLET", "OPHELIA", "HORATIO", "CLAUDIUS", "GERTRUDE", "hamlet", "Polonius", "Laertes", "Rosencrantz", "Guildenstern"};
        final Random rnd = new Random(42);
        final StringBuilder sb = new StringBuilder(ELEMENTS * 64);
        sb.append("<data>");
        for (int i = 0; i < ELEMENTS; i++) {
            final String v = words[rnd.nextInt(words.length)] + " " + rnd.nextInt(1000) + " " + words[rnd.nextInt(words.length)].toLowerCase();
            sb.append("<e><indexed>").append(v).append("</indexed><plain>").append(v).append("</plain></e>");
        }
        sb.append("</data>");
        final XMLResource res = col.createResource("data.xml", XMLResource.class);
        res.setContent(sb.toString());
        col.storeResource(res);
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        if (!Boolean.getBoolean("exist.run.benchmarks")) {
            return;
        }
        server.getRoot().getService(CollectionManagementService.class).removeCollection(COLLECTION_NAME);
    }

    @Test
    public void indexScan() throws XMLDBException {
        for (final String[] p : PATTERNS) {
            time("index-scan", p[0], "count(doc('" + DOC + "')//e[matches(indexed, '" + p[1] + "')])");
        }
    }

    @Test
    public void nodeScan() throws XMLDBException {
        for (final String[] p : PATTERNS) {
            time("node-scan", p[0], "count(doc('" + DOC + "')//e[matches(plain, '" + p[1] + "')])");
        }
    }

    @Test
    public void valuePath() throws XMLDBException {
        for (final String[] p : PATTERNS) {
            time("value-path", p[0], "count(for $s in doc('" + DOC + "')//plain/string() return $s[matches(., '" + p[1] + "')])");
        }
    }

    private void time(final String path, final String label, final String query) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        long count = -1;
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            xqs.query(query);
        }
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            final long start = System.nanoTime();
            final ResourceSet rs = xqs.query(query);
            final long elapsed = System.nanoTime() - start;
            total += elapsed;
            best = Math.min(best, elapsed);
            count = Long.parseLong(rs.getResource(0).getContent().toString());
        }
        System.out.printf("BENCH %-11s %-18s avg=%8.1f ms  best=%8.1f ms  matched=%d%n",
                path, label, total / (double) MEASURE_ITERATIONS / 1e6, best / 1e6, count);
    }
}
