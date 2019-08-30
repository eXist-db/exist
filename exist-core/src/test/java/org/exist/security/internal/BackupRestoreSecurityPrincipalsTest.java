/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.internal;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.backup.Backup;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.NullRestoreServiceTaskListener;
import org.exist.xmldb.UserManagementService;
import org.junit.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class BackupRestoreSecurityPrincipalsTest {

    private final static String BACKUP_FILE_PREFIX = "exist.BackupRestoreSecurityPrincipalsTest";
    private final static String BACKUP_FILE_SUFFIX = ".backup.zip";
    private final static String FRANK_USER = "frank";
    private final static String JOE_USER = "joe";
    private final static String JACK_USER = "jack";

    @ClassRule
    public static ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * 1. With an empty database we create three
     *    users: 'frank', 'joe', and 'jack'.
     *
     * 2. We create a backup of the database which contains
     *    the three users from (1).
     *
     * 3. We destroy the database, restart the server,
     *    and start again with a clean database.
     *
     * 4. With an (again) empty database we create two
     *    users: 'frank', and 'jack'.
     *
     * 5. We then try and restore the database backup from (2), which
     *    contains the original 'frank', 'joe', and 'jack' users.
     *
     * frank will have the same username and user id in the current
     * database and the backup we are trying to restore.
     *
     * joe does not exist in the current database, but his user id
     * in the backup will collide with that of jack in the current database.
     *
     * jack will have a different user id in the backup when compared to the current
     * database, however he will have the same username.
     *
     * We want to make sure that after the restore, all three users are present
     * that they have distinct and expected user ids and that any resources
     * that were owned by them are still correctly owner by them (and not some other user).
     */
    @Test
    public void restoreConflictingUsername() throws PermissionDeniedException, EXistException, SAXException, ParserConfigurationException, IOException, URISyntaxException, XMLDBException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        // creates a database with new users: 'frank(id=11)', 'joe(id=12)', and 'jack(id=13)'
        createInitialUsers(FRANK_USER, JOE_USER, JACK_USER);

        // create a backup of the database (which has the initial users)
        final Path backupFile = backupDatabase();

        //reset database to empty
        server.restart(true);

        //create new users: 'frank(id=11)' and 'jack(id=12)'
        createInitialUsers(FRANK_USER, JACK_USER);

        final String accountQuery = "declare namespace c = 'http://exist-db.org/Configuration';\n" +
            "for $account in //c:account\n" +
            "return\n" +
            "<user id='{$account/@id}' name='{$account/c:name}'/>";

        final XPathQueryService xqs = (XPathQueryService) server.getRoot().getService("XPathQueryService", "1.0");

        final SecurityManagerImpl sm = (SecurityManagerImpl) BrokerPool.getInstance().getSecurityManager();

        //check the current user accounts
        ResourceSet result = xqs.query(accountQuery);
        assertUser(RealmImpl.ADMIN_ACCOUNT_ID, SecurityManager.DBA_USER, ((XMLResource) result.getResource(0)).getContentAsDOM());
        assertUser(RealmImpl.GUEST_ACCOUNT_ID, SecurityManager.GUEST_USER, ((XMLResource) result.getResource(1)).getContentAsDOM());
        assertUser(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 1, "frank", ((XMLResource) result.getResource(2)).getContentAsDOM());
        assertUser(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 2, "jack", ((XMLResource) result.getResource(3)).getContentAsDOM());

        //check the last user id
        assertEquals(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 2, sm.getLastAccountId()); //last account id should be that of 'jack'

        //create a test collection and give everyone access
        final CollectionManagementService cms = (CollectionManagementService)server.getRoot().getService("CollectionManagementService", "1.0");
        final Collection test = cms.createCollection("test");
        final UserManagementService testUms = (UserManagementService)test.getService("UserManagementService", "1.0");
        testUms.chmod("rwxrwxrwx");

        //create and store a new document as 'frank'
        final Collection frankTest = DatabaseManager.getCollection("xmldb:exist:///db/test", FRANK_USER, FRANK_USER);
        final String FRANKS_DOCUMENT = "franks-document.xml";
        final Resource frankDoc = frankTest.createResource(FRANKS_DOCUMENT, XMLResource.RESOURCE_TYPE);
        frankDoc.setContent("<hello>frank</hello>");
        frankTest.storeResource(frankDoc);

        //create and store a new document as 'jack'
        final Collection jackTest = DatabaseManager.getCollection("xmldb:exist:///db/test", JACK_USER, JACK_USER);
        final String JACKS_DOCUMENT = "jacks-document.xml";
        final Resource jackDoc = jackTest.createResource(JACKS_DOCUMENT, XMLResource.RESOURCE_TYPE);
        jackDoc.setContent("<hello>jack</hello>");
        jackTest.storeResource(jackDoc);

        //restore the database backup
        final EXistRestoreService service = (EXistRestoreService)server.getRoot().getService("RestoreService", "1.0");
        service.restore(backupFile.normalize().toAbsolutePath().toString(), null, new NullRestoreServiceTaskListener(), false);


        //check the current user accounts after the restore
        result = xqs.query(accountQuery);
        assertUser(RealmImpl.ADMIN_ACCOUNT_ID, SecurityManager.DBA_USER, ((XMLResource) result.getResource(0)).getContentAsDOM());
        assertUser(RealmImpl.GUEST_ACCOUNT_ID, SecurityManager.GUEST_USER, ((XMLResource) result.getResource(1)).getContentAsDOM());
        assertUser(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 1, FRANK_USER, ((XMLResource) result.getResource(2)).getContentAsDOM());
        assertUser(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 2, JACK_USER, ((XMLResource) result.getResource(3)).getContentAsDOM());
        assertUser(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 3, JOE_USER, ((XMLResource) result.getResource(4)).getContentAsDOM());

        //check the last user id after the restore
        assertEquals(SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID + 3, sm.getLastAccountId()); //last account id should be that of 'joe'

        //check the owner of frank's document after restore
        final Resource fDoc = test.getResource(FRANKS_DOCUMENT);
        final Permission franksDocPermissions = testUms.getPermissions(fDoc);
        assertEquals(FRANK_USER, franksDocPermissions.getOwner().getName());

        //check the owner of jack's document after restore
        final Resource jDoc = test.getResource(JACKS_DOCUMENT);
        final Permission jacksDocPermissions = testUms.getPermissions(jDoc);
        assertEquals(JACK_USER, jacksDocPermissions.getOwner().getName());
    }

    /**
     * Creates initial database users.
     *
     * NOTE: The database must be in a clean initialised empty state.
     */
    private void createInitialUsers(final String... usernames) throws PermissionDeniedException, XMLDBException, SAXException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        int lastAccountId = SecurityManagerImpl.INITIAL_LAST_ACCOUNT_ID;

        for (final String username : usernames) {
            createUser(username, username);
            assertEquals(++lastAccountId, getUser(username).getId());
        }
    }

    /**
     * Backup the database.
     *
     * @return The path to the database backup.
     */
    private Path backupDatabase() throws IOException, XMLDBException, SAXException {
        final Path backupFile = Files.createTempFile(BACKUP_FILE_PREFIX, BACKUP_FILE_SUFFIX);
        backupFile.toFile().deleteOnExit();

        final Backup backup = new Backup(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, backupFile);
        backup.backup(false, null);

        return backupFile;
    }

    private void assertUser(final int userId, final String userName, final Node account) {
        assertEquals(userId, Integer.parseInt(account.getAttributes().getNamedItem("id").getNodeValue()));
        assertEquals(userName, account.getAttributes().getNamedItem("name").getNodeValue());
    }

    private void createUser(final String username, final String password) throws XMLDBException, PermissionDeniedException {
        final UserManagementService ums = (UserManagementService) server.getRoot().getService("UserManagementService", "1.0");

        final Account user = new UserAider(username);
        user.setPassword(password);
        //create the personal group
        final Group group = new GroupAider(username);
        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + username);
        group.addManager(ums.getAccount("admin"));
        ums.addGroup(group);

        //add the personal group as the primary group
        user.addGroup(username);

        //create the account
        ums.addAccount(user);

        //add the new account as a manager of their personal group
        ums.addGroupManager(username, group.getName());
    }

    private Account getUser(final String username) throws XMLDBException {
        final UserManagementService ums = (UserManagementService) server.getRoot().getService("UserManagementService", "1.0");
        return ums.getAccount(username);
    }
}
