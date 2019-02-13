/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.backup;

import org.exist.EXistException;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.jetty.JettyStart;
import static org.exist.repo.AutoDeploymentTrigger.AUTODEPLOY_PROPERTY;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.RealmImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class BackupRestoreSecurityPrincipalsTest {

    private Path backupFile = null;
    private static JettyStart server;
    private final static String BACKUP_FILE_PREFIX = "exist.BackupRestoreSecurityPrincipalsTest";
    private final static String BACKUP_FILE_SUFFIX = ".backup.zip";
    private final static String FRANK_USER = "frank";
    private final static String JOE_USER = "joe";
    private final static String JACK_USER = "jack";

    private String autodeploy;

    private void startupDatabase() throws EXistException, DatabaseConfigurationException {
        server = new JettyStart();
        server.run();
    }

    /**
     * Creates a backup of a database with
     * three users: 'frank', 'joe' and 'jack' present
     *
     * It then clears out the database (including those users)
     * so that it is ready for further testing
     */
    @Before
    public void setup() throws PermissionDeniedException, EXistException, XMLDBException, SAXException, IOException, DatabaseConfigurationException, AuthenticationException {
        //we need to temporarily disable the auto-deploy trigger, as deploying eXide creates user accounts which interferes with this test
	    autodeploy = System.getProperty(AUTODEPLOY_PROPERTY, "off");
	    System.setProperty(AUTODEPLOY_PROPERTY, "off");

        startupDatabase();

        createUser(FRANK_USER, FRANK_USER);   //should have id RealmImpl.INITIAL_LAST_ACCOUNT_ID + 1
        createUser(JOE_USER, JOE_USER);       //should have id RealmImpl.INITIAL_LAST_ACCOUNT_ID + 2
        createUser(JACK_USER, JACK_USER);     //should have id RealmImpl.INITIAL_LAST_ACCOUNT_ID + 3

        backupFile = Files.createTempFile(BACKUP_FILE_PREFIX, BACKUP_FILE_SUFFIX);
        backupFile.toFile().deleteOnExit();

        final Backup backup = new Backup("admin", "", backupFile, XmldbURI.LOCAL_DB_URI, null, false);
        backup.backup(false, null);

        //reset database
        resetDatabaseToClean();
    }

    private void resetDatabaseToClean() throws EXistException, DatabaseConfigurationException, IOException {
        shutdownDatabase();
        deleteDatabaseFiles();
        startupDatabase();
    }

    private void deleteDatabaseFiles() throws DatabaseConfigurationException, IOException {
        final Path confFile = ConfigurationHelper.lookup("conf.xml");
        final Configuration config = new Configuration(confFile.toAbsolutePath().toString());

        final Path dataDir = Paths.get(config.getProperty(BrokerPool.PROPERTY_DATA_DIR).toString());
        if(Files.exists(dataDir)) {
            deleteAllDataFiles(dataDir);
        }

        final Path journalDir = Paths.get(config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR).toString());
        if(Files.exists(journalDir)) {
            deleteAllDataFiles(journalDir);
        }
    }

    /**
     * Deletes all files except those named 'RECOVERY' or 'README'
     *
     * Typically executed in $EXIST_HOME/webapp/WEB-INF/data
     * to clear the database
     */
    private void deleteAllDataFiles(final Path root) throws IOException {
        final List<Path> dataFiles;
        try(final Stream<Path> filesStream = Files.list(root)) {
            dataFiles = filesStream
                    .filter(path -> !(FileUtils.fileName(path).equals("RECOVERY") || FileUtils.fileName(path).equals("README") || FileUtils.fileName(path).equals(".DO_NOT_DELETE")))
                    .collect(Collectors.toList());
        }

        for (final Path dataFile : dataFiles) {
            FileUtils.delete(dataFile);
        }
    }

    @After
    public void shutdownDatabase() {
        server.shutdown();
        server = null;
	    System.setProperty(AUTODEPLOY_PROPERTY, autodeploy); //set the autodeploy trigger enablement back to how it was before this test class
    }

    /**
     * We start with an empty database and then we create
     * two users: 'frank' and 'jack'.
     *
     * We then try and restore a database backup, which already
     * contains users 'frank', 'joe' and 'jack':
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
     * that were owner by them are still correctly owner by them (and not some other user).
     */
    @Test
    public void restoreConflictingUsername() throws PermissionDeniedException, EXistException, SAXException, ParserConfigurationException, IOException, URISyntaxException, XMLDBException, AuthenticationException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
        final XPathQueryService xqs = (XPathQueryService) root.getService("XPathQueryService", "1.0");

        //create new users: 'frank' and 'jack'
        createUser(FRANK_USER, FRANK_USER);   // should have id RealmImpl.INITIAL_LAST_ACCOUNT_ID + 1
        createUser(JACK_USER, JACK_USER);     // should have id RealmImpl.INITIAL_LAST_ACCOUNT_ID + 2

        final String accountQuery = "declare namespace c = 'http://exist-db.org/Configuration';\n" +
            "for $account in //c:account\n" +
            "return\n" +
            "<user id='{$account/@id}' name='{$account/c:name}'/>";

        //check the current user accounts
        ResourceSet result = xqs.query(accountQuery);
        assertUser(RealmImpl.ADMIN_ACCOUNT_ID, SecurityManager.DBA_USER, ((XMLResource) result.getResource(0)).getContentAsDOM());
        assertUser(RealmImpl.GUEST_ACCOUNT_ID, SecurityManager.GUEST_USER, ((XMLResource) result.getResource(1)).getContentAsDOM());
        assertUser(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 1, "frank", ((XMLResource) result.getResource(2)).getContentAsDOM());
        assertUser(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 2, "jack", ((XMLResource) result.getResource(3)).getContentAsDOM());

        //check the last user id
        final String lastAccountIdQuery = "declare namespace c = 'http://exist-db.org/Configuration';\n" +
            "//c:security-manager/string(@last-account-id)";
        result = xqs.query(lastAccountIdQuery);
        assertEquals(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 2, Integer.parseInt(result.getResource(0).getContent().toString())); //last account id should be that of 'jack'

        //create a test collection and give everyone access
        final CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
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
        final Restore restore = new Restore();
        restore.restore(new NullRestoreListener(), "admin", "", null, backupFile, "xmldb:exist:///db");


        //check the current user accounts after the restore
        result = xqs.query(accountQuery);
        assertUser(RealmImpl.ADMIN_ACCOUNT_ID, SecurityManager.DBA_USER, ((XMLResource) result.getResource(0)).getContentAsDOM());
        assertUser(RealmImpl.GUEST_ACCOUNT_ID, SecurityManager.GUEST_USER, ((XMLResource) result.getResource(1)).getContentAsDOM());
        assertUser(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 1, "frank", ((XMLResource) result.getResource(2)).getContentAsDOM());
        assertUser(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 2, "jack", ((XMLResource) result.getResource(3)).getContentAsDOM());
        assertUser(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 4, "joe", ((XMLResource) result.getResource(4)).getContentAsDOM()); //this is `+ 4` because pre-allocating an id skips one

        //check the last user id after the restore
        result = xqs.query(lastAccountIdQuery);
        assertEquals(RealmImpl.INITIAL_LAST_ACCOUNT_ID + 4, Integer.parseInt(result.getResource(0).getContent().toString())); //last account id should be that of 'joe'

        //check the owner of frank's document after restore
        final Resource fDoc = test.getResource(FRANKS_DOCUMENT);
        final Permission franksDocPermissions = testUms.getPermissions(fDoc);
        assertEquals(FRANK_USER, franksDocPermissions.getOwner().getName());

        //check the owner of jack's document after restore
        final Resource jDoc = test.getResource(JACKS_DOCUMENT);
        final Permission jacksDocPermissions = testUms.getPermissions(jDoc);
        assertEquals(JACK_USER, jacksDocPermissions.getOwner().getName());
    }

    private void assertUser(final int userId, final String userName, final Node account) {
        assertEquals(userId, Integer.parseInt(account.getAttributes().getNamedItem("id").getNodeValue()));
        assertEquals(userName, account.getAttributes().getNamedItem("name").getNodeValue());
    }

    private void createUser(final String username, final String password) throws XMLDBException, PermissionDeniedException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
        try {
            final UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");

            final Account user = new UserAider(username);
            user.setPassword(password);

            //create the personal group
            Group group = new GroupAider(username);
            group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + username);
            group.addManager(ums.getAccount("admin"));
            ums.addGroup(group);

            //add the personal group as the primary group
            user.addGroup(username);

            //create the account
            ums.addAccount(user);

            //add the new account as a manager of their personal group
            ums.addGroupManager(username, group.getName());
        } finally {
            root.close();
        }
    }

    private class NullRestoreListener implements RestoreListener {

        @Override
        public void createCollection(String collection) {
        }

        @Override
        public void restored(String resource) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public String warningsAndErrorsAsString() {
            return null;
        }

        @Override
        public boolean hasProblems() {
            return false;
        }

        @Override
        public void setCurrentCollection(String currentCollectionName) {
        }

        @Override
        public void setCurrentResource(String currentResourceName) {
        }

        @Override
        public void restoreStarting() {
        }

        @Override
        public void restoreFinished() {
        }

        @Override
        public void observe(Observable observable) {
        }

        @Override
        public void setCurrentBackup(String currentBackup) {
        }

        @Override
        public void setNumberOfFiles(long nr) {
        }

        @Override
        public void incrementFileCounter() {
        }
    }
}
