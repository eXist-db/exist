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

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

import static org.junit.Assert.*;

import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.io.IOException;
import java.util.Optional;

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

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void failingRemoveCollection()
            throws XMLDBException, PermissionDeniedException, SAXException, EXistException, IOException, AuthenticationException, LockException {
        doQuery(3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);

        boolean caughtPermissionDenied = false;
        try {
        removeCollection(
        		org.exist.security.SecurityManager.GUEST_USER,
        		org.exist.security.SecurityManager.GUEST_USER,
        		TestConstants.TEST_COLLECTION_URI2);
        } catch(final PermissionDeniedException e) {
            caughtPermissionDenied = true;
        }

        if(!caughtPermissionDenied) {
            fail("Guest user should not have been able to remove the collection");
        }

        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI2);
        doQuery(3);
    }

    @Test
    public void removeCollection()
            throws XMLDBException, PermissionDeniedException, SAXException, EXistException, IOException, AuthenticationException, LockException {
        doQuery(3);
        retrieveDoc(TestConstants.TEST_COLLECTION_URI3);

        removeCollection(
                org.exist.security.SecurityManager.DBA_USER,
                "",
                TestConstants.TEST_COLLECTION_URI2);

        doQuery(0);
    }

    private void removeCollection(final String user, final String password, final XmldbURI uri)
            throws AuthenticationException, EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(user, password)));
            final Txn transaction = transact.beginTransaction();
            final Collection test = broker.openCollection(uri, LockMode.WRITE_LOCK)) {
            broker.removeCollection(transaction, test);
            transact.commit(transaction);
		}
    }

    private void retrieveDoc(final XmldbURI uri) throws EXistException, PermissionDeniedException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection test = broker.openCollection(uri, LockMode.WRITE_LOCK)) {
            assertNotNull(test);

            try(final LockedDocument lockedDoc = test.getDocumentWithLock(broker, XmldbURI.createInternal("document.xml"), LockMode.READ_LOCK)) {
                assertNotNull(lockedDoc);

                Serializer serializer = broker.getSerializer();
                serializer.reset();
                String xml = serializer.serialize(lockedDoc.getDocument());

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                test.close();
            }
        }
    }

    private void doQuery(final int expected) throws XMLDBException {
        final org.xmldb.api.base.Collection testCollection =
                DatabaseManager.getCollection("xmldb:exist://" + TestConstants.TEST_COLLECTION_URI.toString(), "admin", "");
        if (testCollection == null) {
            return;
        }
        final EXistXPathQueryService service = (EXistXPathQueryService)
                testCollection.getService("XQueryService", "1.0");
        ResourceSet result = service.query(QUERY1);
        assertEquals(expected, result.getSize());

        result = service.query(QUERY2);
        assertEquals(expected, result.getSize());
    }

    @BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException, ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        // initialize XML:DB driver
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
    }

    @Before
	public void initDB() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException, ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

			Collection root = broker.getOrCreateCollection(transaction,
					TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            Permission perms = root.getPermissions();
            // collection is world-writable
            perms.setMode(0744);
			broker.saveCollection(transaction, root);

            Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test);
            perms = test.getPermissions();
            // collection is world-writable
            perms.setMode(0744);
			broker.saveCollection(transaction, test);

			IndexInfo info = test.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			test.store(transaction, broker, info, DATA);

            Collection childCol1 = broker.getOrCreateCollection(transaction,
                    TestConstants.TEST_COLLECTION_URI2.append("test4"));
            assertNotNull(childCol1);
            perms = childCol1.getPermissions();
            // collection only accessible to user
            perms.setMode(0744);
            broker.saveCollection(transaction, childCol1);

            info = childCol1.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			childCol1.store(transaction, broker, info, DATA);

            Collection childCol = broker.getOrCreateCollection(transaction,
                    TestConstants.TEST_COLLECTION_URI3);
            assertNotNull(childCol);
            perms = childCol.getPermissions();
            // collection only accessible to user
            perms.setMode(0700);
            broker.saveCollection(transaction, childCol);

            info = childCol.validateXMLResource(transaction, broker,
					XmldbURI.create("document.xml"), DATA);
			assertNotNull(info);
			childCol.store(transaction, broker, info, DATA);

            transact.commit(transaction);
		}
	}

    @After
    public void clearDB() throws XMLDBException {
        final org.xmldb.api.base.Collection root =
                DatabaseManager.getCollection("xmldb:exist://" + TestConstants.TEST_COLLECTION_URI.toString(), "admin", "");
        final CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        service.removeCollection(".");
    }
}
