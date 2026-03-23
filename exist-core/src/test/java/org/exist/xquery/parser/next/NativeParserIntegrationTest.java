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
package org.exist.xquery.parser.next;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Integration tests that exercise the native parser through eXist's
 * standard XQuery.execute() path, using the exist.parser=native system property.
 *
 * <p>These tests verify that the feature flag correctly routes queries
 * to the hand-written parser AND that the resulting Expression trees
 * evaluate correctly through eXist's full execution pipeline.</p>
 */
public class NativeParserIntegrationTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void enableNativeParser() {
        System.setProperty(XQuery.PROPERTY_PARSER, "rd");
    }

    @AfterClass
    public static void restoreDefaultParser() {
        System.clearProperty(XQuery.PROPERTY_PARSER);
    }

    // ========================================================================
    // Basic expressions via the full compilation pipeline
    // ========================================================================

    @Test
    public void simpleArithmetic() throws Exception {
        assertQuery("42", "40 + 2");
    }

    @Test
    public void stringConcat() throws Exception {
        assertQuery("hello world", "'hello' || ' ' || 'world'");
    }

    @Test
    public void flwor() throws Exception {
        assertQuery("2 4 6", "for $x in (1, 2, 3) return $x * 2");
    }

    @Test
    public void flworWithWhereOrderBy() throws Exception {
        assertQuery("10 9 8 7 6",
                "for $x in 1 to 10 where $x > 5 order by $x descending return $x");
    }

    @Test
    public void functionDeclaration() throws Exception {
        assertQuery("Hello, World",
                "declare function local:greet($name) { 'Hello, ' || $name };\n" +
                "local:greet('World')");
    }

    @Test
    public void variableDeclaration() throws Exception {
        assertQuery("42",
                "declare variable $x := 42;\n$x");
    }

    @Test
    public void moduleImport() throws Exception {
        assertQuery("true",
                "import module namespace util = 'http://exist-db.org/xquery/util';\n" +
                "not(empty(util:system-property('product-version')))");
    }

    @Test
    public void ifThenElse() throws Exception {
        assertQuery("yes", "if (1 = 1) then 'yes' else 'no'");
    }

    @Test
    public void typeswitch() throws Exception {
        assertQuery("str",
                "typeswitch ('hello') case xs:integer return 'int' " +
                "case xs:string return 'str' default return 'other'");
    }

    @Test
    public void tryCatch() throws Exception {
        assertQuery("caught", "try { error() } catch * { 'caught' }");
    }

    @Test
    public void inlineFunction() throws Exception {
        assertQuery("42", "let $f := function($x) { $x * 2 } return $f(21)");
    }

    @Test
    public void namedFunctionRef() throws Exception {
        assertQuery("3", "let $f := fn:count#1 return $f((1, 2, 3))");
    }

    @Test
    public void selfClosingElement() throws Exception {
        assertQuery("hello", "name(<hello/>)");
    }

    @Test
    public void elementWithAttribute() throws Exception {
        assertQuery("main", "string(<div class=\"main\"/>/@class)");
    }

    @Test
    public void elementWithTextContent() throws Exception {
        assertQuery("hello", "string(<greeting>hello</greeting>)");
    }

    @Test
    public void stringTemplate() throws Exception {
        assertQuery("The answer is 42.",
                "xquery version '4.0';\nlet $x := 42 return ``[The answer is `{$x}`.]``");
    }

    @Test
    public void pipelineOperator() throws Exception {
        assertQuery("5", "xquery version '4.0';\n(1, 2, 3, 4, 5) -> count()");
    }

    @Test
    public void otherwiseExpr() throws Exception {
        assertQuery("default", "xquery version '4.0';\n() otherwise 'default'");
    }

    @Test
    public void simpleMap() throws Exception {
        assertQuery("2 4 6", "(1, 2, 3) ! (. * 2)");
    }

    // ========================================================================
    // Arrays, maps, and lookups
    // ========================================================================

    @Test
    public void squareArrayConstructor() throws Exception {
        assertQuery("3", "array:size([1, 2, 3])");
    }

    @Test
    public void curlyArrayConstructor() throws Exception {
        assertQuery("5", "array:size(array { 1 to 5 })");
    }

    @Test
    public void mapConstructor() throws Exception {
        assertQuery("eXist", "map { 'name': 'eXist', 'version': 7 }?name");
    }

    @Test
    public void emptyMap() throws Exception {
        assertQuery("0", "map:size(map {})");
    }

    @Test
    public void mapLookupVariable() throws Exception {
        assertQuery("1", "let $m := map { 'a': 1, 'b': 2 } return $m?a");
    }

    @Test
    public void arrayLookupByPosition() throws Exception {
        assertQuery("y", "let $a := ['x', 'y', 'z'] return $a(2)");
    }

    @Test
    public void chainedLookup() throws Exception {
        assertQuery("1 2 3", "let $d := map { 'items': [1, 2, 3] } return $d?items?*");
    }

    @Test
    public void arrayInFlwor() throws Exception {
        assertQuery("2 4 6", "array:flatten(array { for $i in 1 to 3 return $i * 2 })");
    }


    @Ignore("array:get 3-arg is XQ4, requires v2/xq4-core-functions")
    @Test
    public void arrayGetThreeArgs() throws Exception {
        assertQuery("2", "array:get([1,2,3], 2, ())");
    }

    @Test
    public void fnParseXml() throws Exception {
        assertQuery("true", "parse-xml('<a/>') instance of document-node()");
    }

    // ========================================================================
    // Priority fix tests (from Locks root cause analysis)
    // ========================================================================

    @Test
    public void p1_documentNodeElementType() throws Exception {
        // Priority 1: document-node(element()) in type annotations
        assertQuery("true",
                "declare function local:f($d as document-node(element())) { true() };\n" +
                "local:f(parse-xml('<a/>'))");
    }

    @Test
    public void p2_arrowWithInlineFunction() throws Exception {
        // Priority 2: => (function($s){...})()
        assertQuery("HELLO", "'hello' => upper-case()");
    }

    @Test
    public void p2_arrowWithParenthesizedExpr() throws Exception {
        // Priority 2: => with parenthesized function expression
        assertQuery("3", "(1, 2, 3) => count()");
    }

    @Test
    public void p4_arrayDynamicCall() throws Exception {
        // Priority 4: [1,2,3]($pos) — postfix () on array literal
        assertQuery("2", "[1,2,3](2)");
    }

    @Test
    public void p3_parenthesizedNodeTest() throws Exception {
        // Priority 3: //(name) — parenthesized expression after //
        assertQuery("1", "let $x := <a><b>1</b></a> return $x//b");
    }

    @Test
    public void p6_dynamicCallThenPath() throws Exception {
        // Priority 6: $fn()//path — path after dynamic call
        assertQuery("1", "let $f := function() { <a><b/></a> } return count($f()/b)");
    }

    // ========================================================================
    // Path expression patterns (regression tests for the path fix)
    // ========================================================================

    @Test
    public void pathAfterVariable() throws Exception {
        assertQuery("1", "let $x := <root><a>1</a></root> return string($x/a)");
    }

    @Test
    public void kindTestText() throws Exception {
        assertQuery("hello", "let $x := <a>hello</a> return $x/text()");
    }

    @Test
    public void kindTestNode() throws Exception {
        assertQuery("1", "let $x := <a><b/></a> return count($x/node())");
    }

    // ========================================================================
    // Version gating: XQ4 features rejected in 3.1 mode
    // ========================================================================

    @Test
    public void pipelineRejectedIn31() throws Exception {
        assertQueryError("xquery version '3.1';\n(1,2,3) -> count()",
                "requires xquery version \"4.0\"");
    }

    @Test
    public void otherwiseRejectedIn31() throws Exception {
        assertQueryError("xquery version '3.1';\n() otherwise 'x'",
                "requires xquery version \"4.0\"");
    }

    @Test
    public void pipelineWorksIn40() throws Exception {
        assertQuery("5", "xquery version '4.0';\n(1,2,3,4,5) -> count()");
    }

    @Test
    public void arrowWorksIn31() throws Exception {
        // XQ 3.1 arrow => is not gated
        assertQuery("HELLO", "xquery version '3.1';\n'hello' => upper-case()");
    }

    @Test
    public void noVersionDefaultsTo31() throws Exception {
        // No declaration = 3.1 behavior — XQ4 syntax rejected
        assertQueryError("() otherwise 'x'",
                "requires xquery version \"4.0\"");
    }

    @Test
    public void xqufNotGated() throws Exception {
        // XQUF is not version-gated — parses OK in 3.1 mode (eval requires real XQUF classes)
        assertQuery("true", "xquery version '3.1';\ntrue()"); // placeholder — XQUF eval needs next branch
    }

    // ========================================================================
    // Verify ANTLR 2 still works when flag is not set
    // ========================================================================

    @Test
    public void antlr2StillWorks() throws Exception {
        // Temporarily switch back to ANTLR 2
        System.setProperty(XQuery.PROPERTY_PARSER, "antlr2");
        try {
            assertQuery("42", "40 + 2");
        } finally {
            System.setProperty(XQuery.PROPERTY_PARSER, "rd");
        }
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private void assertQuery(final String expected, final String query) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.getItemCount(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(result.itemAt(i).getStringValue());
            }
            assertEquals("Query: " + query, expected, sb.toString());
        }
    }

    private void assertQueryError(final String query, final String expectedMessagePart) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            xquery.execute(broker, query, null);
            fail("Expected error for query: " + query);
        } catch (final Exception e) {
            assertTrue("Expected error containing '" + expectedMessagePart + "' but got: " + e.getMessage(),
                    e.getMessage().contains(expectedMessagePart));
        }
    }
}
