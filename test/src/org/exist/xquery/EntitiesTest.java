package org.exist.xquery;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.jetty.JettyStart;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

public class EntitiesTest extends XMLTestCase {
    
    private static String uri = XmldbURI.LOCAL_DB;
    
    public static void setURI(String collectionURI) {
        uri = collectionURI;
    }
    
    private static JettyStart server = null;
    
    private Collection testCollection;
    @SuppressWarnings("unused")
	private String query;
    
    protected void setUp() {
        if (uri.startsWith("xmldb:exist://localhost"))
            initServer();
        try {
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
            Collection root =
                    DatabaseManager.getCollection(
                    uri,
                    "admin",
                    null);
            CollectionManagementService service =
                    (CollectionManagementService) root.getService(
                    "CollectionManagementService",
                    "1.0");
            testCollection = service.createCollection("test");
            assertNotNull(testCollection);
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }
    
	private void initServer() {
        try {
            if (server == null) {
                server = new JettyStart();
                System.out.println("Starting standalone server...");
                server.run();
            }
        } catch(Exception e) {
            fail(e.getMessage());
        }
    }
    
    protected void tearDown() throws Exception {
        try {
            if (!((CollectionImpl) testCollection).isRemoteCollection()) {
                DatabaseInstanceManager dim =
                        (DatabaseInstanceManager) testCollection.getService(
                        "DatabaseInstanceManager", "1.0");
                dim.shutdown();
            }
            testCollection = null;
            
            System.out.println("tearDown PASSED");
            
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    /** Helper that performs an XQuery and does JUnit assertion on result size.
     * @see #queryResource(XQueryService, String, String, int, String)
     */
    @SuppressWarnings("unused")
	private ResourceSet queryResource(XQueryService service, String resource,
            String query, int expected) throws XMLDBException {
        return queryResource(service, resource, query, expected, null);
    }
    
    /** Helper that performs an XQuery and does JUnit assertion on result size.
     * @param service XQuery service
     * @param resource database resource (collection) to query
     * @param query
     * @param expected size of result
     * @param message for JUnit
     * @return a ResourceSet, allowing to do more assertions if necessary.
     * @throws XMLDBException
     */
    private ResourceSet queryResource(XQueryService service, String resource,
            String query, int expected, String message) throws XMLDBException {
        ResourceSet result = service.queryResource(resource, query);
        if(message == null)
            assertEquals(query, expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }
    
    /** For queries without associated data */
    private ResourceSet queryAndAssert(XQueryService service, String query,
            int expected, String message) throws XMLDBException {
        ResourceSet result = service.query(query);
        if(message == null)
            assertEquals(expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }
    
    /** For queries without associated data */
    private XQueryService getQueryService() throws XMLDBException {
        XQueryService service = (XQueryService) testCollection.getService(
                "XPathQueryService", "1.0");
        return service;
    }
    
    /** stores XML String and get Query Service
     * @param documentName to be stored in the DB
     * @param content to be stored in the DB
     * @return the XQuery Service
     * @throws XMLDBException
     */
    @SuppressWarnings("unused")
	private XQueryService storeXMLStringAndGetQueryService(String documentName,
            String content) throws XMLDBException {
        XMLResource doc =
                (XMLResource) testCollection.createResource(
                documentName, "XMLResource" );
        doc.setContent(content);
        testCollection.storeResource(doc);
        XQueryService service =
                (XQueryService) testCollection.getService(
                "XPathQueryService",
                "1.0");
        return service;
    }
    
    /**
     * @param result
     * @throws XMLDBException
     */
    @SuppressWarnings("unused")
	private void printResult(ResourceSet result) throws XMLDBException {
        for (ResourceIterator i = result.getIterator();
        i.hasMoreResources();
        ) {
            Resource r = i.nextResource();
            System.out.println(r.getContent());
        }
    }
    
    public void testAttributeConstructor() {
        try {
            XQueryService service = getQueryService();
            @SuppressWarnings("unused")
			ResourceSet result;
            
            result = queryAndAssert( service,
                    "<foo "+
                    " ampEntity=\"{('&amp;')}\"" +
                    " string=\"{(string('&amp;'))}\"" +
                    " ltEntity=\"{('&lt;')}\"" +
                    " gtEntity=\"{('&gt;')}\"" +
                    " aposEntity=\"{('&apos;')}\"" +
                    " quotEntity=\"{('&quot;')}\"" +
                    "/>",
                    1,  null );
            // TODO: could check result
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testStringConstructor() {
        try {
            XQueryService service = getQueryService();
            @SuppressWarnings("unused")
			ResourceSet result;
            
            result = queryAndAssert( service, "'&amp;'",1,null);
            result = queryAndAssert( service, "'&lt;'",1,null);
            result = queryAndAssert( service, "'&gt;'",1,null);
            result = queryAndAssert( service, "'&apos;'",1,null);
            result = queryAndAssert( service, "'&quot;'",1,null);
            // TODO: could check result
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testURIConstructor() {
    	try {
            XQueryService service = getQueryService();
            ResourceSet result;
            
            result = queryAndAssert(service, "xs:anyURI(\"index.xql?a=1&amp;b=2\")", 1, null);
            // TODO: could check result
            
            result = queryAndAssert(service, "xs:anyURI('a') le xs:anyURI('b')", 1, null);
            assertEquals("true",result.getResource(0).getContent());

            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(EntitiesTest.class);
    }
}
