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
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
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
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.exist.samples.Samples.SAMPLES;

public class MoveCollectionRecoveryTest {

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
    public void moveToSelfSubCollection() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection src = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(src);
            broker.saveCollection(transaction, src);

            final Collection dst = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(dst);
            broker.saveCollection(transaction, dst);

            broker.moveCollection(transaction, src, dst, src.getURI().lastSegment());

            fail("expect PermissionDeniedException: Cannot move collection '/db/test' to it child collection '/db/test/test2'");

            transact.commit(transaction);
        }
    }

    private void store() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction,	TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final String sample;
            try (final InputStream is = SAMPLES.getBiblioSample()) {
                assertNotNull(is);
                sample = InputStreamUtil.readString(is, UTF_8);
            }

            final IndexInfo info = test.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, sample);
            assertNotNull(info);
            test.store(transaction, broker, info, sample);

            final Collection dest = broker.getOrCreateCollection(transaction, TestConstants.DESTINATION_COLLECTION_URI);
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);
            broker.moveCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    private void read() throws EXistException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            try(final LockedDocument lockedDoc =  broker.getXMLResource(TestConstants.DESTINATION_COLLECTION_URI.append("test3").append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK)) {
                assertNotNull("Document should not be null", lockedDoc);
                String data = serializer.serialize(lockedDoc.getDocument());
                assertNotNull(data);
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

                test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final String sample;
                try (final InputStream is = SAMPLES.getBiblioSample()) {
                    assertNotNull(is);
                    sample = InputStreamUtil.readString(is, UTF_8);
                }
                final IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, sample);
                assertNotNull(info);
                test2.store(transaction, broker, info, sample);

                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);

            final Collection dest = broker.getOrCreateCollection(transaction, TestConstants.DESTINATION_COLLECTION_URI2);
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);
            broker.moveCollection(transaction, test2, dest, XmldbURI.create("test3"));

//          Don't commit...
            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void readAborted() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            try(final LockedDocument lockedDoc = broker.getXMLResource(TestConstants.DESTINATION_COLLECTION_URI2.append("test3").append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK)) {
                assertNull("Document should be null", lockedDoc);
            }
        }
    }

    private void xmldbStore() throws XMLDBException, IOException {
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final EXistCollectionManagementService rootMgr = (EXistCollectionManagementService) root.getService("CollectionManagementService", "1.0");
        assertNotNull(rootMgr);

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null) {
            test = rootMgr.createCollection("test");
        }
        assertNotNull(test);

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            EXistCollectionManagementService testMgr = (EXistCollectionManagementService) test.getService("CollectionManagementService", "1.0");
            test2 = testMgr.createCollection("test2");
        }
        assertNotNull(test2);

        final String sample;
        try (final InputStream is = SAMPLES.getBiblioSample()) {
            assertNotNull(is);
            sample = InputStreamUtil.readString(is, UTF_8);
        }
        final Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        assertNotNull(res);
        res.setContent(sample);
        test2.storeResource(res);

        org.xmldb.api.base.Collection dest = root.getChildCollection(TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString());
        if (dest == null) {
            dest = rootMgr.createCollection(TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString());
        }
        assertNotNull(dest);

        rootMgr.move(TestConstants.TEST_COLLECTION_URI2, TestConstants.DESTINATION_COLLECTION_URI3, XmldbURI.create("test3"));
    }

    private void xmldbRead() throws XMLDBException {
        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString() + "/test3", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertNotNull(test);
        final Resource res = test.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);
    }

    @After
    public void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
