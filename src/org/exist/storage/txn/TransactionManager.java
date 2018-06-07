/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage.txn;

import net.jcip.annotations.GuardedBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.journal.JournalException;
import org.exist.storage.journal.JournalManager;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This is the central entry point to the transaction management service.
 * 
 * There's only one TransactionManager per database instance that can be
 * retrieved via {@link BrokerPool#getTransactionManager()}. TransactionManager
 * provides methods to create, commit and rollback a transaction.
 * 
 * @author wolf
 *
 */
public class TransactionManager implements BrokerPoolService {

    /**
     * Logger for this class
     */
    private static final Logger LOG = LogManager.getLogger(TransactionManager.class);

    private long nextTxnId = 0;

    private final BrokerPool pool;
    private final Optional<JournalManager> journalManager;

    private final SystemTaskManager systemTaskManager;

    private final Map<Long, TxnCounter> transactions = new HashMap<>();

    private final Lock lock = new ReentrantLock();

    /**
     * Initialize the transaction manager using the specified data directory.
     * 
     * @param pool
     * @param journalManager
     * @param systemTaskManager
     * @throws EXistException
     */
    public TransactionManager(final BrokerPool pool, final Optional<JournalManager> journalManager,
            final SystemTaskManager systemTaskManager) {
        this.pool = pool;
        this.journalManager = journalManager;
        this.systemTaskManager = systemTaskManager;
    }

    /**
     * Create a new transaction. Creates a new transaction id that will
     * be logged to disk immediately. 
     */
    public Txn beginTransaction() {
        return withLock(broker -> {
            final long txnId = nextTxnId++;
            if(LOG.isDebugEnabled()) {
                LOG.debug("Starting new transaction: " + txnId);
            }

            if(journalManager.isPresent()) {
                try {
                    journalManager.get().journal(new TxnStart(txnId));
                } catch(final JournalException e) {
                    LOG.error("Failed to create transaction. Error writing to log file.", e);
	            }
            }

            final Txn txn = new Txn(TransactionManager.this, txnId);
            transactions.put(txn.getId(), new TxnCounter());
            broker.setCurrentTransaction(txn);
            return txn;
        });
    }
    
    /**
     * Commit a transaction.
     * 
     * @param txn
     * @throws TransactionException
     */
    public void commit(final Txn txn) throws TransactionException {

        if(txn instanceof Txn.ReusableTxn) {
            txn.commit();
            return;
            //throw new IllegalStateException("Commit should be called on the transaction and not via the TransactionManager"); //TODO(AR) remove later when API is cleaned up?
        }

        //we can only commit something which is in the STARTED state
        if (txn.getState() != Txn.State.STARTED) {
            return;
        }

        withLock(broker -> {
            if(journalManager.isPresent()) {
                try {
                    journalManager.get().journalGroup(new TxnCommit(txn.getId()));
                } catch(final JournalException e) {
                    LOG.error("Failed to write commit record to journal: " + e.getMessage());
                }
            }

            txn.signalCommit();
            txn.releaseAll();
            transactions.remove(txn.getId());
            processSystemTasks();
            if(LOG.isDebugEnabled()) {
                LOG.debug("Committed transaction: " + txn.getId());
            }
        });
    }
	
    public void abort(final Txn txn) {
        Objects.requireNonNull(txn);

        //we can only abort something which is in the STARTED state
        if (txn.getState() != Txn.State.STARTED) {
            return;
        }

        withLock(broker -> {
            transactions.remove(txn.getId());

            if(journalManager.isPresent()) {
                try {
                    journalManager.get().journalGroup(new TxnAbort(txn.getId()));
                } catch(final JournalException e) {
                    LOG.error("Failed to write abort record to journal: " + e.getMessage());
                }
            }

            txn.signalAbort();
            txn.releaseAll();
            processSystemTasks();
        });
    }

    /**
     * Make sure the transaction has either been committed or aborted.
     *
     * @param txn
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

            try(final DBBroker broker = pool.getBroker()) {
                broker.setCurrentTransaction(null);
            } catch(final EXistException ee) {
                LOG.fatal(ee.getMessage(), ee);
                throw new RuntimeException(ee);
            }

        } finally {
            txn.setState(Txn.State.CLOSED); //transaction is now closed!
        }
    }

    /**
     * Keep track of a new operation within the given transaction.
     *
     * @param txnId
     */
    public void trackOperation(final long txnId) {
        final TxnCounter count = transactions.get(txnId);
        // checkpoint operations do not create a transaction, so we have to check for null here
        if (count != null) {
            count.increment();
        }
    }

    public Lock getLock() {
        return lock;
    }
    
    /**
     * Create a new checkpoint. A checkpoint fixes the current database state. All dirty pages
     * are written to disk and the journal file is cleaned.
     *
     * This method is called from
     * {@link org.exist.storage.BrokerPool} within pre-defined periods. It
     * should not be called from somewhere else. The database needs to
     * be in a stable state (all transactions completed, no operations running).
     *
     * @param switchFiles Indicates whether a new journal file should be started
     *
     * @throws TransactionException
     */
    public void checkpoint(final boolean switchFiles) throws TransactionException {
	    final long txnId = nextTxnId++;
        if(journalManager.isPresent()) {
            try {
                journalManager.get().checkpoint(txnId, switchFiles);
            } catch(final JournalException e) {
                throw new TransactionException(e.getMessage(), e);
            }
        }
    }

    /**
     * @Deprecated This mixes concerns and should not be here.
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
        if(LOG.isDebugEnabled()) {
            LOG.debug("Shutting down transaction manager. Uncommitted transactions: " + transactions.size());
        }
        final int uncommitted = uncommittedTransaction();
        shutdown(uncommitted == 0);
    }

    public void shutdown(final boolean checkpoint) {
        final long txnId = nextTxnId++;
        if(journalManager.isPresent()) {
            journalManager.get().shutdown(txnId, checkpoint);
        }
        transactions.clear();
    }

    private int uncommittedTransaction() {
        int count = 0;
        if (transactions.isEmpty()) {
            return count;
        }
        for (final Map.Entry<Long, TxnCounter> entry : transactions.entrySet()) {
            if (entry.getValue().counter > 0) {
                LOG.warn("Found an uncommitted transaction with id " + entry.getKey() + ". Pending operations: " + entry.getValue().counter);
                count++;
            }
        }
        if (count > 0) {
            LOG.warn("There are uncommitted transactions. A recovery run may be triggered upon restart.");
        }
        return count;
    }

    public void triggerSystemTask(final SystemTask task) {
        withLock(broker -> {
            systemTaskManager.triggerSystemTask(task);
    	});
    }

    public void processSystemTasks() {
        withLock(broker -> {
           if(transactions.isEmpty()) {
               systemTaskManager.processTasks();
           }
        });
    }

	public void debug(final PrintStream out) {
		out.println("Active transactions: "+ transactions.size());
	}

    /**
     * Run a consumer within a lock on the transaction manager.
     * Make sure locks are acquired in the right order.
     *
     * @param lockedCn A consumer that must be run exclusively
     *                 with respect to the TransactionManager
     *                 instance
     */
    @GuardedBy("lock")
    private void withLock(final Consumer<DBBroker> lockedCn) {
        withLock(broker -> {
            lockedCn.accept(broker);
            return null;
        });
    }

    /**
     * Run a function within a lock on the transaction manager.
     * Make sure locks are acquired in the right order.
     *
     * @param lockedFn A function that must be run exclusively
     *                 with respect to the TransactionManager
     *                 instance
     *
     * @return The result of lockedFn
     */
    @GuardedBy("lock")
    private <T> T withLock(final Function<DBBroker, T> lockedFn) {
        try(final DBBroker broker = pool.getBroker()) {
            try {
                lock.lock();
                return lockedFn.apply(broker);
            } finally {
                lock.unlock();
            }
        } catch (final EXistException e) {
            LOG.error("Transaction manager failed to acquire broker for running system tasks");
            return null;
        }
    }

    /**
     * Keep track of the number of operations processed within a transaction.
     * This is used to determine if there are any uncommitted transactions
     * during shutdown.
     */
    protected final static class TxnCounter {
        int counter = 0;
        public void increment() {
            counter++;
        }
    }
}
