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
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.LockOrderRecorder;
import org.exist.storage.lock.LockOrderRecorder.Violation;
import org.exist.test.ExistWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that WebDAV COPY and MOVE of a document or a Collection request no lock that can deadlock with
 * concurrent readers or writers; see {@link LockOrderRecorder}.
 */
public class WebDavMoveCopyLockOrderTest {

    @RegisterExtension
    public static final ExistWebServer EXIST_WEB_SERVER = new ExistWebServer(true, false, true, true);

    private static final String TEST_COLLECTION = "testWebDavMoveCopyLockOrder";
    private static final String SOURCE = TEST_COLLECTION + "/source";
    private static final String TARGET = TEST_COLLECTION + "/target";
    private static final String DOCUMENT = SOURCE + "/doc.xml";

    private WebDavHttpClient webDav;

    @Test
    public void copyDocument() throws Exception {
        assertEquals(List.of(), record("COPY", DOCUMENT, TARGET + "/doc.xml"));
    }

    @Test
    public void moveDocument() throws Exception {
        assertEquals(List.of(), record("MOVE", DOCUMENT, TARGET + "/doc.xml"));
    }

    @Test
    public void copyCollection() throws Exception {
        assertEquals(List.of(), record("COPY", SOURCE, TARGET + "/source"));
    }

    @Test
    public void copyCollectionIntoParentOfSource() throws Exception {
        assertEquals(List.of(), record("COPY", SOURCE, TEST_COLLECTION + "/source-copy"));
    }

    @Test
    public void moveCollection() throws Exception {
        assertEquals(List.of(), record("MOVE", SOURCE, TARGET + "/source"));
    }

    private List<Violation> record(final String method, final String source, final String destination) throws Exception {
        final List<Integer> status = new ArrayList<>();
        final List<Violation> violations = LockOrderRecorder.violations(BrokerPool.getInstance(), "/db/" + TEST_COLLECTION,
                () -> status.add(webDav.copyOrMove(method, source, destination)));
        assertTrue(status.get(0) == HttpURLConnection.HTTP_CREATED || status.get(0) == HttpURLConnection.HTTP_NO_CONTENT,
                method + " " + source + " to " + destination + " failed with status " + status.get(0));
        return violations;
    }

    @BeforeEach
    public void setUp() throws Exception {
        webDav = new WebDavHttpClient(EXIST_WEB_SERVER.getPort(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertEquals(HttpURLConnection.HTTP_CREATED, webDav.makeCollection(TEST_COLLECTION));
        assertEquals(HttpURLConnection.HTTP_CREATED, webDav.makeCollection(SOURCE));
        assertEquals(HttpURLConnection.HTTP_CREATED, webDav.makeCollection(TARGET));
        assertEquals(HttpURLConnection.HTTP_CREATED, webDav.putDocument(DOCUMENT, "<doc/>", "application/xml"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        webDav.deleteDocument(TEST_COLLECTION);
    }
}
