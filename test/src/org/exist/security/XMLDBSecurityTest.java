package org.exist.security;

import org.exist.jetty.JettyStart;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.UserManagementService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.LinkedList;

import static org.junit.Assert.*;

@RunWith (Parameterized.class)
public class XMLDBSecurityTest {

    private static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private String baseUri;

    private static JettyStart server;

    public XMLDBSecurityTest(String baseUri) {
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
        User guest = ums.getUser("guest");
        // make myself the owner ;-)
        ums.chown(guest, "guest");
    }

    @Test (expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void worldChownResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "guest", "guest");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        User test2 = ums.getUser("guest");
        ums.chown(resource, test2, "guest");
    }

    @Test
    public void groupCreateSubColl() {
        try {
            Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
            CollectionManagementService cms = (CollectionManagementService)
                test.getService("CollectionManagementService", "1.0");
            Collection newCol = cms.createCollection("createdByTest2");
            assertNotNull(newCol);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void groupCreateResource() {
        try {
            Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
            @SuppressWarnings("unused")
			CollectionManagementService cms = (CollectionManagementService)
                test.getService("CollectionManagementService", "1.0");
            Resource resource = test.createResource("createdByTest2.xml", "XMLResource");
            resource.setContent("<testMe/>");
            test.storeResource(resource);

            resource = test.getResource("createdByTest2.xml");
            assertNotNull(resource);
            assertEquals("<testMe/>", resource.getContent().toString());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void groupRemoveCollection() {
        try {
            Collection root = DatabaseManager.getCollection(baseUri + "/db", "test2", "test2");
            CollectionManagementService cms = (CollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
            cms.removeCollection("securityTest1");
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void groupChmodCollection() {
        try {
            Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
            UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
            // grant myself all rights ;-)
            ums.chmod(0777);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void groupChmodResource() {
        try {
            Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
            Resource resource = test.getResource("test.xml");
            UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
            // grant myself all rights ;-)
            ums.chmod(resource, 0777);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test (expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownCollection() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        User test2 = ums.getUser("test2");
        ums.chown(test2, "users");
        Permission perms = ums.getPermissions(test);
        assertEquals("test2", perms.getOwner());
    }

    @Test (expected=XMLDBException.class)
    // only the owner or admin can chown a collection or resource
    public void groupChownResource() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseUri + "/db/securityTest1", "test2", "test2");
        Resource resource = test.getResource("test.xml");
        UserManagementService ums = (UserManagementService)
                test.getService("UserManagementService", "1.0");
        // grant myself all rights ;-)
        User test2 = ums.getUser("test2");
        ums.chown(resource, test2, "users");
    }

    @Before
    public void setup() {
        try {
            Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            GroupAider group = new GroupAider("users");
            ums.addRole(group);

            UserAider user = new UserAider("test1", group);
            user.setPassword("test1");
            ums.addUser(user);

            user = new UserAider("test2", group);
            user.setPassword("test2");
            ums.addUser(user);

            // create a collection /db/securityTest as user "test1"
            CollectionManagementService cms = (CollectionManagementService)
                    root.getService("CollectionManagementService", "1.0");
            Collection test = cms.createCollection("securityTest1");
            ums = (UserManagementService) test.getService("UserManagementService", "1.0");
            // pass ownership to test1
            User test1 = ums.getUser("test1");
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
            CollectionManagementService cms =
                    (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            if (root.getChildCollection("securityTest1") != null)
                cms.removeCollection("securityTest1");
            UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");
            User test1 = ums.getUser("test1");
            ums.removeUser(test1);
            User test2 = ums.getUser("test2");
            ums.removeUser(test2);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @BeforeClass
    public static void startServer() {
        try {
            Class<?> cl = Class.forName(DB_DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
            assertNotNull(root);
            
            server = new JettyStart();
            System.out.println("Starting standalone server...");
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopServer() {
        try {
         Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
            DatabaseInstanceManager mgr =
                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
        server.shutdown();
        server = null;
    }
}
