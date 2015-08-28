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
 * @author wolf
 */
public class RemoveValueLoggable extends AbstractLoggable {

    private DOMFile domDb;
    protected long pageNum;
    protected short tid;
    protected int offset;
    protected byte[] oldData;
    protected boolean isOverflow;
    protected long backLink;

    public RemoveValueLoggable(final Txn transaction, final long pageNum, final short tid, final int offset,
                               final byte[] oldData, final boolean isOverflow, final long backLink) {
        super(DOMFile.LOG_REMOVE_VALUE, transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.offset = offset;
        this.oldData = oldData;
        this.isOverflow = isOverflow;
        this.backLink = backLink;
    }

    public RemoveValueLoggable(final DBBroker broker, final long transactionId) {
        super(DOMFile.LOG_REMOVE_VALUE, transactionId);
        this.domDb = ((NativeBroker) broker).getDOMFile();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.put((byte) (isOverflow ? 1 : 0));
        out.putInt((int) pageNum);
        out.putShort(tid);
        out.putShort((short) offset);
        out.putShort((short) oldData.length);
        out.put(oldData);
        if (ItemId.isRelocated(tid)) {
            out.putLong(backLink);
        }
    }

    @Override
    public void read(final ByteBuffer in) {
        isOverflow = in.get() == 1;
        pageNum = in.getInt();
        tid = in.getShort();
        offset = in.getShort();
        oldData = new byte[in.getShort()];
        in.get(oldData);
        if (ItemId.isRelocated(tid)) {
            backLink = in.getLong();
        }
    }

    @Override
    public int getLogSize() {
        return 11 + oldData.length + (ItemId.isRelocated(tid) ? 8 : 0);
    }

    @Override
    public void redo() throws LogException {
        domDb.redoRemoveValue(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoRemoveValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - removed value; tid = " + ItemId.getId(tid) + " from page " + pageNum + " at " + offset +
                "; len = " + oldData.length;
    }
}
