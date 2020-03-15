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

package org.exist.http.urlrewrite;

import com.evolvedbinary.j8fu.function.FunctionE;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.urlrewrite.XQueryURLRewrite.XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class URLRewritingTest {

    private static final XmldbURI TEST_COLLECTION_NAME = XmldbURI.create("controller-test");
    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/apps").append(TEST_COLLECTION_NAME);

    private static final String TEST_CONTROLLER = "xquery version \"3.1\";\n<controller>{fn:current-dateTime()}</controller>";

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void findsParentController() throws IOException {
        final XmldbURI nestedCollectionName = XmldbURI.create("nested");
        final XmldbURI docName = XmldbURI.create("test.xml");
        final String testDocument = "<hello>world</hello>";

        final String storeDocUri = getRestUri() + TEST_COLLECTION.append(nestedCollectionName).append(docName);
        final Request storeRequest = Request
                .Put(storeDocUri)
                .bodyString(testDocument, ContentType.APPLICATION_XML);
        final int storeResponseStatusCode = withHttpExecutor(executor -> executor.execute(storeRequest).returnResponse().getStatusLine().getStatusCode());
        assertEquals(HttpStatus.SC_CREATED, storeResponseStatusCode);

        final String retrieveDocUri = getAppsUri() + "/" + TEST_COLLECTION_NAME.append(nestedCollectionName).append(docName);
        final Request retrieveRequest = Request
                .Get(retrieveDocUri);
        final Tuple2<Integer, String> retrieveResponseStatusCodeAndBody = withHttpExecutor(executor -> {
            final HttpResponse response = executor.execute(retrieveRequest).returnResponse();
            final String responseBody;
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream((int)response.getEntity().getContentLength())) {
                response.getEntity().writeTo(baos);
                responseBody = baos.toString(UTF_8);
            }
            return Tuple(response.getStatusLine().getStatusCode(), responseBody);
        });
        assertEquals(HttpStatus.SC_OK, retrieveResponseStatusCodeAndBody._1.intValue());
        assertTrue(retrieveResponseStatusCodeAndBody._2.matches("<controller>.+</controller>"));
    }

    @BeforeClass
    public static void setup() throws IOException {
        final Request request = Request
                .Put(getRestUri() + TEST_COLLECTION + "/" + XQUERY_CONTROLLER_FILENAME)
                .bodyString(TEST_CONTROLLER, ContentType.create("application/xquery"));

        final int statusCode = withHttpExecutor(executor ->
                executor.execute(request).returnResponse().getStatusLine().getStatusCode()
        );

        assertEquals(HttpStatus.SC_CREATED, statusCode);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        final Request request = Request
                .Delete(getRestUri() + TEST_COLLECTION);

        final int statusCode = withHttpExecutor(executor ->
                executor.execute(request).returnResponse().getStatusLine().getStatusCode()
        );

        assertEquals(HttpStatus.SC_OK, statusCode);
    }

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/exist";
    }

    private static String getRestUri() {
        return getServerUri() + "/rest";
    }

    private static String getAppsUri() {
        return getServerUri() + "/apps";
    }

    private static <T> T withHttpClient(final FunctionE<HttpClient, T, IOException> fn) throws IOException {
        try (final CloseableHttpClient client = HttpClientBuilder
                .create()
                .disableAutomaticRetries()
                .build()) {
            return fn.apply(client);
        }
    }

    private static <T> T withHttpExecutor(final FunctionE<Executor, T, IOException> fn) throws IOException {
        return withHttpClient(client -> {
            final Executor executor = Executor
                    .newInstance(client)
                    .auth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                    .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));
            return fn.apply(executor);
        });
    }
}
