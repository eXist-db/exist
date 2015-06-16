/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
import java.io.IOException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author wolf
 * 
 */
public class CopyResourceTest {

    @Test
    public void storeAndRead() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, SAXException, EXistException {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndRead";

        store(testCollectionName, subCollection);
        tearDown();
        read(testCollectionName);
    }

    @Test
    public void storeAndReadAborted() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, SAXException, EXistException {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndReadAborted";

        storeAborted(testCollectionName, subCollection);
        tearDown();
        readAborted(testCollectionName, subCollection);
    }

	private void store(final String testCollectionName, final String subCollection) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, DatabaseConfigurationException {
		BrokerPool.FORCE_CORRUPTION = true;

		final BrokerPool pool = startDB();

        final TransactionManager transact = pool.getTransactionManager();
		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            Collection testCollection;
            IndexInfo info;
            try (final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test"));
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                testCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName));
                assertNotNull(testCollection);
                broker.saveCollection(transaction, testCollection);

                final Collection subTestCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection));
                assertNotNull(subTestCollection);
                broker.saveCollection(transaction, subTestCollection);

                final String existHome = System.getProperty("exist.home");
                final File existDir = existHome == null ? new File(".") : new File(existHome);
                final File f = new File(existDir, "samples/shakespeare/r_and_j.xml");
                assertNotNull(f);
                info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info);
                subTestCollection.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

                transact.commit(transaction);
            }

            try (final Txn transaction = transact.beginTransaction()) {

                broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test.xml"));
                broker.saveCollection(transaction, testCollection);

                transact.commit(transaction);
            }
		}
	}

	private void read(final String testCollectionName) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, SAXException {
		BrokerPool.FORCE_CORRUPTION = false;
		final BrokerPool pool = startDB();
		assertNotNull(pool);

		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
			final Serializer serializer = broker.getSerializer();
			serializer.reset();

			DocumentImpl doc = null;
			try {
				doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test.xml"), Lock.READ_LOCK);
				assertNotNull("Document should not be null", doc);
				final String data = serializer.serialize(doc);
				assertNotNull(data);
			} finally {
				if(doc != null) {
					doc.getUpdateLock().release(Lock.READ_LOCK);
				}
			}
		}
	}

    private void storeAborted(final String testCollectionName, final String subCollection) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException {

		BrokerPool.FORCE_CORRUPTION = true;

        final BrokerPool pool = startDB();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            Collection testCollection;
            IndexInfo info;

            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test"));
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                testCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName));
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final Collection subTestCollection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection));
                assertNotNull(subTestCollection);
                broker.saveCollection(transaction, subTestCollection);

                final String existHome = System.getProperty("exist.home");
                final File existDir = existHome == null ? new File(".") : new File(existHome);
                final File f = new File(existDir, "samples/shakespeare/r_and_j.xml");
                assertNotNull(f);
                info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info);
                subTestCollection.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

                transact.commit(transaction);
            }

			final Txn transaction = transact.beginTransaction();

			broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test2.xml"));
			broker.saveCollection(transaction, testCollection);

//DO NOT COMMIT TRANSACTION
			pool.getTransactionManager().getJournal().flushToLog(true);
		}
	}

	private void readAborted(final String testCollectionName, final String subCollection) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, SAXException {

		final BrokerPool pool = startDB();
		assertNotNull(pool);

		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
			final Serializer serializer = broker.getSerializer();
			serializer.reset();

			DocumentImpl doc = null;
			try {
				doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection).append("test2.xml"), Lock.READ_LOCK);
				assertNotNull("Document should not be null", doc);
				final String data = serializer.serialize(doc);
				assertNotNull(data);
			} finally {
				if(doc != null) {
					doc.getUpdateLock().release(Lock.READ_LOCK);
				}
			}

			doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test2.xml"), Lock.READ_LOCK);
			assertNull("Document should not exist", doc);
		}
	}

	protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
		final Configuration config = new Configuration();
		BrokerPool.configure(1, 5, config);
		return BrokerPool.getInstance();
	}

    @After
	public void tearDown() {
		BrokerPool.stopAll(false);
	}
}
