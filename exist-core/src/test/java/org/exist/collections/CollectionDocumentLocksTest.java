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
package org.exist.collections;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.LockTable;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks which document locks {@code fn:collection()} takes: none when no document in the collection
 * is being written, and a READ_LOCK on each document otherwise.
 */
public class CollectionDocumentLocksTest {

    private static final String COLLECTION = "/db/document-locks";
    private static final int DOCUMENTS = 10;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeAll
    public static void setUp() throws EXistException, PermissionDeniedException, XPathException {
        query("""
                let $collection := xmldb:create-collection("/db", "document-locks")
                for $i in 1 to %d
                return xmldb:store($collection, "doc" || $i || ".xml", <doc/>)
                """.formatted(DOCUMENTS));
        query("""
                xmldb:create-collection("/db", "other-collection")
                """);
    }

    @Test
    void noDocumentLocksWithoutWrites() throws Exception {
        assertEquals(0, documentReadLocks());
    }

    @Test
    void noDocumentLocksWhileADocumentInAnotherCollectionIsWritten() throws Exception {
        final LockManager lockManager = existEmbeddedServer.getBrokerPool().getLockManager();
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(XmldbURI.create("/db/other-collection/doc.xml"))) {
            assertEquals(0, documentReadLocks());
        }
    }

    @Test
    void aReadLockOnEachDocumentWhileADocumentInTheCollectionIsWritten() throws Exception {
        final LockManager lockManager = existEmbeddedServer.getBrokerPool().getLockManager();
        try (final ManagedDocumentLock ignored = lockManager.acquireDocumentWriteLock(XmldbURI.create(COLLECTION + "/absent.xml"))) {
            assertEquals(DOCUMENTS, documentReadLocks());
        }
    }

    /**
     * Counts the document READ_LOCKs which {@code count(collection(...))} takes.
     */
    private static int documentReadLocks() throws Exception {
        final LockTable lockTable = existEmbeddedServer.getBrokerPool().getLockManager().getLockTable();
        final AtomicBoolean registered = new AtomicBoolean();
        final AtomicInteger readLocks = new AtomicInteger();
        final LockTable.LockEventListener listener = new LockTable.LockEventListener() {
            @Override
            public void registered() {
                registered.set(true);
            }

            @Override
            public void accept(final LockTable.LockEventType lockEventType, final long timestamp, final long groupId, final LockTable.Entry entry) {
                // read count first to ensure memory visibility of the entry's other fields
                entry.getCount();
                if (lockEventType == LockTable.LockEventType.Acquired && entry.getLockType() == LockType.DOCUMENT
                        && entry.getLockMode() == LockMode.READ_LOCK && entry.getId().startsWith(COLLECTION + "/")) {
                    readLocks.incrementAndGet();
                }
            }

            @Override
            public void unregistered() {
                registered.set(false);
            }
        };

        lockTable.registerListener(listener);
        try {
            while (!registered.get()) {
                Thread.sleep(1);
            }
            assertEquals(DOCUMENTS, query("count(collection(\"" + COLLECTION + "\"))").itemAt(0).toJavaObject(Integer.class));
        } finally {
            lockTable.deregisterListener(listener);
        }
        // the listener is called asynchronously, and is unregistered after it has been called for every earlier event
        while (registered.get()) {
            Thread.sleep(1);
        }
        return readLocks.get();
    }

    private static Sequence query(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
