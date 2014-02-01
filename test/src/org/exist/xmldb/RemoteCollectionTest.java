/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb;

import java.util.ArrayList;

import junit.textui.TestRunner;

import org.exist.xquery.util.URIUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/** A test case for accessing collections remotely
 * @author jmv
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class RemoteCollectionTest extends RemoteDBTest {

	private final static String XML_CONTENT = "<xml/>";
	private final static String BINARY_CONTENT = "TEXT";
	
	public RemoteCollectionTest(String name) {
		super(name);
	}
	
	protected void setUp() {
		try {
			//Don't worry about closing the server : the shutdownDB hook will do the job
			initServer();
			setUpRemoteDatabase();
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}

  	protected void tearDown() {
  		try {
  			removeCollection();
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
  	
    public void testIndexQueryService() {
		// TODO .............
	}

	public void testGetServices() {
		try {
		    Service[] services = getCollection().getServices();
		    assertEquals(6, services.length);
		    assertEquals(RemoteXPathQueryService.class, services[0].getClass());
		    assertEquals(RemoteCollectionManagementService.class, services[1].getClass());
		    assertEquals(RemoteUserManagementService.class, services[2].getClass());
		    assertEquals(RemoteDatabaseInstanceManager.class, services[3].getClass());
		    assertEquals(RemoteIndexQueryService.class, services[4].getClass());
		    assertEquals(RemoteXUpdateQueryService.class, services[5].getClass());

        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	public void testIsRemoteCollection() {
		try {
			assertTrue(getCollection().isRemoteCollection());
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	public void testGetPath() {
		try {
			assertEquals(XmldbURI.ROOT_COLLECTION + "/" + getTestCollectionName(), URIUtils.urlDecodeUtf8(getCollection().getPath()));
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	public void testCreateResource() {
		try {
		    Collection collection = getCollection();
		    { // XML resource:
		        Resource resource = collection.createResource("testresource", "XMLResource");
			    assertNotNull(resource);
			    assertEquals(collection, resource.getParentCollection());
			    resource.setContent("<?xml version='1.0'?><xml/>");
			    collection.storeResource(resource);
		    }
		    { // binary resource:
		        Resource resource = collection.createResource("testresource", "BinaryResource");
			    assertNotNull(resource);
			    assertEquals(collection, resource.getParentCollection());
			    resource.setContent("some random binary data here :-)");
			    collection.storeResource(resource);
		    }
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	public void testGetNonExistentResource() {
		try {
			System.out.println("Retrieving non-existing resource");
			Collection collection = getCollection();
			Resource resource = collection.getResource("unknown.xml");
			assertNull(resource);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	public void testListResources() {
		try {
		    ArrayList<String> xmlNames = new ArrayList<String>();
		    xmlNames.add("xml1");
		    xmlNames.add("xml2");
		    xmlNames.add("xml3");
		    createResources(xmlNames, "XMLResource");
		    
		    ArrayList<String> binaryNames = new ArrayList<String>();
		    binaryNames.add("b1");
		    binaryNames.add("b2");
		    createResources(binaryNames, "BinaryResource");
	
		    String[] actualContents = getCollection().listResources();
		    System.out.println("Resources found: " + actualContents.length);
		    for (int i = 0; i < actualContents.length; i++) {
		        xmlNames.remove(actualContents[i]);
		        binaryNames.remove(actualContents[i]);
		    }
		    assertEquals(0, xmlNames.size());
		    assertEquals(0, binaryNames.size());
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
	}
	
	/**
	 * Trying to access a collection where the parent collection does
	 * not exist caused NullPointerException on DatabaseManager.getCollection() method.
	 */ 
	public void testParent() {
		try {
			Collection c = DatabaseManager.getCollection(URI + XmldbURI.ROOT_COLLECTION, "admin", "");
			assertNull(c.getChildCollection("b"));
			
			System.err.println("col="+ c.getName());
			String parentName = c.getName() + "/" + System.currentTimeMillis();
			String colName = parentName + "/a";
			c = DatabaseManager.getCollection(URI + parentName, "admin", null);
			assertNull(c);
			
			// following fails for XmlDb 20051203
			c = DatabaseManager.getCollection(URI + colName, "admin", null);
			assertNull(c);
		} catch (XMLDBException xe) {
			System.err.println("Unexpected Exception occured: " + xe.getMessage());
			xe.printStackTrace();
		}
	}
	
    private void createResources(ArrayList<String> names, String type) {
    	try {
	        for (String name : names) {
	            Resource res = getCollection().createResource(name, type);
	            if(type.equals("XMLResource"))
	            	res.setContent(XML_CONTENT);
	            else
	            	res.setContent(BINARY_CONTENT);
	            getCollection().storeResource(res);
	        }
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
    }

    public static void main(String[] args) {
		TestRunner.run(RemoteCollectionTest.class);
		//Explicit shutdownDB for the shutdownDB hook
		System.exit(0);
	}
}
