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

import org.exist.TestUtils;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;

import static org.exist.http.urlrewrite.XQueryURLRewrite.LEGACY_XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for eXist-db/exist#6603: the value of the client's {@code If-Modified-Since}
 * header must survive a controller.xql -&gt; view.xql handover even when the dispatch has a
 * {@code <view>}. {@code XQueryURLRewrite} still suppresses {@code If-Modified-Since} from the
 * conditional-GET check (via {@code getDateHeader()} when a view applies, so that view changes are
 * not masked by a 304), but it no longer blanks {@code getHeader()} — so application code reading
 * the raw value through {@code request:get-header()} sees it. Before the fix the value was lost
 * (empty) while the name still appeared in {@code request:get-header-names()}, which is what the
 * reporter observed.
 */
public class IfModifiedSinceHandoverControllerTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String IF_MODIFIED_SINCE = "Wed, 21 Oct 2015 07:28:00 GMT";

    /** Echoes what the request: module can see of the If-Modified-Since header. */
    private static final String ECHO =
            """
            xquery version "3.1";
            <result get-header="{request:get-header('If-Modified-Since')}"
                    name-present="{'If-Modified-Since' = request:get-header-names()}"/>
            """;

    private static final String MODEL =
            """
            xquery version "3.1";
            <model/>
            """;

    /** Dispatch WITH a view: the model output is passed to the view (echo.xql). */
    private static final String CONTROLLER_WITH_VIEW =
            """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";
            declare variable $exist:controller external;
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$exist:controller}/model.xql"/>
                <view>
                    <forward url="{$exist:controller}/echo.xql"/>
                </view>
            </dispatch>
            """;

    /** Dispatch WITHOUT a view: echo.xql is the model, so caching is left enabled. */
    private static final String CONTROLLER_NO_VIEW =
            """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";
            declare variable $exist:controller external;
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$exist:controller}/echo.xql"/>
            </dispatch>
            """;

    @Test
    public void ifModifiedSinceValueSurvivesViewHandover() throws IOException {
        final String coll = "ims-with-view";
        store(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", CONTROLLER_WITH_VIEW);
        store(coll, "model.xql", "application/xquery", MODEL);
        store(coll, "echo.xql", "application/xquery", ECHO);

        final String body = send(coll);

        // The header name is visible to the handler...
        assertTrue("If-Modified-Since should be listed by get-header-names(): " + body,
                body.contains("name-present=\"true\""));
        // ...and, after the #6603 fix, so is its value: the view handover no longer blanks
        // getHeader(), so request:get-header() returns what the client sent.
        assertTrue("If-Modified-Since value must survive the view handover (#6603): " + body,
                body.contains("get-header=\"" + IF_MODIFIED_SINCE + "\""));
    }

    @Test
    public void ifModifiedSinceValueIsVisibleWithoutView() throws IOException {
        final String coll = "ims-no-view";
        store(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", CONTROLLER_NO_VIEW);
        store(coll, "echo.xql", "application/xquery", ECHO);

        final String body = send(coll);

        // Control: the identical request through a view-less dispatch exposes the real value,
        // confirming the header is genuinely sent and that the loss is specific to the view path.
        assertTrue("If-Modified-Since value should be visible without a view: " + body,
                body.contains("get-header=\"" + IF_MODIFIED_SINCE + "\""));
    }

    private void store(final String coll, final String name, final String mediaType, final String content) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getRestUri(existWebServer) + "/db/apps/" + coll + "/" + name),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", mediaType)
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .build();
        final int status = withHttpClient(client -> executeForStatus(client, request));
        assertEquals(java.net.HttpURLConnection.HTTP_CREATED, status);
    }

    private String send(final String coll) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getServerUri(existWebServer) + "/apps/" + coll + "/render"),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("If-Modified-Since", IF_MODIFIED_SINCE)
                .GET()
                .build();
        return withHttpClient(client -> executeForStatusAndBody(client, request).body());
    }
}
