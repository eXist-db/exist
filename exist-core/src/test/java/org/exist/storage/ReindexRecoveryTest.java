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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.junit.After;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.exist.samples.Samples.SAMPLES;

import org.junit.AfterClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Test crash recovery after reindexing a collection.
 */
public class ReindexRecoveryTest {

    private static final Logger LOG = LogManager.getLogger(ReindexRecoveryTest.class);

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void reindexRecoveryTest() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException, LockException, TriggerException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeDocuments(pool);

        existEmbeddedServer.stopDb(false);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        removeCollection(pool);

        existEmbeddedServer.stopDb(false);

        restart();
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {


            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                for (final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
                    try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                        storeDocument(broker, transaction, root, XmldbURI.create(sampleName), InputStreamUtil.readString(is, UTF_8));
                    }
                }
                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            broker.reindexCollection(transaction, TestConstants.TEST_COLLECTION_URI);

            //NOTE: do not commit the transaction

            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void storeDocument(final DBBroker broker, final Txn transaction, final Collection collection,
            final XmldbURI docName, final String data) {
        try {
            broker.storeDocument(transaction, docName, new StringInputSource(data), MimeType.XML_TYPE, collection);
        } catch (final SAXException | EXistException | PermissionDeniedException | LockException | IOException e) {
            fail("Error found while parsing document: " + docName + ": " + e.getMessage());
        }
    }

    /**
     * Recover, remove the collection, then crash after commit.
     */
    private void removeCollection(final BrokerPool pool) {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            BrokerPool.FORCE_CORRUPTION = true;

            try(final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                assertNotNull(root);
                transaction.acquireCollectionLock(() -> broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(root.getURI()));
                broker.removeCollection(transaction, root);
                pool.getJournalManager().get().flush(true, false);
            }
            transact.commit(transaction);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    /**
     * Just recover.
     */
    private void restart() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDb();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK)) {
            assertNull("Removed collection does still exist", root);
        }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
