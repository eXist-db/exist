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
 * Tests for the lsp:definition() function.
 */
public class DefinitionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void emptyExpressionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:definition('', 0, 0))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void invalidExpressionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:definition('let $x :=', 0, 5))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionCallGoesToDefinition() throws XMLDBException {
        // Line 0: declare function local:greet() { 42 };
        // Line 1: local:greet()
        final String xquery =
                "declare function local:greet() { 42 };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 5)\n" +
                "return $def?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionDefinitionHasName() throws XMLDBException {
        final String xquery =
                "declare function local:greet() { 42 };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 5)\n" +
                "return $def?name");
        assertEquals("local:greet#0", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionDefinitionPointsToDeclaration() throws XMLDBException {
        // The declaration is on line 0
        final String xquery =
                "declare function local:greet() { 42 };&#10;local:greet()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 5)\n" +
                "return $def?line");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableReferenceGoesToDeclaration() throws XMLDBException {
        // Line 0: declare variable $local:x := 42;
        // Line 1: $local:x
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 3)\n" +
                "return $def?kind");
        assertEquals("variable", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableDefinitionHasName() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 3)\n" +
                "return $def?name");
        assertEquals("$local:x", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableDefinitionPointsToDeclaration() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 42;&#10;$local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 3)\n" +
                "return $def?line");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void builtinFunctionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:definition('fn:count((1,2,3))', 0, 3))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void noSymbolAtPositionReturnsEmpty() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "empty(lsp:definition('1 + 2', 0, 2))");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void withModuleLoadPath() throws XMLDBException {
        final String xquery =
                "declare function local:foo() { 42 };&#10;local:foo()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $def := lsp:definition('" + xquery + "', 1, 5, '/db')\n" +
                "return $def?kind");
        assertEquals("function", result.getResource(0).getContent().toString());
    }
}
