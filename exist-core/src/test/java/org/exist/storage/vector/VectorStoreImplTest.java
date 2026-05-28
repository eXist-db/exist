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
package org.exist.storage.vector;

import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VectorStoreImplTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private VectorStoreImpl store;

    @Before
    public void setUp() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final VectorStore vectorStore = pool.getVectorStore();
        assertNotNull(vectorStore);
        assertTrue(vectorStore instanceof VectorStoreImpl);
        store = (VectorStoreImpl) vectorStore;
        store.resetEntryCountCache();
    }

    @Test
    public void entryCountTracksPutAndRemove() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager mgr = pool.getTransactionManager();
        final NodeId nodeId = pool.getNodeFactory().createInstance();

        assertEquals(0, store.getEntryCount());

        try (final Txn txn = mgr.beginTransaction()) {
            store.put(txn, "/db/test/doc.xml", nodeId, new byte[]{0, 0, 0, 0});
            mgr.commit(txn);
        }
        assertEquals(1, store.getEntryCount());

        try (final Txn txn = mgr.beginTransaction()) {
            store.put(txn, "/db/test/doc.xml", nodeId, new byte[]{1, 0, 0, 0});
            mgr.commit(txn);
        }
        assertEquals(1, store.getEntryCount());

        try (final Txn txn = mgr.beginTransaction()) {
            store.remove(txn, "/db/test/doc.xml", nodeId);
            mgr.commit(txn);
        }
        assertEquals(0, store.getEntryCount());
    }

    @Test
    public void removeByDocumentAdjustsEntryCount() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager mgr = pool.getTransactionManager();
        final NodeId nodeId1 = pool.getNodeFactory().createInstance(1);
        final NodeId nodeId2 = pool.getNodeFactory().createInstance(2);

        try (final Txn txn = mgr.beginTransaction()) {
            store.put(txn, "/db/test/doc2.xml", nodeId1, new byte[]{0, 0, 0, 0});
            store.put(txn, "/db/test/doc2.xml", nodeId2, new byte[]{1, 0, 0, 0});
            mgr.commit(txn);
        }
        assertEquals(2, store.getEntryCount());

        try (final Txn txn = mgr.beginTransaction()) {
            store.removeByDocument(txn, "/db/test/doc2.xml");
            mgr.commit(txn);
        }
        assertEquals(0, store.getEntryCount());
    }

    @Test
    public void resetEntryCountCacheForcesRescan() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager mgr = pool.getTransactionManager();
        final NodeId nodeId = pool.getNodeFactory().createInstance();

        try (final Txn txn = mgr.beginTransaction()) {
            store.put(txn, "/db/test/doc3.xml", nodeId, new byte[]{0, 0, 0, 0});
            mgr.commit(txn);
        }
        assertEquals(1, store.getEntryCount());
        store.resetEntryCountCache();
        assertTrue(store.getEntryCount() >= 1);
    }
}
