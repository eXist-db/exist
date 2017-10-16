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
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
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

public class CopyCollectionTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void storeAndRead() throws EXistException, InstantiationException, DatabaseConfigurationException, LockException, IllegalAccessException, PermissionDeniedException, SAXException, IOException, XMLDBException, ClassNotFoundException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        read(pool);
    }

    @Test
    public void storeAndReadAborted() throws EXistException, InstantiationException, DatabaseConfigurationException, LockException, IllegalAccessException, PermissionDeniedException, SAXException, IOException, XMLDBException, ClassNotFoundException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeAborted(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        readAborted(pool);
    }

    @Test
    public void storeAndReadXmldb() throws DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, IOException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDb();

        xmldbStore(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        xmldbRead(pool);
    }

    @Test(expected = PermissionDeniedException.class)
    public void copyToSubCollection() throws Exception {
        final BrokerPool pool = startDb();

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

    private void store(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final TransactionManager transact = pool.getTransactionManager();
        
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test);

            final Path f = getSampleData();
            final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    new InputSource(f.toUri().toASCIIString()));
            test.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
            
            final Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            broker.saveCollection(transaction, dest);
            
            broker.copyCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    private void read(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException, PermissionDeniedException, SAXException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset(); 
            
            DocumentImpl doc = null;
            try {
                doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), LockMode.READ_LOCK);
                assertNotNull("Document should not be null", doc);
                final String data = serializer.serialize(doc);
            } finally {
                if(doc != null) {
                    doc.getUpdateLock().release(LockMode.READ_LOCK);
                }
            }
        }
    }

    private void storeAborted(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException, PermissionDeniedException, IOException, SAXException, LockException {
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

                final Path f = getSampleData();

                IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toUri().toASCIIString()));
                test2.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

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

    private void readAborted(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException, PermissionDeniedException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            final DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), LockMode.READ_LOCK);
            assertNotNull("Document should be null", doc);
        }
    }

    private void xmldbStore(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException {
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

        final Path f = getSampleData();
        final Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        assertNotNull(res);
        res.setContent(f);
        test2.storeResource(res);

        org.xmldb.api.base.Collection dest = root.getChildCollection("destination");
        if (dest == null) {
            dest = mgr.createCollection("destination");
        }
        assertNotNull(dest);

        mgr.copy(TestConstants.TEST_COLLECTION_URI2, XmldbURI.ROOT_COLLECTION_URI.append("destination"), XmldbURI.create("test3"));
    }

    private void xmldbRead(final BrokerPool pool) throws DatabaseConfigurationException, XMLDBException, EXistException {
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

    private Path getSampleData() {
        return TestUtils.resolveSample("biblio.rdf");
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException, XMLDBException {
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
}
