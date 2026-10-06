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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    /** Runs of a query before it is timed, so that compilation and class loading are not measured. */
    private static final int WARM_UPS = 2;

    /** Timed runs of a query; the fastest one counts. */
    private static final int RUNS = 5;

    private static final String STORED_EQUAL_TREES =
            "fn:deep-equal(doc('/db/deep-equal-perf-a.xml'), doc('/db/deep-equal-perf-b.xml'))";

    private static final String STORED_ROOT_MISMATCH =
            "fn:deep-equal(doc('/db/deep-equal-perf-a.xml'), doc('/db/deep-equal-perf-c.xml'))";

    /**
     * Visits every node and attribute of the two stored documents of {@link #STORED_EQUAL_TREES} without
     * comparing them: the yardstick for how long a pass over this much stored data takes on this machine.
     */
    private static final String STORED_TRAVERSAL = """
            count(doc('/db/deep-equal-perf-a.xml')//node()) + count(doc('/db/deep-equal-perf-b.xml')//node())
            + count(doc('/db/deep-equal-perf-a.xml')//@*) + count(doc('/db/deep-equal-perf-b.xml')//@*)
            """;

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

    /** Builds the trees of {@link #LARGE_EQUAL_TREES} and only counts their elements: the construction cost alone. */
    private static final String LARGE_TREES_CONSTRUCT_ONLY = """
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
            return count($a//*) + count($b//*)
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

    /**
     * Two stored documents with structurally-identical large trees (~10,000
     * elements, attribute-heavy). Mirrors the GH-4050 reporter's scenario:
     * stored XML, where each persistent-DOM accessor traverses the storage
     * layer rather than running on a fast in-memory linked list. With many
     * attributes per element, compareAttributes' O(attrs^2) NamedNodeMap
     * lookup also bites.
     */
    @BeforeAll
    static void storeLargeDocs() throws XMLDBException {
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
                xmldb:store("/db", "deep-equal-perf-b.xml", local:tree(5, 8)),
                (: same size, another name at the root: the comparison is decided by the first element :)
                xmldb:store("/db", "deep-equal-perf-c.xml", <otherroot>{ local:tree(5, 8)/node() }</otherroot>)
                """);
    }

    @AfterAll
    static void removeStoredDocs() throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("""
                xmldb:remove("/db", "deep-equal-perf-a.xml"),
                xmldb:remove("/db", "deep-equal-perf-b.xml"),
                xmldb:remove("/db", "deep-equal-perf-c.xml")
                """);
    }

    /**
     * The time of the fastest of {@link #RUNS} runs after {@link #WARM_UPS} warm-ups. Noise from a busy
     * machine or a cold JIT only ever makes a run slower, so the minimum is the stable figure; the tests
     * compare two such figures taken one after the other instead of checking a fixed number of
     * milliseconds, which depends on the machine (the same query took 1.0 s on a CI runner and 50 ms
     * on a laptop).
     */
    private long fastestNanos(final String xquery) throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        for (int i = 0; i < WARM_UPS; i++) {
            xqs.query(xquery);
        }
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            final long start = System.nanoTime();
            final ResourceSet rs = xqs.query(xquery);
            final long elapsed = System.nanoTime() - start;
            // Sanity-check the result: every query above returns one value.
            assertEquals(1, rs.getSize());
            fastest = Math.min(fastest, elapsed);
        }
        return fastest;
    }

    private static String ms(final long nanos) {
        return String.format("%.1f ms", nanos / 1_000_000.0);
    }

    private boolean queryResult(final String xquery) throws XMLDBException {
        final XQueryService xqs =
                existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(xquery);
        return Boolean.parseBoolean(rs.getResource(0).getContent().toString());
    }

    @Test
    void deepEqualOnLargeEqualTreesIsFast() throws XMLDBException {
        // In-memory case (memtree) -- the streaming fast path does not
        // apply here; memtree's linked-list sibling traversal is already
        // O(N) and the legacy recursion is the right path. Constructing the
        // two trees is most of the time, so the yardstick is the same query
        // without the comparison: a comparison that is no longer linear in
        // the size of the trees would dwarf it.
        assertTrue(queryResult(LARGE_EQUAL_TREES));
        final long comparing = fastestNanos(LARGE_EQUAL_TREES);
        final long constructing = fastestNanos(LARGE_TREES_CONSTRUCT_ONLY);
        System.out.println("[GH-4050] in-memory equal 10k-element trees: " + ms(comparing)
                + " (construction alone " + ms(constructing) + ")");
        assertTrue(
                comparing <= 5 * constructing,
                "fn:deep-equal on 10,000-element in-memory equal trees took " + ms(comparing)
                        + ", more than 5 times the " + ms(constructing) + " that constructing them takes");
    }

    @Test
    void deepEqualOnStoredEqualDocsIsFast() throws XMLDBException {
        // Persistent-DOM case -- this is the GH-4050 reporter's scenario.
        // Pre-fix every getFirstChild / getNextSibling on a stored
        // ElementImpl acquires a broker and walks the parent's children
        // via a fresh XMLStreamReader, making compareContents quadratic
        // in sibling count. Post-fix the streaming comparator iterates the
        // BTree node stream once per document at storage speed. The yardstick
        // is a plain pass over the same two documents on the same machine:
        // the streaming comparator needs about a third of it, the quadratic
        // version many times more (see the commit that introduced this check).
        assertTrue(queryResult(STORED_EQUAL_TREES));
        final long comparing = fastestNanos(STORED_EQUAL_TREES);
        final long traversing = fastestNanos(STORED_TRAVERSAL);
        System.out.println("[GH-4050] stored equal 10k-element docs (6 attrs/elem): " + ms(comparing)
                + " (a pass over both documents " + ms(traversing) + ")");
        assertTrue(
                comparing <= 2 * traversing,
                "fn:deep-equal on stored 10,000-element docs took " + ms(comparing)
                        + ", more than twice the " + ms(traversing) + " of a plain pass over the same documents;"
                        + " GH-4050 regression?");
    }

    @Test
    void deepEqualOnRootMismatchStillShortCircuits() throws XMLDBException {
        // Top-level name mismatch on stored documents of the same size as the
        // equal pair: the streaming comparator decides on the first element, so
        // it must not take anything like the time of the full comparison. Stored
        // documents are used because nothing has to be constructed there, which
        // would otherwise be most of the time (an in-memory version of this test
        // measured the construction of the trees, not the comparison).
        assertFalse(queryResult(STORED_ROOT_MISMATCH));
        final long mismatching = fastestNanos(STORED_ROOT_MISMATCH);
        final long comparing = fastestNanos(STORED_EQUAL_TREES);
        System.out.println("[GH-4050] deep-equal on root-mismatched stored docs: " + ms(mismatching)
                + " (full comparison " + ms(comparing) + ")");
        assertTrue(
                mismatching * 10 <= comparing,
                "Root-mismatch fn:deep-equal took " + ms(mismatching) + ", more than a tenth of the "
                        + ms(comparing) + " of a full comparison; pre-check ordering broken?");
    }

    @Test
    void deepEqualOnInMemoryRootMismatchIsFalse() throws XMLDBException {
        // Correctness gate for the in-memory path (no timing: see the test above).
        assertFalse(queryResult(LARGE_TREES_DIFFER_AT_ROOT));
    }

    @Test
    void deepEqualOnLeafMismatchProducesCorrectResult() throws XMLDBException {
        // Difference is buried at every leaf; the comparator (streaming
        // for stored docs, recursive for memtree) walks until the leaf
        // mismatch surfaces. Correctness gate only.
        assertFalse(queryResult(LARGE_TREES_DIFFER_AT_LEAF));
    }

    @Test
    void attributeOrderInsensitive() throws XMLDBException {
        final String q = """
                let $a := <e a="1" b="2" c="3"/>
                let $b := <e c="3" a="1" b="2"/>
                return fn:deep-equal($a, $b)
                """;
        assertTrue(queryResult(q));
    }

    @Test
    void nestedAttributeOrderInsensitive() throws XMLDBException {
        final String q = """
                let $a := <root><e a="1" b="2"/><f x="x" y="y"/></root>
                let $b := <root><e b="2" a="1"/><f y="y" x="x"/></root>
                return fn:deep-equal($a, $b)
                """;
        assertTrue(queryResult(q));
    }

    @Test
    void typedNumericVsStringNotEqual() throws XMLDBException {
        // Per W3C XPath 3.1 deep-equal, xs:integer 1 is NOT deep-equal to "1".
        // Atomic comparison; streaming path does not apply.
        assertFalse(queryResult("fn:deep-equal(xs:integer(1), '1')"));
    }

    @Test
    void integerAndDoubleEqual() throws XMLDBException {
        // xs:integer 1 IS deep-equal to xs:double 1.0 per spec.
        assertTrue(queryResult("fn:deep-equal(xs:integer(1), xs:double(1.0))"));
    }

    @Test
    void nanEqualToNan() throws XMLDBException {
        // Special case: NaN is deep-equal to NaN even though NaN != NaN.
        assertTrue(queryResult("fn:deep-equal(xs:double('NaN'), xs:double('NaN'))"));
    }

    @Test
    void textVsCommentChildrenIgnored() throws XMLDBException {
        // compareContents (and the streaming comparator) skip comments and PIs.
        final String q = """
                let $a := <e>hello<!--ignore-->world</e>
                let $b := <e>hello<?pi data?>world</e>
                return fn:deep-equal($a, $b)
                """;
        assertTrue(queryResult(q));
    }

    @Test
    void differentChildOrderNotEqual() throws XMLDBException {
        // Element child order IS significant, unlike attribute order.
        final String q = """
                let $a := <root><a/><b/></root>
                let $b := <root><b/><a/></root>
                return fn:deep-equal($a, $b)
                """;
        assertFalse(queryResult(q));
    }

    @Test
    void differentNamespaceNotEqual() throws XMLDBException {
        final String q = """
                let $a := <e xmlns="urn:a"/>
                let $b := <e xmlns="urn:b"/>
                return fn:deep-equal($a, $b)
                """;
        assertFalse(queryResult(q));
    }

    @Test
    void emptySequencesEqual() throws XMLDBException {
        assertEquals(true, queryResult("fn:deep-equal((), ())"));
    }

    @Test
    void differentLengthSequencesNotEqual() throws XMLDBException {
        assertEquals(false, queryResult("fn:deep-equal((1, 2), (1, 2, 3))"));
    }
}
