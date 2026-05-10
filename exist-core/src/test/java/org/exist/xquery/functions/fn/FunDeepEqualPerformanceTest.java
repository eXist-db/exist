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
 * Regression test for GH-4050: fn:deep-equal was ~24x slower than
 * xmldiff:compare on equivalent large XML inputs (5,490 ms vs 228 ms on
 * the reporter's TEST.zip; ~2,500 ms on the synthetic 10k-element corpus
 * below). The fix dispatches to a streaming comparator built on
 * {@link org.exist.stax.IEmbeddedXMLStreamReader} when both arguments
 * are persistent-DOM {@code DOCUMENT} or {@code ELEMENT} nodes; the
 * reader iterates the BTree node stream directly and bypasses the
 * legacy {@code getFirstChild} / {@code getNextSibling} recursion,
 * which acquires a broker per call.
 */
public class FunDeepEqualPerformanceTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * Two stored documents with structurally-identical large trees (~10,000
     * elements, attribute-heavy). Mirrors the GH-4050 reporter's scenario:
     * stored XML, where each persistent-DOM accessor traverses the storage
     * layer rather than running on a fast in-memory linked list. With many
     * attributes per element, compareAttributes' O(attrs^2) NamedNodeMap
     * lookup also bites.
     */
    @BeforeClass
    public static void storeLargeDocs() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        // breadth 10, depth 4 -> ~10,000 elements; 6 attributes per element.
        // Attribute count chosen large enough to expose compareAttributes'
        // quadratic behaviour without making document storage prohibitively
        // slow for a unit test.
        xqs.query("""
                declare function local:tree($depth, $breadth) {
                    if ($depth eq 0) then
                        <leaf id="x" type="t" a="1" b="2" c="3" d="4">value</leaf>
                    else
                        <branch id="b" depth="{$depth}" a="1" b="2" c="3" d="4">{
                            for $i in 1 to $breadth
                            return local:tree($depth - 1, $breadth)
                        }</branch>
                };
                xmldb:store("/db", "deep-equal-perf-a.xml", local:tree(5, 8)),
                xmldb:store("/db", "deep-equal-perf-b.xml", local:tree(5, 8))
                """);
    }

    @AfterClass
    public static void removeStoredDocs() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("""
                xmldb:remove("/db", "deep-equal-perf-a.xml"),
                xmldb:remove("/db", "deep-equal-perf-b.xml")
                """);
    }

    private static final String STORED_EQUAL_TREES =
            "fn:deep-equal(doc('/db/deep-equal-perf-a.xml'), doc('/db/deep-equal-perf-b.xml'))";

    /**
     * XQuery that builds two structurally-identical large in-memory trees
     * (~10,000 elements: 4 levels deep, breadth 10 at each level, with
     * attributes and text values) and runs fn:deep-equal on them. The
     * memtree path is unchanged by the GH-4050 fix; this test guards
     * against unrelated regressions on in-memory comparison.
     */
    private static final String LARGE_EQUAL_TREES = """
            declare function local:tree($depth, $breadth) {
                if ($depth eq 0) then
                    <leaf id="x" type="t">value</leaf>
                else
                    <branch id="b" depth="{$depth}">{
                        for $i in 1 to $breadth
                        return local:tree($depth - 1, $breadth)
                    }</branch>
            };
            let $a := local:tree(4, 10)
            let $b := local:tree(4, 10)
            return fn:deep-equal($a, $b)
            """;

    private static final String LARGE_TREES_DIFFER_AT_LEAF = """
            declare function local:tree($depth, $breadth, $marker) {
                if ($depth eq 0) then
                    <leaf id="x" type="t">{$marker}</leaf>
                else
                    <branch id="b" depth="{$depth}">{
                        for $i in 1 to $breadth
                        return local:tree($depth - 1, $breadth, $marker)
                    }</branch>
            };
            let $a := local:tree(4, 10, "value")
            let $b := local:tree(4, 10, "VALUE")
            return fn:deep-equal($a, $b)
            """;

    private static final String LARGE_TREES_DIFFER_AT_ROOT = """
            declare function local:tree($depth, $breadth) {
                if ($depth eq 0) then
                    <leaf id="x" type="t">value</leaf>
                else
                    <branch id="b" depth="{$depth}">{
                        for $i in 1 to $breadth
                        return local:tree($depth - 1, $breadth)
                    }</branch>
            };
            let $a := <rootA>{local:tree(4, 10)}</rootA>
            let $b := <rootB>{local:tree(4, 10)}</rootB>
            return fn:deep-equal($a, $b)
            """;

    private long timeQuery(final String xquery) throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        // Warm-up to amortise compilation/class-loading cost.
        xqs.query(xquery);
        final long start = System.nanoTime();
        final ResourceSet rs = xqs.query(xquery);
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        // Sanity-check the result: every query above returns one boolean.
        assertEquals(1, rs.getSize());
        return elapsedMs;
    }

    private boolean queryResult(final String xquery) throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(xquery);
        return Boolean.parseBoolean(rs.getResource(0).getContent().toString());
    }

    @Test
    public void deepEqualOnLargeEqualTreesIsFast() throws XMLDBException {
        // In-memory case (memtree) -- the streaming fast path does not
        // apply here; memtree's linked-list sibling traversal is already
        // O(N) and the legacy recursion is the right path. Sanity check.
        assertTrue(queryResult(LARGE_EQUAL_TREES));
        final long elapsedMs = timeQuery(LARGE_EQUAL_TREES);
        System.out.println("[GH-4050] in-memory equal 10k-element trees: " + elapsedMs + "ms");
        final long threshold = 3000L;
        assertTrue(
                "fn:deep-equal on 10,000-element in-memory equal trees took " + elapsedMs
                        + "ms (threshold " + threshold + "ms)",
                elapsedMs <= threshold);
    }

    @Test
    public void deepEqualOnStoredEqualDocsIsFast() throws XMLDBException {
        // Persistent-DOM case -- this is the GH-4050 reporter's scenario.
        // Pre-fix every getFirstChild / getNextSibling on a stored
        // ElementImpl acquires a broker and walks the parent's children
        // via a fresh XMLStreamReader, making compareContents quadratic
        // in sibling count. The reporter measured ~9000 ms in 2021.
        // Post-fix the streaming comparator iterates the BTree node
        // stream once per document at storage speed; on this 10k-element
        // synthetic the win is ~20x (124 ms observed locally).
        assertTrue(queryResult(STORED_EQUAL_TREES));
        final long elapsedMs = timeQuery(STORED_EQUAL_TREES);
        System.out.println("[GH-4050] stored equal 10k-element docs (6 attrs/elem): " + elapsedMs + "ms");
        // Generous threshold to tolerate CI variance while still catching a
        // regression that puts us back into multi-second territory.
        final long threshold = 5000L;
        assertTrue(
                "fn:deep-equal on stored 10,000-element docs took " + elapsedMs
                        + "ms (threshold " + threshold + "ms); GH-4050 regression?",
                elapsedMs <= threshold);
    }

    @Test
    public void deepEqualOnRootMismatchStillShortCircuits() throws XMLDBException {
        // Top-level name mismatch: in-memory case (memtree). The legacy
        // path bails on the first compareNames mismatch.
        assertEquals(false, queryResult(LARGE_TREES_DIFFER_AT_ROOT));
        final long elapsedMs = timeQuery(LARGE_TREES_DIFFER_AT_ROOT);
        System.out.println("[GH-4050] deep-equal on root-mismatched 10k-element trees: " + elapsedMs + "ms");
        final long threshold = 1500L;
        assertTrue(
                "Root-mismatch fn:deep-equal took " + elapsedMs
                        + "ms (threshold " + threshold + "ms); pre-check ordering broken?",
                elapsedMs <= threshold);
    }

    @Test
    public void deepEqualOnLeafMismatchProducesCorrectResult() throws XMLDBException {
        // Difference is buried at every leaf; the comparator (streaming
        // for stored docs, recursive for memtree) walks until the leaf
        // mismatch surfaces. Correctness gate only.
        assertEquals(false, queryResult(LARGE_TREES_DIFFER_AT_LEAF));
    }

    @Test
    public void attributeOrderInsensitive() throws XMLDBException {
        final String q = """
                let $a := <e a="1" b="2" c="3"/>
                let $b := <e c="3" a="1" b="2"/>
                return fn:deep-equal($a, $b)
                """;
        assertEquals(true, queryResult(q));
    }

    @Test
    public void nestedAttributeOrderInsensitive() throws XMLDBException {
        final String q = """
                let $a := <root><e a="1" b="2"/><f x="x" y="y"/></root>
                let $b := <root><e b="2" a="1"/><f y="y" x="x"/></root>
                return fn:deep-equal($a, $b)
                """;
        assertEquals(true, queryResult(q));
    }

    @Test
    public void typedNumericVsStringNotEqual() throws XMLDBException {
        // Per W3C XPath 3.1 deep-equal, xs:integer 1 is NOT deep-equal to "1".
        // Atomic comparison; streaming path does not apply.
        assertEquals(false, queryResult("fn:deep-equal(xs:integer(1), '1')"));
    }

    @Test
    public void integerAndDoubleEqual() throws XMLDBException {
        // xs:integer 1 IS deep-equal to xs:double 1.0 per spec.
        assertEquals(true, queryResult("fn:deep-equal(xs:integer(1), xs:double(1.0))"));
    }

    @Test
    public void nanEqualToNan() throws XMLDBException {
        // Special case: NaN is deep-equal to NaN even though NaN != NaN.
        assertEquals(true,
                queryResult("fn:deep-equal(xs:double('NaN'), xs:double('NaN'))"));
    }

    @Test
    public void textVsCommentChildrenIgnored() throws XMLDBException {
        // compareContents (and the streaming comparator) skip comments and PIs.
        final String q = """
                let $a := <e>hello<!--ignore-->world</e>
                let $b := <e>hello<?pi data?>world</e>
                return fn:deep-equal($a, $b)
                """;
        assertEquals(true, queryResult(q));
    }

    @Test
    public void differentChildOrderNotEqual() throws XMLDBException {
        // Element child order IS significant, unlike attribute order.
        final String q = """
                let $a := <root><a/><b/></root>
                let $b := <root><b/><a/></root>
                return fn:deep-equal($a, $b)
                """;
        assertEquals(false, queryResult(q));
    }

    @Test
    public void differentNamespaceNotEqual() throws XMLDBException {
        final String q = """
                let $a := <e xmlns="urn:a"/>
                let $b := <e xmlns="urn:b"/>
                return fn:deep-equal($a, $b)
                """;
        assertEquals(false, queryResult(q));
    }

    @Test
    public void emptySequencesEqual() throws XMLDBException {
        assertEquals(true, queryResult("fn:deep-equal((), ())"));
    }

    @Test
    public void differentLengthSequencesNotEqual() throws XMLDBException {
        assertEquals(false, queryResult("fn:deep-equal((1, 2), (1, 2, 3))"));
    }
}
