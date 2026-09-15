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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression test for https://github.com/eXist-db/exist/issues/1610.
 *
 * {@link SecurityManager#updateAccount(Account)} and {@link SecurityManager#updateGroup(Group)}
 * used to call {@link org.exist.security.Principal#save()} (no broker), which falls back to
 * {@code BrokerPool.getInstance()} - a lookup of the pool's own instance name in a static
 * registry that is only populated at the very end of {@code BrokerPool._initialize()}, i.e.
 * *after* startup triggers (such as this test's own) have already run. Called from within a
 * {@link StartupTrigger}, that lookup fails unconditionally with
 * "Database instance 'exist' is not available", even though a valid broker for the pool
 * being started is already available via {@code DBBroker#getBrokerPool()}.
 *
 * This is also why {@code sm:create-account}'s personal-group overload (which calls
 * updateGroup to register the new account as its personal group's manager) and
 * {@code sm:passwd}/{@code sm:passwd-hash} (which call updateAccount) fail when used from a
 * package's finish.xq / pre-install.xql / post-install.xql during autodeploy - those all run
 * via {@link org.exist.repo.AutoDeploymentTrigger}, itself a StartupTrigger.
 */
public class StartupTriggerUpdateAccountAndGroupTest {

    private final static String USER = "testuser2";
    private final static String PASSWORD = "testpass";
    private final static String GROUP = "testgroup2";

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(null, null, properties(), true, true);

    public static Properties properties() {
        final List<Configuration.StartupTriggerConfig> startupTriggers = new ArrayList<>();
        startupTriggers.add(new Configuration.StartupTriggerConfig(TestStartupTrigger.class.getName(), null));

        final Properties properties = new Properties();
        properties.put(BrokerPool.PROPERTY_STARTUP_TRIGGERS, startupTriggers);
        return properties;
    }

    /**
     * Check that a startup trigger can call SecurityManager#updateAccount without the
     * BrokerPool named-instance lookup blowing up.
     */
    @Test
    public void updateAccount() throws DatabaseConfigurationException, EXistException, IOException {
        assertTrue(TestStartupTrigger.updateAccountCompleted);
    }

    /**
     * Check that a startup trigger can call SecurityManager#updateGroup without the
     * BrokerPool named-instance lookup blowing up.
     */
    @Test
    public void updateGroup() throws DatabaseConfigurationException, EXistException, IOException {
        assertTrue(TestStartupTrigger.updateGroupCompleted);
    }

    public static class TestStartupTrigger implements StartupTrigger {

        static volatile boolean updateAccountCompleted = false;
        static volatile boolean updateGroupCompleted = false;

        @Override
        public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params) {
            final SecurityManager secman = sysBroker.getBrokerPool().getSecurityManager();

            try {
                if (!secman.hasGroup(GROUP)) {
                    secman.addGroup(sysBroker, new GroupAider(GROUP));
                }

                if (!secman.hasAccount(USER)) {
                    final UserAider aider = new UserAider(USER);
                    aider.setPassword(PASSWORD);
                    aider.addGroup(GROUP);
                    secman.addAccount(sysBroker, aider);
                }
            } catch (final PermissionDeniedException | EXistException e) {
                fail("Setup for update test failed: " + e.getMessage());
            }

            try {
                // exercises SecurityManagerImpl -> AbstractRealm#updateAccount -> Principal#save(broker)
                final Account account = secman.getAccount(USER);
                secman.updateAccount(account);
                updateAccountCompleted = true;
            } catch (final PermissionDeniedException | EXistException e) {
                fail("updateAccount from a StartupTrigger threw: " + e.getMessage());
            }

            try {
                // exercises SecurityManagerImpl -> AbstractRealm#updateGroup -> Principal#save(broker)
                final Group group = secman.getGroup(GROUP);
                group.addManager(secman.getAccount(USER));
                secman.updateGroup(group);
                updateGroupCompleted = true;
            } catch (final PermissionDeniedException | EXistException e) {
                fail("updateGroup from a StartupTrigger threw: " + e.getMessage());
            }

            try {
                secman.deleteAccount(USER);
                secman.deleteGroup(GROUP);
            } catch (final PermissionDeniedException | EXistException e) {
                fail(e.getMessage());
            }
        }
    }
}
