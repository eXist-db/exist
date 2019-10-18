package org.exist.security;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.exist.xmldb.XmldbURI.ROOT_COLLECTION;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class FnCollectionSecurityTest {

    private static final String TEST_USER_1 = "docTestUser1";

    private static final String TEST_COLLECTION_ALL = "/db/all";
    private static final String TEST_COLLECTION_SYSTEM_ONLY = "/db/system-only";

    private static final String TEST_COLLECTION_1 = "/db/fnCollectionSecurityTest1";
    private static final String TEST_SUB_COLLECTION_1 = TEST_COLLECTION_1 + "/child1";
    private static final String TEST_SUB_COLLECTION_1_1 = TEST_SUB_COLLECTION_1 + "/child1_1";

    private static final String TEST_COLLECTION_2 = "/db/fnCollectionSecurityTest2";
    private static final String TEST_SUB_COLLECTION_2 = TEST_COLLECTION_2 + "/child2";
    private static final String TEST_SUB_COLLECTION_2_2 = TEST_SUB_COLLECTION_2 + "/child2_2";

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    /**
     * Sets up the database like:
     *
     *  /db/all                                  system:dba rwxrwxrwx
     *  /db/system-only                          system:dba rwx------
     *
     *  /db/fnDocSecurityTest1                   system:dba rwxr-xr--
     *  /db/fnDocSecurityTest1/child1            system:dba rwxrwxrwx
     *  /db/fnDocSecurityTest1/child1/child1_1   system:dba rwxrwxrwx
     *
     *  /db/fnDocSecurityTest2                   system:dba rwxr-xr-x+ (acl=[DENIED USER docTestUser1 "r-x"])
     *  /db/fnDocSecurityTest2/child2            system:dba rwxrwxrwx
     *  /db/fnDocSecurityTest2/child2/child2_2   system:dba rwxrwxrwx
     *
     * Creates a new user: docTestUser1
     */
    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, SyntaxException, IOException, SAXException, LockException {

        // as system user
        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createUser(securityManager, broker, TEST_USER_1);

            // create /db/all.xml
            createCollection(broker, transaction, TEST_COLLECTION_ALL, "rwxrwxrwx");

            // create /db/system-only.xml
            createCollection(broker, transaction, TEST_COLLECTION_SYSTEM_ONLY, "rwx------");

            // create /db/fnCollectionSecurityTest1...
            createCollection(broker, transaction, TEST_COLLECTION_1, "rwxr-xr--");
            createCollection(broker, transaction, TEST_SUB_COLLECTION_1, "rwxrwxrwx");
            createCollection(broker, transaction, TEST_SUB_COLLECTION_1_1, "rwxrwxrwx");

            // create /db/fnDocSecurityTest2...
            final ACEAider ace = new ACEAider(ACLPermission.ACE_ACCESS_TYPE.DENIED, ACLPermission.ACE_TARGET.USER, TEST_USER_1, SimpleACLPermission.aceSimpleSymbolicModeToInt("r-x"));
            createCollection(broker, transaction, TEST_COLLECTION_2, "rwxr-xr-x", ace);
            createCollection(broker, transaction, TEST_SUB_COLLECTION_2, "rwxrwxrwx");
            createCollection(broker, transaction, TEST_SUB_COLLECTION_2_2, "rwxrwxrwx");

            transaction.commit();
        }
    }

    @Test
    public void canAccessRoot() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException, IOException, SAXException {
        // as docTestUser1 user
        final String query = "fn:collection('" + ROOT_COLLECTION + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);

            transaction.commit();
        }
    }

    @Test
    public void canAccessCollection() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException, IOException, SAXException {
        // as docTestUser1 user
        final String query = "fn:collection('" + TEST_COLLECTION_ALL + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);

            transaction.commit();
        }
    }

    @Test(expected=PermissionDeniedException.class)
    public void cannotAccessRestrictedCollection() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException, IOException, SAXException {
        // as docTestUser1 user
        final String query = "fn:collection('" + TEST_COLLECTION_SYSTEM_ONLY + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);
            fail("Expected PermissionDeniedException via XPathException");

            transaction.commit();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    @Test(expected=PermissionDeniedException.class)
    public void cannotAccessCollectionInCollectionHierarchyWithDeniedExecute() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException {

        // as docTestUser1 user
        final String query = "fn:collection('" + TEST_SUB_COLLECTION_1_1 + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);
            fail("Expected PermissionDeniedException via XPathException");

            transaction.commit();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    @Test(expected=PermissionDeniedException.class)
    public void cannotAccessCollectionInCollectionHierarchyWithDeniedReadAndExecuteAce() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException {

        // as docTestUser1 user
        final String query = "fn:collection('" + TEST_SUB_COLLECTION_2_2 + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);
            fail("Expected PermissionDeniedException via XPathException");

            transaction.commit();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private static void createUser(final SecurityManager securityManager, final DBBroker broker, final String username) throws PermissionDeniedException, EXistException {
        final UserAider user = new UserAider(username);
        user.setPassword(username);

        Group group = new GroupAider(username);
        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + username);
        group.addManager(user);
        securityManager.addGroup(broker, group);

        // add the personal group as the primary group
        user.addGroup(username);

        securityManager.addAccount(user);

        // add the new account as a manager of their personal group
        group = securityManager.getGroup(username);
        group.addManager(securityManager.getAccount(username));
        securityManager.updateGroup(group);
    }

    private static void createCollection(final DBBroker broker, final Txn transaction, final String collectionUri, final String modeStr, final ACEAider... aces) throws PermissionDeniedException, IOException, TriggerException, SyntaxException {
        try (final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.create(collectionUri))) {
            final Permission permissions = collection.getPermissions();
            permissions.setMode(modeStr);
            if (permissions instanceof SimpleACLPermission) {
                final SimpleACLPermission aclPermissions = (SimpleACLPermission)permissions;
                for (final ACEAider ace : aces) {
                    aclPermissions.addACE(
                            ace.getAccessType(),
                            ace.getTarget(),
                            ace.getWho(),
                            ace.getMode()
                    );
                }
            }
            broker.saveCollection(transaction, collection);
        }
    }
}
