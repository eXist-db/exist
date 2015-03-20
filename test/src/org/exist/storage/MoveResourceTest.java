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

import java.io.File;
import java.io.IOException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class MoveResourceTest {

    @Test
    public void store() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        doStore();
        tearDown();
        doRead();
    }

    private void doStore() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);

            final String existHome = System.getProperty("exist.home");
            final File existDir = existHome==null ? new File(".") : new File(existHome);
            final File f = new File(existDir,"samples/shakespeare/r_and_j.xml");

            final IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toURI().toASCIIString()));
            assertNotNull(info);
            test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

            final DocumentImpl doc = test2.getDocument(broker, TestConstants.TEST_XML_URI);
            assertNotNull(doc);
            broker.moveResource(transaction, doc, test, XmldbURI.create("new_test.xml"));
            broker.saveCollection(transaction, test);

            transact.commit(transaction);
        }
    }

    private void doRead() throws EXistException, PermissionDeniedException, SAXException, IOException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/new_test.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            final String data = serializer.serialize(doc);
            assertNotNull(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
                assertNotNull(root);
                transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    @Test
    public void aborted() throws Exception {
        storeAborted();
        tearDown();
        readAborted();
    }

    private void storeAborted() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final String existHome = System.getProperty("exist.home");
                final File existDir = existHome == null ? new File(".") : new File(existHome);
                final File f = new File(existDir, "samples/shakespeare/r_and_j.xml");

                final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("new_test2.xml"),
                        new InputSource(f.toURI().toASCIIString()));
                test2.store(transaction, broker, info, new InputSource(f.toURI()
                        .toASCIIString()), false);

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

            pool.getTransactionManager().getJournal().flushToLog(true);
        }
    }

    private void readAborted() throws EXistException, PermissionDeniedException, SAXException, IOException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final DocumentImpl doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append("new_test2.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            final String data = serializer.serialize(doc);
            assertNotNull(data);

            doc.getUpdateLock().release(Lock.READ_LOCK);

            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
                assertNotNull(root);
                transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
        }
    }

    @Test
    public void xmldbStore() throws XMLDBException {
        doXmldbStore();
        tearDown();
        doXmldbRead();
    }

    private void doXmldbStore() throws XMLDBException {
        BrokerPool.FORCE_CORRUPTION = true;
        @SuppressWarnings("unused")
        final BrokerPool pool = startDB();

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl)
                root.getService("CollectionManagementService", "1.0");

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null) {
            test = mgr.createCollection("test");
        }

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            test2 = mgr.createCollection("test2");
        }

        final String existHome = System.getProperty("exist.home");
        final File existDir = existHome==null ? new File(".") : new File(existHome);
        final File f = new File(existDir,"samples/shakespeare/r_and_j.xml");

        final Resource res = test2.createResource("test3.xml", "XMLResource");
        res.setContent(f);
        test2.storeResource(res);

        mgr.moveResource(XmldbURI.create(XmldbURI.ROOT_COLLECTION +  "/test2/test3.xml"),
                TestConstants.TEST_COLLECTION_URI, XmldbURI.create("new_test3.xml"));
    }

    private void doXmldbRead() throws XMLDBException {
        BrokerPool.FORCE_CORRUPTION = false;
        @SuppressWarnings("unused")
        final BrokerPool pool = startDB();

        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB +  "/test", "admin", "");
        final Resource res = test.getResource("new_test3.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl)
                root.getService("CollectionManagementService", "1.0");
        mgr.removeCollection(XmldbURI.create("test"));
        mgr.removeCollection(XmldbURI.create("test2"));
    }

    protected BrokerPool startDB() {
        try {
            final Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);

            // initialize driver
            final Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}