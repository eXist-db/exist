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
package org.exist.storage.test;

import java.io.File;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class MoveCollectionTest extends TestCase {
    
    public static void main(String[] args) {
        TestRunner.run(MoveCollectionTest.class);
    }

    public void testStore() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();

        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);

            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/test");
            broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/test/test2");
            broker.saveCollection(transaction, test);
    
            File f = new File("samples/biblio.rdf");
            IndexInfo info = test.validate(transaction, broker, "test.xml",
                    new InputSource(f.toURI().toASCIIString()));
            test.store(transaction, broker, info, new InputSource(f.toURI()
                    .toASCIIString()), false);
            
            Collection dest = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/destination1");
            broker.saveCollection(transaction, dest);
            
            broker.moveCollection(transaction, test, dest, "test3");

            transact.commit(transaction);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc;
            String data;
            
            doc = broker.openDocument(DBBroker.ROOT_COLLECTION +  "/destination1/test3/test.xml", Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testStoreAborted() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();

        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);

            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/test");
            broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/test/test2");
            broker.saveCollection(transaction, test);
    
            File f = new File("samples/biblio.rdf");
            IndexInfo info = test.validate(transaction, broker, "test.xml",
                    new InputSource(f.toURI().toASCIIString()));
            test.store(transaction, broker, info, new InputSource(f.toURI()
                    .toASCIIString()), false);
            
            transact.commit(transaction);
            
            transaction = transact.beginTransaction();
            
            Collection dest = broker.getOrCreateCollection(transaction,
            		DBBroker.ROOT_COLLECTION +  "/destination2");
            broker.saveCollection(transaction, dest);
            
            broker.moveCollection(transaction, test, dest, "test3");

            pool.getTransactionManager().getJournal().flushToLog(true);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testReadAborted() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc = broker.openDocument(DBBroker.ROOT_COLLECTION +  "/destination2/test3/test.xml", Lock.READ_LOCK);
            assertNull("Document should be null", doc);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testXMLDBStore() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", "");
        CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl) 
            root.getService("CollectionManagementService", "1.0");
        org.xmldb.api.base.Collection test = root.getChildCollection("test");
        if (test == null)
            test = mgr.createCollection("test");
        
        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
        if (test2 == null) {
            mgr = (CollectionManagementServiceImpl) 
                test.getService("CollectionManagementService", "1.0");
            test2 = mgr.createCollection("test2");
        }
        
        File f = new File("samples/biblio.rdf");
        Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
        res.setContent(f);
        test2.storeResource(res);
        
        org.xmldb.api.base.Collection dest = root.getChildCollection("destination3");
        if (dest == null) {
            mgr = (CollectionManagementServiceImpl) 
                root.getService("CollectionManagementService", "1.0");
            dest = mgr.createCollection("destination3");
        }
        
        mgr.move(DBBroker.ROOT_COLLECTION +  "/test/test2", DBBroker.ROOT_COLLECTION + "/destination3", "test3");
    }
    
    public void testXMLDBRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        org.xmldb.api.base.Collection test = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION + "/destination3/test3", "admin", "");
        Resource res = test.getResource("test_xmldb.xml");
        assertNotNull("Document should not be null", res);
        System.out.println(res.getContent());
    }
    
    protected BrokerPool startDB() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            
            // initialize driver
            Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
}
