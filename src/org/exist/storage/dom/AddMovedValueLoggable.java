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
package org.exist.storage.dom;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 */
public class AddMovedValueLoggable extends AddValueLoggable {
    protected long backLink;

    public AddMovedValueLoggable(final Txn transaction, final long pageNum, final short tid, final byte[] value,
                                 final long backLink) {
        super(DOMFile.LOG_ADD_MOVED_REC, transaction, pageNum, tid, value, false);
        this.backLink = backLink;
    }

    public AddMovedValueLoggable(final DBBroker broker, final long transactionId) {
        super(DOMFile.LOG_ADD_MOVED_REC, broker, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
        super.write(out);
        out.putLong(backLink);
    }

    @Override
    public void read(final ByteBuffer in) {
        super.read(in);
        backLink = in.getLong();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 8;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoAddMovedValue(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoAddMovedValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - moved value; tid = " + tid + " to page " + pageNum + "; len = " + value.length;
    }
}
