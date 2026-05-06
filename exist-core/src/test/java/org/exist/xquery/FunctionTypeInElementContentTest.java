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

import static org.exist.test.DiffMatcher.elemSource;
import static org.exist.test.XQueryAssertions.*;

/**
 * Ensure function types returned in element content throws at compile time and
 * has location information.
 * See issue :<a href="https://github.com/eXist-db/exist/issues/3474">#3474</a>.
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class FunctionTypeInElementContentTest extends XQueryCompilationTest {

    @Test
    public void arrayLiteral() throws EXistException, PermissionDeniedException {
        final String query = "element test { [] }";
        assertXQResultSimilar(elemSource("<test/>"), executeQuery(query));
    }

    // TODO(JL): array content could be removed after https://github.com/eXist-db/exist/issues/3472 is fixed
    @Test
    public void arrayConstructor() throws EXistException, PermissionDeniedException {
        final String query = "element test { array { () } }";
        assertXQResultSimilar(elemSource("<test/>"), executeQuery(query));
    }

    @Test
    public void sequenceOfItems() throws EXistException, PermissionDeniedException {
        final String query = "element test { (1, map {})[1] }";
        assertXQResultSimilar(elemSource("<test>1</test>"), executeQuery(query));
    }

    @Test
    public void partialBuiltIn() throws EXistException, PermissionDeniedException {
        final String query = "element test { sum(?) }";
        final String error = "Function types are not allowed in element content. Got function(*)";
        assertXQStaticError(ErrorCodes.XQTY0105, 1, 16, error, compileQuery(query));
    }

    // TODO(JL): Does still throw without location info
    @Test
    public void functionReference() throws EXistException, PermissionDeniedException {
        final String query = "element test { sum#0 }";
        final String error = "Function types are not allowed in element content. Got function(*)";
        assertXQStaticError(ErrorCodes.XQTY0105, -1, -1, error, compileQuery(query));
    }

    // TODO(JL): Does not throw at compile time
    @Test
    public void functionVariable() throws EXistException, PermissionDeniedException {
        final String query = "let $f := function () {} return element test { $f }";
        final String error = "Enclosed expression contains function item";
        assertXQDynamicError(ErrorCodes.XQTY0105, 1, 49, error, executeQuery(query));
    }

    // TODO(JL): user defined function has its location offset to a weird location
    @Test
    public void userDefinedFunction() throws EXistException, PermissionDeniedException {
        final String query = "element test { function () {} }";
        final String error = "Function types are not allowed in element content. Got function(*)";
        assertXQStaticError(ErrorCodes.XQTY0105, 1, 25, error, compileQuery(query));
    }

    @Test
    public void mapConstructor() throws EXistException, PermissionDeniedException {
        final String query = "element test { map {} }";
        final String error = "Function types are not allowed in element content. Got map(*)";
        assertXQStaticError(ErrorCodes.XQTY0105, 1, 16, error, compileQuery(query));
    }

    @Test
    public void mapConstructorLookup() throws EXistException, PermissionDeniedException {
        final String query = "element test { map {1:1}?1 }";
        assertXQResultSimilar(elemSource("<test>1</test>"), executeQuery(query));
    }

    /**
     * sequence in enclosed expression with only a function type
     */
    @Test
    public void sequenceOfMaps() throws EXistException, PermissionDeniedException {
        final String query = "element test { (map {}) }";
        final String error = "Function types are not allowed in element content. Got map(*)";
        assertXQDynamicError(ErrorCodes.XQTY0105, 1, 17, error, executeQuery(query));
    }

    // TODO(JL): add (sub-expression) location
    /**
     * This is an edge case, which would evaluate to empty sequence
     * but should arguably still throw.
     */
    @Test
    public void sequenceOfMapsEdgeCase() throws EXistException, PermissionDeniedException {
        final String query = "element test { (map {})[2] }";
        final String error = "Function types are not allowed in element content. Got map(*)";
        assertXQStaticError(ErrorCodes.XQTY0105, 0, 0, error, compileQuery(query));
    }

    // TODO(JL): add (sub-expression) location
    // TODO(JL): this could throw at compile time
    @Test
    public void arrayOfMaps() throws EXistException, PermissionDeniedException {
        final String query = "element test { [map {}] }";
        final String error = "Enclosed expression contains function item";
        assertXQDynamicError(ErrorCodes.XQTY0105, 1, 16, error, executeQuery(query));
    }

    // TODO(JL): add (sub-expression) location
    // TODO(JL): This should throw at compile time, but does not
    @Test
    public void mapConstructorInSubExpression() throws EXistException, PermissionDeniedException {
        final String query = "element test { \"a\", map {} }";
        final String error = "Enclosed expression contains function item";
        assertXQDynamicError(ErrorCodes.XQTY0105, 1, 16, error, executeQuery(query));
    }
}
