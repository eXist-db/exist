/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.ConcurrencyTest;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ConcurrentStoreTest {

    private static final Logger LOG = LogManager.getLogger(ConcurrencyTest.class);

    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private Collection test, test2;

    @Test
    public void storeAndRead() throws InterruptedException, EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        setupCollections(pool);

        final Thread t1 = new StoreThread1(pool);
        t1.start();

        synchronized (this) {
            wait(4000);
        }

        final Thread t2 = new StoreThread2(pool);
        t2.start();

        t1.join();
        t2.join();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = restartDb();

        read(pool);
    }

    private void read(final BrokerPool pool) throws EXistException, PermissionDeniedException, LockException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            test = broker.getCollection(TEST_COLLECTION_URI.append("test1"));
            assertNotNull(test);
            test2 = broker.getCollection(TEST_COLLECTION_URI.append("test2"));
            assertNotNull(test2);
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl next = i.next();
            }
        }
    }
    
    protected void setupCollections(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test1"));
            broker.saveCollection(transaction, test);

            test2 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test2);

            transact.commit(transaction);
        }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    private BrokerPool restartDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.restart(false);
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
    
    class StoreThread1 extends Thread {
        private final BrokerPool pool;

        StoreThread1(final BrokerPool pool) {
            this.pool = pool;
        }

        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                    final Txn transaction = transact.beginTransaction()) {

                // store some documents into the test collection
                for (final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
                    try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                        final String sample = InputStreamUtil.readString(is, UTF_8);
                        broker.storeDocument(transaction, XmldbURI.create(sampleName), new StringInputSource(sample), MimeType.XML_TYPE, test);
                    } catch (SAXException e) {
                        System.err.println("Error found while parsing document: " + sampleName + ": " + e.getMessage());
                    }
//                    if (i % 5 == 0) {
//                        transact.commit(transaction);
//                        transaction = transact.beginTransaction();
//                    }
                }
                
                transact.commit(transaction);
                
//              Don't commit...
                pool.getJournalManager().get().flush(true, false);
    	    } catch (Exception e) {
                LOG.error(e.getMessage(), e);
    	        fail(e.getMessage()); 
            }
        }
    }
    
    class StoreThread2 extends Thread {
        private final BrokerPool pool;

        StoreThread2(final BrokerPool pool) {
            this.pool = pool;
        }

        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                    final Txn transaction = transact.beginTransaction()) {
                
                Iterator<DocumentImpl> i = test.iterator(broker);
                DocumentImpl doc = i.next();

                test.removeXMLResource(transaction, broker, doc.getFileURI());

                try (final InputStream is = SAMPLES.getHamletSample()) {
                    final String sample = InputStreamUtil.readString(is, UTF_8);
                    broker.storeDocument(transaction, XmldbURI.create("test.xml"), new StringInputSource(sample), MimeType.XML_TYPE, test);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: hamlet.xml: " + e.getMessage());
                }
                
                transact.commit(transaction);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
    }
}
