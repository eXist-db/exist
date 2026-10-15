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

/**
 * Tests for duplicate node elimination in path expressions.
 *
 * Per XPath 3.1 §3.3.1.1, the path operator '/' must eliminate duplicate
 * nodes (by identity) and return results in document order when every
 * evaluation of E2 returns nodes. This must apply regardless of whether
 * E2 is an axis step, function call, or other PostfixExpr.
 *
 * @see <a href="https://www.w3.org/TR/xpath-31/#id-path-operator">XPath 3.1 §3.3.1.1</a>
 */
public class PathExprDedupTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeAll
    public static void storeTestDocument() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("""
                xmldb:store("/db", "path-dedup-test.xml", <r><a><c><d/></c></a><b><c><d/></c></b></r>)""");
    }

    @AfterAll
    public static void removeTestDocument() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("""
                xmldb:remove("/db", "path-dedup-test.xml")""");
    }

    private String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        return result.getResource(0).getContent().toString();
    }

    private String queryAll(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        final StringBuilder items = new StringBuilder();
        for (long i = 0; i < result.getSize(); i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append(result.getResource(i).getContent());
        }
        return items.toString();
    }

    /**
     * XQTS K2-Steps-31 (explicit expansion): function call in path where
     * multiple context items produce the same result node. The path operator
     * must deduplicate results per §3.3.1.1.
     */
    @Test
    public void functionCallInPathDedup() throws XMLDBException {
        final String result = query("""
                declare variable $root := <root><c/></root>;
                declare function local:function($arg) { $root[$arg] };
                count($root/descendant-or-self::node()/local:function(.))""");
        assertEquals("1", result);
    }

    /**
     * Simpler case: child::* / function that always returns the same node.
     */
    @Test
    public void functionReturnsConstantNodeDedup() throws XMLDBException {
        final String result = query("""
                declare variable $root := <root><a/><b/></root>;
                declare function local:getroot($x) { $root };
                count($root/*/local:getroot(.))""");
        assertEquals("1", result);
    }

    /**
     * Ensure for-loop results are NOT incorrectly deduplicated.
     * This is the scenario from the 2009 bug fix (SF #2880394).
     */
    @Test
    public void forLoopPreservesDuplicates() throws XMLDBException {
        final String result = query("""
                declare variable $root := <root/>;
                count(for $x in (1, 2, 3) return $root)""");
        assertEquals("3", result);
    }

    /**
     * Global variable with for-loop should preserve all results.
     */
    @Test
    public void globalVarForLoopPreservesDuplicates() throws XMLDBException {
        final String result = query("""
                declare variable $data := <item/>;
                count(for $i in 1 to 5 return $data)""");
        assertEquals("5", result);
    }

    /**
     * XQTS K2-Axes-48: path expression ending with integer literal.
     * Must not NPE when removeDuplicates is called on atomic results.
     */
    @Test
    public void pathEndingWithIntegerLiteral() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query("""
                declare variable $myVar := <e/>;
                $myVar/(<a/>, <b/>, <?d ?>, <!-- e-->, attribute name {}, document {()})/3""");
        assertEquals(6, (int) result.getSize());
        for (int i = 0; i < 6; i++) {
            assertEquals("3", result.getResource(i).getContent().toString());
        }
    }

    /**
     * XQTS K2-Axes-49: path expression ending with number() function.
     * Must not NPE when removeDuplicates is called on atomic results.
     */
    @Test
    public void pathEndingWithNumberFunction() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query("""
                declare variable $myVar := <e/>;
                $myVar/(<a/>, <b/>, <?d ?>, <!-- e-->, attribute name {}, document {()})/number()""");
        assertEquals(6, (int) result.getSize());
        for (int i = 0; i < 6; i++) {
            assertEquals("NaN", result.getResource(i).getContent().toString());
        }
    }

    /**
     * Path with axis step followed by function call — dedup should apply.
     */
    @Test
    public void axisStepThenFunctionCallDedup() throws XMLDBException {
        final String result = query("""
                declare variable $doc := <doc><a/><b/><c/></doc>;
                declare function local:parent($n) { $n/.. };
                count($doc/*/local:parent(.))""");
        assertEquals("1", result);
    }

    /**
     * E1 is not the result of a path operator: when E2 returns atomic values, the results follow E1's order.
     */
    @Test
    public void atomicStepKeepsOrderOfFirstStep() throws XMLDBException {
        final String result = query("""
                declare variable $d := <r><a/><b/></r>;
                string-join(($d/b, $d/a)/name(), ",")""");
        assertEquals("b,a", result, "the atomic results must follow the order of E1, not document order");
    }

    /**
     * E1 is not the result of a path operator: when E2 returns atomic values, each item of E1 produces a result.
     */
    @Test
    public void atomicStepKeepsDuplicatesOfFirstStep() throws XMLDBException {
        final String result = query("""
                declare variable $d := <r><a/><b/></r>;
                string-join(($d/a, $d/a)/name(), ",")""");
        assertEquals("a,a", result, "each item of E1 must provide the focus for E2 once");
    }

    /**
     * When E2 returns nodes, the result of the path is still deduplicated and in document order.
     */
    @Test
    public void nodeStepAfterUnorderedFirstStepDedup() throws XMLDBException {
        final String result = query("""
                declare variable $d := <r><a/><b/></r>;
                string-join(($d/b, $d/a, $d/b)/self::*/name(), ",")""");
        assertEquals("a,b", result, "the nodes must be deduplicated and in document order");
    }

    /**
     * Stored nodes: E1/E2 is the result of a path operator, so it is deduplicated and in document order before E3.
     */
    @Test
    public void storedNodeStepAfterUnorderedFirstStepDedup() throws XMLDBException {
        final String result = query("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                string-join(($r/b, $r/a, $r/b)/c/name(..), ",")""");
        assertEquals("a,b", result, "the nodes must be deduplicated and in document order");
    }

    /**
     * Stored nodes: when E2 returns atomic values, the results follow E1's order and keep its duplicates.
     */
    @Test
    public void storedAtomicStepKeepsOrderAndDuplicatesOfFirstStep() throws XMLDBException {
        final String result = query("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                string-join(($r/b, $r/a, $r/b)/name(), ",")""");
        assertEquals("b,a,b", result, "each item of E1 must provide the focus for E2 once, in E1's order");
    }

    /**
     * A step after the first sees the focus of E1 in E1's order: position() refers to E1, not to E1 in document
     * order.
     */
    @Test
    public void middleStepSeesPositionInOrderOfFirstStep() throws XMLDBException {
        final String first = query("""
                declare variable $d := <r><a><c/></a><b><c/></b></r>;
                string-join(($d/b, $d/a)/(if (position() = 1) then . else ())/name(), ",")""");
        final String second = query("""
                declare variable $d := <r><a><c/></a><b><c/></b></r>;
                string-join(($d/b, $d/a)/(if (position() = 2) then . else ())/name(), ",")""");
        assertEquals("b", first, "position() must follow the order of E1");
        assertEquals("a", second, "position() must follow the order of E1");
    }

    /**
     * Stored nodes: a step after the first sees the focus of E1 in E1's order.
     */
    @Test
    public void storedMiddleStepSeesPositionInOrderOfFirstStep() throws XMLDBException {
        final String first = query("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                string-join(($r/b, $r/a)/(if (position() = 1) then . else ())/name(), ",")""");
        final String second = query("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                string-join(($r/b, $r/a)/(if (position() = 2) then . else ())/name(), ",")""");
        assertEquals("b", first, "position() must follow the order of E1");
        assertEquals("a", second, "position() must follow the order of E1");
    }

    /**
     * Four steps ending in an atomic step: each intermediate result is deduplicated and in document order, so the
     * atomic results follow document order, not E1's order.
     */
    @Test
    public void fourStepPathEndingInAtomicStep() throws XMLDBException {
        final String result = query("""
                declare variable $d := <r><a><c><d/></c></a><b><c><d/></c></b></r>;
                string-join(($d/b, $d/a, $d/b)/c/d/name(../..), ",")""");
        assertEquals("a,b", result, "the results must follow document order and contain no duplicates");
    }

    /**
     * A path that is the body of a main module whose prolog declares a variable: E1 is still the first step of
     * the path, so its order and duplicates must be kept.
     */
    @Test
    public void atomicStepKeepsOrderOfFirstStepAfterPrologVariable() throws XMLDBException {
        final String result = queryAll("""
                declare variable $d := <r><a/><b/></r>;
                ($d/b, $d/a, $d/b)/name()""");
        assertEquals("b,a,b", result, "the atomic results must follow the order of E1 and keep its duplicates");
    }

    /**
     * Stored nodes: a path that is the body of a main module whose prolog declares a variable.
     */
    @Test
    public void storedAtomicStepKeepsOrderOfFirstStepAfterPrologVariable() throws XMLDBException {
        final String result = queryAll("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                ($r/b, $r/a, $r/b)/name()""");
        assertEquals("b,a,b", result, "the atomic results must follow the order of E1 and keep its duplicates");
    }

    /**
     * Stored nodes: four steps ending in an atomic step.
     */
    @Test
    public void storedFourStepPathEndingInAtomicStep() throws XMLDBException {
        final String result = query("""
                declare variable $r := doc("/db/path-dedup-test.xml")/r;
                string-join(($r/b, $r/a, $r/b)/c/d/name(../..), ",")""");
        assertEquals("a,b", result, "the results must follow document order and contain no duplicates");
    }
}
