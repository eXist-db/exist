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

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 *
 * Serialized binary format is as follows:
 *
 * [walDataPathLen, walDataPath, replacePathLen, replacePath, dataPathLen, dataPath]
 *
 * walDataPathLen:  2 bytes, unsigned short
 * walDataPath:     var length bytes, UTF-8 encoded java.lang.String
 * replacePathLen:  2 bytes, unsigned short
 * replacePath:     var length bytes, UTF-8 encoded java.lang.String
 * dataPathLen:     2 bytes, unsigned short
 * dataPath:        var length bytes, UTF-8 encoded java.lang.String
 */
public class ReplaceBinaryLoggable extends AbstractBinaryLoggable {
    private byte[] walDataPath;  // the data to use for the replacement (i.e. the new value)
    private byte[] replacePath;  // the file to be replaced
    private byte[] dataPath;     // the data before the file was replaced (i.e. the current value)

    /**
     * Creates a new instance of ReplaceBinaryLoggable.
     *
     * @param broker The database broker.
     * @param txn The database transaction.
     * @param walData A copy of the data that was stored for {@code replace} file before it was actually replaced (i.e. the new value).
     * @param replace The file that is to be replaced in the database.
     * @param data A copy of the data before the file was replaced (i.e. the current value).
     */
    public ReplaceBinaryLoggable(final DBBroker broker, final Txn txn, final Path walData, final Path replace, final Path data) {
        super(NativeBroker.LOG_REPLACE_BINARY, txn.getId());
        this.walDataPath = getPathData(walData);
        checkPathLen(getClass().getSimpleName(), "walDataPath", walDataPath);
        this.replacePath = getPathData(replace);
        checkPathLen(getClass().getSimpleName(), "replacePath", replacePath);
        this.dataPath = getPathData(data);
        checkPathLen(getClass().getSimpleName(), "dataPath", dataPath);
    }

    /**
     * Creates a new instance of ReplaceBinaryLoggable.
     *
     * @param broker The database broker.
     * @param transactionId The database transaction id.
     */
    public ReplaceBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_REPLACE_BINARY, transactionId);
    }

    @Override
    public int getLogSize() {
        return
                2 +
                walDataPath.length +
                2 +
                replacePath.length +
                2 +
                dataPath.length;
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putShort(asUnsignedShort(walDataPath.length));
        out.put(walDataPath);
        out.putShort(asUnsignedShort(replacePath.length));
        out.put(replacePath);
        out.putShort(asUnsignedShort(dataPath.length));
        out.put(dataPath);
    }

    @Override
    public void read(final ByteBuffer in) {
        final int walDataPathLen = asSignedInt(in.getShort());
        this.walDataPath = new byte[walDataPathLen];
        in.get(walDataPath);

        final int replacePathLen = asSignedInt(in.getShort());
        this.replacePath = new byte[replacePathLen];
        in.get(replacePath);

        final int dataPathLen = asSignedInt(in.getShort());
        this.dataPath = new byte[dataPathLen];
        in.get(dataPath);
    }

    @Override
    public void redo() throws LogException {
        //we need to re-copy the data from the walDataPath file to the replacePath file

        final Path walData = getPath(walDataPath);
        final Path replace = getPath(replacePath);

        if (!Files.exists(walData)) {
            throw new LogException("Cannot redo replace of binary resource: "
                    + replace.toAbsolutePath().toString() + ", missing write ahead data: "
                    + walData.toAbsolutePath().toString());
        }

        try {
            Files.copy(walData, replace, StandardCopyOption.REPLACE_EXISTING);
        } catch(final IOException ioe) {
            throw new LogException("Cannot redo replace of binary resource: "
                    + replace.toAbsolutePath().toString(), ioe);
        }
    }

    @Override
    public void undo() throws LogException {
        // we need to copy the data from dataPath file to replacePath file

        final Path replace = getPath(replacePath);
        final Path data = getPath(dataPath);

        // ensure integrity of data file first!
        if (!Files.exists(data)) {
            throw new LogException("Cannot undo replace of binary resource: "
                    + data.toAbsolutePath().toString() + ", missing data file");
        }

        try {
            Files.copy(data, replace, StandardCopyOption.REPLACE_EXISTING);
        } catch(final IOException ioe) {
            throw new LogException("Cannot undo replace of binary resource: "
                    + replace.toAbsolutePath().toString(), ioe);
        }
    }

    public Path getReplaceFile() {
        return getPath(replacePath);
    }

    @Override
    public String dump() {
        return super.dump() + " - replace binary [key=" + getPath(replacePath).toAbsolutePath().toString() + ", currentValue=" + getPath(dataPath).toAbsolutePath().toString() + ", newValue=" + getPath(walDataPath).toAbsolutePath().toString() + "]";
    }
}
