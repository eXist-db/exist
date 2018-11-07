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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.tuple.Tuple3;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.input.CountingInputStream;
import org.exist.backup.RawDataBackup;
import org.exist.storage.txn.Txn;
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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;
import static org.exist.util.HexEncoder.bytesToHex;

/**
 * De-duplicating store for BLOBs (Binary Large Objects).
 *
 * Each unqiue BLOBs is stored by checksum into a file on disk.
 *
 * For each BLOB a reference count is also kept. Adding a BLOB which is already present
 * increments the reference count only, it does not require any additional storage.
 * Removing a BLOB decrements its reference count, BLOBs are only removed when
 * their reference count reaches zero.
 *
 * The persistent store file reflects the in-memory state of BlobStore.
 * The persistent store file will grow for each unqiue blob added to
 * the system, space is not reclaimed in the persistent file until
 * {@link #compactPersistentReferences(Path)} is called, which typically
 * happens the next time the blob store re-opened.
 *
 * Each unique blob typically takes up only 36 bytes in the
 * persistent store file, but this can vary if a smaller or larger
 * digestType is specified.
 * On-line compaction of the persistent file could be added in
 * future with relative ease if determined necessary.
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
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class BlobStoreImpl implements BlobStore {

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

    private final StampedLock referencesLock = new StampedLock();
    @GuardedBy("referencesLock")
    private Map<BlobId, BlobReference> references;

    private final Lock channelLock = new ReentrantLock();
    @GuardedBy("channelLock")
    private FileChannel channel = null;
    @GuardedBy("channelLock")
    private final ByteBuffer bufEntry;

    private final Path persistentFile;
    private final Path blobDir;
    private final Path stagingDir;
    private final DigestType digestType;

    private final static int CLOSED = 1;
    private final static int CLOSING = 2;
    private final static int OPEN = 3;
    private final static int OPENING = 4;

    private final AtomicInteger state = new AtomicInteger(CLOSED);

    /**
     * @param persistentFile the file path for the persistent blob store metadata.
     * @param blobDir        the directory to store BLOBs in.
     * @param digestType     the message digest type to use for creating checksums of the BLOBs.
     */
    public BlobStoreImpl(final Path persistentFile, final Path blobDir, final DigestType digestType) {
        this.persistentFile = persistentFile;
        this.blobDir = blobDir;
        this.stagingDir = blobDir.resolve("staging");
        this.digestType = digestType;
        this.bufEntry = ByteBuffer.allocate(digestType.getDigestLengthBytes() + REFERENCE_COUNT_LEN);
    }

    @Override
    public void open() throws IOException {
        if (state.get() == OPEN) {
            return;
        }

        if (!state.compareAndSet(CLOSED, OPENING)) {
            throw new IOException("BlobStore is not open");
        }

        try {
            final long writeStamp = referencesLock.writeLock();
            try {
                channelLock.lock();
                try {
                    if (Files.exists(persistentFile)) {
                        // compact existing blob store file and then open
                        this.references = compactPersistentReferences(persistentFile);
                        this.channel = (FileChannel) Files.newByteChannel(persistentFile, WRITE);

                    } else {
                        // open existing blob store file
                        this.references = new HashMap<>();
                        this.channel = (FileChannel) Files.newByteChannel(persistentFile, CREATE_NEW, WRITE);
                        writeFileHeader(channel);
                    }
                } finally {
                    channelLock.unlock();
                }
            } finally {
                referencesLock.unlockWrite(writeStamp);
            }

            Files.createDirectories(stagingDir);

            state.set(OPEN);
        } catch (final IOException e) {
            state.set(CLOSED);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (state.get() == CLOSED) {
            return;
        }

        if (!state.compareAndSet(OPEN, CLOSING)) {
            throw new IOException("BlobStore is not open");
        }

        channelLock.lock();
        try {
            if (channel != null) {
                bufEntry.clear();
                channel.close();
                channel = null;
            }
        } finally {
            state.set(CLOSED);
            channelLock.unlock();
        }
    }

    /**
     * Compacts an existing Blob Store file.
     * <p>
     * Reads the existing Blob Store file and copies non zero reference
     * entries to a new Blob Store file. We call this compaction.
     * Once complete, the existing file is replaced with the new file.
     *
     * @param persistentFile an existing persistentFile to compact.
     * @return An in-memory representation of the compacted Blob Store
     * @throws IOException if an error occurs during compaction.
     */
    private Map<BlobId, BlobReference> compactPersistentReferences(final Path persistentFile) throws IOException {
        final Map<BlobId, BlobReference> compactReferences = new HashMap<>();
        final Path compactPersistentFile = persistentFile.getParent().resolve(persistentFile.getFileName() + ".new." + System.currentTimeMillis());

        try (final SeekableByteChannel channel = Files.newByteChannel(persistentFile, READ)) {

            validateFileHeader(persistentFile, channel);
            bufEntry.clear();

            try (final SeekableByteChannel compactChannel = Files.newByteChannel(compactPersistentFile, CREATE_NEW, APPEND)) {

                long position = writeFileHeader(compactChannel);

                bufEntry.clear();

                int read;
                while ((read = channel.read(bufEntry)) > -1) {
                    bufEntry.flip();
                    final byte[] id = new byte[digestType.getDigestLengthBytes()];
                    bufEntry.get(id);
                    final BlobId blobId = new BlobId(id);
                    final int count = bufEntry.getInt();

                    if (count == 0) {
                        deleteBlob(blobId, false);
                    } else {
                        compactReferences.put(blobId, new BlobReference(count, position));

                        bufEntry.flip();
                        compactChannel.write(bufEntry);
                    }

                    bufEntry.clear();

                    position += read;
                }
            }
        }

        // replace the persistent file with the new compact persistent file
        Files.move(compactPersistentFile, persistentFile, ATOMIC_MOVE, REPLACE_EXISTING);

        return compactReferences;
    }

    /**
     * Writes the persistent file header
     *
     * @param channel the channel to write to
     * @return the number of bytes written.
     * @throws IOException if an error occurs whilst writing the header.
     */
    private long writeFileHeader(final SeekableByteChannel channel) throws IOException {
        final long start = channel.position();

        bufEntry.clear();
        writeFileHeader(bufEntry);

        bufEntry.flip();
        bufEntry.limit(BLOB_STORE_HEADER_LEN);
        channel.write(bufEntry);

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
     * @param file    the file containing the header.
     * @param channel the channel of the file to read from.
     * @throws IOException if the header is invalid.
     */
    private void validateFileHeader(final Path file, final SeekableByteChannel channel) throws IOException {
        bufEntry.clear();
        bufEntry.limit(BLOB_STORE_HEADER_LEN);

        channel.read(bufEntry);

        bufEntry.flip();

        final boolean validMagic =
                bufEntry.get() == BLOB_STORE_MAGIC_NUMBER[0]
                        && bufEntry.get() == BLOB_STORE_MAGIC_NUMBER[1]
                        && bufEntry.get() == BLOB_STORE_MAGIC_NUMBER[2]
                        && bufEntry.get() == BLOB_STORE_MAGIC_NUMBER[3];

        if (!validMagic) {
            throw new IOException("File was not recognised as a valid eXist-db Blob Store: " + file.toAbsolutePath().toString());
        }

        // check the version of the blob store format
        final short storedVersion = bufEntry.getShort();
        final boolean validVersion =
                storedVersion == BLOB_STORE_VERSION;

        if (!validVersion) {
            throw new IOException("Blob Store file was version " + storedVersion + ", but required version " + BLOB_STORE_VERSION + ": " + file.toAbsolutePath().toString());
        }
    }

    @Override
    public Tuple2<BlobId, Long> add(final Txn transaction, final InputStream is) throws IOException {
        if (state.get() != OPEN) {
            throw new IOException("BlobStore is not open");
        }

        final Tuple3<Path, Long, MessageDigest> staged = stage(is);
        final BlobId blobId = new BlobId(staged._3.getValue());

        // get and lock the blobReference
        final long writeStamp = referencesLock.writeLock();
        final BlobReference blobReference = references.computeIfAbsent(blobId, k -> new BlobReference());

        //TODO(AR) we could do optimistic read and optimistic read lock on the references before taking the write lock if there is no reference

        // lock handover writeStamp -> blobReference#lock
        final long blobWriteStamp = blobReference.lock.writeLock();
        referencesLock.unlockWrite(writeStamp);

        try {
            if (blobReference.count == 0) {
                promote(staged);
                blobReference.count++;
                storePersistentReferenceCount(blobId, blobReference);
            } else {
                blobReference.count++;
                updatePersistentReferenceCount(blobId, blobReference);
                unstage(staged);    //TODO(AR) could be done asynchronously or scheduled on a background thread
            }

            return Tuple(blobId, staged._2);

        } finally {
            blobReference.lock.unlockWrite(blobWriteStamp);
        }
    }

    /**
     * Stores a new reference count to the persistent blob store file.
     * <p>
     * Once the new reference count is written, it updates
     * the {@link BlobReference#persistentOffset} with the
     * location of the reference in the persistent file.
     *
     * @param blobId        the identifier of the blob.
     * @param blobReference the reference details for the blob
     * @throws IOException if an error occurs whilst writing the persistent file.
     */
    private void storePersistentReferenceCount(final BlobId blobId, final BlobReference blobReference)
            throws IOException {
        channelLock.lock();
        try {
            // move to the end of the file
            final long offset = channel.size();
            channel.position(offset);

            bufEntry.clear();
            bufEntry.put(blobId.getId());
            bufEntry.putInt(blobReference.count);
            bufEntry.flip();

            channel.write(bufEntry);

            blobReference.persistentOffset = offset;
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * Updates a reference count in the persistent blob store file.
     *
     * @param blobId        the identifier of the blob.
     * @param blobReference the reference details for the blob
     * @throws IOException if an error occurs whilst writing the persistent file.
     */
    private void updatePersistentReferenceCount(final BlobId blobId, final BlobReference blobReference)
            throws IOException {
        channelLock.lock();
        try {
            channel.position(blobReference.persistentOffset + blobId.getId().length);  // offset + blobId#length

            bufEntry.clear();
            bufEntry.putInt(blobReference.count);
            bufEntry.flip();
            bufEntry.limit(REFERENCE_COUNT_LEN);

            channel.write(bufEntry);
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * Stages a BLOB file.
     * <p>
     * Writes a BLOB to a file in the Blob Store staging area.
     *
     * @param is data stream for the BLOB.
     * @return The file path, length and checksum of the staged BLOB
     * @throws IOException if an error occurs whilst staging the BLOB.
     */
    private Tuple3<Path, Long, MessageDigest> stage(final InputStream is) throws IOException {
        // TODO(AR) use fast UUID
        final Path stageFile = stagingDir.resolve(UUID.randomUUID().toString());
        final CountingInputStream cis = new CountingInputStream(is);
        final StreamableDigest streamableDigest = digestType.newStreamableDigest();
        final DigestInputStream dis = new DigestInputStream(cis, streamableDigest);

        Files.copy(dis, stageFile);

        return Tuple(stageFile, cis.getByteCount(), streamableDigest.copyMessageDigest());
    }

    /**
     * Un-stages a BLOB file.
     * <p>
     * Removes a BLOB file from the Blob Store staging area.
     *
     * @param staged the staged BLOB.
     * @throws IOException if an error occurs whilst un-staging the BLOB.
     */
    private void unstage(final Tuple3<Path, Long, MessageDigest> staged) throws IOException {
        Files.delete(staged._1);
    }

    /**
     * Promotes a staged BLOB file to the BLOB store.
     * <p>
     * Moves a staged BLOB file in the Blob Store staging area to
     * the main Blob Store.
     *
     * @param staged the staged BLOB.
     * @throws IOException if an error occurs whilst promoting the BLOB.
     */
    private void promote(final Tuple3<Path, Long, MessageDigest> staged) throws IOException {
        Files.move(staged._1, blobDir.resolve(staged._3.toHexString()), ATOMIC_MOVE);
    }

//    @Override
//    @Nullable
//    public InputStream get(final Txn transaction, final BlobId blobId) throws IOException {
//        if (state.get() != OPEN) {
//            throw new IOException("BlobStore is not open");
//        }
//
//        // optimistic check of null blobReference
//        long stamp = referencesLock.tryOptimisticRead();
//        BlobReference blobReference = references.get(blobId);
//        if (blobReference == null && referencesLock.validate(stamp)) {
//            return null;
//        }
//
//        // pessimistic check of null blobReference
//        stamp = referencesLock.readLock();
//        blobReference = references.get(blobId);
//        if (blobReference == null) {
//            referencesLock.unlockRead(stamp);
//            return null;
//        }
//
//        // optimistic check of 0 blobReference#count
//        long blobStamp = blobReference.lock.tryOptimisticRead();
//        if (blobReference.count == 0 && blobReference.lock.validate(blobStamp)) {
//            referencesLock.unlockRead(stamp);
//            return null;
//        }
//
//        // lock handover referencesLock -> blobReferences#Lock
//        final long blobReadLock = blobReference.lock.readLock();
//        referencesLock.unlockRead(stamp);
//
//        // pessimistic check of 0 blobReference#count
//        if (blobReference.count == 0) {
//            blobReference.lock.unlockRead(blobReadLock);
//            return null;
//        }
//
//        // get the blob
//        final Path blobFile = blobDir.resolve(bytesToHex(blobId.getId()));
//        try {
//            final BlobReference blobReference1 = blobReference;
//            return new OnCloseInputStream(Files.newInputStream(blobFile), () -> blobReference1.lock.unlockRead(blobReadLock));
//        } catch (final IOException e) {
//            blobReference.lock.unlockRead(blobReadLock);
//            throw e;
//        }
//    }

    @Override
    @Nullable public InputStream get(final Txn transaction, final BlobId blobId) throws IOException {
        // get the blob
        final LockedBlobFile lockedBlobFile = getReadLockedBlobFile(transaction, blobId);

        // blob file is unlocked either when the input stream is closed or an error occurs
        try {
            return new OnCloseInputStream(Files.newInputStream(lockedBlobFile.path), lockedBlobFile.unlocker);
        } catch (final IOException e) {
            lockedBlobFile.unlocker.run();  // release the read lock
            throw e;
        }
    }

    @Override
    public <T> T with(final Txn transaction, final BlobId blobId, final Function<Path, T> fnFile) throws IOException {
        // get the blob
        final LockedBlobFile lockedBlobFile = getReadLockedBlobFile(transaction, blobId);
        try {
            return fnFile.apply(lockedBlobFile.path);
        } finally {
            lockedBlobFile.unlocker.run();  // release the read lock
        }
    }

    private LockedBlobFile getReadLockedBlobFile(final Txn transaction, final BlobId blobId) throws IOException {
        if (state.get() != OPEN) {
            throw new IOException("BlobStore is not open");
        }

        // optimistic check of null blobReference
        long stamp = referencesLock.tryOptimisticRead();
        BlobReference blobReference = references.get(blobId);
        if (blobReference == null && referencesLock.validate(stamp)) {
            return null;
        }

        // pessimistic check of null blobReference
        stamp = referencesLock.readLock();
        blobReference = references.get(blobId);
        if (blobReference == null) {
            referencesLock.unlockRead(stamp);
            return null;
        }

        // optimistic check of 0 blobReference#count
        long blobStamp = blobReference.lock.tryOptimisticRead();
        if (blobReference.count == 0 && blobReference.lock.validate(blobStamp)) {
            referencesLock.unlockRead(stamp);
            return null;
        }

        // lock handover referencesLock -> blobReferences#Lock
        final long blobReadLock = blobReference.lock.readLock();
        referencesLock.unlockRead(stamp);

        // pessimistic check of 0 blobReference#count
        if (blobReference.count == 0) {
            blobReference.lock.unlockRead(blobReadLock);
            return null;
        }

        // get the blob
        final Path blobFile = blobDir.resolve(bytesToHex(blobId.getId()));

        final BlobReference exportBlobReference = blobReference;
        return new LockedBlobFile(blobFile, () -> exportBlobReference.lock.unlockRead(blobReadLock));
    }

    @Override
    public void remove(final Txn transaction, final BlobId blobId) throws IOException {
        if (state.get() != OPEN) {
            throw new IOException("BlobStore is not open");
        }

        // optimistic check of null blobReference
        long stamp = referencesLock.tryOptimisticRead();
        BlobReference blobReference = references.get(blobId);
        if (blobReference == null && referencesLock.validate(stamp)) {
            return;
        }

        // pessimistic check of null blobReference
        stamp = referencesLock.readLock();
        blobReference = references.get(blobId);
        if (blobReference == null) {
            referencesLock.unlockRead(stamp);
            return;
        }

        // optimistic check of 0 blobReference#count
        long blobStamp = blobReference.lock.tryOptimisticRead();
        if (blobReference.count == 0 && blobReference.lock.validate(blobStamp)) {
            referencesLock.unlockRead(stamp);
            return;
        }

        // lock handover referencesLock -> blobReferences#lock
        final long blobReadLock = blobReference.lock.writeLock();
        referencesLock.unlockRead(stamp);

        // pessimistic check of 0 blobReference#count
        if (blobReference.count == 0) {
            blobReference.lock.unlockWrite(blobReadLock);
            return;
        }

        // remove the blob
        blobReference.count--;
        try {
            updatePersistentReferenceCount(blobId, blobReference);

            if (blobReference.count == 0) {
                deleteBlob(blobId, true);
            }
        } finally {
            blobReference.lock.unlockWrite(blobReadLock);
        }
    }

    /**
     * Deletes a BLOB file from the Blob Store.
     *
     * @param blobId the identifier of the BLOB file to delete.
     * @param always true if we should always be able to delete the file,
     *               false if the file may not exist.
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

    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException {
        Map<BlobId, BlobReference> referencesCopy = null;

        // make a local copy of the references
        long stamp = referencesLock.tryOptimisticRead();
        referencesCopy = new HashMap<>(references);
        if (!referencesLock.validate(stamp)) {
            stamp = referencesLock.readLock();
            try {
                referencesCopy = new HashMap<>(references);
            } finally {
                referencesLock.unlockRead(stamp);
            }
        }

        // export the blob files
        final Map<BlobId, Integer> exportedBlobFiles = new HashMap<BlobId, Integer>();
        for (final Map.Entry<BlobId, BlobReference> blobReference : referencesCopy.entrySet()) {
            final BlobId blobId = blobReference.getKey();
            final BlobReference reference = blobReference.getValue();

            stamp = reference.lock.readLock();
            try {
                final Path blobFile = blobDir.resolve(bytesToHex(blobId.getId()));

                // blob might have been removed in the meantime e.g. reference.count could be == 0
                if (reference.count > 0) {
                    // do not use try-with-resources here, closing the OutputStream will close the entire backup
                    final OutputStream os = backup.newEntry(persistentFile.relativize(blobFile).toString());
                    try {
                        Files.copy(blobFile, os);
                    } finally {
                        backup.closeEntry();
                    }

                    exportedBlobFiles.put(blobId, reference.count);
                }
            } finally {
                reference.lock.unlockRead(stamp);
            }
        }

        // export the blob.dbx
        // do not use try-with-resources here, closing the OutputStream will close the entire backup
        final WritableByteChannel backupBlobChannel = Channels.newChannel(backup.newEntry(persistentFile.getFileName().toString()));
        try {
            final ByteBuffer buffer = ByteBuffer.allocate(digestType.getDigestLengthBytes() + REFERENCE_COUNT_LEN);
            writeFileHeader(buffer);

            buffer.flip();
            buffer.limit(BLOB_STORE_HEADER_LEN);

            backupBlobChannel.write(buffer);

            for (final Map.Entry<BlobId, Integer> exportedBlobFile : exportedBlobFiles.entrySet()) {
                buffer.clear();
                buffer.put(exportedBlobFile.getKey().getId());
                buffer.putInt(exportedBlobFile.getValue());

                buffer.flip();
                backupBlobChannel.write(buffer);
            }
        } finally {
            backup.closeEntry();
        }
    }

    /**
     * Value class which represents the reference
     * count for a blob and the offset of its entry
     * in the persistent file.
     */
    private static class BlobReference {
        final StampedLock lock = new StampedLock();
        @GuardedBy("lock")
        int count = 0;
        @GuardedBy("lock")
        long persistentOffset = -1;

        public BlobReference() {
        }

        /**
         * @param count            the reference count
         * @param persistentOffset the offset of the blob reference in the persistent file
         */
        public BlobReference(final int count, final long persistentOffset) {
            this.count = count;
            this.persistentOffset = persistentOffset;
        }
    }

    private static class LockedBlobFile {
        final Path path;
        final Runnable unlocker;

        public LockedBlobFile(final Path path, final Runnable unlocker) {
            this.path = path;
            this.unlocker = unlocker;
        }
    }

    /**
     * A FilterInputStream which executes an action when
     * the underlying stream is closed.
     */
    public static class OnCloseInputStream extends FilterInputStream {
        private final Runnable closeAction;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * @param in          An input stream.
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
}
