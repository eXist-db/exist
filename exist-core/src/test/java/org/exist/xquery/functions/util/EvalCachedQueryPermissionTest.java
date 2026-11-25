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
package org.exist.xquery.functions.util;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.AuthenticationException;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Code that {@code util:eval} keeps with the calling query must not let a later user of that query run what
 * compiling the code would refuse them: a module the code imports which they may not read.
 */
public class EvalCachedQueryPermissionTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String TEST_USER = "evalCachedQueryUser";

    private static final XmldbURI COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("eval-cached-query-permission");

    private static final String SECRET = """
            module namespace secret = "http://exist-db.org/test/eval-cached-query-permission/secret";
            declare function secret:answer() { 42 };
            """;

    private static final String EVALUATED = """
            import module namespace secret = "http://exist-db.org/test/eval-cached-query-permission/secret"
                at "xmldb:exist:///db/eval-cached-query-permission/secret.xqm";
            secret:answer()
            """;

    /** Evaluates the stored code. */
    private static final Source EVAL_STORED = new StringSource(
            "util:eval(xs:anyURI('/db/eval-cached-query-permission/evaluated.xq'), true())");

    /** Evaluates the same code given as a string. */
    private static final Source EVAL_STRING = new StringSource(
            "util:eval(``[" + EVALUATED + "]``, true())");

    @BeforeAll
    static void setup() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            createUser(securityManager, broker);
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
            }
            store(broker, transaction, "secret.xqm", SECRET, "rwx------");
            store(broker, transaction, "evaluated.xq", EVALUATED, "rwxr-xr-x");
            transaction.commit();
        }
    }

    @BeforeEach
    void clearPool() {
        existEmbeddedServer.getBrokerPool().getXQueryPool().clear();
    }

    @Test
    void compilingTheStoredCodeIsRefused() throws Exception {
        assertCompilingIsRefused(EVAL_STORED);
    }

    @Test
    void runningTheKeptStoredCodeIsRefused() throws Exception {
        assertRunningTheKeptCodeIsRefused(EVAL_STORED);
    }

    @Test
    void compilingTheCodeStringIsRefused() throws Exception {
        assertCompilingIsRefused(EVAL_STRING);
    }

    @Test
    void runningTheKeptCodeStringIsRefused() throws Exception {
        assertRunningTheKeptCodeIsRefused(EVAL_STRING);
    }

    private static void assertCompilingIsRefused(final Source mainSource) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(testUser()))) {
            final CompiledXQuery main = xquery.compile(new XQueryContext(pool), mainSource);
            assertThrows(XPathException.class, () -> xquery.execute(broker, main, null));
        }
    }

    private static void assertRunningTheKeptCodeIsRefused(final Source mainSource) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        final XQueryPool xqueryPool = pool.getXQueryPool();

        // a user who may read the module runs the query first, which keeps the compiled code
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final CompiledXQuery main = xquery.compile(new XQueryContext(pool), mainSource);
            assertEquals("42", xquery.execute(broker, main, null).getStringValue());
            xqueryPool.returnCompiledXQuery(mainSource, main);
        }

        try (final DBBroker broker = pool.get(Optional.of(testUser()))) {
            final CompiledXQuery main = xqueryPool.borrowCompiledXQuery(broker, mainSource);
            assertNotNull(main, "the main query imports nothing the user may not read");
            assertThrows(XPathException.class, () -> xquery.execute(broker, main, null));
        }
    }

    private static Subject testUser() throws EXistException, AuthenticationException {
        return existEmbeddedServer.getBrokerPool().getSecurityManager().authenticate(TEST_USER, TEST_USER);
    }

    private static void store(final DBBroker broker, final Txn transaction, final String name, final String query,
            final String mode) throws Exception {
        try (final Collection collection = broker.openCollection(COLLECTION, LockMode.WRITE_LOCK)) {
            broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(query.getBytes(UTF_8)),
                    MimeType.XQUERY_TYPE, collection);
        }
        PermissionFactory.chmod_str(broker, transaction, COLLECTION.append(name), Optional.of(mode), Optional.empty());
    }

    private static void createUser(final SecurityManager securityManager, final DBBroker broker)
            throws PermissionDeniedException, EXistException {
        final UserAider user = new UserAider(TEST_USER);
        user.setPassword(TEST_USER);

        Group group = new GroupAider(TEST_USER);
        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + TEST_USER);
        group.addManager(user);
        securityManager.addGroup(broker, group);

        user.addGroup(TEST_USER);
        securityManager.addAccount(user);

        group = securityManager.getGroup(TEST_USER);
        group.addManager(securityManager.getAccount(TEST_USER));
        securityManager.updateGroup(group);
    }
}
