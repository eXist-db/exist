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

public class EntitiesTest {

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
        if(message == null) {
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
    public void attributeConstructor() throws XMLDBException {
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

    }

    @Test
    public void stringConstructor() throws XMLDBException {
        XQueryService service = getQueryService();
        @SuppressWarnings("unused")
        ResourceSet result;

        result = queryAndAssert( service, "'&amp;'",1,null);
        result = queryAndAssert( service, "'&lt;'",1,null);
        result = queryAndAssert( service, "'&gt;'",1,null);
        result = queryAndAssert( service, "'&apos;'",1,null);
        result = queryAndAssert( service, "'&quot;'",1,null);
        // TODO: could check result
    }

    @Test
    public void uriConstructor() throws XMLDBException {
        XQueryService service = getQueryService();
        ResourceSet result;

        result = queryAndAssert(service, "xs:anyURI(\"index.xql?a=1&amp;b=2\")", 1, null);
        // TODO: could check result

        result = queryAndAssert(service, "xs:anyURI('a') le xs:anyURI('b')", 1, null);
        assertEquals("true",result.getResource(0).getContent());
    }
}
