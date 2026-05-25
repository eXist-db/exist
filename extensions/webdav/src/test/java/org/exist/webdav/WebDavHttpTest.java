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
package org.exist.webdav;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Minimal JDK {@link HttpClient} helper for WebDAV PUT/GET round-trip tests.
 * RFC compliance is covered by litmus ({@code exist-docker/.../04-webdav-litmus.bats}).
 */
final class WebDavHttpTest {

    private final HttpClient client;
    private final String collectionUri;
    private final String authorizationHeader;

    WebDavHttpTest(final int port, final String username, final String password) {
        this.collectionUri = "http://localhost:" + port + "/webdav/db/";
        this.authorizationHeader = basicAuthorization(username, password);
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    int putDocument(final String name, final String content, final String contentType)
            throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(documentUri(name))
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8))
                .header("Authorization", authorizationHeader)
                .header("Content-Type", contentType)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    HttpResponse<String> getDocument(final String name) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(documentUri(name))
                .GET()
                .header("Authorization", authorizationHeader)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    int deleteDocument(final String name) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(documentUri(name))
                .DELETE()
                .header("Authorization", authorizationHeader)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI documentUri(final String name) {
        return URI.create(collectionUri + name);
    }

    private static String basicAuthorization(final String username, final String password) {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
