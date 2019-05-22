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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
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
import static org.junit.Assert.assertNotNull;

public class RecoverBinaryTest2 {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static String directory = "webapp/resources";

    @Test
    public void storeAndRead() throws TriggerException, PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, EXistException {
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

    public void store(final BrokerPool pool) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException {
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

    public void read(final BrokerPool pool) throws EXistException, DatabaseConfigurationException, PermissionDeniedException, LockException, IOException, TriggerException {
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
    
    private void storeFiles(final DBBroker broker, final Txn transaction, final Collection test2) throws IOException, EXistException, PermissionDeniedException, LockException, TriggerException {
        // Get files in directory
        final Path dir = FileUtils.resolve(ConfigurationHelper.getExistHome(), directory);
        final List<Path> files = FileUtils.list(dir);
        assertNotNull("Check directory '"+dir.toAbsolutePath().toString()+"'.",files);
        
        // store some documents.
        for (int j = 0; j < 10; j++) {
            for (final Path f : files) {
                assertNotNull(f);
                if (Files.isRegularFile(f)) {
                    final XmldbURI uri = test2.getURI().append(j + "_" + FileUtils.fileName(f));
                    try(final InputStream is = Files.newInputStream(f)) {
                        final BinaryDocument doc =
                            test2.addBinaryResource(transaction, broker, uri, is, MimeType.BINARY_TYPE.getName(),
                                FileUtils.sizeQuietly(f), new Date(), new Date());
                        assertNotNull(doc);
                    }
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
