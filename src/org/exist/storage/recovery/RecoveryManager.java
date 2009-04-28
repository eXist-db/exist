/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.storage.recovery;

import java.io.File;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.storage.journal.JournalReader;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.LogException;
import org.exist.storage.journal.Loggable;
import org.exist.storage.journal.Lsn;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Checkpoint;
import org.exist.util.ProgressBar;
import org.exist.util.hashtable.Long2ObjectHashMap;
import org.exist.util.sanity.SanityCheck;

/**
 * Database recovery. This class is used once during startup to check
 * if the database is in a consistent state. If not, the class attempts to recover
 * the database from the journalling log.
 * 
 * @author wolf
 */
public class RecoveryManager {
	
	private final static Logger LOG = Logger.getLogger(RecoveryManager.class);
	
	/**
     * @uml.property name="logManager"
     * @uml.associationEnd multiplicity="(1 1)"
     */
	private Journal logManager;
	private DBBroker broker;
    private boolean restartOnError;

	public RecoveryManager(DBBroker broker, Journal log, boolean restartOnError) {
        this.broker = broker;
		this.logManager = log;
        this.restartOnError = restartOnError;
	}
	
	/**
	 * Checks if the database is in a consistent state. If not, start a recovery run.
	 * 
	 * The method scans the last log file and tries to find the last checkpoint
	 * record. If the checkpoint record is the last record in the file,
	 * the database was closed cleanly and is in a consistent state. If not, a
	 * recovery run is started beginning at the last checkpoint found.
	 *  
	 * @throws LogException
	 */
	public boolean recover() throws LogException {
        boolean recoveryRun = false;
		File files[] = logManager.getFiles();
        // find the last log file in the data directory
		int lastNum = Journal.findLastFile(files);
		if (-1 < lastNum) {
            // load the last log file
			File last = logManager.getFile(lastNum);
			// scan the last log file and record the last checkpoint found
			JournalReader reader = new JournalReader(broker, last, lastNum);
            try {
            	// try to read the last log record to see if it is a checkpoint
            	boolean checkpointFound = false;
    			try {
                    Loggable lastLog = reader.lastEntry();
                    if (lastLog != null && lastLog.getLogType() == LogEntryTypes.CHECKPOINT) {
                    	Checkpoint checkpoint = (Checkpoint) lastLog;
                    	// Found a checkpoint. To be sure it is indeed a valid checkpoint
                    	// record, we compare the LSN stored in it with the current LSN.
                    	if (checkpoint.getStoredLsn() == checkpoint.getLsn()) {
                    		checkpointFound = true;
                    		LOG.debug("Database is in clean state. Last checkpoint: " + 
                    				checkpoint.getDateString());
                    	}
                    }
                } catch (LogException e) {
                    LOG.info("Reading last journal log entry failed: " + e.getMessage() + ". Will scan the log...");
                    // if an exception occurs at this point, the journal file is probably incomplete,
                    // which indicates a db crash
                    checkpointFound = false;
                }
    			if (!checkpointFound) {
                    LOG.info("Scanning journal...");
    				reader.position(1);
    				Long2ObjectHashMap txnsStarted = new Long2ObjectHashMap();
	    			Checkpoint lastCheckpoint = null;
	    			long lastLsn = Lsn.LSN_INVALID;
	                Loggable next;
	                try {
						ProgressBar progress = new ProgressBar("Scanning journal ", last.length());
	        			while ((next = reader.nextEntry()) != null) {
	//                        LOG.debug(next.dump());
							progress.set(Lsn.getOffset(next.getLsn()));
                            broker.getBrokerPool().signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
							if (next.getLogType() == LogEntryTypes.TXN_START) {
				                // new transaction starts: add it to the transactions table
				                txnsStarted.put(next.getTransactionId(), next);
				            } else if (next.getLogType() == LogEntryTypes.TXN_ABORT) {
				            	// transaction aborted: remove it from the transactions table
				            	txnsStarted.remove(next.getTransactionId());
				            } else if (next.getLogType() == LogEntryTypes.CHECKPOINT) {
				            	txnsStarted.clear();
	        					lastCheckpoint = (Checkpoint) next;
				            }
	        				lastLsn = next.getLsn();
	        			}
	                } catch (LogException e) {
	                    if (LOG.isDebugEnabled()) {
                            LOG.debug("Caught exception while reading log", e);
                            LOG.debug("Last readable log entry lsn: " + Lsn.dump(lastLsn));
                        }
                    }
	                
	    			// if the last checkpoint record is not the last record in the file
	    			// we need a recovery.
	    			if ((lastCheckpoint == null || lastCheckpoint.getLsn() != lastLsn) &&
	    					txnsStarted.size() > 0) {
	    				LOG.info("Dirty transactions: " + txnsStarted.size());
	    				// starting recovery: reposition the log reader to the last checkpoint
						if (lastCheckpoint == null)
						    reader.position(1);
						else {
						    reader.position(lastCheckpoint.getLsn());
						    next = reader.nextEntry();
						}
	                    recoveryRun = true;
                        try {
                            doRecovery(txnsStarted.size(), last, reader, lastLsn);
                        } catch (LogException e) {
                            // if restartOnError == true, we try to bring up the database even if there
                            // are errors. Otherwise, an exception is thrown, which will stop the db initialization
                            if (restartOnError)
                                LOG.error("Errors during recovery. Database will start up, but corruptions are likely.");
                            else
                                throw e;
                        }
                    } else if (LOG.isDebugEnabled())
	    				LOG.debug("Database is in clean state.");
    			}
    			cleanDirectory(files);
            } finally {
                reader.close();
            }
		}
        logManager.setCurrentFileNum(lastNum);
		logManager.switchFiles();
                logManager.clearBackupFiles();
        return recoveryRun;
	}

    /**
     * Called by {@link #recover()} to do the actual recovery.
     * 
     * @param reader
     * @param lastLsn
     * @throws LogException
     */
    private void doRecovery(int txnCount, File last, JournalReader reader, long lastLsn) throws LogException {
        if (LOG.isInfoEnabled())
            LOG.info("Running recovery ...");
        logManager.setInRecovery(true);

        try {
            // map to track running transactions
            Long2ObjectHashMap runningTxns = new Long2ObjectHashMap();

            // ------- REDO ---------
            if (LOG.isInfoEnabled())
                LOG.info("First pass: redoing " + txnCount + " transactions...");
            ProgressBar progress = new ProgressBar("Redo ", last.length());
            Loggable next = null;
            int redoCnt = 0;
            try {
                while ((next = reader.nextEntry()) != null) {
                    SanityCheck.ASSERT(next.getLogType() != LogEntryTypes.CHECKPOINT,
                            "Found a checkpoint during recovery run! This should not ever happen.");
                    broker.getBrokerPool().signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
                    if (next.getLogType() == LogEntryTypes.TXN_START) {
                        // new transaction starts: add it to the transactions table
                        runningTxns.put(next.getTransactionId(), next);
                    } else if (next.getLogType() == LogEntryTypes.TXN_COMMIT) {
                        // transaction committed: remove it from the transactions table
                        runningTxns.remove(next.getTransactionId());
                        redoCnt++;
                    } else if (next.getLogType() == LogEntryTypes.TXN_ABORT) {
                        // transaction aborted: remove it from the transactions table
                        runningTxns.remove(next.getTransactionId());
                    }
        //            LOG.debug("Redo: " + next.dump());
                    // redo the log entry
                    next.redo();
                    progress.set(Lsn.getOffset(next.getLsn()));
                    if (next.getLsn() == lastLsn)
                        break; // last readable entry reached. Stop here.
                }
            } catch (Exception e) {
                LOG.warn("Exception caught while redoing transactions. Aborting recovery.", e);
                if (next != null)
                    LOG.warn("Log entry that caused the exception: " + next.dump());
                throw new LogException("Recovery aborted");
            } finally {
                LOG.info("Redo processed " + redoCnt + " out of " + txnCount + " transactions.");
            }

            // ------- UNDO ---------
            if (LOG.isInfoEnabled())
                LOG.info("Second pass: undoing dirty transactions. Uncommitted transactions: " +
                        runningTxns.size());
            // see if there are uncommitted transactions pending
            if (runningTxns.size() > 0) {
                // do a reverse scan of the log, undoing all uncommitted transactions
                try {
                    while((next = reader.previousEntry()) != null) {
                        if (next.getLogType() == LogEntryTypes.TXN_START) {
                            if (runningTxns.get(next.getTransactionId()) != null) {
                                runningTxns.remove(next.getTransactionId());
                                if (runningTxns.size() == 0)
                                    // all dirty transactions undone
                                    break;
                            }
                        } else if (next.getLogType() == LogEntryTypes.TXN_COMMIT) {
                            // ignore already committed transaction
                        } else if (next.getLogType() == LogEntryTypes.CHECKPOINT) {
                            // found last checkpoint: undo is completed
                            break;
                        }

                        // undo the log entry if it belongs to an uncommitted transaction
                        if (runningTxns.get(next.getTransactionId()) != null) {
    //					LOG.debug("Undo: " + next.dump());
                            next.undo();
                        }
                        broker.getBrokerPool().signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
                    }
                } catch (Exception e) {
                    LOG.warn("Exception caught while undoing dirty transactions. Remaining transactions " +
                            "to be undone: " + runningTxns.size(), e);
                    if (next != null)
                        LOG.warn("Log entry that caused the exception: " + next.dump());
                    throw new LogException("Recovery aborted");
                }
            }
        } finally {
            broker.getBrokerPool().signalSystemStatus(BrokerPool.SIGNAL_STARTUP);
            broker.sync(Sync.MAJOR_SYNC);
            logManager.setInRecovery(false);
        }
    }
    
	private void cleanDirectory(File[] files) {
		for (int i = 0; i < files.length; i++)
			files[i].delete();
	}
}
