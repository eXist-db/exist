/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
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
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.exist.samples.Samples.SAMPLES;

import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class CopyCollectionRecoveryTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndRead() throws EXistException, DatabaseConfigurationException, LockException, PermissionDeniedException, SAXException, IOException {
        BrokerPool.FORCE_CORRUPTION = true;
        store();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read();
    }

    @Test
    public void storeAndReadAborted() throws EXistException, DatabaseConfigurationException, LockException, PermissionDeniedException, SAXException, IOException {
        BrokerPool.FORCE_CORRUPTION = true;
        storeAborted();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        readAborted();
    }

    @Test
    public void storeAndReadXmldb() throws DatabaseConfigurationException, XMLDBException, EXistException, IOException {
        // initialize xml:db driver
        final Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);


        BrokerPool.FORCE_CORRUPTION = false;
        xmldbStore();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        xmldbRead();
    }

    @Test(expected = PermissionDeniedException.class)
    public void copyToSubCollection() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection src = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, src);

            final Collection dst = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            broker.saveCollection(transaction, dst);

            broker.copyCollection(transaction, src, dst, src.getURI().lastSegment());

            fail("expect PermissionDeniedException: Cannot copy collection '/db/test' to it child collection '/db/test/test2'");

            transaction.commit();
        }
    }

    private void store() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test);

            final String sample = getSampleData();
            final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    sample);
            test.store(transaction, broker, info, sample);

            final Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            broker.saveCollection(transaction, dest);

            broker.copyCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    private void read() throws EXistException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), LockMode.READ_LOCK)) {
                assertNotNull("Document should not be null", lockedDoc);
                serializer.serialize(lockedDoc.getDocument());
            }
        }
    }

    private void storeAborted() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection test2;

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final String sample = getSampleData();

                IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), sample);
                test2.store(transaction, broker, info, sample);

                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();

            final Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);
            broker.copyCollection(transaction, test2, dest, XmldbURI.create("test3"));

//DO NOT COMMIT TRANSACTION
            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void readAborted() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), LockMode.READ_LOCK)) {
                assertNull("Document should not exist as copy was not committed", lockedDoc);
            }
        }
    }

    private void xmldbStore() throws XMLDBException, IOException {
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        EXistCollectionManagementService mgr = (EXistCollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
        assertNotNull(mgr);

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null) {
            test = mgr.createCollection(TestConstants.TEST_COLLECTION_URI.toString());
        }
        assertNotNull(test);

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            test2 = mgr.createCollection(TestConstants.TEST_COLLECTION_URI.append("test2").toString());
        }
        assertNotNull(test2);

        final String sample = getSampleData();
        final Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        assertNotNull(res);
        res.setContent(sample);
        test2.storeResource(res);

        org.xmldb.api.base.Collection dest = root.getChildCollection("destination");
        if (dest == null) {
            dest = mgr.createCollection("destination");
        }
        assertNotNull(dest);

        mgr.copy(TestConstants.TEST_COLLECTION_URI2, XmldbURI.ROOT_COLLECTION_URI.append("destination"), XmldbURI.create("test3"));
    }

    private void xmldbRead() throws XMLDBException {
        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/destination/test3", "admin", "");
        assertNotNull(test);
        final Resource res = test.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final EXistCollectionManagementService mgr = (EXistCollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
        assertNotNull(mgr);
        mgr.removeCollection("destination");
    }

    private String getSampleData() throws IOException {
        try (final InputStream is = SAMPLES.getBiblioSample()) {
            return InputStreamUtil.readString(is, UTF_8);
        }
    }

    @After
    public void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
