package org.exist.xquery.functions.validate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.xml.sax.SAXException;
import java.io.IOException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class ONVDLTest {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    private final static String RNG_DATA1 =
    	"<element  name=\'Book\' xmlns='http://relaxng.org/ns/structure/1.0'  ns=\'http://www.books.org\'> " +
    	"<element name=\'Title\'><text/></element>" +
        "<element name=\'Author\'><text/></element>" +
        "<element name=\'Date\'><text/></element>" +
        "<element name=\'ISBN\'><text/></element>" +
        "<element name=\'Publisher\'><text/></element>" +
    	"</element>";

    private final static String NVDL_DATA1 ="<rules xmlns='http://purl.oclc.org/dsdl/nvdl/ns/structure/1.0'> " +
    	"<namespace ns=\'http://www.books.org\'>" +
        "     <validate schema=\"Book.rng\" />" +
        "</namespace>" +
    	"</rules>";

    private final static String XML_DATA1 ="<Book xmlns='http://www.books.org'> " +
    	"<Title>The Wisdom of Crowds</Title>" +
        "<Author>James Surowiecki</Author>" +
        "<Date>2005</Date>" +
        "<ISBN>0-385-72170-6</ISBN>" +
        "<Publisher>Anchor Books</Publisher>" +
    	"</Book>";

    
    public ONVDLTest() {
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
        service = (XPathQueryService) root.getService("XQueryService", "1.0");

        String query="xmldb:create-collection('xmldb:exist:///db','validate-test')";
        ResourceSet result = service.query(query);

        String query1="xmldb:store('/db/validate-test', 'test.nvdl',"+ NVDL_DATA1 +")";
        ResourceSet result1 = service.query(query1);

        String query2="xmldb:store('/db/validate-test', 'Book.rng',"+ RNG_DATA1 +")";
        ResourceSet result2 = service.query(query2);
    }

    @After
    public void tearDown() throws Exception {

        // remove test collection
        String query="xmldb:remove('xmldb:exist:///db/validate-test')";
        ResourceSet result = service.query(query);
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
    //System.out.println("tearDown PASSED");
    }

    @Test
    public void testONVDL() throws XPathException, IOException, XpathException, SAXException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $a := " + XML_DATA1 +
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:validate-report($a,$b)";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (XMLDBException e) {
            System.out.println("testONVDL(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testONVDLFail() throws XPathException, IOException, XpathException, SAXException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $a := <test/>" + 
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:validate-report($a,$b)";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("invalid", "//status/text()", r);

        } catch (XMLDBException e) {
            System.out.println("testONVDLFail(): " + e);
            fail(e.getMessage());
        }

    }
}