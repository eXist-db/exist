/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb.test;

import java.util.ArrayList;
import java.util.Iterator;

import junit.framework.TestCase;
import org.exist.Server;
import org.exist.schema.RemoteSchemaService;
import org.exist.xmldb.RemoteCollection;
import org.exist.xmldb.RemoteCollectionManagementService;
import org.exist.xmldb.RemoteDatabaseInstanceManager;
import org.exist.xmldb.RemoteIndexQueryService;
import org.exist.xmldb.RemoteUserManagementService;
import org.exist.xmldb.RemoteXPathQueryService;
import org.exist.xmldb.RemoteXUpdateQueryService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/** WORK IN PROGRESS !!!
 * @author jmv
 */
public class RemoteCollectionTest extends TestCase {
    protected final static String URI = "xmldb:exist://localhost:8081/exist/xmlrpc";
    private final static String COLLECTION_NAME = "unit-testing-collection";
    private final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";
    private RemoteCollection collection = null;

	/**
	 * @param name
	 */
	public RemoteCollectionTest(String name) {
		super(name);
	}
	
	/** ? @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		String[] args = {"standalone"};
		//Server.main(args);
		// Thread ??
		Server.main(new String[] { } );

        Class cl = Class.forName(DB_DRIVER);
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        
        Collection rootCollection = DatabaseManager.getCollection(URI + "/db", "admin", null);
        
        Collection childCollection = rootCollection.getChildCollection(COLLECTION_NAME);
        if (childCollection == null) {        
	        CollectionManagementService cms = (CollectionManagementService) rootCollection.getService("CollectionManagementService", "1.0");
	        collection = (RemoteCollection) cms.createCollection(COLLECTION_NAME);
        } else {
            throw new Exception("Cannot run test because the collection /db/" + COLLECTION_NAME + " already " +
                "exists. If it is a left-over of a previous test run, please remove it manually.");
        }

	}
	/** ? @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
	    Collection rootCollection = DatabaseManager.getCollection(URI + "/db", "admin", null);
		CollectionManagementService cms = (CollectionManagementService) rootCollection.getService("CollectionManagementService", "1.0");
		cms.removeCollection(COLLECTION_NAME);
		Server.main(new String[] { "shutdown" } );
	}
	public void testIndexQueryService() {
		// TODO .............
	}

	public void testGetServices() throws XMLDBException {
	    Service[] services = collection.getServices();
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
	    assertTrue(collection.isRemoteCollection());
	}
	
	public void testGetPath() throws XMLDBException {
	    assertEquals("/db/" + COLLECTION_NAME, collection.getPath());
	}
	
	public void testCreateResource() throws XMLDBException {
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

	    String[] actualContents = collection.listResources();
	    System.out.println(actualContents);
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
        for (Iterator i = names.iterator(); i.hasNext(); )
            collection.createResource((String) i.next(), type);        
    }

}
