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
package org.exist.xmldb;

import org.exist.TestUtils;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.LockOrderRecorder;
import org.exist.test.ExistWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that storing or removing a document, through each API whose transaction holds the document's WRITE_LOCK
 * until it ends, requests no collection lock while it holds a document lock: such a request could deadlock with a
 * thread that holds the collection's lock and waits for the document.
 */
@ParameterizedClass(name = "{0}")
@MethodSource("data")
public class StoreRemoveLockOrderTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String PORT_PLACEHOLDER = "${PORT}";
    private static final String TEST_COLLECTION_NAME = "testStoreRemoveLockOrder";
    private static final String TEST_COLLECTION_PATH = "/db/" + TEST_COLLECTION_NAME;
    private static final String XML_DOCUMENT = "doc.xml";
    private static final String BINARY_DOCUMENT = "doc.bin";

    @Parameter(0)
    public String apiName;

    @Parameter(1)
    public String baseUri;

    private Collection testCollection;

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("local", "xmldb:exist://"),
                Arguments.of("remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc"));
    }

    private String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @BeforeEach
    public void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootService = root.getService(CollectionManagementService.class);
        testCollection = rootService.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
        storeXml();
        storeBinary();
    }

    @AfterEach
    public void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        root.getService(CollectionManagementService.class).removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }

    @Test
    public void storeXmlResource() throws Exception {
        assertNoDeadlockProneLocks(this::storeXml);
    }

    @Test
    public void storeBinaryResource() throws Exception {
        assertNoDeadlockProneLocks(this::storeBinary);
    }

    @Test
    public void removeXmlResource() throws Exception {
        final Resource resource = testCollection.getResource(XML_DOCUMENT);
        assertNoDeadlockProneLocks(() -> testCollection.removeResource(resource));
    }

    @Test
    public void removeBinaryResource() throws Exception {
        final Resource resource = testCollection.getResource(BINARY_DOCUMENT);
        assertNoDeadlockProneLocks(() -> testCollection.removeResource(resource));
    }

    @Test
    public void storeWithXQuery() throws Exception {
        final XQueryService service = testCollection.getService(XQueryService.class);
        assertNoDeadlockProneLocks(() -> service.query("xmldb:store('" + TEST_COLLECTION_PATH + "', 'stored.xml', <stored/>)"));
    }

    @Test
    public void putWithRest() throws Exception {
        assertNoDeadlockProneLocks(() -> assertEquals(201, rest("PUT", "put.xml", "<put/>")));
    }

    @Test
    public void deleteWithRest() throws Exception {
        assertNoDeadlockProneLocks(() -> assertEquals(200, rest("DELETE", XML_DOCUMENT, null)));
    }

    private void storeXml() throws XMLDBException {
        final XMLResource document = testCollection.createResource(XML_DOCUMENT, XMLResource.class);
        document.setContent("<doc/>");
        testCollection.storeResource(document);
    }

    private void storeBinary() throws XMLDBException {
        final BinaryResource document = testCollection.createResource(BINARY_DOCUMENT, BinaryResource.class);
        document.setContent("binary".getBytes(StandardCharsets.UTF_8));
        testCollection.storeResource(document);
    }

    private static int rest(final String method, final String document, final String content) throws Exception {
        final HttpRequest.BodyPublisher body = content == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content);
        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + existWebServer.getPort() + "/rest" + TEST_COLLECTION_PATH + "/" + document))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((TestUtils.ADMIN_DB_USER + ":" + TestUtils.ADMIN_DB_PWD).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/xml")
                .method(method, body)
                .build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static void assertNoDeadlockProneLocks(final LockOrderRecorder.Operation operation) throws Exception {
        assertEquals(List.of(), LockOrderRecorder.violations(BrokerPool.getInstance(), TEST_COLLECTION_PATH, operation));
    }
}
