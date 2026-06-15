/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import org.exist.TestUtils;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.exist.util.ExistSAXParserFactory;
import org.exquery.restxq.Namespace;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractIntegrationTest {

    private static String COLLECTION_CONFIG =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <triggers>
                    <trigger class="org.exist.extensions.exquery.restxq.impl.RestXqTrigger"/>
                </triggers>
            </collection>""";

    private static String COLLECTION_CONFIG_CONTENT_TYPE = "application/xml; charset=utf-8";

    private static String XQUERY_CONTENT_TYPE = "application/xquery; charset=utf-8";

    /**
     * Standalone test webapp is mounted at {@code /} (see {@code exist.jetty.standalone.webapp.dir}),
     * not at {@code /exist} like {@link AbstractHttpTest#getServerUri(ExistWebServer)} in exist-core tests.
     */
    protected static String getServerUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort();
    }

    protected static String getRestUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/rest";
    }

    protected static String getRestXqUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/restxq";
    }

    /**
     * Build a request to the given URI with a preemptive HTTP Basic {@code Authorization} header for
     * the eXist-db admin user.
     */
    protected static HttpRequest.Builder authenticatedAdminRequest(final String uri) {
        return AbstractHttpTest.authenticatedRequest(URI.create(uri), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
    }

    protected static void enableRestXqTrigger(final ExistWebServer existWebServer, final HttpClient httpClient, final String collectionPath) throws IOException {
        final HttpRequest request = authenticatedAdminRequest(getRestUri(existWebServer) + "/db/system/config" + collectionPath + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .header("Content-Type", COLLECTION_CONFIG_CONTENT_TYPE)
                .PUT(HttpRequest.BodyPublishers.ofString(COLLECTION_CONFIG, UTF_8))
                .build();
        assertEquals(HTTP_CREATED, AbstractHttpTest.executeForStatus(httpClient, request));
    }

    protected static void storeXquery(final ExistWebServer existWebServer, final HttpClient httpClient, final String collectionPath, final String xqueryFilename, final String xquery) throws IOException {
        final HttpRequest request = authenticatedAdminRequest(getRestUri(existWebServer) + collectionPath + "/" + xqueryFilename)
                .header("Content-Type", XQUERY_CONTENT_TYPE)
                .PUT(HttpRequest.BodyPublishers.ofString(xquery, UTF_8))
                .build();
        assertEquals(HTTP_CREATED, AbstractHttpTest.executeForStatus(httpClient, request));
    }

    protected static void removeXquery(final ExistWebServer existWebServer, final HttpClient httpClient, final String collectionPath, final String xqueryFilename) throws IOException {
        final HttpRequest request = authenticatedAdminRequest(getRestUri(existWebServer) + collectionPath + "/" + xqueryFilename)
                .DELETE()
                .build();
        assertEquals(HTTP_OK, AbstractHttpTest.executeForStatus(httpClient, request));
    }

    protected static void assertRestXqResourceFunctionsCount(final ExistWebServer existWebServer, final HttpClient httpClient, final int expectedCount) throws IOException {
        assertEquals(expectedCount, getRestXqResourceFunctions(existWebServer, httpClient).getLength());
    }

    protected static NodeList getRestXqResourceFunctions(final ExistWebServer existWebServer, final HttpClient httpClient) throws IOException {
        final HttpRequest request = authenticatedAdminRequest(getRestUri(existWebServer) + "/db/?_query=rest:resource-functions()")
                .GET()
                .build();
        final HttpResponse<InputStream> response = send(httpClient, request);
        assertEquals(HTTP_OK, response.statusCode());

        final Document doc;
        try (final InputStream is = response.body()) {
            assertNotNull(is);
            doc = parseXml(is);
        }
        assertNotNull(doc);

        final Element docElem = doc.getDocumentElement();
        assertEquals("exist:result", docElem.getNodeName());
        final NodeList resourceFunctionsList = docElem.getElementsByTagNameNS(Namespace.ANNOTATION_NS, "resource-functions");
        assertEquals(1, resourceFunctionsList.getLength());

        final Element resourceFunctionsElem = (Element) resourceFunctionsList.item(0);
        return resourceFunctionsElem.getElementsByTagNameNS(Namespace.ANNOTATION_NS, "resource-function");
    }

    /**
     * Send a request reading the body as an {@link InputStream}, translating the checked
     * {@link InterruptedException} thrown by {@link HttpClient#send} into an {@link IOException}.
     */
    private static HttpResponse<InputStream> send(final HttpClient httpClient, final HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting HTTP response", e);
        }
    }

    protected static Document parseXml(final InputStream inputStream) throws IOException {
        final SAXParserFactory saxParserFactory = ExistSAXParserFactory.getSAXParserFactory();
        saxParserFactory.setNamespaceAware(true);
        try {
            final SAXParser saxParser = saxParserFactory.newSAXParser();
            final XMLReader reader = saxParser.getXMLReader();
            final SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(new InputSource(inputStream));
            return adapter.getDocument();
        } catch (final SAXException | ParserConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    protected static String asString(final InputStream inputStream) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (final Reader reader = new InputStreamReader(inputStream, UTF_8)) {
            final char cbuf[] = new char[4096];
            int read = -1;
            while((read = reader.read(cbuf)) > -1) {
                builder.append(cbuf, 0, read);
            }
        }
        return builder.toString();
    }
}
