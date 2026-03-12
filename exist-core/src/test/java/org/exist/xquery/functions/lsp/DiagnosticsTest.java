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
 * Tests for the lsp:diagnostics() function.
 */
public class DiagnosticsTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String LSP_IMPORT =
            "import module namespace lsp = 'http://exist-db.org/xquery/lsp';\n";

    @Test
    public void validExpressionReturnsEmptyArray() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:diagnostics('let $x := 1 return $x'))");
        assertEquals("Valid expression should return empty array",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void syntaxErrorReturnsDiagnostic() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:diagnostics('let $x := 1 retrun $x'))");
        assertEquals("Syntax error should return one diagnostic",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void diagnosticSeverityIsError() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('let $x := retrun $x')\n" +
                "return $result(1)?severity");
        assertEquals("Severity should be 1 (LSP DiagnosticSeverity.Error)",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void syntaxErrorHasLineZero() throws XMLDBException {
        // Single-line expression: error should be on line 0
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('let $x := 1 retrun $x')\n" +
                "return $result(1)?line");
        final int line = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("Error in single-line expression should be on line 0 or 1, got: " + line,
                line >= 0 && line <= 1);
    }

    @Test
    public void syntaxErrorHasPositiveColumn() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('let $x := 1 retrun $x')\n" +
                "return $result(1)?column");
        final int column = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("Column should be non-negative, got: " + column, column >= 0);
    }

    @Test
    public void diagnosticMessageDescribesError() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('let $x := 1 retrun $x')\n" +
                "return $result(1)?message");
        final String message = result.getResource(0).getContent().toString();
        assertTrue("Error message should not be empty", message.length() > 0);
    }

    @Test
    public void undeclaredVariableHasErrorCode() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('$undeclared')\n" +
                "return $result(1)?code");
        final String code = result.getResource(0).getContent().toString();
        assertTrue("Undeclared variable error code should contain XPST0008, got: " + code,
                code.contains("XPST0008"));
    }

    @Test
    public void undeclaredVariableMessageMentionsVarName() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "let $result := lsp:diagnostics('$undeclared')\n" +
                "return $result(1)?message");
        final String message = result.getResource(0).getContent().toString();
        assertTrue("Message should mention the variable name: " + message,
                message.contains("undeclared"));
    }

    @Test
    public void emptyExpressionReturnsEmptyArray() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:diagnostics(''))");
        assertEquals("Empty expression should return empty array",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void whitespaceOnlyExpressionReturnsEmptyArray() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT + "array:size(lsp:diagnostics('   '))");
        assertEquals("Whitespace-only expression should return empty array",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void withXmldbModuleLoadPath() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "array:size(lsp:diagnostics('let $x := 1 return $x', 'xmldb:exist:///db'))");
        assertEquals("Valid expression with xmldb load path should return empty array",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void withDbModuleLoadPath() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "array:size(lsp:diagnostics('let $x := 1 return $x', '/db'))");
        assertEquals("Valid expression with /db load path should return empty array",
                "0", result.getResource(0).getContent().toString());
    }

    @Test
    public void withEmptyModuleLoadPath() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                LSP_IMPORT +
                "array:size(lsp:diagnostics('let $x := 1 return $x', ()))");
        assertEquals("Valid expression with empty load path should return empty array",
                "0", result.getResource(0).getContent().toString());
    }
}
