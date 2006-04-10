package org.exist.xmldb.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

public class CollectionTest extends TestCase {

	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	private final static String URI = "xmldb:exist://";
	private final static String ESC_COLL = "test%5B98%5D";
	
	public void testCreate() {
		try {
			Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
			CollectionManagementService service = (CollectionManagementService) 
				root.getService("CollectionManagementService", "1.0");
			Collection testCollection = service.createCollection(ESC_COLL);
			assertNotNull(testCollection);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testRead() {
		try {
			// this will fail!!!
			Collection test = 
			DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION + '/' + ESC_COLL, "admin", null);
			assertNotNull(test);
			Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
			test = root.getChildCollection(ESC_COLL);
			assertNotNull(test);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            service.removeCollection(ESC_COLL);
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
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TestRunner.run(CollectionTest.class);
	}

}
