/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 *
 * Serialized binary format is as follows:
 *
 * [deletePathLen, deletePath, deleteDigestType, deleteDigest, dataPathLen, dataPath, dataDigestType, dataDigest]
 *
 * deletePathLen:       2 bytes, unsigned short
 * deletePath:          var length bytes, UTF-8 encoded java.lang.String
 * deleteDigestType:    1 byte
 * deleteDigest:        n-bytes, where n is deteremined by {@code deleteDigestType}
 * dataPathLen:         2 bytes, unsigned short
 * dataPath:            var length bytes, UTF-8 encoded java.lang.String
 * dataDigestType:      1 byte
 * dataDigest:          n-bytes, where n is deteremined by {@code dataDigestType}
 */
public class DeleteBinaryLoggable extends AbstractBinaryLoggable {
    private byte[] deletePath;              // the file to be deleted
    private MessageDigest deleteDigest;
    private byte[] dataPath;                // the data before the file was deleted (i.e. the current value)
    private MessageDigest dataDigest;

    /**
     * Creates a new instance of DeleteBinaryLoggable.
     *
     * @param broker The database broker.
     * @param txn The database transaction.
     * @param delete The file that is to be deleted from the database.
     * @param deleteDigest digest of the {@code delete} content
     * @param data A copy of the data before the file was deleted (i.e. the current value).
     * @param dataDigest digest of the {@code data} content
     */
    public DeleteBinaryLoggable(final DBBroker broker, final Txn txn, final Path delete,
            final MessageDigest deleteDigest, final Path data, final MessageDigest dataDigest) {
        super(NativeBroker.LOG_DELETE_BINARY, txn.getId());
        this.deletePath = getPathData(delete);
        checkPathLen(getClass().getSimpleName(), "deletePath", deletePath);
        this.deleteDigest = deleteDigest;

        this.dataPath = getPathData(data);
        checkPathLen(getClass().getSimpleName(), "dataPath", dataPath);
        this.dataDigest = dataDigest;
    }

    /**
     * Creates a new instance of DeleteBinaryLoggable.
     *
     * @param broker The database broker.
     * @param transactionId The database transaction id.
     */
    public DeleteBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_DELETE_BINARY, transactionId);
    }

    @Override
    public int getLogSize() {
        return
                2 +
                deletePath.length +
                1 +
                deleteDigest.getDigestType().getDigestLengthBytes() +
                2 +
                dataPath.length +
                1 +
                dataDigest.getDigestType().getDigestLengthBytes();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putShort(asUnsignedShort(deletePath.length));
        out.put(deletePath);
        writeMessageDigest(out, deleteDigest);

        out.putShort(asUnsignedShort(dataPath.length));
        out.put(dataPath);
        writeMessageDigest(out, dataDigest);
    }

    @Override
    public void read(final ByteBuffer in) {
        final int replacePathLen = asSignedInt(in.getShort());
        this.deletePath = new byte[replacePathLen];
        in.get(deletePath);
        this.deleteDigest = readMessageDigest(in);

        final int dataPathLen = asSignedInt(in.getShort());
        this.dataPath = new byte[dataPathLen];
        in.get(dataPath);
        this.dataDigest = readMessageDigest(in);
    }

    @Override
    public void redo() throws LogException {
        //we need to delete the deletePath file

        final Path delete = getPath(deletePath);

        if (!Files.exists(delete)) {
            // covers the use-case where the previous operation was a delete
            return;
        }

        try {
            final Path data = getPath(dataPath);

            // ensure integrity of data file first!
            final StreamableDigest dataStreamableDigest = dataDigest.getDigestType().newStreamableDigest();
            FileUtils.digest(data, dataStreamableDigest);
            if (!Arrays.equals(dataStreamableDigest.getMessageDigest(), dataDigest.getValue())) {
                throw new LogException("Cannot redo delete of binary resource: "
                        + delete.toAbsolutePath().toString() + ", checksum of data file is invalid");
            }

            // preform the redo - delete
            FileUtils.delete(delete);
        } catch(final IOException ioe) {
            throw new LogException("Cannot redo delete of binary resource: "
                    + delete.toAbsolutePath().toString(), ioe);
        }
    }

    @Override
    public void undo() throws LogException {
        // we need to copy the data from dataPath file to deletePath file

        final Path delete = getPath(deletePath);
        final Path data = getPath(dataPath);

        if (Files.exists(delete)) {
            throw new LogException("Cannot undo delete of binary resource: "
                    + delete.toAbsolutePath().toString() + ", already exists");
        }

        try {
            // ensure integrity of the data file first!
            if (!Files.exists(data)) {
                throw new LogException("Cannot undo delete of binary resource: "
                        + data.toAbsolutePath().toString() + ", missing data file");
            }

            // ensure integrity of data file by checksum
            final StreamableDigest dataStreamableDigest = dataDigest.getDigestType().newStreamableDigest();
            FileUtils.digest(data, dataStreamableDigest);
            if (!Arrays.equals(dataStreamableDigest.getMessageDigest(), dataDigest.getValue())) {
                throw new LogException("Cannot undo delete of binary resource: "
                        + data.toAbsolutePath().toString() + ", data file checksum is invalid");
            }

            // perform the undo - copy
            FileUtils.copy(data, delete);

            // ensure the integrity of the copy
            dataStreamableDigest.reset();
            FileUtils.digest(delete, dataStreamableDigest);
            if (!Arrays.equals(dataStreamableDigest.getMessageDigest(), dataDigest.getValue())) {
                throw new LogException("Cannot undo delete of binary resource: "
                        + delete.toAbsolutePath().toString() + " from "
                        + data.toAbsolutePath().toString() + ", checksum of new delete file is invalid");
            }
        } catch(final IOException ioe) {
            throw new LogException("Cannot undo delete of binary resource: "
                    + delete.toAbsolutePath().toString(), ioe);
        }
    }

    public Path getDeleteFile() {
        return getPath(deletePath);
    }

    @Override
    public String dump() {
        return super.dump() + " - delete binary [key=" + getPath(deletePath).toAbsolutePath().toString() + ", currentValue=" + getPath(dataPath).toAbsolutePath().toString() + ", newValue=null]";
    }
}
