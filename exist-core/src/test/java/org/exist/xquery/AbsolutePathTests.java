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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.XQueryCompilationTest;
import org.junit.Test;

import static org.exist.test.XQueryAssertions.assertXQResultToStringEquals;
import static org.exist.test.XQueryAssertions.assertXQStaticError;

/**
 * Ensure absolute XPath expression will raise a static error
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class AbsolutePathTests extends XQueryCompilationTest {
    @Test
    public void declaredFunctionAbsoluteSlash() throws EXistException, PermissionDeniedException {
        final String query = "declare function local:x() { /x }; local:x()";
        final String expectedMessage = "Leading '/' selects nothing, ContextItem is absent in function body";
        assertXQStaticError(ErrorCodes.XPDY0002, 1,30, expectedMessage, compileQuery(query));
    }

    @Test
    public void declaredFunctionLoneSlash() throws EXistException, PermissionDeniedException {
        final String query = "declare function local:x() { / }; local:x()";
        final String expectedMessage = "Leading '/' selects nothing, ContextItem is absent in function body";
        assertXQStaticError(ErrorCodes.XPDY0002, 1, 30, expectedMessage, compileQuery(query));
    }

    @Test
    public void declaredFunctionAbsoluteDoubleSlash() throws EXistException, PermissionDeniedException {
        final String query = "declare function local:x() { //x }; local:x()";
        final String expectedMessage = "Leading '//' selects nothing, ContextItem is absent in function body";
        assertXQStaticError(ErrorCodes.XPDY0002, 1, 30, expectedMessage, compileQuery(query));
    }

    @Test
    public void immediateLambdaContainsSlash() throws EXistException, PermissionDeniedException {
        final String query = "(function() { /x })()";
        assertXQStaticError(ErrorCodes.XPDY0002, 1, 15, compileQuery(query));
    }

    @Test
    public void immediateLambdaContainsLoneSlash() throws EXistException, PermissionDeniedException {
        final String query = "(function() { / })()";
        assertXQStaticError(ErrorCodes.XPDY0002, 1, 15, compileQuery(query));
    }

    @Test
    public void immediateLambdaContainsDoubleSlash() throws EXistException, PermissionDeniedException {
        final String query = "(function() { //x })()";
        assertXQStaticError(ErrorCodes.XPDY0002, 1, 15, compileQuery(query));
    }

    @Test
    public void immediateLambdaWithDocumentAndDoubleSlash() throws EXistException, PermissionDeniedException {
        final String query = "let $f := function($d) { $d ! //x }\n" +
                "let $d := document { <root><x/><x/><y><x/></y></root> }\n" +
                "return serialize($f($d))";
        assertXQResultToStringEquals("<x/><x/><x/>", executeQuery(query));
    }

    @Test
    public void immediateLambdaWithDocumentAndSlash() throws EXistException, PermissionDeniedException {
        final String query = "let $f := function($d) { $d ! /root }\n" +
                "let $d := document { <root/> }\n" +
                "return serialize($f($d))";
        assertXQResultToStringEquals("<root/>", executeQuery(query));
    }

    @Test
    public void immediateLambdaWithDocumentAndLoneSlash() throws EXistException, PermissionDeniedException {
        final String query = "let $f := function($d) { $d ! / }\n" +
                "let $d := document { <root/> }\n" +
                "return serialize($f($d))";
        assertXQResultToStringEquals("<root/>", executeQuery(query));
    }

    @Test
    public void topLevelAbsolutePath() throws EXistException, PermissionDeniedException {
        final String query = "count(//*)";
        assertXQResultToStringEquals("1", executeQuery(query));
    }

}
