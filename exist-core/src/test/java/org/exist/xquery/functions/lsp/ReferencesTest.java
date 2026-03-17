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
 * Tests for the lsp:references() function.
 */
public class ReferencesTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void emptyExpressionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:references('', 0, 0))");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionCallFindsAllReferences() throws XMLDBException {
        // local:greet is declared on line 0 and called on lines 1 and 2
        final String xquery =
                "declare function local:greet() { 42 };&#10;" +
                "local:greet(),&#10;" +
                "local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $refs := lsp:references('" + xquery + "', 1, 5)\n" +
                "return array:size($refs)");
        // Should find: declaration + 2 calls = 3
        final int count = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("expected at least 2 references, got " + count, count >= 2);
    }

    @Test
    public void functionReferenceHasKind() throws XMLDBException {
        final String xquery =
                "declare function local:greet() { 42 };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $refs := lsp:references('" + xquery + "', 1, 5)\n" +
                "return $refs(1)?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableReferenceFindsAllReferences() throws XMLDBException {
        // $local:x is declared on line 0 and referenced on lines 1 and 2
        final String xquery =
                "declare variable $local:x := 42;&#10;" +
                "$local:x + 1,&#10;" +
                "$local:x + 2";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $refs := lsp:references('" + xquery + "', 1, 3)\n" +
                "return array:size($refs)");
        final int count = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("expected at least 2 references, got " + count, count >= 2);
    }

    @Test
    public void variableReferenceHasKind() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $refs := lsp:references('" + xquery + "', 1, 3)\n" +
                "return $refs(1)?kind");
        assertEquals("variable", result.getResource(0).getContent().toString());
    }

    @Test
    public void noSymbolReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:references('1 + 2', 0, 2))");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void withModuleLoadPath() throws XMLDBException {
        final String xquery =
                "declare function local:foo() { 42 };&#10;" +
                "local:foo(),&#10;" +
                "local:foo()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $refs := lsp:references('" + xquery + "', 1, 5, '/db')\n" +
                "return array:size($refs)");
        final int count = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("expected at least 2 references, got " + count, count >= 2);
    }
}
