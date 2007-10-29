package org.exist.xmldb;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.test.TestConstants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

public class CollectionTest extends TestCase {

	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	private final static String URI = "xmldb:exist://";
	
	public void testCreate() {
		try {
			Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
			CollectionManagementService service = (CollectionManagementService) 
				root.getService("CollectionManagementService", "1.0");
			Collection testCollection = service.createCollection(TestConstants.SPECIAL_NAME);
			assertNotNull(testCollection);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testRead() {
		try {
			Collection test = 
			DatabaseManager.getCollection(URI + TestConstants.SPECIAL_COLLECTION_URI, "admin", null);
			assertNotNull(test);
			Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
			test = root.getChildCollection(TestConstants.SPECIAL_NAME);
			assertNotNull(test);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            service.removeCollection(TestConstants.SPECIAL_NAME);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void tearDown() {
        try {
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
	 * @param args
	 */
	public static void main(String[] args) {
		TestRunner.run(CollectionTest.class);
	}

}
