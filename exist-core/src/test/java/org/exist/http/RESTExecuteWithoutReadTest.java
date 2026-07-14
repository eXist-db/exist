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
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
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

    private static final String VALID_QUERY = "xquery version \"3.1\";\n<result>{ sum(1 to 3) }</result>";
    private static final String BROKEN_QUERY = "xquery version \"3.1\";\nlet $secret := 1\nreturn $secret +";

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

            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private record Response(int status, String body) {
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
