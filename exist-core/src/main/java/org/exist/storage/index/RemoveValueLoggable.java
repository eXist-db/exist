/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage.index;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class RemoveValueLoggable extends AbstractBFileLoggable {

    protected long page;
    protected short tid;
    protected byte[] oldData;
    protected int offset = 0;
    protected int len;

    /**
     * @param transaction the transaction
     * @param fileId the file id
     * @param page the page
     * @param tid the transaction id
     * @param oldData the old data
     * @param offset the offset in the old data
     * @param len the length of the old data
     */
    public RemoveValueLoggable(Txn transaction, byte fileId, long page, short tid, byte[] oldData, int offset, int len) {
        super(BFile.LOG_REMOVE_VALUE, fileId, transaction);
        this.page = page;
        this.tid = tid;
        this.oldData = oldData;
        this.offset = offset;
        this.len = len;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public RemoveValueLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) page);
        out.putShort(tid);
        out.putShort((short) len);
        out.put(oldData, offset, len);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        page = in.getInt();
        tid = in.getShort();
        len = in.getShort();
        oldData = new byte[len];
        in.get(oldData);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + len + 8;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoRemoveValue(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoRemoveValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - remove value with tid " + tid + " from page " + page;
    }

    public long getPage() {
        return page;
    }

    public byte[] getOldData() {
        return oldData;
    }

    public int getOffset() {
        return offset;
    }

    public int getLen() {
        return len;
    }
}
