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

import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.exceptions.NotFoundException;
import com.ettrema.httpclient.*;
import org.apache.http.impl.client.AbstractHttpClient;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class SerializationTest {

    private static final String XML_WITH_DOCTYPE =
            "<!DOCTYPE bookmap PUBLIC \"-//OASIS//DTD DITA BookMap//EN\" \"bookmap.dtd\">\n" +
            "<bookmap id=\"bookmap-1\"/>";

    private static final String XML_WITH_XMLDECL =
            "<?xml version=\"1.1\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>\n" +
            "<bookmap id=\"bookmap-2\"/>";

    private static String PREV_PROPFIND_METHOD_XML_SIZE = null;

    @ClassRule
    public static final ExistWebServer EXIST_WEB_SERVER = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    @BeforeClass
    public static void setup() {
        PREV_PROPFIND_METHOD_XML_SIZE = System.setProperty(MiltonDocument.PROPFIND_METHOD_XML_SIZE, "exact");
    }

    @AfterClass
    public static void cleanup() {
        if (PREV_PROPFIND_METHOD_XML_SIZE == null) {
            System.clearProperty(MiltonDocument.PROPFIND_METHOD_XML_SIZE);
        } else {
            System.setProperty(MiltonDocument.PROPFIND_METHOD_XML_SIZE, PREV_PROPFIND_METHOD_XML_SIZE);
        }
    }

    @Test
    public void getDocTypeDefault() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String docName = "test-with-doctype.xml";
        final HostBuilder builder = new HostBuilder();
        builder.setServer("localhost");
        final int port = EXIST_WEB_SERVER.getPort();
        builder.setPort(port);
        builder.setRootPath("webdav/db");
        final Host host = builder.buildHost();

        // workaround pre-emptive auth issues of Milton Client
        try (final AbstractHttpClient httpClient = (AbstractHttpClient)host.getClient()) {
            httpClient.addRequestInterceptor(new AlwaysBasicPreAuth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD));

            final Folder folder = host.getFolder("/");
            assertNotNull(folder);

            // store document
            final java.io.File tmpStoreFile = TEMP_FOLDER.newFile();
            Files.writeString(tmpStoreFile.toPath(), XML_WITH_DOCTYPE);
            assertNotNull(folder.uploadFile(docName, tmpStoreFile, null));

            // retrieve document
            final Resource resource = folder.child(docName);
            assertNotNull(resource);
            assertTrue(resource instanceof File);
            assertEquals("application/xml", ((File) resource).contentType);
            final java.io.File tempRetrieveFile = TEMP_FOLDER.newFile();
            resource.downloadTo(tempRetrieveFile, null);
            assertEquals(XML_WITH_DOCTYPE, Files.readString(tempRetrieveFile.toPath()));
        }
    }

    @Test
    public void getXmlDeclDefault() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String docName = "test-with-xmldecl.xml";
        final HostBuilder builder = new HostBuilder();
        builder.setServer("localhost");
        final int port = EXIST_WEB_SERVER.getPort();
        builder.setPort(port);
        builder.setRootPath("webdav/db");
        final Host host = builder.buildHost();

        // workaround pre-emptive auth issues of Milton Client
        try (final AbstractHttpClient httpClient = (AbstractHttpClient)host.getClient()) {
            httpClient.addRequestInterceptor(new AlwaysBasicPreAuth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD));

            final Folder folder = host.getFolder("/");
            assertNotNull(folder);

            // store document
            final java.io.File tmpStoreFile = TEMP_FOLDER.newFile();
            Files.writeString(tmpStoreFile.toPath(), XML_WITH_XMLDECL);
            assertNotNull(folder.uploadFile(docName, tmpStoreFile, null));

            // retrieve document
            final Resource resource = folder.child(docName);
            assertNotNull(resource);
            assertTrue(resource instanceof File);
            assertEquals("application/xml", ((File) resource).contentType);
            final java.io.File tempRetrieveFile = TEMP_FOLDER.newFile();
            resource.downloadTo(tempRetrieveFile, null);
            assertEquals(XML_WITH_XMLDECL, Files.readString(tempRetrieveFile.toPath()));
        }
    }
}
