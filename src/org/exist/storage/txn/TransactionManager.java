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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.storage.SystemTaskManager;
import org.exist.storage.journal.Journal;
import org.exist.storage.recovery.RecoveryManager;
import org.exist.util.ReadOnlyException;
import org.exist.xmldb.XmldbURI;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
@ConfigurationClass("recovery")
public class TransactionManager {
	
	public final static String RECOVERY_GROUP_COMMIT_ATTRIBUTE = "group-commit";
	public final static String PROPERTY_RECOVERY_GROUP_COMMIT = "db-connection.recovery.group-commit";
    public final static String RECOVERY_FORCE_RESTART_ATTRIBUTE = "force-restart";
    public final static String PROPERTY_RECOVERY_FORCE_RESTART = "db-connection.recovery.force-restart";

    /**
     * Timeout for inactive transactions. A transaction which has not processed
     * any operations for this time may be considered dead.
     */
    public final static long TXN_INACTIVE_TIMEOUT = 60 * 1000;

    /**
     * Logger for this class
     */
    private static final Logger LOG = LogManager.getLogger(TransactionManager.class);

    private long nextTxnId = 0;

    private final BrokerPool pool;
    private final Journal journal;
    
    @ConfigurationFieldAsAttribute("enabled")
    private boolean enabled;
    
    @ConfigurationFieldAsAttribute("group-commit")
    private boolean groupCommit = false;

    @ConfigurationFieldAsAttribute("force-restart")
    private boolean forceRestart = false;

    private SystemTaskManager taskManager;

    private final Map<Long, TxnCounter> transactions = new HashMap<>();

    private final Lock lock = new ReentrantLock();

    /**
     * Initialize the transaction manager using the specified data directory.
     * 
     * @param pool
     * @param dataDir
     * @param transactionsEnabled
     * @throws EXistException
     */
    public TransactionManager(final BrokerPool pool, final File dataDir, final boolean transactionsEnabled) throws EXistException {
    	this(
            pool,
            transactionsEnabled,
            transactionsEnabled ? new Journal(pool, dataDir) : null,
            Optional.ofNullable((boolean)pool.getConfiguration().getProperty(PROPERTY_RECOVERY_GROUP_COMMIT)).orElse(false),
            Optional.ofNullable((boolean)pool.getConfiguration().getProperty(PROPERTY_RECOVERY_FORCE_RESTART)).orElse(false),
            new SystemTaskManager(pool)
        );
    }

    TransactionManager(final BrokerPool pool, final boolean transactionsEnabled, final Journal journal, final boolean groupCommit, final boolean forceRestart, final SystemTaskManager taskManager) {
        this.pool = pool;
        this.enabled = transactionsEnabled;
        this.journal = journal;
        this.groupCommit = groupCommit;
        this.forceRestart = forceRestart;
        this.taskManager = taskManager;

        if (LOG.isDebugEnabled()) {
            LOG.debug("GroupCommits = " + groupCommit);
            LOG.debug("ForceRestart = " + forceRestart);
        }
    }

    public void initialize() throws EXistException, ReadOnlyException {
        if (enabled)
            {journal.initialize();}
        transactions.clear();
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Run a database recovery if required. This method is called once during
     * startup from {@link org.exist.storage.BrokerPool}.
     * 
     * @param broker
     * @throws EXistException
     */
	public boolean runRecovery(DBBroker broker) throws EXistException {
		final RecoveryManager recovery = new RecoveryManager(broker, journal, forceRestart);
		return recovery.recover();
	}
	
    /**
     * Create a new transaction. Creates a new transaction id that will
     * be logged to disk immediately. 
     */
    public Txn beginTransaction() {
        if (!enabled)
            {return null;}

        return new RunWithLock<Txn>() {

			public Txn execute() {
				final long txnId = nextTxnId++;
                LOG.debug("Starting new transaction: " + txnId);
                final Txn txn = new Txn(TransactionManager.this, txnId);
	            try {
	                journal.writeToLog(new TxnStart(txnId));
	            } catch (final TransactionException e) {
	                LOG.warn("Failed to create transaction. Error writing to log file.", e);
	            }
                transactions.put(txn.getId(), new TxnCounter());
                return txn;
			}
        	
        }.run();
    }
    
    /**
     * Commit a transaction.
     * 
     * @param txn
     * @throws TransactionException
     */
    public void commit(final Txn txn) throws TransactionException {

        //we can only commit something which is in the STARTED state
        if (!enabled || txn.getState() != Txn.State.STARTED) {
            return;
        }

        new RunWithLock<Object>() {
        	public Object execute() {
                if (enabled) {
                    try {
						journal.writeToLog(new TxnCommit(txn.getId()));
					} catch (final TransactionException e) {
						LOG.error("transaction manager caught exception while committing", e);
					}
                    if (!groupCommit)
                        {journal.flushToLog(true);}
                }
                txn.signalCommit();
                txn.releaseAll();
                transactions.remove(txn.getId());
                processSystemTasks();
                LOG.debug("Committed transaction: " + txn.getId());
                return null;
        	}
        }.run();
    }
	
    public void abort(final Txn txn) {

        //we can only abort something which is in the STARTED state
        if (!enabled || txn == null || txn.getState() != Txn.State.STARTED) {
            return;
        }

        new RunWithLock<Object>() {
        	public Object execute() {
                transactions.remove(txn.getId());
                try {
                    journal.writeToLog(new TxnAbort(txn.getId()));
                } catch (final TransactionException e) {
                    LOG.warn("Failed to write abort record to journal: " + e.getMessage());
                }
                if (!groupCommit)
                    {journal.flushToLog(true);}
                txn.signalAbort();
                txn.releaseAll();
                processSystemTasks();
                return null;
        	}
        }.run();
    }

    /**
     * Make sure the transaction has either been committed or aborted.
     *
     * @param txn
     */
    public void close(final Txn txn) {

        //if the transaction is already closed, do nothing
        if (!enabled || txn == null || txn.getState() == Txn.State.CLOSED) {
            return;
        }

        try {
            //if the transaction is started, then we should auto-abort the uncommitted transaction
            if (txn.getState() == Txn.State.STARTED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Transaction was not committed or aborted, auto aborting!");
                }
                abort(txn);
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
    public void trackOperation(long txnId) {
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
     * @throws TransactionException
     */
    public void checkpoint(boolean switchFiles) throws TransactionException {
        if (!enabled) {
            return;
        }
        
	final long txnId = nextTxnId++;
	journal.checkpoint(txnId, switchFiles);
    }
	
    public Journal getJournal() {
	return journal;
    }

    /**
     * @Deprecated This mixes concerns and should not be here.
     */
    @Deprecated
    public void reindex(DBBroker broker) {
    	final Subject currentUser = broker.getSubject();
    	
        broker.setSubject(broker.getBrokerPool().getSecurityManager().getSystemSubject());
        try {
            broker.reindexCollection(XmldbURI.ROOT_COLLECTION_URI);

        } catch (final PermissionDeniedException e) {
            LOG.warn("Exception during reindex: " + e.getMessage(), e);
            
        } finally {
        	broker.setSubject(currentUser);
        }
    }

    public void shutdown() {
        LOG.debug("Shutting down transaction manager. Uncommitted transactions: " + transactions.size());
        final int uncommitted = uncommittedTransaction();
        shutdown(uncommitted == 0);
    }

    public void shutdown(boolean checkpoint) {
        if (enabled) {
                final long txnId = nextTxnId++;
            journal.shutdown(txnId, checkpoint);
            transactions.clear();
        }
    }

    private int uncommittedTransaction() {
        int count = 0;
        if (transactions.isEmpty())
            {return count;}
        for (final Map.Entry<Long, TxnCounter> entry : transactions.entrySet()) {
            if (entry.getValue().counter > 0) {
                LOG.warn("Found an uncommitted transaction with id " + entry.getKey() + ". Pending operations: " +
                    entry.getValue().counter);
                count++;
            }
        }
        if (count > 0) {
            LOG.warn("There are uncommitted transactions. A recovery run may be triggered upon restart.");
        }
        return count;
    }

    public void triggerSystemTask(final SystemTask task) {
    	new RunWithLock<Object>() {
			public Object execute() {
				taskManager.triggerSystemTask(task);
				return null;
			}
    	}.run();
    }

    public void processSystemTasks() {
    	new RunWithLock<Object>() {
    		public Object execute() {
    			if (transactions.isEmpty())
                    {taskManager.processTasks();}
    			return null;
    		}
    	}.run();
    }

	public void debug(PrintStream out) {
		out.println("Active transactions: "+ transactions.size());
	}

    /**
     * Run code block with a lock on the transaction manager.
     * Make sure locks are acquired in the right order.
     * 
     * @author wolf
     *
     */
    private abstract class RunWithLock<T> {
    	
    	public T run() {
    		DBBroker broker = null;
    		try {
    			// we first need to get a broker for the current thread
    			// before we acquire the transaction manager lock. Otherwise
    			// a deadlock may occur.
    			broker = pool.get(null);
    			
    			try {
    				lock.lock();
    				return execute();
    			} finally {
    				lock.unlock();
    			}
    		} catch (final EXistException e) {
				LOG.warn("Transaction manager failed to acquire broker for running system tasks");
				return null;
			} finally {
    			pool.release(broker);
    		}
    	}
    	
    	public abstract T execute();
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
