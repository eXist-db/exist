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
import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.XQueryCompilationTest;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.exist.test.XQueryAssertions.assertXQErrorCode;

/**
 * @author Dannes Wessels
 */
@RunWith(ParallelRunner.class)
public class FunDocumentUriTest extends XQueryCompilationTest {
    
    @Test
    public void testFnFunDocumentUri() throws EXistException, PermissionDeniedException  {
        final String query = "declare context item := 'a'; document-uri()";
        final Either<XPathException, Sequence> result = executeQuery(query);
        assertXQErrorCode(ErrorCodes.XPTY0004,  result.left().get());
    }
}
