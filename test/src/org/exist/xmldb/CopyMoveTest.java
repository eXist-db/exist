package org.exist.xmldb;

import org.junit.After;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import static org.exist.xmldb.XmldbLocalTests.*;


public class CopyMoveTest {

    private final static String TEST_COLLECTION = "testCopyMove";

    @Test
    public void copyResourceChangeName() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XMLResource original = (XMLResource) testCollection.createResource("original", XMLResource.RESOURCE_TYPE);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) testCollection.getService("CollectionManagementService", "1.0");
        cms.copyResource("original", "", "duplicate");
        assertEquals(2, testCollection.getResourceCount());
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
        System.out.println(duplicate.getContent());
    }

    @Test
    public void queryCopiedResource() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XMLResource original = (XMLResource) testCollection.createResource("original", XMLResource.RESOURCE_TYPE);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        CollectionManagementServiceImpl cms = (CollectionManagementServiceImpl) testCollection.getService("CollectionManagementService", "1.0");
        cms.copyResource("original", "", "duplicate");
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
        XPathQueryService xq = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
        ResourceSet rs = xq.queryResource("duplicate", "/sample");
        assertEquals(1, rs.getSize());
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Database database = (Database) Class.forName(DRIVER).newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService cms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection testCollection = cms.createCollection(TEST_COLLECTION);
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        Account guest = ums.getAccount(GUEST_UID);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod(Permission.DEFAULT_PERM);
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);

        //shutdown the db
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
    }
}