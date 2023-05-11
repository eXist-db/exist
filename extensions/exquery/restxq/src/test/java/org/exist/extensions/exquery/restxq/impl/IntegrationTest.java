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

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import static org.assertj.core.api.Assertions.assertThat;
import org.exist.TestUtils;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.test.ExistWebServer;
import org.exist.util.ExistSAXParserFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;

public class IntegrationTest {

    private static String COLLECTION_CONFIG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
            "    <triggers>\n" +
            "        <trigger class=\"org.exist.extensions.exquery.restxq.impl.RestXqTrigger\"/>\n" +
            "    </triggers>\n" +
            "</collection>";

    private static String TEST_COLLECTION = "/db/restxq/integration-test";

    private static ContentType XQUERY_CONTENT_TYPE = ContentType.create("application/xquery", "UTF-8");
    private static String XQUERY1 =
            "xquery version \"3.0\";\n" +
            "\n" +
            "module namespace mod1 = \"http://mod1\";\n" +
            "\n" +
            "declare namespace output = \"https://www.w3.org/2010/xslt-xquery-serialization\";\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/media-type-json1\")\n" +
            "    %output:media-type(\"application/json\")\n" +
            "    %output:method(\"json\")\n" +
            "function mod1:media-type-json1() {\n" +
            "    <success/>\n" +
            "};";
    private static String XQUERY1_FILENAME = "restxq-tests1.xqm";
    private static Executor executor = null;

    @ClassRule
    public static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort();
    }

    private static String getRestUri() {
        return getServerUri() + "/rest";
    }

    private static String getRestXqUri() {
        return getServerUri() + "/restxq";
    }

    @BeforeClass
    public static void storeResourceFunctions() throws IOException {
        executor = Executor.newInstance()
                .auth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));

        HttpResponse response = null;

        var restURI = getRestUri();
        response = executor.execute(Request
                .Put(getRestUri() + "/db/system/config" + TEST_COLLECTION + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML)
        ).returnResponse();
        //assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        response = executor.execute(Request
                .Put(getRestUri() + TEST_COLLECTION + "/" + XQUERY1_FILENAME)
                .bodyString(XQUERY1, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        response = executor.execute(Request
                .Get(getRestUri() + "/db/?_query=rest:resource-functions()")
        ).returnResponse();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertNotNull(response.getEntity().getContent());

        response = executor.execute(Request
            .Put(getRestUri() + TEST_COLLECTION + XQUERY_BASE_URI_FILENAME)
            .bodyString(XQUERY_BASE_URI, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    }

    @Ignore("TODO(AR) need to figure out how to access the RESTXQ API from {@link ExistWebServer}")
    @Test
    public void mediaTypeJson1() throws IOException {
        final HttpResponse response = executor.execute(Request
                .Get(getRestXqUri() + "/media-type-json1")
                .addHeader(new BasicHeader("Accept", "application/json"))
        ).returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("<success/>", response.getEntity().toString());
    }

    private static String XQUERY_BASE_URI_FILENAME = "restxq-tests-base-uri.xqm";

    private static final String XQUERY_BASE_URI = """
      xquery version "3.1";
      module namespace ex = "http://example/restxq/1";
      import module namespace rest = "http://exquery.org/ns/restxq";
      
      declare
          %rest:GET
          %rest:path("/restxq-tests-base-uri")
      function ex:base-uri-using-restxq() {
          <result>We should call base-uri for real!</result>
      };
      """;

    /**
     * Handler for this is installed in the Before section;
     * check that base-uri is called, and check the result.
     *
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    @Test
    public void baseURI() throws IOException, ParserConfigurationException, SAXException {

        final HttpResponse response = executor.execute(Request
            .Get(getRestXqUri() + "/restxq-tests-base-uri")
            .addHeader(new BasicHeader("Accept", "application/xml"))
        ).returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        final var result = parseResponseElement(response.getEntity().toString());

        assertThat(result).isEqualTo("42");
        /*
        final var result = parseResponseElement(httpGet(restXQ, new HashMap<>()));
        assertThat(result.getAttribute("non-existent")).isEqualTo("42");
         */
    }

    static private Element parseResponseElement(final String data) throws IOException, SAXException, ParserConfigurationException {

        final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        final InputSource src = new InputSource(new StringReader(data));
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();
        final SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);

        return adapter.getDocument().getDocumentElement();
    }

    private static String asString(final InputStream inputStream) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try(final Reader reader = new InputStreamReader(inputStream, UTF_8)) {
            final char cbuf[] = new char[4096];
            int read = -1;
            while((read = reader.read(cbuf)) > -1) {
                builder.append(cbuf, 0, read);
            }
        }
        return builder.toString();
    }

}
