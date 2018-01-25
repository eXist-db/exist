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
import java.nio.file.Path;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.EXistCollectionManagementService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.assertNotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MoveResourceTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void storeAndRead() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        read(pool);
    }

    @Test
    public void storeAndReadAborted() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeAborted(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        readAborted(pool);
    }

    @Test
    public void storeAndReadXmldb() throws XMLDBException, DatabaseConfigurationException, IOException, EXistException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        xmldbStore(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        xmldbRead(pool);
    }

    private void store(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);

            final Path f = TestUtils.resolveShakespeareSample("r_and_j.xml");

            final IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toUri().toASCIIString()));
            assertNotNull(info);
            test2.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

            final DocumentImpl doc = test2.getDocument(broker, TestConstants.TEST_XML_URI);
            assertNotNull(doc);
            broker.moveResource(transaction, doc, test, XmldbURI.create("new_test.xml"));
            broker.saveCollection(transaction, test);

            transact.commit(transaction);
        }
    }

    private void read(final BrokerPool pool) throws EXistException, PermissionDeniedException, SAXException, IOException, LockException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/new_test.xml"), LockMode.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            final String data = serializer.serialize(doc);
            assertNotNull(data);
            doc.getUpdateLock().release(LockMode.READ_LOCK);

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK);
                assertNotNull(root);
                transaction.registerLock(root.getLock(), LockMode.WRITE_LOCK);
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    private void storeAborted(final BrokerPool pool) throws Exception {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final Path f = TestUtils.resolveShakespeareSample("r_and_j.xml");

                final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("new_test2.xml"),
                        new InputSource(f.toUri().toASCIIString()));
                test2.store(transaction, broker, info, new InputSource(f.toUri()
                        .toASCIIString()));

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

    private void readAborted(final BrokerPool pool) throws EXistException, PermissionDeniedException, SAXException, IOException, LockException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final DocumentImpl doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append("new_test2.xml"), LockMode.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            final String data = serializer.serialize(doc);
            assertNotNull(data);

            doc.getUpdateLock().release(LockMode.READ_LOCK);

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK);
                assertNotNull(root);
                transaction.registerLock(root.getLock(), LockMode.WRITE_LOCK);
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    private void xmldbStore(final BrokerPool pool) throws XMLDBException {
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

        final Path f = TestUtils.resolveShakespeareSample("r_and_j.xml");

        final Resource res = test2.createResource("test3.xml", "XMLResource");
        res.setContent(f);
        test2.storeResource(res);

        mgr.moveResource(XmldbURI.create(XmldbURI.ROOT_COLLECTION +  "/test2/test3.xml"),
                TestConstants.TEST_COLLECTION_URI, XmldbURI.create("new_test3.xml"));
    }

    private void xmldbRead(final BrokerPool pool) throws XMLDBException {
        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB +  "/test", "admin", "");
        final Resource res = test.getResource("new_test3.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final EXistCollectionManagementService mgr = (EXistCollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
        mgr.removeCollection(XmldbURI.create("test"));
        mgr.removeCollection(XmldbURI.create("test2"));
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException, ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        existEmbeddedServer.startDb();

        // initialize driver
        final Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

    @AfterClass
    public static void cleanup() throws IOException, DatabaseConfigurationException {
        TestUtils.cleanupDataDir();
    }
}
