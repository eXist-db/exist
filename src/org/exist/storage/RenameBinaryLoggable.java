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

import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 *
 * Serialized binary format is as follows:
 *
 * [sourcePathLen, sourcePath, destinationPathLen, destinationPath, dataPathLen, dataPath]
 *
 * sourcePathLen:       2 bytes, unsigned short
 * sourcePath:          var length bytes, UTF-8 encoded java.lang.String
 * destinationPathLen:  2 bytes, unsigned short
 * destinationPath:     var length bytes, UTF-8 encoded java.lang.String
 * dataPathLen:         2 bytes, unsigned short
 * dataPath:            var length bytes, UTF-8 encoded java.lang.String
 */
public class RenameBinaryLoggable extends AbstractBinaryLoggable {
    private byte[] sourcePath;          // the current path (i.e. the current value)
    private byte[] destinationPath;     // the new path (i.e. the new value)
    @Nullable private byte[] dataPath;  // path to a copy of the destinationPath data before the move (i.e. a copy of the current value) - needed for undo

    /**
     * Creates a new instance of RenameBinaryLoggable.
     *
     * @param broker The database broker.
     * @param txn The database transaction.
     * @param source the path before the move.
     * @param destination the path after the move.
     * @param data a copy of the existing destination data, or null if the destination does not exist
     */
    public RenameBinaryLoggable(final DBBroker broker, final Txn txn, final Path source, final Path destination, @Nullable final Path data) {
        super(NativeBroker.LOG_RENAME_BINARY, txn.getId());
        this.sourcePath = getPathData(source);
        checkPathLen(getClass().getSimpleName(), "sourcePath", sourcePath);
        this.destinationPath = getPathData(destination);
        checkPathLen(getClass().getSimpleName(), "destinationPath", destinationPath);
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
                2 +
                destinationPath.length +
                2 +
                (dataPath == null ? 0 : dataPath.length);
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putShort(asUnsignedShort(sourcePath.length));
        out.put(sourcePath);
        out.putShort(asUnsignedShort(destinationPath.length));
        out.put(destinationPath);
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

        final int destinationPathLen = asSignedInt(in.getShort());
        this.destinationPath = new byte[destinationPathLen];
        in.get(destinationPath);

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
                Files.move(destination, data, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException ioe) {
                throw new LogException("Cannot redo replace of binary resource: move destination="
                        + destination.toAbsolutePath().toString() + " to data=" + destination.toAbsolutePath().toString(), ioe);
            }
        }

        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
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
            Files.move(destination, source, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException ioe) {
            throw new LogException("Cannot undo replace of binary resource: move destination="
                    + destination.toAbsolutePath().toString() + " to source=" + source.toAbsolutePath().toString(), ioe);
        }

        if (data != null) {
            try {
                Files.move(data, destination, StandardCopyOption.ATOMIC_MOVE);
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
