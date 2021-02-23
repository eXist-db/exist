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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import org.exist.xmldb.UserManagementService;
import java.io.IOException;
import org.exist.http.RESTTest;
import org.exist.xmldb.EXistResource;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.test.XmlStringDiffMatcher.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * test HTTP PATCH capabilities of ExistServlet
 */
public class PatchTest extends RESTTest {

    private final static String XQUERY_FILENAME = "test-patch.xql";
    private final static String XML_FILENAME = "test-patch.xml";

    private static Collection root;
    private static XMLResource xml;
    private static BinaryResource bin;

    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");

        bin = (BinaryResource)root.createResource(XQUERY_FILENAME, BinaryResource.RESOURCE_TYPE);
        ((EXistResource) bin).setMimeType("application/xquery");
        bin.setContent("xquery version \"3.1\";" +
                "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";" +
                "declare option output:method \"xml\";" +
                "declare option output:media-type \"application/xml\";" +
                "declare option output:indent \"no\";" +
                "<request><method>{request:get-method()}</method><data>{request:get-data()}</data></request>");
        root.storeResource(bin);
        ums.chmod(bin, 0777);

        xml = (XMLResource)root.createResource(XML_FILENAME, XMLResource.RESOURCE_TYPE);
        xml.setContent("<root/>");
        root.storeResource(xml);
        ums.chmod(xml, 0777);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        root.removeResource(bin);
        root.removeResource(xml);
    }

    @Test
    public void patchBinary() throws IOException {
        final byte[] testData = "12345".getBytes(UTF_8);

        Request patch = Request.Patch(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData, ContentType.APPLICATION_OCTET_STREAM);

        testRequest(patch, encodeBase64String(testData));
    }

    @Test
    public void patchXml() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        Request patch = Request.Patch(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8), ContentType.TEXT_XML);

        testRequest(patch, testData);
    }

    @Test
    public void patchString() throws IOException {
        final String testData = "12345";

        Request patch = Request.Patch(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8));

        testRequest(patch, testData);
    }

    @Test
    public void patchCollectionNotAllowed() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        Request patch = Request.Patch(getCollectionRootUri())
                .bodyByteArray(testData.getBytes(UTF_8), ContentType.TEXT_XML);

        assertMethodNotAllowed(patch);
    }

    @Test
    public void patchXmlResourceNotAllowed() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        Request patch = Request.Patch(getCollectionRootUri() + "/" + XML_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8), ContentType.TEXT_XML);

        assertMethodNotAllowed(patch);
    }

    private void testRequest(Request method, String expectedData) throws IOException {
        final HttpResponse response = method.execute().returnResponse();
        final Matcher<String> valueMatcher = hasSimilarXml(
                "<request><method>PATCH</method><data>" + expectedData + "</data></request>");

        assertHTTPStatusCode(HttpStatus.SC_OK, response);

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            response.getEntity().writeTo(os);

            final String actualResponse = new String(os.toByteArray());
            assertThat(actualResponse, valueMatcher);
        }
    }

    private void assertHTTPStatusCode (final int code, final HttpResponse response) {
        assertEquals(code, response.getStatusLine().getStatusCode());
    }

    private void assertMethodNotAllowed (Request req) throws IOException {
        final HttpResponse response = req.execute().returnResponse();
        assertHTTPStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED, response);
    }
}
