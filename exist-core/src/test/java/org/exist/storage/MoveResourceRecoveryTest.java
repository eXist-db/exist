/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.EXistCollectionManagementService;
import org.junit.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.exist.samples.Samples.SAMPLES;

import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class MoveResourceRecoveryTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndRead() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, URISyntaxException {
        BrokerPool.FORCE_CORRUPTION = true;
        store();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read();
    }

    @Test
    public void storeAndReadAborted() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, URISyntaxException {
        BrokerPool.FORCE_CORRUPTION = true;
        storeAborted();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        readAborted();
    }

    @Test
    public void storeAndReadXmldb() throws XMLDBException, DatabaseConfigurationException, IOException, EXistException, URISyntaxException {
        // initialize xml:db driver
        final Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        BrokerPool.FORCE_CORRUPTION = true;
        xmldbStore();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        xmldbRead();
    }

    private void store() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, URISyntaxException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);

            final String sample;
            try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
                sample = InputStreamUtil.readString(is, UTF_8);
            }

            final IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, sample);
            assertNotNull(info);
            test2.store(transaction, broker, info, sample);

            final DocumentImpl doc = test2.getDocument(broker, TestConstants.TEST_XML_URI);
            assertNotNull(doc);
            broker.moveResource(transaction, doc, test, XmldbURI.create("new_test.xml"));
            broker.saveCollection(transaction, test);

            transact.commit(transaction);
        }
    }

    private void read() throws EXistException, PermissionDeniedException, SAXException, IOException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/new_test.xml"), LockMode.READ_LOCK)) {
                assertNotNull("Document should not be null", lockedDoc);
                final String data = serializer.serialize(lockedDoc.getDocument());
                assertNotNull(data);
            }

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction();
                    final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                assertNotNull(root);
                transaction.acquireCollectionLock(() -> broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(root.getURI()));
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    private void storeAborted() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, URISyntaxException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final String sample;
                try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
                    sample = InputStreamUtil.readString(is, UTF_8);
                }

                final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("new_test2.xml"), sample);
                test2.store(transaction, broker, info, sample);

                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();

            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            final DocumentImpl doc = test2.getDocument(broker, XmldbURI.create("new_test2.xml"));
            assertNotNull(doc);
            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.moveResource(transaction, doc, test, XmldbURI.create("new_test2.xml"));

            broker.saveCollection(transaction, test);

            //NOTE: do not commit the transaction

            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void readAborted() throws EXistException, PermissionDeniedException, SAXException, IOException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            try(final LockedDocument lockedDoc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append("new_test2.xml"), LockMode.READ_LOCK)) {
                assertNotNull("Document should not be null", lockedDoc);
                final String data = serializer.serialize(lockedDoc.getDocument());
                assertNotNull(data);
            }

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction();
                    final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                assertNotNull(root);
                transaction.acquireCollectionLock(() -> broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(root.getURI()));
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    private void xmldbStore() throws XMLDBException, URISyntaxException, IOException {
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final EXistCollectionManagementService mgr = (EXistCollectionManagementService)
                root.getService("CollectionManagementService", "1.0");

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null) {
            test = mgr.createCollection("test");
        }

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            test2 = mgr.createCollection("test2");
        }

        final String sample;
        try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
            sample = InputStreamUtil.readString(is, UTF_8);
        }

        final Resource res = test2.createResource("test3.xml", "XMLResource");
        res.setContent(sample);
        test2.storeResource(res);

        mgr.moveResource(XmldbURI.create(XmldbURI.ROOT_COLLECTION +  "/test2/test3.xml"),
                TestConstants.TEST_COLLECTION_URI, XmldbURI.create("new_test3.xml"));
    }

    private void xmldbRead() throws XMLDBException {
        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB +  "/test", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final Resource res = test.getResource("new_test3.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistCollectionManagementService mgr = (EXistCollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
        mgr.removeCollection(XmldbURI.create("test"));
        mgr.removeCollection(XmldbURI.create("test2"));
    }

    @After
    public void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
