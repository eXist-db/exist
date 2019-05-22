package org.exist.xquery.value;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

/**
 *
 * @author aretter
 */
public class Base64BinaryTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void castToBase64ThenBackToString() throws XMLDBException {
        final String base64String = "QWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        final String query = "let $data := '" + base64String + "' cast as xs:base64Binary return $data cast as xs:string";

        final XQueryService service = (XQueryService)server.getRoot().getService("XQueryService", "1.0");

        final ResourceSet result = service.query(query);

        final String queryResult = (String)result.getResource(0).getContent();

        assertEquals(base64String, queryResult);
    }
}
