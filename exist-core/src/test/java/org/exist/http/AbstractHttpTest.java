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

import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractHttpTest {

    /**
     * HTTP status and body from a single fluent request execution.
     */
    public record HttpResponseResult(int statusCode, String body) {
    }

    /**
     * Get the Server URI.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the Server URI.
     */
    protected static String getServerUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort() + "/exist";
    }

    /**
     * Get the URI of the Server's REST end-point.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the URI of the Server's REST end-point.
     */
    protected static String getRestUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/rest";
    }

    /**
     * Get the URI of the Server's Apps end-point.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the URI of the Server's Apps end-point.
     */
    protected static String getAppsUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/apps";
    }

    /**
     * Create an {@link HttpHost} for the given eXist-db Web Server.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the HTTP host.
     */
    public static HttpHost getHttpHost(final ExistWebServer existWebServer) {
        return new HttpHost("http", "localhost", existWebServer.getPort());
    }

    private static String basicAuthorizationHeader(final String user, final String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create an HTTP client that sends preemptive HTTP Basic authentication.
     *
     * <p>HC5's fluent {@link Executor} auth helpers do not always attach credentials to requests
     * under the {@code /exist/...} context path; the request interceptor ensures the
     * {@code Authorization} header is present on the first request.</p>
     *
     * @param existWebServer the eXist-db Web Server.
     * @param user the user name.
     * @param password the password.
     *
     * @return a closable HTTP client.
     */
    public static CloseableHttpClient createAuthenticatedClient(
            final ExistWebServer existWebServer,
            final String user,
            final String password) {
        final String authorizationHeader = basicAuthorizationHeader(user, password);

        return HttpClients.custom()
                .addRequestInterceptorFirst((request, entity, context) -> {
                    if (!request.containsHeader(HttpHeaders.AUTHORIZATION)) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
                    }
                })
                .disableAutomaticRetries()
                .build();
    }

    /**
     * Create an HTTP executor that sends preemptive HTTP Basic authentication.
     *
     * @param existWebServer the eXist-db Web Server.
     * @param user the user name.
     * @param password the password.
     *
     * @return an executor backed by {@link #createAuthenticatedClient(ExistWebServer, String, String)}.
     */
    public static Executor createAuthenticatedExecutor(
            final ExistWebServer existWebServer,
            final String user,
            final String password) {
        return Executor.newInstance(createAuthenticatedClient(existWebServer, user, password));
    }

    /**
     * Execute a function with a HTTP Client.
     *
     * @param <T> the return type of the <code>fn</code> function.
     * @param fn the function which accepts the HTTP Client.
     *
     * @return the result of the <code>fn</code> function.
     *
     * @throws IOException if an I/O error occurs
     */
    protected static <T> T withHttpClient(final FunctionE<CloseableHttpClient, T, IOException> fn) throws IOException {
        try (final CloseableHttpClient client = HttpClients.custom()
                .disableAutomaticRetries()
                .build()) {
            return fn.apply(client);
        }
    }

    /**
     * Execute a function with a HTTP Executor.
     *
     * @param <T> the return type of the <code>fn</code> function.
     * @param existWebServer the eXist-db Web Server.
     * @param fn the function which accepts the HTTP Executor.
     *
     * @return the result of the <code>fn</code> function.
     *
     * @throws IOException if an I/O error occurs
     */
    protected static <T> T withHttpExecutor(final ExistWebServer existWebServer, final FunctionE<Executor, T, IOException> fn) throws IOException {
        try (final CloseableHttpClient client = createAuthenticatedClient(
                existWebServer, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)) {
            return fn.apply(Executor.newInstance(client));
        }
    }

    /**
     * Execute a request and return its status code, closing the response.
     */
    protected static int executeForStatus(final Executor executor, final Request request) throws IOException {
        try (ClassicHttpResponse response = (ClassicHttpResponse) executor.execute(request).returnResponse()) {
            return response.getCode();
        }
    }

    /**
     * Execute a request and return its status code, closing the response.
     */
    protected static int executeForStatus(final Request request) throws IOException {
        try (ClassicHttpResponse response = (ClassicHttpResponse) request.execute().returnResponse()) {
            return response.getCode();
        }
    }

    /**
     * Execute a request and return status code and body, closing the response.
     */
    public static HttpResponseResult executeForStatusAndBody(final Executor executor, final Request request)
            throws IOException {
        try (ClassicHttpResponse response = (ClassicHttpResponse) executor.execute(request).returnResponse()) {
            return new HttpResponseResult(response.getCode(), readResponseBody(response));
        }
    }

    /**
     * Execute a request and return status code and body, closing the response.
     */
    public static HttpResponseResult executeForStatusAndBody(final Request request) throws IOException {
        try (ClassicHttpResponse response = (ClassicHttpResponse) request.execute().returnResponse()) {
            return new HttpResponseResult(response.getCode(), readResponseBody(response));
        }
    }

    protected static String readResponseBody(final ClassicHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return "";
        }
        try (UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            response.getEntity().writeTo(baos);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Execute a fluent request and assert status and body, closing the response.
     */
    public static void assertRequestResponse(
            final Request request,
            final int expectedStatus,
            final String expectedBody) throws IOException {
        final HttpResponseResult result = executeForStatusAndBody(request);
        assertEquals(expectedStatus, result.statusCode());
        assertEquals(expectedBody, result.body());
    }
}
