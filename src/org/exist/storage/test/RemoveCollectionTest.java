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
import org.xml.sax.InputSource;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * @author wolf
 *
 */
public class RemoveCollectionTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(RemoveCollectionTest.class);
    }
    
    private BrokerPool pool;
    
    public void testStore() throws Exception {
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            BrokerPool.FORCE_CORRUPTION = true;
            
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            System.out.println("Transaction started ...");
            
            Collection test = broker.getOrCreateCollection(transaction, "/db/test");
            broker.saveCollection(transaction, test);
            
            Collection test2 = broker.getOrCreateCollection(transaction, "/db/test/test2");
            broker.saveCollection(transaction, test2);
            
            File f = new File("samples/biblio.rdf");
            InputSource is = new InputSource(f.toURI().toASCIIString());
            IndexInfo info = test.validate(transaction, broker, "biblio.rdf", is);
            test.store(transaction, broker, info, is, false);
            
            transact.commit(transaction);
            
            transaction = transact.beginTransaction();
            
            broker.removeCollection(transaction, test);
            
            transact.getJournal().flushToLog(true);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            Collection test = broker.openCollection("/db/test", Lock.READ_LOCK);
            assertNotNull("Collection /db/test not found", test);
            
            DocumentImpl doc = broker.openDocument("/db/test/biblio.rdf", Lock.READ_LOCK);
            assertNotNull("Document /db/test/biblio.rdf should not be null", doc);
            String data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
            test.release();
        } finally {
            pool.release(broker);
        }
    }
    
    protected void setUp() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
}
