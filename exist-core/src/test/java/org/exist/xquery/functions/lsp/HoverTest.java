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
package org.exist.xquery.functions.lsp;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the lsp:hover() function.
 */
public class HoverTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void emptyExpressionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:hover('', 0, 0))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void invalidExpressionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:hover('let $x :=', 0, 5))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnBuiltinFunctionCall() throws XMLDBException {
        // fn:count starts at column 0
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('fn:count((1,2,3))', 0, 3)\n" +
                "return $hover?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnBuiltinFunctionHasContents() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('fn:count((1,2,3))', 0, 3)\n" +
                "return string-length($hover?contents) > 0");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnBuiltinFunctionContainsName() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('fn:count((1,2,3))', 0, 3)\n" +
                "return contains($hover?contents, 'count')");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnUserFunctionCall() throws XMLDBException {
        // Use a multiline expression so the call is on a known line
        // Line 0: declare function local:greet() as xs:string { 'hi' };
        // Line 1: local:greet()
        final String xquery =
                "declare function local:greet() as xs:string { ''hi'' };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('" + xquery + "', 1, 5)\n" +
                "return $hover?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnUserFunctionContainsSig() throws XMLDBException {
        final String xquery =
                "declare function local:greet() as xs:string { ''hi'' };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('" + xquery + "', 1, 5)\n" +
                "return contains($hover?contents, 'greet')");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnVariableReference() throws XMLDBException {
        // Line 0: declare variable $local:x := 42;
        // Line 1: $local:x
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('" + xquery + "', 1, 3)\n" +
                "return $hover?kind");
        assertEquals("variable", result.getResource(0).getContent().toString());
    }

    @Test
    public void hoverOnVariableHasName() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('" + xquery + "', 1, 3)\n" +
                "return contains($hover?contents, '$local:x')");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void noSymbolAtPositionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:hover('1 + 2', 0, 2))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void withModuleLoadPath() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $hover := lsp:hover('fn:count((1,2,3))', 0, 3, '/db')\n" +
                "return $hover?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }
}
