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
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.CollectionManagementServiceImpl;
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

    @Test
    public void storeAndRead() throws EXistException, InstantiationException, DatabaseConfigurationException, LockException, IllegalAccessException, PermissionDeniedException, SAXException, IOException, XMLDBException, ClassNotFoundException {
        store();
        tearDown();
        read();
    }

    @Test
    public void storeAndReadAborted() throws EXistException, InstantiationException, DatabaseConfigurationException, LockException, IllegalAccessException, PermissionDeniedException, SAXException, IOException, XMLDBException, ClassNotFoundException {
        storeAborted();
        tearDown();
        readAborted();
    }

    @Test
    public void storeAndReadXmldb() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException {
        xmldbStore();
        tearDown();
        xmldbRead();
    }

    private void store() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();

        final TransactionManager transact = pool.getTransactionManager();
        
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test);

            final File f = getSampleData();
            final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    new InputSource(f.toURI().toASCIIString()));
            test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            
            final Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            broker.saveCollection(transaction, dest);
            
            broker.copyCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    private void read() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException, PermissionDeniedException, SAXException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        assertNotNull(pool);

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset(); 
            
            DocumentImpl doc = null;
            try {
                doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), Lock.READ_LOCK);
                assertNotNull("Document should not be null", doc);
                final String data = serializer.serialize(doc);
            } finally {
                if(doc != null) {
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                }
            }
        }
    }

    private void storeAborted() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            Collection test2;

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                final File f = getSampleData();

                IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
                test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

                transact.commit(transaction);
            }
            
            final Txn transaction = transact.beginTransaction();

            final Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);
            broker.copyCollection(transaction, test2, dest, XmldbURI.create("test3"));

//DO NOT COMMIT TRANSACTION
            transact.getJournal().flushToLog(true);
        }
    }

    private void readAborted() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException, PermissionDeniedException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        assertNotNull(pool);

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            final DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), Lock.READ_LOCK);
            assertNotNull("Document should be null", doc);
        }
    }

    private void xmldbStore() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        assertNotNull(pool);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl)
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

        final File f = getSampleData();
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

    private void xmldbRead() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, XMLDBException, EXistException, ClassNotFoundException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        assertNotNull(pool);

        final org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/destination/test3", "admin", "");
        assertNotNull(test);
        final Resource res = test.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        final CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl)
            root.getService("CollectionManagementService", "1.0");
        assertNotNull(mgr);
        mgr.removeCollection("destination");
    }

    private File getSampleData() {
        final String existHome = System.getProperty("exist.home");
        final File existDir = existHome == null ? new File(".") : new File(existHome);
        final File f = new File(existDir, "samples/biblio.rdf");
        assertNotNull(f);
        return f;
    }
    
    protected BrokerPool startDB() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException, DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);

        // initialize driver
        final Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
