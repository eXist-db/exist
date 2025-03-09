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
package org.exist.backup;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.exist.TestUtils;
import org.exist.security.Account;
import org.exist.security.MessageDigester;
import org.exist.security.SecurityManager;
import org.exist.test.ExistWebServer;
import org.exist.util.MimeType;
import org.exist.xmldb.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.FileUtils.withUnixSep;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class XMLDBRestoreTest {

    @Rule
    public final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String PORT_PLACEHOLDER = "${PORT}";

    private static final String COLLECTION1_NAME = "col1";
    private static final DocInfo[] BACKUP_DOCS = {
            new DocInfo("doc1.xml", MimeType.XML, "application/xml", "<doc1/>"),
            new DocInfo("doc2.xml", MimeType.XML, "application/xml", "<doc2/>"),
            new DocInfo("doc3.svg", MimeType.XML, "image/svg+xml", "<svg height=\"100\" width=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\" stroke=\"black\" stroke-width=\"3\" fill=\"red\" />Sorry, your browser does not support inline SVG.</svg>"),
            new DocInfo("doc4.html", MimeType.BINARY, "text/html", "<html><body><h1>BinaryResource</h1></body></html>"),
            new DocInfo("doc5.html", MimeType.XML, "text/html", "<html><body><h1>XMLResource</h1></body></html>"),
            new DocInfo("doc6.xml", MimeType.XML, "<doc6/>"),
            new DocInfo("doc7.bin", MimeType.BINARY, "1234567")
    };

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"local", XmldbURI.EMBEDDED_SERVER_URI.toString()},
                {"remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc"},
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    private String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void restoreValidZipBackup() throws IOException, XMLDBException {
        final Path zipFile = createZipBackupWithValidContent();
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, zipFile, null, listener);

        assertEquals(9, listener.restored.size());
        assertEquals(2, listener.warnings.size());
        assertEquals(0, listener.errors.size());

        for (final DocInfo backupDocInfo : BACKUP_DOCS) {
            checkMediaType(XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION1_NAME), backupDocInfo);
        }
    }

    @Test
    public void restoreValidDirBackup() throws IOException, XMLDBException {
        final Path contentsFile = createBackupWithValidContent();
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, null, listener);

        assertEquals(9, listener.restored.size());
        assertEquals(2, listener.warnings.size());
        assertEquals(0, listener.errors.size());

        for (final DocInfo backupDocInfo : BACKUP_DOCS) {
            checkMediaType(XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION1_NAME), backupDocInfo);
        }
    }

    @Test
    public void restoreZipIsBestEffortAttempt() throws IOException, XMLDBException {
        final Path zipFile = createZipBackupWithInvalidContent();
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, zipFile, null, listener);

        assertEquals(3, listener.restored.size());
        assertEquals(1, listener.warnings.size());
    }

    @Test
    public void restoreDirIsBestEffortAttempt() throws IOException, XMLDBException {
        final Path contentsFile = createBackupWithInvalidContent();
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, null, listener);

        assertEquals(3, listener.restored.size());
        assertEquals(1, listener.warnings.size());
    }

    @Test
    public void restoreZipBackupWithDifferentAdminPassword() throws IOException, XMLDBException {
        final String backupAdminPassword = UUID.randomUUID().toString();
        final Path zipFile = createZipBackupWithDifferentAdminPassword(backupAdminPassword);
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, zipFile, backupAdminPassword, listener);

        assertEquals(3, listener.restored.size());
        assertEquals(0, listener.warnings.size());
        assertEquals(0, listener.errors.size());
    }

    @Test
    public void restoreDirBackupWithDifferentAdminPassword() throws IOException, XMLDBException {
        final String backupAdminPassword = UUID.randomUUID().toString();
        final Path contentsFile = createBackupWithDifferentAdminPassword(backupAdminPassword);
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, backupAdminPassword, listener);

        assertEquals(3, listener.restored.size());
        assertEquals(0, listener.warnings.size());
        assertEquals(0, listener.errors.size());
    }

    @Test
    public void restoreUserWithoutGroupIsPlacedInNoGroup() throws IOException, XMLDBException {
        final String username = UUID.randomUUID() + "-user";
        final Path contentsFile = createBackupWithUserWithoutPrimaryGroup(username);
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, null, listener);

        assertEquals(2, listener.restored.size());
        assertEquals(0, listener.warnings.size());
        assertEquals(0, listener.errors.size());

        final Collection collection = DatabaseManager.getCollection(rootUri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistUserManagementService userManagementService = collection.getService(EXistUserManagementService.class);
        final Account account = userManagementService.getAccount(username);
        assertNotNull(account);
        assertEquals(SecurityManager.UNKNOWN_GROUP, account.getPrimaryGroup());
        assertArrayEquals(new String[]{SecurityManager.UNKNOWN_GROUP}, account.getGroups());
    }

    @Test
    public void restoreUserWithNoSuchGroupIsPlacedInNoGroup() throws IOException, XMLDBException {
        final String username = UUID.randomUUID() + "-user";
        final Path contentsFile = createBackupWithUserInNoSuchGroup(username);
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, null, listener);

        assertEquals(2, listener.restored.size());
        assertEquals(0, listener.warnings.size());
        assertEquals(0, listener.errors.size());

        final Collection collection = DatabaseManager.getCollection(rootUri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistUserManagementService userManagementService = collection.getService(EXistUserManagementService.class);
        final Account account = userManagementService.getAccount(username);
        assertNotNull(account);
        assertEquals(SecurityManager.UNKNOWN_GROUP, account.getPrimaryGroup());
        assertArrayEquals(new String[]{SecurityManager.UNKNOWN_GROUP}, account.getGroups());
    }

    /**
     * Restores users with groups from /db/system/security/exist
     */
    @Ignore("Not yet supported")
    @Test
    public void restoreUserWithGroupsFromExistRealm() throws IOException, XMLDBException {
        final Path backupPath = tempFolder.newFolder().toPath();
        final Path restorePath = backupPath.resolve("db").resolve("system").resolve("security").resolve("exist").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        restoreUserWithGroups(backupPath, restorePath, 8);
    }

    /**
     * Restores users with groups from /db/system/security
     */
    @Ignore("Not yet supported")
    @Test
    public void restoreUserWithGroupsFromSecurityCollection() throws IOException, XMLDBException {
        final Path backupPath = tempFolder.newFolder().toPath();
        final Path restorePath = backupPath.resolve("db").resolve("system").resolve("security").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        restoreUserWithGroups(backupPath, restorePath, 9);
    }

    /**
     * Restores users with groups from /db/system
     */
    @Ignore("Not yet supported")
    @Test
    public void restoreUserWithGroupsFromSystemCollection() throws IOException, XMLDBException {
        final Path backupPath = tempFolder.newFolder().toPath();
        final Path restorePath = backupPath.resolve("db").resolve("system").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        restoreUserWithGroups(backupPath, restorePath, 10);
    }

    /**
     * Restores users with groups from /db
     */
    @Test
    public void restoreUserWithGroupsFromDbCollection() throws IOException, XMLDBException {
        final Path backupPath = tempFolder.newFolder().toPath();
        final Path restorePath = backupPath.resolve("db").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR);
        restoreUserWithGroups(backupPath, restorePath, 11);
    }

    private void restoreUserWithGroups(final Path backupPath, final Path restorePath, final int expectedRestoredCount) throws IOException, XMLDBException {
        final String username = UUID.randomUUID() + "-user";
        final String primaryGroup = username;  // personal group
        final String group1 = UUID.randomUUID() + "-group-1";
        final String group2 = UUID.randomUUID() + "-group-2";
        final String group3 = UUID.randomUUID() + "-group-3";
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        createBackupWithUserInGroups(backupPath, username, primaryGroup, group1, group2, group3);
        restoreBackup(rootUri, restorePath, null, listener);

        assertEquals(expectedRestoredCount, listener.restored.size());
        assertEquals(0, listener.warnings.size());
        assertEquals(0, listener.errors.size());

        final Collection collection = DatabaseManager.getCollection(rootUri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistUserManagementService userManagementService = collection.getService(EXistUserManagementService.class);
        final Account account = userManagementService.getAccount(username);
        assertNotNull(account);
        assertEquals(primaryGroup, account.getPrimaryGroup());
        assertArrayEquals(new String[]{primaryGroup, group1, group2, group3}, account.getGroups());
    }

    private static void restoreBackup(final XmldbURI uri, final Path backup, @Nullable final String backupPassword, final RestoreServiceTaskListener listener) throws XMLDBException {
        final Collection collection = DatabaseManager.getCollection(uri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistRestoreService restoreService = collection.getService(EXistRestoreService.class);
        restoreService.restore(backup.normalize().toAbsolutePath().toString(), backupPassword, listener, false);
    }

    private void checkMediaType(final XmldbURI collectionUri, final DocInfo backupDocInfo) throws XMLDBException {
        final Collection collection = DatabaseManager.getCollection(XmldbURI.create(getBaseUri()).append(collectionUri).toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final Resource resource = collection.getResource(backupDocInfo.name);
        if (backupDocInfo.type == MimeType.XML) {
            assertTrue(resource instanceof XMLResource);
        } else {
            assertTrue(resource instanceof BinaryResource);
        }
        if (backupDocInfo.mediaType != null) {
            assertEquals(backupDocInfo.mediaType, ((EXistResource) resource).getMimeType());
        } else {
            assertEquals(backupDocInfo.type == MimeType.XML ? MimeType.XML_TYPE.getName() : MimeType.BINARY_TYPE.getName(), ((EXistResource) resource).getMimeType());
        }
    }

    private static Path createZipBackupWithValidContent() throws IOException {
        final Path dbContentsFile = createBackupWithValidContent();
        final Path dbDir = dbContentsFile.getParent();
        return zipDirectory(dbDir);
    }

    private static Path createBackupWithValidContent() throws IOException {

        final Path backupDir = tempFolder.newFolder().toPath();
        final Path db = Files.createDirectories(backupDir.resolve("db"));
        final Path col1 = Files.createDirectories(db.resolve(COLLECTION1_NAME));

        final String dbContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db\" owner=\"SYSTEM\" group=\"dba\" mode=\"755\" created=\"2019-05-15T15:58:39.385+04:00\" version=\"1\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <subcollection name=\"" + COLLECTION1_NAME + "\" filename=\"" + COLLECTION1_NAME + "\"/>\n" +
                        "</collection>";


        final StringBuilder col1Contents = new StringBuilder();
        col1Contents.append("<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/").append(COLLECTION1_NAME).append("\" owner=\"admin\" group=\"dba\" mode=\"755\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n");
        col1Contents.append("    <acl entries=\"0\" version=\"1\"/>\n");
        for (final DocInfo backupDocInfo : BACKUP_DOCS) {
            col1Contents.append("    <resource type=\"").append(backupDocInfo.type == MimeType.XML ? "XMLResource" : "BinaryResource").append("\" name=\"").append(backupDocInfo.name).append("\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"").append(backupDocInfo.name).append(backupDocInfo.mediaType != null ? "\" mimetype=\"" + backupDocInfo.mediaType + "\">\n" : "\">\n");
            col1Contents.append("        <acl entries=\"0\" version=\"1\"/>\n");
            col1Contents.append("    </resource>\n");
        }
        col1Contents.append("</collection>");

        final Path dbContentsFile = Files.writeString(db.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), dbContents);
        final Path col1ContentsFile = Files.writeString(col1.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), col1Contents.toString());
        for (final DocInfo backupDocInfo : BACKUP_DOCS) {
            Files.writeString(col1.resolve(backupDocInfo.name), backupDocInfo.content);
        }

        return dbContentsFile;
    }

    private static Path createZipBackupWithInvalidContent() throws IOException {
        final Path dbContentsFile = createBackupWithInvalidContent();
        final Path dbDir = dbContentsFile.getParent();
        return zipDirectory(dbDir);
    }

    private static Path createBackupWithInvalidContent() throws IOException {
        final Path backupDir = tempFolder.newFolder().toPath();
        final Path col1 = Files.createDirectories(backupDir.resolve("db").resolve("col1"));

        final String contents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/col1\" owner=\"admin\" group=\"dba\" mode=\"755\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <resource type=\"XMLResource\" name=\"doc1.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"doc1.xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "    <resource type=\"XMLResource\" name=\"doc2.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"doc2.xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "    <resource type=\"XMLResource\" name=\"doc3.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:49.618+04:00\" modified=\"2019-05-15T15:58:49.618+04:00\" filename=\"doc3.xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "</collection>";

        final String doc1 = "<doc1/>";
        final String doc2 = "<doc2>invalid";
        final String doc3 = "<doc3/>";

        final Path contentsFile = Files.writeString(col1.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), contents);
        Files.writeString(col1.resolve("doc1.xml"), doc1);
        Files.writeString(col1.resolve("doc2.xml"), doc2);
        Files.writeString(col1.resolve("doc3.xml"), doc3);

        return contentsFile;
    }

    private static Path createZipBackupWithDifferentAdminPassword(final String backupPassword) throws IOException {
        final Path dbContentsFile = createBackupWithDifferentAdminPassword(backupPassword);
        final Path dbDir = dbContentsFile.getParent();
        return zipDirectory(dbDir);
    }

    private static Path createBackupWithDifferentAdminPassword(final String backupPassword) throws IOException {
        final Path backupDir = tempFolder.newFolder().toPath();
        final Path accountsCol = Files.createDirectories(backupDir.resolve("db").resolve("system").resolve("security").resolve("exist").resolve("accounts"));

        final String contents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist/accounts\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T19:51:06.258+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <resource type=\"XMLResource\" name=\"admin.xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T19:51:06.319+04:00\" modified=\"2019-05-15T20:49:40.153+04:00\" filename=\"admin.xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "    <resource type=\"XMLResource\" name=\"guest.xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T19:51:06.512+04:00\" modified=\"2019-05-15T19:51:06.566+04:00\" filename=\"guest.xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>" +
                        "</collection>";

        // password for `admin`
        final String backupPasswordHash = Base64.encodeBase64String(ripemd160(backupPassword));
        final String backupPasswordDigest = MessageDigester.byteArrayToHex(ripemd160("admin:exist:" + backupPassword));

        final String admin = "<account xmlns=\"http://exist-db.org/Configuration\" id=\"1048574\"><password>{RIPEMD160}" + backupPasswordHash + "</password><digestPassword>" + backupPasswordDigest + "</digestPassword><group name=\"dba\"/><expired>false</expired><enabled>true</enabled><umask>022</umask><metadata key=\"http://exist-db.org/security/description\">System Administrator</metadata><metadata key=\"http://axschema.org/namePerson\">admin</metadata><name>admin</name></account>";
        final String guest = "<account xmlns=\"http://exist-db.org/Configuration\" id=\"1048573\"><password>{RIPEMD160}q2VXP75jMi+d8E5VAsEr6pD8V5w=</password><group name=\"guest\"/><expired>false</expired><enabled>true</enabled><umask>022</umask><metadata key=\"http://exist-db.org/security/description\">Anonymous User</metadata><metadata key=\"http://axschema.org/namePerson\">guest</metadata><name>guest</name></account>";

        final Path contentsFile = Files.writeString(accountsCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), contents);
        Files.writeString(accountsCol.resolve("admin.xml"), admin);
        Files.writeString(accountsCol.resolve("guest.xml"), guest);

        return contentsFile;
    }

    private static Path createBackupWithUserWithoutPrimaryGroup(final String username) throws IOException {
        final Path backupDir = tempFolder.newFolder().toPath();
        final Path accountsCol = Files.createDirectories(backupDir.resolve("db").resolve("system").resolve("security").resolve("exist").resolve("accounts"));

        final String contents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist/accounts\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <resource type=\"XMLResource\" name=\"" + username + ".xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"" + username + ".xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "</collection>";

        // account with no primary group!
        final String backupPasswordHash = Base64.encodeBase64String(ripemd160(username));
        final String backupPasswordDigest = MessageDigester.byteArrayToHex(ripemd160(username + ":exist:" + username));
        final String invalidUserDoc =
                "<account xmlns=\"http://exist-db.org/Configuration\" id=\"999\">\n" +
                        "<password>{RIPEMD160}" + backupPasswordHash + "</password>\n" +
                        "<digestPassword>" + backupPasswordDigest + "</digestPassword>\n" +
                        "<expired>false</expired>\n" +
                        "<enabled>true</enabled>\n" +
                        "<umask>022</umask>\n" +
                        "<name>" + username + "</name>\n" +
                        "</account>";

        final Path contentsFile = Files.writeString(accountsCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), contents);
        Files.writeString(accountsCol.resolve(username + ".xml"), invalidUserDoc);

        return contentsFile;
    }

    private static Path createBackupWithUserInNoSuchGroup(final String username) throws IOException {
        final Path backupDir = tempFolder.newFolder().toPath();
        final Path accountsCol = Files.createDirectories(backupDir.resolve("db").resolve("system").resolve("security").resolve("exist").resolve("accounts"));

        final String contents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist/accounts\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <resource type=\"XMLResource\" name=\"" + username + ".xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"" + username + ".xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "</collection>";

        // account with no such group!
        final String backupPasswordHash = Base64.encodeBase64String(ripemd160(username));
        final String backupPasswordDigest = MessageDigester.byteArrayToHex(ripemd160(username + ":exist:" + username));
        final String invalidUserDoc =
                "<account xmlns=\"http://exist-db.org/Configuration\" id=\"999\">\n" +
                        "<password>{RIPEMD160}" + backupPasswordHash + "</password>\n" +
                        "<digestPassword>" + backupPasswordDigest + "</digestPassword>\n" +
                        "<group name=\"no-such-group\"/>\n" +
                        "<expired>false</expired>\n" +
                        "<enabled>true</enabled>\n" +
                        "<umask>022</umask>\n" +
                        "<name>" + username + "</name>\n" +
                        "</account>\n";

        final Path contentsFile = Files.writeString(accountsCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), contents);
        Files.writeString(accountsCol.resolve(username + ".xml"), invalidUserDoc);

        return contentsFile;
    }

    private static void createBackupWithUserInGroups(final Path backupDir, final String username, final String... groupNames) throws IOException {
        final Path dbCol = Files.createDirectories(backupDir.resolve("db"));
        final Path systemCol = Files.createDirectories(dbCol.resolve("system"));
        final Path securityCol = Files.createDirectories(systemCol.resolve("security"));
        final Path existRealmCol = Files.createDirectories(securityCol.resolve("exist"));
        final Path groupsCol = Files.createDirectories(existRealmCol.resolve("groups"));
        final Path accountsCol = Files.createDirectories(existRealmCol.resolve("accounts"));

        final String dbContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db\" version=\"1\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2021-01-28T04:06:13.166Z\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <subcollection name=\"system\" filename=\"system\"/>\n" +
                        "</collection>";

        final String systemContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system\" version=\"1\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2021-01-28T04:06:13.166Z\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <subcollection name=\"security\" filename=\"security\"/>\n" +
                        "</collection>";

        final String securityContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security\" version=\"1\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2021-01-28T04:06:13.166Z\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <subcollection name=\"exist\" filename=\"exist\"/>\n" +
                        "</collection>";

        final String existRealmContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist\" version=\"1\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2021-01-28T04:06:13.166Z\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <subcollection name=\"accounts\" filename=\"accounts\"/>\n" +
                        "    <subcollection name=\"groups\" filename=\"groups\"/>\n" +
                        "</collection>";

        final StringBuilder groupsContents = new StringBuilder(
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist/groups\" version=\"1\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2021-01-28T04:06:13.172Z\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n");

        for (final String groupName : groupNames) {
            groupsContents.append(
                    "    <resource type=\"XMLResource\" name=\"" + groupName + ".xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"" + groupName + ".xml\" mimetype=\"application/xml\">\n" +
                            "        <acl entries=\"0\" version=\"1\"/>\n" +
                            "    </resource>\n");
        }
        groupsContents.append(
                "</collection>");

        final String accountsContents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/system/security/exist/accounts\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                        "    <acl entries=\"0\" version=\"1\"/>\n" +
                        "    <resource type=\"XMLResource\" name=\"" + username + ".xml\" owner=\"SYSTEM\" group=\"dba\" mode=\"770\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"" + username + ".xml\" mimetype=\"application/xml\">\n" +
                        "        <acl entries=\"0\" version=\"1\"/>\n" +
                        "    </resource>\n" +
                        "</collection>";

        // account with no such group!
        final String backupPasswordHash = Base64.encodeBase64String(ripemd160(username));
        final String backupPasswordDigest = MessageDigester.byteArrayToHex(ripemd160(username + ":exist:" + username));
        final StringBuilder userDoc = new StringBuilder(
                "<account xmlns=\"http://exist-db.org/Configuration\" id=\"999\">\n" +
                        "<password>{RIPEMD160}" + backupPasswordHash + "</password>\n" +
                        "<digestPassword>" + backupPasswordDigest + "</digestPassword>\n");
        for (final String groupName : groupNames) {
            userDoc.append(
                    "<group name=\"" + groupName + "\"/>\n");
        }
        userDoc.append(
                "<expired>false</expired>\n" +
                        "<enabled>true</enabled>\n" +
                        "<umask>022</umask>\n" +
                        "<name>" + username + "</name>\n" +
                        "</account>\n");

        final Path dbContentsFile = Files.writeString(dbCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), dbContents);
        final Path systemContentsFile = Files.writeString(systemCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), systemContents);
        final Path securityContentsFile = Files.writeString(securityCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), securityContents);
        final Path existRealmContentsFile = Files.writeString(existRealmCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), existRealmContents);
        final Path groupsContentsFile = Files.writeString(groupsCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), groupsContents.toString());
        final Path accountsContentsFile = Files.writeString(accountsCol.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), accountsContents);

        int groupId = 123;
        for (final String groupName : groupNames) {
            final String groupDoc =
                    "<group xmlns=\"http://exist-db.org/Configuration\" id=\"" + (groupId++) + "\">\n" +
                            "    <manager name=\"admin\"/>\n" +
                            "    <metadata key=\"http://exist-db.org/security/description\">Group named: " + groupName + "</metadata>\n" +
                            "    <name>" + groupName + "</name>\n" +
                            "</group>";

            Files.writeString(groupsCol.resolve(groupName + ".xml"), groupDoc);
        }

        Files.writeString(accountsCol.resolve(username + ".xml"), userDoc.toString());
    }

    private static byte[] ripemd160(final String s) {
        final RIPEMD160Digest digester = new RIPEMD160Digest();
        final byte[] data = s.getBytes();
        digester.update(data, 0, data.length);

        final byte[] digest = new byte[digester.getDigestSize()];
        digester.doFinal(digest, 0);
        return digest;
    }

    private static Path zipDirectory(final Path dir) throws IOException {
        final Path zipFile = File.createTempFile("backup", ".zip", tempFolder.getRoot()).toPath();
        try (final ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path zipEntryPath = dir.relativize(file);
                    final String zipEntryName = withUnixSep(zipEntryPath.toString());
                    final ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    out.putNextEntry(zipEntry);
                    Files.copy(file, out);
                    out.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return zipFile;
    }

    private static class TestRestoreListener extends AbstractRestoreServiceTaskListener {
        final List<String> restored = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void createdCollection(final String collection) {
            restored.add(collection);
        }

        @Override
        public void restoredResource(final String resource) {
            restored.add(resource);
        }

        @Override
        public void info(final String message) {
        }

        @Override
        public void warn(final String message) {
            warnings.add(message);
        }

        @Override
        public void error(final String message) {
            errors.add(message);
        }
    }

    private static class DocInfo {
        final String name;
        final int type;
        @Nullable final String mediaType;
        final String content;

        private DocInfo(final String name, final int type, final String content) {
            this(name, type, null, content);
        }

        private DocInfo(final String name, final int type, final String mediaType, final String content) {
            this.name = name;
            this.type = type;
            this.mediaType = mediaType;
            this.content = content;
        }
    }
}
