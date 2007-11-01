package org.exist.collections.triggers;

import org.exist.TestUtils;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.util.LinkedList;

/**
 * Test proper configuration of triggers in collection.xconf, in particular if there's
 * only a configuration for the parent collection, but not the child. The trigger should
 * be created with the correct base collection.
 */
@RunWith(Parameterized.class)
public class TriggerConfigTest {

    @Parameterized.Parameters
	public static LinkedList<String[]> data() {
		LinkedList<String[]> params = new LinkedList<String[]>();
		params.add(new String[] { "/db/triggers" });
		params.add(new String[] { "/db/triggers/sub1" });
        params.add(new String[] { "/db/triggers/sub1/sub2" });
        return params;
	}

    private static final String COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
	    "  <exist:triggers>" +
		"     <exist:trigger event='store,remove' class='org.exist.collections.triggers.TestTrigger'/>" +
        "  </exist:triggers>" +
        "</exist:collection>";

    private static final String EMPTY_COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
        "</exist:collection>";

    private final static String DOCUMENT_CONTENT =
		  "<test>"
		+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
		+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
		+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
		+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
		+ "</test>";

    private final static String BASE_URI = "xmldb:exist://";

    private String testCollection;

    public TriggerConfigTest(String testCollection) {
        this.testCollection = testCollection;
    }

    @Test
    public void storeDocument() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(COLLECTION_CONFIG);
            
            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);
            printMessages();
            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.queryResource("messages.xml", "string(//event[last()]/@collection)");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void removeDocument() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(COLLECTION_CONFIG);

            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            root.removeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.queryResource("messages.xml", "string(//event[last()]/@collection)");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void removeTriggers() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(EMPTY_COLLECTION_CONFIG);

            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.query("doc('" + testCollection + "/messages.xml')/events/event[@id = 'STORE']");
            assertEquals("No trigger should have fired. Configuration was removed", 0, result.getSize());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void updateTriggers() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(EMPTY_COLLECTION_CONFIG);

            Collection configCol =  DatabaseManager.getCollection(BASE_URI + "/db/system/config" + testCollection, "admin", null);
            Resource resource = configCol.createResource("collection.xconf", "XMLResource");
            resource.setContent(COLLECTION_CONFIG);
            configCol.storeResource(resource);

            resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.query("doc('" + testCollection + "/messages.xml')/events/event[@id = 'STORE']/string(@collection)");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private void printMessages() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            XMLResource messages = (XMLResource) root.getResource("messages.xml");
            System.out.println(messages.getContent().toString());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void cleanDB() {
        try {
            Collection config = DatabaseManager.getCollection(BASE_URI + "/db/system/config" + testCollection, "admin", null);
            if (config != null) {
                CollectionManagementService mgmt = (CollectionManagementService) config.getService("CollectionManagementService", "1.0");
                mgmt.removeCollection(".");
            }
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", null);
            Resource resource = root.getResource("messages.xml");
            if (resource != null) {
                root.removeResource(resource);
            }
            resource = root.getResource("data.xml");
            if (resource != null) {
                root.removeResource(resource);
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @BeforeClass
    public static void initDB() {
        // initialize XML:DB driver
        try {
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection testCol = mgmt.createCollection("triggers");

            for (int i = 1; i <= 2; i++) {
                mgmt = (CollectionManagementService) testCol.getService("CollectionManagementService", "1.0");
                testCol = mgmt.createCollection("sub" + i);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void closeDB() {
        TestUtils.cleanupDB();
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}