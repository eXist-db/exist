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

import static org.junit.Assert.*;
import static org.exist.samples.Samples.SAMPLES;

import java.util.Optional;

import org.exist.collections.*;
import org.exist.storage.txn.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.InputStreamSupplierInputSource;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

public class RemoveRootCollectionTest {

    private DBBroker broker;
    Collection root;

    @Test
    public void removeEmptyRootCollection() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Test
    public void removeRootCollectionWithChildCollection() throws Exception {
        addChildToRoot();
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Ignore
    @Test
    public void removeRootCollectionWithDocument() throws Exception {
        addDocumentToRoot();
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void startDB() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
        root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
    }

    @After
    public void stopDB() {
        if (broker != null) {
            broker.close();
        }
    }

    private void addDocumentToRoot() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            root.storeDocument(transaction, broker, XmldbURI.create("hamlet.xml"), new InputStreamSupplierInputSource(() -> SAMPLES.getHamletSample()), MimeType.XML_TYPE);
            transact.commit(transaction);
        }
    }

    private void addChildToRoot() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            final Collection child = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("child"));
            broker.saveCollection(transaction, child);
            transact.commit(transaction);
        }
    }
}
