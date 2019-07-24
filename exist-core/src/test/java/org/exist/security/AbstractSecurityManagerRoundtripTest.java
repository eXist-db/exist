package org.exist.security;

import java.io.IOException;

import org.exist.EXistException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.base.XMLDBException;

/**
 * Ensures that security manager data, accounts, groups (and associations)
 * are correctly persisted across database restarts
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public abstract class AbstractSecurityManagerRoundtripTest {

    protected abstract String getBaseUri();

    protected abstract void restartServer() throws XMLDBException, IOException;

    @Test
    public void checkGroupMembership() throws XMLDBException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {

        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            restartServer();
            /**************************/

            root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            final Account u1 = ums.getAccount(userName);
            if (u1 != null) {
                ums.removeAccount(u1);
            }
            final Group g1 = ums.getGroup(group1Name);
            if (g1 != null) {
                ums.removeGroup(g1);
            }
            final Group g2 = ums.getGroup(group2Name);
            if (g2 != null) {
                ums.removeGroup(g2);
            }
        }
    }

    @Test
    public void checkPrimaryGroupRemainsDBA() throws XMLDBException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {

        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            restartServer();
            /**************************/

            root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            final Account u1 = ums.getAccount(userName);
            if (u1 != null) {
                ums.removeAccount(u1);
            }
            final Group g1 = ums.getGroup(group1Name);
            if (g1 != null) {
                ums.removeGroup(g1);
            }
            final Group g2 = ums.getGroup(group2Name);
            if (g2 != null) {
                ums.removeGroup(g2);
            }
        }
    }

    @Test
    public void checkPrimaryGroupStability() throws XMLDBException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {

        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            restartServer();
            /**************************/

            root = DatabaseManager.getCollection(getBaseUri() + "/db", "admin", "");
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
            final Account u1 = ums.getAccount(userName);
            if (u1 != null) {
                ums.removeAccount(u1);
            }
            final Group g1 = ums.getGroup(group1Name);
            if (g1 != null) {
                ums.removeGroup(g1);
            }
            final Group g2 = ums.getGroup(group2Name);
            if (g2 != null) {
                ums.removeGroup(g2);
            }
        }
    }
}
