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
import org.exist.storage.NativeBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

public class AddValueLoggable extends AbstractLoggable {
    protected DOMFile domDb;
    protected long pageNum;
    protected short tid;
    protected byte[] value;
    protected boolean isOverflow = false;

    public AddValueLoggable() {
        super(DOMFile.LOG_ADD_VALUE, 0);
    }

    protected AddValueLoggable(final byte id, final Txn transaction, final long pageNum, final short tid, final byte[] value, final boolean isOverflow) {
        super(id, transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.value = value;
        this.isOverflow = isOverflow;
    }

    public AddValueLoggable(final DBBroker broker, final long transactionId) {
        this(DOMFile.LOG_ADD_VALUE, broker, transactionId);
    }

    protected AddValueLoggable(final byte id, final DBBroker broker, final long transactionId) {
        super(id, transactionId);
        this.domDb = ((NativeBroker) broker).getDOMFile();
    }

    public void clear(final Txn transaction, final long pageNum, final short tid, final byte[] value, final boolean isOverflow) {
        super.clear(transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.value = value;
        this.isOverflow = isOverflow;
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putShort(tid);
        if (isOverflow) {
            out.putShort((short)0);
        } else {
            out.putShort((short) value.length);
        }
        out.put(value);
    }

    @Override
    public void read(final ByteBuffer in) {
        pageNum = in.getInt();
        tid = in.getShort();
        final short vlen = in.getShort();
        isOverflow = vlen == 0;
        value = isOverflow ? new byte[8] : new byte[vlen];
        in.get(value);
    }

    @Override
    public int getLogSize() {
        return 8 + value.length;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoAddValue(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoAddValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - added value; tid = " + tid + " to page " + pageNum;
    }
}
