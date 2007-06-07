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

import java.io.File;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.journal.Journal;
import org.exist.storage.recovery.RecoveryManager;
import org.exist.util.ReadOnlyException;
import org.exist.xmldb.XmldbURI;

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
public class TransactionManager {
	
	public final static String PROPERTY_RECOVERY_GROUP_COMMIT = "db-connection.recovery.group-commit";
	
    /**
     * Logger for this class
     */
    private static final Logger LOG = Logger .getLogger(TransactionManager.class);

    private long nextTxnId = 0;

    private Journal journal;
    
    private boolean enabled;
    
    private boolean groupCommit = false;
    
    /**
     * Initialize the transaction manager using the specified data directory.
     * 
     * @param dataDir
     * @throws EXistException
     */
    public TransactionManager(BrokerPool pool, File dataDir, boolean transactionsEnabled) throws EXistException {
        enabled = transactionsEnabled;
        if (enabled)
            journal = new Journal(pool, dataDir);
        Boolean groupOpt = (Boolean) pool.getConfiguration().getProperty(PROPERTY_RECOVERY_GROUP_COMMIT);
        if (groupOpt != null) {
            groupCommit = groupOpt.booleanValue();
            if (LOG.isDebugEnabled())
                LOG.debug("GroupCommits = " + groupCommit);
        }
    }
    
    public void initialize() throws EXistException, ReadOnlyException {
        if (enabled)
            journal.initialize();
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
		RecoveryManager recovery = new RecoveryManager(broker, journal);
		return recovery.recover();
	}
	
    /**
     * Create a new transaction. Creates a new transaction id that will
     * be logged to disk immediately. 
     */
    public Txn beginTransaction() {
        if (!enabled)
            return null;
        long txnId = nextTxnId++;
        try {
            journal.writeToLog(new TxnStart(txnId));
        } catch (TransactionException e) {
            LOG.warn("Failed to create transaction. Error writing to log file.", e);
        }
        return new Txn(txnId);
    }
    
    /**
     * Commit a transaction.
     * 
     * @param txn
     * @throws TransactionException
     */
    public void commit(Txn txn) throws TransactionException {
        if (!enabled)
            return;
        if (enabled) {
            journal.writeToLog(new TxnCommit(txn.getId()));
            if (!groupCommit)
                journal.flushToLog(true);
        }
        txn.releaseAll();
    }
	
    public void abort(Txn txn) {
        if (!enabled || txn == null)
        	return;
        try {
			journal.writeToLog(new TxnAbort(txn.getId()));
		} catch (TransactionException e) {
			LOG.warn("Failed to write abort record to journal: " + e.getMessage());
		}
        if (!groupCommit)
        	journal.flushToLog(true);
        txn.releaseAll();
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
        if (!enabled)
            return;
		long txnId = nextTxnId++;
		journal.checkpoint(txnId, switchFiles);
	}
	
	public Journal getJournal() {
		return journal;
	}
    
    public void reindex(DBBroker broker) {
        broker.setUser(SecurityManager.SYSTEM_USER);
        try {
            broker.reindexCollection(XmldbURI.ROOT_COLLECTION_URI);
        } catch (PermissionDeniedException e) {
            LOG.warn("Exception during reindex: " + e.getMessage(), e);
        }
    }
    
    public void shutdown() {
        if (enabled) {
        	long txnId = nextTxnId++;
            journal.shutdown(txnId);
        }
    }
}