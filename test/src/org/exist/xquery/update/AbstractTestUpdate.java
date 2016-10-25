package org.exist.xquery.update;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public abstract class AbstractTestUpdate {
    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer();

    private Collection testCollection;

    private final static String COLLECTION_CONF = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "    <index>\n" +
            "        <!-- Range index for fn:id and fn:idref -->" +
            "        <range>" +
            "            <ID/>" +
            "            <IDREF/>" +
            "        </range>" +
            "    </index>" +
            "</collection>";

    @Before
    public void setUp() throws Exception {
        CollectionManagementService service =
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");

        final Collection testCollection_confCollection = service.createCollection("system/config/db/test");
        final Resource conf = testCollection_confCollection.createResource("collection.xconf", "XMLResource");
        conf.setContent(COLLECTION_CONF);
        testCollection_confCollection.storeResource(conf);

        testCollection = service.createCollection("test");
    }

    @After
    public void tearDown() throws XMLDBException {
        CollectionManagementService service =
                (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                        "CollectionManagementService",
                        "1.0");
        service.removeCollection("test");
        testCollection = null;
    }

    /** stores XML String and get Query Service
     * @param documentName to be stored in the DB
     * @param content to be stored in the DB
     * @return the XQuery Service
     * @throws XMLDBException
     */
    protected XQueryService storeXMLStringAndGetQueryService(String documentName,
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

    /** Helper that performs an XQuery and does JUnit assertion on result size.
     * @see #queryResource(XQueryService, String, String, int, String)
     */
    protected ResourceSet queryResource(XQueryService service, String resource,
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
    protected ResourceSet queryResource(XQueryService service, String resource,
            String query, int expected, String message) throws XMLDBException {
        ResourceSet result = service.queryResource(resource, query);
        if(message == null)
            assertEquals(query, expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }
}
