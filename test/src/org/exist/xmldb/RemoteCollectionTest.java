/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb;

import java.util.ArrayList;

import org.exist.xquery.util.URIUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.*;

/** A test case for accessing collections remotely
 * @author jmv
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class RemoteCollectionTest extends RemoteDBTest {

	private final static String XML_CONTENT = "<xml/>";
	private final static String BINARY_CONTENT = "TEXT";

	@Before
	public void setUp() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
		//Don't worry about closing the server : the shutdownDB hook will do the job
		initServer();
		setUpRemoteDatabase();
	}

    @After
  	public void tearDown() {
        removeCollection();
	}

    @Ignore
    @Test
    public void indexQueryService() {
		// TODO .............
	}

    @Test
	public void getServices() throws XMLDBException {
        Service[] services = getCollection().getServices();
        assertEquals(6, services.length);
        assertEquals(RemoteXPathQueryService.class, services[0].getClass());
        assertEquals(RemoteCollectionManagementService.class, services[1].getClass());
        assertEquals(RemoteUserManagementService.class, services[2].getClass());
        assertEquals(RemoteDatabaseInstanceManager.class, services[3].getClass());
        assertEquals(RemoteIndexQueryService.class, services[4].getClass());
        assertEquals(RemoteXUpdateQueryService.class, services[5].getClass());
	}

    @Test
	public void isRemoteCollection() throws XMLDBException {
        assertTrue(getCollection().isRemoteCollection());
	}

    @Test
	public void getPath() throws XMLDBException {
        assertEquals(XmldbURI.ROOT_COLLECTION + "/" + getTestCollectionName(), URIUtils.urlDecodeUtf8(getCollection().getPath()));
	}

    @Test
	public void createResource() throws XMLDBException {
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

    @Test
	public void getNonExistentResource() throws XMLDBException {
        Collection collection = getCollection();
        Resource resource = collection.getResource("unknown.xml");
        assertNull(resource);
	}

    @Test
	public void listResources() throws XMLDBException {
        ArrayList<String> xmlNames = new ArrayList<>();
        xmlNames.add("xml1");
        xmlNames.add("xml2");
        xmlNames.add("xml3");
        createResources(xmlNames, "XMLResource");

        ArrayList<String> binaryNames = new ArrayList<>();
        binaryNames.add("b1");
        binaryNames.add("b2");
        createResources(binaryNames, "BinaryResource");

        String[] actualContents = getCollection().listResources();
        for (int i = 0; i < actualContents.length; i++) {
            xmlNames.remove(actualContents[i]);
            binaryNames.remove(actualContents[i]);
        }
        assertEquals(0, xmlNames.size());
        assertEquals(0, binaryNames.size());
	}
	
	/**
	 * Trying to access a collection where the parent collection does
	 * not exist caused NullPointerException on DatabaseManager.getCollection() method.
	 */
    @Test
	public void parent() throws XMLDBException {
        Collection c = DatabaseManager.getCollection(URI + XmldbURI.ROOT_COLLECTION, "admin", "");
        assertNull(c.getChildCollection("b"));

        String parentName = c.getName() + "/" + System.currentTimeMillis();
        String colName = parentName + "/a";
        c = DatabaseManager.getCollection(URI + parentName, "admin", null);
        assertNull(c);

        // following fails for XmlDb 20051203
        c = DatabaseManager.getCollection(URI + colName, "admin", null);
        assertNull(c);
	}
	
    private void createResources(ArrayList<String> names, String type) throws XMLDBException {
        for (String name : names) {
            Resource res = getCollection().createResource(name, type);
            if(type.equals("XMLResource")) {
                res.setContent(XML_CONTENT);
            } else {
                res.setContent(BINARY_CONTENT);
            }
            getCollection().storeResource(res);
        }
    }
}
