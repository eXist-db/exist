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

import java.io.StringWriter;
import java.io.Writer;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.FixedByteArray;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author wolf
 *
 */
public class BFileRecoverTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(BFileRecoverTest.class);
    }
    
    private BrokerPool pool;
    
    public void testAdd() {
        TransactionManager mgr = pool.getTransactionManager();
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            broker.flush();
            broker.sync(Sync.MAJOR_SYNC);
            
            Txn txn = mgr.beginTransaction();
            System.out.println("Transaction started ...");
            
            BFile collectionsDb = (BFile) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            BrokerPool.FORCE_CORRUPTION = true;
            
            for (int i = 1; i < 1001; i++) {
                String value = "test" + i;
                byte[] data = value.getBytes(UTF_8);
                collectionsDb.put(txn, new Value(data), new FixedByteArray(data, 0, data.length), true);
            }
            
            byte[] replacement = "new value".getBytes(UTF_8);
            for (int i = 1; i < 101; i++) {
                String value = "test" + i;
                byte[] data = value.getBytes(UTF_8);
                collectionsDb.put(txn, new Value(data), new FixedByteArray(replacement, 0, replacement.length), true);
            }
            mgr.commit(txn);
            
            Writer writer = new StringWriter();
            collectionsDb.dump(writer);
            System.out.println(writer.toString());
        } catch (Exception e) {            
            fail(e.getMessage());            
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRead() {
        BrokerPool.FORCE_CORRUPTION = false;
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            BFile collectionsDb = (BFile)((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            Writer writer = new StringWriter();
            collectionsDb.dump(writer);
            System.out.println(writer.toString());
            
            for (int i = 1; i < 1001; i++) {
                String key = "test" + i;
                byte[] data = key.getBytes(UTF_8);
                Value value = collectionsDb.get(new Value(data));
                if (value == null)
                    System.out.println("Key " + key + " not found.");
                else
                    System.out.println(new String(value.data(), value.start(), value.getLength(), UTF_8));
            }
        } catch (Exception e) {            
            fail(e.getMessage());            
        } finally {
            pool.release(broker);
        }
    }
    
    protected void setUp() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
    }

    protected void tearDown() {
        BrokerPool.stopAll(false);
    }

}
