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
import org.junit.Ignore;
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
public class FunctionTypeInElementContentTest {
    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void arrayLiteral() throws XMLDBException {
        final String query = "element test { [] }";
        assertCompilationSuccess(query);
    }

    // TODO: array content could be removed after https://github.com/eXist-db/exist/issues/3472 is fixed
    @Test
    public void arrayConstructor() throws XMLDBException {
        final String query = "element test { array { () } }";
        assertCompilationSuccess(query);
    }

    @Test
    public void sequenceOfItems() throws XMLDBException {
        final String query = "element test { (1, map {})[1] }";
        assertCompilationSuccess(query);
    }

    @Test
    public void partialBuiltIn() throws XMLDBException {
        final String query = "element test { sum(?) }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 16, source: element test { sum(?) }]";
        assertCompilationError(query, error);
    }

    // TODO: Investigate does still throw without location info
    @Ignore
    @Test
    public void functionReference() throws XMLDBException {
        final String query = "element test { sum#0 }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 16, source: element test { sum#0 }]";
        assertCompilationError(query, error);
    }

    // Does not throw at compile time
    @Ignore
    @Test
    public void functionVariable() throws XMLDBException {
        final String query = "let $f := function () {} return element test { $f }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 16, source: element test { sum(?) }]";
        assertCompilationError(query, error);
    }

    @Test
    public void userDefinedFunction() throws XMLDBException {
        final String query = "element test { function () {} }";
        // TODO: user defined function has its location offset to a weird location
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got function(*) [at line 1, column 25, source: element test { function () { () } }]";
        assertCompilationError(query, error);
    }

    @Test
    public void mapConstructor() throws XMLDBException {
        final String query = "element test { map {} }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [at line 1, column 16, source: element test { map {} }]";
        assertCompilationError(query, error);
    }

    /**
     * sequence in enclosed expression with only a function type
     */
    @Ignore
    @Test
    public void sequenceOfMaps() throws XMLDBException {
        final String query = "element test { (map {}) }";
        final String error = "An exception occurred during query execution: err:XQTY0105 Function types are not allowed in element content. Got map(*) [source: element foo { (map{}) }]";
        assertCompilationError(query, error);
    }

    /**
     * This is an edge case, which would evaluate to empty sequence
     * but should arguably still throw.
     */
    // TODO: add (sub-expression) location
    @Test
    public void sequenceOfMapsEdgeCase() throws XMLDBException {
        final String query = "element test { (map {})[2] }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [source: element test { (map {})[2] }]";
        assertCompilationError(query, error);
    }

    // TODO: add (sub-expression) location
    // TODO: this could throw at compile time
    @Test
    public void ArrayOfMaps() throws XMLDBException {
        final String query = "element test { [map {}] }";
        final String error = "An exception occurred during query execution: err:XQTY0105 Function types are not allowed in element content. Got map(*) [source: element test { [map{}] }]";
        assertCompilationError(query, error);
    };

    // TODO: add (sub-expression) location
    // TODO: This should throw at compile time, but does not
    @Ignore
    @Test
    public void mapConstructorInSubExpression() throws XMLDBException {
        final String query = "element test { \"a\", map {} }";
        final String error = "err:XQTY0105 Function types are not allowed in element content. Got map(*) [at line 1, column 20, source: element test { map {} }]";
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
    private void assertCompilationSuccess(final String query) throws XMLDBException {
        final XQueryService service = (XQueryService)existEmbeddedServer.getRoot().getService("XQueryService", "1.0");

        service.compile(query);
        assertTrue( true );
    }
}
