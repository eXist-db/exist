package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

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
