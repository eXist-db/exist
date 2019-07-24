package org.exist.xquery;

import java.io.IOException;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;

import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
/**
 *
 * @author jimfuller
 */
public class XQueryProcessingInstruction {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void testPI() throws XPathException, SAXException, IOException, XMLDBException {
        final String query = "let $xml := <doc>" +
                "<?pi test?>" +
                "This is a p." +
                "</doc>" +
                "return\n" +
                "$xml";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertXMLEqual(r, "<doc><?pi test?>This is a p.</doc>");
    }
}
