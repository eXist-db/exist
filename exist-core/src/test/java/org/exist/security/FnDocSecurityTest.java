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
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.SyntaxException;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;

import static org.exist.xmldb.XmldbURI.ROOT_COLLECTION;
import static org.junit.Assert.*;

public class FnDocSecurityTest {

    private static final String TEST_USER_1 = "docTestUser1";

    private static final String TEST_DOC_NAME_ALL = "all.xml";
    private static final String TEST_DOC_URI_ALL = ROOT_COLLECTION + "/" + TEST_DOC_NAME_ALL;
    private static final String TEST_DOC_NAME_SYSTEM_ONLY = "system-only.xml";
    private static final String TEST_DOC_URI_SYSTEM_ONLY = ROOT_COLLECTION + "/" + TEST_DOC_NAME_SYSTEM_ONLY;

    private static final String TEST_COLLECTION_1 = "/db/fnDocSecurityTest1";
    private static final String TEST_SUB_COLLECTION_1 = TEST_COLLECTION_1 + "/child1";
    private static final String TEST_DOC_NAME_1 = "doc1.xml";
    private static final String TEST_DOC_URI_1 = TEST_SUB_COLLECTION_1 + "/" + TEST_DOC_NAME_1;

    private static final String TEST_COLLECTION_2 = "/db/fnDocSecurityTest2";
    private static final String TEST_SUB_COLLECTION_2 = TEST_COLLECTION_2 + "/child2";
    private static final String TEST_DOC_NAME_2 = "doc2.xml";
    private static final String TEST_DOC_URI_2 = TEST_SUB_COLLECTION_2 + "/" + TEST_DOC_NAME_2;

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    /**
     * Sets up the database like:
     *
     *  /db/all.xml                              system:dba rwxrwxrwx
     *  /db/system-only.xml                      system:dba rwx------
     *
     *  /db/fnDocSecurityTest1                   system:dba rwxr-xr--
     *  /db/fnDocSecurityTest1/child1            system:dba rwxrwxrwx
     *  /db/fnDocSecurityTest1/child1/doc1.xml   system:dba rwxrwxrwx
     *
     *  /db/fnDocSecurityTest2                   system:dba rwxr-xr-x+ (acl=[DENIED USER docTestUser1 "r-x"])
     *  /db/fnDocSecurityTest2/child2            system:dba rwxrwxrwx
     *  /db/fnDocSecurityTest2/child2/doc2.xml   system:dba rwxrwxrwx
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
            createDocument(broker, transaction, ROOT_COLLECTION, TEST_DOC_NAME_ALL, "<hello/>", "rwxrwxrwx");

            // create /db/system-only.xml
            createDocument(broker, transaction, ROOT_COLLECTION, TEST_DOC_NAME_SYSTEM_ONLY, "<hello/>", "rwx------");

            // create /db/fnDocSecurityTest1...
            createCollection(broker, transaction, TEST_COLLECTION_1, "rwxr-xr--");
            createCollection(broker, transaction, TEST_SUB_COLLECTION_1, "rwxrwxrwx");
            createDocument(broker, transaction, TEST_SUB_COLLECTION_1, TEST_DOC_NAME_1, "<hello/>", "rwxrwxrwx");

            // create /db/fnDocSecurityTest2...
            final ACEAider ace = new ACEAider(ACLPermission.ACE_ACCESS_TYPE.DENIED, ACLPermission.ACE_TARGET.USER, TEST_USER_1, SimpleACLPermission.aceSimpleSymbolicModeToInt("r-x"));
            createCollection(broker, transaction, TEST_COLLECTION_2, "rwxr-xr-x", ace);
            createCollection(broker, transaction, TEST_SUB_COLLECTION_2, "rwxrwxrwx");
            createDocument(broker, transaction, TEST_SUB_COLLECTION_2, TEST_DOC_NAME_2, "<hello/>", "rwxrwxrwx");

            transaction.commit();
        }
    }

    @Test
    public void canAccessDocument() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException, IOException, SAXException {
        // as docTestUser1 user
        final String query = "fn:doc('" + TEST_DOC_URI_ALL + "')";

        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        final Subject testUser1 = securityManager.authenticate(TEST_USER_1, TEST_USER_1);

        try (final DBBroker broker = pool.get(Optional.of(testUser1));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            assertEquals("<hello/>", serialize(broker, result));

            transaction.commit();
        }
    }

    @Test(expected=PermissionDeniedException.class)
    public void cannotAccessRestrictedDocument() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException, IOException, SAXException {
        // as docTestUser1 user
        final String query = "fn:doc('" + TEST_DOC_URI_SYSTEM_ONLY + "')";

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
    public void cannotAccessDocumentInCollectionHierarchyWithDeniedExecute() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException {

        // as docTestUser1 user
        final String query = "fn:doc('" + TEST_DOC_URI_1 + "')";

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
    public void cannotAccessDocumentInCollectionHierarchyWithDeniedReadAndExecuteAce() throws EXistException, AuthenticationException, PermissionDeniedException, XPathException {

        // as docTestUser1 user
        final String query = "fn:doc('" + TEST_DOC_URI_2 + "')";

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

    private static void createDocument(final DBBroker broker, final Txn transaction, final String collectionUri, final String docName, final String content, final String modeStr) throws PermissionDeniedException, LockException, SAXException, EXistException, IOException, SyntaxException {
        try (final Collection collection = broker.openCollection(XmldbURI.create(collectionUri), Lock.LockMode.WRITE_LOCK)) {
            broker.storeDocument(transaction, XmldbURI.create(docName), new StringInputSource(content), MimeType.XML_TYPE, collection);

            PermissionFactory.chmod_str(broker, transaction, XmldbURI.create(collectionUri).append(docName), Optional.of(modeStr), Optional.empty());
        }
    }

    private String serialize(final DBBroker broker, final Sequence sequence) throws IOException, XPathException, SAXException {
        try (final StringWriter writer = new StringWriter()) {
            final XQuerySerializer serializer = new XQuerySerializer(broker, new Properties(), writer);
            serializer.serialize(sequence);
            return writer.toString();
        }
    }
}
