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
 * Tests for the lsp:completions() function.
 */
public class CompletionsTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void returnsNonEmptyArrayForEmptyExpression() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:completions('')) > 0");
        assertEquals("Should return built-in functions even for empty expression",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesBuiltinFnCount() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = 'fn:count#1' }))");
        assertEquals("Should include fn:count#1",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesBuiltinFnStringJoin() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = 'fn:string-join#2' }))");
        assertEquals("Should include fn:string-join#2",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void builtinFunctionHasCorrectKind() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $fnCount := array:filter($completions, function($c) { $c?label = 'fn:count#1' })\n" +
                "return $fnCount(1)?kind");
        assertEquals("Function CompletionItemKind = 3",
                "3", result.getResource(0).getContent().toString());
    }

    @Test
    public void builtinFunctionHasDetail() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $fnCount := array:filter($completions, function($c) { $c?label = 'fn:count#1' })\n" +
                "return string-length($fnCount(1)?detail) > 0");
        assertEquals("Function should have non-empty detail",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void builtinFunctionHasInsertText() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $fnCount := array:filter($completions, function($c) { $c?label = 'fn:count#1' })\n" +
                "return $fnCount(1)?insertText");
        assertEquals("Insert text should be fn:count()",
                "fn:count()", result.getResource(0).getContent().toString());
    }

    @Test
    public void builtinFunctionHasDocumentation() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $fnCount := array:filter($completions, function($c) { $c?label = 'fn:count#1' })\n" +
                "return string-length($fnCount(1)?documentation) > 0");
        assertEquals("Built-in function should have documentation",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesKeywords() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = 'return' }))");
        assertEquals("Should include 'return' keyword",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void keywordHasCorrectKind() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $kw := array:filter($completions, function($c) { $c?label = 'let' })\n" +
                "return $kw(1)?kind");
        assertEquals("Keyword CompletionItemKind = 14",
                "14", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesUserDeclaredFunction() throws XMLDBException {
        final String xquery =
                "declare function local:greet($name as xs:string) as xs:string { " +
                "  ''Hello, '' || $name " +
                "}; local:greet(''world'')";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('" + xquery + "')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = 'local:greet#1' }))");
        assertEquals("Should include user-declared local:greet#1",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesUserDeclaredVariable() throws XMLDBException {
        final String xquery =
                "declare variable $local:greeting := ''hello''; $local:greeting";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('" + xquery + "')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = '$local:greeting' }))");
        assertEquals("Should include user-declared $local:greeting",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void userVariableHasCorrectKind() throws XMLDBException {
        final String xquery =
                "declare variable $local:x := 42; $local:x";
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('" + xquery + "')\n" +
                "let $var := array:filter($completions, function($c) { $c?label = '$local:x' })\n" +
                "return $var(1)?kind");
        assertEquals("Variable CompletionItemKind = 6",
                "6", result.getResource(0).getContent().toString());
    }

    @Test
    public void invalidExpressionStillReturnsBuiltins() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('let $x :=')\n" +
                "return array:size(array:filter($completions, function($c) { $c?label = 'fn:count#1' }))");
        assertEquals("Should include fn:count#1 even with invalid expression",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesMapModuleFunctions() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "return array:size(array:filter($completions, function($c) { starts-with($c?label, 'map:') })) > 0");
        assertEquals("Should include map: module functions",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void includesArrayModuleFunctions() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "return array:size(array:filter($completions, function($c) { starts-with($c?label, 'array:') })) > 0");
        assertEquals("Should include array: module functions",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void withModuleLoadPath() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:completions('', '/db')) > 0");
        assertEquals("Should return completions with /db load path",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void excludesPrivateFunctions() throws XMLDBException {
        // Private functions from built-in modules should not appear
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $private := array:filter($completions, function($c) { " +
                "  $c?kind = 3 and contains($c?detail, 'private') })\n" +
                "return array:size($private)");
        assertEquals("Should not include private functions",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void completionItemHasAllKeys() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $completions := lsp:completions('')\n" +
                "let $first := $completions(1)\n" +
                "return string-join((\n" +
                "  'label=' || map:contains($first, 'label'),\n" +
                "  'kind=' || map:contains($first, 'kind'),\n" +
                "  'detail=' || map:contains($first, 'detail'),\n" +
                "  'documentation=' || map:contains($first, 'documentation'),\n" +
                "  'insertText=' || map:contains($first, 'insertText')\n" +
                "), ',')");
        assertEquals("label=true,kind=true,detail=true,documentation=true,insertText=true",
                result.getResource(0).getContent().toString());
    }
}
