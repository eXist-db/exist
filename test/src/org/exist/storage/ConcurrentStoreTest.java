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
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ConcurrentStoreTest {
    
    //TODO : revisit !
    private static String directory = "/home/wolf/xml/shakespeare";
    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    
    private static File dir = new File(directory);
    
    private BrokerPool pool;
    private Collection test, test2;

    @Test
    public synchronized void store() throws InterruptedException, EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException {
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
    }

    @Test
    public void read() throws EXistException, PermissionDeniedException, DatabaseConfigurationException {
        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            test = broker.getCollection(TEST_COLLECTION_URI.append("test1"));
            assertNotNull(test);
            test2 = broker.getCollection(TEST_COLLECTION_URI.append("test2"));
            assertNotNull(test2);
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl next = i.next();
            }
        }
    }
    
    protected void setupCollections() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction();) {

            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test1"));
            broker.saveCollection(transaction, test);

            test2 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test2);

            transact.commit(transaction);
        }
    }
    
    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    protected void tearDown() {
        BrokerPool.stopAll(false);
    }
    
    class StoreThread1 extends Thread {
        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                    final Txn transaction = transact.beginTransaction()) {
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
    	    } catch (Exception e) {
                e.printStackTrace();
    	        fail(e.getMessage()); 
            }
        }
    }
    
    class StoreThread2 extends Thread {
        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                    final Txn transaction = transact.beginTransaction()) {
                
                Iterator<DocumentImpl> i = test.iterator(broker);
                DocumentImpl doc = i.next();

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
            }
        }
    }
}