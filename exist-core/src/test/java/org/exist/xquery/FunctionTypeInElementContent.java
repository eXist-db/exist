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
 * Ensure function types returned in element content throws at compile time and has location information
 * https://github.com/eXist-db/exist/issues/3474
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class FunctionTypeInElementContent {
    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void arrayLiteral() throws XMLDBException {
        final String query = "element test { [] }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got array(*) [at line 1, column 14, source: element test { [] }]";
        assertCompilationError(query, error);
    }

    @Test
    public void partialBuiltIn() throws XMLDBException {
        final String query = "element test { sum(?) }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 14, source: element test { sum(?) }]";
        assertCompilationError(query, error);
    }

    @Test
    public void userDefinedFunction() throws XMLDBException {
        final String query = "element test { function () { () } }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 14, source: element test { function () { () } }]";
        assertCompilationError(query, error);
    }

    @Test
    public void arrayConstructor() throws XMLDBException {
        final String query = "element test { array { () } }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got array(*) [at line 1, column 14, source: element test { array { () } }]";
        assertCompilationError(query, error);
    }

    @Test
    public void mapConstructor() throws XMLDBException {
        final String query = "element test { map {} }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [at line 1, column 14, source: element test { map {} }]";
        assertCompilationError(query, error);
    }

//  -- no error is thrown at compile time
//    @Test
//    public void mapConstructorInSequence() throws XMLDBException {
//        final String query = "element test {\n" +
//                "    \"a\",\n" +
//                "    map {}\n" +
//                "}";
//        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [at line 1, column 14, source: element test { map {} }]";
//        assertCompilationError(query, error);
//    }


//  -- no error is thrown at compile time nor run time
//    @Test
//    public void arrayInSequence() throws XMLDBException {
//        final String query = "element test {\n" +
//                "    \"a\",\n" +
//                "    []\n" +
//                "}";
//        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [at line 1, column 14, source: element test { map {} }]";
//        assertCompilationError(query, error);
//    }

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
