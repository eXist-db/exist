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
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author alex
 */
public class CreateBinaryLoggable extends AbstractLoggable {
    private Path original;

    /**
     * Creates a new instance of RenameBinaryLoggable
     */
    public CreateBinaryLoggable(final DBBroker broker, final Txn txn, final Path original) {
        super(NativeBroker.LOG_CREATE_BINARY, txn.getId());
        this.original = original;
    }

    public CreateBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_CREATE_BINARY, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
        final String originalPath = original.toAbsolutePath().toString();
        final byte[] data = originalPath.getBytes(StandardCharsets.UTF_8);
        out.putInt(data.length);
        out.put(data);
    }

    @Override
    public void read(final ByteBuffer in) {
        final int size = in.getInt();
        final byte[] data = new byte[size];
        in.get(data);
        original = Paths.get(new String(data, StandardCharsets.UTF_8));
    }

    @Override
    public int getLogSize() {
        return 4 + original.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void redo() throws LogException {
        // TODO: do we need to redo?  The file was stored...
    }

    @Override
    public void undo() throws LogException {
        try {
            Files.delete(original);
        } catch(final IOException ioe) {
            throw new LogException("Cannot delete binary resource: " + original.toAbsolutePath().toString(), ioe);
        }
    }

    public Path getCreatedFile() {
        return original;
    }

    @Override
    public String dump() {
        return super.dump() + " - create binary " + original;
    }
}
