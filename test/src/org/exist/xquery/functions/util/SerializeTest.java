package org.exist.xquery.functions.util;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import org.xml.sax.SAXException;
import java.io.IOException;

import org.custommonkey.xmlunit.XMLUnit;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class SerializeTest {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public SerializeTest() {
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
    public void testSerialize() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <test/>\n" +
                    "return\n" +
                    "util:serialize($xml,'method=xml')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertXMLEqual(r,"<test/>");

        } catch (IOException ioe) {
                fail(ioe.getMessage());
        } catch (SAXException sae) {
                fail(sae.getMessage());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }

    }
    
    @Test
    public void testSerialize2() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <test><a/><b/></test>\n" +
                    "return\n" +
                    "util:serialize($xml,'method=xml indent=no')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertXMLEqual(r,"<test><a/><b/></test>");

        } catch (IOException ioe) {
                fail(ioe.getMessage());
        } catch (SAXException sae) {
                fail(sae.getMessage());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testSerializeText() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <test><a>test</a></test>\n" +
                    "return\n" +
                    "util:serialize($xml,'method=text indent=no')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals(r,"test");

        } catch (XMLDBException e) {
            fail(e.getMessage());
        }

    } 

    @Test
    public void testSerializeIndent() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <test><a/><b/></test>\n" +
                    "return\n" +
                    "util:serialize($xml,'method=xml indent=yes')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            XMLUnit.setIgnoreWhitespace(true);
            assertXMLEqual(r,"<test><a/><b/></test>");

        } catch (IOException ioe) {
                fail(ioe.getMessage());
        } catch (SAXException sae) {
                fail(sae.getMessage());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }

    }
    
    @Test
    public void testSerializeXincludes() throws XPathException, XpathException {
                        
        ResourceSet result = null;
        String r = "";
        try {
                                
            String query = "let $xml := " +
            		"<test xmlns:xi='http://www.w3.org/2001/XInclude'>" +
            		"	<xi:include href='/db/system/security/config.xml'/>" +
            		"</test>\n" +
                    "return\n" +
                    "util:serialize($xml,'enable-xincludes=yes')";

           result = service.query(query);
           r = (String) result.getResource(0).getContent();
           assertXpathEvaluatesTo("2.0","/test//@version",r);

        } catch (IOException ioe) {
                fail(ioe.getMessage());
        } catch (SAXException sae) {
                fail(sae.getMessage());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }    
}