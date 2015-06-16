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
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MoveCollectionTest {

    @Test
    public void store() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction,	TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test);
            broker.saveCollection(transaction, test);
    
            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            File f = new File(existDir,"samples/biblio.rdf");
            assertNotNull(f);
            IndexInfo info = test.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toURI().toASCIIString()));
            assertNotNull(info);
            test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            
            Collection dest = broker.getOrCreateCollection(transaction, TestConstants.DESTINATION_COLLECTION_URI);
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);            
            broker.moveCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
        }
    }

    @Test
    public void read() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, PermissionDeniedException, SAXException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("/destination1/test3/test.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            String data = serializer.serialize(doc);
            assertNotNull(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }

    @Test
    public void storeAborted() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            Collection test2;

            try(final Txn transaction = transact.beginTransaction()) {

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                assertNotNull(test2);
                broker.saveCollection(transaction, test2);

                String existHome = System.getProperty("exist.home");
                File existDir = existHome == null ? new File(".") : new File(existHome);
                File f = new File(existDir, "samples/biblio.rdf");
                assertNotNull(f);
                IndexInfo info = test2.validateXMLResource(transaction, broker, TestConstants.TEST_XML_URI, new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info);
                test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

                transact.commit(transaction);
            }
            
            final Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection dest = broker.getOrCreateCollection(transaction, TestConstants.DESTINATION_COLLECTION_URI2);
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);            
            broker.moveCollection(transaction, test2, dest, XmldbURI.create("test3"));

//          Don't commit...
            pool.getTransactionManager().getJournal().flushToLog(true);
	    }
    }

    @Test
    public void readAborted() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException, PermissionDeniedException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            Serializer serializer = broker.getSerializer();
            serializer.reset();            
            DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination2/test3/test.xml"), Lock.READ_LOCK);
            assertNull("Document should be null", doc);
        }
    }

    @Test
    public void xmldbStore() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();

        assertNotNull(pool);
        org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        assertNotNull(root);
        CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl)
            root.getService("CollectionManagementService", "1.0");
        assertNotNull(mgr);

        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null)
            test = mgr.createCollection("test");
        assertNotNull(test);

        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null)
            test2 = mgr.createCollection("test2");
        assertNotNull(test2);

            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
        File f = new File(existDir,"samples/biblio.rdf");
        assertNotNull(f);
        Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        assertNotNull(res);
        res.setContent(f);
        test2.storeResource(res);

        org.xmldb.api.base.Collection dest = root.getChildCollection("destination3");
        if (dest == null)
            dest = mgr.createCollection("destination3");
        assertNotNull(dest);

        mgr.move(TestConstants.TEST_COLLECTION_URI2, TestConstants.DESTINATION_COLLECTION_URI3, XmldbURI.create("test3"));
    }

    @Test
    public void xmldbRead() throws IllegalAccessException, DatabaseConfigurationException, InstantiationException, ClassNotFoundException, XMLDBException, EXistException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();

        assertNotNull(pool);
        org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/destination3/test3", "admin", "");
        assertNotNull(test);
        Resource res = test.getResource("test_xmldb.xml");
        assertNotNull(res);
        assertNotNull("Document should not be null", res);
    }
    
    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException, ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);

        // initialize driver
        Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
