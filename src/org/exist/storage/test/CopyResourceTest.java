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

import java.io.File;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.xml.sax.InputSource;

/**
 * @author wolf
 * 
 */
public class CopyResourceTest extends TestCase {

	public static void main(String[] args) {
		TestRunner.run(CopyResourceTest.class);
	}

	private BrokerPool pool;

	public void testStore() throws Exception {
		BrokerPool.FORCE_CORRUPTION = true;
		BrokerPool pool = startDB();

		DBBroker broker = null;
		try {
			broker = pool.get(SecurityManager.SYSTEM_USER);

			TransactionManager transact = pool.getTransactionManager();
			Txn transaction = transact.beginTransaction();

			System.out.println("Transaction started ...");

			Collection root = broker.getOrCreateCollection(transaction,
					DBBroker.ROOT_COLLECTION + "/test");
			broker.saveCollection(transaction, root);

			Collection test = broker.getOrCreateCollection(transaction,
					DBBroker.ROOT_COLLECTION +  "/test/test2");
			broker.saveCollection(transaction, test);

			File f = new File("samples/shakespeare/r_and_j.xml");
			IndexInfo info = test.validate(transaction, broker, "test.xml",
					new InputSource(f.toURI().toASCIIString()));
			test.store(transaction, broker, info, new InputSource(f.toURI()
					.toASCIIString()), false);

			broker.copyResource(transaction, info.getDocument(), root,
					"new_test.xml");
			broker.saveCollection(transaction, root);

			transact.commit(transaction);
		} finally {
			pool.release(broker);
		}
	}

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

			doc = broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/new_test.xml", Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			data = serializer.serialize(doc);
			System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);
		} finally {
			pool.release(broker);
		}
	}

	public void testStoreAborted() throws Exception {
		BrokerPool.FORCE_CORRUPTION = true;
		BrokerPool pool = startDB();

		DBBroker broker = null;
		try {
			broker = pool.get(SecurityManager.SYSTEM_USER);

			TransactionManager transact = pool.getTransactionManager();
			Txn transaction = transact.beginTransaction();

			System.out.println("Transaction started ...");

			Collection root = broker.getOrCreateCollection(transaction,
					DBBroker.ROOT_COLLECTION + "/test");
			broker.saveCollection(transaction, root);

			Collection test = broker.getOrCreateCollection(transaction,
					DBBroker.ROOT_COLLECTION + "/test/test2");
			broker.saveCollection(transaction, test);

			File f = new File("samples/shakespeare/r_and_j.xml");
			IndexInfo info = test.validate(transaction, broker, "test2.xml",
					new InputSource(f.toURI().toASCIIString()));
			test.store(transaction, broker, info, new InputSource(f.toURI()
					.toASCIIString()), false);

			transact.commit(transaction);

			transaction = transact.beginTransaction();

			broker.copyResource(transaction, info.getDocument(), root,
					"new_test2.xml");
			broker.saveCollection(transaction, root);

			pool.getTransactionManager().getJournal().flushToLog(true);
		} finally {
			pool.release(broker);
		}
	}

	public void testReadAborted() throws Exception {
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

			doc = broker.openDocument(DBBroker.ROOT_COLLECTION +  "/test/test2/test2.xml",
					Lock.READ_LOCK);
			assertNotNull("Document should not be null", doc);
			data = serializer.serialize(doc);
			System.out.println(data);
			doc.getUpdateLock().release(Lock.READ_LOCK);

			doc = broker.openDocument(DBBroker.ROOT_COLLECTION +  "/test/new_test2.xml", Lock.READ_LOCK);
			assertNull("Document should not exist", doc);
		} finally {
			pool.release(broker);
		}
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
