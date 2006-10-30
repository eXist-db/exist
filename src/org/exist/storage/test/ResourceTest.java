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

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;

/**
 *  0 byte binary files cannot be retrieved from database. This test
 * displays the error.
 *
 * @author wessels
 */
public class ResourceTest extends TestCase {
    
    private static String EMPTY_BINARY_FILE="";
    private static XmldbURI DOCUMENT_NAME_URI = XmldbURI.create("empty.txt");
    
    
    public static void main(String[] args) {
        TestRunner.run(ResourceTest.class);
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
        try {
            BrokerPool.stopAll(false);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testStore() {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            
            Txn transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");
            
            Collection collection = broker
                    .getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            
            broker.saveCollection(transaction, collection);
            
            BinaryDocument doc =
                    collection.addBinaryResource(transaction, broker,
                    DOCUMENT_NAME_URI , EMPTY_BINARY_FILE.getBytes(), "text/text");
            
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
        BrokerPool pool = startDB();
        
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        
        
        byte[] data = null;
        
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = pool.getTransactionManager();            
            Txn transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");
            
            XmldbURI docPath = TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_NAME_URI);
            
            BinaryDocument binDoc = (BinaryDocument) broker
                    .getXMLResource(docPath, Lock.READ_LOCK);
            
            // if document is not present, null is returned
            if(binDoc == null){
                fail("Binary document '" + docPath + " does not exist.");
            } else {
                data = broker.getBinaryResource(binDoc);
                binDoc.getUpdateLock().release(Lock.READ_LOCK);
            }
            
            Collection collection = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            collection.removeBinaryResource(transaction, broker, binDoc);
            
            broker.saveCollection(transaction, collection);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
        } catch (Exception ex){
            fail("Error opening document" + ex);
            
        } finally {
            if(pool!=null){
                pool.release(broker);
            }
        }
        
        assertEquals(0, data.length);
    }
    
    public void testStore2() {
    	testStore();
    }
    
    public void testRemoveCollection() {
    	BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        System.out.println("testRemoveCollection() ...\n");
        
        DBBroker broker = null;
        
        
        byte[] data = null;
        
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = pool.getTransactionManager();            
            Txn transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");
            
            XmldbURI docPath = TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_NAME_URI);
            
            BinaryDocument binDoc = (BinaryDocument) broker
                    .getXMLResource(docPath, Lock.READ_LOCK);
            
            // if document is not present, null is returned
            if(binDoc == null){
                fail("Binary document '" + docPath + " does not exist.");
            } else {
                data = broker.getBinaryResource(binDoc);
                binDoc.getUpdateLock().release(Lock.READ_LOCK);
            }
            
            Collection collection = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            broker.removeCollection(transaction, collection);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
        } catch (Exception ex){
            fail("Error opening document" + ex);
            
        } finally {
            if(pool!=null){
                pool.release(broker);
            }
        }
        
        assertEquals(0, data.length);
    }
}
