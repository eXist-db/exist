/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb.test;

import java.util.ArrayList;
import java.util.Iterator;

import junit.textui.TestRunner;

import org.exist.schema.RemoteSchemaService;
import org.exist.xmldb.RemoteCollectionManagementService;
import org.exist.xmldb.RemoteDatabaseInstanceManager;
import org.exist.xmldb.RemoteIndexQueryService;
import org.exist.xmldb.RemoteUserManagementService;
import org.exist.xmldb.RemoteXPathQueryService;
import org.exist.xmldb.RemoteXUpdateQueryService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/** WORK IN PROGRESS !!!
 * @author jmv
 */
public class RemoteCollectionTest extends RemoteDBTest {

	private final static String XML_CONTENT = "<xml/>";
	private final static String BINARY_CONTENT = "TEXT";
	
	/**
	 * @param name
	 */
	public RemoteCollectionTest(String name) {
		super(name);
	}
	
	protected void setUp() throws Exception {
		setUpRemoteDatabase();
	}

  	protected void tearDown() throws Exception {
	    removeCollection();
	}

    public void testIndexQueryService() {
		// TODO .............
	}

	public void testGetServices() throws XMLDBException {
	    Service[] services = getCollection().getServices();
	    assertEquals(7, services.length);
	    assertEquals(RemoteXPathQueryService.class, services[0].getClass());
	    assertEquals(RemoteCollectionManagementService.class, services[1].getClass());
	    assertEquals(RemoteUserManagementService.class, services[2].getClass());
	    assertEquals(RemoteDatabaseInstanceManager.class, services[3].getClass());
	    assertEquals(RemoteIndexQueryService.class, services[4].getClass());
	    assertEquals(RemoteXUpdateQueryService.class, services[5].getClass());
	    assertEquals(RemoteSchemaService.class, services[6].getClass());
	}
	
	public void testIsRemoteCollection() throws XMLDBException {
	    assertTrue(getCollection().isRemoteCollection());
	}
	
	public void testGetPath() throws XMLDBException {
	    assertEquals("/db/" + getTestCollectionName(), getCollection().getPath());
	}
	
	public void testCreateResource() throws XMLDBException {
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
	}
	
	public void testGetNonExistentResource() throws XMLDBException {
		System.out.println("Retrieving non-existing resource");
		Collection collection = getCollection();
		Resource resource = collection.getResource("unknown.xml");
		assertNull(resource);
	}
	
	public void testListResources() throws XMLDBException {
	    ArrayList xmlNames = new ArrayList();
	    xmlNames.add("xml1");
	    xmlNames.add("xml2");
	    xmlNames.add("xml3");
	    createResources(xmlNames, "XMLResource");
	    
	    ArrayList binaryNames = new ArrayList();
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
	        
	}

    /**
     * @param xmlnames
     * @param string
     */
    private void createResources(ArrayList names, String type) throws XMLDBException {
        for (Iterator i = names.iterator(); i.hasNext(); ) {
            Resource res = getCollection().createResource((String) i.next(), type);
            if(type.equals("XMLResource"))
            	res.setContent(XML_CONTENT);
            else
            	res.setContent(BINARY_CONTENT);
            getCollection().storeResource(res);
        }
    }

    public static void main(String[] args) {
    	TestRunner.run(RemoteCollectionTest.class);
	}
}
