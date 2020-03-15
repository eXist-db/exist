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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirtyShutdownTest {

    private static final Logger LOG = LogManager.getLogger(DirtyShutdownTest.class);

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);
    
    @Test
    public void run() {
        final ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(() -> storeRepeatedly());

        synchronized (this) {
            try {
                wait(5000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
            }
        }
    }

    public void storeRepeatedly() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection root;

            try(final Txn transaction = transact.beginTransaction()) {
                root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);
                transact.commit(transaction);
            }

            for (int i = 0; i < 50; i++) {
                try(final Txn transaction = transact.beginTransaction()) {

                    final URL url = getClass().getClassLoader().getResource("/samples/shakespeare/macbeth.xml");
                    final Path f = Paths.get(url.toURI());

                    final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), new InputSource(f.toUri().toASCIIString()));
                    assertNotNull(info);
                    root.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

                    transact.commit(transaction);
                }
            }
        } catch (final PermissionDeniedException | EXistException | URISyntaxException | SAXException | LockException | IOException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }
}
