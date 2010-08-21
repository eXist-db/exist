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
import java.io.StringWriter;
import java.io.Writer;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;

/**
 * Test recovery after a forced database corruption.
 * 
 * @author wolf
 *
 */
public class RecoveryTest2 extends TestCase {
    
    public static void main(String[] args) {
        TestRunner.run(RecoveryTest2.class);
    }
    
    private static String xmlDir = "/home/wolf/xml/Saami";
    
    @SuppressWarnings("unused")
	private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";
    
    public void testStore() {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = null;        
        DBBroker broker = null;
        try {
        	pool = startDB();
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
            
            Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2); 
            broker.saveCollection(transaction, test2);
            
            System.out.println("Contents of dom.dbx:\n\n");
            DOMFile domDb = ((NativeBroker)broker).getDOMFile();
            assertNotNull(domDb); 
            Writer writer = new StringWriter();
            domDb.dump(writer);
            System.out.println(writer.toString());
            
            File f;
            IndexInfo info;
            
            // store some documents. Will be replaced below
            File dir = new File(xmlDir);
            assertNotNull(dir); 
            File[] docs = dir.listFiles();
            assertNotNull(docs); 
            for (int i = 0; i < docs.length; i++) {
                f = docs[i];
                assertNotNull(f); 
                info = test2.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info); 
                test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            }
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
	    } catch (Exception e) {            
	        fail(e.getMessage());          
        } finally {
        	if (pool != null) pool.release(broker);
        }
    }
    
    public void testRead() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	System.out.println("testRead() ...\n");
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append("terms-eng.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            String data = serializer.serialize(doc);
            assertNotNull(data);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
	    } catch (Exception e) {            
	        fail(e.getMessage()); 
	    } finally {
	    	if (pool != null) pool.release(broker);
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
        BrokerPool.stopAll(false);
    }
}
