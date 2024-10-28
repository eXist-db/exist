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
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.hamcrest.Matcher;
import org.junit.*;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.storage.DBBroker.PreserveType.NO_PRESERVE;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

public class StoreResourceTest {

    private static final String USER1_NAME = "user1";
    private static final String USER1_PWD = USER1_NAME;
    private static final String USER2_NAME = "user2";
    private static final String USER2_PWD = USER2_NAME;
    private static final String GROUP1_NAME = "group1";

    private static final XmldbURI USER1_DOC1 = XmldbURI.create("u1d1.xml");
    private static final XmldbURI USER1_BIN_DOC1 = XmldbURI.create("u1d1.bin");

    private static final int USER1_DOC1_MODE = 0664;  // rw-rw--r--
    private static final int USER1_BIN_DOC1_MODE = 0664;  // rw-rw--r--

    @ClassRule
    public static final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    /**
     * As group member replace {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI}
     */
    @Test
    public void replaceXmlAsOwner() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, SAXException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalDoc1LastModified = getLastModified(USER1_DOC1);
        Thread.sleep(5);
        replaceXmlDoc(user2, NO_PRESERVE, USER1_DOC1, "<something>else</something>");
        checkAttributes(USER1_DOC1, USER1_NAME, GROUP1_NAME, USER1_DOC1_MODE, equalTo(getCreated(USER1_DOC1)), greaterThanOrEqualTo(originalDoc1LastModified));
    }

    /**
     * As group member replace {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI}
     */
    @Test
    public void replaceBinaryAsGroupMember() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, SAXException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalDoc1LastModified = getLastModified(USER1_BIN_DOC1);
        Thread.sleep(5);
        replaceBinDoc(user2, NO_PRESERVE, USER1_BIN_DOC1, "something else");
        checkAttributes(USER1_BIN_DOC1, USER1_NAME, GROUP1_NAME, USER1_BIN_DOC1_MODE, equalTo(getCreated(USER1_BIN_DOC1)), greaterThanOrEqualTo(originalDoc1LastModified));
    }

    private void replaceXmlDoc(final Subject execAsUser, final DBBroker.PreserveType preserve, final XmldbURI docName, final String content) throws EXistException, PermissionDeniedException, LockException, IOException, SAXException {
        final XmldbURI uri = TEST_COLLECTION_URI.append(docName);

        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection col = broker.openCollection(uri.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {


            broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(content), MimeType.XML_TYPE, col);

            transaction.commit();
        }

        // check the replaced document is correct
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
             final LockedDocument lockedDoc = broker.getXMLResource(uri, Lock.LockMode.READ_LOCK)) {

            final Serializer serializer = broker.borrowSerializer();
            try {
                final String docXml = serializer.serialize(lockedDoc.getDocument());

                final Diff diff = DiffBuilder
                        .compare(Input.fromString(content))
                        .withTest(Input.fromString(docXml))
                        .build();

                assertFalse(diff.toString(), diff.hasDifferences());
            } finally {
                broker.returnSerializer(serializer);
            }
        }
    }

    private void replaceBinDoc(final Subject execAsUser, final DBBroker.PreserveType preserve, final XmldbURI docName, final String content) throws EXistException, PermissionDeniedException, LockException, IOException, SAXException {
        final XmldbURI uri = TEST_COLLECTION_URI.append(docName);

        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection col = broker.openCollection(uri.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {

            broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(content.getBytes(UTF_8)), MimeType.BINARY_TYPE, col);

            transaction.commit();
        }

        // check the replaced document is correct
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
             final LockedDocument lockedDoc = broker.getXMLResource(uri, Lock.LockMode.READ_LOCK);
             final InputStream is = broker.getBinaryResource((BinaryDocument)lockedDoc.getDocument());
             final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {

            os.write(is);

            assertArrayEquals(content.getBytes(UTF_8), os.toByteArray());
        }
    }

    private long getCreated(final XmldbURI docName) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), Lock.LockMode.READ_LOCK)) {

            return lockedDoc.getDocument().getCreated();
        }
    }

    private long getLastModified(final XmldbURI docName) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), Lock.LockMode.READ_LOCK)) {

            return lockedDoc.getDocument().getLastModified();
        }
    }

    private void checkAttributes(final XmldbURI docName, final String expectedOwner, final String expectedGroup, final int expectedMode, final Matcher<Long> expectedCreated, final Matcher<Long> expectedLastModified) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), Lock.LockMode.READ_LOCK)) {

            final DocumentImpl doc = lockedDoc.getDocument();
            final Permission permission = doc.getPermissions();
            assertEquals("Owner value was not expected", expectedOwner, permission.getOwner().getName());
            assertEquals("Group value was not expected", expectedGroup, permission.getGroup().getName());
            assertEquals("Mode value was not expected", expectedMode, permission.getMode());

            assertThat("Created value is not correct", doc.getCreated(), expectedCreated);
            assertThat("LastModified value is not correct", doc.getLastModified(), expectedLastModified);
        }
    }

    @BeforeClass
    public static void prepareDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            PermissionFactory.chmod(broker, collection, Optional.of(511), Optional.empty());
            broker.saveCollection(transaction, collection);

            createGroup(broker, sm, GROUP1_NAME);
            createUser(broker, sm, USER1_NAME, USER1_PWD, GROUP1_NAME);
            createUser(broker, sm, USER2_NAME, USER2_PWD, GROUP1_NAME);

            transaction.commit();
        }
    }

    @Before
    public void setup() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, AuthenticationException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        // create user1 resources
        final Subject user1 = pool.getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        try (final DBBroker broker = pool.get(Optional.of(user1));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection collection = broker.openCollection(TEST_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {

            final String u1d3xml = "<empty3/>";
            broker.storeDocument(transaction, USER1_DOC1, new StringInputSource(u1d3xml), MimeType.XML_TYPE, collection);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC1), USER1_DOC1_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC1), GROUP1_NAME);

            final String u1d3bin = "bin3";
            broker.storeDocument(transaction, USER1_BIN_DOC1, new StringInputSource(u1d3bin.getBytes(UTF_8)), MimeType.TEXT_TYPE, collection);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC1), USER1_BIN_DOC1_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC1), GROUP1_NAME);

            transaction.commit();
        }
    }

    @After
    public void teardown() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC1));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC1));

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanupDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeUser(sm, USER2_NAME);
            removeUser(sm, USER1_NAME);
            removeGroup(sm, GROUP1_NAME);

            removeCollection(broker, transaction, TEST_COLLECTION_URI);

            transaction.commit();
        }
    }

    private static void createUser(final DBBroker broker, final SecurityManager sm, final String username, final String password, final String... supplementalGroups) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(username);
        sm.addGroup(broker, userGroup);
        final Account user = new UserAider(username);
        user.setPassword(password);
        user.setPrimaryGroup(userGroup);
        sm.addAccount(user);

        userGroup = sm.getGroup(username);
        userGroup.addManager(sm.getAccount(username));
        sm.updateGroup(userGroup);

        for (final String supplementalGroup : supplementalGroups) {
            userGroup = sm.getGroup(supplementalGroup);
            user.addGroup(userGroup);
        }
        sm.updateAccount(user);
    }

    private static void createGroup(final DBBroker broker, final SecurityManager sm, final String group) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(group);
        sm.addGroup(broker, userGroup);
    }

    private static void chmod(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final int mode) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, transaction, pathUri, Optional.of(mode), Optional.empty());
    }

    private static void chgrp(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final String group) throws PermissionDeniedException {
        PermissionFactory.chown(broker, transaction, pathUri, Optional.empty(), Optional.of(group));
    }

    private static void removeUser(final SecurityManager sm, final String username) throws PermissionDeniedException, EXistException {
        sm.deleteAccount(username);
        sm.deleteGroup(username);
    }

    private static void removeGroup(final SecurityManager sm, final String group) throws PermissionDeniedException, EXistException {
        sm.deleteGroup(group);
    }

    private static void removeDocument(final DBBroker broker, final Txn transaction, final XmldbURI documentUri) throws PermissionDeniedException, LockException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(documentUri.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {

            final DocumentImpl doc = collection.getDocument(broker, documentUri.lastSegment());
            if (doc != null) {
                collection.removeResource(transaction, broker, doc);
            }
        }
    }

    private static void removeCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {

            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
        }
    }
}
