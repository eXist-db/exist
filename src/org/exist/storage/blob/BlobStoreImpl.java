/*
 * Copyright (C) 2018 Adam Retter
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
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.crypto.digest.DigestInputStream;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;

import javax.annotation.Nullable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
 *
 * The Blob Store is backed to disk by a persistent store file which
 * reflects the in-memory state of BlobStore.
 *
 * The persistent store file will grow for each unqiue blob added to
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
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class BlobStoreImpl implements BlobStore {

    private static final Logger LOG = LogManager.getLogger(BlobStoreImpl.class);

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
    private static final byte[] BLOB_STORE_MAGIC_NUMBER = {0x0E, 0x0D, 0x0B, 0x02};

    /**
     * File header - blob store version
     */
    public static final short BLOB_STORE_VERSION = 1;

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
    private final PriorityBlockingQueue<Tuple2<BlobId, BlobReference>> vacuumQueue = new PriorityBlockingQueue<>(11,
            (br1, br2) -> br2._2.readers.get() - br1._2.readers.get());

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
        CLOSED,
        CLOSING,
        OPEN,
        OPENING
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
        if (state.get() == State.OPEN) {
            return;
        }

        if (!state.compareAndSet(State.CLOSED, State.OPENING)) {
            throw new IOException("BlobStore is not open");
        }

        // size the buffer to hold a complete entry
        final ByteBuffer buffer = ByteBuffer.allocate(digestType.getDigestLengthBytes() + REFERENCE_COUNT_LEN);
        SeekableByteChannel channel = null;
        try {
            // open the dbx file
            if (Files.exists(persistentFile)) {
                // compact existing blob store file and then open
                this.references = compactPersistentReferences(buffer, persistentFile);
                channel = Files.newByteChannel(persistentFile, WRITE);

            } else {
                // open existing blob store file
                this.references = new ConcurrentHashMap<>();
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

        // thread group for the blob store
        final ThreadGroup blobStoreThreadGroup = newInstanceSubThreadGroup(database, "blob-store");

        // startup the persistent writer thread
        this.persistentWriter = new PersistentWriter(persistQueue, buffer, channel, this::abnormalPersistentWriterShutdown);
        this.persistentWriterThread = new Thread(blobStoreThreadGroup, persistentWriter,
                nameInstanceThread(database, "blob-store.persistent-writer"));
        persistentWriterThread.start();

        // startup the blob vacuum thread
        this.blobVacuum = new BlobVacuum();
        this.blobVacuumThread = new Thread(blobStoreThreadGroup, blobVacuum,
                nameInstanceThread(database, "blob-store.vacuum"));
        blobVacuumThread.start();

        // we are now open!
        state.set(State.OPEN);
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
            buffer.clear();

            try (final SeekableByteChannel compactChannel = Files.newByteChannel(compactPersistentFile,
                    CREATE_NEW, APPEND)) {

                writeFileHeader(buffer, compactChannel);

                buffer.clear();

                while (channel.read(buffer) > -1) {
                    final byte[] id = new byte[digestType.getDigestLengthBytes()];
                    buffer.flip();
                    buffer.get(id);
                    final BlobId blobId = new BlobId(id);
                    final int count = buffer.getInt();

                    if (count == 0) {
                        orphanedBlobFileIds.add(blobId);
                    } else {
                        orphanedBlobFileIds.remove(blobId);

                        compactReferences.put(blobId, new BlobReference(count, compactChannel.position()));

                        buffer.flip();
                        compactChannel.write(buffer);
                    }

                    buffer.clear();
                }
            }
        }

        // cleanup any orphaned Blob files
        for (final BlobId orphanedBlobFileId : orphanedBlobFileIds) {
            deleteBlob(orphanedBlobFileId, false);
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

        buffer.clear();
        writeFileHeader(buffer);

        buffer.flip();
        buffer.limit(BLOB_STORE_HEADER_LEN);
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
     * Validates a file header.
     *
     * @param buffer a byte buffer to use
     * @param file the file containing the header.
     * @param channel the channel of the file to read from.
     *
     * @throws IOException if the header is invalid.
     */
    private void validateFileHeader(final ByteBuffer buffer, final Path file, final SeekableByteChannel channel)
            throws IOException {
        buffer.clear();
        buffer.limit(BLOB_STORE_HEADER_LEN);

        channel.read(buffer);

        buffer.flip();

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

        final Tuple3<Path, Long, MessageDigest> staged = stage(is);
        final BlobId blobId = new BlobId(staged._3.getValue());

        // if the blob entry does not exist, we exclusively compute it as STAGED.
        BlobReference blobReference = references.computeIfAbsent(blobId, k -> new BlobReference(STAGED));

        try {
            while (true) {

                if (blobReference.count.compareAndSet(STAGED, PROMOTING)) {
                    // we are the only thread that can be in this branch for the blobId
                    promote(staged);

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
                    // we are the only thread that can be in this branch for the blobId

                    final int newCount = count + 1;

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
            return fnFile.apply(blobFileLease.path);
        } finally {
            blobFileLease.release.run();  // MUST release the read lease!
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
                    return new BlobFileLease(blobFile, () -> blobReference.readers.decrementAndGet());
                }
            }
        } catch (final InterruptedException e) {
            // only thrown by Thread.sleep above
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
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
                    // we are the only thread that can be in this branch for the blobId

                    final int newCount = count - 1;

                    // persist the new value
                    persistQueue.put(Tuple(blobId, blobReference, newCount));

                    if (newCount == 0) {
                        // schedule blob file for vacuum.
                        vacuumQueue.put(Tuple(blobId, blobReference));
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
    public void close() throws IOException {
        // check the blob store is open
        if (state.get() == State.CLOSED) {
            return;
        }
        if (!state.compareAndSet(State.OPEN, State.CLOSING)) {
            throw new IOException("BlobStore is not open");
        }

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
            // we are now closed!
            state.set(State.CLOSED);
        }
    }

    /**
     * Closes the BlobStore if the {@link #persistentWriter} has
     * to shutdown due to abnormal circumstances.
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

            // NOTE: persistent writer thread will join when this method finished!

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
            // we are now closed!
            state.set(State.CLOSED);
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
        // TODO(AR) upgrade to com.fasterxml.uuid.java-uuid-generator
        final Path stageFile = stagingDir.resolve(UUID.randomUUID().toString());
        final CountingInputStream cis = new CountingInputStream(is);
        final StreamableDigest streamableDigest = digestType.newStreamableDigest();
        final DigestInputStream dis = new DigestInputStream(cis, streamableDigest);

        Files.copy(dis, stageFile);

        return Tuple(stageFile, cis.getByteCount(), streamableDigest.copyMessageDigest());
    }

    /**
     * Promotes a staged BLOB file to the BLOB store.
     *
     * Moves a staged BLOB file in the Blob Store staging area to
     * the live Blob Store.
     *
     * @param staged the staged BLOB.
     * @throws IOException if an error occurs whilst promoting the BLOB.
     */
    private void promote(final Tuple3<Path, Long, MessageDigest> staged) throws IOException {
        Files.move(staged._1, blobDir.resolve(staged._3.toHexString()), ATOMIC_MOVE, REPLACE_EXISTING);
    }

    /**
     * Deletes a BLOB file from the Blob Store.
     *
     * @param blobId the identifier of the BLOB file to delete.
     * @param always true if we should always be able to delete the file,
     *     false if the file may not exist.
     *
     * @throws IOException if the file cannot be deleted, for example if {@code always}
     *                     is set to true and the BLOB does not exist.
     */
    private void deleteBlob(final BlobId blobId, final boolean always) throws IOException {
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
         * by calling {@link super#close()} and then
         * always executes the {@link #closeAction}.
         *
         * This method is idempotent, which is to say that
         * the operation will only be
         * applied once.
         *
         * @exception IOException if an I/O error occurs.
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
                        shutdown();
                        break;  // exit
                    }

                    // write an entry
                    writeEntry(blobData._1, blobData._2, blobData._3);
                }
            } catch (final InterruptedException e) {
                // Restore the interrupted status
                LOG.error("PersistentWriter Shutting down due to interrupt: " + e.getMessage());
                Thread.currentThread().interrupt();
                shutdown();
                abnormalShutdownCallback.run();
            } catch (final IOException e) {
                LOG.error("PersistentWriter Shutting down, received: " + e.getMessage(), e);
                shutdown();
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

            buffer.clear();
            buffer.put(blobId.getId());
            buffer.putInt(newCount);
            buffer.flip();

            channel.write(buffer);
        }

        /**
         * Cleans up the resources associated
         * with the persistent writer.
         *
         * Closes the {@link #channel}.
         */
        private void shutdown() {
            buffer.clear();

            // close the file channel
            if (channel != null) {
                try {
                    channel.close();
                } catch (final IOException e) {
                    // non-critical error
                    LOG.error("Error whilst closing blob.dbx: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * The BlobVacuum is designed to be run
     * exclusively on its own single thread for a BlobStore
     * and is solely responsible for deleting blob files from
     * the blob file store.
     */
    private class BlobVacuum implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Tuple2<BlobId, BlobReference> blobData = vacuumQueue.take();
                    final BlobId blobId = blobData._1;
                    final BlobReference blobReference = blobData._2;

                    // we can only delete if there are no references
                    if (blobReference.count.compareAndSet(0, DELETING)) {

                        // make sure there are no readers still actively reading
                        if (blobReference.readers.get() == 0) {

                            // no more readers can be taken whilst count == DELETING, so we can delete
                            try {
                                deleteBlob(blobId, true);
                            } catch (final IOException ioe) {
                                // non-critical error
                                LOG.error("Unable to delete blob file: " + bytesToHex(blobId.getId()), ioe);
                            }

                            // remove from shared map
                            references.remove(blobId);

                        } else {
                            // reschedule the blob vacuum for later (when hopefully there are no active readers)
                            vacuumQueue.put(blobData);
                        }

                        // NOTE: DELETING is the last state of a BlobReference#count -- there is no future change from this!

                    } //else {
                    // ignore this blob and continue as there are now again active references, so we don't need to dalete
                    //}
                }
            } catch (final InterruptedException e) {
                // expected when we are shutting down, only thrown by vacuumQueue.take.
                // Any remaining objects in the queue which we have not yet vacuumed will
                // be taken care of by {@link #compactPersistentReferences(ByteBuffer, Path)
                // when the persistent blob store file is next opened

                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }
        }
    }
}
