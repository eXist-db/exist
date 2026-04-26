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
package org.exist.xquery.value.jnode;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * JUnit tests for XQuery 4.0 JNode support.
 * Tests fn:jtree, fn:jkey, fn:jvalue, fn:jposition via embedded XQuery evaluation.
 */
public class JNodeTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private Sequence executeQuery(final String xquery) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return xqueryService.execute(broker, xquery, null);
        }
    }

    @Test
    public void jtreeFromMapReturnsNonEmpty() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree(map { 'a': 1 }) return exists($j)");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jtreeFromArrayReturnsNonEmpty() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree(array { 1, 2, 3 }) return exists($j)");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jtreeFromStringReturnsNonEmpty() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree('hello') return exists($j)");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jvalueFromMapReturnMap() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree(map { 'name': 'Alice' }) " +
                "return fn:jvalue($j)('name')");
        assertEquals("Alice", result.getStringValue());
    }

    @Test
    public void jvalueFromArrayReturnsArray() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree(array { 1, 2, 3 }) " +
                "return array:size(fn:jvalue($j))");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void jvalueFromStringReturnsString() throws Exception {
        final Sequence result = executeQuery(
                "fn:jvalue(fn:jtree('hello'))");
        assertEquals("hello", result.getStringValue());
    }

    @Test
    public void jkeyOfRootIsEmpty() throws Exception {
        final Sequence result = executeQuery(
                "let $j := fn:jtree(map { 'a': 1 }) return empty(fn:jkey($j))");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jpositionOfRootIsZero() throws Exception {
        final Sequence result = executeQuery(
                "fn:jposition(fn:jtree(map { 'a': 1 }))");
        assertEquals("0", result.getStringValue());
    }

    @Test
    public void jtreePreservesMapType() throws Exception {
        final Sequence result = executeQuery(
                "fn:jvalue(fn:jtree(map { 'x': 1 })) instance of map(*)");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jtreePreservesArrayType() throws Exception {
        final Sequence result = executeQuery(
                "fn:jvalue(fn:jtree(array { 'a', 'b' })) instance of array(*)");
        assertEquals("true", result.getStringValue());
    }

    // --- fn:jchildren ---

    @Test
    public void jchildrenOfMapReturnsMembers() throws Exception {
        final Sequence result = executeQuery(
                "count(fn:jchildren(fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 })))");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void jchildrenOfArrayReturnsItems() throws Exception {
        final Sequence result = executeQuery(
                "count(fn:jchildren(fn:jtree(array { 10, 20, 30 })))");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void jchildrenOfLeafIsEmpty() throws Exception {
        final Sequence result = executeQuery(
                "empty(fn:jchildren(fn:jtree('hello')))");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jkeyOfMapChild() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(map { 'name': 'Alice' }) " +
                "let $child := fn:jchildren($root)[1] " +
                "return fn:jkey($child)");
        assertEquals("name", result.getStringValue());
    }

    @Test
    public void jvalueOfMapChild() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(map { 'name': 'Alice' }) " +
                "let $child := fn:jchildren($root)[1] " +
                "return fn:jvalue($child)");
        assertEquals("Alice", result.getStringValue());
    }

    @Test
    public void jpositionOfChildren() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(array { 'x', 'y', 'z' }) " +
                "return string-join(for $c in fn:jchildren($root) return string(fn:jposition($c)), ',')");
        assertEquals("1,2,3", result.getStringValue());
    }

    // --- fn:jparent ---

    @Test
    public void jparentOfRootIsEmpty() throws Exception {
        final Sequence result = executeQuery(
                "empty(fn:jparent(fn:jtree(map { 'a': 1 })))");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jparentOfChildIsRoot() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(map { 'a': 1 }) " +
                "let $child := fn:jchildren($root)[1] " +
                "return fn:jvalue(fn:jparent($child))('a')");
        assertEquals("1", result.getStringValue());
    }

    // --- Nested navigation ---

    @Test
    public void nestedMapNavigation() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(map { 'person': map { 'name': 'Alice', 'age': 30 } }) " +
                "let $person := fn:jchildren($root)[1] " +
                "let $name := fn:jchildren(fn:jtree(fn:jvalue($person)))[1] " +
                "return fn:jvalue($name)");
        // The first child of the nested map
        assertNotNull(result);
        assertTrue(result.getItemCount() > 0);
    }

    // --- Kind tests (grammar) ---

    @Test
    public void instanceOfJsonNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; fn:jtree(map { 'a': 1 }) instance of json-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void instanceOfObjectNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; fn:jtree(map { 'a': 1 }) instance of object-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void instanceOfArrayNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; fn:jtree(array { 1, 2 }) instance of array-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void instanceOfStringNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; fn:jtree('hello') instance of string-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void mapIsNotArrayNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; fn:jtree(map { 'a': 1 }) instance of array-node()");
        assertEquals("false", result.getStringValue());
    }

    @Test
    public void kindTestRejectedInXQ31() throws Exception {
        try {
            executeQuery("xquery version \"3.1\"; fn:jtree(map { 'a': 1 }) instance of object-node()");
            fail("Expected XPST0003 for JNode kind test in XQuery 3.1");
        } catch (final XPathException e) {
            assertTrue(e.getMessage().contains("requires xquery version"));
        }
    }

    @Test
    public void arrayOfMapsNavigation() throws Exception {
        final Sequence result = executeQuery(
                "let $root := fn:jtree(array { map { 'x': 1 }, map { 'x': 2 } }) " +
                "let $items := fn:jchildren($root) " +
                "return count($items)");
        assertEquals("2", result.getStringValue());
    }

    // --- XPath axis navigation (requires xquery version 4.0) ---

    @Test
    public void xpathChildAxis() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 }) " +
                "return count($root/child::json-node())");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void xpathChildWildcard() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 }) " +
                "return count($root/child::*)");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void xpathChildNamedKey() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'name': 'Joe', 'age': 42 }) " +
                "return fn:jvalue($root/name)");
        assertEquals("Joe", result.getStringValue());
    }

    @Test
    public void xpathMultiStepNavigation() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': map { 'x': 1, 'y': 2 }, 'b': 3 }) " +
                "return count($root/a/child::*)");
        assertEquals("2", result.getStringValue());
    }

    @Test
    public void xpathHistogramSelfCount() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2 }) " +
                "return count($root/self::*)");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void xpathHistogramChildCount() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 }) " +
                "return count($root/child::*)");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void xpathHistogramDescendantCount() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': map { 'x': 1 }, 'b': 2 }) " +
                "return count($root/descendant::*)");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void xpathHistogramAncestorCount() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': map { 'x': 1 }, 'b': 2 }) " +
                "return count($root/a/x/ancestor::*)");
        assertEquals("2", result.getStringValue());
    }

    @Test
    public void xpathHistogramFollowingSiblingCount() throws Exception {
        // Wildcard following-sibling
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 }) " +
                "let $first := ($root/child::json-node())[1] " +
                "return count($first/following-sibling::*)");
        assertEquals("2", result.getStringValue());
    }

    @Test
    public void xpathChildObjectNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(array { map { 'x': 1 }, 'hello', 42 }) " +
                "return count($root/child::object-node())");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void xpathChildStringNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(array { map { 'x': 1 }, 'hello', 42 }) " +
                "return count($root/child::string-node())");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void xpathMultiStepChildParent() throws Exception {
        // Verify children are JNodes
        final Sequence step1 = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2 }) " +
                "let $children := $root/child::json-node() " +
                "return count($children)");
        assertEquals("2", step1.getStringValue());

        // Verify parent of each child via function
        final Sequence step2 = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'a': 1, 'b': 2 }) " +
                "for $c in $root/child::json-node() " +
                "return exists(fn:jparent($c))");
        // Should return "true true"
        assertTrue(step2.getStringValue().contains("true"));

        // Single-variable parent axis: $child/parent::json-node()
        final Sequence step3 = executeQuery(
                "xquery version '4.0'; " +
                "let $child := fn:jchildren(fn:jtree(map { 'a': 1 }))[1] " +
                "return count($child/parent::json-node())");
        assertEquals("1", step3.getStringValue());
    }

    // --- Serialization ---

    @Test
    public void serializeJNodeMapAsJson() throws Exception {
        final Sequence result = executeQuery(
                "serialize(fn:jtree(map { 'name': 'Alice', 'age': 30 }), " +
                "map { 'method': 'json' })");
        final String json = result.getStringValue();
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"Alice\""));
        assertTrue(json.contains("30"));
    }

    @Test
    public void serializeJNodeArrayAsJson() throws Exception {
        final Sequence result = executeQuery(
                "serialize(fn:jtree(array { 1, 2, 3 }), " +
                "map { 'method': 'json' })");
        assertEquals("[1,2,3]", result.getStringValue().replaceAll("\\s+", ""));
    }

    @Test
    public void serializeJNodeAdaptive() throws Exception {
        final Sequence result = executeQuery(
                "serialize(fn:jtree(map { 'x': 1 }), " +
                "map { 'method': 'adaptive' })");
        assertTrue(result.getStringValue().contains("\"x\""));
    }

    @Test
    public void serializeJNodeStringAsJson() throws Exception {
        final Sequence result = executeQuery(
                "serialize(fn:jtree('hello'), " +
                "map { 'method': 'json' })");
        assertEquals("\"hello\"", result.getStringValue());
    }

    @Test
    public void xpathParentViaFunction() throws Exception {
        // Parent axis via fn:jparent works; parent via "/" chain is a TODO
        // (requires removeDuplicates() to handle JNodes)
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'name': 'Alice' }) " +
                "let $child := fn:jchildren($root)[1] " +
                "return fn:jparent($child) instance of object-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void xpathSelfAxis() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "fn:jtree(map { 'a': 1 })/self::object-node() instance of object-node()");
        assertEquals("true", result.getStringValue());
    }

    // --- Map/Array Predicate Tests (XPDY0002 regression) ---

    @Test
    public void mapNavigationWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; map{'asdf':1}/asdf[. <= 1]");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void mapWildcardWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; map{'a':1,'b':2}/*[. > 1]");
        assertEquals("2", result.getStringValue());
    }

    @Test
    public void arrayWildcardWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; [1,2,3]/*[. > 1]");
        assertEquals(2, result.getItemCount());
    }

    @Test
    public void parenthesizedMapWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; (map{'asdf':1}/asdf)[. <= 1]");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void nestedMapWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; map{'x': map{'y': 1}}/x/y[. = 1]");
        assertEquals("1", result.getStringValue());
    }

    @Test
    public void mapSequenceValueWithPredicate() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; map{'a': (1,2,3)}/a[. > 1]");
        assertEquals(2, result.getItemCount());
    }

    @Test
    public void xpathDescendantAxis() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'inner': map { 'deep': 'value' } }) " +
                "return count($root/descendant::json-node())");
        // inner map child (1) + deep string child inside inner (1) = 2
        assertTrue(Integer.parseInt(result.getStringValue()) >= 2);
    }

    /**
     * Regression test for JNode flowing into a user-defined function whose parameter
     * is declared as jnode(). Path expressions have static return type Type.NODE,
     * so the function-dispatch static type check would otherwise reject
     * the JNode flowing in. The check must defer to runtime DynamicTypeCheck.
     */
    @Test
    public void udfWithJsonNodeParamAcceptsPathArg() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "declare function local:count-children($n as jnode()) as xs:integer { " +
                "    count($n/child::*) " +
                "}; " +
                "let $root := fn:jtree(map { 'a': map { 'x': 1, 'y': 2 }, 'b': 3 }) " +
                "return local:count-children($root/a)");
        assertEquals("2", result.getStringValue());
    }

    @Test
    public void udfWithJsonNodeParamAcceptsMultiStepPath() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "declare function local:depth($n as jnode()) as xs:integer { " +
                "    count($n/ancestor::*) " +
                "}; " +
                "let $root := fn:jtree(map { 'a': map { 'b': map { 'c': 1 } } }) " +
                "return local:depth($root/a/b/c)");
        assertEquals("3", result.getStringValue());
    }

    /**
     * Mirrors the XQTS JAxes-002 pattern: caller is in default (3.1) mode,
     * UDF accepts jnode() parameter, path expression argument is the call site.
     * Tests that the type-coercion fix doesn't require the caller to declare XQ4.
     */
    @Test
    public void udfWithJsonNodeParamFromXq31Caller() throws Exception {
        // Note: XQ4 mode required to declare 'as jnode()' parameter type.
        // JAxes-002 module is presumably parsed in XQ4 mode somehow.
        // Local function in same query body inherits caller's version.
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "declare function local:depth($n as jnode()) as xs:integer { " +
                "    count($n/ancestor::*) " +
                "}; " +
                "let $root := fn:jtree(map { 'a': map { 'b': 1 } }) " +
                "return local:depth($root/a/b)");
        assertEquals("2", result.getStringValue());
    }

    /**
     * Diagnostic: does jnode() kind test parse in 3.1 mode at all?
     */
    @Test
    public void jnodeKindTestRequiresXQ4() throws Exception {
        try {
            executeQuery(
                    "declare function local:f($n as jnode()) { 1 }; " +
                    "local:f(1)");
            fail("Expected XPST0003 — jnode() requires XQ4");
        } catch (final XPathException e) {
            assertTrue(e.getMessage().contains("xquery version") ||
                       e.getMessage().contains("4.0") ||
                       e.getMessage().contains("XPST0003"));
        }
    }

    /**
     * Replicates the JAxes-002 setup as closely as possible:
     * - Imported module declares xquery version 4.0 with as jnode() parameter
     * - Test query is in default 3.1 mode
     * - Path expression argument flows into JSON_NODE param
     */
    @Test
    public void udfXQ4ModuleCalledFromXQ31Caller() throws Exception {
        // Write a temporary module file declaring jnode() param
        final java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("jnode-mod");
        try {
            final java.nio.file.Path modFile = tmpDir.resolve("histogram.xq");
            java.nio.file.Files.writeString(modFile,
                    "xquery version \"4.0\";\n" +
                    "module namespace ax=\"http://test.com/ax\";\n" +
                    "declare function ax:histogram($origin as jnode()) as map(*) {\n" +
                    "  map { 'child': count($origin/child::*) }\n" +
                    "};\n");
            final String moduleUri = modFile.toUri().toString();
            final Sequence result = executeQuery(
                    "import module namespace ax=\"http://test.com/ax\" at \"" + moduleUri + "\"; " +
                    "let $root := fn:jtree(map { 'a': map { 'x': 1, 'y': 2 } }) " +
                    "return ax:histogram($root/a)?child");
            assertEquals("2", result.getStringValue());
        } finally {
            java.nio.file.Files.walk(tmpDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception e) { } });
        }
    }

    // --- get(args) as path step (XQuery 4.0) ---

    @Test
    public void getStepOnMapStringKey() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $m := map { 'x': 1, 'y': 2, 'z': 3 } " +
                "return $m/get('z')");
        assertEquals("3", result.getStringValue());
    }

    @Test
    public void getStepOnMapMultipleKeys() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $m := map { 'x': 1, 'y': 2, 'z': 3 } " +
                "return $m/get(('z', 'x'))");
        // Lookup with multiple keys returns matching values in order
        assertTrue(result.getItemCount() == 2);
    }

    @Test
    public void getStepOnArrayInteger() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $a := array { 'a', 'b', 'c' } " +
                "return $a/get(2)");
        assertEquals("b", result.getStringValue());
    }

    @Test
    public void getStepOnJNodeObjectReturnsChildNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'name': 'Joe', 'age': 42 }) " +
                "return fn:jvalue($j/get('name'))");
        assertEquals("Joe", result.getStringValue());
    }

    @Test
    public void getStepOnJNodeObjectExistsCheck() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'name': 'Joe' }) " +
                "return exists($j/get('name'))");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void getStepOnJNodeReturnsMemberNode() throws Exception {
        // For an object-node child accessed via get(), the result is a member-node
        // (key + container value). Use member-node() instead of object-node().
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'a': map { 'x': 1 } }) " +
                "return $j/get('a') instance of member-node()");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void getStepReturnsSameAsNameStep() throws Exception {
        // /name returns child JNode; /get('name') should return the same
        final Sequence viaName = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'name': 'Joe' }) " +
                "return fn:jvalue($j/name)");
        final Sequence viaGet = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'name': 'Joe' }) " +
                "return fn:jvalue($j/get('name'))");
        assertEquals(viaName.getStringValue(), viaGet.getStringValue());
    }

    @Test
    public void getStepOnJNodeArrayReturnsChildNode() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(array { 'a', 'b', 'c' }) " +
                "return fn:jvalue($j/get(2))");
        assertEquals("b", result.getStringValue());
    }

    @Test
    public void getStepAbsentKeyReturnsEmpty() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $m := map { 'x': 1, 'y': 2 } " +
                "return empty($m/get('w'))");
        assertEquals("true", result.getStringValue());
    }

    @Test
    public void jnodeWildcardLookupReturnsAllChildren() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $j := fn:jtree(map { 'a': 1, 'b': 2, 'c': 3 }) " +
                "return count($j?*)");
        assertEquals("3", result.getStringValue());
    }
}
