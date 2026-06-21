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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.exist.test.runner.XSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@RunWith(XSuite.class)
@XSuite.XSuiteFiles({
    "src/test/xquery"
})
public class HttpClientXqueryTests {

    @ClassRule
    public static final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @BeforeClass
    public static void setup() {
        // Configure some default endpoints
        wireMockRule.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world!")));

        wireMockRule.stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Hello JSON\"}")));

        wireMockRule.stubFor(get(urlEqualTo("/xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("<root><item>Hello XML</item></root>")));

        wireMockRule.stubFor(post(urlEqualTo("/post"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("ACK")));

        wireMockRule.stubFor(get(urlEqualTo("/headers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Header", "X-Value")
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Check headers")));

        // Expose port to XQuery via system property
        System.setProperty("wiremock.port", String.valueOf(wireMockRule.port()));
    }

    @AfterClass
    public static void teardown() {
        System.clearProperty("wiremock.port");
    }
}
