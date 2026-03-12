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
 * Tests for the lsp:symbols() function.
 */
public class SymbolsTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void emptyExpressionReturnsEmptyArray() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:symbols(''))");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void invalidExpressionReturnsEmptyArray() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:symbols('let $x :='))");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionDeclarationReturnsSymbol() throws XMLDBException {
        final String xquery =
                "declare function local:hello($name as xs:string) as xs:string { " +
                "  ''Hello, '' || $name " +
                "}; local:hello(''world'')";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return array:size($symbols)");
        assertEquals("1", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionSymbolHasCorrectName() throws XMLDBException {
        final String xquery =
                "declare function local:greet($name as xs:string) as xs:string { " +
                "  ''Hello, '' || $name " +
                "}; local:greet(''world'')";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?name");
        assertEquals("local:greet#1", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionSymbolHasKind12() throws XMLDBException {
        final String xquery =
                "declare function local:foo() { () }; local:foo()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?kind");
        assertEquals("LSP SymbolKind.Function = 12",
                "12", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionSymbolHasPositiveLine() throws XMLDBException {
        final String xquery =
                "declare function local:foo() { () }; local:foo()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?line");
        // Line should be 0 (first line, 0-based)
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void functionSymbolDetailIncludesReturnType() throws XMLDBException {
        final String xquery =
                "declare function local:add($a as xs:integer, $b as xs:integer) as xs:integer { " +
                "  $a + $b " +
                "}; local:add(1, 2)";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?detail");
        final String detail = result.getResource(0).getContent().toString();
        assertTrue("Detail should contain parameter names: " + detail,
                detail.contains("$a") && detail.contains("$b"));
        assertTrue("Detail should contain return type: " + detail,
                detail.contains("xs:integer"));
    }

    @Test
    public void variableDeclarationReturnsSymbol() throws XMLDBException {
        final String xquery =
                "declare variable $local:greeting := ''hello''; $local:greeting";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return array:size($symbols)");
        assertEquals("1", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableSymbolHasCorrectName() throws XMLDBException {
        final String xquery =
                "declare variable $local:greeting := ''hello''; $local:greeting";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?name");
        assertEquals("$local:greeting", result.getResource(0).getContent().toString());
    }

    @Test
    public void variableSymbolHasKind13() throws XMLDBException {
        final String xquery =
                "declare variable $local:greeting := ''hello''; $local:greeting";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?kind");
        assertEquals("LSP SymbolKind.Variable = 13",
                "13", result.getResource(0).getContent().toString());
    }

    @Test
    public void typedVariableHasDetailInfo() throws XMLDBException {
        final String xquery =
                "declare variable $local:count as xs:integer := 42; $local:count";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return $symbols(1)?detail");
        final String detail = result.getResource(0).getContent().toString();
        assertTrue("Detail should contain xs:integer: " + detail,
                detail.contains("integer"));
    }

    @Test
    public void multipleFunctionsAndVariables() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 1; " +
                "declare function local:a() { $local:x }; " +
                "declare function local:b($n as xs:integer) as xs:integer { $n + $local:x }; " +
                "local:b(local:a())";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return array:size($symbols)");
        assertEquals("Should find 1 variable + 2 functions = 3 symbols",
                "3", result.getResource(0).getContent().toString());
    }

    @Test
    public void multipleFunctionsAndVariablesNames() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 1; " +
                "declare function local:a() { $local:x }; " +
                "declare function local:b($n as xs:integer) as xs:integer { $n + $local:x }; " +
                "local:b(local:a())";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "')\n" +
                "return string-join(for $s in $symbols?* return $s?name, ', ')");
        final String names = result.getResource(0).getContent().toString();
        assertTrue("Should contain local:a#0: " + names, names.contains("local:a#0"));
        assertTrue("Should contain local:b#1: " + names, names.contains("local:b#1"));
        assertTrue("Should contain $local:x: " + names, names.contains("$local:x"));
    }

    @Test
    public void noSymbolsInSimpleExpression() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:symbols('1 + 2'))");
        assertEquals("Simple expression has no declarations",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void withModuleLoadPathEmpty() throws XMLDBException {
        final String xquery =
                "declare function local:foo() { () }; local:foo()";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $symbols := lsp:symbols('" + xquery + "', ())\n" +
                "return array:size($symbols)");
        assertEquals("1", result.getResource(0).getContent().toString());
    }
}
