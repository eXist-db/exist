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
package org.exist.http.restxq;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.exist.TestUtils;
import org.exist.collections.CollectionConfiguration;
import org.exist.test.ExistWebServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * Base class for RESTXQ integration tests.
 *
 * <p>Provides infrastructure for registering XQuery RESTXQ modules in the database,
 * making HTTP requests against them, and asserting on responses. Adapted from
 * BaseX's RestXqTest pattern and eXist's existing AbstractIntegrationTest.</p>
 *
 * <p>Each test class should extend this. Uses a shared {@link ExistWebServer} via
 * {@code @ClassRule}. Tests register XQuery functions via {@link #register(String)},
 * then call {@link #get(String, String, String)}, {@link #get(int, String, String)},
 * or {@link #post(String, String, String, String, String)} to verify behavior.</p>
 */
public abstract class RestXqTestBase {

    @ClassRule
    public static final ExistWebServer existWebServer =
            new ExistWebServer(true, false, true, true);

    protected static Executor executor;

    private static final String TEST_COLLECTION = "/db/restxq-test";

    private static final String COLLECTION_CONFIG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
            "    <triggers>\n" +
            "        <trigger class=\"org.exist.extensions.exquery.restxq.impl.RestXqTrigger\"/>\n" +
            "    </triggers>\n" +
            "</collection>";

    private static final ContentType XQUERY_CONTENT_TYPE =
            ContentType.create("application/xquery", UTF_8);

    /**
     * XQuery module header. Declares the module namespace and RESTXQ imports.
     * Subclass tests should concatenate function declarations after this.
     */
    protected static final String HEADER =
            "xquery version '3.1';\n" +
            "module namespace m = 'http://exist-db.org/test/restxq';\n" +
            "declare namespace rest = 'http://exquery.org/ns/restxq';\n" +
            "declare namespace output = 'http://www.w3.org/2010/xslt-xquery-serialization';\n" +
            "declare namespace http = 'http://expath.org/ns/http-client';\n" +
            "declare namespace input = 'http://exquery.org/ns/restxq/input';\n" +
            "import module namespace web = 'http://basex.org/modules/web';\n";

    private static int moduleCounter = 0;

    @BeforeClass
    public static void setupBase() throws Exception {
        executor = Executor.newInstance()
                .auth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));
    }

    protected static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort();
    }

    protected static String getRestUri() {
        return getServerUri() + "/rest";
    }

    protected static String getRestXqUri() {
        return getServerUri() + "/restxq";
    }

    private static void enableRestXqTrigger() throws IOException {
        final HttpResponse response = executor.execute(Request
                .Put(getRestUri() + "/db/system/config" + TEST_COLLECTION + "/"
                        + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML.withCharset(UTF_8))
        ).returnResponse();
        final int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
            throw new IOException("Failed to enable RestXqTrigger, status: " + status);
        }
    }

    /**
     * Register a RESTXQ module by storing it in the database.
     * Each call creates a fresh module file (with incrementing name) and
     * removes any previous module to ensure clean state.
     *
     * @param function the RESTXQ function declaration(s), without module header
     */
    protected static void register(final String function) throws IOException {
        final String module = HEADER + function;
        final String filename = "test" + (moduleCounter++) + ".xqm";

        // Remove previous module if it existed
        if (moduleCounter > 1) {
            final String prevFilename = "test" + (moduleCounter - 2) + ".xqm";
            try {
                executor.execute(Request
                        .Delete(getRestUri() + TEST_COLLECTION + "/" + prevFilename)
                ).returnResponse();
            } catch (final Exception e) {
                // ignore - may not exist
            }
        }

        final HttpResponse response = executor.execute(Request
                .Put(getRestUri() + TEST_COLLECTION + "/" + filename)
                .bodyString(module, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        final int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
            final String body = asString(response.getEntity().getContent());
            throw new IOException("Failed to store module (status " + status + "): " + body);
        }

        // Invalidate the native RESTXQ cache so routes are re-scanned
        invalidateRouteCache();
    }

    private static void invalidateRouteCache() throws IOException {
        executor.execute(Request.Get(getRestXqUri() + "/.init")).returnResponse();
    }

    /**
     * Store a module directly without cleanup of previous modules.
     */
    protected static void storeModuleDirect(final String filename, final String content) throws Exception {
        final HttpResponse response = executor.execute(Request
                .Put(getRestUri() + TEST_COLLECTION + "/" + filename)
                .bodyString(content, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        final int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
            throw new IOException("Failed to store module " + filename + ", status: " + status);
        }
    }

    /**
     * Register a function, GET the path, and assert the response body matches expected.
     */
    protected static void get(final String expected, final String function, final String path) throws Exception {
        register(function);
        final String result = doGet(path);
        assertEquals(expected, result.trim());
    }

    /**
     * Register a function, GET the path, and assert the HTTP status code.
     */
    protected static void get(final int expectedStatus, final String function, final String path) throws Exception {
        register(function);
        final int status = doGetStatus(path);
        assertEquals(expectedStatus, status);
    }

    /**
     * Register a function, POST to the path, and assert the response body matches expected.
     */
    protected static void post(final String expected, final String function, final String path,
                               final String body, final String contentType) throws Exception {
        register(function);
        final String result = doPost(path, body, contentType);
        assertEquals(expected, result.trim());
    }

    /**
     * Register a function, POST to the path, and assert the HTTP status code.
     */
    protected static void post(final int expectedStatus, final String function, final String path,
                               final String body, final String contentType) throws Exception {
        register(function);
        final int status = doPostStatus(path, body, contentType);
        assertEquals(expectedStatus, status);
    }

    // === HTTP request helpers ===

    protected static String doGet(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Get(getRestXqUri() + "/" + path)
        ).returnResponse();
        return asString(response.getEntity().getContent());
    }

    protected static int doGetStatus(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Get(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static HttpResponse doGetWithAccept(final String path, final String accept) throws IOException {
        return executor.execute(Request
                .Get(getRestXqUri() + "/" + path)
                .addHeader("Accept", accept)
        ).returnResponse();
    }

    protected static String doPost(final String path, final String body, final String contentType) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Post(getRestXqUri() + "/" + path)
                .bodyString(body, ContentType.parse(contentType))
        ).returnResponse();
        return response.getEntity() != null ? asString(response.getEntity().getContent()) : "";
    }

    protected static int doPostStatus(final String path, final String body, final String contentType) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Post(getRestXqUri() + "/" + path)
                .bodyString(body, ContentType.parse(contentType))
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static int doPutStatus(final String path, final String body, final String contentType) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Put(getRestXqUri() + "/" + path)
                .bodyString(body, ContentType.parse(contentType))
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static int doDeleteStatus(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Delete(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static int doHeadStatus(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Head(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static String doOptions(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Options(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getEntity() != null ? asString(response.getEntity().getContent()) : "";
    }

    protected static int doOptionsStatus(final String path) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Options(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    protected static int doPatchStatus(final String path, final String body, final String contentType) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Patch(getRestXqUri() + "/" + path)
                .bodyString(body, ContentType.parse(contentType))
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    /**
     * Java's HttpURLConnection rejects non-standard method names like RETRIEVE.
     * Use java.net.http.HttpClient which supports arbitrary method names.
     */
    private static final HttpClient CUSTOM_HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static String authHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((TestUtils.ADMIN_DB_USER + ":" + TestUtils.ADMIN_DB_PWD).getBytes(UTF_8));
    }

    protected static int doCustomMethodStatus(final String method, final String path) throws IOException {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getRestXqUri() + "/" + path))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .header("Authorization", authHeader())
                    .build();
            final java.net.http.HttpResponse<String> response =
                    CUSTOM_HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during custom method request", e);
        }
    }

    protected static String doCustomMethodBody(final String method, final String path,
                                               final String body, final String contentType) throws IOException {
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getRestXqUri() + "/" + path))
                    .header("Authorization", authHeader());
            if (body != null && contentType != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                       .header("Content-Type", contentType);
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            final java.net.http.HttpResponse<String> response =
                    CUSTOM_HTTP.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during custom method request", e);
        }
    }

    protected static int doGetStatusNoAuth(final String path) throws IOException {
        final Executor noAuthExecutor = Executor.newInstance();
        final HttpResponse response = noAuthExecutor.execute(Request
                .Get(getRestXqUri() + "/" + path)
        ).returnResponse();
        return response.getStatusLine().getStatusCode();
    }

    /**
     * Read an InputStream to a String (UTF-8).
     */
    protected static String asString(final InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        try (final Reader reader = new InputStreamReader(inputStream, UTF_8)) {
            final char[] cbuf = new char[4096];
            int read;
            while ((read = reader.read(cbuf)) > -1) {
                builder.append(cbuf, 0, read);
            }
        }
        return builder.toString();
    }
}
