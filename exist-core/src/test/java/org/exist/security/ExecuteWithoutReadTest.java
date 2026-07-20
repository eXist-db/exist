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
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ExecutableResource;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ErrorDisclosure;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Executing a stored query requires EXECUTE, and a caller which may execute but not read it must
 * learn only that the execution failed. Both stored modules below are run twice — once by a caller
 * which may read them and once by a caller which may not — and the difference must be visible in
 * the failures only, never in the results.
 *
 * NOTE: no execution entry point (REST, RESTXQ, XQueryServlet, …) is wired to this mechanism yet,
 * that is Phase 1b/1c of eXist-db/exist#6568. {@link #executeStoredQuery} therefore stands in for
 * what those loaders will do: resolve the query on EXECUTE, take the disclosure level from the
 * resolved handle, compile, execute, and filter any failure through {@link ErrorDisclosure}.
 */
public class ExecuteWithoutReadTest {

    private static final String TEST_USER = "executeWithoutReadUser";

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/executeWithoutRead");
    private static final XmldbURI VALID_QUERY = TEST_COLLECTION.append("valid.xq");
    private static final XmldbURI SYNTAX_ERROR_QUERY = TEST_COLLECTION.append("syntax-error.xq");
    private static final XmldbURI RUNTIME_ERROR_QUERY = TEST_COLLECTION.append("runtime-error.xq");

    private static final String VALID_MODULE =
            "xquery version \"3.1\";\n<result>{ sum(1 to 3) }</result>";

    /** the trailing operand is missing, so this fails to compile with XPST0003 */
    private static final String SYNTAX_ERROR_MODULE =
            "xquery version \"3.1\";\nlet $secret := 1\nreturn $secret +";

    /** this compiles, and fails with FOAR0001 once it runs */
    private static final String RUNTIME_ERROR_MODULE =
            "xquery version \"3.1\";\nlet $secret := 0\nreturn 1 idiv $secret";

    private static final String READ_AND_EXECUTE = "rwxr-xr-x";
    private static final String EXECUTE_ONLY = "rwx--x--x";

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, TriggerException {
        final BrokerPool pool = server.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();

        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createUser(securityManager, broker, TEST_USER);

            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
            }

            storeQuery(broker, transaction, VALID_QUERY, VALID_MODULE);
            storeQuery(broker, transaction, SYNTAX_ERROR_QUERY, SYNTAX_ERROR_MODULE);
            storeQuery(broker, transaction, RUNTIME_ERROR_QUERY, RUNTIME_ERROR_MODULE);

            transaction.commit();
        } catch (final SyntaxException e) {
            throw new EXistException(e);
        }
    }

    // --- 1. the caller may read AND execute the query: failures are disclosed in full ---

    @Test
    public void readAndExecuteValidQueryReturnsItsResult() throws Exception {
        chmodAll(READ_AND_EXECUTE);

        try (final DBBroker broker = testUserBroker()) {
            final Sequence result = executeStoredQuery(broker, VALID_QUERY, true);

            assertEquals(1, result.getItemCount());
            assertEquals("6", result.itemAt(0).getStringValue());
        }
    }

    @Test
    public void readAndExecuteSyntaxErrorIsDisclosedInFull() throws Exception {
        chmodAll(READ_AND_EXECUTE);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, SYNTAX_ERROR_QUERY, true);
            fail("the query does not compile, so it must fail");
        } catch (final XPathException e) {
            assertEquals("a read-capable caller sees the real error", ErrorCodes.XPST0003, e.getErrorCode());
            assertFalse("a read-capable caller is not fobbed off with the generic error",
                    e.getMessage().contains("Query execution failed"));
        }
    }

    @Test
    public void readAndExecuteRuntimeErrorIsDisclosedInFull() throws Exception {
        chmodAll(READ_AND_EXECUTE);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, RUNTIME_ERROR_QUERY, true);
            fail("the query divides by zero, so it must fail");
        } catch (final XPathException e) {
            assertEquals("a read-capable caller sees the real error", ErrorCodes.FOAR0001, e.getErrorCode());
        }
    }

    // --- 2. the caller may only execute the query: it runs, but failures are generic ---

    @Test
    public void executeOnlyValidQueryReturnsTheSameResult() throws Exception {
        chmodAll(EXECUTE_ONLY);

        try (final DBBroker broker = testUserBroker()) {
            final Sequence result = executeStoredQuery(broker, VALID_QUERY, true);

            assertEquals("a query which succeeds returns its results, being unreadable changes nothing",
                    1, result.getItemCount());
            assertEquals("6", result.itemAt(0).getStringValue());
        }
    }

    @Test
    public void executeOnlySyntaxErrorIsGeneric() throws Exception {
        chmodAll(EXECUTE_ONLY);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, SYNTAX_ERROR_QUERY, true);
            fail("the query does not compile, so it must fail");
        } catch (final XPathException e) {
            assertGeneric(e);
        }
    }

    @Test
    public void executeOnlyRuntimeErrorIsGeneric() throws Exception {
        chmodAll(EXECUTE_ONLY);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, RUNTIME_ERROR_QUERY, true);
            fail("the query divides by zero, so it must fail");
        } catch (final XPathException e) {
            assertGeneric(e);
        }
    }

    // --- what each half of the mechanism is responsible for ---

    /**
     * XQuery.execute recomputes the disclosure level from the current subject on every execution, so
     * a runtime failure is sanitized even for a loader which never set the level itself. This is what
     * protects a query served from the XQuery pool, whose context was primed by another user.
     */
    @Test
    public void executeOnlyRuntimeErrorIsGenericEvenWhenOnlyXQueryExecuteSetsTheLevel() throws Exception {
        chmodAll(EXECUTE_ONLY);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, RUNTIME_ERROR_QUERY, false);
            fail("the query divides by zero, so it must fail");
        } catch (final XPathException e) {
            assertGeneric(e);
        }
    }

    /**
     * A compile error never reaches XQuery.execute, so XQuery.execute cannot sanitize it: the loader
     * MUST take the level from {@link ExecutableResource#callerCanRead()} before it compiles. This
     * pins that requirement — if a Phase 1b entry point forgets, a read-blind caller is handed the
     * syntax error of a query it cannot read, which is exactly the leak this mechanism exists to stop.
     */
    @Test
    public void aLoaderWhichDoesNotSetTheLevelBeforeCompilingLeaksTheSyntaxError() throws Exception {
        chmodAll(EXECUTE_ONLY);

        try (final DBBroker broker = testUserBroker()) {
            executeStoredQuery(broker, SYNTAX_ERROR_QUERY, false);
            fail("the query does not compile, so it must fail");
        } catch (final XPathException e) {
            assertEquals("compile errors bypass XQuery.execute entirely, so the loader has to set the"
                            + " disclosure level itself before compiling",
                    ErrorCodes.XPST0003, e.getErrorCode());
        }
    }

    /**
     * Stands in for the execution entry points until Phase 1b wires them up.
     *
     * @param loaderSetsDisclosure whether the loader takes the disclosure level from the resolved
     *     handle before compiling, as a Phase 1b entry point must. When false, only the level which
     *     {@link XQuery#execute} computes for itself applies.
     */
    private static Sequence executeStoredQuery(final DBBroker broker, final XmldbURI uri, final boolean loaderSetsDisclosure)
            throws PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = broker.getBrokerPool();

        try (final ExecutableResource resource = broker.getResourceForExecution(uri)) {
            assertNotNull("the caller holds EXECUTE, so the query must resolve", resource);

            final XQueryContext context = new XQueryContext(pool);
            if (loaderSetsDisclosure) {
                context.setErrorDisclosure(resource.callerCanRead() ? ErrorDisclosure.FULL : ErrorDisclosure.GENERIC);
            }

            final Source source = new DBSource(pool, (BinaryDocument) resource.document().getDocument(), true);
            final XQuery xquery = pool.getXQueryService();
            try {
                final CompiledXQuery compiled = xquery.compile(broker, context, source);
                return xquery.execute(broker, compiled, null);
            } catch (final XPathException e) {
                throw ErrorDisclosure.disclose(context, e);
            }
        }
    }

    private static void assertGeneric(final XPathException e) {
        assertEquals("a read-blind caller learns only that the execution failed",
                ErrorCodes.EXXQDY0010, e.getErrorCode());

        final String message = e.getMessage();
        assertTrue("the caller is given a correlation id to quote to the owner/DBA",
                message.matches(".*\\(ref [0-9a-f-]+\\)\\.?$"));
        assertFalse("the source must not leak", message.contains("$secret"));
        assertFalse("the spec error code must not leak", message.contains("XPST0003"));
        assertFalse("the spec error code must not leak", message.contains("FOAR0001"));
        assertFalse("the location must not leak", message.contains("at line"));
    }

    private static void chmodAll(final String modeStr) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            for (final XmldbURI uri : new XmldbURI[]{VALID_QUERY, SYNTAX_ERROR_QUERY, RUNTIME_ERROR_QUERY}) {
                PermissionFactory.chmod_str(broker, transaction, uri, Optional.of(modeStr), Optional.empty());
            }

            transaction.commit();
        }
    }

    private static DBBroker testUserBroker() throws EXistException, AuthenticationException {
        final BrokerPool pool = server.getBrokerPool();
        final Subject testUser = pool.getSecurityManager().authenticate(TEST_USER, TEST_USER);
        return pool.get(Optional.of(testUser));
    }

    private static void storeQuery(final DBBroker broker, final Txn transaction, final XmldbURI uri, final String module)
            throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
        try (final Collection collection = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
            broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(module.getBytes(UTF_8)),
                    MimeType.XQUERY_TYPE, collection);
        }
    }

    private static void createUser(final SecurityManager securityManager, final DBBroker broker, final String username)
            throws PermissionDeniedException, EXistException {
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
