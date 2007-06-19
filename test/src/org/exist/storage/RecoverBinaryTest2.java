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

import java.io.*;
import java.util.Date;
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;

public class RecoverBinaryTest2 extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(RecoverBinaryTest2.class);
    }
    
    private static String directory = "webapp/resources";
    
    //private static File dir = new File(directory);
    
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
            
            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);            
            
            storeFiles(broker, transaction, test2);
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
        } catch (Exception e) {
            e.printStackTrace();
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
            System.out.println("testRead2() ...\n");
            pool = startDB();
            assertNotNull(pool);        
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (Iterator i = test2.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = (DocumentImpl) i.next();
                System.out.println(doc.getURI().toString());
            }
            
            BrokerPool.FORCE_CORRUPTION = true;
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("Transaction started ...");
            
            storeFiles(broker, transaction, test2);
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());              
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }
    
    public void testRead2() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            System.out.println("testRead2() ...\n");
            pool = startDB();
            assertNotNull(pool);        
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (Iterator i = test2.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = (DocumentImpl) i.next();
                System.out.println(doc.getURI().toString());
            }
            
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");
            Collection test1 = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            broker.removeCollection(transaction, test1);
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());              
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }
    
    private void storeFiles(DBBroker broker, Txn transaction, Collection test2) throws IOException, EXistException, PermissionDeniedException, LockException, TriggerException {
        
        // Get absolute path
        File dir = new File(ConfigurationHelper.getExistHome(), directory);
        
        // Get files in directory
        File files[] = dir.listFiles();
        assertNotNull("Check directory '"+dir.getAbsolutePath()+"'.",files);
        
        File f;
        
        // store some documents.
        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                assertNotNull(f);
                if (f.isFile()) {
                    XmldbURI uri = test2.getURI().append(j + "_" + f.getName());
                    InputStream is = new FileInputStream(f);
                    BinaryDocument doc = 
                        test2.addBinaryResource(transaction, broker, uri, is, MimeType.BINARY_TYPE.getName(), 
                                (int) f.length(), new Date(), new Date());
                    assertNotNull(doc);
                    is.close();
                }
            }
        }
    }
    
    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() {
        BrokerPool.stopAll(false);
    }
}
