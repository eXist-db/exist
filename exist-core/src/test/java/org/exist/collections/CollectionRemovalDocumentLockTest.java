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
package org.exist.collections;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Element;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that a Collection isn't removed while a document in it is READ_LOCKed.
 *
 * <p>A reader takes the Collection's lock only to find the document: {@code getXMLResource} releases it once
 * it holds the document's lock, and reads the document under that lock alone. Removing the Collection must
 * therefore wait for the document's lock too, or it deletes the document while it is being read.</p>
 */
public class CollectionRemovalDocumentLockTest {

    private static final XmldbURI COLLECTION = XmldbURI.create("/db/removal-document-lock");
    private static final XmldbURI DOCUMENT = COLLECTION.append("doc.xml");
    private static final String CONTENT = """
            <document>
                <chapter><title>Chapter 1</title></chapter>
                <chapter><title>Chapter 2</title></chapter>
            </document>
            """;

    /** How long the removal may take without being blocked, before we conclude that it is blocked. */
    private static final long BLOCKED_SECONDS = 2;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    void storeDocument() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
            broker.saveCollection(transaction, collection);
            broker.storeDocument(transaction, DOCUMENT.lastSegment(), new StringInputSource(CONTENT), MimeType.XML_TYPE, collection);
            transaction.commit();
        }
    }

    @Test
    void removeCollectionWaitsForADocumentReadLock() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Future<?> removal;
            try (final LockedDocument lockedDocument = broker.getXMLResource(DOCUMENT, LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);
                assertEquals("document", lockedDocument.getDocument().getDocumentElement().getNodeName());

                // the reader now holds only the document's READ_LOCK, as while it serializes or queries the document
                removal = executor.submit(() -> {
                    removeCollection(pool);
                    return null;
                });

                try {
                    removal.get(BLOCKED_SECONDS, TimeUnit.SECONDS);
                    fail("The Collection was removed while its document was READ_LOCKed; the reader now sees "
                            + whatTheReaderSees(lockedDocument.getDocument()));
                } catch (final TimeoutException e) {
                    // expected: the removal waits for the document's lock
                }
            }

            // once the reader is done, the removal goes ahead
            removal.get(30, TimeUnit.SECONDS);
            assertNull(broker.getCollection(COLLECTION));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void removeCollection(final BrokerPool pool) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection collection = broker.openCollection(COLLECTION, LockMode.WRITE_LOCK)) {
                assertNotNull(collection);
                broker.removeCollection(transaction, collection);
            }
            transaction.commit();
        }
    }

    private static String whatTheReaderSees(final DocumentImpl document) {
        try {
            final Element element = document.getDocumentElement();
            return element == null ? "no document element" : "the document element <" + element.getNodeName() + ">";
        } catch (final RuntimeException e) {
            return e.toString();
        }
    }
}
