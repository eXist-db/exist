package org.exist.xmldb;

import org.exist.storage.DBBroker;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;
import junit.textui.TestRunner;


public class CopyMoveTest extends TestCase {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	public static void main(String[] args) {
		TestRunner.run(CopyMoveTest.class);
	}
	
	public CopyMoveTest(String name) {
		super(name);
	}
	
	public void testCopyResourceChangeName() {	
		Collection c =null;
		try {
			c = setupTestCollection();
			XMLResource original = (XMLResource) c.createResource("original", XMLResource.RESOURCE_TYPE);
			original.setContent("<sample/>");
			c.storeResource(original);
			CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) c.getService("CollectionManagementService", "1.0");
			cms.copyResource("original", "", "duplicate");
			assertEquals(2, c.getResourceCount());
			XMLResource duplicate = (XMLResource) c.getResource("duplicate");
			assertNotNull(duplicate);
			System.out.println(duplicate.getContent());
        } catch (Exception e) {            
            fail(e.getMessage()); 			
		} finally {
			closeCollection(c);
		}
	}

	public void testQueryCopiedResource() {		
		Collection c = null;
		try {
			c = setupTestCollection();		
			XMLResource original = (XMLResource) c.createResource("original", XMLResource.RESOURCE_TYPE);
			original.setContent("<sample/>");
			c.storeResource(original);
			CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) c.getService("CollectionManagementService", "1.0");
			cms.copyResource("original", "", "duplicate");
			XMLResource duplicate = (XMLResource) c.getResource("duplicate");
			assertNotNull(duplicate);
			XPathQueryService xq = (XPathQueryService) c.getService("XPathQueryService", "1.0");
			ResourceSet rs = xq.queryResource("duplicate", "/sample");
			assertEquals(1, rs.getSize());
        } catch (Exception e) {            
            fail(e.getMessage()); 			
		} finally {
			closeCollection(c);
		}
	}

	private Collection setupTestCollection() {
		try {
			Collection root = DatabaseManager.getCollection(URI);
			CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			Collection c = root.getChildCollection("test");
			if(c != null)
				rootcms.removeCollection("test");
			rootcms.createCollection("test");
			c = DatabaseManager.getCollection(URI+"/test");
			assertNotNull(c);
			return c;
	    } catch (Exception e) {            
	        fail(e.getMessage()); 
		}
	    return null;
	}

	protected void setUp() {
		try {
			// initialize driver
			Database database = (Database) Class.forName(DRIVER).newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
        } catch (Exception e) {            
            fail(e.getMessage()); 
		}
	}
	
	private void closeCollection(Collection collection) {
		try {
			if (null != collection) {		
				collection.close();
			}
	    } catch (Exception e) {            
	        fail(e.getMessage()); 
		}
	}
	
}
