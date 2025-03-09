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
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.hamcrest.Matcher;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.exist.security.SecurityManager.DBA_GROUP;
import static org.exist.storage.DBBroker.PreserveType.*;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Tests to ensure that collection content and attributes
 * are correctly copied under various circumstances.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CopyCollectionTest {

    private static final String USER1_NAME = "user1";
    private static final String USER1_PWD = USER1_NAME;
    private static final String USER2_NAME = "user2";
    private static final String USER2_PWD = USER2_NAME;

    private static final XmldbURI USER1_COL1 = XmldbURI.create("u1c1");
    private static final XmldbURI USER1_COL2 = XmldbURI.create("u1c2");
    private static final XmldbURI USER1_NEW_COL = XmldbURI.create("u1cx");

    private static final XmldbURI USER2_COL2 = XmldbURI.create("u2c2");
    private static final XmldbURI USER2_NEW_COL = XmldbURI.create("u2cx");

    private static final int USER1_COL1_MODE = 0555;  // r-xr-xr-x
    private static final int USER1_COL2_MODE = 0744;  // rwxr--r--

    private static final int USER2_COL2_MODE = 0744;  // rwxr--r--

    @ClassRule
    public static final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    /**
     * As the owner copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_COL}.
     */
    @Test
    public void copyToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        copyCol(user1, NO_PRESERVE, USER1_COL1, USER1_NEW_COL);
        checkAttributes(USER1_NEW_COL, USER1_NAME, USER1_NAME, USER1_COL1_MODE, not(getCreated(USER1_COL1)));
    }

    /**
     * As the owner copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_COL2}.
     */
    @Test
    public void copyToExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        copyCol(user1, NO_PRESERVE, USER1_COL1, USER1_COL2);
        checkAttributes(USER1_COL2, USER1_NAME, USER1_NAME, USER1_COL2_MODE, equalTo(getCreated(USER1_COL2)));
    }

    /**
     * As a DBA copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_COL}.
     */
    @Test
    public void copyToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        copyCol(adminUser, NO_PRESERVE, USER1_COL1, USER1_NEW_COL);
        checkAttributes(USER1_NEW_COL, ADMIN_DB_USER, DBA_GROUP, USER1_COL1_MODE, not(getCreated(USER1_COL1)));
    }

    /**
     * As a DBA copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_COL2}.
     */
    @Test
    public void copyToExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        copyCol(adminUser, NO_PRESERVE, USER1_COL1, USER1_COL2);
        checkAttributes(USER1_COL2, USER1_NAME, USER1_NAME, USER1_COL2_MODE, equalTo(getCreated(USER1_COL2)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER2_NEW_COL}.
     */
    @Test
    public void copyToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        copyCol(user2, NO_PRESERVE, USER1_COL1, USER2_NEW_COL);
        checkAttributes(USER2_NEW_COL, USER2_NAME, USER2_NAME, USER1_COL1_MODE, not(getCreated(USER1_COL1)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_COL2}.
     */
    @Test
    public void copyToExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        copyCol(user2, NO_PRESERVE, USER1_COL1, USER2_COL2);
        checkAttributes(USER2_COL2, USER2_NAME, USER2_NAME, USER2_COL2_MODE, equalTo(getCreated(USER2_COL2)));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_COL}.
     */
    @Test
    public void copyPreserveToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        copyCol(user1, PRESERVE, USER1_COL1, USER1_NEW_COL);
        checkAttributes(USER1_NEW_COL, USER1_NAME, USER1_NAME, USER1_COL1_MODE, equalTo(getCreated(USER1_COL1)));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_COL2}.
     */
    @Test
    public void copyPreserveToExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalCol2Created = getCreated(USER1_COL2);
        copyCol(user1, PRESERVE, USER1_COL1, USER1_COL2);
        checkAttributes(USER1_COL2, USER1_NAME, USER1_NAME, USER1_COL1_MODE, equalTo(originalCol2Created));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_COL}.
     */
    @Test
    public void copyPreserveToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        copyCol(adminUser, PRESERVE, USER1_COL1, USER1_NEW_COL);
        checkAttributes(USER1_NEW_COL, USER1_NAME, USER1_NAME, USER1_COL1_MODE, equalTo(getCreated(USER1_COL1)));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_COL2}.
     */
    @Test
    public void copyPreserveToExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long originalCol2Created = getCreated(USER1_COL2);
        copyCol(adminUser, PRESERVE, USER1_COL1, USER1_COL2);
        checkAttributes(USER1_COL2, USER1_NAME, USER1_NAME, USER1_COL1_MODE, equalTo(originalCol2Created));
    }

    /**
     * Whilst preserving attributes,
     * as some other (non-owner) user copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER2_NEW_COL}.
     */
    @Test
    public void copyPreserveToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        copyCol(user2, PRESERVE, USER1_COL1, USER2_NEW_COL);
        checkAttributes(USER2_NEW_COL, USER2_NAME, USER2_NAME, USER1_COL1_MODE, equalTo(getCreated(USER1_COL1)));
    }

    /**
     * Whilst preserving attributes,
     * as some other (non-owner) user copy {@link #USER1_COL1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_COL2}.
     */
    @Test
    public void copyPreserveToExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalCol2Created = getCreated(USER2_COL2);
        copyCol(user2, PRESERVE, USER1_COL1, USER2_COL2);
        checkAttributes(USER2_COL2, USER2_NAME, USER2_NAME, USER1_COL1_MODE, equalTo(originalCol2Created));
    }

    /**
     * Test copy collection /db/a/b/c/d/e/f/g/h/i/j/k to /db/z/y/x/w/v/u/k
     */
    @Test
    public void copyDeep() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final XmldbURI srcUri = XmldbURI.create("/db/a/b/c/d/e/f/g/h/i/j/k");
        final XmldbURI destUri = XmldbURI.create("/db/z/y/x/w/v/u");
        final XmldbURI newName = srcUri.lastSegment();

        final BrokerPool pool = existWebServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            try (final Collection src = broker.getOrCreateCollection(transaction, srcUri)) {
                assertNotNull(src);
                broker.saveCollection(transaction, src);
            }

            try (final Collection dst = broker.getOrCreateCollection(transaction, destUri)) {
                assertNotNull(dst);
                broker.saveCollection(transaction, dst);
            }

            try(final Collection src = broker.openCollection(srcUri, LockMode.WRITE_LOCK);
                final Collection dst = broker.openCollection(destUri, LockMode.WRITE_LOCK)) {

                broker.copyCollection(transaction, src, dst, newName);
            }

            transact.commit(transaction);
        }

        // check that the source still exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            try (final Collection src = broker.openCollection(srcUri, LockMode.READ_LOCK)) {
                assertNotNull(src);
            }

            transaction.commit();
        }

        // check that the new copy exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI copyUri = destUri.append(newName);

            try (final Collection copy = broker.openCollection(copyUri, LockMode.READ_LOCK)) {
                assertNotNull(copy);
            }

            transaction.commit();
        }
    }

    /**
     * Test copy collection /db/a/b/c/d/e/f/g/h/i/j/k to /db/z/y/x/w/v/u/k
     *
     * Note that the collection /db/a/b/c/d/e/f/g/h/i/j/k has the sub-collections (sub-1 and sub-2),
     * this test checks that the sub-collections are correctly preserved.
     */
    @Test
    public void copyDeepWithSubCollections() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final XmldbURI srcUri = XmldbURI.create("/db/a/b/c/d/e/f/g/h/i/j/k");
        final XmldbURI srcSubCol1Uri = srcUri.append("sub-1");
        final XmldbURI srcSubCol2Uri = srcUri.append("sub-2");
        final XmldbURI destUri = XmldbURI.create("/db/z/y/x/w/v/u");
        final XmldbURI newName = srcUri.lastSegment();

        final BrokerPool pool = existWebServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            // create src collection
            try (final Collection src = broker.getOrCreateCollection(transaction, srcUri)) {
                assertNotNull(src);
                broker.saveCollection(transaction, src);
            }

            // create src sub-collections
            try (final Collection srcColSubCol1 = broker.getOrCreateCollection(transaction, srcSubCol1Uri)) {
                assertNotNull(srcColSubCol1);
                broker.saveCollection(transaction, srcColSubCol1);
            }
            try (final Collection srcColSubCol2 = broker.getOrCreateCollection(transaction, srcSubCol2Uri)) {
                assertNotNull(srcColSubCol2);
                broker.saveCollection(transaction, srcColSubCol2);
            }

            // create dst collection
            try (Collection dst = broker.getOrCreateCollection(transaction, destUri)) {
                assertNotNull(dst);
                broker.saveCollection(transaction, dst);
            }

            try (final Collection src = broker.openCollection(srcUri, LockMode.WRITE_LOCK);
                final Collection dst = broker.openCollection(destUri, LockMode.WRITE_LOCK)) {

                broker.copyCollection(transaction, src, dst, newName);
            }

            transact.commit(transaction);
        }

        // check that the source still exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            try (final Collection src = broker.openCollection(srcUri, LockMode.READ_LOCK)) {
                assertNotNull(src);
            }

            // check that the source sub-collections still exist
            try (final Collection srcSubCol1 = broker.openCollection(srcSubCol1Uri, LockMode.READ_LOCK)) {
                assertNotNull(srcSubCol1);
            }
            try (final Collection srcSubCol2 = broker.openCollection(srcSubCol2Uri, LockMode.READ_LOCK)) {
                assertNotNull(srcSubCol2);
            }

            transaction.commit();
        }

        // check that the new copy exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI copyUri = destUri.append(newName);

            try (final Collection copy = broker.openCollection(copyUri, LockMode.READ_LOCK)) {
                assertNotNull(copy);
            }

            // check that the new copy has sub-collection copies
            final XmldbURI copySubCol1Uri = copyUri.append(srcSubCol1Uri.lastSegment());
            try (final Collection copySubCol1 = broker.openCollection(copySubCol1Uri, LockMode.READ_LOCK)) {
                assertNotNull(copySubCol1);
            }
            final XmldbURI copySubCol2Uri = copyUri.append(srcSubCol2Uri.lastSegment());
            try (final Collection copySubCol2 = broker.openCollection(copySubCol2Uri, LockMode.READ_LOCK)) {
                assertNotNull(copySubCol2);
            }

            transaction.commit();
        }
    }

    private void copyCol(final Subject execAsUser, final DBBroker.PreserveType preserve, final XmldbURI srcColName, final XmldbURI destColName) throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        final XmldbURI src = TEST_COLLECTION_URI.append(srcColName);
        final XmldbURI dest = TEST_COLLECTION_URI.append(destColName);

        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection srcCol = broker.openCollection(src, LockMode.READ_LOCK);
                final Collection destCol = broker.openCollection(dest.removeLastSegment(), LockMode.WRITE_LOCK)) {

            // Wait a moment to ensure both resources have a different timestamp.
            Thread.sleep(10);
            broker.copyCollection(transaction, srcCol, destCol, dest.lastSegment(), preserve);

            transaction.commit();
        } catch (InterruptedException e) {
            throw new EXistException(e);
        }

        // basic shallow check that copy of the collection is the same as the original
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
                final Collection original = broker.openCollection(src, LockMode.READ_LOCK);
                final Collection copy = broker.openCollection(dest, LockMode.READ_LOCK)) {

            assertEquals(original.getDocumentCount(broker), copy.getDocumentCount(broker));
            assertEquals(original.getChildCollectionCount(broker), copy.getChildCollectionCount(broker));
        }
    }

    private long getCreated(final XmldbURI colName) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col = broker.openCollection(TEST_COLLECTION_URI.append(colName), LockMode.READ_LOCK)) {
            return col.getCreated();
        }
    }

    private void checkAttributes(final XmldbURI colName, final String expectedOwner, final String expectedGroup, final int expectedMode, final Matcher<Long> expectedCreated) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col = broker.openCollection(TEST_COLLECTION_URI.append(colName), LockMode.READ_LOCK)) {

            final Permission permission = col.getPermissions();
            assertEquals("Owner value was not expected", expectedOwner, permission.getOwner().getName());
            assertEquals("Group value was not expected", expectedGroup, permission.getGroup().getName());
            assertEquals("Mode value was not expected", expectedMode, permission.getMode());

            assertThat("Created value is not correct", col.getCreated(), expectedCreated);
        }
    }

    @BeforeClass
    public static void prepareDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            chmod(broker, transaction, collection.getURI(), 511);
            broker.saveCollection(transaction, collection);

            createUser(broker, sm, USER1_NAME, USER1_PWD);
            createUser(broker, sm, USER2_NAME, USER2_PWD);

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
                final Collection collection = broker.openCollection(TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {

            final Collection u1c1 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append(USER1_COL1));
            chmod(broker, u1c1, USER1_COL1_MODE);
            broker.saveCollection(transaction, u1c1);

            final Collection u1c2 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append(USER1_COL2));
            chmod(broker, u1c2, USER1_COL2_MODE);
            broker.saveCollection(transaction, u1c2);

            broker.saveCollection(transaction, collection);

            transaction.commit();
        }

        // create user2 resources
        final Subject user2 = pool.getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        try (final DBBroker broker = pool.get(Optional.of(user2));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection collection = broker.openCollection(TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {

            final Collection u2c2 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append(USER2_COL2));
            chmod(broker, u2c2, USER2_COL2_MODE);
            broker.saveCollection(transaction, u2c2);

            broker.saveCollection(transaction, collection);

            transaction.commit();
        }
    }

    @After
    public void teardown() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeCollection(broker, transaction, TEST_COLLECTION_URI.append(USER1_COL1));
            removeCollection(broker, transaction, TEST_COLLECTION_URI.append(USER1_COL2));
            removeCollection(broker, transaction, TEST_COLLECTION_URI.append(USER1_NEW_COL));

            removeCollection(broker, transaction, TEST_COLLECTION_URI.append(USER2_COL2));
            removeCollection(broker, transaction, TEST_COLLECTION_URI.append(USER2_NEW_COL));

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

            removeCollection(broker, transaction, TEST_COLLECTION_URI);

            transaction.commit();
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

    private static void chmod(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final int mode) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, transaction, pathUri, Optional.of(mode), Optional.empty());
    }

    private static void chmod(final DBBroker broker, final Collection collection, final int mode) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, collection, Optional.of(mode), Optional.empty());
    }

    private static void removeUser(final SecurityManager sm, final String username) throws PermissionDeniedException, EXistException {
        sm.deleteAccount(username);
        sm.deleteGroup(username);
    }

    private static void removeCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(collectionUri, LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
        }
    }
}
