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
package org.exist.xquery.functions.request;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;

import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.MoreBodyPublishers;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import org.exist.http.AbstractHttpTest.HttpResponseResult;
import org.exist.http.RESTTest;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * Tests request:get-uploaded-file-headers() — the file part headers of a multipart upload.
 */
public class GetUploadedFileHeadersTest extends RESTTest {

    private static final String XQUERY =
            "xquery version \"3.1\";\n"
          + "let $file-headers := request:get-uploaded-file-headers(\"fileUpload\")\n"
          + "let $field-headers := request:get-uploaded-file-headers(\"param1\")\n"
          + "return string-join((\n"
          + "  \"file-count=\" || count($file-headers),\n"
          + "  \"field-count=\" || count($field-headers),\n"
          + "  for $m in $file-headers\n"
          + "    for $k in map:keys($m)\n"
          + "    return \"header:\" || $k || \"=\" || $m($k)\n"
          + "), \"|\")";
    private static final String XQUERY_FILENAME = "test-get-uploaded-file-headers.xql";

    private static final String TEST_FILE_NAME = "helloworld.txt";
    private static final String TEST_FILE_CONTENT = "hello world";

    private static Collection root;

    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        final BinaryResource res = root.createResource(XQUERY_FILENAME, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
        final UserManagementService ums = root.getService(UserManagementService.class);
        ums.chmod(res, 0777);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        final BinaryResource res = (BinaryResource) root.getResource(XQUERY_FILENAME);
        root.removeResource(res);
    }

    @Test
    public void fileHeadersAreExposedAndFieldsHaveNone() throws IOException {
        final MultipartBodyPublisher multipart = MultipartBodyPublisher.newBuilder()
                .textPart("param1", "value1")
                .formPart("fileUpload", TEST_FILE_NAME,
                        MoreBodyPublishers.ofMediaType(
                                HttpRequest.BodyPublishers.ofByteArray(TEST_FILE_CONTENT.getBytes(UTF_8)),
                                MediaType.TEXT_PLAIN))
                .build();

        final HttpRequest post = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME))
                .header("Content-Type", multipart.mediaType().toString())
                .POST(multipart)
                .build();

        final HttpResponseResult result = withHttpClient(client -> executeForStatusAndBody(client, post));
        assertEquals(200, result.statusCode());
        final String body = result.body();

        // one map for the single uploaded file, none for the plain form field
        assertTrue("expected one file header map: " + body, body.contains("file-count=1"));
        assertTrue("plain form field must not report file headers: " + body, body.contains("field-count=0"));
        // the file part's own Content-Type is exposed
        assertTrue("file part Content-Type should be exposed: " + body,
                body.toLowerCase().contains("header:content-type=text/plain"));
        // the Content-Disposition (carrying the filename) is exposed
        assertTrue("file part Content-Disposition should be exposed: " + body,
                body.toLowerCase().contains("header:content-disposition=") && body.contains(TEST_FILE_NAME));
    }
}
