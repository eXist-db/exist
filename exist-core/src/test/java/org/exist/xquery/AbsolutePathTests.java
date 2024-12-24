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

import com.evolvedbinary.j8fu.Either;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.XQueryCompilationTest;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.junit.Test;

import javax.xml.transform.Source;

import static org.exist.test.DiffMatcher.docSource;
import static org.exist.test.DiffMatcher.elemSource;
import static org.exist.test.XQueryAssertions.assertThatXQResult;
import static org.exist.test.XQueryAssertions.assertXQStaticError;
import static org.exist.test.XQueryAssertions.assertXQResultIdentical;
import static org.exist.test.XQueryAssertions.assertXQResultSimilar;
import static org.hamcrest.Matchers.equalTo;

/**
 * Ensure absolute XPath expression will raise a static error.
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
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
        final Source expected = elemSource("<result><x/><x/><x/></result>");

        final String query = "let $f := function($d) { $d ! //x }\n" +
                "let $d := document { <root><x/><x/><y><x/></y></root> }\n" +
                "return\n" +
                "  <result>{ $f($d) }</result>";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertXQResultIdentical(expected, actual);
    }

    @Test
    public void immediateLambdaWithDocumentAndSlash() throws EXistException, PermissionDeniedException {
        final Source expected = elemSource("<root/>");

        final String query = "let $f := function($d) { $d ! /root }\n" +
                "let $d := document { <root/> }\n" +
                "return\n" +
                "  $f($d)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertXQResultIdentical(expected, actual);
    }

    @Test
    public void immediateLambdaWithDocumentAndLoneSlash() throws EXistException, PermissionDeniedException {
        final Source expected = docSource("<root/>");

        final String query = "let $f := function($d) { $d ! / }\n" +
                "let $d := document { <root/> }\n" +
                "return\n" +
                "  $f($d)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertXQResultSimilar(expected, actual);
    }

    @Test
    public void topLevelAbsolutePath() throws EXistException, PermissionDeniedException {
        final Sequence expected = new IntegerValue(1);

        final String query = "count(//*)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }

}
