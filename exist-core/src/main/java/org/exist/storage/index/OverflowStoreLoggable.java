/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
public class OverflowStoreLoggable extends AbstractBFileLoggable {

    protected long pageNum;
    protected long prevPage;
    protected byte[] data;
    protected int startOffset;
    protected int size;

    /**
     *
     * @param fileId the file id
     * @param transaction the database transaction
     * @param page  the page
     * @param prevPage the previous page
     * @param chunk the chunk
     * @param startOffset the start offset in the chunk
     * @param chunkSize the size of the chunk
     */
    public OverflowStoreLoggable(byte fileId, Txn transaction, long page, long prevPage, 
            byte[] chunk, int startOffset, int chunkSize) {
        super(BFile.LOG_OVERFLOW_STORE, fileId, transaction);
        this.pageNum = page;
        this.data = chunk;
        this.size = chunkSize;
        this.prevPage = prevPage;
        this.startOffset = startOffset;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public OverflowStoreLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) pageNum);
        out.putInt((int) prevPage);
        out.putInt(size);
        out.put(data, startOffset, size);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getInt();
        prevPage = in.getInt();
        size = in.getInt();
        data = new byte[size];
        in.get(data);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 12 + size;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoStoreOverflow(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - stored overflow page " + pageNum;
    }
}
