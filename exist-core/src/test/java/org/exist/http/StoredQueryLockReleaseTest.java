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
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockTable;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.ExistWebServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The document READ_LOCK taken on a stored XQuery to resolve and compile it must be released at
 * end-of-compile — never held through execution and result serialization. An executor which parks
 * on further collection/document locks while holding the query document's shared lock is one
 * required edge of the save-while-running deadlock family, and it also means a concurrent store of
 * that query blocks until the execution finishes.
 *
 * Each test runs a stored query which signals that its execution phase has begun (by storing a
 * marker document) and then polls for a release document; while it is parked, the test asserts
 * that no lock is still held on the query document, stores a new version of the very query being
 * executed — which must complete immediately — and only then releases the executor, which finishes
 * on the old source.
 *
 * See https://github.com/eXist-db/exist/issues/6593
 */
public class StoredQueryLockReleaseTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("storedQueryLockRelease");

    private static final XmldbURI REST_QUERY = TEST_COLLECTION.append("slow-rest.xq");
    private static final XmldbURI REST_STARTED = TEST_COLLECTION.append("started-rest.xml");
    private static final XmldbURI REST_RELEASE = TEST_COLLECTION.append("release-rest.xml");

    private static final XmldbURI RPC_QUERY = TEST_COLLECTION.append("slow-rpc.xq");
    private static final XmldbURI RPC_STARTED = TEST_COLLECTION.append("started-rpc.xml");
    private static final XmldbURI RPC_RELEASE = TEST_COLLECTION.append("release-rpc.xml");

    private static final String NEW_QUERY = "xquery version \"3.1\";\n\"new\"";

    private static ExecutorService executor;
    private static String credentials;

    /**
     * Stores a marker document to signal that its execution phase (and therefore its compilation)
     * has finished, then parks polling for a release document, so the test fully controls how long
     * the execution phase lasts. The poll is bounded so that a deadlocked run under a regressed
     * server fails instead of hanging forever.
     */
    private static String awaitQuery(final XmldbURI startedUri, final XmldbURI releaseUri) {
        return """
                xquery version "3.1";
                declare function local:await($tries as xs:integer) as xs:string {
                    if (doc-available('%s')) then 'old'
                    else if ($tries le 0) then 'timeout'
                    else (util:wait(25), local:await($tries - 1))
                };
                let $started := xmldb:store('%s', '%s', <started/>)
                return local:await(600)"""
                .formatted(releaseUri, startedUri.removeLastSegment(), startedUri.lastSegment());
    }

    @BeforeClass
    public static void setup() throws Exception {
        executor = Executors.newCachedThreadPool();
        credentials = Base64.encodeBase64String("admin:".getBytes(UTF_8));

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                broker.saveCollection(transaction, collection);
            }

            transaction.commit();
        }
    }

    @AfterClass
    public static void tearDown() {
        executor.shutdownNow();
    }

    @Test(timeout = 60_000)
    public void restSaveDuringExecutionCompletes() throws Exception {
        store(REST_QUERY, awaitQuery(REST_STARTED, REST_RELEASE), MimeType.XQUERY_TYPE);

        final Future<Response> running = executor.submit(() -> get(REST_QUERY));
        try {
            awaitDocumentExists(REST_STARTED);

            // compilation is over, execution has begun: the executor must no longer hold the
            // query document's READ_LOCK
            assertNoLockHeldOn(REST_QUERY);

            // the #5916 hang edge: storing a new version of the query being executed must
            // complete immediately, not block until the execution finishes
            final Future<?> save = executor.submit(() -> {
                store(REST_QUERY, NEW_QUERY, MimeType.XQUERY_TYPE);
                return null;
            });
            save.get(10, TimeUnit.SECONDS);
            assertFalse("the save must not have waited for the execution to finish", running.isDone());
        } finally {
            // whatever happened above, un-park the executing query
            store(REST_RELEASE, "<release/>", MimeType.XML_TYPE);
        }

        final Response response = running.get(30, TimeUnit.SECONDS);
        assertEquals(200, response.status);
        assertTrue("the in-flight execution finishes on the old source: " + response.body,
                response.body.contains("old"));

        final Response after = get(REST_QUERY);
        assertEquals(200, after.status);
        assertTrue("the next execution runs the newly stored version: " + after.body,
                after.body.contains("new"));
    }

    @Test(timeout = 60_000)
    public void xmlRpcSaveDuringExecutionCompletes() throws Exception {
        store(RPC_QUERY, awaitQuery(RPC_STARTED, RPC_RELEASE), MimeType.XQUERY_TYPE);

        final Future<Map<String, Object>> running = executor.submit(() -> executeStoredQuery(RPC_QUERY));
        try {
            awaitDocumentExists(RPC_STARTED);

            assertNoLockHeldOn(RPC_QUERY);

            final Future<?> save = executor.submit(() -> {
                store(RPC_QUERY, NEW_QUERY, MimeType.XQUERY_TYPE);
                return null;
            });
            save.get(10, TimeUnit.SECONDS);
            assertFalse("the save must not have waited for the execution to finish", running.isDone());
        } finally {
            store(RPC_RELEASE, "<release/>", MimeType.XML_TYPE);
        }

        final Map<String, Object> response = running.get(30, TimeUnit.SECONDS);
        assertNull("the in-flight execution succeeds: " + response, response.get("error"));
        assertEquals("the in-flight execution finishes on the old source: " + response,
                "old", singleTypedResult(response));

        final Map<String, Object> after = executeStoredQuery(RPC_QUERY);
        assertEquals("the next execution runs the newly stored version: " + after,
                "new", singleTypedResult(after));
    }

    /**
     * Asserts that no thread holds a lock on {@code uri} right now. While the stored query is in
     * its execution phase the only candidate holder is the request thread executing it, so this
     * pins down the #6593 invariant: the query document's READ_LOCK is released at end-of-compile.
     */
    private static void assertNoLockHeldOn(final XmldbURI uri) {
        final LockTable lockTable = existEmbeddedServer.getBrokerPool().getLockManager().getLockTable();
        assertFalse("no lock on " + uri + " may outlive the compilation of the query",
                lockTable.getAcquired().containsKey(uri.toString()));
    }

    private static void awaitDocumentExists(final XmldbURI uri) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final long deadline = System.currentTimeMillis() + 30_000;
        while (true) {
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                 final LockedDocument locked = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
                if (locked != null) {
                    return;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("Timed out waiting for " + uri + " — the stored query never reached its execution phase");
            }
            Thread.sleep(10);
        }
    }

    private static void store(final XmldbURI uri, final String content, final MimeType mimeType) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection collection = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
                broker.storeDocument(transaction, uri.lastSegment(), new StringInputSource(content.getBytes(UTF_8)),
                        mimeType, collection);
            }
            transaction.commit();
        }
    }

    /**
     * Unpacks the value of the single item of an {@code executeT} response.
     */
    private static String singleTypedResult(final Map<String, Object> response) {
        final Object[] results = (Object[]) response.get("results");
        assertEquals("expected a single result item: " + response, 1, results.length);
        @SuppressWarnings("unchecked")
        final Map<String, String> item = (Map<String, String>) results[0];
        return item.get("value");
    }

    private static Map<String, Object> executeStoredQuery(final XmldbURI uri) throws Exception {
        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL("http://localhost:" + existWebServer.getPort() + "/xmlrpc"));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) client.execute("executeT",
                Arrays.asList(uri.toString(), new HashMap<String, Object>()));
        return result;
    }

    private static Response get(final XmldbURI uri) throws Exception {
        final String path = "http://localhost:" + existWebServer.getPort() + "/rest" + uri;

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
}
