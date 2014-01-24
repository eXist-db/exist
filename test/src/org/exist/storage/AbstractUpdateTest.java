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

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public abstract class AbstractUpdateTest {

	protected static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";

    @Test
    public void read() {
    	BrokerPool.FORCE_CORRUPTION = false;         
        
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	System.out.println("testRead() ...\n");  
        	
        	pool = startDB();
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc;
            String data;
            
            doc = broker.getXMLResource(TEST_COLLECTION_URI.append("test2/test.xml"), Lock.READ_LOCK);
            assertNotNull("Document '"+ TEST_COLLECTION_URI.append("test2/test.xml")+"' should not be null", doc);
            data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
            
            XQuery xquery = broker.getXQueryService();
            Sequence seq = xquery.execute("/products/product[last()]", null, AccessContext.TEST);
            System.out.println("Found: " + seq.getItemCount());
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                System.out.println(serializer.serialize((NodeValue) next));
            }
        } catch (Exception e) {            
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    protected IndexInfo init(DBBroker broker, TransactionManager mgr) {
    	IndexInfo info = null;
    	Txn transaction = null;
    	try {
    		
    		transaction = mgr.beginTransaction();        
        	System.out.println("Transaction started ...");
	        
	        Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
	        broker.saveCollection(transaction, root);
	        
	        Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
	        broker.saveCollection(transaction, test);
	        
	        info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), TEST_XML);
	        //TODO : unlock the collection here ?
	        test.store(transaction, broker, info, TEST_XML, false);
	
	        mgr.commit(transaction);	
	        System.out.println("Transaction commited ...");
	        
	    } catch (Exception e) {
	    	mgr.abort(transaction);
	        fail(e.getMessage());
	    }  
	    return info;
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

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }    
}
