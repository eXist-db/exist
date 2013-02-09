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
import org.exist.util.ByteArray;
import org.exist.util.FixedByteArray;

/**
 * @author wolf
 *
 */
public class StoreValueLoggable extends AbstractBFileLoggable {

    protected long page;
    protected short tid;
    protected ByteArray value;

    /**
     * 
     * 
     * @param transaction 
     * @param fileId 
     * @param page 
     * @param tid 
     * @param value 
     */
    public StoreValueLoggable(Txn transaction, byte fileId, long page, short tid, ByteArray value) {
        super(BFile.LOG_STORE_VALUE, fileId, transaction);
        this.page = page;
        this.tid = tid;
        this.value = value;
    }

    public StoreValueLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) page);
        out.putShort(tid);
        out.putShort((short) value.size());
        value.copyTo(out);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        page = in.getInt();
        tid = in.getShort();
        final int len = in.getShort();
        final byte[] data = new byte[len];
        in.get(data);
        value = new FixedByteArray(data, 0, len);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    @Override
    public int getLogSize() {
        return super.getLogSize() + 8 + value.size();
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoStoreValue(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoStoreValue(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - stored value with tid " + tid + " on page " + page;
    }
}
