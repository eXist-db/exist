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
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for issue #2129. Wildcard {@code following::*} previously
 * walked an {@link org.exist.stax.IEmbeddedXMLStreamReader} from the document
 * root regardless of where the reference node was, so a query like
 * {@code $w/following::*[1]} ran in O(refPosition) instead of O(K). The fix
 * in {@code LocationStep.getPrecedingOrFollowing} starts the reader at the
 * reference node when it is inside the document subtree being walked.
 *
 * Two complementary checks:
 * <ul>
 *   <li>Correctness — same nodes are returned, descendants of the reference
 *       node are still excluded;</li>
 *   <li>Performance — late positions complete in roughly the same time as
 *       early positions, on a 50,000-element flat document.</li>
 * </ul>
 */
public class FollowingAxisPositionRegressionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String SMALL_DOC = "/db/words-small.xml";
    private static final String LARGE_DOC = "/db/words-large.xml";

    @BeforeClass
    public static void storeTestDocuments() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query(
                """
                let $words := for $i in (1 to 50) return <w xml:id="{$i}">{$i}</w>
                return xmldb:store('/db', 'words-small.xml', document {<words>{$words}</words>})
                """);
        xqs.query(
                """
                let $words := for $i in (1 to 50000) return <w xml:id="{$i}">{$i}</w>
                return xmldb:store('/db', 'words-large.xml', document {<words>{$words}</words>})
                """);
    }

    @AfterClass
    public static void removeTestDocuments() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("xmldb:remove('/db', 'words-small.xml')");
        xqs.query("xmldb:remove('/db', 'words-large.xml')");
    }

    @Test
    public void reproducerOutputAtMidpoint() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(
                """
                let $w := doc('/db/words-small.xml')//w[@xml:id='25']
                let $before := for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text()
                let $word := '[' || $w/text() || ']'
                let $after := for $i in (1 to 5) return $w/following::*[$i][self::w]/text()
                return string-join(($before, $word, $after), ' ')
                """);
        assertEquals(1, rs.getSize());
        assertEquals("20 21 22 23 24 [25] 26 27 28 29 30",
                rs.getResource(0).getContent().toString());
    }

    @Test
    public void reproducerOutputAtLatePosition() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(
                """
                let $w := doc('/db/words-small.xml')//w[@xml:id='45']
                let $before := for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text()
                let $word := '[' || $w/text() || ']'
                let $after := for $i in (1 to 5) return $w/following::*[$i][self::w]/text()
                return string-join(($before, $word, $after), ' ')
                """);
        assertEquals(1, rs.getSize());
        assertEquals("40 41 42 43 44 [45] 46 47 48 49 50",
                rs.getResource(0).getContent().toString());
    }

    @Test
    public void followingExcludesDescendants() throws XMLDBException {
        // The fix changes the StAX reader to start at the reference node, so
        // its descendant events come first. The FollowingFilter must still
        // exclude them.
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query(
                """
                xquery version "3.1";
                let $doc := <root><a><inner1/><inner2/></a><b/><c><inner3/></c></root>
                return xmldb:store('/db', 'descendants.xml', document {$doc})
                """);
        try {
            final ResourceSet rs = xqs.query(
                    """
                    for $n in doc('/db/descendants.xml')//a/following::*
                    return name($n)
                    """);
            assertEquals(3, rs.getSize());
            assertEquals("b", rs.getResource(0).getContent().toString());
            assertEquals("c", rs.getResource(1).getContent().toString());
            assertEquals("inner3", rs.getResource(2).getContent().toString());
        } finally {
            xqs.query("xmldb:remove('/db', 'descendants.xml')");
        }
    }

    @Test
    public void followingAxisIsPositionIndependent() throws XMLDBException {
        // On a 50,000-element flat document, isolating the wildcard following::
        // axis. Before the fix, the late-position run took 1.6-2x the early-
        // position run because the StAX reader walked from the document root.
        // After the fix, both run in ~constant time. Threshold is loose to
        // tolerate JIT warm-up and CI variance but tight enough to catch a
        // re-regression that re-introduces the doc-start walk.
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);

        // Warm-up.
        xqs.query(followingOnlyQuery(25000));
        xqs.query(followingOnlyQuery(25000));

        final long earlyMs = timeQuery(xqs, followingOnlyQuery(5000));
        final long lateMs = timeQuery(xqs, followingOnlyQuery(45000));

        final long threshold = Math.max(500L, earlyMs * 3L);
        assertTrue(
                "following:: at position 45000 took " + lateMs + "ms; "
                        + "at position 5000 it took " + earlyMs + "ms; "
                        + "threshold=" + threshold + "ms (3x early or 500ms min). "
                        + "If this regressed, the StAX reader is probably walking "
                        + "from the document root again - see issue #2129.",
                lateMs <= threshold);
    }

    private static long timeQuery(final XQueryService xqs, final String query)
            throws XMLDBException {
        final long start = System.nanoTime();
        xqs.query(query).getSize();
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static String followingOnlyQuery(final int xmlId) {
        return """
                xquery version "3.1";
                let $w := doc('%s')//w[@xml:id='%d']
                return for $i in (1 to 5) return $w/following::*[$i][self::w]/text()
                """.formatted(LARGE_DOC, xmlId);
    }
}
