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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Characterization test for a behavior that {@code CachingResponseWrapper} relies on but that,
 * before this test, nothing actually asserted: an {@code <exist:set-header>} directive on a
 * {@code controller.xql} forward step must survive to the final response even when that step is
 * followed by an {@code <exist:view>} step -- even though {@code applyViews()} discards the forward
 * step's response wrapper (and only ever flushes the last view's wrapper) once the view runs.
 * <p>
 * This is deliberately NOT a red/green regression test -- it passes both before and after the
 * {@code CachingResponseWrapper} header-buffering redesign it accompanies. Its purpose is to pin
 * this specific behavior as a safety net for that redesign: a naive "buffer every header while
 * caching, replay only from the last step's flush()" implementation would silently drop the
 * {@code Cache-Control}/{@code Pragma} directives below, because they are set on the forward step's
 * wrapper -- which never gets flushed -- not on the view step's. Both
 * https://github.com/eXist-db/exist/issues/6667 and
 * https://github.com/eXist-db/exist/issues/6669's own repro {@code controller.xql} scripts rely on
 * exactly this survival for their own {@code <exist:set-header>} directives.
 */
public class URLRewriteSetHeaderSurvivesViewPipelineTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-set-header-view-pipeline";

    private static final String CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="step1.xql">
                <exist:set-header name="Cache-Control" value="no-cache"/>
                <exist:set-header name="Pragma" value="no-cache"/>
              </exist:forward>
              <exist:view>
                <exist:forward url="view.xql"/>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""";

    private static final String STEP1_XQL = """
            xquery version "3.1";

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
                java.net.URLEncoder.encode(chmod, StandardCharsets.UTF_8) + "&_wrap=no";
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
    public void setHeaderOnForwardStepSurvivesToFinalViewResponse() throws IOException, InterruptedException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-set-header-view-pipeline/test";

        final HttpClient client = AbstractHttpTest.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals("Expected 200 OK but got " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(300, response.body().length())),
                HTTP_OK, response.statusCode());

        // Proves the request actually went through the full forward-then-view pipeline, not just
        // step1's raw output.
        assertTrue("Response should contain the view's output, not step1's raw output",
                response.body().contains("View saw: Hello"));

        // The actual behavior under test: the forward step's <exist:set-header> directives must
        // have survived past the view step that replaced its response wrapper.
        assertEquals("Cache-Control set on the forward step must survive to the final response",
                "no-cache", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("Pragma set on the forward step must survive to the final response",
                "no-cache", response.headers().firstValue("Pragma").orElse(null));
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
