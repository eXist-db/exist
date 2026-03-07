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
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

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

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        return result.getResource(0).getContent().toString();
    }

    /**
     * XQTS K2-Steps-31 (explicit expansion): function call in path where
     * multiple context items produce the same result node. The path operator
     * must deduplicate results per §3.3.1.1.
     */
    @Test
    public void functionCallInPathDedup() throws XMLDBException {
        final String result = query(
                "declare variable $root := <root><c/></root>;\n" +
                "declare function local:function($arg) { $root[$arg] };\n" +
                "count($root/descendant-or-self::node()/local:function(.))");
        assertEquals("1", result);
    }

    /**
     * Simpler case: child::* / function that always returns the same node.
     */
    @Test
    public void functionReturnsConstantNodeDedup() throws XMLDBException {
        final String result = query(
                "declare variable $root := <root><a/><b/></root>;\n" +
                "declare function local:getroot($x) { $root };\n" +
                "count($root/*/local:getroot(.))");
        assertEquals("1", result);
    }

    /**
     * Ensure for-loop results are NOT incorrectly deduplicated.
     * This is the scenario from the 2009 bug fix (SF #2880394).
     */
    @Test
    public void forLoopPreservesDuplicates() throws XMLDBException {
        final String result = query(
                "declare variable $root := <root/>;\n" +
                "count(for $x in (1, 2, 3) return $root)");
        assertEquals("3", result);
    }

    /**
     * Global variable with for-loop should preserve all results.
     */
    @Test
    public void globalVarForLoopPreservesDuplicates() throws XMLDBException {
        final String result = query(
                "declare variable $data := <item/>;\n" +
                "count(for $i in 1 to 5 return $data)");
        assertEquals("5", result);
    }

    /**
     * XQTS K2-Axes-48: path expression ending with integer literal.
     * Must not NPE when removeDuplicates is called on atomic results.
     */
    @Test
    public void pathEndingWithIntegerLiteral() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(
                "declare variable $myVar := <e/>;\n" +
                "$myVar/(<a/>, <b/>, <?d ?>, <!-- e-->, attribute name {}, document {()})/3");
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
        final ResourceSet result = xqs.query(
                "declare variable $myVar := <e/>;\n" +
                "$myVar/(<a/>, <b/>, <?d ?>, <!-- e-->, attribute name {}, document {()})/number()");
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
        final String result = query(
                "declare variable $doc := <doc><a/><b/><c/></doc>;\n" +
                "declare function local:parent($n) { $n/.. };\n" +
                "count($doc/*/local:parent(.))");
        assertEquals("1", result);
    }
}
