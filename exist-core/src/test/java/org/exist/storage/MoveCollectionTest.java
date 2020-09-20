/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class MoveCollectionTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void moveDeep() throws EXistException, IOException, PermissionDeniedException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI srcUri = XmldbURI.create("/db/a/b/c/d/e/f/g/h/i/j/k");
            final XmldbURI destUri = XmldbURI.create("/db/z/y/x/w/v/u");

            try (final Collection src = broker.getOrCreateCollection(transaction, srcUri)) {
                assertNotNull(src);
                broker.saveCollection(transaction, src);
            }

            try (final Collection dst = broker.getOrCreateCollection(transaction, destUri)) {
                assertNotNull(dst);
                broker.saveCollection(transaction, dst);
            }

            try (final Collection src = broker.openCollection(srcUri, Lock.LockMode.WRITE_LOCK);
                 final Collection dst = broker.openCollection(destUri, Lock.LockMode.WRITE_LOCK)) {

                broker.moveCollection(transaction, src, dst, src.getURI().lastSegment());
            }

            transact.commit(transaction);
        }
    }

    @Test
    public void rename() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final XmldbURI testColUri = XmldbURI.create("/db/move-collection-test-rename");
            final XmldbURI srcColUri = testColUri.append("before");
            final XmldbURI newName = XmldbURI.create("after");

            try (final Collection testCol = broker.getOrCreateCollection(transaction, testColUri)) {
                assertNotNull(testCol);
                broker.saveCollection(transaction, testCol);
            }

            try (final Collection srcCol = broker.getOrCreateCollection(transaction, srcColUri)) {
                assertNotNull(srcCol);
                broker.saveCollection(transaction, srcCol);
            }

            try (final Collection src = broker.openCollection(srcColUri, Lock.LockMode.WRITE_LOCK);
                 final Collection testCol = broker.openCollection(testColUri, Lock.LockMode.WRITE_LOCK)) {

                broker.moveCollection(transaction, src, testCol, newName);

                assertFalse(testCol.hasChildCollection(broker, srcColUri.lastSegment()));
                assertTrue(testCol.hasChildCollection(broker, newName));
            }

            transact.commit(transaction);
        }
    }
}
