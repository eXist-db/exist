package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SpecialNamesTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private Collection testCollection;
    @SuppressWarnings("unused")
	private String query;

    @Before
    public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        final CollectionManagementService service =
                (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection("test");
        assertNotNull(testCollection);
    }

    @After
    public void tearDown() throws Exception {
        final CollectionManagementService service =
                (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                        "CollectionManagementService",
                        "1.0");
        service.removeCollection("test");
        testCollection = null;
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
        if(message == null) {
            assertEquals(query, expected, result.getSize());
        } else {
            assertEquals(message, expected, result.getSize());
        }
        return result;
    }
    
    /** For queries without associated data */
    private ResourceSet queryAndAssert(XQueryService service, String query,
            int expected, String message) throws XMLDBException {
        ResourceSet result = service.query(query);
        if (message == null) {
            assertEquals(expected, result.getSize());
        } else {
            assertEquals(message, expected, result.getSize());
        }
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

    @Test
    public void attributes() throws XMLDBException {
        XQueryService service = getQueryService();
        @SuppressWarnings("unused")
        ResourceSet result;

        result = queryAndAssert( service,
                "<foo amp='x' lt='x' gt='x' apos='x' quot='x'/>",
                1,  null );
        // TODO: could check result
    }
}
