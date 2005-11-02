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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * @author wolf
 *
 */
public class RecoverBinaryTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(RecoverBinaryTest.class);
    }
    
    private BrokerPool pool;
    
    public void testStore() throws Exception {
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            BrokerPool.FORCE_CORRUPTION = true;
            
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            Collection root = broker.getOrCreateCollection(transaction, DBBroker.ROOT_COLLECTION + "/test");
            broker.saveCollection(transaction, root);
    
            FileInputStream is = new FileInputStream("LICENSE");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int count = 0;
            while ((count = is.read(buf)) > -1) {
                os.write(buf, 0, count);
            }
            BinaryDocument doc = 
				root.addBinaryResource(transaction, broker, "binary.txt", os.toByteArray(), 
						"text/text");
            
            transact.commit(transaction);
            
            // the following transaction will not be committed. It will thus be rolled back by recovery
//            transaction = transact.beginTransaction();
//            root.removeBinaryResource(transaction, broker, doc);
            
            transact.getJournal().flushToLog(true);
        } finally {
            pool.release(broker);
        }
    }
    
    public void testLoad() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            BinaryDocument binDoc = (BinaryDocument) broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/binary.txt", Lock.READ_LOCK);
            assertNotNull("Binary document is null", binDoc);
            String data = new String(broker.getBinaryResourceData(binDoc));
            System.out.println(data);
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
