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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Add a larger number of documents into a collection,
 * crash the database, restart, remove the collection and add some
 * more documents.
 * 
 * This test needs quite a few documents to be in the collection. Change
 * the directory path below to point to a directory with at least 1000 docs.
 * 
 * @author wolf
 *
 */
public class RecoveryTest3 {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final static int RESOURCE_COUNT = 5000;
    
    private static String directory = "/media/Shared/XML/movies";
    
    private static Path dir = Paths.get(directory);

    @Test
    public void store() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
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
                    final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                    assertNotNull(info);
                    test2.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
                } catch (final SAXException e) {
                    fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                }
            }

            transact.commit(transaction);
        }
    }

    @Test
    public void read() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException, LockException {

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
                            final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                            assertNotNull(info);
                            test2.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
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
    public void read2() throws DatabaseConfigurationException, EXistException, IOException {
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
