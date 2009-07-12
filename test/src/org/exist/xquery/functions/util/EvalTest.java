package org.exist.xquery.functions.util;

import org.exist.dom.NodeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
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
public class EvalTest {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public EvalTest() {
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
    }

    @After
    public void tearDown() throws Exception {
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
    //System.out.println("tearDown PASSED");
    }

    @Test
    public void testEval() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $query := 'let $a := 1 return $a'\n" +
                    "return\n" +
                    "util:eval($query)";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1", r);

        } catch (XMLDBException e) {
            System.out.println("testEval(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalwithPI() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $query := 'let $a := <test><?pi test?></test> return count($a//processing-instruction())'\n" +
                    "return\n" +
                    "util:eval($query)";            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1", r);

        } catch (XMLDBException e) {
            System.out.println("testEval(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalInline() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := document{<test><a><b/></a></test>}\n" +
                    "let $query := 'count(.//*)'\n" +
                    "return\n" +
                    "util:eval-inline($xml,$query)";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("3", r);

        } catch (XMLDBException e) {
            System.out.println("testEvalInline(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalWithContextVariable() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $xml := <test><a/><b/></test>\n" +
                    "let $context := <static-context>\n" +
                    "<variable name='xml'>{$xml}</variable>\n" +
                    "</static-context>\n" +
                    "let $query := 'count($xml//*) mod 2 = 0'\n" +
                    "return\n" +
                    "util:eval-with-context($query, $context, false())";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);

        } catch (XMLDBException e) {
            System.out.println("testEvalWithContextVariable(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalSupplyingContext() throws XPathException {

        ResourceSet result = null;
        String r = "test";
        try {
            String query = "let $xml := <test><a/></test>\n" +
                    "let $context := <static-context>\n" +
                    "<default-context>{$xml}</default-context>\n" +
                    "</static-context>\n" +
                    "let $query := 'count(.//*) mod 2 = 0'\n" +
                    "return\n" +
                    "util:eval-with-context($query, $context, false())";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();

            assertEquals("true", r);


        } catch (XMLDBException e) {
            System.out.println("testEvalSupplyingContext(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalSupplyingContextAndVariable() throws XPathException {

        ResourceSet result = null;
        String r = "test";
        try {
            String query = "let $xml := <test><a/></test>\n" +
                    "let $context := <static-context>\n" +
                    "<variable name='xml'>{$xml}</variable>\n" +
                    "<default-context>{$xml}</default-context>\n" +
                    "</static-context>\n" +
                    "let $query := 'count($xml//*) + count(.//*)'\n" +
                    "return\n" +
                    "util:eval-with-context($query, $context, false())";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();

            assertEquals("3", r);


        } catch (XMLDBException e) {
            System.out.println("testEvalSupplyingContextAndVariable(): " + e);
            fail(e.getMessage());
        }

    }
}