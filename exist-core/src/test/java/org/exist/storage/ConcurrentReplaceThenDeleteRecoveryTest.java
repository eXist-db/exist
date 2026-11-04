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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Recovery redoes committed transactions and then undoes uncommitted ones, so it can only restore a consistent
 * database if no committed transaction wrote a document after an uncommitted one did. One transaction replaces a
 * document, and holds its WRITE_LOCK until it ends, as each API that stores or removes a single document now does;
 * another, which removes the document, must then wait for it to end before it can commit. After a crash, recovery
 * must find the document removed.
 */
public class ConcurrentReplaceThenDeleteRecoveryTest {

    private static final XmldbURI DOCUMENT = XmldbURI.create("replaced.xml");

    @RegisterExtension
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterEach
    public void resetCorruption() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    @Test
    void removeWaitsForTheReplaceToEnd() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        try (final DBBroker broker = broker();
                final Txn transaction = transactionManager().beginTransaction();
                final Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
            broker.saveCollection(transaction, collection);
            broker.storeDocument(transaction, DOCUMENT, new StringInputSource("<doc>first</doc>"), MimeType.XML_TYPE, collection);
            transaction.commit();
        }
        existEmbeddedServer.restart();

        // from here on, simulate a crash on shutdown
        BrokerPool.FORCE_CORRUPTION = true;

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch replaced = new CountDownLatch(1);
            final CountDownLatch commitReplace = new CountDownLatch(1);
            final Future<?> replace = executor.submit(() -> {
                try (final DBBroker broker = broker();
                        final Txn transaction = transactionManager().beginTransaction()) {
                    transaction.setHoldDocumentLocksUntilEnd(true);
                    try (final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                        broker.storeDocument(transaction, DOCUMENT, new StringInputSource("<doc>second</doc>"), MimeType.XML_TYPE, collection);
                    }
                    replaced.countDown();
                    commitReplace.await();
                    transaction.commit();
                }
                return null;
            });
            replaced.await();

            final Future<?> remove = executor.submit(() -> {
                try (final DBBroker broker = broker();
                        final Txn transaction = transactionManager().beginTransaction();
                        final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                    collection.removeXMLResource(transaction, broker, DOCUMENT);
                    transaction.commit();
                }
                return null;
            });

            // the remove can't write the document while the replace's transaction holds its lock
            assertThrows(TimeoutException.class, () -> remove.get(2, TimeUnit.SECONDS));

            commitReplace.countDown();
            replace.get(30, TimeUnit.SECONDS);
            remove.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        existEmbeddedServer.getBrokerPool().getJournalManager().get().flush(true, false);
        existEmbeddedServer.restart();
        BrokerPool.FORCE_CORRUPTION = false;

        try (final DBBroker broker = broker();
                final LockedDocument document = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append(DOCUMENT), LockMode.READ_LOCK)) {
            assertNull(document, "the removed document was restored by recovery");
            assertNotNull(broker.getCollection(TestConstants.TEST_COLLECTION_URI));
        }
    }

    private DBBroker broker() throws EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    private TransactionManager transactionManager() {
        return existEmbeddedServer.getBrokerPool().getTransactionManager();
    }
}
