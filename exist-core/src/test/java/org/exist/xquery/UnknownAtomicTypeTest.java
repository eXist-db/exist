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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.*;

/**
 * Ensure tests for unknown atomic types are reported with location information
 * https://github.com/eXist-db/exist/issues/3518
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class UnknownAtomicTypeTest {
    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void letVariable() throws XMLDBException {
        final String query = "let $x as y := 0 return $x";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 11]";
        assertCompilationError(query, error);
    }

    @Test
    public void functionReturnType() throws XMLDBException {
        final String query = "function () as y { () }";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 16]";
        assertCompilationError(query, error);
    }

    @Test
    public void functionParameterType() throws XMLDBException {
        final String query = "function ($x as y) { $x }";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 17]";
        assertCompilationError(query, error);
    }

    @Test
    public void instanceOf() throws XMLDBException {
        final String query = "1 instance of y";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 15]";
        assertCompilationError(query, error);
    }

    @Test
    public void treatAs() throws XMLDBException {
        final String query = "1 treat as y";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 12]";
        assertCompilationError(query, error);
    }

    @Test
    public void castAs() throws XMLDBException {
        final String query = "1 cast as y";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 11]";
        assertCompilationError(query, error);
    }

    @Test
    public void castableAs() throws XMLDBException {
        final String query = "1 castable as y";
        final String error = "err:XPST0051 Unknown simple type y [at line 1, column 15]";
        assertCompilationError(query, error);
    }

    private void assertCompilationError(final String query, final String error) throws XMLDBException {
        final XQueryService service = (XQueryService)existEmbeddedServer.getRoot().getService("XQueryService", "1.0");

        try {
            service.compile(query);
            fail("no XMLDBException was thrown during compilation.");
        } catch (XMLDBException ex) {
            assertEquals( error, ex.getMessage() );
        }
    }
}
