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
package org.exist.xquery.modules.httpclient.xquery;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.exist.test.xqsuite.XQSuite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@XQSuite({
        "src/test/xquery"
})
class HttpClientXqueryTests {

    private static WireMockServer wireMock;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        // Configure some default endpoints
        wireMock.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world!")));

        wireMock.stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Hello JSON\"}")));

        wireMock.stubFor(get(urlEqualTo("/xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("<root><item>Hello XML</item></root>")));

        wireMock.stubFor(post(urlEqualTo("/post"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("ACK")));

        wireMock.stubFor(get(urlEqualTo("/headers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Header", "X-Value")
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Check headers")));

        // Expose port to XQuery via system property
        System.setProperty("wiremock.port", String.valueOf(wireMock.port()));
    }

    @AfterAll
    static void teardown() {
        System.clearProperty("wiremock.port");
        wireMock.stop();
    }
}
