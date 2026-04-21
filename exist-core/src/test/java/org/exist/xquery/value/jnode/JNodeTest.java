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

    @Test
    public void xpathDescendantAxis() throws Exception {
        final Sequence result = executeQuery(
                "xquery version '4.0'; " +
                "let $root := fn:jtree(map { 'inner': map { 'deep': 'value' } }) " +
                "return count($root/descendant::json-node())");
        // inner map child (1) + deep string child inside inner (1) = 2
        assertTrue(Integer.parseInt(result.getStringValue()) >= 2);
    }
}
