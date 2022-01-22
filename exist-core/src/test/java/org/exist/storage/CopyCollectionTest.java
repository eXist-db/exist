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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.*;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests to ensure that collections
 * are correctly copied under various circumstances.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CopyCollectionTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    /**
     * Test copy collection /db/a/b/c/d/e/f/g/h/i/j/k to /db/z/y/x/w/v/u/k
     */
    @Test
    public void copyDeep() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final XmldbURI srcUri = XmldbURI.create("/db/a/b/c/d/e/f/g/h/i/j/k");
        final XmldbURI destUri = XmldbURI.create("/db/z/y/x/w/v/u");
        final XmldbURI newName = srcUri.lastSegment();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            Collection src = broker.getOrCreateCollection(transaction, srcUri);
            assertNotNull(src);
            broker.saveCollection(transaction, src);

            Collection dst = broker.getOrCreateCollection(transaction, destUri);
            assertNotNull(dst);
            broker.saveCollection(transaction, dst);

            src = null;
            dst = null;
            try {
                src = broker.openCollection(srcUri, Lock.LockMode.WRITE_LOCK);
                assertNotNull(src);
                dst = broker.openCollection(destUri, Lock.LockMode.WRITE_LOCK);
                assertNotNull(dst);

                broker.copyCollection(transaction, src, dst, newName);
            } finally {
                if (dst != null) {
                    dst.getLock().release(Lock.LockMode.WRITE_LOCK);
                }

                if (src != null) {
                    src.getLock().release(Lock.LockMode.WRITE_LOCK);
                }
            }

            transact.commit(transaction);
        }

        // check that the source still exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            Collection src = null;
            try {
                src = broker.openCollection(srcUri, Lock.LockMode.READ_LOCK);
                assertNotNull(src);
            } finally {
                if (src != null) {
                    src.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            transaction.commit();
        }

        // check that the new copy exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI copyUri = destUri.append(newName);

            Collection copy = null;
            try {
                copy = broker.openCollection(copyUri, Lock.LockMode.READ_LOCK);
                assertNotNull(copy);
            } finally {
                if (copy != null) {
                    copy.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            transaction.commit();
        }
    }

    /**
     * Test copy collection /db/a/b/c/d/e/f/g/h/i/j/k to /db/z/y/x/w/v/u/k
     *
     * Note that the collection /db/a/b/c/d/e/f/g/h/i/j/k has the sub-collections (sub-1 and sub-2),
     * this test checks that the sub-collections are correctly preserved.
     */
    @Test
    public void copyDeepWithSubCollections() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final XmldbURI srcUri = XmldbURI.create("/db/a/b/c/d/e/f/g/h/i/j/k");
        final XmldbURI srcSubCol1Uri = srcUri.append("sub-1");
        final XmldbURI srcSubCol2Uri = srcUri.append("sub-2");
        final XmldbURI destUri = XmldbURI.create("/db/z/y/x/w/v/u");
        final XmldbURI newName = srcUri.lastSegment();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            // create src collection
            Collection src = broker.getOrCreateCollection(transaction, srcUri);
            assertNotNull(src);
            broker.saveCollection(transaction, src);

            // create src sub-collections
            final Collection srcColSubCol1 = broker.getOrCreateCollection(transaction, srcSubCol1Uri);
            assertNotNull(srcColSubCol1);
            broker.saveCollection(transaction, srcColSubCol1);
            final Collection srcColSubCol2 = broker.getOrCreateCollection(transaction, srcSubCol2Uri);
            assertNotNull(srcColSubCol2);
            broker.saveCollection(transaction, srcColSubCol2);

            // create dst collection
            Collection dst = broker.getOrCreateCollection(transaction, destUri);
            assertNotNull(dst);
            broker.saveCollection(transaction, dst);

            src = null;
            dst = null;
            try {
                src = broker.openCollection(srcUri, Lock.LockMode.WRITE_LOCK);
                assertNotNull(src);
                dst = broker.openCollection(destUri, Lock.LockMode.WRITE_LOCK);
                assertNotNull(dst);

                broker.copyCollection(transaction, src, dst, newName);
            } finally {
                if (dst != null) {
                    dst.getLock().release(Lock.LockMode.WRITE_LOCK);
                }

                if (src != null) {
                    src.getLock().release(Lock.LockMode.WRITE_LOCK);
                }
            }

            transact.commit(transaction);
        }

        // check that the source still exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            Collection src = null;
            try {
                src = broker.openCollection(srcUri, Lock.LockMode.READ_LOCK);
                assertNotNull(src);
            } finally {
                if (src != null) {
                    src.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            // check that the source sub-collections still exist
            Collection srcSubCol1 = null;
            try {
                srcSubCol1 = broker.openCollection(srcSubCol1Uri, Lock.LockMode.READ_LOCK);
                assertNotNull(srcSubCol1);
            } finally {
                if (srcSubCol1 != null) {
                    srcSubCol1.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }
            Collection srcSubCol2 = null;
            try {
                srcSubCol2 = broker.openCollection(srcSubCol2Uri, Lock.LockMode.READ_LOCK);
                assertNotNull(srcSubCol2);
            } finally {
                if (srcSubCol2 != null) {
                    srcSubCol2.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            transaction.commit();
        }

        // check that the new copy exists
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI copyUri = destUri.append(newName);

            Collection copy = null;
            try {
                copy = broker.openCollection(copyUri, Lock.LockMode.READ_LOCK);
                assertNotNull(copy);
            } finally {
                if (copy != null) {
                    copy.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            // check that the new copy has sub-collection copies
            Collection copySubCol1 = null;
            try {
                final XmldbURI copySubCol1Uri = copyUri.append(srcSubCol1Uri.lastSegment());
                copySubCol1 = broker.openCollection(copySubCol1Uri, Lock.LockMode.READ_LOCK);
                assertNotNull(copySubCol1);
            } finally {
                if (copySubCol1 != null) {
                    copySubCol1.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }
            Collection copySubCol2 = null;
            try {
                final XmldbURI copySubCol2Uri = copyUri.append(srcSubCol2Uri.lastSegment());
                copySubCol2 = broker.openCollection(copySubCol2Uri, Lock.LockMode.READ_LOCK);
                assertNotNull(copySubCol2);
            } finally {
                if (copySubCol2 != null) {
                    copySubCol2.getLock().release(Lock.LockMode.READ_LOCK);
                }
            }

            transaction.commit();
        }
    }
}
