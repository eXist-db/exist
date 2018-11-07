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
import org.exist.backup.RawDataBackup;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Store for BLOBs (Binary Large Objects).
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public interface BlobStore extends Closeable {

    /**
     * Open's the BLOB Store.
     *
     * Should be called before any other actions.
     *
     * @throws IOException if the store cannot be opened.
     */
    void open() throws IOException;

    /**
     * Add a BLOB to the BLOB Store.
     *
     * @param transaction the current database transaction.
     * @param is the input stream containing the blob data.
     *
     * @return an identifier representing the stored blob and the size of the blob in bytes.
     *
     * @throws IOException if the BLOB cannot be added.
     */
    Tuple2<BlobId, Long> add(final Txn transaction, final InputStream is) throws IOException;

    /**
     * Get a BLOB from the BLOB Store.
     *
     * @param transaction the current database transaction.
     * @param blobId the identifier representing the blob to be retrieved.
     *
     * @return an InputStream for accessing the BLOB data, or null if there is no such BLOB.
     *     NOTE the stream MUST be closed when the caller has finished
     *     with it to release any associated resources.
     *
     * @throws IOException if an error occurs whilst retrieving the BLOB.
     */
    @Nullable InputStream get(final Txn transaction, final BlobId blobId) throws IOException;

    /**
     * Perform an operation with a {@link Path} reference to a BLOB.
     *
     * NOTE: Use of this method should be avoided where possible. It only
     * exists for integration with tools external to Java which can only
     * work with File Paths and where making a copy of the file is not
     * necessary.
     *
     * WARNING: The provided {@link Path} MUST ONLY be used for
     * READ operations, any WRITE/DELETE operation will corrupt the
     * integrity of the blob store.
     *
     * Consider if you really need to use this method. It is likely you could
     * instead use {@link #get(Txn, BlobId)} and make a copy of the data to
     * a temporary file.
     *
     * Note that any resources associated with the BLOB file
     * may not be released until the {@code fnFile} has finished executing.
     *
     * USE WITH CAUTION!
     *
     * @param transaction the current database transaction.
     * @param blobId the identifier representing the blob to be retrieved.
     * @param <T> the type of the return value
     * @param fnFile a function which performs a read-only operation on the BLOB file.
     *     If you wish to handle exceptions in your function you should consider
     *     {@link com.evolvedbinary.j8fu.Try} or similar.
     *
     * @throws IOException if an error occurs whilst retrieving the BLOB file.
     */
    <T> T with(final Txn transaction, final BlobId blobId, final Function<Path, T> fnFile) throws IOException;

    /**
     * Remove a BLOB from the BLOB Store.
     *
     * @param transaction the current database transaction.
     * @param blobId the identifier representing the blob to be removed.
     *
     * @throws IOException if an error occurs whilst removing the BLOB.
     */
    void remove(final Txn transaction, final BlobId blobId) throws IOException;

    /**
     * Backup the Blob Store to the backup.
     *
     * @param backup the backup to write the Blob Store to.
     */
    void backupToArchive(final RawDataBackup backup) throws IOException;
}
