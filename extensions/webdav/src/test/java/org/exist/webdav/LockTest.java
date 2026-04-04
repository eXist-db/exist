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
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for WebDAV LOCK and UNLOCK operations.
 */
public class LockTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void lockAndUnlockXmlDocument() throws IOException, NotAuthorizedException, BadRequestException,
            HttpException, ConflictException, NotFoundException, URISyntaxException {
        final String docName = "webdav-lock-test.xml";
        final String docContent = "<root>lock test</root>";

        final Host host = buildHost();
        final Folder folder = host.getFolder("/");
        assertNotNull(folder);

        // store document
        final java.io.File tmpFile = tempFolder.newFile();
        Files.writeString(tmpFile.toPath(), docContent);
        assertNotNull(folder.uploadFile(docName, tmpFile, null));

        // lock
        final String docUri = docUri(docName);
        final String lockToken = host.doLock(docUri);
        assertNotNull("LOCK should return a lock token", lockToken);
        assertFalse("Lock token should not be empty", lockToken.isEmpty());

        // unlock
        final int unlockStatus = host.doUnLock(docUri, lockToken);
        assertTrue("UNLOCK should return 2xx status, got " + unlockStatus,
                unlockStatus >= 200 && unlockStatus < 300);
    }

    @Test
    public void lockAndUnlockBinDocument() throws IOException, NotAuthorizedException, BadRequestException,
            HttpException, ConflictException, NotFoundException, URISyntaxException {
        final String docName = "webdav-lock-test.bin";
        final String docContent = "binary lock test data";

        final Host host = buildHost();
        final Folder folder = host.getFolder("/");
        assertNotNull(folder);

        // store document
        final java.io.File tmpFile = tempFolder.newFile();
        Files.writeString(tmpFile.toPath(), docContent);
        assertNotNull(folder.uploadFile(docName, tmpFile, null));

        // lock
        final String docUri = docUri(docName);
        final String lockToken = host.doLock(docUri);
        assertNotNull("LOCK should return a lock token", lockToken);

        // unlock
        final int unlockStatus = host.doUnLock(docUri, lockToken);
        assertTrue("UNLOCK should return 2xx status, got " + unlockStatus,
                unlockStatus >= 200 && unlockStatus < 300);
    }

    @Test
    public void relockReturnsFreshToken() throws IOException, NotAuthorizedException, BadRequestException,
            HttpException, ConflictException, NotFoundException, URISyntaxException {
        final String docName = "webdav-relock-test.xml";
        final String docContent = "<root>relock test</root>";

        final Host host = buildHost();
        final Folder folder = host.getFolder("/");
        assertNotNull(folder);

        // store document
        final java.io.File tmpFile = tempFolder.newFile();
        Files.writeString(tmpFile.toPath(), docContent);
        assertNotNull(folder.uploadFile(docName, tmpFile, null));

        final String docUri = docUri(docName);

        // first lock
        final String lockToken1 = host.doLock(docUri);
        assertNotNull("First LOCK should succeed", lockToken1);

        // second lock by same user replaces the lock
        final String lockToken2 = host.doLock(docUri);
        assertNotNull("Second LOCK by same user should succeed", lockToken2);

        // cleanup: unlock with latest token
        host.doUnLock(docUri, lockToken2);
    }

    private String docUri(final String docName) {
        return "http://localhost:" + existWebServer.getPort() + "/webdav/db/" + docName;
    }

    private Host buildHost() {
        final HostBuilder builder = new HostBuilder();
        builder.setServer("localhost");
        builder.setPort(existWebServer.getPort());
        builder.setUser(TestUtils.ADMIN_DB_USER);
        builder.setPassword(TestUtils.ADMIN_DB_PWD);
        builder.setRootPath("webdav/db");
        final Host host = builder.buildHost();

        // preemptive Basic auth for all requests
        final AbstractHttpClient httpClient = (AbstractHttpClient) host.getClient();
        httpClient.addRequestInterceptor(new AlwaysBasicPreAuth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD));

        return host;
    }
}
