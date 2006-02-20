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

import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

/**
 * @author wolf
 *
 */
public class CollectionTest extends TestCase {

    private static String docs[] = { "hamlet.xml", "r_and_j.xml", "macbeth.xml" };
    
    private static String TEST_COLLECTION = DBBroker.ROOT_COLLECTION + "/test";
    private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";
    
    public static void main(String[] args) {
        TestRunner.run(CollectionTest.class);
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
            
            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, root);
            
            Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION + "/test2");
            broker.saveCollection(transaction, test);
            
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
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            BTree btree = ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            Writer writer = new StringWriter();
            btree.dump(writer);
            System.out.println(writer.toString());
            
            Collection test = broker.getCollection(TEST_COLLECTION + "/test2");
            assertNotNull(test);
            System.out.println("Contents of collection " + test.getName() + ":");
            for (Iterator i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl next = (DocumentImpl) i.next();
                System.out.println("- " + next.getName());
            }
        } catch (Exception e) {            
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
    	try {
	        BrokerPool.stopAll(false);
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }
    }
}
