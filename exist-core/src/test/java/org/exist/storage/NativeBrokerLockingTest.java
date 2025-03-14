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

import net.jcip.annotations.ThreadSafe;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockTable;
import org.exist.storage.lock.LockTable.LockEventListener;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Tests to check that the acquire/release lease lifetimes
 * of various locks used by {@link NativeBroker} functions
 * are symmetrical
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class NativeBrokerLockingTest {

    private final static XmldbURI TEST_COLLECTION =  XmldbURI.ROOT_COLLECTION_URI.append("test");
    private final static XmldbURI COLLECTION_A =  TEST_COLLECTION.append("colA");
    private final static XmldbURI COLLECTION_B =  TEST_COLLECTION.append("colB");

    private final static int TRACE_STACK_DEPTH = 5;

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void setupTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            createCollection(broker, transaction, TEST_COLLECTION);
            createCollection(broker, transaction, COLLECTION_A);
            createCollection(broker, transaction, COLLECTION_B);

            transaction.commit();
        }
    }

    private Collection createCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws PermissionDeniedException, IOException, TriggerException {
        final Collection collection = broker.getOrCreateCollection(transaction, uri);
        broker.saveCollection(transaction, collection);
        return collection;
    }

    @After
    public void removeTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getCollection(TEST_COLLECTION);
            if(testCollection != null) {
                if(!broker.removeCollection(transaction, testCollection)) {
                    transaction.abort();
                    fail("Unable to remove test collection");
                }
            }

            transaction.commit();
        }
    }

    @Test
    public void openCollection() throws EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                try(final Collection collectionA = broker.openCollection(COLLECTION_A, LockMode.READ_LOCK)) {
                    //no -op
                }

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void openCollection_doesntExist() throws EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                try(final Collection collectionNone = broker.openCollection(COLLECTION_A.append("none"), LockMode.READ_LOCK)) {
                    assertNull(collectionNone);
                }

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void getCollection() throws EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                final Collection collectionA = broker.getCollection(COLLECTION_B);
                assertNotNull(collectionA);

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void getCollection_doesntExist() throws EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());
                registered = true;

                final Collection collectionNone = broker.getCollection(COLLECTION_B.append("none"));
                assertNull(collectionNone);

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void getOrCreateCollection() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                final XmldbURI collectionC = COLLECTION_B.append("colC");
                try(final Collection collectionA = broker.getOrCreateCollection(transaction, collectionC)) {
                    // no-op
                }

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void moveCollection() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                final Collection collectionA = broker.getCollection(COLLECTION_A);
                final Collection collectionB = broker.getCollection(COLLECTION_B);

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                broker.moveCollection(transaction, collectionA, collectionB, XmldbURI.create("colA"));

                transaction.commit();
            }
        } finally {
            if (registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void copyEmptyCollection() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                final Collection collectionA = broker.getCollection(COLLECTION_A);
                final Collection collectionB = broker.getCollection(COLLECTION_B);

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                broker.copyCollection(transaction, collectionA, collectionB, XmldbURI.create("colA"));

                transaction.commit();
            }
        } finally {
            if(registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }

    @Test
    public void removeEmptyCollection() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final LockTable lockTable = brokerPool.getLockManager().getLockTable();
        lockTable.setTraceStackDepth(TRACE_STACK_DEPTH);

        final LockSymmetryListener lockSymmetryListener = new LockSymmetryListener();
        boolean registered = false;
        try {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

                final Collection collectionA = broker.getCollection(COLLECTION_A);

                lockTable.registerListener(lockSymmetryListener);
                // wait for the listener to be registered
                while(!lockSymmetryListener.isRegistered());

                registered = true;
                broker.removeCollection(transaction, collectionA);

                transaction.commit();
            }
        } finally {
            if (registered) {
                lockTable.deregisterListener(lockSymmetryListener);
            }
        }

        // wait for the listener to be deregistered
        while(lockSymmetryListener.isRegistered()) {}

        assertTrue(lockSymmetryListener.isSymmetrical());
    }


    @ThreadSafe
    private static class LockSymmetryListener implements LockEventListener {
        private final Stack<LockAction> events = new Stack<>();
        private final Stack<LockAction> eventsAfterError = new Stack<>();

        private final AtomicBoolean registered = new AtomicBoolean();
        private final AtomicBoolean error = new AtomicBoolean();   // indicates if lock acquire/release is no longer symmetrical

        @Override
        public void registered() {
            registered.compareAndSet(false, true);
        }

        @Override
        public void unregistered() {
            registered.compareAndSet(true, false);
        }

        public boolean isRegistered() {
            return registered.get();
        }

        public boolean isSymmetrical() {
            return !error.get() && events.empty();
        }

        @Override
        public void accept(final LockTable.LockEventType lockEventType, final long timestamp, final long groupId,
                final LockTable.Entry entry) {

            // read count first to ensure memory visibility from volatile!
            final int localCount = entry.getCount();

            // ignore sync events
            final StackTraceElement[] stackTrace;
            if (entry.getStackTraces() != null && !entry.getStackTraces().isEmpty()) {
                stackTrace = entry.getStackTraces().getFirst();

                final String reason = LockTable.getSimpleStackReason(stackTrace);
                if (reason != null && ("sync".equals(reason) || "notifySync".equals(reason))) {
                    return;
                }
            } else {
                stackTrace = null;
            }

//            System.out.println(LockTable.formatString(lockEventType, groupId, entry.getId(), entry.getLockType(),
//                    entry.getLockMode(), entry.getOwner(), localCount, timestamp,
//                    stackTrace));

            final LockAction lockAction = new LockAction(lockEventType, groupId, entry.getId(), entry.getLockType(),
                    entry.getLockMode(), entry.getOwner(), localCount, timestamp,
                    stackTrace);

            if(error.get()) {
                eventsAfterError.push(lockAction);
                return;
            }

            switch(lockEventType) {
                case Attempt:
                    events.push(lockAction);
                    break;

                case Acquired:
                    if(isAcquireAfterAttempt(lockAction)) {
                        //OK
                        events.push(lockAction);
                    } else {
                        //error
                        error.set(true);
                    }
                    break;

                case Released:
                    if(isSymmetricalRelease(lockAction)) {
                        final LockAction acquired = events.pop();
                        if(isAcquireAfterAttempt(acquired)) {
                            final LockAction attempt = events.pop();
                            if(attempt != null) {
                                //OK
                                break;
                            }
                        }
                    }
                    //error
                    error.set(true);
                    break;

                default:
                    throw new IllegalStateException(lockEventType + " should not happen!");
            }
        }

        private boolean isAcquireAfterAttempt(final LockAction current) {
            final LockAction previous = events.peek();
            return previous.lockEventType == LockTable.LockEventType.Attempt
                    && isRelated(previous, current);
        }

        private boolean isSymmetricalRelease(final LockAction current) {
            final LockAction previous = events.peek();
            return previous.lockEventType == LockTable.LockEventType.Acquired
                    && isRelated(previous, current);
        }

        private boolean isRelated(final LockAction lockAction1, final LockAction lockAction2) {
            return lockAction1.lockType.equals(lockAction2.lockType)
                    && lockAction1.id.equals(lockAction2.id);
        }
    }

    private static class LockAction {
        public final LockTable.LockEventType lockEventType;
        public final long groupId;
        public final String id;
        public final Lock.LockType lockType;
        public final Lock.LockMode mode;
        public final String threadName;
        public final int count;
        /**
         * System#nanoTime()
         */
        public final long timestamp;
        @Nullable
        public final StackTraceElement[] stackTrace;

        private LockAction(final LockTable.LockEventType lockEventType, final long groupId, final String id,
                final Lock.LockType lockType, final Lock.LockMode mode, final String threadName, final int count,
                final long timestamp, @Nullable final StackTraceElement[] stackTrace) {
            this.lockEventType = lockEventType;
            this.groupId = groupId;
            this.id = id;
            this.lockType = lockType;
            this.mode = mode;
            this.threadName = threadName;
            this.count = count;
            this.timestamp = timestamp;
            this.stackTrace = stackTrace;
        }
    }
}
