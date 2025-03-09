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
 * Tests for replacing a document via WebDAV.
 */
public class ReplaceTest {

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
    public void replaceXmlDocument() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String docName = "webdav-copy-test.xml";
        final String docContent = "<elem1>Hello there</elem1>";
        final String replacementDocContent = "<elem2>Goodbye friend</elem2>";
        replaceDocument(docName, docContent, replacementDocContent, "application/xml");
    }

    @Test
    public void replaceBinDocument() throws IOException, NotAuthorizedException, BadRequestException, HttpException, ConflictException, NotFoundException {
        final String docName = "webdav-copy-test.bin";
        final String docContent = "0123456789";
        final String replacementDocContent = "9876543210";
        replaceDocument(docName, docContent, replacementDocContent, "application/octet-stream");
    }

    private void replaceDocument(final String docName, final String docContent, final String replacementDocContent, final String expectedMediaType) throws BadRequestException, HttpException, IOException, NotAuthorizedException, ConflictException, NotFoundException {
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
        Files.writeString(tmpStoreFile.toPath(), docContent);
        assertNotNull(folder.uploadFile(docName, tmpStoreFile, null));

        // retrieve document
        final Resource srcResource = folder.child(docName);
        assertNotNull(srcResource);
        assertTrue(srcResource instanceof File);
        assertEquals(expectedMediaType, ((File) srcResource).contentType);
        final java.io.File tempRetrievedSrcFile = tempFolder.newFile();
        srcResource.downloadTo(tempRetrievedSrcFile, null);
        assertEquals(docContent, Files.readString(tempRetrievedSrcFile.toPath()));

        // replace document
        final java.io.File tmpReplacementFile = tempFolder.newFile();
        Files.writeString(tmpReplacementFile.toPath(), replacementDocContent);
        assertNotNull(folder.uploadFile(docName, tmpReplacementFile, null));

        // retrieve replaced document
        final Resource replacedResource = folder.child(docName);
        assertNotNull(replacedResource);
        assertTrue(replacedResource instanceof File);
        assertEquals(expectedMediaType, ((File) replacedResource).contentType);
        final java.io.File tempRetrievedReplacedFile = tempFolder.newFile();
        replacedResource.downloadTo(tempRetrievedReplacedFile, null);
        assertEquals(replacementDocContent, Files.readString(tempRetrievedReplacedFile.toPath()));
    }
}
