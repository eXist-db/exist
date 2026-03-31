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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String TEST_COLLECTION = "/db/apps/test-url-rewrite";

    private static final String CONTROLLER_XQ =
            "xquery version \"3.1\";\n" +
            "declare variable $exist:path external;\n" +
            "declare variable $exist:resource external;\n" +
            "declare variable $exist:controller external;\n" +
            "declare variable $exist:prefix external;\n" +
            "\n" +
            "if (ends-with($exist:resource, '.html')) then\n" +
            "    <dispatch xmlns=\"http://exist.sourceforge.net/NS/exist\">\n" +
            "        <view>\n" +
            "            <forward url=\"view.xq\"/>\n" +
            "        </view>\n" +
            "    </dispatch>\n" +
            "else\n" +
            "    <dispatch xmlns=\"http://exist.sourceforge.net/NS/exist\">\n" +
            "        <cache-control cache=\"yes\"/>\n" +
            "    </dispatch>";

    private static final String VIEW_XQ =
            "xquery version \"3.1\";\n" +
            "declare namespace output=\"http://www.w3.org/2010/xslt-xquery-serialization\";\n" +
            "declare option output:method \"html\";\n" +
            "declare option output:media-type \"text/html\";\n" +
            "\n" +
            "let $html := request:get-data()\n" +
            "return\n" +
            "    <html>\n" +
            "        <head>\n" +
            "            <title>View Pipeline Test</title>\n" +
            "            { $html/html/head/* }\n" +
            "        </head>\n" +
            "        { $html/html/body }\n" +
            "    </html>";

    private static final String HTML_WITH_HEAD =
            "<html>\n" +
            "    <head>\n" +
            "        <title>Test Page</title>\n" +
            "        <meta charset=\"utf-8\"/>\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <h1>Hello World</h1>\n" +
            "    </body>\n" +
            "</html>";

    private static final String HTML_WITHOUT_HEAD =
            "<html>\n" +
            "    <body>\n" +
            "        <h1>Hello World</h1>\n" +
            "    </body>\n" +
            "</html>";

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
        Request.Get("http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                java.net.URLEncoder.encode(chmod, "UTF-8") + "&_wrap=no")
                .addHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("admin:".getBytes()))
                .execute();
    }

    @AfterClass
    public static void teardown() throws Exception {
        // Remove test collection via REST
        Request.Delete("http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION)
                .addHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("admin:".getBytes()))
                .execute();
    }

    /**
     * Tests that an HTML document WITH a head element can be served through
     * the URL rewrite view pipeline. This is the regression case — the view
     * must receive the document as XML nodes, not as a string.
     */
    @Test
    public void htmlWithHeadThroughViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/apps/test-url-rewrite/with-head.html";

        final HttpResponse response = Request.Get(url).execute().returnResponse();
        final int status = response.getStatusLine().getStatusCode();
        final String body = new String(
                response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);

        // Should return 200, not 400 (namespace error) or 500 (XPTY0019)
        assertEquals("Expected 200 OK but got " + status + ": " + body.substring(0, Math.min(200, body.length())),
                HttpStatus.SC_OK, status);

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
                + "/apps/test-url-rewrite/no-head.html";

        final HttpResponse response = Request.Get(url).execute().returnResponse();
        final int status = response.getStatusLine().getStatusCode();

        assertEquals(HttpStatus.SC_OK, status);

        final String body = new String(
                response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue("Response should contain body content",
                body.contains("Hello World"));
    }

    private static void storeViaRest(final String url, final String content, final String contentType)
            throws IOException {
        Request.Put(url)
                .addHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("admin:".getBytes()))
                .bodyString(content, ContentType.create(contentType, StandardCharsets.UTF_8))
                .execute();
    }
}
