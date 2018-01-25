package org.exist.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import static org.exist.xmldb.XmldbLocalTests.*;


public class CopyMoveTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    private final static String TEST_COLLECTION = "testCopyMove";

    @Test
    public void copyResourceChangeName() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XMLResource original = (XMLResource) testCollection.createResource("original", XMLResource.RESOURCE_TYPE);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        EXistCollectionManagementService cms = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        cms.copyResource("original", "", "duplicate");
        assertEquals(2, testCollection.getResourceCount());
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
    }

    @Test
    public void queryCopiedResource() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XMLResource original = (XMLResource) testCollection.createResource("original", XMLResource.RESOURCE_TYPE);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        EXistCollectionManagementService cms = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        cms.copyResource("original", "", "duplicate");
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
        XPathQueryService xq = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
        ResourceSet rs = xq.queryResource("duplicate", "/sample");
        assertEquals(1, rs.getSize());
    }
    
    @Test
    public void changePermissionsAfterCopy() throws XMLDBException {
        final String collectionURL = ROOT_URI + "/" + TEST_COLLECTION;
        final String originalResource = "original.xml";
        final String copyResource = "copy.xml";
        
        final String resourceURL = collectionURL + "/" + originalResource;
        
        //get collection & services
        EXistCollection col = (EXistCollection)DatabaseManager.getCollection(collectionURL);
        EXistCollectionManagementService service = (EXistCollectionManagementService) col.getService("CollectionManagementService", "1.0");
        UserManagementService ums = (UserManagementService)DatabaseManager.getCollection(collectionURL, ADMIN_UID, ADMIN_PWD).getService("UserManagementService", "1.0");
        
        //store xml document
        XMLResource original = (XMLResource) col.createResource(originalResource, XMLResource.RESOURCE_TYPE);
        original.setContent("<sample/>");
        col.storeResource(original);

        //get original resource
        Resource orgnRes = col.getResource(originalResource);

        //check permission before copy
        Permission prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());
        
        //copy
        service.copyResource(XmldbURI.create(resourceURL), col.getPathURI(), XmldbURI.create(copyResource));

        //check permission after copy
        prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());

        //get copy resource
        Resource copyRes = col.getResource(copyResource);
        
        //change permission on copy
        Account admin = ums.getAccount(ADMIN_UID);
        ums.chown(copyRes, admin, admin.getPrimaryGroup());
        ums.chmod(copyRes, "rwx--x---");
        
        //check permission of copy
        prm = ums.getPermissions(copyRes);
        assertEquals("rwx--x---", prm.toString());

        //check permission of original
        prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());
    }

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService cms = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        final Collection testCollection = cms.createCollection(TEST_COLLECTION);
        final UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        final Account guest = ums.getAccount(GUEST_UID);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        final CollectionManagementService cms = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);
    }
}