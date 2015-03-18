package org.exist.xquery.functions.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class EvalTest {

    private final static String URI = XmldbURI.LOCAL_DB;
    
    private XQueryService service;
    private Collection root = null;
    private Database database = null;
    private Resource invokableQuery;

    private final static String INVOKABLE_QUERY_FILENAME = "invokable.xql";
    private final static String INVOKABLE_QUERY_EXTERNAL_VAR_NAME = "some-value";

    public EvalTest() {
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        service = (XQueryService) root.getService("XQueryService", "1.0");

        invokableQuery = root.createResource(INVOKABLE_QUERY_FILENAME, "BinaryResource");
        invokableQuery.setContent(
            "declare variable $" + INVOKABLE_QUERY_EXTERNAL_VAR_NAME + " external;\n" + "<hello>{$" + INVOKABLE_QUERY_EXTERNAL_VAR_NAME + "}</hello>"
        );
        ((EXistResource) invokableQuery).setMimeType("application/xquery");
        root.storeResource(invokableQuery);
    }

    @After
    public void tearDown() throws Exception {

        root.removeResource(invokableQuery);

        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
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
            fail(e.getMessage());
        }

    }

    @Test
    public void testEvalWithExternalVars() throws XPathException {
        ResourceSet result = null;
        try {
            String query = "let $value := 'world' return\n" +
                    "\tutil:eval(xs:anyURI('/db/" + INVOKABLE_QUERY_FILENAME + "'), false(), (xs:QName('" + INVOKABLE_QUERY_EXTERNAL_VAR_NAME + "'), $value))";
            result = service.query(query);

            LocalXMLResource res = (LocalXMLResource)result.getResource(0);
            Node n = res.getContentAsDOM();
            assertEquals(n.getLocalName(), "hello");
            assertEquals("world", n.getFirstChild().getNodeValue());
        } catch (XMLDBException e) {
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
            fail(e.getMessage());
        }

    }
    
    @Test
    public void evalInContextWithPreDeclaredNamespace() throws XMLDBException {
        
        createCollection("testEvalInContextWithPreDeclaredNamespace");
        
        final String query =
            "xquery version \"1.0\";\r\n" +
            "declare namespace db = \"http://docbook.org/ns/docbook\";\r\n" +
            "import module namespace util = \"http://exist-db.org/xquery/util\";\r\n" +
            "let $q := \"/db:article\" return\r\n" +
            "util:eval($q)";

        executeQuery(query);
    }
    
    @Test
    public void evalInContextWithPreDeclaredNamespaceAcrossLocalFunctionBoundary() throws XMLDBException {
        
        createCollection("testEvalInContextWithPreDeclaredNamespace");
        
        final String query =
            "xquery version \"1.0\";\r\n" +
            "import module namespace util = \"http://exist-db.org/xquery/util\";\r\n" +
            "declare namespace db = \"http://docbook.org/ns/docbook\";\r\n" +
            "declare function local:process($q as xs:string) {\r\n" +
            "\tutil:eval($q)\r\n" +
            "};\r\n" +
            "let $q := \"/db:article\" return\r\n" +
            "local:process($q)";

        executeQuery(query);
    }
    
    //should fail with - Error while evaluating expression: /db:article. XPST0081: No namespace defined for prefix db [at line 5, column 9]
    @Test(expected=XMLDBException.class)
    public void evalInContextWithPreDeclaredNamespaceAcrossModuleBoundary() throws XMLDBException {
        
        Collection testHome = createCollection("testEvalInContextWithPreDeclaredNamespace");
        
        final String processorModule =
                "xquery version \"1.0\";\r\n" +
                "module namespace processor = \"http://processor\";\r\n" +
                "import module namespace util = \"http://exist-db.org/xquery/util\";\r\n" +
                "declare function processor:process($q as xs:string) {\r\n" +
                "\tutil:eval($q)\r\n" +
                "};";
        
        writeModule(testHome, "processor.xqm", processorModule);
        
        final String query =
            "xquery version \"1.0\";\r\n" +
            "import module namespace processor = \"http://processor\" at \"xmldb:exist://" + testHome.getName() + "/processor.xqm\";\r\n" +
            "declare namespace db = \"http://docbook.org/ns/docbook\";\r\n" +
            "let $q := \"/db:article\" return\r\n" +
            "processor:process($q)";

        executeQuery(query);
    }
    
    private Collection createCollection(String collectionName) throws XMLDBException {
        Collection collection = root.getChildCollection(collectionName);
        if (collection == null) {
            CollectionManagementService cmService = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            cmService.createCollection(collectionName);
        }

        collection = DatabaseManager.getCollection(URI + "/" + collectionName, "admin", "");
        assertNotNull(collection);
        return collection;
    }
    
    private void writeModule(Collection collection, String modulename, String module) throws XMLDBException {
        BinaryResource res = (BinaryResource) collection.createResource(modulename, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(module.getBytes());
        collection.storeResource(res);
        collection.close();
    }

    private ResourceSet executeQuery(String query) throws XMLDBException {
        
        CompiledExpression compiledQuery = service.compile(query);
        ResourceSet result = service.execute(compiledQuery);
        return result;
    }
}