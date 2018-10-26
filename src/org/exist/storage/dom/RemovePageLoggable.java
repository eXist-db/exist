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
 *
 */
public class RemovePageLoggable extends AbstractLoggable {
    private DOMFile domDb;
    protected long pageNum;
    protected long prevPage;
    protected long nextPage;
    protected byte[] oldData;
    protected int oldLen;
    protected short oldTid;
    protected short oldRecCnt;

    public RemovePageLoggable(final Txn transaction, final long pageNum, final long prevPage, final long nextPage,
                              final byte[] oldData, final int oldLen, final short oldTid, final short oldRecCnt) {
        super(DOMFile.LOG_REMOVE_PAGE, transaction.getId());
        this.pageNum = pageNum;
        this.prevPage = prevPage;
        this.nextPage = nextPage;
        this.oldData = oldData;
        this.oldLen = oldLen;
        this.oldTid = oldTid;
        this.oldRecCnt = oldRecCnt;
    }

    public RemovePageLoggable(final DBBroker broker, final long transactionId) {
        super(DOMFile.LOG_REMOVE_PAGE, transactionId);
        this.domDb = broker == null ? null : ((NativeBroker) broker).getDOMFile();
    }
    
    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putInt((int) prevPage);
        out.putInt((int) nextPage);
        out.putShort(oldTid);
        out.putShort(oldRecCnt);
        out.putShort((short) oldLen);
        out.put(oldData, 0, oldLen);
    }

    @Override
    public void read(final ByteBuffer in) {
        pageNum = in.getInt();
        prevPage = in.getInt();
        nextPage = in.getInt();
        oldTid = in.getShort();
        oldRecCnt = in.getShort();
        oldLen = in.getShort();
        oldData = new byte[oldLen];
        in.get(oldData, 0, oldLen);
    }

    @Override
    public int getLogSize() {
        return 18 + oldLen;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoRemovePage(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoRemovePage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - removed page " + pageNum;
    }

    public long getPageNum() {
        return pageNum;
    }

    public byte[] getOldData() {
        return oldData;
    }

    public int getOldLen() {
        return oldLen;
    }
}
