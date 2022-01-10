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

import org.exist.EXistException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.internal.RealmImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class SecurityManagerTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI ACCOUNTS_URI = SecurityManager.SECURITY_COLLECTION_URI.append(RealmImpl.ID).append("accounts");
    private static final XmldbURI REMOVED_ACCOUNTS_URI = ACCOUNTS_URI.append("removed");
    private static final XmldbURI GROUPS_URI = SecurityManager.SECURITY_COLLECTION_URI.append(RealmImpl.ID).append("groups");
    private static final XmldbURI REMOVED_GROUPS_URI = GROUPS_URI.append("removed");

    private static final String TEST_USER_NAME = "test-user-1";
    private static final String TEST_GROUP_NAME = TEST_USER_NAME;

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final SecurityManager securityManager = brokerPool.getSecurityManager();

        // create the personal group
        final Group group = new GroupAider(TEST_GROUP_NAME);
        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + TEST_GROUP_NAME);
        try (final DBBroker broker = brokerPool.get(Optional.of(securityManager.getSystemSubject()))) {
            securityManager.addGroup(broker, group);

            // create the account
            final Account user = new UserAider(TEST_USER_NAME);
            user.setPassword(TEST_USER_NAME);
            user.addGroup(TEST_GROUP_NAME);
            securityManager.addAccount(user);

            // add the new account as a manager of their personal group
            final Group personalGroup = securityManager.getGroup(TEST_GROUP_NAME);
            personalGroup.addManager(securityManager.getAccount(TEST_USER_NAME));
            securityManager.updateGroup(personalGroup);
        }
    }

    @Test
    public void deleteAccount() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final SecurityManager securityManager = brokerPool.getSecurityManager();

        try (final DBBroker broker = brokerPool.get(Optional.of(securityManager.getSystemSubject()))) {

            // 1. pre-check - assert the account exists
            assertTrue(securityManager.hasAccount(TEST_USER_NAME));

            // 2. pre-check - assert the XML document for the account and group exists
            try (final LockedDocument lockedDocument = broker.getXMLResource(ACCOUNTS_URI.append(TEST_USER_NAME + ".xml"), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);
            }
            try (final LockedDocument lockedDocument = broker.getXMLResource(GROUPS_URI.append(TEST_GROUP_NAME + ".xml"), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);
            }

            // 3. pre-check - assert the XML document for any removed account or group does NOT exist
            assertFalse(removedAccountExists(broker, TEST_USER_NAME));
            assertFalse(removedGroupExists(broker, TEST_GROUP_NAME));

            // 4. DELETE THE ACCOUNT
            securityManager.deleteAccount(TEST_USER_NAME);

            // 5. post-check - assert the account does NOT exist
            assertFalse(securityManager.hasAccount(TEST_USER_NAME));

            // 6. post-check - assert the XML document for the account does NOT exist, but that the group still exists
            try (final LockedDocument lockedDocument = broker.getXMLResource(ACCOUNTS_URI.append(TEST_USER_NAME + ".xml"), Lock.LockMode.READ_LOCK)) {
                assertNull(lockedDocument);
            }
            try (final LockedDocument lockedDocument = broker.getXMLResource(GROUPS_URI.append(TEST_GROUP_NAME + ".xml"), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);
            }

            // 7. post-check - assert the XML document for the removed account does exist, but no such document exists for the group
            assertTrue(removedAccountExists(broker, TEST_USER_NAME));
            assertFalse(removedGroupExists(broker, TEST_GROUP_NAME));
        }
    }

    private boolean removedAccountExists(final DBBroker broker, final String username) throws XPathException, PermissionDeniedException {
        final XQuery queryService = broker.getBrokerPool().getXQueryService();
        final Sequence result = queryService.execute(broker, "declare namespace config='http://exist-db.org/Configuration'; collection('" + REMOVED_ACCOUNTS_URI + "')//config:account[config:name eq '" + username + "']", null);
        return result.getItemCount() == 1 && result.itemAt(0).toJavaObject(Boolean.class) == true;
    }

    private boolean removedGroupExists(final DBBroker broker, final String groupName) throws XPathException, PermissionDeniedException {
        final XQuery queryService = broker.getBrokerPool().getXQueryService();
        final Sequence result = queryService.execute(broker, "declare namespace config='http://exist-db.org/Configuration'; collection('" + REMOVED_GROUPS_URI + "')//config:group[config:name eq '" + groupName + "']", null);
        return result.getItemCount() == 1 && result.itemAt(0).toJavaObject(Boolean.class) == true;
    }
}
