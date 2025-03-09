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

package org.exist.xquery.functions.securitymanager;

import com.evolvedbinary.j8fu.function.Runnable4E;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PermissionsFunctionChmodTest {

    private static final boolean IS_SET = true;
    private static final boolean NOT_SET = false;

    private static final String USER1_NAME = "user1";
    private static final String USER1_PWD = USER1_NAME;
    private static final String USER2_NAME = "user2";
    private static final String USER2_PWD = USER2_NAME;

    private static final XmldbURI USER1_COL1 = XmldbURI.create("u1c1");
    private static final XmldbURI USER1_COL2 = XmldbURI.create("u1c2");
    private static final XmldbURI USER1_DOC1 = XmldbURI.create("u1d1.xml");
    private static final XmldbURI USER1_XQUERY1 = XmldbURI.create("u1xq1.xq");

    private static final String OTHER_GROUP_NAME = "otherGroup";

    private static final String RWXRWXRWX = "rwxrwxrwx";
    private static final String RWXRWS__ = "rwxrws---";
    private static final String RWXRWSRWX = "rwxrwsrwx";

    @ClassRule
    public static final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    @Test
    public void changeDocumentModeAsDBA() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        changeMode(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_DOC1), RWXRWXRWX);
    }

    @Test
    public void changeCollectionModeAsDBA() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        changeMode(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_COL1), RWXRWXRWX);
    }

    @Test
    public void changeDocumentModeAsNonDBAOwner() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        changeMode(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_DOC1), RWXRWXRWX);
    }

    @Test
    public void changeCollectionModeAsNonDBAOwner() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        changeMode(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_COL1), RWXRWXRWX);
    }

    @Test(expected=PermissionDeniedException.class)
    public void changeDocumentModeAsNonOwner() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        extractPermissionDenied(() ->
                changeMode(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_DOC1), RWXRWXRWX)
        );
    }

    @Test(expected=PermissionDeniedException.class)
    public void changeCollectionModeAsNonOwner() throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        extractPermissionDenied(() ->
            changeMode(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_COL1), RWXRWXRWX)
        );
    }

    @Test
    public void changeDocumentModeAsDBA_preservesSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);

        // check the setGid bit is set before we begin
        assertDocumentSetGid(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), IS_SET);

        // change the mode
        changeMode(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), RWXRWS__);

        // check the setGid bit still set
        assertDocumentSetGid(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), IS_SET);
    }

    @Test
    public void changeCollectionModeAsDBA_preservesSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);

        // check the setGid bit is set before we begin
        assertCollectionSetGid(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), IS_SET);

        // change the mode
        changeMode(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), RWXRWS__);

        // check the setGid bit still set
        assertCollectionSetGid(adminUser, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), IS_SET);
    }

    @Test
    public void changeDocumentModeAsNonDBAOwner_preservesSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);

        // check the setGid bit is set before we begin
        assertDocumentSetGid(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), IS_SET);

        // change the mode
        changeMode(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), RWXRWS__);

        // check the setGid bit still set
        assertDocumentSetGid(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), IS_SET);
    }

    @Test
    public void changeCollectionModeAsNonDBAOwner_preservesSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);

        // check the setGid bit is set before we begin
        assertCollectionSetGid(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), IS_SET);

        // change the mode
        changeMode(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), RWXRWS__);

        // check the setGid bit still set
        assertCollectionSetGid(user1, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), IS_SET);
    }

    @Test(expected=PermissionDeniedException.class)
    public void changeDocumentModeAsNonOwner_clearsSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);

        // check the setGid bit is set before we begin
        assertDocumentSetGid(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), IS_SET);

        // change the mode
        extractPermissionDenied(() ->
            changeMode(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), RWXRWSRWX)
        );

        // check the setGid bit still set
        assertDocumentSetGid(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_XQUERY1), NOT_SET);
    }

    @Test(expected=PermissionDeniedException.class)
    public void changeCollectionModeAsNonOwner_clearsSetGid() throws AuthenticationException, EXistException, PermissionDeniedException, XPathException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);

        // check the setGid bit is set before we begin
        assertCollectionSetGid(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), IS_SET);

        // change the mode
        extractPermissionDenied(() ->
                changeMode(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), RWXRWSRWX)
        );

        // check the setGid bit still set
        assertCollectionSetGid(user2, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2), NOT_SET);
    }

    private void changeMode(final Subject execAsUser, final XmldbURI uri, final String newMode) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        final String query =
                "import module namespace sm = 'http://exist-db.org/xquery/securitymanager';\n" +
                        "sm:chmod(xs:anyURI('" + uri.getRawCollectionPath() + "'), '" + newMode + "'),\n" +
                        "sm:get-permissions(xs:anyURI('" + uri.getRawCollectionPath() + "'))/sm:permission/string(@mode)";

        try (final DBBroker broker = pool.get(Optional.of(execAsUser))) {

            final XQuery xquery = existWebServer.getBrokerPool().getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);

            assertEquals(1, result.getItemCount());
            assertEquals(newMode, result.itemAt(0).getStringValue());
        }
    }

    @BeforeClass
    public static void prepareDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            PermissionFactory.chmod(broker, collection, Optional.of(511), Optional.empty());
            broker.saveCollection(transaction, collection);

            createUser(broker, sm, USER1_NAME, USER1_PWD);
            createUser(broker, sm, USER2_NAME, USER2_PWD);

            final Group otherGroup = new GroupAider(OTHER_GROUP_NAME);
            sm.addGroup(broker, otherGroup);
            final Account user1 = sm.getAccount(USER1_NAME);
            user1.addGroup(OTHER_GROUP_NAME);
            sm.updateAccount(user1);
            final Account user2 = sm.getAccount(USER2_NAME);
            user2.addGroup(OTHER_GROUP_NAME);
            sm.updateAccount(user2);

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
                final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {

            final Collection u1c1 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append(USER1_COL1));
            broker.saveCollection(transaction, u1c1);

            final Collection u1c2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2));
            PermissionFactory.chmod_str(broker, u1c2, Optional.of("u+s,g+s"), Optional.empty());
            broker.saveCollection(transaction, u1c2);

            final String xml1 = "<empty1/>";
            broker.storeDocument(transaction, USER1_DOC1, new StringInputSource(xml1), MimeType.XML_TYPE, collection);

            final String xquery1 =
                    "import module namespace sm = 'http://exist-db.org/xquery/securitymanager';\n" +
                            "sm:id()";
            broker.storeDocument(transaction, USER1_XQUERY1, new StringInputSource(xquery1.getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
            PermissionFactory.chmod_str(broker, transaction, collection.getURI().append(USER1_XQUERY1), Optional.of("u+s,g+s"), Optional.empty());

            transaction.commit();
        }
    }

    @After
    public void teardown() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeDocument(broker, transaction, TestConstants.TEST_COLLECTION_URI.append(USER1_DOC1));
            removeCollection(broker, transaction, TestConstants.TEST_COLLECTION_URI.append(USER1_COL2));
            removeCollection(broker, transaction, TestConstants.TEST_COLLECTION_URI.append(USER1_COL1));

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

            removeCollection(broker, transaction, TestConstants.TEST_COLLECTION_URI);

            transaction.commit();
        }
    }

    private static void assertDocumentSetGid(final Subject execAsUser, final XmldbURI uri, final boolean isSet) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
                final LockedDocument lockedDoc = broker.getXMLResource(uri, Lock.LockMode.READ_LOCK)) {

            final DocumentImpl doc = lockedDoc.getDocument();
            if (isSet) {
                assertTrue(doc.getPermissions().isSetGid());
            } else {
                assertFalse(doc.getPermissions().isSetGid());
            }
        }
    }

    private static void assertCollectionSetGid(final Subject execAsUser, final XmldbURI uri, final boolean isSet) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser))) {
            try (final Collection col = broker.openCollection(uri, Lock.LockMode.READ_LOCK)) {
                if (isSet) {
                    assertTrue(col.getPermissions().isSetGid());
                } else {
                    assertFalse(col.getPermissions().isSetGid());
                }
            }
        }
    }

    private static void extractPermissionDenied(final Runnable4E<AuthenticationException, XPathException, PermissionDeniedException, EXistException> runnable) throws AuthenticationException, XPathException, PermissionDeniedException, EXistException {
        try {
            runnable.run();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException)e.getCause();
            } else {
                throw e;
            }
        }
    }

    private static void removeDocument(final DBBroker broker, final Txn transaction, final XmldbURI documentUri) throws PermissionDeniedException, LockException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(documentUri.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {
            collection.removeXMLResource(transaction, broker, documentUri.lastSegment());
            broker.saveCollection(transaction, collection);
        }
    }

    private static void removeCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            broker.removeCollection(transaction, collection);
        }
    }

    private static void createUser(final DBBroker broker, final SecurityManager sm, final String username, final String password) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(username);
        sm.addGroup(broker, userGroup);
        final Account user = new UserAider(username);
        user.setPassword(password);
        user.setPrimaryGroup(userGroup);
        sm.addAccount(user);

        userGroup = sm.getGroup(username);
        userGroup.addManager(sm.getAccount(username));
        sm.updateGroup(userGroup);
    }

    private static void removeUser(final SecurityManager sm, final String username) throws PermissionDeniedException, EXistException {
        sm.deleteAccount(username);
        sm.deleteGroup(username);
    }
}
