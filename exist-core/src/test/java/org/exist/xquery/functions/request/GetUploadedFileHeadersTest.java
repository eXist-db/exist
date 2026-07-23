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
 *
 * <p>The stored query inspects the upload parameter named by the {@code inspect} URL query
 * parameter (defaulting to {@code fileUpload}) and reports, for that name: the number of
 * uploaded files, the file names (from request:get-uploaded-file-name, to check positional
 * alignment), and each file's Content-Type / Content-Disposition (looked up case-insensitively,
 * since header-name casing is the servlet container's to decide).</p>
 */
public class GetUploadedFileHeadersTest extends RESTTest {

    private static final String XQUERY =
            """
            xquery version "3.1";
            let $name := request:get-parameter("inspect", "fileUpload")
            let $headers := request:get-uploaded-file-headers($name)
            let $names := request:get-uploaded-file-name($name)
            return string-join((
              "count=" || count($headers),
              "names=[" || string-join($names, ",") || "]",
              for $i in 1 to count($headers)
                let $m := $headers[$i]
                let $ct := $m(map:keys($m)[lower-case(.) = "content-type"])
                let $cd := $m(map:keys($m)[lower-case(.) = "content-disposition"])
                return "file" || $i || ":ct=" || ($ct, "")[1] || ":cd=" || ($cd, "")[1]
            ), "|")""";
    private static final String XQUERY_FILENAME = "test-get-uploaded-file-headers.xql";

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
    public void singleFileExposesItsHeaders() throws IOException {
        final MultipartBodyPublisher body = MultipartBodyPublisher.newBuilder()
                .textPart("param1", "value1")
                .formPart("fileUpload", "helloworld.txt", filePart("hello world", MediaType.TEXT_PLAIN))
                .build();

        final String result = post(body, "fileUpload");
        assertTrue("one header map for the single uploaded file: " + result, result.contains("count=1"));
        assertTrue("file names aligned: " + result, result.contains("names=[helloworld.txt]"));
        assertTrue("Content-Type exposed: " + result, result.contains("file1:ct=text/plain:"));
        assertTrue("Content-Disposition exposed with filename: " + result, result.contains("filename=\"helloworld.txt\""));
    }

    @Test
    public void plainFormFieldHasNoFileHeaders() throws IOException {
        final MultipartBodyPublisher body = MultipartBodyPublisher.newBuilder()
                .textPart("param1", "value1")
                .formPart("fileUpload", "helloworld.txt", filePart("hello world", MediaType.TEXT_PLAIN))
                .build();

        final String result = post(body, "param1");
        assertTrue("a plain form field is not a file part, so no header maps: " + result, result.contains("count=0"));
    }

    @Test
    public void multipleFilesEachHaveHeadersPositionallyAligned() throws IOException {
        final MultipartBodyPublisher body = MultipartBodyPublisher.newBuilder()
                .formPart("fileUpload", "first.xml", filePart("<a/>", MediaType.APPLICATION_XML))
                .formPart("fileUpload", "second.json", filePart("{}", MediaType.APPLICATION_JSON))
                .build();

        final String result = post(body, "fileUpload");
        assertTrue("one header map per uploaded file: " + result, result.contains("count=2"));
        assertTrue("both file names present and ordered: " + result, result.contains("names=[first.xml,second.json]"));
        // header map i aligns with file name i
        assertTrue("first file's headers align with first.xml: " + result,
                result.contains("file1:ct=application/xml:") && result.contains("filename=\"first.xml\""));
        assertTrue("second file's headers align with second.json: " + result,
                result.contains("file2:ct=application/json:") && result.contains("filename=\"second.json\""));
    }

    @Test
    public void nonExistentParameterReturnsEmpty() throws IOException {
        final MultipartBodyPublisher body = MultipartBodyPublisher.newBuilder()
                .formPart("fileUpload", "helloworld.txt", filePart("hello world", MediaType.TEXT_PLAIN))
                .build();

        final String result = post(body, "doesNotExist");
        assertTrue("an unknown parameter yields the empty sequence: " + result, result.contains("count=0"));
    }

    @Test
    public void nonMultipartRequestReturnsEmpty() throws IOException {
        // A plain GET is not a multipart request, so request:get-uploaded-file-headers() must be empty.
        final HttpRequest get = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME + "?inspect=fileUpload"))
                .GET()
                .build();
        final HttpResponseResult result = withHttpClient(client -> executeForStatusAndBody(client, get));
        assertEquals(200, result.statusCode());
        assertTrue("a non-multipart request yields the empty sequence: " + result.body(), result.body().contains("count=0"));
    }

    private static HttpRequest.BodyPublisher filePart(final String content, final MediaType mediaType) {
        return MoreBodyPublishers.ofMediaType(HttpRequest.BodyPublishers.ofByteArray(content.getBytes(UTF_8)), mediaType);
    }

    private String post(final MultipartBodyPublisher body, final String inspect) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/" + XQUERY_FILENAME + "?inspect=" + inspect))
                .header("Content-Type", body.mediaType().toString())
                .POST(body)
                .build();
        final HttpResponseResult result = withHttpClient(client -> executeForStatusAndBody(client, request));
        assertEquals(200, result.statusCode());
        return result.body();
    }
}
