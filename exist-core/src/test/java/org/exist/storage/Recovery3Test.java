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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Add a larger number of documents into a collection,
 * crash the database, restart, remove the collection and add some
 * more documents. store() must run before read() and read2().
 *
 * @author wolf
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Recovery3Test {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final static int RESOURCE_COUNT = 150;
    
    private Path dir;

    @Before
    public void createTestData() throws IOException {
        dir = tempFolder.newFolder("recovery3-data").toPath();
        for (int i = 0; i < RESOURCE_COUNT; i++) {
            final Path f = dir.resolve("doc" + i + ".xml");
            Files.write(f, ("<?xml version=\"1.0\"?><movie id=\"" + i + "\"><title>Movie " + i + "</title></movie>").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void test01_store() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDb();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);

            final List<Path> files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());
            assertNotNull(files);

            // store some documents.
            for (int i = 0; i < files.size() && i < RESOURCE_COUNT; i++) {
                final Path f = files.get(i);
                try {
                    broker.storeDocument(transaction, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()), MimeType.XML_TYPE, test2);
                } catch (final SAXException e) {
                    fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                }
            }

            transact.commit(transaction);
        }
    }

    @Test
    public void test02_read() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException, LockException {

    	BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDb();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            BrokerPool.FORCE_CORRUPTION = true;

            try (final Txn transaction = transact.beginTransaction();
                    final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                assertNotNull(root);
                transaction.acquireCollectionLock(() -> broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(root.getURI()));
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }

            try (final Txn transaction = transact.beginTransaction();
                    final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                //TODO(AR) needs write lock
                try(final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2)) {
                    assertNotNull(test2);
                    broker.saveCollection(transaction, test2);

                    final List<Path> files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());

                    // store some documents.
                    for (int i = 0; i < files.size() && i < RESOURCE_COUNT; i++) {
                        final Path f = files.get(i);
                        try {
                            broker.storeDocument(transaction, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()), MimeType.XML_TYPE, test2);
                        } catch (SAXException e) {
                            fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                        }
                    }
                }

                transact.commit(transaction);
            }
        }
    }

    @Test
    public void test03_read2() throws DatabaseConfigurationException, EXistException, IOException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDb();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            assertNotNull(broker);

            //TODO : do something ?
	    }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

}
