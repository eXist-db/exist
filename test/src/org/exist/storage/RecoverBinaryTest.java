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

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;

/**
 * @author wolf
 *
 */
public class RecoverBinaryTest extends TestCase {

    public static void main(String[] args) {
        TestRunner.run(RecoverBinaryTest.class);
    }
    
    private BrokerPool pool;
    
    public void testStore() {
    	BrokerPool.FORCE_CORRUPTION = true;
        DBBroker broker = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");
            
            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
    
            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            FileInputStream is = new FileInputStream(new File(existDir,"LICENSE"));
            assertNotNull(is);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int count = 0;
            while ((count = is.read(buf)) > -1) {
                os.write(buf, 0, count);
            }
            BinaryDocument doc = 
				root.addBinaryResource(transaction, broker, TestConstants.TEST_BINARY_URI, os.toByteArray(),	"text/text");
            assertNotNull(doc);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
            // the following transaction will not be committed. It will thus be rolled back by recovery
//            transaction = transact.beginTransaction();
//            root.removeBinaryResource(transaction, broker, doc);
            
            //TODO : remove ?
            transact.getJournal().flushToLog(true);
		} catch (Exception e) {            
	        fail(e.getMessage());             
        } finally {
            if (pool != null) pool.release(broker);
        }
    }
    
    public void testLoad() {
        BrokerPool.FORCE_CORRUPTION = false;
        DBBroker broker = null;
        try {
        	System.out.println("testRead() ...\n");
        	assertNotNull(pool);
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
        	assertNotNull(broker);
            BinaryDocument binDoc = (BinaryDocument) broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append(TestConstants.TEST_BINARY_URI), Lock.READ_LOCK);
            assertNotNull("Binary document is null", binDoc);
            InputStream is = broker.getBinaryResource(binDoc);
            byte [] bdata = new byte[(int)broker.getBinaryResourceSize(binDoc)];
            is.read(bdata);
            is.close();
            String data = new String(bdata);
            assertNotNull(data);
            System.out.println(data);
		} catch (Exception e) {            
	        fail(e.getMessage());
	    } finally {
            if (pool != null) pool.release(broker);
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
        pool = null;
    }
}
