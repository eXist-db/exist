/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.collections;

import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * Creates 3 collections, /db/test/test2, /db/test/test2/test3 and /db/test/test2/test4
 * and stores one document into each. Collection /db/test/test2/test3 is only writable for
 * the admin user. The test {@link #failingRemoveCollection()} tries to remove this collection
 * using the "guest" user account. eXist should detect the missing permissions and properly
 * abort the transaction.
 */
public class CollectionRemovalTest {

    private final static String DATA =
            "<document>" +
            "   <chapter>" +
            "       <title>Chapter 1</title>" +
            "   </chapter>" +
            "</document>";

    private final static String QUERY1 = "/document/chapter";
    private final static String QUERY2 = "//chapter[title = 'Chapter 1']";

    private static BrokerPool pool;

    @Test
    public void failingRemoveCollection() {
        doQuery(3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);
        
        removeCollection(org.exist.security.SecurityManager.GUEST_USER, TestConstants.TEST_COLLECTION_URI2);
        
        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI2);
        doQuery(3);
    }

    @Test
    public void removeCollection() {
        doQuery(3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);

        removeCollection(org.exist.security.SecurityManager.DBA_USER, TestConstants.TEST_COLLECTION_URI2);

        doQuery(0);
    }

    private void removeCollection(String user, XmldbURI uri) {
        TransactionManager transact = null;
		Txn transaction = null;
		DBBroker broker = null;
        Collection test = null;
        try {
            User guest = pool.getSecurityManager().getUser(user);
            broker = pool.get(guest);
			transact = pool.getTransactionManager();
			assertNotNull(transact);
			transaction = transact.beginTransaction();

            test = broker.openCollection(uri, Lock.WRITE_LOCK);
            broker.removeCollection(transaction, test);
            transact.commit(transaction);
        } catch (Exception e) {
			transact.abort(transaction);
			e.printStackTrace();
		} finally {
            if (test != null)
                test.release(Lock.WRITE_LOCK);
            pool.release(broker);
		}
    }

    private void retrieveDoc(XmldbURI uri) {
        DBBroker broker = null;
        Collection test = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            test = broker.openCollection(uri, Lock.WRITE_LOCK);
            assertNotNull(test);
            DocumentImpl doc = test.getDocument(broker, XmldbURI.createInternal("document.xml"));
            assertNotNull(doc);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            String xml = serializer.serialize(doc);
            System.out.println(xml);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (test != null)
                test.release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
    }

    private void doQuery(int expected) {
        try {
            org.xmldb.api.base.Collection testCollection =
                    DatabaseManager.getCollection("xmldb:exist://" + TestConstants.TEST_COLLECTION_URI.toString(), "admin", null);
            if (testCollection == null)
                return;
            XPathQueryServiceImpl service = (XPathQueryServiceImpl)
                    testCollection.getService("XQueryService", "1.0");
            ResourceSet result = service.query(QUERY1);
            assertEquals(expected, result.getSize());

            result = service.query(QUERY2);
            assertEquals(expected, result.getSize());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void clearDB() {
        try {
			org.xmldb.api.base.Collection root =
                    DatabaseManager.getCollection("xmldb:exist://" + TestConstants.TEST_COLLECTION_URI.toString(), "admin", null);
			CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			service.removeCollection(".");
        } catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }

    @Before
	public void initDB() {
		TransactionManager transact = null;
		Txn transaction = null;
		DBBroker broker = null;
		try {
			Configuration config = new Configuration();
			BrokerPool.configure(1, 40, config);
			pool = BrokerPool.getInstance();

			broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
			transact = pool.getTransactionManager();
			assertNotNull(transact);
			transaction = transact.beginTransaction();

			Collection root = broker.getOrCreateCollection(transaction,
					TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            Permission perms = root.getPermissions();
            // collection is world-writable
            perms.setPermissions(0777);
			broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test);
            perms = test.getPermissions();
            // collection is world-writable
            perms.setPermissions(0777);
			broker.saveCollection(transaction, test);

			IndexInfo info = test.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			test.store(transaction, broker, info, DATA, false);

            Collection childCol1 = broker.getOrCreateCollection(transaction,
                    TestConstants.TEST_COLLECTION_URI2.append("test4"));
            assertNotNull(childCol1);
            perms = childCol1.getPermissions();
            // collection only accessible to user
            perms.setPermissions(0777);
            broker.saveCollection(transaction, childCol1);

            info = childCol1.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			childCol1.store(transaction, broker, info, DATA, false);

            Collection childCol = broker.getOrCreateCollection(transaction,
                    TestConstants.TEST_COLLECTION_URI3);
            assertNotNull(childCol);
            perms = childCol.getPermissions();
            // collection only accessible to user
            perms.setPermissions(0700);
            broker.saveCollection(transaction, childCol);

            info = childCol.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			childCol.store(transaction, broker, info, DATA, false);

            transact.commit(transaction);

			// initialize XML:DB driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			DatabaseManager.registerDatabase(database);
		} catch (Exception e) {
			transact.abort(transaction);
			e.printStackTrace();
			fail(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

    @BeforeClass
    public static void startDB() {
        try {
			Configuration config = new Configuration();
			BrokerPool.configure(1, 40, config);
			pool = BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    @AfterClass
	public static void stopDB() {
		try {
			org.xmldb.api.base.Collection root = DatabaseManager.getCollection(
					"xmldb:exist:///db", "admin", null);
            DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
			dim.shutdown();
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		pool = null;
	}
}
