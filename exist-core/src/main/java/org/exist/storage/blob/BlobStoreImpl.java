/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage.blob;

import com.evolvedbinary.j8fu.Try;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.tuple.Tuple3;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.backup.RawDataBackup;
import org.exist.storage.journal.JournalException;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;
import org.exist.storage.txn.TxnListener;
import org.exist.util.FileUtils;
import org.exist.util.UUIDGenerator;
import org.exist.util.crypto.digest.DigestInputStream;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.evolvedbinary.j8fu.Try.TaggedTryUnchecked;
import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;
import static org.exist.storage.blob.BlobLoggable.LOG_STORE_BLOB_FILE;
import static org.exist.storage.blob.BlobLoggable.LOG_UPDATE_BLOB_REF_COUNT;
import static org.exist.storage.blob.BlobStoreImpl.BlobReference.*;
import static org.exist.util.FileUtils.fileName;
import static org.exist.util.HexEncoder.bytesToHex;
import static org.exist.util.ThreadUtils.nameInstanceThread;
import static org.exist.util.ThreadUtils.newInstanceSubThreadGroup;

/**
 * De-duplicating store for BLOBs (Binary Large Objects).
 *
 * Each unique BLOB is stored by checksum into a blob file on disk.
 *
 * For each BLOB a reference count and the number of active readers is maintained.
 * Adding a BLOB which is already present increments the reference count only,
 * it does not require any additional storage.
 *
 * Removing a BLOB decrements its reference count, BLOBs are only removed when
 * their reference count reaches zero, the blob file itself is scheduled for deletion
 * and will only be removed when there are no active readers and its reference is zero.
 * When the scheduled action for deleting a blob file is realised, if another thread
 * has meanwhile added a BLOB to the BLOB store where its blob file has the same
 * checksum, then the BLOB's reference count will have increased from zero,
 * therefore the schedule will not delete this now again active blob file, we call
 * this feature "recycling".
 *
 * The Blob Store is backed to disk by a persistent store file which
 * reflects the in-memory state of BlobStore.
 *
 * The persistent store file will grow for each unique blob added to
 * the system, space is not reclaimed in the persistent file until
 * {@link #compactPersistentReferences(ByteBuffer, Path)} is called,
 * which typically only happens the next time the blob store is re-opened.
 *
 * Each unique blob typically takes up only 36 bytes in the
 * persistent store file, but this can vary if a smaller or larger
 * digestType is specified.
 *
 * On-line compaction of the persistent file could be added in
 * future with relative ease if deemed necessary.
 *
 * The persistent file for the blob store has the format:
 *
 * [fileHeader entry+]
 *
 * fileHeader:          [magicNumber blobStoreVersion].
 * magicNumber:         4 bytes. See {@link #BLOB_STORE_MAGIC_NUMBER}.
 * blobStoreVersion:    2 bytes. java.lang.short, see {@link #BLOB_STORE_VERSION}.
 *
 * entry:               [blobChecksum blobReferenceCount]
 * blobChecksum:        n-bytes determined by the constructed {@link MessageDigest}.
 * blobReferenceCount:  4 bytes. java.lang.int.
 *
 * Note the persistent file may contain more than one entry
 * for the same blobChecksum, however all entries previous to
 * the last entry will have a blobReferenceCount of zero. The last
 * entry will have the current blobReferenceCount which may be
 * zero or more.
 *     The use of {@code orphanedBlobFileIds} in the
 * {@link #compactPersistentReferences(ByteBuffer, Path)} method
 * makes sure to only delete blob files which have a final
 * blobReferenceCount of zero.
 *
 * For performance, writing to the persistent store file,
 * removing staged blob files, and deleting blob files are all
 * asynchronous actions. To ensure the ability to achieve a consistent
 * state after a system crash, the BLOB Store writes entries to a
 * Journal WAL (Write-Ahead-Log) which is flushed to disk before each state
 * changing operation. If the system restarts after a crash, then a recovery
 * process will be performed from the entries in the WAL.
 *
 * Journal and Recovery of the BLOB Store works as follows:
 *
 *  Add Blob:
 *    Writes two journal entries:
 *      * StoreBlobFile(blobId, stagedUuid)
 *      * UpdateBlobReferenceCount(blobId, currentCount, newCount + 1)
 *
 *      On crash recovery, firstly:
 *          the StoreBlobFile will either be:
 *              1. undone, which copies the blob file from the blob store to the staging area,
 *              2. redone, which copies the blob file from the staging area to the blob store,
 *              3. or both undone and redone.
 *          This is possible because the blob file in the staging area is ONLY deleted after
 *          a COMMIT and CHECKPOINT have been written to the Journal, which means
 *          that the staged file is always available for recovery, and that no
 *          crash recovery of the staged file itself is needed
 *
 *          Deletion of the staged
 *          file happens on a best effort basis, any files which were not deleted due
 *          to a system crash, will be deleted upon restart (after recovery) when
 *          the Blob Store is next opened.
 *
 *      Secondly:
 *          the BlobReferenceCount will either be undone, redone, or both.
 *
 *
 *  Remove Blob:
 *      Writes a single journal entry:
 *        *  UpdateBlobReferenceCount(blobId, currentCount, currentCount - 1)
 *
 *      On crash recovery the BlobReferenceCount will either be undone, redone,
 *      or both.
 *
 *      It is worth noting that the actual blob file on disk is only ever deleted
 *      after a COMMIT and CHECKPOINT have been written to the Journal, and then
 *      only when it has zero references and zero readers. As it is only
 *      deleted after a CHECKPOINT, there is no need for any crash recovery of the
 *      disk file itself.
 *
 *      Deletion of the blob file happens on a best effort basis, any files which
 *      were not deleted due to a system crash, will be deleted upon restart
 *      (after recovery) by the {@link #compactPersistentReferences(ByteBuffer, Path)}
 *      process when the Blob Store is next opened.
 *
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class BlobStoreImpl implements BlobStore {

    private static final Logger LOG = LogManager.getLogger(BlobStoreImpl.class);

    /**
     * Maximum time to wait whilst trying add an
     * item to the vacuum queue {@link #vacuumQueue}.
     */
    private static final long VACUUM_ENQUEUE_TIMEOUT = 5000;  // 5 seconds

    /*
     * Journal entry types
     */
    static {
        LogEntryTypes.addEntryType(LOG_STORE_BLOB_FILE, StoreBlobFileLoggable::new);
        LogEntryTypes.addEntryType(LOG_UPDATE_BLOB_REF_COUNT, UpdateBlobRefCountLoggable::new);
    }

    /**
     * Length in bytes of the reference count.
     */
    static final int REFERENCE_COUNT_LEN = 4;

    /**
     * File header length
     */
    static final int BLOB_STORE_HEADER_LEN = 6;

    /**
     * File header - magic number
     */
    static final byte[] BLOB_STORE_MAGIC_NUMBER = {0x0E, 0x0D, 0x0B, 0x02};

    /**
     * File header - blob store version
     */
    public static final short BLOB_STORE_VERSION = 1;

    private ByteBuffer buffer;
    private SeekableByteChannel channel;

    /**
     * In-memory representation of the Blob Store.
     */
    private ConcurrentMap<BlobId, BlobReference> references;

    /**
     * Queue for communicating between the thread calling
     * the BlobStore and the {@link #persistentWriter} thread.
     *
     * Holds blob references which need to be updated in the
     * blob stores persistent dbx file ({@link #persistentFile})
     * on disk.
     */
    private final BlockingQueue<Tuple3<BlobId, BlobReference, Integer>> persistQueue = new LinkedBlockingQueue<>();

    /**
     * Queue for communicating between the thread calling
     * the BlobStore and the {@link #blobVacuum} thread.
     *
     * Head of the queue is the blob with the least active readers.
     *
     * Holds blob references which are scheduled to have their blob file
     * removed from the blob file store.
     */
    private final BlockingQueue<BlobVacuum.Request> vacuumQueue = new PriorityBlockingQueue<>();

    private final Database database;
    private final Path persistentFile;
    private final Path blobDir;
    private final Path stagingDir;
    private final DigestType digestType;

    /**
     * Enumeration of possible
     * Blob Store states.
     */
    private enum State {
        OPENING,
        OPEN,
        RECOVERY,
        CLOSING,
        CLOSED
    }

    /**
     * State of the Blob Store
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    /**
     * Thread which updates the persistent blob
     * store file on disk.
     */
    private PersistentWriter persistentWriter;
    private Thread persistentWriterThread;

    /**
     * Thread which deletes de-referenced
     * blob files when there are no
     * more readers.
     */
    private BlobVacuum blobVacuum;
    private Thread blobVacuumThread;

    /**
     * @param database the database that this BlobStore is operating within
     * @param persistentFile the file path for the persistent blob store metadata.
     * @param blobDir the directory to store BLOBs in.
     * @param digestType the message digest type to use for creating checksums of the BLOBs.
     */
    public BlobStoreImpl(final Database database, final Path persistentFile, final Path blobDir,
            final DigestType digestType) {
        this.database = database;
        this.persistentFile = persistentFile;
        this.blobDir = blobDir;
        this.stagingDir = blobDir.resolve("staging");
        this.digestType = digestType;
    }

    @Override
    public void open() throws IOException {
        openBlobStore(false);

        // thread group for the blob store
        final ThreadGroup blobStoreThreadGroup = newInstanceSubThreadGroup(database, "blob-store");

        // startup the persistent writer thread
        this.persistentWriter = new PersistentWriter(persistQueue, buffer, channel,
                this::abnormalPersistentWriterShutdown);
        this.persistentWriterThread = new Thread(blobStoreThreadGroup, persistentWriter,
                nameInstanceThread(database, "blob-store.persistent-writer"));
        persistentWriterThread.start();

        // startup the blob vacuum thread
        this.blobVacuum = new BlobVacuum(vacuumQueue);
        this.blobVacuumThread = new Thread(blobStoreThreadGroup, blobVacuum,
                nameInstanceThread(database, "blob-store.vacuum"));
        blobVacuumThread.start();

        // we are now open!
        state.set(State.OPEN);
    }

    @Override
    public void openForRecovery() throws IOException {
        openBlobStore(true);
        state.set(State.RECOVERY);
    }

    /**
     * Opens the BLOB Store's persistent store file,
     * and prepares the staging area.
     *
     * @param forRecovery true if the Blob Store is being opened for crash recovery, false otherwise
     *
     * @throws IOException if an error occurs whilst opening the BLOB Store
     */
    private void openBlobStore(final boolean forRecovery) throws IOException {
        if (state.get() == State.OPEN) {
            if (forRecovery) {
                throw new IOException("BlobStore is already open!");
            } else {
                return;
            }
        }

        if (!state.compareAndSet(State.CLOSED, State.OPENING)) {
            throw new IOException("BlobStore is not closed");
        }

        // size the buffer to hold a complete entry
        buffer = ByteBuffer.allocate(digestType.getDigestLengthBytes() + REFERENCE_COUNT_LEN);
        try {
            // open the dbx file
            if (Files.exists(persistentFile)) {
                if (!forRecovery) {
                    // compact existing blob store file and then open
                    this.references = compactPersistentReferences(buffer, persistentFile);
                    channel = Files.newByteChannel(persistentFile, WRITE);

                    /*
                     * We are not recovering, so we can delete any staging area left over
                     * from a previous running database instance
                     */
                    FileUtils.deleteQuietly(stagingDir);
                } else {
                    // recovery... so open the existing blob store file and just validate its header
                    channel = Files.newByteChannel(persistentFile, WRITE, READ);
                    validateFileHeader(buffer, persistentFile, channel);
                }
            } else {
                // open new blob store file
                if (forRecovery) {
                    // we are trying to recover, but there is no existing Blob Store!
                    throw new FileNotFoundException("No Blob Store found at '"
                            + persistentFile.toAbsolutePath().toString() + "' to recover!");
                }

                references = new ConcurrentHashMap<>();
                channel = Files.newByteChannel(persistentFile, CREATE_NEW, WRITE);
                writeFileHeader(buffer, channel);
            }

            // create the staging directory if it does not exist
            Files.createDirectories(stagingDir);
        } catch (final IOException e) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (final IOException ce) {
                    // ignore
                }
            }
            state.set(State.CLOSED);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        // check the blob store is open
        if (state.get() == State.CLOSED) {
            return;
        }

        if (state.compareAndSet(State.OPEN, State.CLOSING)) {

            // close up normally
            normalClose();

        } else if (state.compareAndSet(State.RECOVERY, State.CLOSING)) {

            // close up after recovery was attempted
            closeAfterRecoveryAttempt();

        } else {
            throw new IOException("BlobStore is not open");
        }
    }

    /**
     * Closes the Blob Store.
     *
     * @throws IOException if an error occurs whilst closing the Blob Store
     */
    private void normalClose() throws IOException {
        try {
            // shutdown the persistent writer
            if (persistentWriter != null) {
                persistQueue.put(PersistentWriter.POISON_PILL);
            }
            persistentWriterThread.join();

            // shutdown the vacuum
            if (blobVacuum != null) {
                blobVacuumThread.interrupt();
            }
            blobVacuumThread.join();
        } catch (final InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            closeBlobStore();

            // we are now closed!
            state.set(State.CLOSED);
        }
    }

    /**
     * Closes the Blob Store after it was opened for Recovery.
     */
    private void closeAfterRecoveryAttempt() {
        closeBlobStore();

        // we are now closed!
        state.set(State.CLOSED);
    }

    /**
     * Closes the resources associated
     * with the Blob Store persistent file.
     *
     * Clears the {@link #buffer} and closes the {@link #channel}.
     */
    private void closeBlobStore() {
        if (buffer != null) {
            ((java.nio.Buffer) buffer).clear();
            buffer = null;
        }

        // close the file channel
        if (channel != null) {
            try {
                channel.close();
                channel = null;
            } catch (final IOException e) {
                // non-critical error
                LOG.error("Error whilst closing blob.dbx: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Closes the BlobStore if the {@link #persistentWriter} has
     * to shutdown due to abnormal circumstances.
     *
     * Only called when the Blob Store is in the {@link State#OPEN} state!
     */
    private void abnormalPersistentWriterShutdown() {
        // check the blob store is open
        if (state.get() == State.CLOSED) {
            return;
        }
        if (!state.compareAndSet(State.OPEN, State.CLOSING)) {
            return;
        }

        try {

            // NOTE: persistent writer thread will join when this method finishes!

            // shutdown the vacuum
            if (blobVacuum != null) {
                blobVacuumThread.interrupt();
            }
            blobVacuumThread.join();

        } catch (final InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
        } finally {
            closeBlobStore();

            // we are now closed!
            state.set(State.CLOSED);
        }
    }

    /**
     * Compacts an existing Blob Store file.
     *
     * Reads the existing Blob Store file and copies non zero reference
     * entries to a new Blob Store file. We call this compaction.
     * Once complete, the existing file is replaced with the new file.
     *
     * @param persistentFile an existing persistentFile to compact.
     *
     * @return An in-memory representation of the compacted Blob Store
     *
     * @throws IOException if an error occurs during compaction.
     */
    private ConcurrentMap<BlobId, BlobReference> compactPersistentReferences(final ByteBuffer buffer,
            final Path persistentFile) throws IOException {

        final ConcurrentMap<BlobId, BlobReference> compactReferences = new ConcurrentHashMap<>();
        final Path compactPersistentFile = persistentFile.getParent().resolve(
                persistentFile.getFileName() + ".new." + System.currentTimeMillis());

        // tracks the BlobIds of Blob Files which have been orphaned
        final Set<BlobId> orphanedBlobFileIds = new HashSet<>();

        try (final SeekableByteChannel channel = Files.newByteChannel(persistentFile, READ)) {

            validateFileHeader(buffer, persistentFile, channel);
            ((java.nio.Buffer) buffer).clear();

            try (final SeekableByteChannel compactChannel = Files.newByteChannel(compactPersistentFile,
                    CREATE_NEW, APPEND)) {

                writeFileHeader(buffer, compactChannel);

                ((java.nio.Buffer) buffer).clear();

                while (channel.read(buffer) > -1) {
                    final byte[] id = new byte[digestType.getDigestLengthBytes()];
                    ((java.nio.Buffer) buffer).flip();
                    buffer.get(id);
                    final BlobId blobId = new BlobId(id);
                    final int count = buffer.getInt();

                    if (count == 0) {
                        orphanedBlobFileIds.add(blobId);
                    } else {
                        orphanedBlobFileIds.remove(blobId);

                        compactReferences.put(blobId, new BlobReference(count, compactChannel.position()));

                        ((java.nio.Buffer) buffer).flip();
                        compactChannel.write(buffer);
                    }

                    ((java.nio.Buffer) buffer).clear();
                }
            }
        }

        // cleanup any orphaned Blob files
        for (final BlobId orphanedBlobFileId : orphanedBlobFileIds) {
            deleteBlob(blobDir, orphanedBlobFileId, false);
        }

        // replace the persistent file with the new compact persistent file
        Files.move(compactPersistentFile, persistentFile, ATOMIC_MOVE, REPLACE_EXISTING);

        return compactReferences;
    }

    /**
     * Writes the persistent file header
     *
     * @param buffer a byte buffer to use
     * @param channel the channel to write to
     * @return the number of bytes written.
     * @throws IOException if an error occurs whilst writing the header.
     */
    private long writeFileHeader(final ByteBuffer buffer, final SeekableByteChannel channel) throws IOException {
        final long start = channel.position();

        ((java.nio.Buffer) buffer).clear();
        writeFileHeader(buffer);

        ((java.nio.Buffer) buffer).flip();
        ((java.nio.Buffer) buffer).limit(BLOB_STORE_HEADER_LEN);
        channel.write(buffer);

        return channel.position() - start;
    }

    /**
     * Writes the persistent file header
     *
     * @param buffer the buffer to write to
     */
    private static void writeFileHeader(final ByteBuffer buffer) {
        buffer.put(BLOB_STORE_MAGIC_NUMBER);
        buffer.putShort(BLOB_STORE_VERSION);
    }

    /**
     * Validates the persistent file header.
     *
     * @param buffer a byte buffer to use
     * @param file the file containing the header.
     * @param channel the channel of the file to read from.
     *
     * @throws IOException if the header is invalid.
     */
    private void validateFileHeader(final ByteBuffer buffer, final Path file, final SeekableByteChannel channel)
            throws IOException {
        ((java.nio.Buffer) buffer).clear();
        ((java.nio.Buffer) buffer).limit(BLOB_STORE_HEADER_LEN);

        channel.read(buffer);

        ((java.nio.Buffer) buffer).flip();

        final boolean validMagic =
                buffer.get() == BLOB_STORE_MAGIC_NUMBER[0]
                        && buffer.get() == BLOB_STORE_MAGIC_NUMBER[1]
                        && buffer.get() == BLOB_STORE_MAGIC_NUMBER[2]
                        && buffer.get() == BLOB_STORE_MAGIC_NUMBER[3];

        if (!validMagic) {
            throw new IOException("File was not recognised as a valid eXist-db Blob Store: "
                    + file.toAbsolutePath().toString());
        }

        // check the version of the blob store format
        final short storedVersion = buffer.getShort();
        final boolean validVersion =
                storedVersion == BLOB_STORE_VERSION;

        if (!validVersion) {
            throw new IOException("Blob Store file was version " + storedVersion + ", but required version "
                    + BLOB_STORE_VERSION + ": " + file.toAbsolutePath().toString());
        }
    }

    @Override
    public Tuple2<BlobId, Long> add(final Txn transaction, final InputStream is) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        // stage the BLOB file
        final Tuple3<Path, Long, MessageDigest> staged = stage(is);

        final BlobVacuum.RequestDeleteStagedBlobFile requestDeleteStagedBlobFile =
                new BlobVacuum.RequestDeleteStagedBlobFile(stagingDir, staged._1.getFileName().toString());

        // register a callback to cleanup the staged BLOB file ONLY after commit+checkpoint
        final JournalManager journalManager = database.getJournalManager().orElse(null);
        if (journalManager != null) {
            final DeleteStagedBlobFile cleanupStagedBlob = new DeleteStagedBlobFile(vacuumQueue, requestDeleteStagedBlobFile);
            journalManager.listen(cleanupStagedBlob);
            transaction.registerListener(cleanupStagedBlob);
        }

        final BlobId blobId = new BlobId(staged._3.getValue());

        // if the blob entry does not exist, we exclusively compute it as STAGED.
        BlobReference blobReference = references.computeIfAbsent(blobId, k -> new BlobReference(STAGED));

        try {
            while (true) {

                if (blobReference.count.compareAndSet(STAGED, PROMOTING)) {
                    // NOTE: we are the only thread that can be in this branch for the blobId

                    // write journal entries to the WAL
                    if (journalManager != null) {
                        try {
                            journalManager.journal(new StoreBlobFileLoggable(transaction.getId(), blobId, staged._1.getFileName().toString()));
                            journalManager.journal(new UpdateBlobRefCountLoggable(transaction.getId(), blobId, 0, 1));
                            journalManager.flush(true, true);   // force WAL entries to disk!
                        } catch (final JournalException e) {
                            references.remove(blobId);
                            throw new IOException(e);
                        }
                    }

                    // promote the staged blob
                    promote(staged);
                    if (journalManager == null) {
                        // no journal (or recovery)... so go ahead and schedule cleanup of the staged blob file
                        enqueueVacuum(vacuumQueue, requestDeleteStagedBlobFile);
                    }

                    // schedule disk persist of the new value
                    persistQueue.put(Tuple(blobId, blobReference, 1));

                    // update memory with the new value
                    blobReference.count.set(1);

                    // done!
                    return Tuple(blobId, staged._2);
                }

                final int count = blobReference.count.get();

                // guard against a concurrent #add or #remove
                if (count == PROMOTING || count == UPDATING_COUNT) {
                    // spin whilst another thread promotes the blob, or updates the reference count
                    // sleep a small time to save CPU
                    Thread.sleep(10);
                    continue;
                }

                // guard against a concurrent vacuum operation
                // ...retry the blob reference until the vacuum has completed!
                // i.e. wait for the deletion of the blob to complete, and then we can add the blob again
                if (count == DELETING) {
                    blobReference = references.computeIfAbsent(blobId, k -> new BlobReference(STAGED));
                    continue;   // loop again
                }

                // only increment the blob reference if the blob is active!
                if (count >= 0 && blobReference.count.compareAndSet(count, UPDATING_COUNT)) {
                    // NOTE: we are the only thread that can be in this branch for the blobId

                    final int newCount = count + 1;

                    // write journal entries to the WAL
                    if (journalManager != null) {
                        try {
                            journalManager.journal(new UpdateBlobRefCountLoggable(transaction.getId(), blobId, count, newCount));
                            journalManager.flush(true, true);   // force WAL entries to disk!
                        } catch (final JournalException e) {
                            // restore the state of the blobReference first!
                            blobReference.count.set(count);
                            throw new IOException(e);
                        }
                    }

                    // persist the new value
                    persistQueue.put(Tuple(blobId, blobReference, newCount));

                    // update memory with the new value, and release other spinning threads
                    blobReference.count.set(newCount);

                    // done!
                    return Tuple(blobId, staged._2);
                }
            }
        } catch (final InterruptedException e) {
            // thrown by persistQueue.put or Thread.sleep
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public BlobId copy(final Txn transaction, final BlobId blobId) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        final BlobReference blobReference = references.get(blobId);
        if (blobReference == null) {
            return null;
        }

        // NOTE: that copy is simply an increment of the reference count!
        try {
            while (true) {

                final int count = blobReference.count.get();

                // guard against a concurrent #add or #remove
                if (count == STAGED || count == PROMOTING || count == UPDATING_COUNT) {
                    // spin whilst another thread promotes the blob, or updates the reference count
                    // sleep a small time to save CPU
                    Thread.sleep(10);
                    continue;
                }

                // guard against a concurrent vacuum operation
                if (count == DELETING) {
                    return null;
                }

                // only increment the blob reference if the blob is active!
                if (count >= 0 && blobReference.count.compareAndSet(count, UPDATING_COUNT)) {
                    // NOTE: we are the only thread that can be in this branch for the blobId

                    final int newCount = count + 1;

                    // write journal entries to the WAL
                    final JournalManager journalManager = database.getJournalManager().orElse(null);
                    if (journalManager != null) {
                        try {
                            journalManager.journal(new UpdateBlobRefCountLoggable(transaction.getId(), blobId, count, newCount));
                            journalManager.flush(true, true);   // force WAL entries to disk!
                        } catch (final JournalException e) {
                            // restore the state of the blobReference first!
                            blobReference.count.set(count);
                            throw new IOException(e);
                        }
                    }

                    // persist the new value
                    persistQueue.put(Tuple(blobId, blobReference, newCount));

                    // update memory with the new value, and release other spinning threads
                    blobReference.count.set(newCount);

                    // done!
                    return blobId;
                }
            }
        } catch (final InterruptedException e) {
            // thrown by persistQueue.put or Thread.sleep
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    @Nullable public InputStream get(final Txn transaction, final BlobId blobId) throws IOException {
        final BlobFileLease blobFileLease = readLeaseBlobFile(transaction, blobId);
        if (blobFileLease == null) {
            return null;
        }

        // blob file lease is released either when the input stream is closed, or if an error occurs opening the stream
        try {
            return new OnCloseInputStream(Files.newInputStream(blobFileLease.path), blobFileLease.release);
        } catch (final IOException e) {
            blobFileLease.release.run();  // MUST release the read lease!
            throw e;
        }
    }

    @Override
    @Nullable public MessageDigest getDigest(final Txn transaction, final BlobId blobId, final DigestType digestType)
            throws IOException {
        if (this.digestType.equals(digestType)) {
            // optimisation, we can just return the BlobId as that is the digest for this digest type!
            return new MessageDigest(digestType, blobId.getId());

        } else {
            // calculate the digest
            final StreamableDigest streamableDigest = digestType.newStreamableDigest();
            final Try<MessageDigest, IOException> result = with(transaction, blobId, maybeBlobFile ->
                    maybeBlobFile == null ? null :
                            TaggedTryUnchecked(IOException.class, () -> {
                                FileUtils.digest(maybeBlobFile, streamableDigest);
                                return new MessageDigest(streamableDigest.getDigestType(),
                                        streamableDigest.getMessageDigest());
                            })
            );
            return result.get();
        }
    }

    @Override
    public <T> T with(final Txn transaction, final BlobId blobId, final Function<Path, T> fnFile) throws IOException {
        final BlobFileLease blobFileLease = readLeaseBlobFile(transaction, blobId);
        try {
            return fnFile.apply(blobFileLease == null ? null : blobFileLease.path);
        } finally {
            if (blobFileLease != null) {
                blobFileLease.release.run();  // MUST release the read lease!
            }
        }
    }

    /**
     * Lease a Blob file for reading from the Blob Store.
     *
     * @param transaction the current database transaction.
     * @param blobId the identifier of the blob to lease the blob file from.
     *
     * @return the blob file lease, or null if the blob does not exist in the Blob Store
     *
     * @throws IOException if an error occurs whilst retrieving the BLOB file.
     */
    private BlobFileLease readLeaseBlobFile(final Txn transaction, final BlobId blobId) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        final BlobReference blobReference = references.get(blobId);
        if (blobReference == null) {
            return null;
        }

        try {
            while (true) {
                final int count = blobReference.count.get();

                if (count == 0) {
                    // can't return something with has zero references
                    return null;
                }

                // guard against a concurrent vacuum operation
                if (count == DELETING) {
                    // can't return something with has zero references (because it is being deleted)
                    return null;
                }

                // guard against a concurrent #add doing staging
                if (count == STAGED || count == PROMOTING) {
                    // spin whilst another thread promotes the blob
                    // sleep a small time to save CPU
                    Thread.sleep(10);
                    continue;
                }

                // read a blob which has references
                if (count > 0) {
                    // we are reading
                    blobReference.readers.incrementAndGet();

                    // get the blob
                    final Path blobFile = blobDir.resolve(bytesToHex(blobId.getId()));
                    return new BlobFileLease(blobFile, blobReference.readers::decrementAndGet);
                }
            }
        } catch (final InterruptedException e) {
            // only thrown by Thread.sleep above
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /**
     * Gets the reference count for the Blob
     *
     * NOTE: this method is not thread-safe and should ONLY
     * be used for testing, which is why this method is
     * marked package-private!
     *
     * @param blobId The id of the blob
     *
     * @return the reference count, or null if the blob id is not in the references table.
     *
     * @throws IOException if the BlobStore is not open.
     */
    @Nullable Integer getReferenceCount(final BlobId blobId) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        final BlobReference blobReference = references.get(blobId);
        if (blobReference == null) {
            return null;
        }
        return blobReference.count.get();
    }

    @Override
    public void remove(final Txn transaction, final BlobId blobId) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        final BlobReference blobReference = references.get(blobId);
        if (blobReference == null) {
            return;
        }

        try {
            while (true) {
                final int count = blobReference.count.get();

                if (count == 0) {
                    // can't remove something which has zero references
                    return;
                }

                // guard against a concurrent vacuum operation
                if (count == DELETING) {
                    // can't remove something which has zero references (because it is being deleted)
                    return;
                }

                // guard against a concurrent #add or #remove
                if (count == STAGED || count == PROMOTING || count == UPDATING_COUNT) {
                    // spin whilst another thread promotes the blob or updates the reference count
                    // sleep a small time to save CPU
                    Thread.sleep(10);
                    continue;
                }

                // only decrement the blob reference if the blob has more than zero references
                if (count > 0 && blobReference.count.compareAndSet(count, UPDATING_COUNT)) {
                    // NOTE: we are the only thread that can be in this branch for the blobId

                    final int newCount = count - 1;

                    // write journal entries to the WAL
                    final JournalManager journalManager = database.getJournalManager().orElse(null);
                    if (journalManager != null) {
                        try {
                            journalManager.journal(new UpdateBlobRefCountLoggable(transaction.getId(), blobId, count, newCount));
                            journalManager.flush(true, true);   // force WAL entries to disk!
                        } catch (final JournalException e) {
                            // restore the state of the blobReference first!
                            blobReference.count.set(count);
                            throw new IOException(e);
                        }
                    }

                    // schedule disk persist of the new value
                    persistQueue.put(Tuple(blobId, blobReference, newCount));

                    if (newCount == 0) {
                        // schedule blob file for vacuum.

                        final BlobVacuum.RequestDeleteBlobFile requestDeleteBlobFile =
                                new BlobVacuum.RequestDeleteBlobFile(references, blobDir, blobId, blobReference);

                        if (journalManager != null) {
                            // register a callback to schedule the BLOB file for vacuum ONLY after commit+checkpoint
                            final ScheduleDeleteBlobFile scheduleDeleteBlobFile = new ScheduleDeleteBlobFile(
                                    vacuumQueue, requestDeleteBlobFile);
                            journalManager.listen(scheduleDeleteBlobFile);
                            transaction.registerListener(scheduleDeleteBlobFile);
                        } else {
                            // no journal (or recovery)... so go ahead and schedule
                            enqueueVacuum(vacuumQueue, requestDeleteBlobFile);
                        }
                    }

                    // update memory with the new value, and release other spinning threads
                    blobReference.count.set(newCount);

                    // done!
                    return;
                }
            }
        } catch (final InterruptedException e) {
            // thrown by persistQueue.put or Thread.sleep
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException {
        if (state.get() != State.OPEN) {
            throw new IOException("Blob Store is not open!");
        }

        // TODO(AR) should we enter an exclusive backup state?

        // NOTE: do not use try-with-resources here, closing the OutputStream will close the entire backup

        // backup the blob.dbx
        try {
            final OutputStream os = backup.newEntry(fileName(persistentFile));
            Files.copy(persistentFile, os);
        } finally {
            backup.closeEntry();
        }

        // backup the blob files
        for (final Path blobFile : FileUtils.list(blobDir, Files::isRegularFile)) {
            try {
                final OutputStream os = backup.newEntry(fileName(blobDir) + '/' + fileName(blobFile));
                Files.copy(persistentFile, os);
            } finally {
                backup.closeEntry();
            }
        }

        // backup the staging area
        for (final Path blobFile : FileUtils.list(stagingDir, Files::isRegularFile)) {
            try {
                final OutputStream os = backup.newEntry(fileName(blobDir) + '/' + fileName(stagingDir) + '/' + fileName(blobFile));
                Files.copy(persistentFile, os);
            } finally {
                backup.closeEntry();
            }
        }
    }

    @Override
    public void redo(final BlobLoggable blobLoggable) throws LogException {
        try {
            if (blobLoggable instanceof StoreBlobFileLoggable) {
                final StoreBlobFileLoggable storeBlobFileLoggable = (StoreBlobFileLoggable) blobLoggable;
                redoStoreBlobFile(storeBlobFileLoggable.getBlobId(), storeBlobFileLoggable.getStagedUuid());

            } else if (blobLoggable instanceof UpdateBlobRefCountLoggable) {
                final UpdateBlobRefCountLoggable updateBlobRefCountLoggable = (UpdateBlobRefCountLoggable) blobLoggable;
                updateBlogRefCount(updateBlobRefCountLoggable.getBlobId(), updateBlobRefCountLoggable.getNewCount());
            }
        } catch (final IOException e) {
            throw new LogException(e.getMessage(), e);
        }
    }

    @Override
    public void undo(final BlobLoggable blobLoggable) throws LogException {
        try {
            if (blobLoggable instanceof StoreBlobFileLoggable) {
                final StoreBlobFileLoggable storeBlobFileLoggable = (StoreBlobFileLoggable) blobLoggable;
                undoStoreBlobFile(storeBlobFileLoggable.getBlobId(), storeBlobFileLoggable.getStagedUuid());

            } else if (blobLoggable instanceof UpdateBlobRefCountLoggable) {
                final UpdateBlobRefCountLoggable updateBlobRefCountLoggable = (UpdateBlobRefCountLoggable) blobLoggable;
                updateBlogRefCount(updateBlobRefCountLoggable.getBlobId(), updateBlobRefCountLoggable.getCurrentCount());
            }
        } catch (final IOException e) {
            throw new LogException(e.getMessage(), e);
        }
    }

    /**
     * Recovery - redo: Promotes the Staged Blob File after performing some checks.
     *
     * This is possible because the Staged Blob file is not
     * removed until a checkpoint is written AFTER the transaction
     * was committed.
     *
     * @param blobId the blobId
     * @param stagedUuid The uuid of the staged blob file.
     *
     * @throws IOException if the staged blob file cannot be promoted
     */
    private void redoStoreBlobFile(final BlobId blobId, final String stagedUuid) throws IOException {
        final Path stagedBlobFile = stagingDir.resolve(stagedUuid);

        // check the staged file exists
        if (!Files.exists(stagedBlobFile)) {
            throw new IOException("Staged Blob File does not exist: " + stagedBlobFile.toAbsolutePath());
        }

        // check the staged file has the correct checksum
        final StreamableDigest streamableDigest = digestType.newStreamableDigest();
        FileUtils.digest(stagedBlobFile, streamableDigest);
        final String blobFilename = bytesToHex(blobId.getId());
        if (!Arrays.equals(blobId.getId(), streamableDigest.getMessageDigest())) {
            throw new IOException("Staged Blob File checksum '"
                    + bytesToHex(streamableDigest.getMessageDigest()) + "', does not match checksum of blobId ''"
                    + blobFilename + "'");
        }

        final Path blobFile = blobDir.resolve(blobFilename);

        Files.copy(stagedBlobFile, blobFile, REPLACE_EXISTING);
    }

    /**
     * Recovery - undo: Demotes the Blob File back to the staging area after performing some checks.
     *
     * @param blobId the blobId
     * @param stagedUuid The uuid of the staged blob file.
     *
     * @throws IOException if the blob file cannot be demoted to the staging area
     */
    private void undoStoreBlobFile(final BlobId blobId, final String stagedUuid) throws IOException {
        final String blobFilename = bytesToHex(blobId.getId());
        final Path blobFile = blobDir.resolve(blobFilename);

        // check the blob file exists
        if (!Files.exists(blobFile)) {
            throw new IOException("Blob File does not exist: " + blobFile.toAbsolutePath());
        }

        final Path stagedBlobFile = stagingDir.resolve(stagedUuid);

        Files.copy(blobFile, stagedBlobFile, REPLACE_EXISTING);
    }

    /**
     * Recovery - redo/undo: sets the reference count of a blob.
     *
     * @param blobId the blobId
     * @param count The reference count to set.
     *
     * @throws IOException if the blob's reference count cannot be set
     */
    private void updateBlogRefCount(final BlobId blobId, final int count) throws IOException {
        ((java.nio.Buffer) buffer).clear();
        ((java.nio.Buffer) buffer).limit(digestType.getDigestLengthBytes());  // we are only going to read the BlobIds

        // start immediately after the file header
        channel.position(BLOB_STORE_HEADER_LEN);

        boolean updatedCount = false;

        while (channel.read(buffer) > 0) {
            ((java.nio.Buffer) buffer).flip();
            final byte[] id = new byte[digestType.getDigestLengthBytes()];
            buffer.get(id);
            final BlobId readBlobId = new BlobId(id);

            if (blobId.equals(readBlobId)) {

                ((java.nio.Buffer) buffer).clear();
                ((java.nio.Buffer) buffer).limit(REFERENCE_COUNT_LEN);
                buffer.putInt(count);
                ((java.nio.Buffer) buffer).flip();

                channel.write(buffer);

                updatedCount = true;

                break;
            }

            // skip over the reference count
            channel.position(channel.position() + REFERENCE_COUNT_LEN);
        }

        /*
         * If we could not update the count of an existing entry, append a new entry to the end of the file.
         * We even include those entries with count = 0, so that their blob files will be cleared up by
         * the next call to compactPersistentReferences
         */
        if (!updatedCount) {
            ((java.nio.Buffer) buffer).clear();
            buffer.put(blobId.getId());
            buffer.putInt(count);

            ((java.nio.Buffer) buffer).flip();

            channel.write(buffer);
        }
    }

    /**
     * Stages a BLOB file.
     *
     * Writes a BLOB to a file in the Blob Store staging area.
     *
     * @param is data stream for the BLOB.
     * @return The file path, length and checksum of the staged BLOB
     * @throws IOException if an error occurs whilst staging the BLOB.
     */
    private Tuple3<Path, Long, MessageDigest> stage(final InputStream is) throws IOException {
        final Path stageFile = stagingDir.resolve(UUIDGenerator.getUUIDversion4());
        final CountingInputStream cis = new CountingInputStream(is);
        final StreamableDigest streamableDigest = digestType.newStreamableDigest();
        final DigestInputStream dis = new DigestInputStream(cis, streamableDigest);

        Files.copy(dis, stageFile);

        return Tuple(stageFile, cis.getByteCount(), streamableDigest.copyMessageDigest());
    }

    /**
     * Promotes a staged BLOB file to the BLOB store.
     *
     * Copies a staged BLOB file in the Blob Store staging area to
     * the live Blob Store.
     *
     * The staged BLOB will be removed as part of the Journalling
     * and Recovery.
     *
     * @param staged the staged BLOB.
     * @throws IOException if an error occurs whilst promoting the BLOB.
     */
    private void promote(final Tuple3<Path, Long, MessageDigest> staged) throws IOException {
        Files.copy(staged._1, blobDir.resolve(staged._3.toHexString()), REPLACE_EXISTING);
    }

    /**
     * Deletes a BLOB file from the Blob Store.
     *
     * @param blobDir the blob directory.
     * @param blobId the identifier of the BLOB file to delete.
     * @param always true if we should always be able to delete the file,
     *     false if the file may not exist.
     *
     * @throws IOException if the file cannot be deleted, for example if {@code always}
     *                     is set to true and the BLOB does not exist.
     */
    private static void deleteBlob(final Path blobDir, final BlobId blobId, final boolean always) throws IOException {
        final Path blobFile = blobDir.resolve(bytesToHex(blobId.getId()));
        if (always) {
            Files.delete(blobFile);
        } else {
            Files.deleteIfExists(blobFile);
        }
    }

    /**
     * Value class which represents the reference
     * count for a blob, the number of active readers,
     * and the offset of its entry in the persistent file.
     */
    static class BlobReference {
        static final int DELETING = -4;
        static final int STAGED = -3;
        static final int PROMOTING = -2;
        static final int UPDATING_COUNT = -1;

        final AtomicInteger count;
        final AtomicInteger readers = new AtomicInteger();

        static final long NOT_PERSISTED = -1;

        /**
         * Is only read and written from a single
         * thread in {@link PersistentWriter}
         * so no synchronization needed.
         */
        long persistentOffset = NOT_PERSISTED;

        /**
         * Construct a new Blob Reference which has not yet
         * been persisted.
         *
         * @param count the reference count
         *
         * The persistentOffset will be set to {@link #NOT_PERSISTED}
         */
        public BlobReference(final int count) {
            this.count = new AtomicInteger(count);
        }

        /**
         * Construct a new Blob Reference to a persistent
         * blob.
         *
         * @param count the reference count
         * @param persistentOffset the offset of the blob reference in the persistent file
         */
        public BlobReference(final int count, final long persistentOffset) {
            this.count = new AtomicInteger(count);
            this.persistentOffset = persistentOffset;
        }
    }

    /**
     * Value class which represents a lease
     * of a Blob's file.
     */
    private static class BlobFileLease {
        final Path path;
        final Runnable release;

        /**
         * @param path the blob file
         * @param release the action to run to release the lease
         */
        public BlobFileLease(final Path path, final Runnable release) {
            this.path = path;
            this.release = release;
        }
    }

    /**
     * A FilterInputStream which executes an action when
     * the underlying stream is closed.
     */
    public static class OnCloseInputStream extends FilterInputStream {
        private final Runnable closeAction;

        /**
         * Ensures that the close action is only executed once.
         */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * @param in  An input stream.
         * @param closeAction an action to run after the stream is closed.
         */
        public OnCloseInputStream(final InputStream in, final Runnable closeAction) {
            super(in);
            this.closeAction = closeAction;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return in.read(b);
        }

        /**
         * First, closes the underlying Input Stream
         * by calling {@code super#close()} and then
         * always executes the {@link #closeAction}.
         *
         * This method is idempotent, which is to say that
         * the operation will only be
         * applied once.
         *
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                try {
                    super.close();
                } finally {
                    closeAction.run();
                }
            }
        }
    }

    /**
     * A Journal and Transaction listener which will execute an action only
     * after the transaction has been completed (aborted or committed) and
     * a checkpoint has been written.
     */
    private static abstract class CommitThenCheckpointListener implements TxnListener, JournalManager.JournalListener {
        // written from single-thread, read from multiple threads
        private volatile boolean committedOrAborted = false;

        @Override
        public void commit() {
            committedOrAborted = true;
        }

        @Override
        public void abort() {
            committedOrAborted = true;
        }

        @Override
        public boolean afterCheckpoint(final long txnId) {
            if (!committedOrAborted) {
                /*
                 * we have not yet/committed or aborted
                 * so keep receiving checkpoint events!
                 */
                return true;
            }

            execute();

            return false;
        }

        /**
         * Called after the transaction has completed
         * and a checkpoint has been written.
         */
        public abstract void execute();
    }

    /**
     * Deletes a staged Blob file once the transaction that promoted it has
     * completed and a checkpoint has been written.
     */
    private static class DeleteStagedBlobFile extends CommitThenCheckpointListener {
        private final BlockingQueue<BlobVacuum.Request> vacuumQueue;
        private final BlobVacuum.RequestDeleteStagedBlobFile requestDeleteStagedBlobFile;

        /**
         * @param vacuumQueue the vacuum queue.
         * @param requestDeleteStagedBlobFile the request to delete the staged blob file.
         */
        public DeleteStagedBlobFile(final BlockingQueue<BlobVacuum.Request> vacuumQueue, final BlobVacuum.RequestDeleteStagedBlobFile requestDeleteStagedBlobFile) {
            this.vacuumQueue = vacuumQueue;
            this.requestDeleteStagedBlobFile = requestDeleteStagedBlobFile;
        }

        @Override
        public void execute() {
            enqueueVacuum(vacuumQueue, requestDeleteStagedBlobFile);
        }
    }

    /**
     * Schedules a Blob File for deletion once the transaction that removed it
     * has completed and a checkpoint has been written.
     */
    private static class ScheduleDeleteBlobFile extends CommitThenCheckpointListener {
        private final BlockingQueue<BlobVacuum.Request> vacuumQueue;
        private final BlobVacuum.RequestDeleteBlobFile requestDeleteBlobFile;

        /**
         * @param vacuumQueue the vacuum queue.
         * @param requestDeleteBlobFile the request to delete the blob file.
         */
        public ScheduleDeleteBlobFile(final BlockingQueue<BlobVacuum.Request> vacuumQueue,
                final BlobVacuum.RequestDeleteBlobFile requestDeleteBlobFile) {
            this.vacuumQueue = vacuumQueue;
            this.requestDeleteBlobFile = requestDeleteBlobFile;
        }

        @Override
        public void execute() {
            enqueueVacuum(vacuumQueue, requestDeleteBlobFile);
        }
    }

    private static void enqueueVacuum(final BlockingQueue<BlobVacuum.Request> vacuumQueue,
            final BlobVacuum.Request request) {
        try {
            /*
             * We offer with timeout because vacuum
             * is best effort rather than essential, anything
             * we cannot vacuum will be cleaned up at next startup
             * either as a part of crash recovery or compaction
             */
            if (!vacuumQueue.offer(request, VACUUM_ENQUEUE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                LOG.error("Timeout, could not not enqueue for vacuum: {}", request);
            }
        } catch (final InterruptedException e) {
            LOG.error("Interrupted, could not not enqueue for vacuum: {}", request, e);
            Thread.currentThread().interrupt();  // restore interrupted status!
            return;
        }
    }

    /**
     * The PersistentWriter is designed to be run
     * exclusively on its own single thread for a BlobStore
     * and is solely responsible for writing updates to the
     * persistent blob store file.
     */
    private static class PersistentWriter implements Runnable {

        /**
         * The Poison Pill can be placed on the {@link #persistQueue},
         * when encountered the {@link PersistentWriter} will
         * shutdown.
         */
        public static final Tuple3<BlobId, BlobReference, Integer> POISON_PILL = Tuple(null, null, null);

        private final BlockingQueue<Tuple3<BlobId, BlobReference, Integer>> persistQueue;
        private final ByteBuffer buffer;
        private final SeekableByteChannel channel;
        private final Runnable abnormalShutdownCallback;

        PersistentWriter(final BlockingQueue<Tuple3<BlobId, BlobReference, Integer>> persistQueue,
                final ByteBuffer buffer, final SeekableByteChannel channel, final Runnable abnormalShutdownCallback) {
            this.persistQueue = persistQueue;
            this.buffer = buffer;
            this.channel = channel;
            this.abnormalShutdownCallback = abnormalShutdownCallback;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    final Tuple3<BlobId, BlobReference, Integer> blobData = persistQueue.take();
                    if (blobData == POISON_PILL) {
                        // if we received the Poison Pill, we should shutdown!
                        break;  // exit
                    }

                    // write an entry
                    writeEntry(blobData._1, blobData._2, blobData._3);
                }
            } catch (final InterruptedException e) {
                // Restore the interrupted status
                LOG.error("PersistentWriter Shutting down due to interrupt: {}", e.getMessage());
                Thread.currentThread().interrupt();
                abnormalShutdownCallback.run();
            } catch (final IOException e) {
                LOG.error("PersistentWriter Shutting down, received: {}", e.getMessage(), e);
                abnormalShutdownCallback.run();
            }
        }

        /**
         * Stores the reference count for a blob to the persistent blob store file.
         *
         * When a new reference count is written for the first time it updates
         * the {@link BlobStoreImpl.BlobReference#persistentOffset} with the
         * location of the reference in the persistent file.
         *
         * @param blobId the identifier of the blob.
         * @param blobReference the reference details for the blob
         * @param newCount the new reference count to store.
         *
         * @throws IOException if an error occurs whilst writing the persistent file.
         */
        private void writeEntry(final BlobId blobId, final BlobReference blobReference, final int newCount)
                throws IOException {

            // if new record (i.e. not yet persisted), append to the end of the file
            if (blobReference.persistentOffset == NOT_PERSISTED) {
                blobReference.persistentOffset = channel.size();
            }

            channel.position(blobReference.persistentOffset);

            ((java.nio.Buffer) buffer).clear();
            buffer.put(blobId.getId());
            buffer.putInt(newCount);
            ((java.nio.Buffer) buffer).flip();

            channel.write(buffer);
        }
    }

    /**
     * The BlobVacuum is designed to be run
     * exclusively on its own single thread for a BlobStore
     * and is solely responsible for deleting blob files from
     * the blob file store.
     */
    private static class BlobVacuum implements Runnable {
        private final BlockingQueue<Request> vacuumQueue;

        public BlobVacuum(final BlockingQueue<Request> vacuumQueue) {
            this.vacuumQueue = vacuumQueue;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    final Request request = vacuumQueue.take();
                    if (!request.service()) {
                        // if the request could not be serviced then enque it so we can try again in future

                        try {
                            if (!vacuumQueue.offer(request, VACUUM_ENQUEUE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                                LOG.error("Timeout, could not not enqueue for vacuum: {}", request);
                            }
                        } catch (final InterruptedException e) {
                            LOG.error("Interrupted, could not not enqueue for vacuum: {}", request, e);
                            Thread.currentThread().interrupt();  // restore interrupted status!
                            throw e;
                        }
                    }
                }
            } catch (final InterruptedException e) {
                // expected when we are shutting down, only thrown by vacuumQueue.take/offer.
                // Any remaining objects in the queue which we have not yet vacuumed will
                // be taken care of by {@link #compactPersistentReferences(ByteBuffer, Path)
                // when the persistent blob store file is next opened

                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }
        }

        /**
         * The type of Vacuum Request
         */
        interface Request extends Comparable<Request> {
            /**
             * @return true if the request was serviced,
             *     false if the request should be re-scheduled.
             */
            boolean service();
        }

        /**
         * Vacuum request for deleting a Blob File for a Blob that has been removed.
         *
         * The Blob File will only be deleted if it has no references and no active readers.
         *
         * As vacuuming happens asynchronously, a new Blob may have been added which
         * has the same de-duplicated Blob File, causing an increase in references,
         * in which case the Blob File will be recycled instead
         * and will not be deleted here.
         */
        public static final class RequestDeleteBlobFile implements Request {
            private final ConcurrentMap<BlobId, BlobReference> references;
            private final Path blobDir;
            private final BlobId blobId;
            private final BlobReference blobReference;

            public RequestDeleteBlobFile(final ConcurrentMap<BlobId, BlobReference> references,
                    final Path blobDir, final BlobId blobId, final BlobReference blobReference) {
                this.references = references;
                this.blobDir = blobDir;
                this.blobId = blobId;
                this.blobReference = blobReference;
            }

            @Override
            public String toString() {
                return "RequestDeleteBlobFile(" + blobId + ")";
            }

            @Override
            public int compareTo(final Request other) {
                if (other instanceof RequestDeleteBlobFile) {
                    return ((RequestDeleteBlobFile) other).blobReference.readers.get() - blobReference.readers.get();
                } else {
                    // This class has higher priority than other classes
                    return 1;
                }
            }

            @Override
            public boolean service() {
                // we can only delete the blob file itelf if there are no references
                if (blobReference.count.compareAndSet(0, DELETING)) {

                    // make sure there are no readers still actively reading the blob file
                    if (blobReference.readers.get() == 0) {

                        // no more readers can be taken whilst count == DELETING, so we can delete
                        try {
                            deleteBlob(blobDir, blobId, true);
                        } catch (final IOException ioe) {
                            // non-critical error
                            LOG.error("Unable to delete blob file: {}", bytesToHex(blobId.getId()), ioe);
                        }

                        // remove from shared map
                        references.remove(blobId);

                    } else {
                        // reschedule the blob vacuum for later (when hopefully there are no active readers)
                        return false;
                    }

                    // NOTE: DELETING is the last state of a BlobReference#count -- there is no coming back from this!

                } else {
                    /*
                     * no-op: ignore this blob as it now again has active references,
                     * so we don't need to delete it, instead it has been recycled :-)
                     * Therefore we can just continue...
                     */
                }

                // we serviced this request!
                return true;
            }
        }

        public static final class RequestDeleteStagedBlobFile implements Request {
            private final Path stagingDir;
            private final String stagedBlobUuid;

            public RequestDeleteStagedBlobFile(final Path stagingDir, final String stagedBlobUuid) {
                this.stagingDir = stagingDir;
                this.stagedBlobUuid = stagedBlobUuid;
            }

            @Override
            public String toString() {
                return "RequestDeleteStagedBlobFile(" + stagedBlobUuid + ")";
            }

            @Override
            public int compareTo(final Request other) {
                if (other instanceof RequestDeleteStagedBlobFile) {
                    return stagedBlobUuid.compareTo(((RequestDeleteStagedBlobFile)other).stagedBlobUuid);
                } else {
                    // This class has lower priority than other classes
                    return -1;
                }
            }

            @Override
            public boolean service() {
                final Path stagedBlobFile = stagingDir.resolve(stagedBlobUuid);
                FileUtils.deleteQuietly(stagedBlobFile);
                return true;
            }
        }
    }
}
