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
package org.exist.storage.recovery;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.BrokerPool;
import org.exist.storage.blob.BlobStore;
import org.exist.storage.journal.*;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Checkpoint;
import org.exist.util.FileUtils;
import org.exist.util.ProgressBar;
import com.evolvedbinary.j8fu.function.SupplierE;
import org.exist.util.sanity.SanityCheck;

/**
 * Database recovery. This class is used once during startup to check
 * if the database is in a consistent state. If not, the class attempts to recover
 * the database from the journalling log.
 * 
 * @author wolf
 */
public class RecoveryManager {
	
	private final static Logger LOG = LogManager.getLogger(RecoveryManager.class);

    private final DBBroker broker;
    private final JournalRecoveryAccessor journalRecovery;
    private final boolean restartOnError;

    public RecoveryManager(final DBBroker broker, final JournalManager journalManager, final boolean restartOnError) {
        this.broker = broker;
        this.journalRecovery = journalManager.getRecoveryAccessor(this);
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
	 * @throws LogException Reading of journal failed.
     * @return if recover was successful
	 */
	public boolean recover() throws LogException {
        boolean recoveryRun = false;
		final List<Path> files;
        try(final Stream<Path> fileStream = journalRecovery.getFiles.get()) {
            files = fileStream.collect(Collectors.toList());
        } catch(final IOException ioe) {
            throw new LogException("Unable to find journal files in data dir", ioe);
        }
        // find the last log file in the data directory
		final short lastNum = Journal.findLastFile(files.stream());
		if (-1 < lastNum) {
            // load the last log file
			final Path last = journalRecovery.getFile.apply(lastNum);
			// scan the last log file and record the last checkpoint found
            try (JournalReader reader = new JournalReader(broker, last, lastNum)) {
                // try to read the last log record to see if it is a checkpoint
                boolean checkpointFound = false;
                try {
                    final Loggable lastLog = reader.lastEntry();
                    if (lastLog != null && lastLog.getLogType() == LogEntryTypes.CHECKPOINT) {
                        final Checkpoint checkpoint = (Checkpoint) lastLog;
                        // Found a checkpoint. To be sure it is indeed a valid checkpoint
                        // record, we compare the LSN stored in it with the current LSN.
                        if (checkpoint.getStoredLsn().equals(checkpoint.getLsn())) {
                            checkpointFound = true;
                            LOG.debug("Database is in clean state. Last checkpoint: {}", checkpoint.getDateString());
                        }
                    }
                } catch (final LogException e) {
                    LOG.info("Reading last journal log entry failed: {}. Will scan the log...", e.getMessage());
                    // if an exception occurs at this point, the journal file is probably incomplete,
                    // which indicates a db crash
                    checkpointFound = false;
                }
                if (!checkpointFound) {
                    LOG.info("Unclean shutdown detected. Scanning journal...");
                    broker.getBrokerPool().reportStatus("Unclean shutdown detected. Scanning log...");
                    reader.positionFirst();
                    final Long2ObjectMap<Loggable> txnsStarted = new Long2ObjectOpenHashMap<>();
                    Checkpoint lastCheckpoint = null;
                    Lsn lastLsn = Lsn.LSN_INVALID;
                    Loggable next;
                    try {
                        final long lastSize = FileUtils.sizeQuietly(last);
                        final ProgressBar scanProgressBar = new ProgressBar("Scanning journal ", lastSize);
                        while ((next = reader.nextEntry()) != null) {
//	                        LOG.debug(next.dump());
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

                            scanProgressBar.set(next.getLsn().getOffset());
                        }
                        scanProgressBar.set(lastSize);  // 100%
                    } catch (final LogException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Caught exception while reading log", e);
                        }
                        LOG.warn("Last readable journal log entry lsn: {}", lastLsn);
                    }

                    // if the last checkpoint record is not the last record in the file
                    // we need a recovery.
                    if ((lastCheckpoint == null || !lastCheckpoint.getLsn().equals(lastLsn)) &&
                            txnsStarted.size() > 0) {
                        LOG.info("Dirty transactions: {}", txnsStarted.size());
                        // starting recovery: reposition the log reader to the last checkpoint
                        if (lastCheckpoint == null) {
                            reader.positionFirst();
                        } else {
                            reader.position(lastCheckpoint.getLsn());
                            next = reader.nextEntry();
                        }
                        recoveryRun = true;
                        try {
                            LOG.info("Running recovery...");
                            broker.getBrokerPool().reportStatus("Running recovery...");


                            try (final BlobStore blobStore = broker.getBrokerPool().getBlobStore()) {
                                try {
                                    blobStore.openForRecovery();
                                } catch (final FileNotFoundException e) {
                                    LOG.warn(e.getMessage(), e);
                                } catch (final IOException e) {
                                    throw new LogException("Unable to Open the Blob Store for Recovery: " + e.getMessage(), e);
                                }

                                doRecovery(txnsStarted.size(), last, reader, lastLsn);

                            } catch (final IOException e) {
                                LOG.error("Error whilst closing the Blob Store after recovery: {}", e.getMessage(), e);
                            }
                        } catch (final LogException e) {
                            // if restartOnError == true, we try to bring up the database even if there
                            // are errors. Otherwise, an exception is thrown, which will stop the db initialization
                            broker.getBrokerPool().reportStatus(BrokerPool.SIGNAL_ABORTED);
                            if (restartOnError) {
                                LOG.error("Aborting recovery. eXist-db detected an error during recovery. This may not be fatal. Database will start up, but corruptions are likely.");
                            } else {
                                LOG.error("Aborting recovery. eXist-db detected an error during recovery. This may not be fatal. Please consider running a consistency check via the export tool and create a backup if problems are reported. The db should come up again if you restart it.");
                                throw e;
                            }
                        }
                    } else {
                        LOG.info("Database is in clean state. Nothing to recover from the journal.");
                    }
                }
            } finally {
                // remove .log files from directory even if recovery failed.
                // Re-applying them on a second start up attempt would definitely damage the db, so we better
                // delete them before user tries to launch again.
                cleanDirectory(files.stream());
                if (recoveryRun) {
                    broker.repairPrimary();
                    broker.sync(Sync.MAJOR);
                }
            }
		}
        journalRecovery.setCurrentFileNum.accept(lastNum);
        journalRecovery.switchFiles.get();

        return recoveryRun;
	}

    public class JournalRecoveryAccessor {
        final Consumer<Boolean> setInRecovery;
        final SupplierE<Stream<Path>, IOException> getFiles;
        final Function<Short, Path> getFile;
        final Consumer<Short> setCurrentFileNum;
        final SupplierE<Void, LogException> switchFiles;


        public JournalRecoveryAccessor(final Consumer<Boolean> setInRecovery,
                final SupplierE<Stream<Path>, IOException> getFiles, final Function<Short, Path> getFile,
                final Consumer<Short> setCurrentFileNum, final SupplierE<Void, LogException> switchFiles) {
            this.setInRecovery = setInRecovery;
            this.getFiles = getFiles;
            this.getFile = getFile;
            this.setCurrentFileNum = setCurrentFileNum;
            this.switchFiles = switchFiles;
        }
    }

    /**
     * Called by {@link #recover()} to do the actual recovery.
     *
     * @param txnCount
     * @param last
     * @param reader
     * @param lastLsn
     *
     * @throws LogException
     */
    private void doRecovery(final int txnCount, final Path last, final JournalReader reader, final Lsn lastLsn) throws LogException {
        if (LOG.isInfoEnabled()) {
            LOG.info("Running recovery ...");
        }
        journalRecovery.setInRecovery.accept(true);

        try {
            // map to track running transactions
            final Long2ObjectMap<Loggable> runningTxns = new Long2ObjectOpenHashMap<>();

            // ------- REDO ---------
            if (LOG.isInfoEnabled())
                {
                    LOG.info("First pass: redoing {} transactions...", txnCount);}
            Loggable next = null;
            int redoCnt = 0;
            try {
                final long lastSize = FileUtils.sizeQuietly(last);
                final ProgressBar redoProgressBar = new ProgressBar("Redo ", lastSize);
                while ((next = reader.nextEntry()) != null) {
                    SanityCheck.ASSERT(next.getLogType() != LogEntryTypes.CHECKPOINT,
                            "Found a checkpoint during recovery run! This should not ever happen.");
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
                    redoProgressBar.set(next.getLsn().getOffset());
                    if (next.getLsn().equals(lastLsn)) {
                        // last readable entry reached. Stop here.
                        break;
                    }
                }
                redoProgressBar.set(lastSize);  // 100% done
            } catch (final Exception e) {
                LOG.error("Exception caught while redoing transactions. Aborting recovery to avoid possible damage. " +
                    "Before starting again, make sure to run a check via the emergency export tool.", e);
                if (next != null)
                    {
                        LOG.info("Log entry that caused the exception: {}", next.dump());}
                throw new LogException("Recovery aborted. ");
            } finally {
                LOG.info("Redo processed {} out of {} transactions.", redoCnt, txnCount);
            }

            // ------- UNDO ---------
            if (LOG.isInfoEnabled())
                {
                    LOG.info("Second pass: undoing dirty transactions. Uncommitted transactions: {}", runningTxns.size());}
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
                                    {break;}
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
                    }
                } catch (final Exception e) {
                    LOG.warn("Exception caught while undoing dirty transactions. Remaining transactions to be undone: {}. Aborting recovery to avoid possible damage. Before starting again, make sure to run a check via the emergency export tool.", runningTxns.size(), e);
                    if (next != null)
                        {
                            LOG.warn("Log entry that caused the exception: {}", next.dump());}
                    throw new LogException("Recovery aborted", e);
                }
            }
        } finally {
            broker.sync(Sync.MAJOR);
            journalRecovery.setInRecovery.accept(false);
        }
    }
    
	private void cleanDirectory(final Stream<Path> files) {
        files.forEach(FileUtils::deleteQuietly);
	}
}
