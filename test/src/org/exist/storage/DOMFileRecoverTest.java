/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

/**
 * Tests transaction management  and basic recovery for the DOMFile class.
 * 
 * @author wolf
 *
 */
public class DOMFileRecoverTest extends TestCase {

	public static void main(String[] args) {
		TestRunner.run(DOMFileRecoverTest.class);
	}
	
	private BrokerPool pool;
	
	public void testAdd() {		
		BrokerPool.FORCE_CORRUPTION = false;
		DBBroker broker = null;
		NodeIdFactory idFact = pool.getNodeFactory();
		try {
			System.out.println("Add some random data and force db corruption ...\n");
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			assertNotNull(broker);
			//TODO : is this necessary ?
            broker.flush();
			TransactionManager mgr = pool.getTransactionManager();
			assertNotNull(mgr);
			Txn txn = mgr.beginTransaction();
			assertNotNull(txn);
			System.out.println("Transaction started ...");
            
            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            assertNotNull(domDb);
            domDb.setOwnerObject(this);
            
            // put 1000 values into the btree
            long firstToRemove = -1;
            for (int i = 1; i <= 10000; i++) {
                byte[] data = ("Value" + i).getBytes();
                NodeId id = idFact.createInstance(i);
                long addr = domDb.put(txn, new NativeBroker.NodeRef(500, id), data);
//              TODO : test addr ?
                if (i == 1)
                    firstToRemove = addr;
            }

            domDb.closeDocument();
            
            // remove all
            NativeBroker.NodeRef ref = new NativeBroker.NodeRef(500);
            assertNotNull(ref);
            IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            assertNotNull(idx);
            domDb.remove(txn, idx, null);
            domDb.removeAll(txn, firstToRemove);
            
            // put some more
            for (int i = 1; i <= 10000; i++) {
                byte[] data = ("Value" + i).getBytes();
                @SuppressWarnings("unused")
				long addr = domDb.put(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)), data);
//              TODO : test addr ?
            }
            
            domDb.closeDocument();
            mgr.commit(txn);
            System.out.println("Transaction commited ...");
            
            txn = mgr.beginTransaction();
            System.out.println("Transaction started ...");
            
            // put 1000 new values into the btree
            for (int i = 1; i <= 1000; i++) {
                byte[] data = ("Value" + i).getBytes();
                long addr = domDb.put(txn, new NativeBroker.NodeRef(501, idFact.createInstance(i)), data);
//              TODO : test addr ?                
                if (i == 1)
                    firstToRemove = addr;
            }            
            
            domDb.closeDocument();
            mgr.commit(txn);
            System.out.println("Transaction commited ...");
            
            // the following transaction is not committed and will be rolled back during recovery
            txn = mgr.beginTransaction();
            System.out.println("Transaction started ...");
            
            for (int i = 1; i <= 200; i++) {
                domDb.remove(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)));
            }

            idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, new NativeBroker.NodeRef(501));
            domDb.remove(txn, idx, null);
            domDb.removeAll(txn, firstToRemove);
            
//          Don't commit...
            mgr.commit(txn);
            mgr.getJournal().flushToLog(true);
            System.out.println("Transaction interrupted ...");
            
            Writer writer = new StringWriter();
            domDb.dump(writer);
            System.out.println(writer.toString());
	    } catch (Exception e) {
	    	e.printStackTrace();
	        fail(e.getMessage());               
		} finally {
			pool.release(broker);
		}
	}
	
    public void testGet() {
        
        DBBroker broker = null;
        try {
        	System.out.println("Recover and read the data ...\n");        	
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager mgr = pool.getTransactionManager();
            assertNotNull(mgr);
            
            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            assertNotNull(domDb);
            domDb.setOwnerObject(this);
            
            IndexQuery query = new IndexQuery(IndexQuery.GT, new NativeBroker.NodeRef(500));
            assertNotNull(query);
            List<?> keys = domDb.findKeys(query);
            assertNotNull(keys);
            int count = 0;
            for (Iterator<?> i = keys.iterator(); i.hasNext(); count++) {
                Value key = (Value) i.next();
                assertNotNull(key);
                Value value = domDb.get(key);
                assertNotNull(value);
                System.out.println(new String(value.data(), value.start(), value.getLength()));
            }
            System.out.println("Values read: " + count);
            
            Writer writer = new StringWriter();
            domDb.dump(writer);
            System.out.println(writer.toString());
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
            e.printStackTrace();
            fail(e.getMessage());
        }
	}

	protected void tearDown() {
		BrokerPool.stopAll(false);
	}
}
