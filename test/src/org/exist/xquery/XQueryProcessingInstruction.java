package org.exist.xquery;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
/**
 *
 * @author jimfuller
 */
public class XQueryProcessingInstruction {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public XQueryProcessingInstruction() {
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        service = (XPathQueryService) root.getService("XQueryService", "1.0");
    }

    @After
    public void tearDown() throws Exception {
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
    }

    @Test
    public void testPI() throws XPathException, SAXException, IOException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <doc>"+
                                "<?pi test?>"+
                                "<p>This is a p.</p>"+
                                "</doc>" +
                    "return\n" +
                    "$xml";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertXMLEqual(r,"<doc><?pi test?><p>This is a p.</p></doc>");

        } catch (IOException ioe) {
                fail(ioe.getMessage());
        } catch (SAXException sae) {
                fail(sae.getMessage());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }

    }


   
}