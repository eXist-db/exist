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
package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.Either;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.XQueryCompilationTest;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the XQuery 4.0 'content' option of fn:load-xquery-module,
 * which compiles a library module from a string instead of fetching by URI.
 *
 * Targets the FOQM0003 false-positive that previously occurred when
 * caller and inline module both declared xquery version "4.0":
 * the version check compared the temporary host context (always 3.1)
 * to the requested 4.0 instead of the loaded module's own context.
 */
public class LoadXQueryModuleContentTest extends XQueryCompilationTest {

    private static String unwrap(final Either<XPathException, Sequence> result) {
        if (result.isLeft()) {
            fail("Query failed: " + result.left().get().getMessage());
        }
        try {
            return result.right().get().itemAt(0).getStringValue();
        } catch (final XPathException e) {
            fail("Could not stringify result: " + e.getMessage());
            return null;
        }
    }

    @Test
    public void contentOption_xq40_matchesCallerVersion() throws EXistException, PermissionDeniedException {
        final String query =
                "xquery version \"4.0\";\n" +
                "let $module := \"xquery version '4.0';\n" +
                "module namespace m = 'http://example.com/m';\n" +
                "declare function m:hello() as xs:string { 'hi' };\"\n" +
                "let $loaded := fn:load-xquery-module(\n" +
                "  'http://example.com/m',\n" +
                "  map { 'content': $module }\n" +
                ")\n" +
                "let $f := $loaded?functions(fn:QName('http://example.com/m','hello'))?0\n" +
                "return $f()";

        assertEquals("hi", unwrap(executeQuery(query)));
    }

    @Test
    public void contentOption_versionMismatch_raisesFOQM0003()
            throws EXistException, PermissionDeniedException {
        // Caller is 4.0, inline module declares 3.1. Spec requires FOQM0003.
        final String query =
                "xquery version \"4.0\";\n" +
                "let $module := \"xquery version '3.1';\n" +
                "module namespace m = 'http://example.com/m31';\n" +
                "declare function m:hello() as xs:string { 'hi' };\"\n" +
                "return fn:load-xquery-module(\n" +
                "  'http://example.com/m31',\n" +
                "  map { 'content': $module }\n" +
                ")";

        final Either<XPathException, Sequence> result = executeQuery(query);
        assertTrue("Expected FOQM0003 error, got: " + result, result.isLeft());
        assertEquals(ErrorCodes.FOQM0003, result.left().get().getErrorCode());
    }
}
