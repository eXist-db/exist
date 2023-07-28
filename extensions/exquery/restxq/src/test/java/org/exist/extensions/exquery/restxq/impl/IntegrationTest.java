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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.exist.TestUtils;
import org.exist.collections.CollectionConfiguration;
import org.exist.test.ExistWebServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

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

    private static ContentType XQUERY_CONTENT_TYPE = ContentType.create("application/xquery", UTF_8);
    private static String XQUERY1 =
            "xquery version \"3.0\";\n" +
            "\n" +
            "module namespace mod1 = \"http://mod1\";\n" +
            "\n" +
            "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";\n" +
            "\n" +
            "declare %private variable $mod1:data := document { <person><firstName>Adam</firstName><lastName>Retter</lastName></person> } ;\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/media-type-json1\")\n" +
            "    %output:media-type(\"application/json\")\n" +
            "    %output:method(\"json\")\n" +
            "function mod1:media-type-json1() {\n" +
            "    $mod1:data\n" +
            "};\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/media-type-json2\")\n" +
            "    %output:media-type(\"application/json\")\n" +
            "    %output:method(\"json\")\n" +
            "function mod1:media-type-json2() {\n" +
            "    $mod1:data/person\n" +
            "};\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/media-type-xml1\")\n" +
            "    %output:media-type(\"application/xml\")\n" +
            "    %output:method(\"xml\")\n" +
            "    %output:indent(\"no\")\n" +
            "function mod1:media-type-xml1() {\n" +
            "    $mod1:data\n" +
            "};\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/media-type-xml2\")\n" +
            "    %output:media-type(\"application/xml\")\n" +
            "    %output:method(\"xml\")\n" +
            "    %output:indent(\"no\")\n" +
            "function mod1:media-type-xml2() {\n" +
            "    $mod1:data/person\n" +
            "};\n";
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

        response = executor.execute(Request
                .Put(getRestUri() + "/db/system/config" + TEST_COLLECTION + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML.withCharset(UTF_8))
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

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
    }

    @Test
    public void mediaTypeJson1() throws IOException {
        assertMediaTypeResponse("/media-type-json1", ContentType.APPLICATION_JSON,
                "application/json; charset=utf-8",
                "{ \"firstName\" : \"Adam\", \"lastName\" : \"Retter\" }");
    }

    @Test
    public void mediaTypeJson2() throws IOException {
        assertMediaTypeResponse("/media-type-json2", ContentType.APPLICATION_JSON,
                "application/json; charset=utf-8",
                "{ \"firstName\" : \"Adam\", \"lastName\" : \"Retter\" }");
    }

    @Test
    public void mediaTypeXml1() throws IOException {
        assertMediaTypeResponse("/media-type-xml1", ContentType.APPLICATION_XML.withCharset(UTF_8),
                ContentType.APPLICATION_XML.withCharset(UTF_8).toString(),
                "<person><firstName>Adam</firstName><lastName>Retter</lastName></person>");
    }

    @Test
    public void mediaTypeXml2() throws IOException {
        assertMediaTypeResponse("/media-type-xml2", ContentType.APPLICATION_XML.withCharset(UTF_8),
                ContentType.APPLICATION_XML.withCharset(UTF_8).toString(),
                "<person><firstName>Adam</firstName><lastName>Retter</lastName></person>");
    }

    private void assertMediaTypeResponse(final String uriEndpoint, final ContentType acceptContentType, final String expectedResponseContentType, final String expectedResponseBody) throws IOException {
        final HttpResponse response = executor.execute(Request
                .Get(getRestXqUri() + uriEndpoint)
                .addHeader(new BasicHeader("Accept", acceptContentType.toString()))
        ).returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        final HttpEntity responseEntity = response.getEntity();
        assertEquals(expectedResponseContentType, responseEntity.getContentType().getValue());

        final String responseBody;
        try (final InputStream is = responseEntity.getContent()) {
            responseBody = asString(is);
        }
        assertEquals(expectedResponseBody, responseBody);
    }

    private static String asString(final InputStream inputStream) throws IOException {
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
