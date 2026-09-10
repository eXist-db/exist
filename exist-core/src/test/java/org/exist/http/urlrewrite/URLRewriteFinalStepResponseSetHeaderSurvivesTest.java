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

/**
 * Characterization test, not a red/green regression test -- like
 * {@link URLRewriteSetHeaderSurvivesViewPipelineTest}, it passes both before and after the
 * {@code CachingResponseWrapper} header-buffering redesign it accompanies. It pins the case that
 * broke a first attempt at that redesign (an allowlist-based "suppress anything not in
 * controller.xql's static config" approach): a header the <em>final</em>, actually-flushed pipeline
 * step sets programmatically via XQuery's {@code response:set-header()} -- with no static config
 * representation at all -- must still reach the client. A naive "buffer while cache=true, drop
 * anything not explicitly configured" implementation drops this along with the genuinely
 * intermediate-step headers {@link URLRewriteResponseSetHeaderViewPipelineTest} covers, because it
 * cannot distinguish "this step's wrapper is the one that gets flushed" from "this step's wrapper
 * gets discarded" using config alone -- {@code cache=true} means "a view or error-handler exists
 * somewhere in the pipeline," not "this step is provisional."
 */
public class URLRewriteFinalStepResponseSetHeaderSurvivesTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-final-step-set-header";

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

            <step1>Hello</step1>""";

    private static final String VIEW_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return (
                (: The final step -- the one whose output actually reaches the client -- sets a
                   header programmatically, not via <exist:set-header> config. :)
                response:set-header("X-Custom-Header", "custom-value"),
                <p>View saw: { $data//step1/text() }</p>
            )""";

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
    public void finalStepResponseSetHeaderSurvivesToClient() throws IOException, InterruptedException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-final-step-set-header/test";

        final HttpClient client = AbstractHttpTest.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals("Expected 200 OK but got " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(300, response.body().length())),
                HTTP_OK, response.statusCode());
        assertEquals("The final step's own response:set-header() call must reach the client",
                "custom-value", response.headers().firstValue("X-Custom-Header").orElse(null));
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
