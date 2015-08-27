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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author alex
 */
public class UpdateBinaryLoggable extends AbstractLoggable {

    private final static Logger LOG = LogManager.getLogger(UpdateBinaryLoggable.class);

    private File original;
    private File backup;

    /**
     * Creates a new instance of RenameBinaryLoggable
     */
    public UpdateBinaryLoggable(final DBBroker broker, final Txn txn, File original, final File backup) {
        super(NativeBroker.LOG_UPDATE_BINARY, txn.getId());
        this.original = original;
        this.backup = backup;
    }

    public UpdateBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_UPDATE_BINARY, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
        final String originalPath = original.getAbsolutePath();
        byte[] data = originalPath.getBytes();
        out.putInt(data.length);
        out.put(data);
        final String backupPath = backup.getAbsolutePath();
        data = backupPath.getBytes();
        out.putInt(data.length);
        out.put(data);
    }

    @Override
    public void read(final ByteBuffer in) {
        int size = in.getInt();
        byte[] data = new byte[size];
        in.get(data);
        original = new File(new String(data));
        size = in.getInt();
        data = new byte[size];
        in.get(data);
        backup = new File(new String(data));
    }

    @Override
    public int getLogSize() {
        return 8 + original.getAbsolutePath().getBytes().length + backup.getAbsolutePath().getBytes().length;
    }

    @Override
    public void redo() throws LogException {
        // TODO: is there something to do?  The file has been written
    }

    @Override
    public void undo() throws LogException {
        try(final FileInputStream is = new FileInputStream(backup);
                final FileOutputStream os = new FileOutputStream(original)) {
            final byte[] buffer = new byte[4096];
            int len = -1;
            while ((len = is.read(buffer)) > -1) {
                os.write(buffer, 0, len);
            }
        } catch (final IOException ioe) {
            LOG.error(ioe);
        }
    }

    @Override
    public String dump() {
        return super.dump() + " - update " + original + " to " + backup;
    }
}
