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
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.TestDataGenerator;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;

/**
 * @author wolf
 *
 */
public class RemoveCollectionTest {

    private final static String generateXQ =
            "<book id=\"{$filename}\" n=\"{$count}\">" +
            "   <chapter>" +
            "       <title>{pt:random-text(7)}</title>" +
            "       {" +
            "           for $section in 1 to 8 return" +
            "               <section id=\"sect{$section}\">" +
            "                   <title>{pt:random-text(7)}</title>" +
            "                   {" +
            "                       for $para in 1 to 10 return" +
            "                           <para>{pt:random-text(40)}</para>" +
            "                   }" +
            "               </section>" +
            "       }" +
            "   </chapter>" +
            "</book>";

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text match=\"/*\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";
    
    private final static int COUNT = 300;
    
    @Test
    public void runTests() {
        removeCollection();
        recover(true);
        removeResources();
        recover(true);
        replaceResources();
        recover(false);
    }
    
    public void removeCollection() {
    	BrokerPool.FORCE_CORRUPTION = true;
        DBBroker broker = null;
        BrokerPool pool = startDB();
        assertNotNull(pool);
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);                       
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Collection test = storeDocs(broker, transact);
            Txn transaction;
            
            transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");

            System.out.println("Removing collection ...");
            broker.removeCollection(transaction, test);
            
            transact.commit(transaction);
            System.out.println("Transaction interrupted ...");
	    } catch (Exception e) {  
	    	e.printStackTrace();
	        fail(e.getMessage());               
        } finally {
        	if (pool != null) pool.release(broker);
            stopDB();
        }
    }

    public void removeResources() {
    	BrokerPool.FORCE_CORRUPTION = true;
        DBBroker broker = null;
        BrokerPool pool = startDB();
        assertNotNull(pool);
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Collection test = storeDocs(broker, transact);
            Txn transaction;

            transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");

            System.out.println("Removing documents one by one ...");
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                broker.removeXMLResource(transaction, doc);
            }
            broker.saveCollection(transaction, test);
            transact.commit(transaction);

            System.out.println("Transaction committed ...");
	    } catch (Exception e) {
	        fail(e.getMessage());
        } finally {
        	if (pool != null) pool.release(broker);
            stopDB();
        }
    }

    public void replaceResources() {
    	BrokerPool.FORCE_CORRUPTION = true;
        DBBroker broker = null;
        BrokerPool pool = startDB();
        assertNotNull(pool);
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Collection test = storeDocs(broker, transact);
            Txn transaction;

            transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");

            System.out.println("Replacing resources ...");
            TestDataGenerator generator = new TestDataGenerator("xdb", COUNT);
            System.out.println("Generating " + COUNT + " files...");
            File[] files = generator.generate(broker, test, generateXQ);

            int j = 0;
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext() && j < files.length; j++) {
                DocumentImpl doc = i.next();
                InputSource is = new InputSource(files[j].toURI().toASCIIString());
                assertNotNull(is);
                IndexInfo info = test.validateXMLResource(transaction, broker, doc.getURI(), is);
                assertNotNull(info);
                test.store(transaction, broker, info, is, false);
            }
            generator.releaseAll();
            transact.commit(transaction);
            System.out.println("Transaction committed ...");
	    } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
        	if (pool != null) pool.release(broker);
            stopDB();
        }
    }

    private Collection storeDocs(DBBroker broker, TransactionManager transact) throws Exception {
        Txn transaction = transact.beginTransaction();
        assertNotNull(transaction);
        System.out.println("Transaction started ...");

        Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
        assertNotNull(test);
        broker.saveCollection(transaction, test);

        CollectionConfigurationManager mgr = broker.getBrokerPool().getConfigurationManager();
        mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);

        InputSource is = new InputSource(new File("samples/shakespeare/hamlet.xml").toURI().toASCIIString());
        assertNotNull(is);
        IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"), is);
        assertNotNull(info);
        test.store(transaction, broker, info, is, false);
        transact.commit(transaction);
        System.out.println("Transaction commited ...");

        transaction = transact.beginTransaction();
        TestDataGenerator generator = new TestDataGenerator("xdb", COUNT);
        System.out.println("Generating " + COUNT + " files...");
        File[] files = generator.generate(broker, test, generateXQ);
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            is = new InputSource(file.toURI().toASCIIString());
            assertNotNull(is);
            info = test.validateXMLResource(transaction, broker, XmldbURI.create(file.getName()), is);
            assertNotNull(info);
            test.store(transaction, broker, info, is, false);
        }
        generator.releaseAll();
        transact.commit(transaction);
        System.out.println("Transaction commited ...");
        return test;
    }

    public void recover(boolean checkResource) {
        BrokerPool.FORCE_CORRUPTION = false;
        DBBroker broker = null;
        BrokerPool pool = startDB();
        assertNotNull(pool);
        DocumentImpl doc = null;
        try {
        	System.out.println("testRead() ...\n");
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            if (checkResource) {
                doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append("hamlet.xml"), Lock.READ_LOCK);
                assertNull("Resource should have been removed", doc);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
	    } finally {
            if (doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            if (pool != null) pool.release(broker);
            stopDB();
        }
    }

    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
            return null;
        }
    }

    protected void stopDB() {
        BrokerPool.stopAll(false);
    }
}
