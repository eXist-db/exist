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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reproduces https://github.com/eXist-db/exist/issues/6667 : a controller
 * pipeline that forwards to an XQuery step, then feeds the result through an
 * {@code <exist:view>} step that forwards to {@code XSLTServlet}, throws
 * "getOutputStream cannnot be called after getWriter" instead of serving the
 * transformed output.
 */
public class URLRewriteXSLTViewPipelineTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-xslt-view-pipeline";

    private static final String CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="A.xql">
                <exist:set-header name="Cache-Control" value="no-cache"/>
                <exist:set-header name="Pragma" value="no-cache"/>
              </exist:forward>
              <exist:view>
                <exist:forward servlet="XSLTServlet">
                  <exist:set-attribute name="xslt.stylesheet" value="xmldb:exist://%s/B.xsl"/>
                </exist:forward>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""".formatted(TEST_COLLECTION);

    private static final String A_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            <record>
              <name>Bob</name>
            </record>""";

    private static final String B_XSL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml">

              <xsl:output method="xml" media-type="text/html" omit-xml-declaration="yes" indent="no"/>

              <xsl:template match="/record">
                <p>Hello <xsl:value-of select="name"/></p>
              </xsl:template>

            </xsl:stylesheet>""";

    @BeforeClass
    public static void setup() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/A.xql", A_XQL, "application/xquery");
        storeViaRest(restUrl + "/B.xsl", B_XSL, "application/xslt+xml");

        final String chmod = "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/controller.xql'), 'rwxr-xr-x')," +
                "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/A.xql'), 'rwxr-xr-x')";
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
    public void forwardThenXsltViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-xslt-view-pipeline/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);

        assertEquals("Expected 200 OK but got " + result.statusCode() + ": "
                        + result.body().substring(0, Math.min(300, result.body().length())),
                HTTP_OK, result.statusCode());
        assertTrue("Response should contain the XSLT-transformed output",
                result.body().contains("Hello Bob"));
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
