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

import static org.exist.test.XQueryAssertions.assertXQStaticError;

/**
 * Ensure tests for unknown atomic types are reported with location information
 * https://github.com/eXist-db/exist/issues/3518
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class UnknownAtomicTypeTest extends XQueryCompilationTest {
    @Test
    public void letVariable() throws EXistException, PermissionDeniedException {
        final String query = "let $x as a := 0 return $x";
        final String error = "Unknown simple type a";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 11, error, compileQuery(query));
    }

    @Test
    public void functionReturnType() throws EXistException, PermissionDeniedException {
        final String query = "function () as b { () }";
        final String error = "Unknown simple type b";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 16, error, compileQuery(query));
    }

    @Test
    public void functionParameterType() throws EXistException, PermissionDeniedException {
        final String query = "function ($x as c) { $x }";
        final String error = "Unknown simple type c";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 17, error, compileQuery(query));
    }

    @Test
    public void instanceOf() throws EXistException, PermissionDeniedException {
        final String query = "1 instance of d";
        final String error = "Unknown simple type d";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 15, error, compileQuery(query));
    }

    @Test
    public void treatAs() throws EXistException, PermissionDeniedException {
        final String query = "1 treat as e";
        final String error = "Unknown simple type e";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 12, error, compileQuery(query));
    }

    @Test
    public void castAs() throws EXistException, PermissionDeniedException {
        final String query = "1 cast as f";
        final String error = "Unknown simple type f";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 11, error, compileQuery(query));
    }

    @Test
    public void castableAs() throws EXistException, PermissionDeniedException {
        final String query = "1 castable as g";
        final String error = "Unknown simple type g";
        assertXQStaticError(ErrorCodes.XPST0051, 1, 15, error, compileQuery(query));
    }
}
