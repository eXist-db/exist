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

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;

/**
 * @author wolf
 * 
 */
public class CopyResourceTest extends TestCase {

	public static void main(String[] args) {
		TestRunner.run(CopyResourceTest.class);
	}	

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

			Collection root = broker.getOrCreateCollection(transaction,	XmldbURI.ROOT_COLLECTION_URI.append("test"));
			assertNotNull(root);
			broker.saveCollection(transaction, root);

			Collection test2 = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test/test2"));
			assertNotNull(test2);
			broker.saveCollection(transaction, test2);

                        String existHome = System.getProperty("exist.home");
                        File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,"samples/shakespeare/r_and_j.xml");
			assertNotNull(f);
			IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
			assertNotNull(info);
			test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

			broker.copyResource(transaction, info.getDocument(), root, XmldbURI.create("new_test.xml"));
			broker.saveCollection(transaction, root);

			transact.commit(transaction);
			System.out.println("Transaction commited ...");
	    } catch (Exception e) {
            e.printStackTrace();
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

			DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/new_test.xml"), Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			String data = serializer.serialize(doc);
			assertNotNull(data);
			System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);
	    } catch (Exception e) {            
	        fail(e.getMessage()); 			
		} finally {
			pool.release(broker);
		}
	}

	public void testStoreAborted() {
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

			Collection test2 = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test/test2"));
			assertNotNull(test2);
			broker.saveCollection(transaction, test2);

                        String existHome = System.getProperty("exist.home");
                        File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,"samples/shakespeare/r_and_j.xml");
			assertNotNull(f);
			IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), new InputSource(f.toURI().toASCIIString()));
			assertNotNull(info);
			test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

			transact.commit(transaction);
			System.out.println("Transaction commited ...");

			transaction = transact.beginTransaction();
			System.out.println("Transaction started ...");

			broker.copyResource(transaction, info.getDocument(), root, XmldbURI.create("new_test2.xml"));
			broker.saveCollection(transaction, root);
			
//			Don't commit...
			pool.getTransactionManager().getJournal().flushToLog(true);
			System.out.println("Transaction interrupted ...");
	    } catch (Exception e) {            
	        fail(e.getMessage());			
		} finally {
			if (pool != null) pool.release(broker);
		}
	}

	public void testReadAborted() {
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

			DocumentImpl doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/test2/test2.xml"),	Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			String data = serializer.serialize(doc);
			assertNotNull(data);
			System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);

			doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/new_test2.xml"), Lock.READ_LOCK);
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

	protected void tearDown() {
		BrokerPool.stopAll(false);
	}
}
