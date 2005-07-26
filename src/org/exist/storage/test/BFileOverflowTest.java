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

import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.FixedByteArray;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * @author wolf
 *
 */
public class BFileOverflowTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(BFileOverflowTest.class);
    }
    
    private BrokerPool pool;
    
    public void testAdd() throws Exception {
        TransactionManager mgr = pool.getTransactionManager();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            broker.flush();
            broker.sync(Sync.MAJOR_SYNC);
            
            Txn txn = mgr.beginTransaction();
            System.out.println("Transaction started ...");
            
            BFile collectionsDb = (BFile) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            BrokerPool.FORCE_CORRUPTION = true;
            
            Value key = new Value("test".getBytes());
            
            byte[] data = "_HELLO_YOU_".getBytes();
            collectionsDb.put(txn, key, new FixedByteArray(data, 0, data.length), true);
            
            for (int i = 1; i < 101; i++) {
                String value = "_HELLO_" + i;
                data = value.getBytes("UTF-8");
                collectionsDb.append(txn, key, new FixedByteArray(data, 0, data.length));
            }
            
            mgr.commit(txn);
            
            // start a new transaction that will not be committed and thus undone
            txn = mgr.beginTransaction();
            
            
            for (int i = 1001; i < 2001; i++) {
                String value = "_HELLO_" + i;
                data = value.getBytes("UTF-8");
                collectionsDb.append(txn, key, new FixedByteArray(data, 0, data.length));
            }
       
            collectionsDb.remove(txn, key);
            
            mgr.getLogManager().flushToLog(true);
            
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            BFile collectionsDb = (BFile)((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            
            Value key = new Value("test".getBytes());
            Value val = collectionsDb.get(key);
            System.out.println(new String(val.data(), val.start(), val.getLength()));
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
