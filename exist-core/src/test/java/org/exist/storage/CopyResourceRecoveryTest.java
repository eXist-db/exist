/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.exist.samples.Samples.SAMPLES;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CopyResourceRecoveryTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndRead() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, SAXException, EXistException, URISyntaxException {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndRead";

        BrokerPool.FORCE_CORRUPTION = true;
        store(testCollectionName, subCollection);

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(testCollectionName);
    }

    @Test
    public void storeAndReadAborted() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, SAXException, EXistException, URISyntaxException {
        final String testCollectionName = "copyResource";
        final String subCollection = "storeAndReadAborted";


        BrokerPool.FORCE_CORRUPTION = true;
        storeAborted(testCollectionName, subCollection);

        existEmbeddedServer.restart();

        readAborted(testCollectionName, subCollection);
    }

    private void store(final String testCollectionName, final String subCollection) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, URISyntaxException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

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

                final String sample;
                try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
                    assertNotNull(is);
                    sample = InputStreamUtil.readString(is, UTF_8);
                }
                info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), sample);
                assertNotNull(info);
                subTestCollection.store(transaction, broker, info, sample);

                transact.commit(transaction);
            }

            try (final Txn transaction = transact.beginTransaction()) {

                broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test.xml"));
                broker.saveCollection(transaction, testCollection);

                transact.commit(transaction);
            }
        }
    }

    private void read(final String testCollectionName) throws EXistException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

			try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test.xml"), LockMode.READ_LOCK)) {
				assertNotNull("Document should not be null", lockedDoc);
				final String data = serializer.serialize(lockedDoc.getDocument());
				assertNotNull(data);
			}
		}
	}

    private void storeAborted(final String testCollectionName, final String subCollection) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, URISyntaxException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

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

                final String sample;
                try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
                    assertNotNull(is);
                    sample = InputStreamUtil.readString(is, UTF_8);
                }
                info = subTestCollection.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), sample);
                assertNotNull(info);
                subTestCollection.store(transaction, broker, info, sample);

                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();

            broker.copyResource(transaction, info.getDocument(), testCollection, XmldbURI.create("new_test2.xml"));
            broker.saveCollection(transaction, testCollection);

//DO NOT COMMIT TRANSACTION
            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void readAborted(final String testCollectionName, final String subCollection) throws EXistException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

			try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append(subCollection).append("test2.xml"), LockMode.READ_LOCK)) {
				assertNotNull("Document should not be null", lockedDoc);
				final String data = serializer.serialize(lockedDoc.getDocument());
				assertNotNull(data);
			}

			try(final LockedDocument lockedDoc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test").append(testCollectionName).append("new_test2.xml"), LockMode.READ_LOCK)) {
                assertNull("Document should not exist as copy was not committed", lockedDoc);
            }
		}
	}

    @After
    public void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
