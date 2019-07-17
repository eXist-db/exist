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
public class OverflowRemoveLoggable extends AbstractBFileLoggable {

    protected byte status;
    protected long pageNum;
    protected byte[] data;
    protected int length;
    protected long nextInChain;
    
    /**
     *
     * @param fileId the file id
     * @param transaction the database transaction
     * @param status the status
     * @param pageNum the page number
     * @param data the data
     * @param length the length of the data
     * @param nextInChain the next in chain
     */
    public OverflowRemoveLoggable(byte fileId, Txn transaction, byte status, long pageNum, byte[] data, 
            int length, long nextInChain) {
        super(BFile.LOG_OVERFLOW_REMOVE, fileId, transaction);
        this.status = status;
        this.pageNum = pageNum;
        this.data = data;
        this.length = length;
        this.nextInChain = nextInChain;
    }

    /**
     * @param broker the database broker
     * @param transactionId thr transaction id
     */
    public OverflowRemoveLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.put(status);
        out.putInt((int) pageNum);
        out.putInt((int) nextInChain);
        out.putInt(length);
        out.put(data, 0, length);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        status = in.get();
        pageNum = in.getInt();
        nextInChain = in.getInt();
        length = in.getInt();
        data = new byte[length];
        in.get(data);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 13 + length;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoRemoveOverflow(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoRemoveOverflow(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - remove overflow page " + pageNum;
    }
}
