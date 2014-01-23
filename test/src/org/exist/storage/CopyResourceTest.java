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

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;

/**
 * @author wolf
 * 
 */
public class CopyResourceTest {

    @Test
    public void storeAndRead() {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndRead";

        store(testCollectionName, subCollection);
        tearDown();
        read(testCollectionName);
    }

    @Test
    public void storeAndReadAborted() {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndReadAborted";

        storeAborted(testCollectionName, subCollection);
        tearDown();
        readAborted(testCollectionName, subCollection);
    }

	private void store(final String testCollectionName, final String subCollection) {
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

			Collection root = broker.getOrCreateCollection(transaction,	XmldbURI.ROOT_COLLECTION_URI.append("test"));
			assertNotNull(root);
			broker.saveCollection(transaction, root);

			Collection testCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName));
			assertNotNull(testCollection);
			broker.saveCollection(transaction, testCollection);

            Collection subTestCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection));
            assertNotNull(subTestCollection);
            broker.saveCollection(transaction, subTestCollection);

            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,"samples/shakespeare/r_and_j.xml");
			assertNotNull(f);
			IndexInfo info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
			assertNotNull(info);
			subTestCollection.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

			broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test.xml"));
			broker.saveCollection(transaction, testCollection);

			transact.commit(transaction);
			System.out.println("Transaction commited ...");
	    } catch (Exception e) {
            e.printStackTrace();
	        fail(e.getMessage()); 	      			
		} finally {
			if (pool != null) pool.release(broker);
		}
	}

	private void read(final String testCollectionName) {
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

			DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test.xml"), Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			String data = serializer.serialize(doc);
			assertNotNull(data);
			//System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);
	    } catch (Exception e) {            
	        fail(e.getMessage()); 			
		} finally {
			pool.release(broker);
		}
	}

    private void storeAborted(final String testCollectionName, final String subCollection) {
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

			Collection root = broker.getOrCreateCollection(transaction,	XmldbURI.ROOT_COLLECTION_URI.append("test"));
			assertNotNull(root);
			broker.saveCollection(transaction, root);

            Collection testCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName));
            assertNotNull(root);
            broker.saveCollection(transaction, root);

			Collection subTestCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection));
			assertNotNull(subTestCollection);
			broker.saveCollection(transaction, subTestCollection);

            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,"samples/shakespeare/r_and_j.xml");
			assertNotNull(f);
			IndexInfo info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), new InputSource(f.toURI().toASCIIString()));
			assertNotNull(info);
			subTestCollection.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

			transact.commit(transaction);
			System.out.println("Transaction commited ...");

			transaction = transact.beginTransaction();
			System.out.println("Transaction started ...");

			broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test2.xml"));
			broker.saveCollection(transaction, testCollection);
			
//			Don't commit...
			pool.getTransactionManager().getJournal().flushToLog(true);
			System.out.println("Transaction interrupted ...");
	    } catch (Exception e) {            
	        fail(e.getMessage());			
		} finally {
			if (pool != null) pool.release(broker);
		}
	}

	private void readAborted(final String testCollectionName, final String subCollection) {
		BrokerPool.FORCE_CORRUPTION = false;
		BrokerPool pool = null;
		DBBroker broker = null;		
		try {
			System.out.println("testReadAborted() ...\n");
			pool = startDB();
			assertNotNull(pool);
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			assertNotNull(broker);
			Serializer serializer = broker.getSerializer();
			serializer.reset();

			DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection).append("test2.xml"),	Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			String data = serializer.serialize(doc);
			assertNotNull(data);
			//System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);

			doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test2.xml"), Lock.READ_LOCK);
			assertNull("Document should not exist", doc);
	    } catch (Exception e) {            
	        fail(e.getMessage());  			
		} finally {
			pool.release(broker);
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

    @After
	public void tearDown() {
		BrokerPool.stopAll(false);
	}
}
