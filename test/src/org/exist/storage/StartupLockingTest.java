/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.LockEventJsonListener;
import org.exist.storage.lock.LockEventXmlListener;
import org.exist.storage.lock.LockTable;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Simple test that Starts the database and checks that no Collection Locks are still held
 * once the database is at rest
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class StartupLockingTest {

    private static LockCountListener lockCountListener = new LockCountListener();

    /**
     * Very useful for debugging lock life cycles
     */
//    private static LockEventJsonListener lockEventJsonListener = new LockEventJsonListener(Paths.get("/tmp/startupLockingTest" + System.currentTimeMillis() + ".json"), true);
//    private static LockEventXmlListener lockEventXmlListener = new LockEventXmlListener(Paths.get("/tmp/startupLockingTest" + System.currentTimeMillis() + ".xml"), true);

    private static LockTable lockTable = LockTable.getInstance();
    static {
        lockTable.registerListener(lockCountListener);
        while(!lockCountListener.isRegistered()) {}
//        lockTable.registerListener(lockEventJsonListener);
//        while(!lockEventJsonListener.isRegistered()) {}
//        lockTable.registerListener(lockEventXmlListener);
//        while(!lockEventXmlListener.isRegistered()) {}
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterClass
    public static void removeListener() {
        lockTable.deregisterListener(lockCountListener);
//        lockTable.deregisterListener(lockEventJsonListener);
//        lockTable.deregisterListener(lockEventXmlListener);

        while(lockCountListener.isRegistered()) {}
//        while(lockEventJsonListener.isRegistered()) {}
//        while(lockEventXmlListener.isRegistered()) {}
    }

    /**
     * Checks that there are no dangling Collection locks
     * once eXist has finished starting up
     *
     * A failure of this test indicates either:
     *   1) Locks have been acquired but not released
     *   2) A bug has been introduced in {@link org.exist.storage.lock.LockManager}
     */
    @Test
    public void noCollectionLocksAfterStartup() throws InterruptedException {
        lockTable.deregisterListener(lockCountListener);

        // wait for the listener to be deregistered
        while(lockCountListener.isRegistered()) {}

        //check all locks are zero!
        final Tuple2<Long, Long> lockCount = lockCountListener.getlockCount();
        assertEquals(0l, lockCount._1.longValue());
        assertEquals(0l, lockCount._2.longValue());
    }

    /**
     * Checks that no locks are leaked by {@link NativeBroker#getOrCreateCollectionExplicit(Txn, XmldbURI)}
     *
     * That is to say the number of read and write locks held after a call
     * to {@link NativeBroker#getOrCreateCollectionExplicit(Txn, XmldbURI)} should be the same
     * as before the call was made
     */
    @Test
    public void getOrCreateCollectionDoesNotGainLocks() throws InterruptedException, EXistException, PermissionDeniedException, IOException, TriggerException {
        lockTable.deregisterListener(lockCountListener);

        // wait for the listener to be deregistered
        while(lockCountListener.isRegistered()) {}

        final Tuple2<Long, Long> preLockCount = lockCountListener.getlockCount();

        lockTable.registerListener(lockCountListener);
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.create("/db/a"))) {
                assertNotNull(collection);
            }

            transaction.commit();

        } finally {
            lockTable.deregisterListener(lockCountListener);
        }

        // wait for the listener to be deregistered
        while(lockCountListener.isRegistered()) {}


        //check that we haven't gained any locks
        final Tuple2<Long, Long> postLockCount = lockCountListener.getlockCount();
        assertEquals(preLockCount._1.longValue(), postLockCount._1.longValue());
        assertEquals(preLockCount._2.longValue(), postLockCount._2.longValue());
    }

    @ThreadSafe
    private static class LockCountListener implements LockTable.LockEventListener {
        // holds a Map of all locks whose read or write count is greater than zero
        //  <lockId, Tuple2<readCount, writeCount>>
        private final Map<String, Tuple2<Long, Long>> lockReadWriteCount = new TreeMap<>();

        private final AtomicBoolean registered = new AtomicBoolean();

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

        /**
         * @return Tuple2<readCount, writeCount>
         */
        public Tuple2<Long, Long> getlockCount() {
            synchronized (lockReadWriteCount) {
                long readCount = 0;
                long writeCount = 0;
                for(final Tuple2<Long, Long> value : lockReadWriteCount.values()) {
                    readCount += value._1;
                    writeCount += value._2;
                }
                return new Tuple2<>(readCount, writeCount);
            }
        }

        @Override
        public void accept(final LockTable.LockAction lockAction) {
            synchronized (lockReadWriteCount) {
                final long change;
                switch(lockAction.action) {
                    case Acquired:
                        change = 1;
                        break;

                    case Released:
                        change = -1;
                        break;

                    default:
                        change = 0;
                }

                Tuple2<Long, Long> value = lockReadWriteCount.get(lockAction.id);
                if(value == null) {
                    value = new Tuple2<>(0l, 0l);
                }

                switch(lockAction.mode) {
                    case READ_LOCK:
                        value = new Tuple2<>(value._1 + change, value._2);
                        break;

                    case WRITE_LOCK:
                        value = new Tuple2<>(value._1, value._2 + change);
                        break;
                }

                if(value._1 < 0 || value._2 < 0) {
                    throw new IllegalStateException("Cannot have less than zero locks!");
                }

                if(value._1 == 0 && value._2 == 0) {
                    lockReadWriteCount.remove(lockAction.id);
                } else {
                    lockReadWriteCount.put(lockAction.id, value);
                }
            }
        }
    }
}
