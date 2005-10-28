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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

public abstract class AbstractUpdateTest extends TestCase {

	protected static String TEST_COLLECTION = DBBroker.ROOT_COLLECTION + "/test";
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";

    public void testRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        System.out.println("testRead() ...\n");
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc;
            String data;
            
            doc = broker.openDocument(TEST_COLLECTION + "/test2/test.xml", Lock.READ_LOCK);
            assertNotNull("Document "+ TEST_COLLECTION + "/test2/test.xml should not be null", doc);
            data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
            
            XQuery xquery = broker.getXQueryService();
            Sequence seq = xquery.execute("/products/product[last()]", null);
            System.out.println("Found: " + seq.getLength());
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                System.out.println(serializer.serialize((NodeValue) next));
            }
        } finally {
            pool.release(broker);
        }
    }

    protected IndexInfo init(DBBroker broker, TransactionManager mgr) throws Exception {
        Txn transaction = mgr.beginTransaction();
        
        System.out.println("Transaction started ...");
        
        Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
        broker.saveCollection(transaction, root);
        
        Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION + "/test2");
        broker.saveCollection(transaction, test);
        
        IndexInfo info = test.validate(transaction, broker, "test.xml", TEST_XML);
        test.store(transaction, broker, info, TEST_XML, false);

        mgr.commit(transaction);
        return info;
    }
    
    protected BrokerPool startDB() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }    
}
