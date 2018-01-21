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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
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
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.*;

public class MoveCollectionTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test(expected = PermissionDeniedException.class)
    public void moveToSelfSubCollection() throws EXistException, IOException, DatabaseConfigurationException, PermissionDeniedException, TriggerException, LockException, XMLDBException {
        final BrokerPool pool = startDb();

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

    @Test
    public void storeAndRead() throws EXistException, DatabaseConfigurationException, LockException, PermissionDeniedException, SAXException, IOException, XMLDBException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        read(pool);
    }

    @Test
    public void storeAndReadAborted() throws EXistException, DatabaseConfigurationException, LockException, PermissionDeniedException, SAXException, IOException, XMLDBException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeAborted(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        readAborted(pool);
    }

    @Test
    public void storeAndReadXmldb() throws DatabaseConfigurationException, XMLDBException, EXistException, IOException {
        BrokerPool.FORCE_CORRUPTION = false;
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

            final Collection root = broker.getOrCreateCollection(transaction,	TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final Path f = TestUtils.resolveSample("biblio.rdf");
            assertTrue(Files.exists(f));
            final IndexInfo info = test.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toUri().toASCIIString()));
            assertNotNull(info);
            test.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
            
            final Collection dest = broker.getOrCreateCollection(transaction, TestConstants.DESTINATION_COLLECTION_URI);
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);            
            broker.moveCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    private void read(final BrokerPool pool) throws EXistException, PermissionDeniedException, SAXException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final DocumentImpl doc = broker.getXMLResource(TestConstants.DESTINATION_COLLECTION_URI.append("test3").append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            String data = serializer.serialize(doc);
            assertNotNull(data);
            doc.getUpdateLock().release(LockMode.READ_LOCK);
        }
    }

    private void storeAborted(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
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

                final Path f = TestUtils.resolveSample("biblio.rdf");
                assertTrue(Files.exists(f));
                final IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toUri().toASCIIString()));
                assertNotNull(info);
                test2.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

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

    private void readAborted(final BrokerPool pool) throws EXistException, PermissionDeniedException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();            
            final DocumentImpl doc = broker.getXMLResource(TestConstants.DESTINATION_COLLECTION_URI2.append("test3").append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK);
            assertNull("Document should be null", doc);
        }
    }

    private void xmldbStore(final BrokerPool pool) throws XMLDBException {
        assertNotNull(pool);
        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final CollectionManagementServiceImpl rootMgr = (CollectionManagementServiceImpl) root.getService("CollectionManagementService", "1.0");
        assertNotNull(rootMgr);

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null) {
            test = rootMgr.createCollection("test");
        }
        assertNotNull(test);

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            CollectionManagementServiceImpl testMgr = (CollectionManagementServiceImpl) test.getService("CollectionManagementService", "1.0");
            test2 = testMgr.createCollection("test2");
        }
        assertNotNull(test2);

        final Path f = TestUtils.resolveSample("biblio.rdf");
        assertTrue(Files.exists(f));
        final Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        assertNotNull(res);
        res.setContent(f);
        test2.storeResource(res);

        org.xmldb.api.base.Collection dest = root.getChildCollection(TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString());
        if (dest == null) {
            dest = rootMgr.createCollection(TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString());
        }
        assertNotNull(dest);

        rootMgr.move(TestConstants.TEST_COLLECTION_URI2, TestConstants.DESTINATION_COLLECTION_URI3, XmldbURI.create("test3"));
    }

    private void xmldbRead(final BrokerPool pool) throws XMLDBException {
        assertNotNull(pool);
        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TestConstants.DESTINATION_COLLECTION_URI3.lastSegment().toString() + "/test3", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertNotNull(test);
        final Resource res = test.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException, XMLDBException {
        existEmbeddedServer.startDb();

        final Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }
}
