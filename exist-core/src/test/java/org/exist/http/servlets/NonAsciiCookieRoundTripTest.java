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
package org.exist.http.servlets;

import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end round trip for a non-ASCII cookie value set via {@code response:set-cookie()} and
 * read back via {@code request:get-cookie-value()} on a following request. Red against unfixed
 * {@code HttpResponseWrapper}/{@code GetCookieValue}, and worse than the silent drop
 * {@link HttpResponseWrapperEncodingWireTest} measured directly: through this request shape, the
 * whole request fails with a 500 -- {@code org.eclipse.jetty.http.ComplianceViolationException:
 * ... RFC6265 Cookie values characters restricted to US-ASCII: 0xd0 (forbidden)}, the first byte
 * of "К" in UTF-8 -- rather than merely omitting the cookie. Same root cause either way: RFC
 * 6265's cookie-octet grammar excludes bytes >= 0x80, and the current ISO-8859-1 byte-packing
 * scheme (shared with the header path, where it works fine -- see
 * {@link HttpResponseWrapperEncodingTest}) produces exactly those bytes for non-ASCII text.
 * <p>
 * Uses a normal cookie-aware {@link HttpClient} rather than a raw socket: once the wire
 * representation is fixed to be percent-encoded, it is pure ASCII, so there is no header-value
 * charset ambiguity left for a higher-level HTTP client to reintroduce.
 */
public class NonAsciiCookieRoundTripTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-cookie-round-trip";

    private static final String CYRILLIC = "Кириллица";

    private static final String TEST_XQL = """
            xquery version "3.1";
            if (empty(request:get-cookie-value("test-cookie")))
            then (
                response:set-cookie("test-cookie", "%s"),
                <result step="set"/>
            )
            else
                <result step="get" value="{request:get-cookie-value('test-cookie')}"/>""".formatted(CYRILLIC);

    @BeforeClass
    public static void setup() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;
        final HttpRequest storeRequest = AbstractHttpTest.authenticatedRequest(
                        URI.create(restUrl + "/test.xql"), "admin", "")
                .header("Content-Type", "application/xquery; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(TEST_XQL, UTF_8))
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), storeRequest);

        final String chmod = "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/test.xql'), 'rwxr-xr-x')";
        final String chmodUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                URLEncoder.encode(chmod, UTF_8) + "&_wrap=no";
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
    public void nonAsciiCookieSurvivesAndRoundTripsCorrectly() throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        final URI url = URI.create("http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-cookie-round-trip/test.xql");

        final HttpResponse<String> first = client.send(HttpRequest.newBuilder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals("First request should not fail -- see class javadoc for why it currently does "
                        + "(a 500, not just a dropped cookie, depending on request shape): " + first.body(),
                200, first.statusCode());
        assertTrue("First request should hit the 'set' branch (no cookie sent yet): " + first.body(),
                first.body().contains("step=\"set\""));
        assertTrue("Set-Cookie must actually be present on the first response -- currently dropped "
                        + "entirely for non-ASCII values (see HttpResponseWrapperEncodingWireTest)",
                first.headers().firstValue("Set-Cookie").isPresent());

        final HttpResponse<String> second = client.send(HttpRequest.newBuilder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, second.statusCode());
        assertTrue("Second request (cookie jar now has test-cookie) should hit the 'get' branch: " + second.body(),
                second.body().contains("step=\"get\""));
        assertTrue("request:get-cookie-value() should return the original Cyrillic text: " + second.body(),
                second.body().contains("value=\"" + CYRILLIC + "\""));
    }
}
