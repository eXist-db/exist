package org.exist.xquery;

import java.net.BindException;
import java.util.Iterator;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.mortbay.util.MultiException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

public class SpecialNamesTest extends XMLTestCase {
    
    private static String uri = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    
    public static void setURI(String collectionURI) {
        uri = collectionURI;
    }
    
    private static StandaloneServer server = null;
    
    private Collection testCollection;
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
                server = new StandaloneServer();
                if (!server.isStarted()) {
                    try {
                        System.out.println("Starting standalone server...");
                        String[] args = {};
                        server.run(args);
                        while (!server.isStarted()) {
                            Thread.sleep(1000);
                        }
                    } catch (MultiException e) {
                        boolean rethrow = true;
                        Iterator i = e.getThrowables().iterator();
                        while (i.hasNext()) {
                            Exception e0 = (Exception)i.next();
                            if (e0 instanceof BindException) {
                                System.out.println("A server is running already !");
                                rethrow = false;
                                break;
                            }
                        }
                        if (rethrow) throw e;
                    }
                }
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
    private void printResult(ResourceSet result) throws XMLDBException {
        for (ResourceIterator i = result.getIterator();
        i.hasMoreResources();
        ) {
            Resource r = i.nextResource();
            System.out.println(r.getContent());
        }
    }
    
    public void testAttributes() {
        try {
            XQueryService service = getQueryService();
            ResourceSet result;
            
            result = queryAndAssert( service,
                    "<foo amp='x' lt='x' gt='x' apos='x' quot='x'/>",
                    1,  null );
            // TODO: could check result
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(SpecialNamesTest.class);
    }
}
