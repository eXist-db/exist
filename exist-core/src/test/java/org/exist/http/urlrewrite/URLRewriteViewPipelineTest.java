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

import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.*;

/**
 * Tests the URL rewrite view pipeline — specifically the case where a stored
 * HTML document (text/html) is forwarded through a view.xq that processes it
 * via request:get-data().
 *
 * This test was written to catch a regression where:
 * 1. RESTServer forces method=xhtml for text/html documents
 * 2. The XHTML serialization produces non-self-closing meta tags
 * 3. The view's request:get-data() fails to parse the invalid XML
 * 4. The view receives a string instead of XML nodes, causing XPTY0019
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/XXXX">URL rewrite view pipeline regression</a>
 */
public class URLRewriteViewPipelineTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-url-rewrite";

    private static final String CONTROLLER_XQ = """
            xquery version "3.1";
            declare variable $exist:path external;
            declare variable $exist:resource external;
            declare variable $exist:controller external;
            declare variable $exist:prefix external;

            if (ends-with($exist:resource, '.html')) then
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <view>
                        <forward url="view.xq"/>
                    </view>
                </dispatch>
            else
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <cache-control cache="yes"/>
                </dispatch>""";

    private static final String VIEW_XQ = """
            xquery version "3.1";
            declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";
            declare option output:method "html";
            declare option output:media-type "text/html";

            let $html := request:get-data()
            return
                <html>
                    <head>
                        <title>View Pipeline Test</title>
                        { $html/html/head/* }
                    </head>
                    { $html/html/body }
                </html>""";

    private static final String HTML_WITH_HEAD = """
            <html>
                <head>
                    <title>Test Page</title>
                    <meta charset="utf-8"/>
                </head>
                <body>
                    <h1>Hello World</h1>
                </body>
            </html>""";

    private static final String HTML_WITHOUT_HEAD = """
            <html>
                <body>
                    <h1>Hello World</h1>
                </body>
            </html>""";

    @BeforeClass
    public static void setup() throws Exception {
        // Store test files via REST API (admin user)
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;

        // Create collection and store files via HTTP PUT
        storeViaRest(restUrl + "/controller.xq", CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/view.xq", VIEW_XQ, "application/xquery");
        storeViaRest(restUrl + "/with-head.html", HTML_WITH_HEAD, "text/html");
        storeViaRest(restUrl + "/no-head.html", HTML_WITHOUT_HEAD, "text/html");

        // Set execute permissions on XQuery files
        final String chmod = "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/controller.xq'), 'rwxr-xr-x')," +
                "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/view.xq'), 'rwxr-xr-x')";
        final String chmodUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                URLEncoder.encode(chmod, StandardCharsets.UTF_8) + "&_wrap=no";
        final HttpRequest chmodRequest = AbstractHttpTest.authenticatedRequest(URI.create(chmodUrl), "admin", "")
                .GET()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), chmodRequest);
    }

    @AfterClass
    public static void teardown() throws Exception {
        // Remove test collection via REST
        final String deleteUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;
        final HttpRequest deleteRequest = AbstractHttpTest.authenticatedRequest(URI.create(deleteUrl), "admin", "")
                .DELETE()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), deleteRequest);
    }

    /**
     * Tests that an HTML document WITH a head element can be served through
     * the URL rewrite view pipeline. This is the regression case — the view
     * must receive the document as XML nodes, not as a string.
     */
    @Test
    public void htmlWithHeadThroughViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-url-rewrite/with-head.html";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        final int status = result.statusCode();
        final String body = result.body();

        // Should return 200, not 400 (namespace error) or 500 (XPTY0019)
        assertEquals("Expected 200 OK but got " + status + ": " + body.substring(0, Math.min(200, body.length())),
                HTTP_OK, status);

        // The response should contain the original title from the source HTML
        assertTrue("Response should contain the source page's title",
                body.contains("Test Page"));

        // The response should contain the view's wrapper title
        assertTrue("Response should contain the view's title",
                body.contains("View Pipeline Test"));

        // The response should contain the body content
        assertTrue("Response should contain body content",
                body.contains("Hello World"));

        // The response should NOT contain raw XML entities (indicating string was returned)
        assertFalse("Response should not contain escaped XML (string instead of nodes)",
                body.contains("&lt;html"));
    }

    /**
     * Tests that an HTML document WITHOUT a head element works (baseline).
     */
    @Test
    public void htmlWithoutHeadThroughViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-url-rewrite/no-head.html";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        final int status = result.statusCode();

        assertEquals(HTTP_OK, status);

        final String body = result.body();
        assertTrue("Response should contain body content",
                body.contains("Hello World"));
    }

    private static void storeViaRest(final String url, final String content, final String contentType)
            throws IOException {
        final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(url), "admin", "")
                .header("Content-Type", contentType + "; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8))
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), request);
    }
}
