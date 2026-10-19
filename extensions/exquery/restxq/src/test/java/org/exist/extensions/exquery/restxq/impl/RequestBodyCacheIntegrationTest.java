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
package org.exist.extensions.exquery.restxq.impl;

import org.exist.util.io.FilterInputStreamCacheMonitor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Every PUT or POST to a Resource Function caches the HTTP request body, by default in a temporary file.
 * That cache must be released once the request has been serviced, otherwise each request leaks an open
 * file handle until the JVM runs out of them.
 * See issue <a href="https://github.com/eXist-db/exist/issues/186">#186</a>.
 */
class RequestBodyCacheIntegrationTest extends AbstractClassIntegrationTest {

    private static final String TEST_COLLECTION = "/db/restxq/request-body-cache-integration-test";

    private static final String XQUERY =
            """
            xquery version "3.1";

            module namespace mod1 = "http://mod1";

            declare namespace rest = "http://exquery.org/ns/restxq";

            declare
                %rest:PUT("{$body}")
                %rest:path("/request-body-cache/body")
            function mod1:put-body($body) {
                <ok/>
            };

            declare
                %rest:POST("{$body}")
                %rest:path("/request-body-cache/body")
            function mod1:post-body($body) {
                <ok/>
            };

            declare
                %rest:PUT
                %rest:path("/request-body-cache/unbound-body")
            function mod1:put-unbound-body() {
                <ok/>
            };

            declare
                %rest:PUT("{$body}")
                %rest:path("/request-body-cache/error")
            function mod1:put-error($body) {
                error(xs:QName("mod1:error"), "intentional")
            };
            """;
    private static final String XQUERY_FILENAME = "request-body-cache.xqm";

    private static final int REQUEST_COUNT = 5;
    private static final long MAX_WAIT_PERIOD = 5_000;  // 5 seconds
    private static final long WAIT_INTERVAL = 50;

    @BeforeAll
    static void storeResourceFunctions() throws IOException {
        enableRestXqTrigger(TEST_COLLECTION);
        storeXquery(TEST_COLLECTION, XQUERY_FILENAME, XQUERY);
        assertRestXqResourceFunctionsCount(4);
    }

    static Stream<Arguments> requests() {
        return Stream.of(
                arguments("PUT", "/body", "application/xml", "<a>hello</a>", HTTP_OK),
                arguments("PUT", "/body", "application/xml", "<a>not well-formed", HTTP_OK),
                arguments("PUT", "/body", "text/plain", "hello", HTTP_OK),
                arguments("PUT", "/body", null, "", HTTP_OK),
                arguments("POST", "/body", "application/xml", "<a>hello</a>", HTTP_OK),
                arguments("PUT", "/unbound-body", "application/xml", "<a>hello</a>", HTTP_OK),
                arguments("PUT", "/error", "application/xml", "<a>hello</a>", HTTP_INTERNAL_ERROR),
                arguments("PUT", "/error", "text/plain", "hello", HTTP_INTERNAL_ERROR)
        );
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("requests")
    void requestBodyCacheIsReleased(final String method, final String endpoint, final String contentType, final String body, final int expectedStatus) throws IOException, InterruptedException {
        final int activeCachesBefore = activeCaches();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            final HttpRequest.Builder builder = authenticatedAdminRequest(getRestXqUri() + "/request-body-cache" + endpoint)
                    .method(method, HttpRequest.BodyPublishers.ofString(body, UTF_8));
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }

            final HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(expectedStatus, response.statusCode());
        }

        // the response may reach the client just before the servlet releases the cache, so give it a little time to do so...
        int activeCachesAfter = activeCaches();
        long timeSlept = 0;
        while (activeCachesAfter != activeCachesBefore && timeSlept < MAX_WAIT_PERIOD) {
            Thread.sleep(WAIT_INTERVAL);
            timeSlept += WAIT_INTERVAL;
            activeCachesAfter = activeCaches();
        }

        assertEquals(activeCachesBefore, activeCachesAfter, "request body caches were not released");
    }

    private static int activeCaches() {
        return FilterInputStreamCacheMonitor.getInstance().getActive().size();
    }
}
