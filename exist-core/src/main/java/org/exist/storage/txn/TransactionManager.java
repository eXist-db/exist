/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.txn;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.journal.JournalException;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.sync.Sync;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Transaction Manager provides methods to begin, commit, and abort
 * transactions.
 *
 * This implementation of the transaction manager is non-blocking lock-free.
 * It makes use of several CAS variables to ensure thread-safe concurrent
 * access. The most important of which is {@link #state} which indicates
 * either:
 *     1) the number of active transactions
 *     2) that the Transaction Manager is executing system
 *         tasks ({@link #STATE_SYSTEM}), during which time no
 *         other transactions are active.
 *     3) that the Transaction Manager has (or is)
 *         been shutdown ({@link #STATE_SHUTDOWN}).
 *
 * NOTE: the Transaction Manager may optimistically briefly enter
 *     the state {@link #STATE_SYSTEM} to block the initiation of
 *     new transactions and then NOT execute system tasks if it
 *     detects concurrent active transactions.
 *
 * System tasks are mutually exclusive with any other operation
 * including shutdown. When shutdown is requested, if system tasks
 * are executing, then the thread will spin until they are finished.
 * 
 * There's only one TransactionManager per database instance, it can be
 * accessed via {@link BrokerPool#getTransactionManager()}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author wolf
 */
@ThreadSafe
public class TransactionManager implements BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(TransactionManager.class);

    private final BrokerPool pool;
    private final Optional<JournalManager> journalManager;
    private final SystemTaskManager systemTaskManager;

    /**
     * The next transaction id
     */
    private final AtomicLong nextTxnId = new AtomicLong();

    /**
     * Currently active transactions and their operations journal write count.
     *  Key is the transaction id
     *  Value is the transaction's operations journal write count.
     */
    private final ConcurrentHashMap<Long, TxnCounter> transactions = new ConcurrentHashMap<>();

    /**
     * State for when the Transaction Manager has been shutdown.
     */
    private static final int STATE_SHUTDOWN = -2;

    /**
     * State for when the Transaction Manager has executing system tasks.
     */
    private static final int STATE_SYSTEM = -1;

    /**
     * State for when the Transaction Manager is idle, i.e. no active transactions.
     */
    private static final int STATE_IDLE = 0;

    /**
     * State of the transaction manager.
     *
     * Will be either {@link #STATE_SHUTDOWN}, {@link #STATE_SYSTEM},
     * {@link #STATE_IDLE} or a non-zero positive integer which
     * indicates the number of active transactions.
     */
    private final AtomicInteger state = new AtomicInteger(STATE_IDLE);

    /**
     * Id of the thread which is executing system tasks when
     * the {@link #state} == {@link #STATE_SYSTEM}. This
     * is used for reentrancy when system tasks need to
     * make transactional operations.
     */
    private final AtomicLong systemThreadId = new AtomicLong(-1);


    /**
     * Constructs a transaction manager for a Broker Pool.
     * 
     * @param pool the broker pool
     * @param journalManager the journal manager
     * @param systemTaskManager the system task manager
     */
    public TransactionManager(final BrokerPool pool, final Optional<JournalManager> journalManager,
            final SystemTaskManager systemTaskManager) {
        this.pool = pool;
        this.journalManager = journalManager;
        this.systemTaskManager = systemTaskManager;
    }

    private static void throwShutdownException() {
        //TODO(AR) API should be revised in future so that this is a TransactionException
        throw new RuntimeException("Transaction Manager is shutdown");
    }

    /**
     * Create a new transaction.
     *
     * @return the new transaction
     */
    public Txn beginTransaction() {
        try {
            // CAS loop
            while (true) {
                final int localState = state.get();

                // can NOT begin transaction when shutdown!
                if (localState == STATE_SHUTDOWN) {
                    throwShutdownException();
                }

                // must NOT begin transaction when another thread is processing system tasks!
                if (localState == STATE_SYSTEM) {
                    final long thisThreadId = Thread.currentThread().getId();
                    if (systemThreadId.compareAndSet(thisThreadId, thisThreadId)) {
                        // our thread is executing system tasks, allow reentrancy from our thread!

                        // done... return from CAS loop!
                        return doBeginTransaction();

                    } else {
                        // spin whilst another thread executes the system tasks
                        // sleep a small time to save CPU
                        Thread.sleep(10);
                        continue;
                    }
                }

                // if we are operational and are not preempted by another thread, begin transaction
                if (localState >= STATE_IDLE && state.compareAndSet(localState, localState + 1)) {
                    // done... return from CAS loop!
                    return doBeginTransaction();
                }
            }
        } catch (final InterruptedException e) {
            // thrown by Thread.sleep
            Thread.currentThread().interrupt();
            //TODO(AR) API should be revised in future so that this is a TransactionException
            throw new RuntimeException(e);
        }
    }

    private Txn doBeginTransaction() {
        final long txnId = nextTxnId.getAndIncrement();
        if (journalManager.isPresent()) {
            try {
                journalManager.get().journal(new TxnStart(txnId));
            } catch (final JournalException e) {
                LOG.error("Failed to create transaction. Error writing to Journal", e);
            }
        }

        /*
         * NOTE: we intentionally increment the txn counter here
         *     to set the counter to 1 to represent the TxnStart,
         *     as that will not be done
         *     by {@link JournalManager#journal(Loggable)} or
         *     {@link Journal#writeToLog(loggable)}.
         */
        transactions.put(txnId, new TxnCounter().increment());
        final Txn txn = new Txn(this, txnId);

        // TODO(AR) ultimately we should be doing away with DBBroker#setCurrentTransaction
        try(final DBBroker broker = pool.getBroker()) {
            broker.setCurrentTransaction(txn);
        } catch(final EXistException ee) {
            LOG.fatal(ee.getMessage(), ee);
            throw new RuntimeException(ee);
        }

        return txn;
    }
    
    /**
     * Commit a transaction.
     * 
     * @param txn the transaction to commit.
     *
     * @throws TransactionException if the transaction could not be committed.
     */
    public void commit(final Txn txn) throws TransactionException {
        Objects.requireNonNull(txn);

        if(txn instanceof Txn.ReusableTxn) {
            txn.commit();
            return;
            //throw new IllegalStateException("Commit should be called on the transaction and not via the TransactionManager"); //TODO(AR) remove later when API is cleaned up?
        }

        //we can only commit something which is in the STARTED state
        if (txn.getState() != Txn.State.STARTED) {
            return;
        }

        // CAS loop
        try {
            while (true) {
                final int localState = state.get();

                // can NOT commit transaction when shutdown!
                if (localState == STATE_SHUTDOWN) {
                    throwShutdownException();
                }

                // must NOT commit transaction when another thread is processing system tasks!
                if (localState == STATE_SYSTEM) {
                    final long thisThreadId = Thread.currentThread().getId();
                    if (systemThreadId.compareAndSet(thisThreadId, thisThreadId)) {
                        // our thread is executing system tasks, allow reentrancy from our thread!
                        doCommitTransaction(txn);

                        // done... exit CAS loop!
                        return;

                    } else {
                        // spin whilst another thread executes the system tasks
                        // sleep a small time to save CPU
                        Thread.sleep(10);
                        continue;
                    }
                }

                // if we are have active transactions and are not preempted by another thread, commit transaction
                if (localState > STATE_IDLE && state.compareAndSet(localState, localState - 1)) {
                    doCommitTransaction(txn);

                    // done... exit CAS loop!
                    return;
                }
            }
        } catch (final InterruptedException e) {
            // thrown by Thread.sleep
            Thread.currentThread().interrupt();
            //TODO(AR) API should be revised in future so that this is a TransactionException
            throw new RuntimeException(e);
        }
    }

    private void doCommitTransaction(final Txn txn) throws TransactionException {
        if (journalManager.isPresent()) {
            try {
                journalManager.get().journalGroup(new TxnCommit(txn.getId()));
            } catch (final JournalException e) {
                throw new TransactionException("Failed to write commit record to journal: " + e.getMessage(), e);
            }
        }

        txn.signalCommit();
        txn.releaseAll();

        transactions.remove(txn.getId());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Committed transaction: " + txn.getId());
        }
    }

    /**
     * Abort a transaction.
     *
     * @param txn the transaction to abort.
     */
    public void abort(final Txn txn) {
        Objects.requireNonNull(txn);

        //we can only abort something which is in the STARTED state
        if (txn.getState() != Txn.State.STARTED) {
            return;
        }

        // CAS loop
        try {
            while (true) {
                final int localState = state.get();

                // can NOT abort transaction when shutdown!
                if (localState == STATE_SHUTDOWN) {
                    throwShutdownException();
                }

                // must NOT abort transaction when another thread is processing system tasks!
                if (localState == STATE_SYSTEM) {
                    final long thisThreadId = Thread.currentThread().getId();
                    if (systemThreadId.compareAndSet(thisThreadId, thisThreadId)) {
                        // our thread is executing system tasks, allow reentrancy from our thread!
                        doAbortTransaction(txn);

                        // done... exit CAS loop!
                        return;

                    } else {
                        // spin whilst another thread executes the system tasks
                        // sleep a small time to save CPU
                        Thread.sleep(10);
                        continue;
                    }
                }

                // if we are have active transactions and are not preempted by another thread, abort transaction
                if (localState > STATE_IDLE && state.compareAndSet(localState, localState - 1)) {
                    doAbortTransaction(txn);

                    // done... exit CAS loop!
                    return;
                }
            }
        } catch (final InterruptedException e) {
            // thrown by Thread.sleep
            Thread.currentThread().interrupt();
            //TODO(AR) API should be revised in future so that this is a TransactionException
            throw new RuntimeException(e);
        }
    }

    private void doAbortTransaction(final Txn txn) {
        if (journalManager.isPresent()) {
            try {
                journalManager.get().journalGroup(new TxnAbort(txn.getId()));
            } catch (final JournalException e) {
                //TODO(AR) should revise the API in future to throw TransactionException
                LOG.error("Failed to write abort record to journal: " + e.getMessage(), e);
            }
        }

        txn.signalAbort();
        txn.releaseAll();

        transactions.remove(txn.getId());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Aborted transaction: " + txn.getId());
        }
    }

    /**
     * Close the transaction.
     *
     * Ensures that the transaction has either been committed or aborted.
     *
     * @param txn the transaction to close
     */
    public void close(final Txn txn) {
        Objects.requireNonNull(txn);

        //if the transaction is already closed, do nothing
        if (txn.getState() == Txn.State.CLOSED) {
            return;
        }
        try {
            //if the transaction is started, then we should auto-abort the uncommitted transaction
            if (txn.getState() == Txn.State.STARTED) {
                LOG.warn("Transaction was not committed or aborted, auto aborting!");
                abort(txn);
            }

            // TODO(AR) ultimately we should be doing away with DBBroker#setCurrentTransaction
            try(final DBBroker broker = pool.getBroker()) {
                broker.setCurrentTransaction(null);
            } catch(final EXistException ee) {
                LOG.fatal(ee.getMessage(), ee);
                throw new RuntimeException(ee);
            }

        } finally {
            txn.setState(Txn.State.CLOSED); //transaction is now closed!
        }

        processSystemTasks();
    }

    /**
     * Keep track of a new operation within the given transaction.
     *
     * @param txnId the transaction id.
     */
    public void trackOperation(final long txnId) {
        transactions.get(txnId).increment();
    }

    /**
     * Create a new checkpoint. A checkpoint fixes the current database state. All dirty pages
     * are written to disk and the journal file is cleaned.
     *
     * This method is called from
     * {@link org.exist.storage.BrokerPool#sync(DBBroker, Sync)} within pre-defined periods. It
     * should not be called from somewhere else. The database needs to
     * be in a stable state (all transactions completed, no operations running).
     *
     * @param switchFiles Indicates whether a new journal file should be started
     *
     * @throws TransactionException if an error occurs whilst writing the checkpoint.
     */
    public void checkpoint(final boolean switchFiles) throws TransactionException {
        if (state.get() == STATE_SHUTDOWN) {
            throwShutdownException();
        }

        if(journalManager.isPresent()) {
            try {
                final long txnId = nextTxnId.getAndIncrement();
                journalManager.get().checkpoint(txnId, switchFiles);
            } catch(final JournalException e) {
                throw new TransactionException(e.getMessage(), e);
            }
        }
    }

    /**
     * @deprecated This mixes concerns and should not be here!
     * @param broker the  eXist-db DBBroker
     * @throws IOException in response to an I/O error
     */
    @Deprecated
    public void reindex(final DBBroker broker) throws IOException {
        broker.pushSubject(broker.getBrokerPool().getSecurityManager().getSystemSubject());
        try(final Txn transaction = beginTransaction()) {
            broker.reindexCollection(transaction, XmldbURI.ROOT_COLLECTION_URI);
            commit(transaction);
        } catch (final PermissionDeniedException | LockException | TransactionException e) {
            LOG.error("Exception during reindex: " + e.getMessage(), e);
        } finally {
        	broker.popSubject();
        }
    }

    @Override
    public void shutdown() {
        try {
            while (true) {
                final int localState = state.get();

                if (localState == STATE_SHUTDOWN) {
                    // already shutdown!
                    return;
                }

                // can NOT shutdown whilst system tasks are executing
                if (localState == STATE_SYSTEM) {
                    // spin whilst another thread executes the system tasks
                    // sleep a small time to save CPU
                    Thread.sleep(10);
                    continue;
                }

                if (state.compareAndSet(localState, STATE_SHUTDOWN)) {
                    // CAS above guarantees that only a single thread will ever enter this block once!

                    final int uncommitted = uncommittedTransaction();
                    final boolean checkpoint = uncommitted == 0;

                    final long txnId = nextTxnId.getAndIncrement();
                    if (journalManager.isPresent()) {
                        journalManager.get().shutdown(txnId, checkpoint);
                    }

                    transactions.clear();

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Shutting down transaction manager. Uncommitted transactions: " + transactions.size());
                    }

                    // done... exit CAS loop!
                    return;
                }
            }
        } catch (final InterruptedException e) {
            // thrown by Thread.sleep
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private int uncommittedTransaction() {
        final Integer uncommittedCount = transactions.reduce(1000,
                (txnId, txnCounter) -> {
                    if (txnCounter.getCount() > 0) {
                        LOG.warn("Found an uncommitted transaction with id " + txnId + ". Pending operations: " + txnCounter.getCount());
                        return 1;
                    } else {
                        return 0;
                    }
                },
                (a, b) -> a + b
        );

        if (uncommittedCount == null) {
           return 0;
        }

        if (uncommittedCount > 0) {
            LOG.warn("There are uncommitted transactions. A recovery run may be triggered upon restart.");
        }

        return uncommittedCount;
    }

    public void triggerSystemTask(final SystemTask task) {
        systemTaskManager.addSystemTask(task);
        processSystemTasks();
    }

    private void processSystemTasks() {
        if (state.get() != STATE_IDLE) {
            // avoids taking a broker below if it is not needed
            return;
        }

        try (final DBBroker systemBroker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // no new transactions can begin, commit, or abort whilst processing system tasks
            // only process system tasks if there are no active transactions, i.e. the state == IDLE
            if (state.compareAndSet(STATE_IDLE, STATE_SYSTEM)) {
                // CAS above guarantees that only a single thread will ever enter this block at once
                try {
                    this.systemThreadId.set(Thread.currentThread().getId());

                    // we have to check that `transactions` is empty
                    // otherwise we might be in SYSTEM state but `abort` or `commit`
                    // functions are still finishing
                    if (transactions.isEmpty()) {
                        try (final Txn transaction = beginTransaction()) {
                            systemTaskManager.processTasks(systemBroker, transaction);
                            transaction.commit();
                        }
                    }

                } finally {
                    this.systemThreadId.set(-1);

                    // restore IDLE state
                    state.set(STATE_IDLE);
                }
            }
        } catch (final EXistException e) {
            LOG.error("Unable to process system tasks: " + e.getMessage(), e);
        }
    }

    /**
     * Keep track of the number of operations processed within a transaction.
     * This is used to determine if there are any uncommitted transactions
     * during shutdown.
     */
    private static final class TxnCounter {
        /**
         * The counter variable is declared volatile as it is only ever
         * written from one thread (via {@link #increment()} which is
         * the `transaction` for which it is maintaining a count, whilst
         * it is read from (potentially) a different thread
         * (via {@link #getCount()} when {@link TransactionManager#shutdown()}
         * calls {@link TransactionManager#uncommittedTransaction()}.
         */
        private volatile long counter = 0;

        public TxnCounter increment() {
            counter++;
            return this;
        }

        public long getCount() {
            return counter;
        }
    }
}
