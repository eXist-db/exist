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
import static org.exist.test.XQueryAssertions.assertXQResultSimilar;

/**
 * Ensure string literals can be used inside attribute value templates.
 * See issue: <a href="https://github.com/eXist-db/exist/issues/291">#291</a>.
 */
public class StringLiteralInAttributeConstructorTest extends XQueryCompilationTest {

    @Test
    public void singleQuotedStringInDoubleQuotedAttribute() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{'a'}\"/>";
        assertXQResultSimilar(elemSource("<root a='a'/>"), executeQuery(query));
    }

    @Test
    public void doubleQuotedStringInSingleQuotedAttribute() throws EXistException, PermissionDeniedException {
        final String query = "<root a='{\"a\"}'/>";
        assertXQResultSimilar(elemSource("<root a='a'/>"), executeQuery(query));
    }

    @Test
    public void numericLiteralInAttribute() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{1}\"/>";
        assertXQResultSimilar(elemSource("<root a='1'/>"), executeQuery(query));
    }

    @Test
    public void concatStringWithEmptySequence() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{() || 'a'}\"/>";
        assertXQResultSimilar(elemSource("<root a='a'/>"), executeQuery(query));
    }

    @Test
    public void variableSubstitution() throws EXistException, PermissionDeniedException {
        final String query = "let $a := 'a' return <root a=\"{$a}\"/>";
        assertXQResultSimilar(elemSource("<root a='a'/>"), executeQuery(query));
    }

    @Test
    public void stringConstructor() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{``[a]``}\"/>";
        assertXQResultSimilar(elemSource("<root a='a'/>"), executeQuery(query));
    }

    @Test
    public void multipleAttributesWithStringLiterals() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{'a'}\" b=\"{'b'}\"/>";
        assertXQResultSimilar(elemSource("<root a='a' b='b'/>"), executeQuery(query));
    }

    @Test
    public void stringConcatInAttribute() throws EXistException, PermissionDeniedException {
        final String query = "<root a=\"{'hello' || ' ' || 'world'}\"/>";
        assertXQResultSimilar(elemSource("<root a='hello world'/>"), executeQuery(query));
    }
}
