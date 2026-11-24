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
import org.exist.collections.Collection;
import org.exist.collections.triggers.SAXTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.storage.txn.TxnListener;
import org.exist.test.ExistWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that each API which stores or removes one Document in a transaction of its own still holds the
 * Document's WRITE_LOCK when the transaction commits, see {@link Txn#setHoldDocumentLocksUntilEnd(boolean)}.
 *
 * A trigger on the test Collection registers a {@link TxnListener} after each store or remove; the
 * listener records, when the transaction commits, whether the Document is still locked for write.
 *
 * The remote API stores content given as a File by uploading it and calling {@code parseLocal}
 * (XML of 512 KB or more) or {@code parseLocalExt} (binary), and other content with {@code parse} or
 * {@code storeBinary}.
 */
@ParameterizedClass(name = "{0}")
@MethodSource("data")
public class HoldDocumentLockUntilEndTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String PORT_PLACEHOLDER = "${PORT}";
    private static final String TEST_COLLECTION_NAME = "testHoldDocumentLockUntilEnd";
    private static final String TEST_COLLECTION_PATH = "/db/" + TEST_COLLECTION_NAME;
    private static final String CONFIG_COLLECTION_PATH = "/db/system/config" + TEST_COLLECTION_PATH;
    private static final String XML_DOCUMENT = "doc.xml";
    private static final String BINARY_DOCUMENT = "doc.bin";

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <triggers>
                    <trigger class="%s"/>
                </triggers>
            </collection>
            """.formatted(LockedAtCommitTrigger.class.getName());

    /** Document URI → whether it was locked for write when the transaction that stored or removed it committed. */
    private static final Map<String, Boolean> LOCKED_AT_COMMIT = new ConcurrentHashMap<>();

    @Parameter(0)
    public String apiName;

    @Parameter(1)
    public String baseUri;

    @TempDir
    Path tempDir;

    private org.xmldb.api.base.Collection testCollection;

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
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootService = root.getService(CollectionManagementService.class);

        final org.xmldb.api.base.Collection configCollection = rootService.createCollection(CONFIG_COLLECTION_PATH.substring("/db/".length()));
        final XMLResource config = configCollection.createResource("collection.xconf", XMLResource.class);
        config.setContent(COLLECTION_CONFIG);
        configCollection.storeResource(config);

        testCollection = rootService.createCollection(TEST_COLLECTION_NAME);
        storeXml("<doc/>");
        storeBinary("binary".getBytes(StandardCharsets.UTF_8));
        LOCKED_AT_COMMIT.clear();
    }

    @AfterEach
    public void tearDown() throws XMLDBException {
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootService = root.getService(CollectionManagementService.class);
        rootService.removeCollection(TEST_COLLECTION_NAME);
        rootService.removeCollection(CONFIG_COLLECTION_PATH.substring("/db/".length()));
        testCollection = null;
        LOCKED_AT_COMMIT.clear();
    }

    @Test
    public void storeXmlFromMemory() throws XMLDBException {
        storeXml("<replaced/>");
        assertLockedAtCommit(XML_DOCUMENT);
    }

    @Test
    public void storeXmlFromFile() throws XMLDBException, IOException {
        // the remote API uploads a file only from 512 KB, see RemoteCollection#MAX_CHUNK_LENGTH
        storeXml(Files.writeString(tempDir.resolve(XML_DOCUMENT), "<replaced>" + "x".repeat(600 * 1024) + "</replaced>").toFile());
        assertLockedAtCommit(XML_DOCUMENT);
    }

    @Test
    public void storeBinaryFromMemory() throws XMLDBException {
        storeBinary("replaced".getBytes(StandardCharsets.UTF_8));
        assertLockedAtCommit(BINARY_DOCUMENT);
    }

    @Test
    public void storeBinaryFromFile() throws XMLDBException, IOException {
        storeBinary(Files.writeString(tempDir.resolve(BINARY_DOCUMENT), "replaced").toFile());
        assertLockedAtCommit(BINARY_DOCUMENT);
    }

    @Test
    public void removeXml() throws XMLDBException {
        final Resource resource = testCollection.getResource(XML_DOCUMENT);
        testCollection.removeResource(resource);
        assertLockedAtCommit(XML_DOCUMENT);
    }

    @Test
    public void removeBinary() throws XMLDBException {
        final Resource resource = testCollection.getResource(BINARY_DOCUMENT);
        testCollection.removeResource(resource);
        assertLockedAtCommit(BINARY_DOCUMENT);
    }

    @Test
    public void putWithRest() throws IOException, InterruptedException {
        assertEquals(201, rest("PUT", XML_DOCUMENT, "<replaced/>"));
        assertLockedAtCommit(XML_DOCUMENT);
    }

    @Test
    public void deleteWithRest() throws IOException, InterruptedException {
        assertEquals(200, rest("DELETE", XML_DOCUMENT, null));
        assertLockedAtCommit(XML_DOCUMENT);
    }

    private void storeXml(final Object content) throws XMLDBException {
        final XMLResource document = testCollection.createResource(XML_DOCUMENT, XMLResource.class);
        document.setContent(content);
        testCollection.storeResource(document);
    }

    private void storeBinary(final Object content) throws XMLDBException {
        final BinaryResource document = testCollection.createResource(BINARY_DOCUMENT, BinaryResource.class);
        document.setContent(content);
        testCollection.storeResource(document);
    }

    private static int rest(final String method, final String document, final String content) throws IOException, InterruptedException {
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

    private static void assertLockedAtCommit(final String document) {
        assertEquals(Map.of(TEST_COLLECTION_PATH + "/" + document, true), LOCKED_AT_COMMIT);
    }

    /**
     * Records, when the transaction that stored or removed a Document commits, whether the Document is still
     * locked for write.
     */
    public static class LockedAtCommitTrigger extends SAXTrigger {

        private static void recordAtCommit(final DBBroker broker, final Txn transaction, final XmldbURI documentUri) {
            transaction.registerListener(new TxnListener() {
                @Override
                public void commit() {
                    LOCKED_AT_COMMIT.put(documentUri.getCollectionPath(), broker.getBrokerPool().getLockManager().isDocumentLockedForWrite(documentUri));
                }

                @Override
                public void abort() {
                    // nothing to record
                }
            });
        }

        @Override
        public void configure(final DBBroker broker, final Txn transaction, final Collection parent, final Map<String, List<?>> parameters) throws TriggerException {
            super.configure(broker, transaction, parent, parameters);
        }

        @Override
        public void beforeCreateDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
            // nothing to record
        }

        @Override
        public void afterCreateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            recordAtCommit(broker, transaction, document.getURI());
        }

        @Override
        public void beforeUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            // nothing to record
        }

        @Override
        public void afterUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            recordAtCommit(broker, transaction, document.getURI());
        }

        @Override
        public void beforeCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) {
            // nothing to record
        }

        @Override
        public void afterCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) {
            // nothing to record
        }

        @Override
        public void beforeMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) {
            // nothing to record
        }

        @Override
        public void afterMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) {
            // nothing to record
        }

        @Override
        public void beforeDeleteDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            // nothing to record
        }

        @Override
        public void afterDeleteDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
            recordAtCommit(broker, transaction, uri);
        }

        @Override
        public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            // nothing to record
        }

        @Override
        public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
            // nothing to record
        }
    }
}
