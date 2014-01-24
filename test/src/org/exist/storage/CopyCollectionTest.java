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
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;

public class CopyCollectionTest {

    @Test
    public void storeAndRead() {
        store();
        tearDown();
        read();
    }

    @Test
    public void storeAndReadAborted() {
        storeAborted();
        tearDown();
        readAborted();
    }

    @Test
    public void storeAndReadXmldb() {
        xmldbStore();
        tearDown();
        xmldbRead();
    }

    private void store() {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();
        DBBroker broker = null;
        
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test);
    
            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            File f = new File(existDir,"samples/biblio.rdf");
            IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    new InputSource(f.toURI().toASCIIString()));
            test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            
            Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            broker.saveCollection(transaction, dest);
            
            broker.copyCollection(transaction, test, dest, XmldbURI.create("test3"));

            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
	    } catch (Exception e) {            
	        fail(e.getMessage());              
        } finally {
            pool.release(broker);
        }
    }

    private void read() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        
        try {
        	System.out.println("testRead() ...\n");  
        	pool = startDB();
        	assertNotNull(pool);
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
            Serializer serializer = broker.getSerializer();
            serializer.reset(); 
            
            DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            String data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);                
	    } catch (Exception e) {  
	    	e.printStackTrace();
	        fail(e.getMessage());              
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    private void storeAborted() {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = null;

        DBBroker broker = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("test2"));
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);
    
            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            File f = new File(existDir,"samples/biblio.rdf");
            assertNotNull(f);
            IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
            test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
            transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");
            
            Collection dest = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("destination"));
            assertNotNull(dest);
            broker.saveCollection(transaction, dest);            
            broker.copyCollection(transaction, test2, dest, XmldbURI.create("test3"));

//          Don't commit...
            transact.getJournal().flushToLog(true);
            System.out.println("Transaction interrupted ...");            
	    } catch (Exception e) {    
	    	e.printStackTrace();
	        fail(e.getMessage());              
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    private void readAborted() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
       
        try {
        	System.out.println("testReadAborted() ...\n");
        	pool = startDB();
        	assertNotNull(pool);
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
        	assertNotNull(broker);
        	
            Serializer serializer = broker.getSerializer();
            serializer.reset();            
            DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("destination/test3/test.xml"), Lock.READ_LOCK);
            assertNotNull("Document should be null", doc);
	    } catch (Exception e) {
	    	e.printStackTrace();
	        fail(e.getMessage());              
        } finally {
            pool.release(broker);
        }
    }

    private void xmldbStore() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
	        org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
	        assertNotNull(root);
	        CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl) 
	            root.getService("CollectionManagementService", "1.0");
	        assertNotNull(mgr);
	        org.xmldb.api.base.Collection test = root.getChildCollection("test");
	        if (test == null)
	            test = mgr.createCollection(TestConstants.TEST_COLLECTION_URI.toString());
	        assertNotNull(test);
	        org.xmldb.api.base.Collection test2 = test.getChildCollection("test2");
	        if (test2 == null)
	            test2 = mgr.createCollection(TestConstants.TEST_COLLECTION_URI.append("test2").toString());
	        assertNotNull(test2);
	        
                String existHome = System.getProperty("exist.home");
                File existDir = existHome==null ? new File(".") : new File(existHome);
	        File f = new File(existDir,"samples/biblio.rdf");
	        assertNotNull(f);
	        Resource res = test2.createResource("test_xmldb.xml", "XMLResource");
	        assertNotNull(res);
	        res.setContent(f);
	        test2.storeResource(res);
	        
	        org.xmldb.api.base.Collection dest = root.getChildCollection("destination");	        
	        if (dest == null)
	            dest = mgr.createCollection("destination");
	        assertNotNull(dest);
	        
	        mgr.copy(TestConstants.TEST_COLLECTION_URI2, XmldbURI.ROOT_COLLECTION_URI.append("destination"), XmldbURI.create("test3"));
	    } catch (Exception e) {            
	        fail(e.getMessage()); 
	    }
    }

    private void xmldbRead() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        
        try {
        	pool = startDB();
        	assertNotNull(pool);
	        org.xmldb.api.base.Collection test = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/destination/test3", "admin", "");
	        assertNotNull(test);
	        Resource res = test.getResource("test_xmldb.xml");
	        assertNotNull("Document should not be null", res);
	        System.out.println(res.getContent());
	        
	        org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
	        assertNotNull(root);
	        CollectionManagementServiceImpl mgr = (CollectionManagementServiceImpl) 
	            root.getService("CollectionManagementService", "1.0");
	        assertNotNull(mgr);
	        mgr.removeCollection("destination");
	    } catch (Exception e) {            
	        fail(e.getMessage()); 
	    }        
    }
    
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

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
