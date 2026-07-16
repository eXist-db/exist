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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.urlrewrite.XQueryURLRewrite.LEGACY_XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reproduction for multipart/form-data parsing on methods other than POST
 * (eXist-db/exist#6580, #6578), via a controller.xql that reports what the
 * {@code request:} module can see of an identical multipart body — the same
 * path third-party routers (e.g. roaster) take.
 */
public class MultipartMethodControllerTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String BOUNDARY = "wdbBoundary";

    private static final String MULTIPART_BODY =
            "--" + BOUNDARY + "\r\n"
          + "Content-Disposition: form-data; name=\"path\"\r\n"
          + "\r\n"
          + "edition/01/17410105.xml\r\n"
          + "--" + BOUNDARY + "\r\n"
          + "Content-Disposition: form-data; name=\"file\"; filename=\"17410105.xml\"\r\n"
          + "Content-Type: application/xml\r\n"
          + "\r\n"
          + "<example>hello</example>\r\n"
          + "--" + BOUNDARY + "--\r\n";

    private static final String CONTROLLER =
            """
            xquery version "3.1";
            <result method="{request:get-method()}"
                    is-multipart="{request:is-multipart-content()}"
                    param-names="{string-join(request:get-parameter-names(), ',')}"
                    path="{request:get-parameter('path', ())}"
                    file-param="{request:get-parameter('file', ())}"
                    uploaded-files="{string-join(request:get-uploaded-file-name('file'), ',')}"/>
            """;

    @Test
    public void multipartFormDataIsParsedForPostAndPut() throws IOException {
        final String coll = "multipart-method-controller";
        store(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", CONTROLLER);

        // A multipart/form-data body must be parsed identically regardless of HTTP method:
        // both the form field ("path") and the uploaded file ("file") must be visible, and
        // request:is-multipart-content() must report true. Prior to the fix, PUT reported
        // is-multipart-content()=false and exposed neither the file nor its part (#6580),
        // and the controller/RESTXQ path never exposed the uploaded file at all (#6578).
        for (final String method : new String[]{"POST", "PUT"}) {
            final String body = send(coll, method);
            assertTrue(method + ": is-multipart-content() should be true: " + body,
                    body.contains("is-multipart=\"true\""));
            assertTrue(method + ": form field 'path' should be visible: " + body,
                    body.contains("path=\"edition/01/17410105.xml\""));
            assertTrue(method + ": uploaded file 'file' should be visible: " + body,
                    body.contains("uploaded-files=\"17410105.xml\""));
        }
    }

    private void store(final String coll, final String name, final String mediaType, final String content) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getRestUri(existWebServer) + "/db/apps/" + coll + "/" + name),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", mediaType)
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .build();
        final int status = withHttpClient(client -> executeForStatus(client, request));
        assertEquals(HttpURLConnection.HTTP_CREATED, status);
    }

    private String send(final String coll, final String method) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getServerUri(existWebServer) + "/apps/" + coll + "/echo"),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .method(method, HttpRequest.BodyPublishers.ofString(MULTIPART_BODY, UTF_8))
                .build();
        return withHttpClient(client -> executeForStatusAndBody(client, request).body());
    }
}
