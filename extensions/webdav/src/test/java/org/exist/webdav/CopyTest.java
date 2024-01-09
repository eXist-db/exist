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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * Tests for copying a document via WebDAV.
 */
public class CopyTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

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
    public void copyXmlDocument() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String srcDocName = "webdav-copy-test.xml";
        final String srcDocContent = "<elem1>Hello there</elem1>";
        final String destDocName = "webdav-copied-test.xml";
        copyDocument(srcDocName, srcDocContent, destDocName, "application/xml");
    }

    @Test
    public void copyBinDocument() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String srcDocName = "webdav-copy-test.bin";
        final String srcDocContent = "0123456789";
        final String destDocName = "webdav-copied-test.bin";
        copyDocument(srcDocName, srcDocContent, destDocName, "application/octet-stream");
    }

    private void copyDocument(final String srcDocName, final String srcDocContent, final String destDocName, final String expectedMediaType) throws BadRequestException, HttpException, IOException, NotAuthorizedException, ConflictException, NotFoundException {
        final HostBuilder builder = new HostBuilder();
        builder.setServer("localhost");
        final int port = existWebServer.getPort();
        builder.setPort(port);
        builder.setRootPath("webdav/db");
        final Host host = builder.buildHost();

        // workaround pre-emptive auth issues of Milton Client
        final AbstractHttpClient httpClient = (AbstractHttpClient)host.getClient();
        httpClient.addRequestInterceptor(new AlwaysBasicPreAuth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD));

        final Folder folder = host.getFolder("/");
        assertNotNull(folder);

        // store document
        final java.io.File tmpStoreFile = tempFolder.newFile();
        Files.writeString(tmpStoreFile.toPath(), srcDocContent);
        assertNotNull(folder.uploadFile(srcDocName, tmpStoreFile, null));

        // retrieve document
        final Resource srcResource = folder.child(srcDocName);
        assertNotNull(srcResource);
        assertTrue(srcResource instanceof File);
        assertEquals(expectedMediaType, ((File) srcResource).contentType);
        final java.io.File tempRetrievedSrcFile = tempFolder.newFile();
        srcResource.downloadTo(tempRetrievedSrcFile, null);
        assertEquals(srcDocContent, Files.readString(tempRetrievedSrcFile.toPath()));

        // copy document
        srcResource.copyTo(folder, destDocName);

        // retrieve copied document
        final Resource destResource = folder.child(destDocName);
        assertNotNull(destResource);
        assertTrue(destResource instanceof File);
        assertEquals(expectedMediaType, ((File) destResource).contentType);
        final java.io.File tempRetrievedDestFile = tempFolder.newFile();
        destResource.downloadTo(tempRetrievedDestFile, null);
        assertEquals(srcDocContent, Files.readString(tempRetrievedDestFile.toPath()));
    }
}
