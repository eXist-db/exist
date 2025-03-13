/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

import java.io.IOException;
import java.util.List;

import org.exist.EXistException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.UserManagementService;
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

    protected abstract Collection getRoot() throws XMLDBException;

    protected abstract void restartServer() throws XMLDBException, IOException;

    @Test
    public void checkGroupMembership() throws XMLDBException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        UserManagementService ums = getRoot().getService(UserManagementService.class);

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

            ums = getRoot().getService(UserManagementService.class);

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
        UserManagementService ums = getRoot().getService(UserManagementService.class);

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

            ums = getRoot().getService(UserManagementService.class);

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

        UserManagementService ums = getRoot().getService(UserManagementService.class);

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

            ums = getRoot().getService(UserManagementService.class);

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
    public void checkGroupManagerStability() throws XMLDBException, PermissionDeniedException, IOException {
        UserManagementService ums = getRoot().getService(UserManagementService.class);

        final String commonGroupName = "commonGroup";
        Group commonGroup = new GroupAider(commonGroupName);

        final String userName = "testUserA";
        final Group userGroup = new GroupAider(userName);
        final Account userAccount = new UserAider(userName, userGroup); //set users primary group as personal group

        try {
            // create a user with personal group
            ums.addGroup(userGroup);
            ums.addAccount(userAccount);

            //add user1 as a manager of common group
            ums.addGroup(commonGroup);
            commonGroup.addManager(userAccount);
            ums.updateGroup(commonGroup);

            /*** RESTART THE SERVER ***/
            restartServer();
            /**************************/

            ums = getRoot().getService(UserManagementService.class);

            // get the common group
            commonGroup = ums.getGroup(commonGroupName);
            assertNotNull(commonGroup);

            // assert that user1 is still a manager of the common group
            final List<Account> commonGroupManagers = commonGroup.getManagers();
            assertNotNull(commonGroupManagers);
            assertEquals(1, commonGroupManagers.size());
            assertEquals(commonGroupManagers.getFirst().getName(), userName);

        } finally {
            //cleanup
            try { ums.removeGroup(commonGroup); } catch(Exception e) {}
            try { ums.removeAccount(userAccount); } catch(Exception e) {}
            try { ums.removeGroup(userGroup); } catch(Exception e) {}
        }
    }
}
