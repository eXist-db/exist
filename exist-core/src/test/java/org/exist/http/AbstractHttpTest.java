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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.exist.test.ExistWebServer;

import static org.junit.Assert.assertEquals;

/**
 * Shared HTTP test infrastructure, built on the JDK {@link java.net.http.HttpClient} (no Apache
 * HttpClient). Provides the eXist-db server URIs, preemptive HTTP Basic authentication, and small
 * helpers for executing a request and reading the status / body.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractHttpTest {

    /**
     * HTTP status and body from a single request execution.
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
     * Build a preemptive HTTP Basic {@code Authorization} header value.
     *
     * @param user the user name.
     * @param password the password.
     *
     * @return the {@code Authorization} header value, e.g. {@code "Basic dXNlcjpwYXNz"}.
     */
    public static String basicAuthorizationHeader(final String user, final String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create an HTTP client that follows redirects (matching the previous fluent client's default).
     *
     * @return a new {@link HttpClient}.
     */
    public static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Start building a request to the given URI with a preemptive HTTP Basic {@code Authorization}
     * header already set.
     *
     * @param uri the request URI.
     * @param user the user name.
     * @param password the password.
     *
     * @return a request builder carrying the {@code Authorization} header.
     */
    public static HttpRequest.Builder authenticatedRequest(final URI uri, final String user, final String password) {
        return HttpRequest.newBuilder(uri)
                .header("Authorization", basicAuthorizationHeader(user, password));
    }

    /**
     * Execute a request and return its status code.
     *
     * @param client the HTTP client.
     * @param request the request.
     *
     * @return the HTTP status code.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static int executeForStatus(final HttpClient client, final HttpRequest request) throws IOException {
        return send(client, request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * Execute a request and return its status code and body.
     *
     * @param client the HTTP client.
     * @param request the request.
     *
     * @return the status code and body.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static HttpResponseResult executeForStatusAndBody(final HttpClient client, final HttpRequest request)
            throws IOException {
        final HttpResponse<String> response = send(client, request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResponseResult(response.statusCode(), response.body());
    }

    /**
     * Execute a request and assert its status and body.
     *
     * @param client the HTTP client.
     * @param request the request.
     * @param expectedStatus the expected HTTP status code.
     * @param expectedBody the expected response body.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static void assertRequestResponse(final HttpClient client, final HttpRequest request,
            final int expectedStatus, final String expectedBody) throws IOException {
        final HttpResponseResult result = executeForStatusAndBody(client, request);
        assertEquals(expectedStatus, result.statusCode());
        assertEquals(expectedBody, result.body());
    }

    /**
     * Execute a function with a new HTTP Client.
     *
     * @param <T> the return type of the <code>fn</code> function.
     * @param fn the function which accepts the HTTP Client.
     *
     * @return the result of the <code>fn</code> function.
     *
     * @throws IOException if an I/O error occurs.
     */
    protected static <T> T withHttpClient(final FunctionE<HttpClient, T, IOException> fn) throws IOException {
        return fn.apply(newHttpClient());
    }

    /**
     * Send a request, translating the checked {@link InterruptedException} thrown by
     * {@link HttpClient#send} into an {@link IOException} so callers keep a single checked type.
     */
    private static <T> HttpResponse<T> send(final HttpClient client, final HttpRequest request,
            final HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
        try {
            return client.send(request, bodyHandler);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting HTTP response", e);
        }
    }
}
