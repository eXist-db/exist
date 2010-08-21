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
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ConcurrentStoreTest extends TestCase {
	
    public static void main(String[] args) {
        TestRunner.run(ConcurrentStoreTest.class);
    }
    
    //TODO : revisit !
    private static String directory = "/home/wolf/xml/shakespeare";
    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    
    private static File dir = new File(directory);
    
    private BrokerPool pool;
    private Collection test, test2;
    
    public synchronized void testStore() {
    	try {
	        BrokerPool.FORCE_CORRUPTION = true;
	        pool = startDB();
	        setupCollections();
	        
	        Thread t1 = new StoreThread1();
	        t1.start();
	        
	        wait(8000);
	        
	        Thread t2 = new StoreThread2();
	        t2.start();
	        
	        t1.join();
	        t2.join();
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }
    }
    
    public void testRead() {
        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDB();
        
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            test = broker.getCollection(TEST_COLLECTION_URI.append("test1"));
            assertNotNull(test);
            test2 = broker.getCollection(TEST_COLLECTION_URI.append("test2"));
            assertNotNull(test2);
            System.out.println("Contents of collection " + test.getURI() + ":");
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl next = i.next();
                System.out.println("- " + next.getURI());
            }
	    } catch (Exception e) {            
	        fail(e.getMessage());              
        } finally {
            pool.release(broker);
        }
    }
    
    protected void setupCollections() {
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            
            System.out.println("Transaction started ...");
            
            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);
            
            test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test1"));
            broker.saveCollection(transaction, test);
            
            test2 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test2);
            
            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);            
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() {
        BrokerPool.stopAll(false);
    }
    
    class StoreThread1 extends Thread {
        
        public void run() {
            DBBroker broker = null;
            try {
                broker = pool.get(pool.getSecurityManager().getSystemSubject());
                
                TransactionManager transact = pool.getTransactionManager();
                Txn transaction = transact.beginTransaction();
                
                System.out.println("Transaction started ...");
                XMLFilenameFilter filter = new XMLFilenameFilter();
                File files[] = dir.listFiles(filter);
                
                File f;
                IndexInfo info;
                // store some documents into the test collection
                for (int i = 0; i < files.length; i++) {
                    f = files[i];
                    try {
                        info = test.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                        test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                    } catch (SAXException e) {
                        System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                    }
//                    if (i % 5 == 0) {
//                        transact.commit(transaction);
//                        transaction = transact.beginTransaction();
//                    }
                }
                
                transact.commit(transaction);
                
//              Don't commit...
                transact.getJournal().flushToLog(true);
                System.out.println("Transaction interrupted ...");
    	    } catch (Exception e) {            
    	        fail(e.getMessage()); 
            } finally {
                pool.release(broker);
            }
        }
    }
    
    class StoreThread2 extends Thread {
        public void run() {
            DBBroker broker = null;
            try {
                broker = pool.get(pool.getSecurityManager().getSystemSubject());
                
                TransactionManager transact = pool.getTransactionManager();
                Txn transaction = transact.beginTransaction();
                
                System.out.println("Transaction started ...");
                
                Iterator<DocumentImpl> i = test.iterator(broker);
                DocumentImpl doc = i.next();
                
                System.out.println("\nREMOVING DOCUMENT\n");
                test.removeXMLResource(transaction, broker, doc.getFileURI());
                
                File f = new File(dir + File.separator + "hamlet.xml");
                try {
                    IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
                    test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
                
                transact.commit(transaction);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            } finally {
                pool.release(broker);
            }
        }
    }
}