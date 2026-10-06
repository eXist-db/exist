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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for GH-2697: following-sibling axis was O(N^2) on persistent
 * documents because {@code NewArrayNodeSet.selectFollowingSiblings} continued
 * scanning past the parent's subtree boundary instead of breaking, while the
 * symmetric {@code selectPrecedingSiblings} broke correctly. On the issue's
 * 100,000-element doc, the asymmetry was ~80x.
 */
public class AxisPerformanceRegressionTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String DOC_PATH = "/db/axis-perf-test.xml";
    private static final int ROUNDS = 5;

    @BeforeAll
    public static void storeTestDocument() throws XMLDBException {
        // 1500 <a> elements, each with 20 <b> children -> 30,000 <b> total.
        // 19 of each <a>'s 20 children have a preceding-sibling <b>; same for
        // following. So count for both predicates is 28,500. With the bug
        // present, following-sibling is O(total-following-Bs) per context node
        // (~900M operations) and runs in seconds; with the fix, both predicates
        // run in under a second.
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query(
                """
                let $doc :=
                    <test>{(1 to 1500) ! <a>{(1 to 20) ! <b>{.}</b>}</a>}</test>
                return xmldb:store("/db", "axis-perf-test.xml", $doc)
                """);
    }

    @AfterAll
    public static void removeTestDocument() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("xmldb:remove(\"/db\", \"axis-perf-test.xml\")");
    }

    private long timeQuery(final String axis) throws XMLDBException {
        final long start = System.nanoTime();
        execute("count(doc(\"" + DOC_PATH + "\")//b[" + axis + "::b])");
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private ResourceSet execute(final String xquery) throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        return xqs.query(xquery);
    }

    @Test
    public void followingSiblingMatchesPrecedingSiblingCount() throws XMLDBException {
        final ResourceSet preceding = execute(
                "count(doc(\"" + DOC_PATH + "\")//b[preceding-sibling::b])");
        final ResourceSet following = execute(
                "count(doc(\"" + DOC_PATH + "\")//b[following-sibling::b])");

        final String precedingCount = preceding.getResource(0).getContent().toString();
        final String followingCount = following.getResource(0).getContent().toString();

        assertEquals("28500", precedingCount);
        assertEquals(precedingCount, followingCount);
    }

    @Test
    public void followingSiblingPerformanceCloseToPrecedingSibling() throws XMLDBException {
        // Warm-up - first run pays index/parsing costs we don't want to measure.
        execute("count(doc(\"" + DOC_PATH + "\")//b[preceding-sibling::b])");
        execute("count(doc(\"" + DOC_PATH + "\")//b[following-sibling::b])");

        // Five rounds in alternating order, each side judged by its fastest round: load on the machine
        // slows a round down but never makes it faster than the code can run, so one slow round
        // (a GC pause, a busy CI runner) cannot decide the outcome.
        long precedingMs = Long.MAX_VALUE;
        long followingMs = Long.MAX_VALUE;
        for (int round = 0; round < ROUNDS; round++) {
            if (round % 2 == 0) {
                precedingMs = Math.min(precedingMs, timeQuery("preceding-sibling"));
                followingMs = Math.min(followingMs, timeQuery("following-sibling"));
            } else {
                followingMs = Math.min(followingMs, timeQuery("following-sibling"));
                precedingMs = Math.min(precedingMs, timeQuery("preceding-sibling"));
            }
        }

        // Pre-fix this ratio was ~80x on the original issue's corpus and well
        // over 10x on this smaller one. Threshold is intentionally loose so it
        // tolerates CI variance but still catches a re-regression.
        final long threshold = Math.max(500L, precedingMs * 5L);
        assertTrue(
                followingMs <= threshold,
                "following-sibling=" + followingMs + "ms, preceding-sibling=" + precedingMs
                        + "ms (best of " + ROUNDS + " rounds); threshold=" + threshold + "ms (5x preceding-sibling, min 500ms)");
    }
}
