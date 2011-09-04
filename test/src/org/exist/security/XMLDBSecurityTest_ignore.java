package org.exist.security;

import org.junit.Before;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.Test;
import org.exist.jetty.JettyStart;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.UserManagementService;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.LinkedList;

import static org.junit.Assert.*;

@RunWith (Parameterized.class)
public class XMLDBSecurityTest_ignore {

    private static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private String baseUri;

    private static JettyStart server;

    public XMLDBSecurityTest_ignore(String baseUri) {
        this.baseUri = baseUri;
    }

    @Parameterized.Parameters
    public static LinkedList<String[]> instances() {
        LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String[] { "xmldb:exist://" });
        params.add(new String[] { "xmldb:exist://localhost:8088/xmlrpc" });
        return params;
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldCreateCollection() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        CollectionManagementService cms = (CollectionManagementService)
            test.getService("CollectionManagementService", "1.0");
        cms.createCollection("createdByGuest");
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldAddResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        Resource resource = test.createResource("createdByGuest", "XMLResource");
        resource.setContent("<testMe/>");
        test.storeResource(resource);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldRemoveCollection() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(baseUri + "/db", "guest", "guest");
        CollectionManagementService cms = (CollectionManagementService)
            root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodCollection() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        UserManagementService ums = (UserManagementService)
            test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(0777);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChmodResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService)
            test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test (expected=XMLDBException.class) // fails since guest has no write permissions
    public void worldChownCollection() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        UserManagementService ums = (UserManagementService)
            test.getService("UserManagementService", "1.0");
        Account guest = ums.getAccount("guest");
        // make myself the owner ;-)
        ums.chown(guest, "guest");
    }

    @Test (expected=XMLDBException.class)
    // only the owner or dba can chown a collection or resource
    public void worldChownResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        Account test2 = ums.getAccount("guest");
        ums.chown(resource, test2, "guest");
    }

    @Test
    public void groupCreateSubColl() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        CollectionManagementService cms = (CollectionManagementService)test.getService("CollectionManagementService", "1.0");
        Collection newCol = cms.createCollection("createdByTest2");
        assertNotNull(newCol);
    }

    @Test
    public void groupCreateResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        Resource resource = test.createResource("createdByTest2.xml", "XMLResource");
        resource.setContent("<testMe/>");
        test.storeResource(resource);

        resource = test.getResource("createdByTest2.xml");
        assertNotNull(resource);
        assertEquals("<testMe/>", resource.getContent().toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupRemoveCollection_canNotWriteParent() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(baseUri + "/db", "test2", "test2");
        CollectionManagementService cms = (CollectionManagementService)
            root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test
    public void groupRemoveCollection_canWriteParent() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        CollectionManagementService cms = (CollectionManagementService)
            root.getService("CollectionManagementService", "1.0");
        cms.removeCollection("securityTest1");
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodCollection_asNotOwnerAndNotDBA() throws XMLDBException {
    
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        UserManagementService ums = (UserManagementService)test.getService("UserManagementService", "1.0");

        // grant myself all rights ;-)
        ums.chmod(07777);
    }

    @Test
    public void groupChmodCollection_asOwner() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(07777);

        assertEquals("rwsrwsrwt", ums.getPermissions(test).toString());
    }

    @Test(expected=XMLDBException.class)
    public void groupChmodResource_asNotOwnerAndNotDBA() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test
    public void groupChmodResource_asOwner() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        ums.chmod(resource, 0777);
    }

    @Test (expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownCollection() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        Account test2 = ums.getAccount("test2");
        ums.chown(test2, "users");
        Permission perms = ums.getPermissions(test);
        assertEquals("test2", perms.getOwner().getName());
    }

    @Test (expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        Account test2 = ums.getAccount("test2");
        ums.chown(resource, test2, "users");
    }

    @Before
    public void setup() {
        try {
            Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            Account test1 = ums.getAccount("test1");
            if (test1 != null){
                ums.removeAccount(test1);
            }

            Account test2 = ums.getAccount("test2");
            if (test2 != null){
                ums.removeAccount(test2);
            }

            Group group = ums.getGroup("users");
            if (group != null){
                ums.removeGroup(group);
            }

            group = new GroupAider("exist","users");
            ums.addGroup(group);

            UserAider user = new UserAider("test1", group);
            user.setPassword("test1");
            ums.addAccount(user);

            user = new UserAider("test2", group);
            user.setPassword("test2");
            ums.addAccount(user);

            // create a collection /db/securityTest as user "test1"
            CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            Collection test = cms.createCollection("securityTest1");
            ums = (UserManagementService) test.getService("UserManagementService", "1.0");
            // pass ownership to test1
            test1 = ums.getAccount("test1");
            ums.chown(test1, "users");
            // full permissions for user and group, none for world
            ums.chmod(0770);

            test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test1", "test1");
            Resource resource = test.createResource("test.xml", "XMLResource");
            resource.setContent("<test/>");
            test.storeResource(resource);
            ums.chmod(resource, 0770);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void cleanup() {
        try {
            Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            CollectionManagementService cms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            if (root.getChildCollection("securityTest1") != null) {
                cms.removeCollection("securityTest1");
            }

            UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            Account test1 = ums.getAccount("test1");
            if (test1 != null) {
                ums.removeAccount(test1);
            }

            Account test2 = ums.getAccount("test2");
            if (test2 != null) {
                ums.removeAccount(test2);
            }

            Group group = ums.getGroup("users");
            if (group != null) {
                ums.removeGroup(group);
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @BeforeClass
    public static void startServer() {
        try {
//            Class<?> cl = Class.forName(DB_DRIVER);
//            Database database = (Database) cl.newInstance();
//            database.setProperty("create-database", "true");
//            DatabaseManager.registerDatabase(database);
//            Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
//            assertNotNull(root);
            
            System.out.println("Starting standalone server...");
            server = new JettyStart();
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopServer() {
//        try {
//         Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
//            DatabaseInstanceManager mgr =
//                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
//            mgr.shutdown();
//        } catch (XMLDBException e) {
//            e.printStackTrace();
//        }
        System.out.println("Shutdown standalone server...");
        server.shutdown();
        server = null;
    }
}
