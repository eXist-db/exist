package org.exist.xmldb.test;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

public class SerializationTest extends XMLTestCase {

	private static final String TEST_COLLECTION_NAME = "test";

	private static final String XML =
		"<root xmlns=\"http://foo.com\">" +
		"	<entry>1</entry>" +
		"	<entry>2</entry>" +
		"</root>";
	
	private static final String XML_EXPECTED =
		"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" hitCount=\"2\">\n" + 
		"    <entry xmlns=\"http://foo.com\">1</entry>\n" + 
		"    <entry xmlns=\"http://foo.com\">2</entry>\n" + 
		"</exist:result>";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(SerializationTest.class);
	}

	private Database database;
	private Collection testCollection;
	
	public void testQueryResults() {
		try {
			XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
			ResourceSet result = service.query("declare namespace foo=\"http://foo.com\"; //foo:entry");
			Resource resource = result.getMembersAsResource();
			String str = resource.getContent().toString();
			System.out.println(str);
			assertXMLEqual(XML_EXPECTED, str);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
            Collection root =
                DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service =
                (CollectionManagementService) root.getService(
                    "CollectionManagementService",
                    "1.0");
            testCollection = service.createCollection(TEST_COLLECTION_NAME);
            assertNotNull(testCollection);
            
            XMLResource res = (XMLResource) 
            	testCollection.createResource("defaultns.xml", "XMLResource");
            res.setContent(XML);
            testCollection.storeResource(res);
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
        	e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() {
    	try {
	        Collection root =
	            DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
	        CollectionManagementService service =
	            (CollectionManagementService) root.getService(
	                "CollectionManagementService",
	                "1.0");
	        service.removeCollection(TEST_COLLECTION_NAME);
	        
	        DatabaseManager.deregisterDatabase(database);
	        DatabaseInstanceManager dim =
	            (DatabaseInstanceManager) testCollection.getService(
	                "DatabaseInstanceManager", "1.0");
	        dim.shutdown();
            database = null;
            testCollection = null;
	        System.out.println("tearDown PASSED");
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
    }
}