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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers a header-leak shape the {@code CachingResponseWrapper} fixes in
 * https://github.com/eXist-db/exist/pull/6691 could only ever close one enumerated header name at
 * a time (Content-Length, then Content-Type, then Last-Modified/ETag/Accept-Ranges): a header an
 * <em>intermediate</em> pipeline step sets programmatically via XQuery's {@code response:set-header()}
 * -- not through {@code controller.xql}'s static {@code <exist:set-header>} config -- has no name a
 * fixed allowlist could ever contain, yet describes that step's own (discarded, once a view runs)
 * output just as much as its Content-Length does.
 * <p>
 * Paired with {@link URLRewriteFinalStepResponseSetHeaderSurvivesTest}, which pins the opposite,
 * equally-necessary case: a header the <em>final</em>, actually-flushed step sets the same way must
 * still reach the client.
 */
public class URLRewriteResponseSetHeaderViewPipelineTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-intermediate-set-header";

    private static final String CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="step1.xql"/>
              <exist:view>
                <exist:forward url="view.xql"/>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""";

    private static final String STEP1_XQL = """
            xquery version "3.1";

            (: Set programmatically, not via <exist:set-header> config -- this step's own header,
               which must not survive once the view step below replaces its output. :)
            response:set-header("X-Step1-Debug", "step1-was-here"),
            <step1>Hello</step1>""";

    private static final String VIEW_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return
                <p>View saw: { $data//step1/text() }</p>""";

    @BeforeClass
    public static void setup() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/step1.xql", STEP1_XQL, "application/xquery");
        storeViaRest(restUrl + "/view.xql", VIEW_XQL, "application/xquery");

        final String chmod = "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/controller.xql'), 'rwxr-xr-x')," +
                "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/step1.xql'), 'rwxr-xr-x')," +
                "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/view.xql'), 'rwxr-xr-x')";
        final String chmodUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                URLEncoder.encode(chmod, StandardCharsets.UTF_8) + "&_wrap=no";
        final HttpRequest chmodRequest = AbstractHttpTest.authenticatedRequest(URI.create(chmodUrl), "admin", "")
                .GET()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), chmodRequest);
    }

    @AfterClass
    public static void teardown() throws Exception {
        final String deleteUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;
        final HttpRequest deleteRequest = AbstractHttpTest.authenticatedRequest(URI.create(deleteUrl), "admin", "")
                .DELETE()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), deleteRequest);
    }

    @Test
    public void intermediateStepHeaderDoesNotSurviveToFinalViewResponse() throws IOException, InterruptedException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-intermediate-set-header/test";

        final HttpClient client = AbstractHttpTest.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals("Expected 200 OK but got " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(300, response.body().length())),
                HTTP_OK, response.statusCode());

        // Proves the request actually went through the full forward-then-view pipeline.
        assertTrue("Response should contain the view's output",
                response.body().contains("View saw: Hello"));

        // The actual behavior under test: step1's own header must not leak past the view that
        // replaced its output -- step1's response was never the one actually sent to the client.
        assertTrue("X-Step1-Debug from the discarded intermediate step must not reach the client",
                response.headers().firstValue("X-Step1-Debug").isEmpty());
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
