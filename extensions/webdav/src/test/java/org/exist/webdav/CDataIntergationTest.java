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

/**
 * Tests for retrieving a document containing CDATA via
 * WebDAV.
 */
public class CDataIntergationTest {

    private static final String CDATA_CONTENT = "Hello there, \"Bob?\"";
    private static final String CDATA_XML = "<elem1><![CDATA[" + CDATA_CONTENT + "]]></elem1>";

    @ClassRule
    public static final ExistWebServer EXIST_WEB_SERVER = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    private static String PREV_PROPFIND_METHOD_XML_SIZE = null;

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
    public void cdataWebDavApi() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String docName = "webdav-cdata-test.xml";
        final HostBuilder builder = new HostBuilder();
        builder.setServer("localhost");
        final int port = EXIST_WEB_SERVER.getPort();
        builder.setPort(port);
        builder.setRootPath("webdav/db");
        final Host host = builder.buildHost();

        // workaround pre-emptive auth issues of Milton Client
        final AbstractHttpClient httpClient = (AbstractHttpClient)host.getClient();
        httpClient.addRequestInterceptor(new AlwaysBasicPreAuth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD));

        final Folder folder = host.getFolder("/");
        assertNotNull(folder);

        // store document
        final java.io.File tmpStoreFile = TEMP_FOLDER.newFile();
        Files.writeString(tmpStoreFile.toPath(), CDATA_XML);
        assertNotNull(folder.uploadFile(docName, tmpStoreFile, null));

        // retrieve document
        final Resource resource = folder.child(docName);
        assertNotNull(resource);
        assertTrue(resource instanceof File);
        assertEquals("application/xml", ((File) resource).contentType);
        final java.io.File tempRetrieveFile = TEMP_FOLDER.newFile();
        resource.downloadTo(tempRetrieveFile, null);
        assertEquals(CDATA_XML, Files.readString(tempRetrieveFile.toPath()));
    }
}
