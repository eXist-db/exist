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
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.AuthenticationException;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DBBroker#getResourceForExecution(XmldbURI, LockMode)} is the single boundary at which a
 * stored query is authorized for execution: it gates on EXECUTE rather than READ, so that the
 * database can compile a query on behalf of a caller which may run it but not read it — as a Unix
 * kernel reads a {@code --x} binary for a process which cannot read it.
 *
 * This asserts the gate itself: EXECUTE grants the handle, READ alone does not, and the handle
 * reports whether the caller may also read the source (which decides how much of a failure may be
 * disclosed to them). The READ-gated getters must be unaffected.
 */
public class GetResourceForExecutionTest {

    private static final String TEST_USER = "executeWithoutReadTestUser";

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/executeWithoutReadTest");

    /** rwx--x--x — the caller may execute it, but not read it */
    private static final XmldbURI EXECUTE_ONLY = TEST_COLLECTION.append("execute-only.xq");

    /** rwxr-xr-x — the usual mode: the caller may both execute and read it */
    private static final XmldbURI EXECUTE_AND_READ = TEST_COLLECTION.append("execute-and-read.xq");

    /** rwxr--r-- — the caller may read it, but not execute it */
    private static final XmldbURI READ_ONLY = TEST_COLLECTION.append("read-only.xq");

    private static final XmldbURI NOT_STORED = TEST_COLLECTION.append("not-stored.xq");

    private static final String QUERY = "<result>{ 1 + 1 }</result>";

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, SyntaxException, IOException, SAXException, LockException, TriggerException {
        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();

        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createUser(securityManager, broker, TEST_USER);

            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
            }

            storeQuery(broker, transaction, EXECUTE_ONLY, "rwx--x--x");
            storeQuery(broker, transaction, EXECUTE_AND_READ, "rwxr-xr-x");
            storeQuery(broker, transaction, READ_ONLY, "rwxr--r--");

            transaction.commit();
        }
    }

    @Test
    public void executeOnlyResourceIsResolvedAndReportsThatTheCallerCannotRead() throws EXistException, AuthenticationException, PermissionDeniedException {
        try (final DBBroker broker = testUserBroker();
             final ExecutableResource resource = broker.getResourceForExecution(EXECUTE_ONLY, LockMode.READ_LOCK)) {

            assertNotNull("EXECUTE alone must be enough to resolve a query for execution", resource);
            assertNotNull(resource.document().getDocument());
            assertFalse("the caller may execute but not read, so failures must not be disclosed to them",
                    resource.callerCanRead());
        }
    }

    @Test
    public void readableResourceIsResolvedAndReportsThatTheCallerCanRead() throws EXistException, AuthenticationException, PermissionDeniedException {
        try (final DBBroker broker = testUserBroker();
             final ExecutableResource resource = broker.getResourceForExecution(EXECUTE_AND_READ, LockMode.READ_LOCK)) {

            assertNotNull(resource);
            assertTrue("the caller may read the source, so failures may be disclosed in full",
                    resource.callerCanRead());
        }
    }

    @Test
    public void readWithoutExecuteIsDenied() throws EXistException, AuthenticationException {
        try (final DBBroker broker = testUserBroker()) {
            broker.getResourceForExecution(READ_ONLY, LockMode.READ_LOCK);
            fail("Execution must require EXECUTE, being able to read the query is not enough");
        } catch (final PermissionDeniedException expected) {
            // expected
        }
    }

    @Test
    public void missingResourceIsNotFoundRatherThanDenied() throws EXistException, AuthenticationException, PermissionDeniedException {
        try (final DBBroker broker = testUserBroker()) {
            assertNull(broker.getResourceForExecution(NOT_STORED, LockMode.READ_LOCK));
        }
    }

    @Test
    public void theDbaCanExecuteAndReadEverything() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final ExecutableResource resource = broker.getResourceForExecution(EXECUTE_ONLY, LockMode.READ_LOCK)) {

            assertNotNull(resource);
            assertTrue("a DBA is never read-blind", resource.callerCanRead());
        }
    }

    /**
     * The data-read getters keep their READ semantics: relaxing the execution gate must not open a
     * way to fetch the source of an execute-only query as data.
     */
    @Test
    public void readingAnExecuteOnlyResourceAsDataIsStillDenied() throws EXistException, AuthenticationException {
        try (final DBBroker broker = testUserBroker()) {
            broker.getXMLResource(EXECUTE_ONLY, LockMode.READ_LOCK);
            fail("Reading a query as data must still require READ");
        } catch (final PermissionDeniedException expected) {
            // expected
        }
    }

    /**
     * The existing three argument getter must keep validating READ, so that every caller which has
     * not been migrated to the execution boundary behaves exactly as before.
     */
    @Test
    public void theCollectionGetterDefaultsToRead() throws EXistException, AuthenticationException, PermissionDeniedException, LockException {
        try (final DBBroker broker = testUserBroker();
             final Collection collection = broker.openCollection(TEST_COLLECTION, LockMode.READ_LOCK)) {

            try (final LockedDocument lockedDocument = collection.getDocumentWithLock(broker, EXECUTE_ONLY.lastSegment(), LockMode.READ_LOCK)) {
                fail("the three argument getter must still require READ");
            } catch (final PermissionDeniedException expected) {
                // expected
            }

            try (final LockedDocument lockedDocument = collection.getDocumentWithLock(broker, EXECUTE_ONLY.lastSegment(), LockMode.READ_LOCK, Permission.EXECUTE)) {
                assertNotNull("the same document is reachable when EXECUTE is the required mode", lockedDocument);
            }
        }
    }

    private static DBBroker testUserBroker() throws EXistException, AuthenticationException {
        final BrokerPool pool = server.getBrokerPool();
        final Subject testUser = pool.getSecurityManager().authenticate(TEST_USER, TEST_USER);
        return pool.get(Optional.of(testUser));
    }

    private static void storeQuery(final DBBroker broker, final Txn transaction, final XmldbURI uri, final String modeStr)
            throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, SyntaxException {
        try (final Collection collection = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
            broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(QUERY.getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
        }
        PermissionFactory.chmod_str(broker, transaction, uri, Optional.of(modeStr), Optional.empty());
    }

    private static void createUser(final SecurityManager securityManager, final DBBroker broker, final String username) throws PermissionDeniedException, EXistException {
        final UserAider user = new UserAider(username);
        user.setPassword(username);

        Group group = new GroupAider(username);
        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + username);
        group.addManager(user);
        securityManager.addGroup(broker, group);

        user.addGroup(username);
        securityManager.addAccount(user);

        group = securityManager.getGroup(username);
        group.addManager(securityManager.getAccount(username));
        securityManager.updateGroup(group);
    }
}
