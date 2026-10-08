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
import org.exist.storage.lock.LockOrderRecorder.Violation;
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
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Checks that moving and copying a resource or a Collection through the XML:DB API, locally and over XML-RPC,
 * requests no lock that can deadlock with concurrent readers or writers; see {@link LockOrderRecorder}.
 */
@ParameterizedClass(name = "{0}")
@MethodSource("data")
public class MoveCopyLockOrderTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    private static final String TEST_COLLECTION_NAME = "testMoveCopyLockOrder";
    private static final String TEST_COLLECTION_PATH = "/db/" + TEST_COLLECTION_NAME;
    private static final String SOURCE_COLLECTION_NAME = "source";
    private static final String TARGET_COLLECTION_NAME = "target";
    private static final String SOURCE_COLLECTION_PATH = TEST_COLLECTION_PATH + "/" + SOURCE_COLLECTION_NAME;
    private static final String TARGET_COLLECTION_PATH = TEST_COLLECTION_PATH + "/" + TARGET_COLLECTION_NAME;
    private static final String DOCUMENT_NAME = "doc.xml";

    @Parameter(0)
    public String apiName;

    @Parameter(1)
    public String baseUri;

    private Collection sourceCollection;

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("local", "xmldb:exist://"),
                Arguments.of("remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc"));
    }

    private String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void moveResource() throws Exception {
        assertNoDeadlockProneLocks(service ->
                service.moveResource(XmldbURI.create(DOCUMENT_NAME), XmldbURI.create(TARGET_COLLECTION_PATH), null));
    }

    @Test
    public void copyResource() throws Exception {
        assertNoDeadlockProneLocks(service ->
                service.copyResource(XmldbURI.create(DOCUMENT_NAME), XmldbURI.create(TARGET_COLLECTION_PATH), null));
    }

    @Test
    public void copyCollection() throws Exception {
        assertNoDeadlockProneLocks(service ->
                service.copy(XmldbURI.create(SOURCE_COLLECTION_PATH), XmldbURI.create(TARGET_COLLECTION_PATH), null));
    }

    /**
     * Copying a Collection into the parent of the source, as {@code xmldb:copy-collection} does when copying a
     * top-level Collection into /db: the source's READ_LOCK puts an INTENTION_READ on the destination.
     */
    @Test
    public void copyCollectionIntoParentOfSource() throws Exception {
        assertNoDeadlockProneLocks(service ->
                service.copy(XmldbURI.create(SOURCE_COLLECTION_PATH), XmldbURI.create(TEST_COLLECTION_PATH), XmldbURI.create("source-copy")));
    }

    @Test
    public void moveCollection() throws Exception {
        assertNoDeadlockProneLocks(service ->
                service.move(XmldbURI.create(SOURCE_COLLECTION_PATH), XmldbURI.create(TARGET_COLLECTION_PATH), null));
    }

    /**
     * Moving a Collection into an ancestor of the source: the source's WRITE_LOCK puts an INTENTION_WRITE on the
     * destination.
     */
    @Test
    public void moveCollectionIntoAncestorOfSource() throws Exception {
        final EXistCollectionManagementService sourceService = sourceCollection.getService(EXistCollectionManagementService.class);
        assertNotNull(sourceService.createCollection("inner"));
        assertNoDeadlockProneLocks(service ->
                service.move(XmldbURI.create(SOURCE_COLLECTION_PATH + "/inner"), XmldbURI.create(TEST_COLLECTION_PATH), XmldbURI.create("inner-moved")));
    }

    /**
     * Copying a Collection into one of its own sub-Collections is refused, and must not wait for a lock the
     * thread itself holds on the way there.
     */
    @Test
    public void copyCollectionIntoItselfIsRefused() throws Exception {
        final EXistCollectionManagementService service = sourceCollection.getService(EXistCollectionManagementService.class);
        assertNotNull(service.createCollection("child"));
        assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
                assertThrows(XMLDBException.class, () ->
                        service.copy(XmldbURI.create(SOURCE_COLLECTION_PATH), XmldbURI.create(SOURCE_COLLECTION_PATH + "/child"), null)));
    }

    private void assertNoDeadlockProneLocks(final XmldbOperation operation) throws Exception {
        assertEquals(List.of(), record(operation));
    }

    private List<Violation> record(final XmldbOperation operation) throws Exception {
        final EXistCollectionManagementService service = sourceCollection.getService(EXistCollectionManagementService.class);
        return LockOrderRecorder.violations(BrokerPool.getInstance(), TEST_COLLECTION_PATH, () -> operation.run(service));
    }

    @FunctionalInterface
    private interface XmldbOperation {
        void run(EXistCollectionManagementService service) throws XMLDBException;
    }

    @BeforeEach
    public void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootService = root.getService(CollectionManagementService.class);
        final Collection testCollection = rootService.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);

        final CollectionManagementService service = testCollection.getService(CollectionManagementService.class);
        sourceCollection = service.createCollection(SOURCE_COLLECTION_NAME);
        assertNotNull(service.createCollection(TARGET_COLLECTION_NAME));

        final XMLResource document = sourceCollection.createResource(DOCUMENT_NAME, XMLResource.class);
        document.setContent("<doc/>");
        sourceCollection.storeResource(document);
    }

    @AfterEach
    public void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        sourceCollection = null;
    }
}
