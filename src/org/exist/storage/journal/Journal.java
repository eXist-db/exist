/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
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
package org.exist.storage.journal;

import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.FileLock;
import org.exist.storage.txn.Checkpoint;
import org.exist.util.FileUtils;
import org.exist.util.ReadOnlyException;
import org.exist.util.sanity.SanityCheck;

import static org.exist.util.ThreadUtils.newInstanceThread;

/**
 * Manages the journalling log. The database uses one central journal for
 * all data files. If the journal exceeds the predefined maximum size, a new file is created.
 * Every journal file has a unique number, which keeps growing during the lifetime of the db.
 * The name of the file corresponds to the file number. The file with the highest
 * number will be used for recovery.
 * <p>
 * A buffer is used to temporarily buffer journal entries. To guarantee consistency, the buffer will be flushed
 * and the journal is synched after every commit or whenever a db page is written to disk.
 * <p>
 * Each entry has the structure:
 *
 * <pre>[byte: entryType, long: transactionId, short length, byte[] data, short backLink]</pre>
 *
 * <ul>
 * <li>entryType is a unique id that identifies the log record. Entry types are registered via the
 * {@link org.exist.storage.journal.LogEntryTypes} class.</li>
 * <li>transactionId: the id of the transaction that created the record.</li>
 * <li>length: the length of the log entry data.</li>
 * <li>data: the payload data provided by the {@link org.exist.storage.journal.Loggable} object.</li>
 * <li>backLink: offset to the start of the record. Used when scanning the log file backwards.</li>
 * </ul>
 *
 * @author wolf
 */
@ConfigurationClass("journal")
//TODO: conf.xml refactoring <recovery> => <recovery><journal/></recovery>
public final class Journal {
    /**
     * Logger for this class
     */
    private static final Logger LOG = LogManager.getLogger(Journal.class);

    public static final String RECOVERY_SYNC_ON_COMMIT_ATTRIBUTE = "sync-on-commit";
    public static final String RECOVERY_JOURNAL_DIR_ATTRIBUTE = "journal-dir";
    public static final String RECOVERY_SIZE_LIMIT_ATTRIBUTE = "size";

    public static final String PROPERTY_RECOVERY_SIZE_MIN = "db-connection.recovery.size-min";
    public static final String PROPERTY_RECOVERY_SIZE_LIMIT = "db-connection.recovery.size-limit";
    public static final String PROPERTY_RECOVERY_JOURNAL_DIR = "db-connection.recovery.journal-dir";
    public static final String PROPERTY_RECOVERY_SYNC_ON_COMMIT = "db-connection.recovery.sync-on-commit";

    public static final String LOG_FILE_SUFFIX = "log";
    public static final String BAK_FILE_SUFFIX = ".bak";

    public static final String LCK_FILE = "journal.lck";

    /**
     * the length of the header of each entry: entryType (1 byte) + transactionId (8 bytes) + length (2 bytes)
     */
    public static final int LOG_ENTRY_HEADER_LEN = 11;

    /**
     * the length of the back-link in a log entry
     */
    public static final int LOG_ENTRY_BACK_LINK_LEN = 2;

    /**
     * header length + trailing back link
     */
    public static final int LOG_ENTRY_BASE_LEN = LOG_ENTRY_HEADER_LEN + LOG_ENTRY_BACK_LINK_LEN;

    /**
     * default maximum journal size
     */
    public static final int DEFAULT_MAX_SIZE = 100; //MB

    /**
     * minimal size the journal needs to have to be replaced by a new file during a checkpoint
     */
    private static final int DEFAULT_MIN_SIZE = 1; // MB

    /**
     * Minimum size limit for the journal file before it is replaced by a new file.
     */
    @ConfigurationFieldAsAttribute("minSize")
    //TODO: conf.xml refactoring <recovery minSize=""> => <journal minSize="">
    private final long journalSizeMin;

    /**
     * size limit for the journal file. A checkpoint will be triggered if the file
     * exceeds this size limit.
     */
    @ConfigurationFieldAsAttribute("size")
    //TODO: conf.xml refactoring <recovery size=""> => <journal size="">
    private final long journalSizeLimit;

    /**
     * the current output channel
     * Only valid after switchFiles() was called at least once!
     */
    private FileOutputStream os;
    private FileChannel channel;

    /**
     * Syncing the journal is done by a background thread
     */
    private final FileSyncRunnable fileSyncRunnable;
    private final Thread fileSyncThread;

    /**
     * latch used to synchronize writes to the channel
     */
    private final Object latch = new Object();

    /**
     * the data directory where journal files are written to
     */
    @ConfigurationFieldAsAttribute("journal-dir")
    //TODO: conf.xml refactoring <recovery journal-dir=""> => <journal dir="">
    private final Path dir;

    private FileLock fileLock;

    /**
     * the current file number
     */
    private int currentFile = 0;

    /**
     * temp buffer
     */
    private ByteBuffer currentBuffer;

    /**
     * the last LSN written by the JournalManager
     */
    private long currentLsn = Lsn.LSN_INVALID;

    /**
     * the last LSN actually written to the file
     */
    private long lastLsnWritten = Lsn.LSN_INVALID;

    /**
     * stores the current LSN of the last file sync on the file
     */
    private long lastSyncLsn = Lsn.LSN_INVALID;

    /**
     * set to true while recovery is in progress
     */
    private boolean inRecovery = false;

    /**
     * the {@link BrokerPool} that created this manager
     */
    private final BrokerPool pool;

    /**
     * if set to true, a sync will be triggered on the log file after every commit
     */
    @ConfigurationFieldAsAttribute("sync-on-commit")
    //TODO: conf.xml refactoring <recovery sync-on-commit=""> => <journal sync-on-commit="">
    private final static boolean DEFAULT_SYNC_ON_COMMIT = true;
    private final boolean syncOnCommit;

    private final Path fsJournalDir;

    public Journal(final BrokerPool pool, final Path directory) throws EXistException {
        this.pool = pool;
        this.fsJournalDir = directory.resolve("fs.journal");
        // we use a 1 megabyte buffer:
        this.currentBuffer = ByteBuffer.allocateDirect(1024 * 1024);

        this.fileSyncRunnable = new FileSyncRunnable(latch);
        this.fileSyncThread = newInstanceThread(pool, "file-sync-thread", fileSyncRunnable);
        fileSyncThread.start(); //this makes us to use class as a final only - no inheritance allowed

        this.syncOnCommit = pool.getConfiguration().getProperty(PROPERTY_RECOVERY_SYNC_ON_COMMIT, DEFAULT_SYNC_ON_COMMIT);
        if (LOG.isDebugEnabled()) {
            LOG.debug("SyncOnCommit = " + syncOnCommit);
        }

        final Optional<Path> logDir = Optional.ofNullable((Path) pool.getConfiguration().getProperty(PROPERTY_RECOVERY_JOURNAL_DIR));
        if (logDir.isPresent()) {
            Path f = logDir.get();
            if (!f.isAbsolute()) {
                f = pool.getConfiguration().getExistHome()
                        .map(h -> Optional.of(h.resolve(logDir.get())))
                        .orElse(pool.getConfiguration().getConfigFilePath().map(p -> p.getParent().resolve(logDir.get())))
                        .orElse(f);
            }

            if (!Files.exists(f)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Output directory for journal files does not exist. Creating " + f.toAbsolutePath().toString());
                }

                try {
                    Files.createDirectories(f);
                } catch (final IOException | SecurityException e) {
                    throw new EXistException("Failed to create output directory: " + f.toAbsolutePath().toString());
                }
            }
            if (!Files.isWritable(f)) {
                throw new EXistException("Cannot write to journal output directory: " + f.toAbsolutePath().toString());
            }
            this.dir = f;
        } else {
            this.dir = directory;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using directory for the journal: " + dir.toAbsolutePath().toString());
        }

        this.journalSizeMin = 1024 * 1024 * pool.getConfiguration().getProperty(PROPERTY_RECOVERY_SIZE_MIN, DEFAULT_MIN_SIZE);
        this.journalSizeLimit = 1024 * 1024 * pool.getConfiguration().getProperty(PROPERTY_RECOVERY_SIZE_LIMIT, DEFAULT_MAX_SIZE);
    }

    public void initialize() throws EXistException, ReadOnlyException {
        final Path lck = dir.resolve(LCK_FILE);
        fileLock = new FileLock(pool, lck);
        final boolean locked = fileLock.tryLock();
        if (!locked) {
            final String lastHeartbeat =
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                            .format(fileLock.getLastHeartbeat());
            throw new EXistException("The journal log directory seems to be locked by another " +
                    "eXist process. A lock file: " + lck.toAbsolutePath().toString() + " is present in the " +
                    "log directory. Last access to the lock file: " + lastHeartbeat);
        }
    }

    /**
     * Write a log entry to the journal.
     *
     * @param entry the journal entry to write
     * @throws JournalException if the entry could not be written
     */
    public synchronized void writeToLog(final Loggable entry) throws JournalException {
        if (currentBuffer == null) {
            throw new JournalException("Database is shut down.");
        }

        SanityCheck.ASSERT(!inRecovery, "Write to log during recovery. Should not happen!");
        final int size = entry.getLogSize();
        final int required = size + LOG_ENTRY_BASE_LEN;
        if (required > currentBuffer.remaining()) {
            flushToLog(false);
        }

        try {
            final long offset = channel.position();
            if (offset > Integer.MAX_VALUE) {
                throw new JournalException("Journal can only write log files of less that 2GB");
            }
            currentLsn = Lsn.create(currentFile, ((int)(channel.position() &0x7FFFFFFF)) + currentBuffer.position() + 1);
        } catch (final IOException e) {
            throw new JournalException("Unable to create LSN for: " + entry.dump());
        }
        entry.setLsn(currentLsn);

        try {
            currentBuffer.put(entry.getLogType());
            currentBuffer.putLong(entry.getTransactionId());
            currentBuffer.putShort((short) entry.getLogSize());
            entry.write(currentBuffer);
            currentBuffer.putShort((short) (size + LOG_ENTRY_HEADER_LEN));
        } catch (final BufferOverflowException e) {
            throw new JournalException("Buffer overflow while writing log record: " + entry.dump(), e);
        }
        pool.getTransactionManager().trackOperation(entry.getTransactionId());
    }

    /**
     * Returns the last LSN physically written to the journal.
     *
     * @return last written LSN
     */
    public long lastWrittenLsn() {
        return lastLsnWritten;
    }

    /**
     * Flush the current buffer to disk. If fsync is true, a sync will
     * be called on the file to force all changes to disk.
     *
     * @param fsync forces all changes to disk if true and syncMode is set to SYNC_ON_COMMIT.
     */
    public void flushToLog(final boolean fsync) {
        flushToLog(fsync, false);
    }

    /**
     * Flush the current buffer to disk. If fsync is true, a sync will
     * be called on the file to force all changes to disk.
     *
     * @param fsync     forces all changes to disk if true and syncMode is set to SYNC_ON_COMMIT.
     * @param forceSync force changes to disk even if syncMode doesn't require it.
     */
    public synchronized void flushToLog(final boolean fsync, final boolean forceSync) {
        if (inRecovery) {
            return;
        }
        flushBuffer();
        if (forceSync || (fsync && syncOnCommit && currentLsn > lastSyncLsn)) {
            fileSyncRunnable.triggerSync();
            lastSyncLsn = currentLsn;
        }
        try {
            if (channel != null && channel.size() >= journalSizeLimit) {
                pool.triggerCheckpoint();
            }
        } catch (final IOException e) {
            LOG.warn("Failed to trigger checkpoint!", e);
        }
    }

    /**
     * Flush the buffer to disk.
     */
    private void flushBuffer() {
        if (currentBuffer == null || channel == null) {
            return; // the db has probably been shut down already or not fully initialized
        }
        synchronized (latch) {
            try {
                if (currentBuffer.position() > 0) {
                    currentBuffer.flip();
                    final int size = currentBuffer.remaining();
                    while (currentBuffer.hasRemaining()) {
                        channel.write(currentBuffer);
                    }

                    lastLsnWritten = currentLsn;
                }
            } catch (final IOException e) {
                LOG.warn("Flushing log file failed!", e);
            } finally {
                currentBuffer.clear();
            }
        }
    }

    /**
     * Write a checkpoint record to the journal and flush it. If switchLogFiles is true,
     * a new journal will be started, but only if the file is larger than
     * {@link #journalSizeMin}. The old log is removed.
     *
     * @param txnId          The transaction id
     * @param switchLogFiles Indicates whether a new journal file should be started
     * @throws JournalException if the checkpoint could not be written to the journal.
     */
    public void checkpoint(final long txnId, final boolean switchLogFiles) throws JournalException {
        LOG.debug("Checkpoint reached");
        writeToLog(new Checkpoint(txnId));
        if (switchLogFiles) {
            // if we switch files, we don't need to sync.
            // the file will be removed anyway.
            flushBuffer();
        } else {
            flushToLog(true, true);
        }
        try {
            if (switchLogFiles && channel != null && channel.position() > journalSizeMin) {
                final Path oldFile = getFile(currentFile);
                final RemoveRunnable removeRunnable = new RemoveRunnable(channel, oldFile);
                try {
                    switchFiles();
                } catch (final LogException e) {
                    LOG.warn("Failed to create new journal: " + e.getMessage(), e);
                }

                final Thread removeThread = newInstanceThread(pool, "remove-journal", removeRunnable);
                removeThread.start();
            }
            clearBackupFiles();
        } catch (final IOException e) {
            LOG.warn("IOException while writing checkpoint", e);
        }
    }

    /**
     * Set the file number of the last file used.
     *
     * @param fileNum the log file number
     */
    public void setCurrentFileNum(final int fileNum) {
        currentFile = fileNum;
    }

    public void clearBackupFiles() {
        if (Files.exists(fsJournalDir)) {
            try (final Stream<Path> backupFiles = Files.list(fsJournalDir)) {
                backupFiles.forEach(p -> {
                    LOG.info("Checkpoint deleting: " + p.toAbsolutePath().toString());
                    if (!FileUtils.deleteQuietly(p)) {
                        LOG.fatal("Cannot delete file '" + p.toAbsolutePath().toString() + "' from backup journal.");
                    }
                });
            } catch (final IOException ioe) {
                LOG.error("Could not clear fs.journal backup files", ioe);
            }
        }
    }

    /**
     * Create a new journal with a larger file number
     * than the previous file.
     *
     * @throws LogException if the journal files could not be switched
     */
    public void switchFiles() throws LogException {
        ++currentFile;
        final String fname = getFileName(currentFile);
        final Path file = dir.resolve(fname);
        if (Files.exists(file)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Journal file " + file.toAbsolutePath() + " already exists. Copying it.");
            }

            try {
                final Path renamed = Files.move(file, file.resolveSibling(FileUtils.fileName(file) + BAK_FILE_SUFFIX), StandardCopyOption.ATOMIC_MOVE);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Old file renamed from '" + file.toAbsolutePath().toString() + "' to '" + renamed.toAbsolutePath().toString() + "'");
                }
            } catch (final IOException ioe) {
                LOG.warn(ioe); //TODO(AR) should probably be an LogException but wasn't previously!
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating new journal: " + file.toAbsolutePath().toString());
        }

        synchronized (latch) {
            close();
            try {
                //RandomAccessFile raf = new RandomAccessFile(file, "rw");
                os = new FileOutputStream(file.toFile(), true);
                channel = os.getChannel();

                fileSyncRunnable.setChannel(channel);
            } catch (final FileNotFoundException e) {
                throw new LogException("Failed to open new journal: " + file.toAbsolutePath().toString(), e);
            }
        }
    }

    /**
     * Close the journal.
     */
    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (final IOException e) {
                LOG.warn("Failed to close journal channel", e);
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (final IOException e) {
                LOG.warn("Failed to close journal output stream", e);
            }
        }
    }

    private static int journalFileNum(final Path path) {
        final String fileName = FileUtils.fileName(path);
        final int p = fileName.indexOf('.');
        final String baseName = fileName.substring(0, p);
        return Integer.parseInt(baseName, 16);
    }

    /**
     * Find the journal file with the highest file number.
     *
     * @param files the journal files to consider.
     */
    public static int findLastFile(final Stream<Path> files) {
        return files
                .map(Journal::journalFileNum)
                .max(Integer::max)
                .orElse(-1);
    }

    /**
     * Returns a Stream of all journal files found in the data directory.
     *
     * @return A Stream of all journal files. NOTE - This is
     * an I/O Stream and so you are responsible for closing it!
     * @throws IOException if an I/O error occurs whilst finding journal files.
     */
    public Stream<Path> getFiles() throws IOException {
        final String suffix = '.' + LOG_FILE_SUFFIX;
        final String indexSuffix = "_index" + suffix;

        return Files.find(dir, 1, (path, attrs) ->
                attrs.isRegularFile() &&
                        FileUtils.fileName(path).endsWith(suffix) &&
                        !FileUtils.fileName(path).endsWith(indexSuffix));
    }

    /**
     * Returns the file corresponding to the specified
     * file number.
     *
     * @param fileNum the journal file number.
     */
    public Path getFile(final int fileNum) {
        return dir.resolve(getFileName(fileNum));
    }

    /**
     * Shut down the journal. This will write a checkpoint record
     * to the log, so recovery manager knows the file has been
     * closed in a clean way.
     *
     * @param txnId      the transaction id.
     * @param checkpoint true if a checkpoint should be written before shitdown
     */
    public void shutdown(final long txnId, final boolean checkpoint) {
        if (currentBuffer == null) {
            return; // the db has probably shut down already
        }

        if (!BrokerPool.FORCE_CORRUPTION) {
            if (checkpoint) {
                LOG.info("Transaction journal cleanly shutting down with checkpoint...");
                try {
                    writeToLog(new Checkpoint(txnId));
                } catch (final JournalException e) {
                    LOG.error("An error occurred while closing the journal file: " + e.getMessage(), e);
                }
            }
            flushBuffer();
        }
        fileLock.release();
        fileSyncRunnable.shutdown();
        fileSyncThread.interrupt();
        try {
            fileSyncThread.join();
        } catch (final InterruptedException e) {
            //Nothing to do
        }
        currentBuffer = null;
    }

    /**
     * Called to signal that the db is currently in
     * recovery phase, so no output should be written.
     *
     * @param inRecovery true when the database is in recovery, false otherwise.
     */
    public void setInRecovery(final boolean inRecovery) {
        this.inRecovery = inRecovery;
    }

    /**
     * Translate a file number into a file name.
     *
     * @param fileNum the journal file number
     * @return The file name
     */
    static String getFileName(final int fileNum) {
        String hex = Integer.toHexString(fileNum);
        hex = "0000000000".substring(hex.length()) + hex;
        return hex + '.' + LOG_FILE_SUFFIX;
    }

    private static class RemoveRunnable implements Runnable {
        private final FileChannel channel;
        private final Path path;

        RemoveRunnable(final FileChannel channel, final Path path) {
            this.channel = channel;
            this.path = path;
        }

        @Override
        public void run() {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (final IOException e) {
                LOG.warn("Exception while closing journal file: " + e.getMessage(), e);
            }
            FileUtils.deleteQuietly(path);
        }
    }
}
