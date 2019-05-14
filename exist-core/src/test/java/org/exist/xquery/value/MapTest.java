package org.exist.xquery.value;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MapTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void effectiveBooleanValue() {
        try {
            final XQueryService queryService = (XQueryService) server.getRoot().getService("XQueryService", "1.0");
            queryService.query("fn:boolean(map{})");
        } catch(final XMLDBException e) {
           final Throwable cause = e.getCause();
           if(cause instanceof XPathException) {
               final XPathException xpe = (XPathException)cause;
               assertEquals(ErrorCodes.FORG0006, xpe.getErrorCode());
               return;
           }
        }
        fail("effectiveBooleanValue of a map should cause the error FORG0006");
    }
}
