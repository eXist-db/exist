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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author alex
 */
public class RenameBinaryLoggable extends AbstractLoggable {

    private final static Logger LOG = LogManager.getLogger(RenameBinaryLoggable.class);

    private Path original;
    private Path backup;

    /**
     * Creates a new instance of RenameBinaryLoggable
     */
    public RenameBinaryLoggable(final DBBroker broker, final Txn txn, final Path original, final Path backup) {
        super(NativeBroker.LOG_RENAME_BINARY, txn.getId());
        this.original = original;
        this.backup = backup;
        LOG.debug("Rename binary created " + original + " -> " + backup);
    }

    public RenameBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_RENAME_BINARY, transactionId);
        LOG.debug("Rename binary created ...");
    }

    @Override
    public void write(final ByteBuffer out) {
        final String originalPath = original.toAbsolutePath().toString();
        byte[] data = originalPath.getBytes(StandardCharsets.UTF_8);
        out.putInt(data.length);
        out.put(data);
        final String backupPath = backup.toAbsolutePath().toString();
        data = backupPath.getBytes(StandardCharsets.UTF_8);
        out.putInt(data.length);
        out.put(data);
    }

    @Override
    public void read(final ByteBuffer in) {
        int size = in.getInt();
        byte[] data = new byte[size];
        in.get(data);
        original = Paths.get(new String(data, StandardCharsets.UTF_8));
        size = in.getInt();
        data = new byte[size];
        in.get(data);
        backup = Paths.get(new String(data, StandardCharsets.UTF_8));
        LOG.debug("Rename binary read: " + original.toAbsolutePath().toString() + " -> " + backup.toAbsolutePath().toString());
    }

    @Override
    public int getLogSize() {
        return 8 + original.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8).length + backup.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void redo() throws LogException {
    }

    @Override
    public void undo() throws LogException {
        LOG.debug("Undo rename: " + original.toAbsolutePath().toString());
        try {
            Files.move(backup, original, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException ioe) {
            throw new LogException("Cannot move backup " + backup.toAbsolutePath().toString() + " to original file " + original.toAbsolutePath().toString());
        }
    }

    public Path getRenamedFile() {
        return original;
    }

    @Override
    public String dump() {
        return super.dump() + " - rename " + original + " to " + backup;
    }
}
