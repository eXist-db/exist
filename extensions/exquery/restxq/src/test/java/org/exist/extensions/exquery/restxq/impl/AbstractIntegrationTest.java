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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.memtree.SAXAdapter;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractIntegrationTest {

    private static String COLLECTION_CONFIG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
                    "    <triggers>\n" +
                    "        <trigger class=\"org.exist.extensions.exquery.restxq.impl.RestXqTrigger\"/>\n" +
                    "    </triggers>\n" +
                    "</collection>";

    private static ContentType XQUERY_CONTENT_TYPE = ContentType.create("application/xquery", UTF_8);

    protected static String getServerUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort();
    }

    protected static String getRestUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/rest";
    }

    protected static String getRestXqUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/restxq";
    }

    protected static void enableRestXqTrigger(final ExistWebServer existWebServer, final Executor executor, final String collectionPath) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Put(getRestUri(existWebServer) + "/db/system/config" + collectionPath + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML.withCharset(UTF_8))
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
    }

    protected static void storeXquery(final ExistWebServer existWebServer, final Executor executor, final String collectionPath, final String xqueryFilename, final String xquery) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Put(getRestUri(existWebServer) + collectionPath + "/" + xqueryFilename)
                .bodyString(xquery, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
    }

    protected static void removeXquery(final ExistWebServer existWebServer, final Executor executor, final String collectionPath, final String xqueryFilename) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Delete(getRestUri(existWebServer) + collectionPath + "/" + xqueryFilename)
        ).returnResponse();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    protected static void assertRestXqResourceFunctionsCount(final ExistWebServer existWebServer, final Executor executor, final int expectedCount) throws IOException {
        assertEquals(expectedCount, getRestXqResourceFunctions(existWebServer, executor).getLength());
    }

    protected static NodeList getRestXqResourceFunctions(final ExistWebServer existWebServer, final Executor executor) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Get(getRestUri(existWebServer) + "/db/?_query=rest:resource-functions()")
        ).returnResponse();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        final Document doc;
        try (final InputStream is = response.getEntity().getContent()) {
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
