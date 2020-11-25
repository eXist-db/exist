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
package org.exist.test;

import com.evolvedbinary.j8fu.Either;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;

import static org.junit.Assert.*;

/**
 * This set of assertions are meant to help when testing XQuery compilation, execution and errors
 * Especially useful, if the testsuite inherits from/ extends XQueryCompilationTest class
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class XQueryAssertions {
    public static void assertXQStaticError(final ErrorCodes.ErrorCode expectedCode, final int line, final int column, final String expectedMessage, final Either<XPathException, CompiledXQuery> actual) {
        assertXQStaticError(expectedCode, line, column, actual);
        assertXQErrorMessage(expectedMessage, actual.left().get());
    }

    public static void assertXQStaticError(final ErrorCodes.ErrorCode expectedCode, final int line, final int column, final Either<XPathException, CompiledXQuery> actual) {
        assertTrue("Expected static error: " + expectedCode.getErrorQName() + ", but no error was thrown.", actual.isLeft());
        final XPathException xpe = actual.left().get();
        assertXQErrorCode(expectedCode, xpe);
        assertXQErrorLine(line, xpe);
        assertXQErrorColumn(column, xpe);
    }

    public static void assertXQDynamicError(final ErrorCodes.ErrorCode expectedCode, final int line, final int column, final String expectedMessage, final Either<XPathException, Sequence> actual) {
        assertXQDynamicError(expectedCode, line, column, actual);
        assertXQErrorMessage(expectedMessage, actual.left().get());
    }

    public static void assertXQDynamicError(final ErrorCodes.ErrorCode expectedCode, final int line, final int column, final Either<XPathException, Sequence> actual) {
        assertTrue("Expected dynamic error: " + expectedCode.getErrorQName() + ", but no error was thrown.", actual.isLeft());
        final XPathException xpe = actual.left().get();
        assertXQErrorLine(line, xpe);
        assertXQErrorColumn(column, xpe);
        assertXQErrorCode(expectedCode, xpe);
    }

    public static void assertXQResultToStringEquals(final String expectedSerializedString, final Either<XPathException, Sequence> actual) {
        assertTrue("Expected: " + expectedSerializedString + ", but got result: " + actual.toString(), actual.isRight());
        final Sequence sequence = actual.right().get();
        assertEquals(expectedSerializedString, sequence.toString());
    }

    public static void assertXQErrorCode(final ErrorCodes.ErrorCode expectedCode, final XPathException exception) {
        assertEquals("Expected: " + expectedCode.getErrorQName() + ", but got: " + exception.getErrorCode().getErrorQName(),
                expectedCode, exception.getErrorCode());
    }

    public static void assertXQErrorLine(final int expectedLine, final XPathException exception) {
        assertEquals("Expected line to be " + expectedLine + ", but got " + exception.getLine(),
                expectedLine, exception.getLine());
    }

    public static void assertXQErrorColumn(final int expectedColumn, final XPathException exception) {
        assertEquals("Expected column to be " + expectedColumn + ", but got " + exception.getColumn(),
                expectedColumn, exception.getColumn());
    }

    public static void assertXQErrorMessage(final String expectedMessage, final XPathException exception) {
        assertEquals("Expected message to be " + expectedMessage + ", but got " + exception.getMessage(),
                expectedMessage, exception.getDetailMessage());
    }

}
