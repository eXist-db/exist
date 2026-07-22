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
package org.exist.http;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.http.HttpStatus;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.ExistWebServer;
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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Executing a stored query over REST requires EXECUTE, not READ. A caller which may execute but not
 * read a query gets its results, but learns only that it failed when it fails — the query text, the
 * message, the spec error code and the location all stay server-side.
 *
 * The same queries are served to a caller which may also read them, and must come back with the real
 * error, which is what proves the disclosure level is per-request rather than baked into the pooled
 * compiled query.
 */
public class RESTExecuteWithoutReadTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String TEST_USER = "restExecUser";

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("restExecuteWithoutRead");

    /** rwx--x--x — runnable by anybody, readable by nobody but the owner */
    private static final XmldbURI EXEC_ONLY_VALID = TEST_COLLECTION.append("exec-only-valid.xq");
    private static final XmldbURI EXEC_ONLY_BROKEN = TEST_COLLECTION.append("exec-only-broken.xq");

    /** rwxr-xr-x — the usual mode */
    private static final XmldbURI READABLE_VALID = TEST_COLLECTION.append("readable-valid.xq");
    private static final XmldbURI READABLE_BROKEN = TEST_COLLECTION.append("readable-broken.xq");

    /** rw-r--r-- — the default mode of a stored resource: readable, but NOT executable */
    private static final XmldbURI NOT_EXECUTABLE = TEST_COLLECTION.append("not-executable.xq");

    /** compiles and runs, then fails when the result is serialized (a function item is not serializable) */
    private static final XmldbURI EXEC_ONLY_SERIALIZE_FAIL = TEST_COLLECTION.append("exec-only-serialize-fail.xq");
    private static final XmldbURI READABLE_SERIALIZE_FAIL = TEST_COLLECTION.append("readable-serialize-fail.xq");

    /** an XQuery library module, execute-only for "other" (rwx--x--x) — NOT readable by the test user */
    private static final XmldbURI LIB_MODULE = TEST_COLLECTION.append("lib.xqm");
    /** a main query that imports {@link #LIB_MODULE}, likewise execute-only for the test user */
    private static final XmldbURI IMPORTS_LIB = TEST_COLLECTION.append("imports-lib.xq");

    private static final String VALID_QUERY = "xquery version \"3.1\";\n<result>{ sum(1 to 3) }</result>";
    private static final String BROKEN_QUERY = "xquery version \"3.1\";\nlet $secret := 1\nreturn $secret +";
    // returns a function item; execute() succeeds but serialization of the result fails with SENR0001,
    // which is the path that escaped ErrorDisclosure before the fix
    private static final String SERIALIZE_FAIL_QUERY = "xquery version \"3.1\";\nlet $secret := 1\nreturn function() { $secret }";

    private static final String LIB_MODULE_SRC =
            "xquery version \"3.1\";\n" +
            "module namespace lib = \"http://exist-db.org/test/execwithoutread/lib\";\n" +
            "declare function lib:answer() as xs:integer { 42 };";
    private static final String IMPORTS_LIB_SRC =
            "xquery version \"3.1\";\n" +
            "import module namespace lib = \"http://exist-db.org/test/execwithoutread/lib\" at \"lib.xqm\";\n" +
            "<result>{ lib:answer() }</result>";

    private static String credentials;

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, TriggerException, SyntaxException {
        credentials = Base64.encodeBase64String((TEST_USER + ":" + TEST_USER).getBytes(UTF_8));

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final SecurityManager securityManager = pool.getSecurityManager();

        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createUser(securityManager, broker, TEST_USER);

            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
            }

            storeQuery(broker, transaction, EXEC_ONLY_VALID, VALID_QUERY, "rwx--x--x");
            storeQuery(broker, transaction, EXEC_ONLY_BROKEN, BROKEN_QUERY, "rwx--x--x");
            storeQuery(broker, transaction, READABLE_VALID, VALID_QUERY, "rwxr-xr-x");
            storeQuery(broker, transaction, READABLE_BROKEN, BROKEN_QUERY, "rwxr-xr-x");
            storeQuery(broker, transaction, NOT_EXECUTABLE, VALID_QUERY, "rw-r--r--");
            storeQuery(broker, transaction, EXEC_ONLY_SERIALIZE_FAIL, SERIALIZE_FAIL_QUERY, "rwx--x--x");
            storeQuery(broker, transaction, READABLE_SERIALIZE_FAIL, SERIALIZE_FAIL_QUERY, "rwxr-xr-x");

            storeQuery(broker, transaction, LIB_MODULE, LIB_MODULE_SRC, "rwx--x--x");
            storeQuery(broker, transaction, IMPORTS_LIB, IMPORTS_LIB_SRC, "rwx--x--x");

            transaction.commit();
        }
    }

    @Test
    public void executeOnlyQueryRuns() throws IOException {
        final Response response = get(EXEC_ONLY_VALID, null);

        assertEquals(HttpStatus.OK_200, response.status);
        assertTrue("the query the caller cannot read still returns its results: " + response.body,
                response.body.contains("<result>6</result>"));
    }

    @Test
    public void executeOnlyQueryFailsGenerically() throws IOException {
        final Response response = get(EXEC_ONLY_BROKEN, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.status);
        assertTrue("the caller learns only that it failed: " + response.body,
                response.body.contains("Query execution failed"));
        assertTrue("and is given a correlation id to quote: " + response.body,
                response.body.matches("(?s).*\\(ref [0-9a-f-]+\\).*"));

        assertNoLeak(response.body);
    }

    @Test
    public void readableQueryFailsWithTheRealError() throws IOException {
        final Response response = get(READABLE_BROKEN, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.status);
        assertTrue("a caller which may read the query sees the real error: " + response.body,
                response.body.contains("XPST0003"));
        assertFalse("and is not fobbed off with the generic error: " + response.body,
                response.body.contains("Query execution failed"));
    }

    /**
     * A failure that surfaces while the result is being serialized — not during execute() — must be
     * sanitized just the same. Before the fix this escaped the disclosure filter, because it is
     * raised outside the narrow try that caught XPathException from execute().
     */
    @Test
    public void executeOnlySerializationFailureIsGeneric() throws IOException {
        final Response response = get(EXEC_ONLY_SERIALIZE_FAIL, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.status);
        assertTrue("a serialization failure of an unreadable query must also be generic: " + response.body,
                response.body.contains("Query execution failed"));
        assertFalse("the serialization error code must not leak: " + response.body,
                response.body.contains("SENR0001"));
        assertNoLeak(response.body);
    }

    @Test
    public void readableSerializationFailureShowsTheRealError() throws IOException {
        final Response response = get(READABLE_SERIALIZE_FAIL, null);

        // a readable caller keeps the original failure's status and detail — here a serialization
        // BadRequestException, which the servlet maps to 400. Only the read-blind path is normalized
        // to a uniform 500 + generic message (executeOnlySerializationFailureIsGeneric)
        assertTrue("a readable caller gets a real error status, not 200: " + response.status,
                response.status >= 400);
        assertFalse("a caller which may read the query is not fobbed off with the generic error: " + response.body,
                response.body.contains("Query execution failed"));
    }

    /**
     * X-XQuery-Cached reveals whether another user recently ran the shared query, so it must not be
     * sent to a read-blind caller (plan §4.6). A read-capable caller still gets it.
     */
    @Test
    public void theCacheHeaderIsSuppressedForAReadBlindCaller() throws IOException {
        final Response readBlind = get(EXEC_ONLY_VALID, null);
        assertEquals(HttpStatus.OK_200, readBlind.status);
        assertFalse("the shared-pool activity oracle must not reach a read-blind caller",
                readBlind.headers.containsKey("X-XQuery-Cached"));

        final Response readable = get(READABLE_VALID, null);
        assertEquals(HttpStatus.OK_200, readable.status);
        assertTrue("a read-capable caller still gets the cache header",
                readable.headers.containsKey("X-XQuery-Cached"));
    }

    /**
     * The pooled compiled query is shared between users, so the disclosure level must be recomputed on
     * every request. Running the readable copy first primes the pool with a FULL context; the
     * execute-only copy must still come back generic, and vice versa.
     */
    @Test
    public void theDisclosureLevelIsNotCachedWithTheCompiledQuery() throws IOException {
        final Response readable = get(READABLE_BROKEN, null);
        assertTrue(readable.body.contains("XPST0003"));

        final Response readBlind = get(EXEC_ONLY_BROKEN, null);
        assertTrue("a read-blind caller must not inherit the verbosity of a read-capable one: " + readBlind.body,
                readBlind.body.contains("Query execution failed"));
        assertNoLeak(readBlind.body);

        final Response readableAgain = get(READABLE_BROKEN, null);
        assertTrue("nor must a read-capable caller be starved of detail: " + readableAgain.body,
                readableAgain.body.contains("XPST0003"));
    }

    /**
     * Relaxing the execution gate must not open a way to fetch the source as data.
     */
    @Test
    public void theSourceOfAnExecuteOnlyQueryCannotBeViewed() throws IOException {
        final Response response = get(EXEC_ONLY_VALID, "_source=yes");

        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
        assertFalse("the source must not leak through the ?_source view: " + response.body,
                response.body.contains("sum(1 to 3)"));
    }

    /**
     * A query stored with the default resource mode (0666 → rw-r--r-- here) has no execute bit, so it
     * cannot be run — being able to read it is not enough.
     */
    @Test
    public void aReadableButNonExecutableQueryIsDenied() throws IOException {
        final Response response = get(NOT_EXECUTABLE, null);

        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
    }

    /**
     * A read-blind caller cannot run a query that {@code import module ... at}'s a module it cannot
     * READ — and this holds whether or not the shared compiled-query pool is already warm.
     *
     * One might expect a warm pool to help: another user compiles the query, the module is linked into
     * the pooled {@link org.exist.xquery.CompiledXQuery}, and the read-blind caller borrows it under
     * EXECUTE without re-reading the module. It does not. {@link XQueryPool#borrowCompiledXQuery}
     * revalidates the pooled query through {@link DBSource#isValid()}, which re-opens EACH imported
     * module with a {@code getXMLResource(READ_LOCK)} under the CALLING subject. The read-blind caller
     * is denied there, the entry is judged invalid and evicted, and the query is recompiled — which
     * resolves the module on READ ({@link org.exist.source.SourceFactory#getSource}) and is denied
     * again. So the outcome is deterministic, not cache-warmth-dependent.
     */
    @Test
    public void aReadBlindCallerCannotRunAQueryImportingAnUnreadableModule() throws Exception {
        final XQueryPool xqPool = existEmbeddedServer.getBrokerPool().getXQueryPool();

        // COLD: nothing pooled -> recompile -> module resolved on READ -> denied -> generic failure.
        xqPool.clear();
        final Response cold = get(IMPORTS_LIB, null);
        assertEquals("cold: recompile resolves the module on READ, denied: " + cold.body,
                HttpStatus.INTERNAL_SERVER_ERROR_500, cold.status);
        assertTrue("cold failure is generic for a read-blind caller: " + cold.body,
                cold.body.contains("Query execution failed"));
        assertFalse("no result when the import failed: " + cold.body, cold.body.contains("42"));

        // WARM: the system subject has already compiled and pooled the query (module linked in), yet
        // the read-blind caller STILL fails identically — the pool's validity check re-reads the module
        // under the caller's READ. This is the asymmetry hypothesis being empirically refuted.
        xqPool.clear();
        primeQueryPool(IMPORTS_LIB);
        final Response warm = get(IMPORTS_LIB, null);
        assertEquals("warm: pool validity re-reads the module under the caller's READ, still denied: " + warm.body,
                HttpStatus.INTERNAL_SERVER_ERROR_500, warm.status);
        assertTrue("warm failure is generic, exactly as cold: " + warm.body,
                warm.body.contains("Query execution failed"));
        assertFalse("a pooled module-importing query is not reusable by a read-blind caller: " + warm.body,
                warm.body.contains("42"));
    }

    /**
     * Compiles a stored query as the system subject and returns it to the shared pool, standing in for
     * any read-capable principal having run it first. Keyed by {@link DBSource} (document URI), so the
     * REST execution path finds this entry for the same resource.
     */
    private static void primeQueryPool(final XmldbURI uri) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final LockedDocument locked = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
            final BinaryDocument bin = (BinaryDocument) locked.getDocument();
            final Source source = new DBSource(pool, bin, true);
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(bin.getCollection().getURI()).toString());
            final CompiledXQuery compiled = xquery.compile(context, source);
            pool.getXQueryPool().returnCompiledXQuery(source, compiled);
        }
    }

    private static void assertNoLeak(final String body) {
        assertFalse("the source must not leak: " + body, body.contains("$secret"));
        assertFalse("the spec error code must not leak: " + body, body.contains("XPST0003"));
        assertFalse("the location must not leak: " + body, body.contains("at line"));
    }

    private static Response get(final XmldbURI uri, final String queryString) throws IOException {
        final String path = "http://localhost:" + existWebServer.getPort() + "/rest" + uri
                + (queryString == null ? "" : "?" + queryString);

        final HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
        try {
            connection.setRequestProperty("Authorization", "Basic " + credentials);
            connection.setRequestMethod("GET");
            connection.connect();

            final int status = connection.getResponseCode();
            final InputStream is = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            final String body = is == null ? "" : new String(is.readAllBytes(), UTF_8);

            return new Response(status, body, connection.getHeaderFields());
        } finally {
            connection.disconnect();
        }
    }

    private record Response(int status, String body, Map<String, List<String>> headers) {
    }

    private static void storeQuery(final DBBroker broker, final Txn transaction, final XmldbURI uri, final String query, final String modeStr)
            throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, SyntaxException {
        try (final Collection collection = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
            broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(query.getBytes(UTF_8)),
                    MimeType.XQUERY_TYPE, collection);
        }
        PermissionFactory.chmod_str(broker, transaction, uri, Optional.of(modeStr), Optional.empty());
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
