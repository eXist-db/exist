package org.exist.security;

import java.util.Arrays;

import org.exist.TestUtils;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.junit.runners.Parameterized;
import org.exist.jetty.JettyStart;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Adam Retter <adam@existsolutions.com.com>
 */

/**
 * Ensures that security manager data, accounts, groups (and associations)
 * are correctly persisted across database restarts
 */
@RunWith (Parameterized.class)
public class SecurityManagerRoundtripTest {

    private JettyStart server;

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "local", "xmldb:exist://" },
            { "remote", "xmldb:exist://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc" }
        });
    };
    
    @Parameter
    public String apiName;
    
    @Parameter(value = 1)
    public String baseUri;

    @Before
    public void startServer() {
        try {
            server = new JettyStart();
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void stopServer() {
        TestUtils.cleanupDB();
        server.shutdown();
        server = null;
    }

    @Test
    public void checkGroupMembership() throws XMLDBException, PermissionDeniedException {

        Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");

        final String group1Name = "testGroup1";
        final String group2Name = "testGroup2";
        final String userName = "testUser";
        Group group1 = new GroupAider(group1Name);
        Group group2 = new GroupAider(group2Name);
        Account user = new UserAider(userName, group1);

        try {
            ums.addGroup(group1);
            ums.addGroup(group2);

            ums.addAccount(user);
            ums.getAccount(userName);
            user.addGroup(group2);

            ums.updateAccount(user);

            /*** RESTART THE SERVER ***/
            stopServer();
            startServer();
            /**************************/

            root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            ums = (UserManagementService)root.getService("UserManagementService", "1.0");

            user = ums.getAccount(userName);
            assertNotNull(user);

            Group defaultGroup = user.getDefaultGroup();
            assertNotNull(defaultGroup);
            assertEquals(group1Name, defaultGroup.getName());

            String groups[] = user.getGroups();
            assertNotNull(groups);
            assertEquals(2, groups.length);
            assertEquals(group1Name, groups[0]);
            assertEquals(group2Name, groups[1]);

        } finally {
            //cleanup
            try { ums.removeGroup(group1); } catch(Exception e) {}
            try { ums.removeGroup(group2); } catch(Exception e) {}
            try { ums.removeAccount(user); } catch(Exception e) {}
        }
    }

    @Test
    public void checkPrimaryGroupRemainsDBA() throws XMLDBException, PermissionDeniedException {

        Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");

        final String group1Name = "testGroup1";
        final String group2Name = "testGroup2";
        final String userName = "testUser";
        Group group1 = new GroupAider(group1Name);
        Group group2 = new GroupAider(group2Name);
        Account user = new UserAider(userName, ums.getGroup(SecurityManager.DBA_GROUP)); //set users primary group as DBA

        try {
            ums.addGroup(group1);
            ums.addGroup(group2);

            ums.addAccount(user);
            ums.getAccount(userName);
            user.addGroup(group1);
            user.addGroup(group2);

            ums.updateAccount(user);

            /*** RESTART THE SERVER ***/
            stopServer();
            startServer();
            /**************************/

            root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            ums = (UserManagementService)root.getService("UserManagementService", "1.0");

            user = ums.getAccount(userName);
            assertNotNull(user);

            Group defaultGroup = user.getDefaultGroup();
            assertNotNull(defaultGroup);
            assertEquals(SecurityManager.DBA_GROUP, defaultGroup.getName());

            String groups[] = user.getGroups();
            assertNotNull(groups);
            assertEquals(3, groups.length);
            assertEquals(SecurityManager.DBA_GROUP, groups[0]);
            assertEquals(group1Name, groups[1]);
            assertEquals(group2Name, groups[2]);

        } finally {
            //cleanup
            try { ums.removeGroup(group1); } catch(Exception e) {}
            try { ums.removeGroup(group2); } catch(Exception e) {}
            try { ums.removeAccount(user); } catch(Exception e) {}
        }
    }

    @Test
    public void checkPrimaryGroupStability() throws XMLDBException, PermissionDeniedException {

        Collection root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");

        final String group1Name = "testGroupA";
        final String group2Name = "testGroupB";
        final String userName = "testUserA";
        Group group1 = new GroupAider(group1Name);
        Group group2 = new GroupAider(group2Name);
        Account user = new UserAider(userName, group1); //set users primary group as group1

        try {
            ums.addGroup(group1);
            ums.addGroup(group2);

            ums.addAccount(user);
            ums.getAccount(userName);
            user.addGroup(group2Name);

            ums.updateAccount(user);

            /*** RESTART THE SERVER ***/
            stopServer();
            startServer();
            /**************************/

            root = DatabaseManager.getCollection(baseUri + "/db", "admin", "");
            ums = (UserManagementService)root.getService("UserManagementService", "1.0");

            user = ums.getAccount(userName);
            assertNotNull(user);

            Group defaultGroup = user.getDefaultGroup();
            assertNotNull(defaultGroup);
            assertEquals(group1Name, defaultGroup.getName());

            String groups[] = user.getGroups();
            assertNotNull(groups);
            assertEquals(2, groups.length);
            assertEquals(group1Name, groups[0]);
            assertEquals(group2Name, groups[1]);

        } finally {
            //cleanup
            try { ums.removeGroup(group1); } catch(Exception e) {}
            try { ums.removeGroup(group2); } catch(Exception e) {}
            try { ums.removeAccount(user); } catch(Exception e) {}
        }
    }
}
