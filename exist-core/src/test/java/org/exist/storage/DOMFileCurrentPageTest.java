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
import java.util.Optional;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.TerminatedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that a page which has been freed is no longer the current page of the owner that was writing to it.
 *
 * A writer's current page is only forgotten by {@link DOMFile#closeDocument()}. If the page was freed
 * before that (for example, the document was removed), the next write of the same owner appended to a page
 * that is on the free list. Another owner could take the same page from the free list, and from then on two
 * documents shared one page, which made reads return missing records or another document's nodes.
 */
public class DOMFileCurrentPageTest {

    @RegisterExtension
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void freedPageIsNotCurrentPageOfItsOwner()
            throws EXistException, ReadOnlyException, IOException, BTreeException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final NodeIdFactory idFactory = pool.getNodeFactory();
        final NodeId nodeId = idFactory.createInstance(1);
        final Object ownerA = new Object();
        final Object ownerB = new Object();
        final byte[] dataA1 = "first record of A".getBytes(StandardCharsets.UTF_8);
        final byte[] dataB = "record of B".getBytes(StandardCharsets.UTF_8);
        final byte[] dataA2 = "second record of A".getBytes(StandardCharsets.UTF_8);

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            final TransactionManager transactionManager = pool.getTransactionManager();

            try (final Txn txn = transactionManager.beginTransaction()) {
                // owner A writes a record and keeps its current page (no closeDocument)
                domDb.setOwnerObject(ownerA);
                final long addressA1 = domDb.put(txn, new NativeBroker.NodeRef(900, nodeId), dataA1);
                final int pageA = StorageAddress.pageFromPointer(addressA1);

                // the document is removed the way NativeBroker does: its keys, then its pages
                domDb.remove(txn, new IndexQuery(IndexQuery.TRUNC_RIGHT, new NativeBroker.NodeRef(900)), null);
                domDb.removeAll(txn, addressA1);

                // owner B takes the freed page from the free list
                domDb.setOwnerObject(ownerB);
                final long addressB = domDb.put(txn, new NativeBroker.NodeRef(901, nodeId), dataB);
                final int pageB = StorageAddress.pageFromPointer(addressB);
                assertEquals(pageA, pageB, "owner B was expected to reuse the freed page");

                // owner A writes again, it must not append to the page that now belongs to B
                domDb.setOwnerObject(ownerA);
                final long addressA2 = domDb.put(txn, new NativeBroker.NodeRef(902, nodeId), dataA2);
                assertNotEquals(pageB, StorageAddress.pageFromPointer(addressA2),
                        "owner A appended to a page it no longer owns");

                domDb.closeDocument();
                domDb.setOwnerObject(ownerB);
                domDb.closeDocument();

                final Value valueB = domDb.get(addressB);
                assertNotNull(valueB);
                assertArrayEquals(dataB, valueB.getData());
                final Value valueA2 = domDb.get(addressA2);
                assertNotNull(valueA2);
                assertArrayEquals(dataA2, valueA2.getData());

                transactionManager.abort(txn);
            }
        }
    }
}
