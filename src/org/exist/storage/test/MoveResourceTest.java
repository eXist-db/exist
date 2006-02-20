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
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class MoveResourceTest extends TestCase {

	public static void main(String[] args) {
		TestRunner.run(MoveResourceTest.class);
	}

	public void testStore() {
		BrokerPool.FORCE_CORRUPTION = true;
		BrokerPool pool = null;
		DBBroker broker = null;
		try {
			pool = startDB();
			assertNotNull(pool);
			broker = pool.get(SecurityManager.SYSTEM_USER);
			assertNotNull(broker);
			TransactionManager transact = pool.getTransactionManager();
			assertNotNull(transact);
			Txn transaction = transact.beginTransaction();
			assertNotNull(transaction);
			System.out.println("Transaction started ...");

			Collection root = broker.getOrCreateCollection(transaction,	DBBroker.ROOT_COLLECTION + "/test");
			assertNotNull(root);
			broker.saveCollection(transaction, root);

			Collection test2 = broker.getOrCreateCollection(transaction,	DBBroker.ROOT_COLLECTION + "/test/test2");
			assertNotNull(test2);
			broker.saveCollection(transaction, test2);

			File f = new File("samples/shakespeare/r_and_j.xml");
			assertNotNull(f);
			IndexInfo info = test2.validateXMLResource(transaction, broker, "test.xml", new InputSource(f.toURI().toASCIIString()));
			assertNotNull(info);
			test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
			
            System.out.println("Moving document test.xml to new_test.xml ...");
			broker.moveXMLResource(transaction, info.getDocument(), root, "new_test.xml");
			broker.saveCollection(transaction, root);
            
			transact.commit(transaction);
			System.out.println("Transaction commited ...");
		} catch (Exception e) {            
	        fail(e.getMessage());  			
		} finally {
			pool.release(broker);
		}
	}

	public void testRead() {
	    BrokerPool.FORCE_CORRUPTION = false;
	    BrokerPool pool = null;
	    DBBroker broker = null;
	    try {
	    	System.out.println("testRead() ...\n");
	    	pool = startDB();
	    	assertNotNull(pool);
	        broker = pool.get(SecurityManager.SYSTEM_USER);
	        assertNotNull(broker);
	        Serializer serializer = broker.getSerializer();
	        serializer.reset();
	        
	        DocumentImpl doc = broker.getXMLResource(DBBroker.ROOT_COLLECTION + "/test/new_test.xml", Lock.READ_LOCK);
	        assertNotNull("Document should not be null", doc);
	        String data = serializer.serialize(doc);
	        assertNotNull(data);
//	        System.out.println(data);
	        doc.getUpdateLock().release(Lock.READ_LOCK);
            
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");
            
            Collection root = broker.openCollection(DBBroker.ROOT_COLLECTION + "/test", Lock.WRITE_LOCK);
            assertNotNull(root);
            transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);            
            broker.removeCollection(transaction, root); 
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
	    } catch (Exception e) {            
	        fail(e.getMessage());  	
	    } finally {
	        pool.release(broker);
	    }
	}
	
//	public void testStoreAborted() {
//		BrokerPool.FORCE_CORRUPTION = true;
//		BrokerPool pool = startDB();
//
//		DBBroker broker = null;
//		try {
//			broker = pool.get(SecurityManager.SYSTEM_USER);
//
//			TransactionManager transact = pool.getTransactionManager();
//			Txn transaction = transact.beginTransaction();
//
//			System.out.println("Transaction started ...");
//
//			Collection root = broker.getOrCreateCollection(transaction,
//					DBBroker.ROOT_COLLECTION +  "/test");
//			broker.saveCollection(transaction, root);
//
//			Collection test = broker.getOrCreateCollection(transaction,
//					DBBroker.ROOT_COLLECTION + "/test/test2");
//			broker.saveCollection(transaction, test);
//
//			File f = new File("samples/shakespeare/r_and_j.xml");
//			IndexInfo info = test.validate(transaction, broker, "test2.xml",
//					new InputSource(f.toURI().toASCIIString()));
//			test.store(transaction, broker, info, new InputSource(f.toURI()
//					.toASCIIString()), false);
//			
//			transact.commit(transaction);
//			
//			transaction = transact.beginTransaction();
//			
//			broker.moveResource(transaction, info.getDocument(), root,
//					"new_test2.xml");
//			broker.saveCollection(transaction, root);
//
//			pool.getTransactionManager().getLogManager().flushToLog(true);
//		} finally {
//			pool.release(broker);
//		}
//	}
	
//	public void testReadAborted() {
//	    BrokerPool.FORCE_CORRUPTION = false;
//	    BrokerPool pool = startDB();
//	    
//	    System.out.println("testRead() ...\n");
//	    
//	    DBBroker broker = null;
//	    try {
//	        broker = pool.get(SecurityManager.SYSTEM_USER);
//	        Serializer serializer = broker.getSerializer();
//	        serializer.reset();
//	        
//	        DocumentImpl doc;
//	        String data;
//	        
//	        doc = broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/test2/test2.xml", Lock.READ_LOCK);
//	        assertNotNull("Document should not be null", doc);
//	        data = serializer.serialize(doc);
//	        System.out.println(data);
//	        doc.getUpdateLock().release(Lock.READ_LOCK);
//	        
//	        doc = broker.openDocument(DBBroker.ROOT_COLLECTION +  "/test/new_test2.xml", Lock.READ_LOCK);
//	        assertNull("Document should not exist", doc);
//	    } finally {
//	        pool.release(broker);
//	    }
//	}
//	
//	public void testXMLDBStore() {
//		BrokerPool.FORCE_CORRUPTION = false;
//	    BrokerPool pool = startDB();
//	    
//	    org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION + , "admin", "");
//	    CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl) 
//	    	root.getService("CollectionManagementService", "1.0");
//	    org.xmldb.api.base.Collection test = root.getChildCollection("test");
//	    if (test == null)
//	    	test = mgr.createCollection("test");
//	    org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
//	    if (test2 == null)
//	    	test2 = mgr.createCollection("test2");
//	    
//	    File f = new File("samples/shakespeare/r_and_j.xml");
//	    Resource res = test2.createResource("test3.xml", "XMLResource");
//	    res.setContent(f);
//	    test2.storeResource(res);
//	    
//	    mgr.moveResource(DBBroker.ROOT_COLLECTION +  "/test/test2/test3.xml", DBBroker.ROOT_COLLECTION + "/test", "new_test3.xml");
//	}
//	
//	public void testXMLDBRead() {
//		BrokerPool.FORCE_CORRUPTION = false;
//	    BrokerPool pool = startDB();
//	    
//	    org.xmldb.api.base.Collection test = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION +  "/test", "admin", "");
//	    Resource res = test.getResource("new_test3.xml");
//	    assertNotNull("Document should not be null", res);
//	    System.out.println(res.getContent());
//	}
	
	protected BrokerPool startDB() {
		try {
			Configuration config = new Configuration();
			BrokerPool.configure(1, 5, config);
			
			// initialize driver
			Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			return BrokerPool.getInstance();
		} catch (Exception e) {			
			fail(e.getMessage());
		}
		return null;
	}

	protected void tearDown() {
		BrokerPool.stopAll(false);
	}
}
