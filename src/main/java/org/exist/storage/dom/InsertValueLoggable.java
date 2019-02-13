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

/**
 * Insert a value into a data page.
 *
 * @author wolf
 */
public class InsertValueLoggable extends AbstractLoggable {
    private DOMFile domDb;
    protected byte isOverflow;
    protected long pageNum;
    protected short tid;
    protected byte[] value;
    protected int offset;

    public InsertValueLoggable(final Txn transaction, final long pageNum, final boolean isOverflow, final short tid, final byte[] value, final int offset) {
        super(DOMFile.LOG_INSERT_RECORD, transaction.getId());
        this.pageNum = pageNum;
        this.isOverflow = (isOverflow ? (byte) 1 : 0);
        this.tid = tid;
        this.value = value;
        this.offset = offset;
    }

    public InsertValueLoggable(final DBBroker broker, final long transactionId) {
        super(DOMFile.LOG_INSERT_RECORD, transactionId);
        this.domDb = ((NativeBroker) broker).getDOMFile();
    }

    protected boolean isOverflow() {
        return isOverflow == 1;
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) pageNum);
        out.put(isOverflow);
        out.putInt(offset);
        out.putShort(tid);
        out.putShort((short) value.length);
        out.put(value);
    }

    @Override
    public void read(final ByteBuffer in) {
        pageNum = in.getInt();
        isOverflow = in.get();
        offset = in.getInt();
        tid = in.getShort();
        value = new byte[in.getShort()];
        in.get(value);
    }

    @Override
    public int getLogSize() {
        return 13 + value.length;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoInsertValue(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoInsertValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - inserted value; tid = " + tid + " in page " + pageNum +
                "; bytes: " + value.length + "; offset: " + offset;
    }
}
