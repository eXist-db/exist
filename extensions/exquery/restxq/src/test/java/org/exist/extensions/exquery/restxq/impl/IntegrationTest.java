/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.extensions.exquery.restxq.impl;

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
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;

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
    public static ExistWebServer existWebServer = new ExistWebServer(false, false, true, false);

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
                .authPreemptive("localhost");

        HttpResponse response = null;

        response = executor.execute(Request
                .Put(getRestUri() + "/db/system/config" + TEST_COLLECTION + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
                .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML)
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
        System.out.println(asString(response.getEntity().getContent()));
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
