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
package org.exist.xquery.functions.request;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.xmldb.UserManagementService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.exist.http.RESTTest;
import org.exist.xmldb.EXistResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class GetDataTest extends RESTTest {

    private final static String CONTAINER_ELEMENT_NAME = "data";
    private final static String XQUERY = wrapInElement("{request:get-data()}");
    private final static String XQUERY_FILENAME = "test-get-data.xql";

    private static Collection root;

    private static String wrapInElement(String value) {
        return value == null || value.isEmpty() ? "<" + CONTAINER_ELEMENT_NAME + "/>" : "<" + CONTAINER_ELEMENT_NAME + ">" + value + "</" + CONTAINER_ELEMENT_NAME + ">";
    }

    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        BinaryResource res = root.createResource(XQUERY_FILENAME, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
        UserManagementService ums = root.getService(UserManagementService.class);
        ums.chmod(res, 0777);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        BinaryResource res = (BinaryResource)root.getResource(XQUERY_FILENAME);
        root.removeResource(res);
    }

    @Test
    public void retrieveEmpty() throws IOException {
        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        testRequest(post, wrapInElement("").getBytes());
    }

    @Ignore("Jetty 12 rejects HTTP/0.9, which the JDK HttpClient cannot express")
    @Test
    public void retrieveBinaryHttp09() throws IOException {
        final String testData = "12345";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        assertEquals(HTTP_VERSION, executeForStatus(newHttpClient(), post));
    }

    @Ignore("Jetty 12 drops the connection on HTTP/1.0 without a response, which the JDK HttpClient cannot express")
    @Test
    public void retrieveBinaryHttp10() throws IOException {
        final String testData = "12345";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        testRequest(post, wrapInElement(encodeBase64String(testData.getBytes(UTF_8)).trim()).getBytes());
    }

    @Test
    public void retrieveBinaryHttp11() throws IOException {
        final String testData = "12345";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        testRequest(post, wrapInElement(encodeBase64String(testData.getBytes(UTF_8)).trim()).getBytes());
    }

    @Test
    public void retrieveBinaryHttp11ChunkedTransferEncoding() throws IOException {
        final String testData = "12345";

        try (final InputStream is = new UnsynchronizedByteArrayInputStream(testData.getBytes(UTF_8))) {
            final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> is))
                    .build();

            testRequest(post, wrapInElement(encodeBase64String(testData.getBytes(UTF_8)).trim()).getBytes());
        }
    }

    @Ignore("Jetty 12 rejects HTTP/0.9, which the JDK HttpClient cannot express")
    @Test
    public void retrieveXmlHttp09() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "text/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        assertEquals(HTTP_VERSION, executeForStatus(newHttpClient(), post));
    }

    @Ignore("Jetty 12 drops the connection on HTTP/1.0 without a response, which the JDK HttpClient cannot express")
    @Test
    public void retrieveXmlHttp10() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "text/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        testRequest(post, wrapInElement("\n\t" + testData + "\n").getBytes(), true);
    }

    @Test
    public void retrieveXmlHttp11() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "text/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        testRequest(post, wrapInElement("\n\t" + testData + "\n").getBytes(), true);
    }

    @Test
    public void retrieveXmlHttp11ChunkedTransferEncoding() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        try (final InputStream is = new UnsynchronizedByteArrayInputStream(testData.getBytes(UTF_8))) {
            final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "text/xml")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> is))
                    .build();

            testRequest(post, wrapInElement("\n\t" + testData + "\n").getBytes(), true);
        }
    }

    @Test
    public void retrieveMalformedXmlFallbackToString() throws IOException {
        final String testData = "<a><b></a>";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
            .header("Content-Type", "text/xml")
            .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
            .build();

        testRequest(post, wrapInElement(testData.replace("<", "&lt;").replace(">", "&gt;")).getBytes());
    }

    @Test
    public void retrieveString() throws IOException {
        final String testData = "12345";

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .POST(HttpRequest.BodyPublishers.ofByteArray(testData.getBytes(UTF_8)))
                .build();

        testRequest(post, wrapInElement(testData).getBytes());
    }

    private void testRequest(final HttpRequest method, final byte expectedResponse[]) throws IOException {
        testRequest(method, expectedResponse, false);
    }

    private void testRequest(final HttpRequest method, byte expectedResponse[], boolean stripWhitespaceAndFormatting) throws IOException {
        final HttpResponse<byte[]> response = send(newHttpClient(), method, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(HTTP_OK, response.statusCode());

        byte actualResponse[] = response.body();
        if(stripWhitespaceAndFormatting) {
            expectedResponse = new String(expectedResponse).replace("\n", "").replace("\t", "").replace(" ", "").getBytes(UTF_8);
            actualResponse = new String(actualResponse).replace("\n", "").replace("\t","").replace(" ", "").getBytes(UTF_8);
        }
        assertArrayEquals(expectedResponse, actualResponse);
    }

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
