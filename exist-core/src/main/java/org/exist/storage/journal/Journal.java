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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.Optional;
import java.util.stream.Stream;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.FileLock;
import org.exist.storage.txn.Checkpoint;
import org.exist.storage.txn.TxnStart;
import org.exist.util.ByteConversion;
import org.exist.util.FileUtils;
import org.exist.util.ReadOnlyException;
import org.exist.util.sanity.SanityCheck;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.exist.util.ThreadUtils.newInstanceThread;

/**
 * Manages the journal log. The database uses one central journal for
 * all data files. If the journal exceeds the predefined maximum size, a new file is created.
 * Every journal file has a unique number, which keeps growing during the lifetime of the db.
 * The name of the file corresponds to the file number. The file with the highest
 * number will be used for recovery.
 *
 * A buffer is used to temporarily buffer journal entries. To guarantee consistency, the buffer will be flushed
 * and the journal is synced after every commit or whenever a db page is written to disk.
 *
 * Each journal file has the following format:
 *
 * <pre>{@code
 *     [magicNumber, version, entry*]
 * }</pre>
 *
 * {@code magicNumber}  4 bytes with the value {@link #JOURNAL_MAGIC_NUMBER}.
 * {@code version}      2 bytes (java.lang.short) with the value {@link #JOURNAL_VERSION}.
 * {@code entry}        one or more variable length journal {@code entry} records.
 *
 * Each {@code entry} record has the format:
 *
 * <pre>{@code
 *     [entryHeader, data, backLink, checksum]
 * }</pre>
 *
 * {@code entryHeader}      11 bytes describes the entry (see below).
 * {@code data}             {@code entryHeader->length} bytes of data for the entry.
 * {@code backLink}         2 bytes (java.lang.short) offset to the start of the entry record, calculated by {@code entryHeader.length + dataLength}.
 *                              The offset for the start of the entry record can be calculated as {@code endOfRecordOffset - 8 - 2 - backLink}.
 *                              This is used when scanning the log file backwards for recovery.
 * {@code checksum}         8 bytes for a 64 bit checksum. The checksum includes the {@code entryHeader}, {@code data}, and {@code backLink}.
 *
 * The {@code entryHeader} has the format:
 *
 * <pre>{@code
 *     [entryType, transactionId, dataLength]
 * }</pre>
 *
 * {@code entryType}        1 byte indicates the type of the entry.
 * {@code transactionId}    8 bytes (java.lang.long) the id of the transaction that created the record.
 * {@code dataLength}       2 bytes (java.lang.short) the length of the log entry {@code data}.
 *
 * @author wolf
 * @author aretter
 */
@ConfigurationClass("journal")
//TODO: conf.xml refactoring <recovery> => <recovery><journal/></recovery>
public final class Journal implements Closeable {
    /**
     * Logger for this class
     */
    private static final Logger LOG = LogManager.getLogger(Journal.class);

    /**
     * The length in bytes of the Header in the Journal file
     *
     * 4 bytes for the magic number, and then 2 bytes for the journal version
     */
    public static final int JOURNAL_HEADER_LEN = 6;
    public static final byte[] JOURNAL_MAGIC_NUMBER = {0x0E, 0x0D, 0x0B, 0x01};
    public static final short JOURNAL_VERSION = 6;

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
     * the length of the checkum in a log entry
     */
    public static final int LOG_ENTRY_CHECKSUM_LEN = 8;

    /**
     * header length + trailing back link length + checksum length
     */
    public static final int LOG_ENTRY_BASE_LEN = LOG_ENTRY_HEADER_LEN + LOG_ENTRY_BACK_LINK_LEN + LOG_ENTRY_CHECKSUM_LEN;

    /**
     * default maximum journal size
     */
    public static final int DEFAULT_MAX_SIZE = 100;  //MB

    /**
     * minimal size the journal needs to have to be replaced by a new file during a checkpoint
     */
    private static final int DEFAULT_MIN_SIZE = 1;  // MB

    /**
     * We use a 1 megabyte buffer.
     */
    public static final int BUFFER_SIZE = 1024 * 1024;  // bytes

    /**
     * Seed used for xxhash-64 checksums calculated
     * by the journal.
     */
    public static final long XXHASH64_SEED = 0x9747b28c;

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
    private FileChannel channel;

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
    private Lsn currentLsn = Lsn.LSN_INVALID;

    /**
     * the last LSN actually written to the file
     */
    private Lsn lastLsnWritten = Lsn.LSN_INVALID;

    /**
     * stores the current LSN of the last file sync on the file
     */
    private Lsn lastSyncLsn = Lsn.LSN_INVALID;

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

    private volatile boolean initialised = false;

    private final XXHash64 xxHash64 = XXHashFactory.fastestInstance().hash64();

    public Journal(final BrokerPool pool, final Path directory) throws EXistException {
        this.pool = pool;
        this.fsJournalDir = directory.resolve("fs.journal");
        this.currentBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

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

        if (size > Short.MAX_VALUE) {
            throw new JournalException("Journal can only write log entries of less that 32KB");
        }

        final int required = size + LOG_ENTRY_BASE_LEN;
        if (required > currentBuffer.remaining()) {
            flushToLog(false);
        }

        try {
            if (currentFile > Short.MAX_VALUE) {
                throw new JournalException("Journal can only support " + Short.MAX_VALUE + " log files");
            }
            currentLsn = new Lsn((short)currentFile, channel.position() + currentBuffer.position() + 1);
        } catch (final IOException e) {
            throw new JournalException("Unable to create LSN for: " + entry.dump());
        }
        entry.setLsn(currentLsn);

        try {
            final int currentBufferEntryOffset = currentBuffer.position();

            // write entryHeader
            currentBuffer.put(entry.getLogType());
            currentBuffer.putLong(entry.getTransactionId());
            currentBuffer.putShort((short) size);

            // write entry data
            entry.write(currentBuffer);

            // write backlink
            currentBuffer.putShort((short) (size + LOG_ENTRY_HEADER_LEN));

            // write checksum
            final long checksum = xxHash64.hash(currentBuffer, currentBufferEntryOffset, currentBuffer.position() - currentBufferEntryOffset, XXHASH64_SEED);
            currentBuffer.putLong(checksum);
        } catch (final BufferOverflowException e) {
            throw new JournalException("Buffer overflow while writing log record: " + entry.dump(), e);
        }

        // NOTE: we don't track operations on txnStart or checkpoints!
        if (!(entry instanceof TxnStart || entry instanceof Checkpoint)) {
            pool.getTransactionManager().trackOperation(entry.getTransactionId());
        }
    }

    /**
     * Returns the last LSN physically written to the journal.
     *
     * @return last written LSN
     */
    public Lsn lastWrittenLsn() {
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

        try {
            if (forceSync || (fsync && syncOnCommit && currentLsn.compareTo(lastSyncLsn) > 0)) {
                sync();
                lastSyncLsn = currentLsn;
            }
        } catch (final IOException e) {
            LOG.error("Could not sync Journal to disk: " + e.getMessage(), e);
        }

        try {
            if (channel != null && channel.size() >= journalSizeLimit) {
                pool.triggerCheckpoint();
            }
        } catch (final IOException e) {
            LOG.warn("Failed to trigger checkpoint!", e);
        }
    }

    private void sync() throws IOException {
        channel.force(true);
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
                LOG.debug("Journal file " + file.toAbsolutePath() + " already exists. Moving it to a backup file.");
            }

            try {
                final Path renamed = Files.move(file, file.resolveSibling(FileUtils.fileName(file) + BAK_FILE_SUFFIX), StandardCopyOption.ATOMIC_MOVE);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Old Journal file renamed from '" + file.toAbsolutePath().toString() + "' to '" + renamed.toAbsolutePath().toString() + "'");
                }
            } catch (final IOException ioe) {
                LOG.warn(ioe); //TODO(AR) should probably be an LogException but wasn't previously!
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating new journal: " + file.toAbsolutePath().toString());
        }

        synchronized (latch) {
            try {
                // close current file
                close();

                // open new file
                channel = (FileChannel) Files.newByteChannel(file, CREATE_NEW, WRITE);
                writeJournalHeader(channel);
                initialised = true;
            } catch (final IOException e) {
                throw new LogException("Failed to open new journal: " + file.toAbsolutePath().toString(), e);
            }
        }
    }

    private void writeJournalHeader(final SeekableByteChannel channel) throws IOException {
        final ByteBuffer buf = ByteBuffer.allocateDirect(JOURNAL_HEADER_LEN);

        // write the magic number
        buf.put(JOURNAL_MAGIC_NUMBER);

        // write the version of the journal format
        final byte[] journalVersion = new byte[2];
        ByteConversion.shortToByteH(JOURNAL_VERSION, journalVersion, 0);
        buf.put(journalVersion);

        buf.flip();
        channel.write(buf);
    }

    /**
     * Close the journal.
     */
    @Override
    public void close() throws IOException {
        if (channel != null) {
            try {
                sync();
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
            }
            channel.close();
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
     *
     * @return the number of the last journal file
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
     *
     * @return the journal file
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
        if (!initialised) {
            // no journal is initialized
            return;
        }

        if (currentBuffer == null) {
            return; // the db has probably shut down already
        }

        if (!BrokerPool.FORCE_CORRUPTION) {
            if (checkpoint) {
                LOG.info("Shutting down Journal with checkpoint...");
                try {
                    writeToLog(new Checkpoint(txnId));
                } catch (final JournalException e) {
                    LOG.error("An error occurred whilst writing a checkpoint to the Journal: " + e.getMessage(), e);
                }
            }
            flushBuffer();
        }

        try {
            channel.close();
        } catch (final IOException e) {
            LOG.error("Unable to close Journal file: " + e.getMessage(), e);
        }
        channel = null;
        fileLock.release();
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
        private final SeekableByteChannel channel;
        private final Path path;

        RemoveRunnable(final SeekableByteChannel channel, final Path path) {
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
