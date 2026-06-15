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
package org.exist.webdav;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * eXist-specific WebDAV round-trip tests (XML serialization edge cases).
 * Replaces the former milton-client JUnit suite; protocol compliance is covered by litmus.
 */
public class WebDavRoundTripTest {

    private static final String XML_WITH_DOCTYPE =
            """
            <!DOCTYPE bookmap PUBLIC "-//OASIS//DTD DITA BookMap//EN" "bookmap.dtd">
            <bookmap id="bookmap-1"/>""";

    private static final String XML_WITH_XMLDECL =
            """
            <?xml version="1.1" encoding="ISO-8859-1" standalone="yes"?>
            <bookmap id="bookmap-2"/>""";

    private static final String CDATA_XML = "<elem1><![CDATA[Hello there, \"Bob?\"]]></elem1>";

    private static final String XML_WITH_NAMESPACES =
            "<x:root xmlns:x=\"http://example.com/x\" xmlns:y=\"http://example.com/y\">"
                    + "<x:child y:attr=\"v\">text</x:child></x:root>";

    private static final String XML_WITH_NON_ASCII = "<doc>café — 日本語</doc>";

    @ClassRule
    public static final ExistWebServer EXIST_WEB_SERVER = new ExistWebServer(true, false, true, true);

    private static final List<String> STORED_DOCUMENTS = new ArrayList<>();

    private static String prevPropfindMethodXmlSize = null;

    @BeforeClass
    public static void setup() {
        prevPropfindMethodXmlSize = System.setProperty("org.exist.webdav.PROPFIND_METHOD_XML_SIZE", "exact");
    }

    @AfterClass
    public static void cleanup() throws Exception {
        try {
            deleteStoredDocuments();
        } finally {
            if (prevPropfindMethodXmlSize == null) {
                System.clearProperty("org.exist.webdav.PROPFIND_METHOD_XML_SIZE");
            } else {
                System.setProperty("org.exist.webdav.PROPFIND_METHOD_XML_SIZE", prevPropfindMethodXmlSize);
            }
        }
    }

    @Test
    public void getDocTypeDefault() throws Exception {
        assertEquals(XML_WITH_DOCTYPE, roundTrip("test-with-doctype.xml", XML_WITH_DOCTYPE, "application/xml"));
    }

    @Test
    public void getXmlDeclDefault() throws Exception {
        assertEquals(XML_WITH_XMLDECL, roundTrip("test-with-xmldecl.xml", XML_WITH_XMLDECL, "application/xml"));
    }

    @Test
    public void cdataWebDavApi() throws Exception {
        assertEquals(CDATA_XML, roundTrip("webdav-cdata-test.xml", CDATA_XML, "application/xml"));
    }

    @Test
    public void storeAndRetrieveBinDocument() throws Exception {
        assertEquals("0123456789", roundTrip("webdav-roundtrip-test.bin", "0123456789", "application/octet-stream"));
    }

    @Test
    public void namespacesPreserved() throws Exception {
        assertEquals(XML_WITH_NAMESPACES, roundTrip("webdav-namespaces-test.xml", XML_WITH_NAMESPACES, "application/xml"));
    }

    @Test
    public void nonAsciiPreserved() throws Exception {
        assertEquals(XML_WITH_NON_ASCII, roundTrip("webdav-non-ascii-test.xml", XML_WITH_NON_ASCII, "application/xml"));
    }

    private String roundTrip(final String docName, final String content, final String expectedMediaType) throws Exception {
        STORED_DOCUMENTS.add(docName);

        final WebDavHttpClient webDav = new WebDavHttpClient(
                EXIST_WEB_SERVER.getPort(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);

        final int putStatus = webDav.putDocument(docName, content, expectedMediaType);
        assertEquals("PUT " + docName + " failed with status " + putStatus, 201, putStatus);

        final HttpResponse<String> getResponse = webDav.getDocument(docName);
        assertEquals("GET " + docName + " failed", 200, getResponse.statusCode());
        final String contentType = getResponse.headers().firstValue("Content-Type").orElse("");
        assertTrue("Unexpected Content-Type: " + contentType, contentType.startsWith(expectedMediaType));
        return getResponse.body();
    }

    private static void deleteStoredDocuments() throws Exception {
        if (STORED_DOCUMENTS.isEmpty()) {
            return;
        }
        final WebDavHttpClient webDav = new WebDavHttpClient(
                EXIST_WEB_SERVER.getPort(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        for (final String docName : STORED_DOCUMENTS) {
            final int deleteStatus = webDav.deleteDocument(docName);
            assertTrue("DELETE " + docName + " failed with status " + deleteStatus, isSuccess(deleteStatus));
        }
        STORED_DOCUMENTS.clear();
    }

    private static boolean isSuccess(final int status) {
        return status >= 200 && status < 300;
    }
}
