/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 *
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
package org.exist.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;

import javax.annotation.Nullable;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 *
 * Serialized binary format is as follows:
 *
 * [sourcePathLen, sourcePath, sourceDigestType, sourceDigest, destinationPathLen, destinationPath, destinationDigestType, destinationDigest?, dataPathLen, dataPath?]
 *
 * sourcePathLen:           2 bytes, unsigned short
 * sourcePath:              var length bytes, UTF-8 encoded java.lang.String
 * sourceDigestType:        1 byte
 * sourceDigest:            n-bytes, where n is deteremined by {@code sourceDigestType}
 * destinationPathLen:      2 bytes, unsigned short
 * destinationPath:         var length bytes, UTF-8 encoded java.lang.String
 * destinationDigestType:   1 byte
 * destinationDigest:       n-bytes, where n is deteremined by {@code destinationDigestType}
 * dataPathLen:             2 bytes, unsigned short
 * dataPath:                var length bytes, UTF-8 encoded java.lang.String
 */
public class RenameBinaryLoggable extends AbstractBinaryLoggable {
    private byte[] sourcePath;                      // the current path (i.e. the current value)
    private MessageDigest sourceDigest;

    private byte[] destinationPath;                 // the new path (i.e. the new value)
    @Nullable private MessageDigest destinationDigest;

    @Nullable private byte[] dataPath;              // path to a copy of the destinationPath data before the move (i.e. a copy of the current value) - needed for undo

    /**
     * Creates a new instance of RenameBinaryLoggable.
     *
     * @param broker The database broker.
     * @param txn The database transaction.
     * @param source the path before the move.
     * @param sourceDigest digest of the {@code source} content
     * @param destination the path after the move.
     * @param destinationDigest digest of the {@code destination} content, or null if the destination does not exist
     * @param data a copy of the existing destination data, or null if the destination does not exist
     */
    public RenameBinaryLoggable(final DBBroker broker, final Txn txn, final Path source,
            final MessageDigest sourceDigest, final Path destination, @Nullable final MessageDigest destinationDigest,
            @Nullable final Path data) {
        super(NativeBroker.LOG_RENAME_BINARY, txn.getId());
        this.sourcePath = getPathData(source);
        checkPathLen(getClass().getSimpleName(), "sourcePath", sourcePath);
        this.sourceDigest = sourceDigest;
        this.destinationPath = getPathData(destination);
        checkPathLen(getClass().getSimpleName(), "destinationPath", destinationPath);
        this.destinationDigest = destinationDigest;
        this.dataPath = getPathData(data);
        checkPathLen(getClass().getSimpleName(), "dataPath", dataPath);
    }

    /**
     * Creates a new instance of RenameBinaryLoggable.
     *
     * @param broker The database broker.
     * @param transactionId The database transaction id.
     */
    public RenameBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_RENAME_BINARY, transactionId);
    }


    @Override
    public int getLogSize() {
        return
                2 +
                sourcePath.length +
                1 +
                sourceDigest.getDigestType().getDigestLengthBytes() +
                2 +
                destinationPath.length +
                1 +
                (destinationDigest == null ? 0 : destinationDigest.getDigestType().getDigestLengthBytes()) +
                2 +
                (dataPath == null ? 0 : dataPath.length);
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putShort(asUnsignedShort(sourcePath.length));
        out.put(sourcePath);
        writeMessageDigest(out, sourceDigest);

        out.putShort(asUnsignedShort(destinationPath.length));
        out.put(destinationPath);
        writeMessageDigest(out, destinationDigest);

        if (dataPath == null) {
            out.putShort(asUnsignedShort(0));
        } else {
            out.putShort(asUnsignedShort(dataPath.length));
            out.put(dataPath);
        }
    }

    @Override
    public void read(final ByteBuffer in) {
        final int sourcePathLen = asSignedInt(in.getShort());
        this.sourcePath = new byte[sourcePathLen];
        in.get(sourcePath);
        this.sourceDigest = readMessageDigest(in);

        final int destinationPathLen = asSignedInt(in.getShort());
        this.destinationPath = new byte[destinationPathLen];
        in.get(destinationPath);
        this.destinationDigest = readMessageDigest(in);

        final int dataPathLen = asSignedInt(in.getShort());
        if (dataPathLen == 0) {
            this.dataPath = null;
        } else {
            this.dataPath = new byte[dataPathLen];
            in.get(dataPath);
        }
    }

    @Override
    public void redo() throws LogException {
        // we need to move the destination to a backup of data, and then move the source to the destination

        final Path source = getPath(sourcePath);
        final Path destination = getPath(destinationPath);
        final Path data = getPath(dataPath);

        if (data != null) {
            try {
                // ensure the integrity of the destination file
                final StreamableDigest destinationStreamableDigest = destinationDigest.getDigestType().newStreamableDigest();
                FileUtils.digest(destination, destinationStreamableDigest);
                if (!Arrays.equals(destinationStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot redo replace of binary resource: "
                            + destination.toAbsolutePath().toString() + " from "
                            + source.toAbsolutePath().toString() + ", digest of destination file is invalid");
                }

                // perform the pre-redo - backup move
                Files.move(destination, data, StandardCopyOption.ATOMIC_MOVE);

                // ensure the integrity of the move
                destinationStreamableDigest.reset();
                FileUtils.digest(data, destinationStreamableDigest);
                if (!Arrays.equals(destinationStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot redo replace of binary resource: "
                            + destination.toAbsolutePath().toString() + " from "
                            + source.toAbsolutePath().toString() + ", digest of new data file is invalid");
                }
            } catch (final IOException ioe) {
                throw new LogException("Cannot redo replace of binary resource: move destination="
                        + destination.toAbsolutePath().toString() + " to data=" + destination.toAbsolutePath().toString(), ioe);
            }
        }

        try {

            if (destinationDigest != null) {
                // ensure the integrity of the destination file
                final StreamableDigest destinationStreamableDigest = destinationDigest.getDigestType().newStreamableDigest();
                FileUtils.digest(destination, destinationStreamableDigest);
                if (!Arrays.equals(destinationStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot redo replace of binary resource: move source= "
                            + source.toAbsolutePath().toString() + " to destination= "
                            + destination.toAbsolutePath().toString() + ", digest of destination file is invalid");
                }
            }

            // ensure the integrity of the source file
            final StreamableDigest sourceStreamableDigest = sourceDigest.getDigestType().newStreamableDigest();
            FileUtils.digest(source, sourceStreamableDigest);
            if (!Arrays.equals(sourceStreamableDigest.getMessageDigest(), sourceDigest.getValue())) {
                throw new LogException("Cannot redo replace of binary resource: move source= "
                        + source.toAbsolutePath().toString() + " to destination= "
                        + destination.toAbsolutePath().toString() + ", digest of source file is invalid");
            }

            // perform the redo - move
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);

            // ensure the integrity of the move
            sourceStreamableDigest.reset();
            FileUtils.digest(destination, sourceStreamableDigest);
            if (!Arrays.equals(sourceStreamableDigest.getMessageDigest(), sourceDigest.getValue())) {
                throw new LogException("Cannot redo replace of binary resource: move source= "
                        + source.toAbsolutePath().toString() + " to destination= "
                        + destination.toAbsolutePath().toString() + ", digest of new destination file is invalid");
            }
        } catch (final IOException ioe) {
            throw new LogException("Cannot redo replace of binary resource: move source="
                    + source.toAbsolutePath().toString() + " to destination=" + destination.toAbsolutePath().toString(), ioe);
        }
    }

    @Override
    public void undo() throws LogException {
        // to undo a move of A to B, we have to:
        // 1) move B to A
        // 2) restore any previous value of B that we originally overwrote

        final Path source = getPath(sourcePath);
        final Path destination = getPath(destinationPath);
        final Path data = getPath(dataPath);

        try {
            StreamableDigest destinationStreamableDigest = null;
            if (destinationDigest != null) {
                // ensure the integrity of the destination file
                destinationStreamableDigest = destinationDigest.getDigestType().newStreamableDigest();
                FileUtils.digest(destination, destinationStreamableDigest);
                if (!Arrays.equals(destinationStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot undo replace of binary resource: move destination="
                            + destination.toAbsolutePath().toString()
                            + " to source=" + source.toAbsolutePath().toString()
                            + ", digest of destination file is invalid");
                }
            }

            // perform the undo - move
            Files.move(destination, source, StandardCopyOption.ATOMIC_MOVE);

            // ensure the integrity of the move
            if (destinationDigest != null) {
                destinationStreamableDigest.reset();
                FileUtils.digest(source, destinationStreamableDigest);
                if (!Arrays.equals(destinationStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot undo replace of binary resource: move destination="
                            + destination.toAbsolutePath().toString()
                            + " to source=" + source.toAbsolutePath().toString()
                            + ", digest of new source file is invalid");
                }
            } else {
                final DigestType digestType = DigestType.BLAKE_256;
                final StreamableDigest streamableDigest = digestType.newStreamableDigest();
                FileUtils.digest(destination, streamableDigest);
                final byte[] destinationDigest = Arrays.copyOf(streamableDigest.getMessageDigest(), digestType.getDigestLengthBytes());

                streamableDigest.reset();
                FileUtils.digest(source, streamableDigest);

                if (!Arrays.equals(destinationDigest, streamableDigest.getMessageDigest())) {
                    throw new LogException("Cannot undo replace of binary resource: move destination="
                            + destination.toAbsolutePath().toString()
                            + " to source=" + source.toAbsolutePath().toString()
                            + ", digest of new source file is invalid");
                }
            }
        } catch (final IOException ioe) {
            throw new LogException("Cannot undo replace of binary resource: move destination="
                    + destination.toAbsolutePath().toString() + " to source=" + source.toAbsolutePath().toString(), ioe);
        }

        if (data != null) {
            try {
                // ensure the integrity of the data file
                final StreamableDigest dataStreamableDigest = destinationDigest.getDigestType().newStreamableDigest();
                FileUtils.digest(data, dataStreamableDigest);
                if (!Arrays.equals(dataStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot undo replace of binary resource: "
                            + destination.toAbsolutePath().toString() + " from "
                            + source.toAbsolutePath().toString() + ", digest of data file is invalid");
                }

                // perform the pre-undo - backup move
                Files.move(data, destination, StandardCopyOption.ATOMIC_MOVE);

                // ensure the integrity of the move
                dataStreamableDigest.reset();
                FileUtils.digest(destination, dataStreamableDigest);
                if (!Arrays.equals(dataStreamableDigest.getMessageDigest(), destinationDigest.getValue())) {
                    throw new LogException("Cannot undo replace of binary resource: move destination="
                            + destination.toAbsolutePath().toString()
                            + " to source=" + source.toAbsolutePath().toString()
                            + ", digest of new destination file is invalid");
                }

            } catch (final IOException ioe) {
                throw new LogException("Cannot undo replace of binary resource: move data="
                        + data.toAbsolutePath().toString() + " to destination=" + destination.toAbsolutePath().toString(), ioe);
            }
        }
    }

    public Path getSourceFile() {
        return getPath(sourcePath);
    }

    @Override
    public String dump() {
        return super.dump() + " - rename binary [key=" + getPath(sourcePath).toAbsolutePath().toString() + ", currentValue=" + getPath(dataPath).toAbsolutePath().toString() + ", newValue=" + getPath(destinationPath).toAbsolutePath().toString() + "]";
    }
}
