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
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reproduces https://github.com/eXist-db/exist/issues/6669 : a controller
 * pipeline that forwards to a static filesystem XML resource (served by the
 * servlet engine's "default" servlet via {@link PassThrough}), then feeds the
 * result through an {@code <exist:view>} step that forwards to an XQuery
 * generating longer output. On the real bug this throws
 * "written x &gt; z content-length" because the static resource's
 * Content-Length header leaks onto the real response and is never reset
 * before the view's longer output is flushed to it.
 *
 * <p>Deliberately uses filesystem-served resources (matching the original bug
 * report, which bind-mounted plain files into the webapp), since the static
 * resource's Content-Length is set by the servlet engine's default servlet,
 * not by eXist's own DB-resource serving path.</p>
 */
public class URLRewriteContentLengthViewPipelineTest {

    @ClassRule
    public static final ExistWebServer existWebServer = ExistWebServer.builder()
            .useRandomPort()
            .disableAutoDeploy()
            .useTemporaryStorage()
            .jettyStandaloneMode(false)
            .build();

    private static final String TEST_DIR_NAME = "test-content-length-view-pipeline";

    private static final String CONTROLLER_XQL = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";
            declare variable $exist:path external;

            if (contains($exist:path, 'A.xml')) then
              <ignore xmlns="http://exist.sourceforge.net/NS/exist">
                <cache-control cache="no"/>
              </ignore>
            else
              <exist:dispatch>
                <exist:forward url="A.xml">
                  <exist:set-header name="Cache-Control" value="no-cache"/>
                  <exist:set-header name="Pragma" value="no-cache"/>
                </exist:forward>
                <exist:view>
                  <exist:forward url="B.xql"/>
                </exist:view>
                <exist:cache-control cache="no"/>
              </exist:dispatch>""";

    private static final String A_XML = """
            <record>
              <name>Bob</name>
            </record>""";

    private static final String B_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return
                <p>Hello { $data//name/text() }, how do you do today { $data//name/text() } ?</p>""";

    private static Path testDir;

    @BeforeClass
    public static void setup() throws Exception {
        // Mirrors the relative path used by exist-webapp-context.xml (jetty.home/../../../webapp)
        // to locate the distribution-mode "/exist" main webapp's exploded document root.
        final Path webappDir = Path.of(System.getProperty("jetty.home"), "..", "..", "..", "webapp").normalize();
        testDir = webappDir.resolve(TEST_DIR_NAME);
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("controller.xql"), CONTROLLER_XQL, StandardCharsets.UTF_8);
        Files.writeString(testDir.resolve("A.xml"), A_XML, StandardCharsets.UTF_8);
        Files.writeString(testDir.resolve("B.xql"), B_XQL, StandardCharsets.UTF_8);
    }

    @AfterClass
    public static void teardown() throws Exception {
        if (testDir != null) {
            Files.deleteIfExists(testDir.resolve("controller.xql"));
            Files.deleteIfExists(testDir.resolve("A.xml"));
            Files.deleteIfExists(testDir.resolve("B.xql"));
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void forwardStaticThenLongerViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/" + TEST_DIR_NAME + "/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);

        assertEquals("Expected 200 OK but got " + result.statusCode() + ": "
                        + result.body().substring(0, Math.min(300, result.body().length())),
                HTTP_OK, result.statusCode());
        assertTrue("Response should contain the view's longer output",
                result.body().contains("Hello Bob, how do you do today Bob ?"));
    }
}
