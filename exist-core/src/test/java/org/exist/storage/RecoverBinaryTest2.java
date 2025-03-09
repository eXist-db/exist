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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertNotNull;

public class RecoverBinaryTest2 {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static String directory = "webapp/resources";

    @Test
    public void storeAndRead() throws SAXException, PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, EXistException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        read(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        read2(pool);
    }

    public void store(final BrokerPool pool) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);            
            
            storeFiles(broker, transaction, test2);
            transact.commit(transaction);
        }
    }

    public void read(final BrokerPool pool) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, LockException, IOException, SAXException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (final Iterator<DocumentImpl> i = test2.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
            }
            
            BrokerPool.FORCE_CORRUPTION = true;
            final TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                storeFiles(broker, transaction, test2);
                transact.commit(transaction);
            }
        }
    }

    public void read2(final BrokerPool pool) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException {
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (final Iterator<DocumentImpl> i = test2.iterator(broker); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
            }
            
            final TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);
                final Collection test1 = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
                broker.removeCollection(transaction, test1);
                transact.commit(transaction);
            }
        }
    }
    
    private void storeFiles(final DBBroker broker, final Txn transaction, final Collection test2) throws IOException, EXistException, PermissionDeniedException, LockException, SAXException {
        // Get files in directory
        final Path dir = FileUtils.resolve(ConfigurationHelper.getExistHome(), directory);
        final List<Path> files = FileUtils.list(dir);
        assertNotNull("Check directory '"+ dir.toAbsolutePath() +"'.",files);
        
        // store some documents.
        for (int j = 0; j < 10; j++) {
            for (final Path f : files) {
                assertNotNull(f);
                if (Files.isRegularFile(f)) {
                    final XmldbURI uri = test2.getURI().append(j + "_" + FileUtils.fileName(f));

                    broker.storeDocument(transaction, uri, new FileInputSource(f), MimeType.BINARY_TYPE, test2);
                    final BinaryDocument doc = (BinaryDocument) test2.getDocument(broker, uri);
                    assertNotNull(doc);
                }
            }
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
