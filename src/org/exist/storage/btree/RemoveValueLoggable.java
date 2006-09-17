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
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class RemoveValueLoggable extends BTAbstractLoggable {

    protected long pageNum;
    protected int idx;
    protected Value oldValue;
    protected long oldPointer;
    
    /**
     * @param fileId 
     * @param pageNum 
     * @param idx 
     * @param oldValue 
     * @param oldPointer 
     * @param transaction 
     */
    public RemoveValueLoggable(Txn transaction, byte fileId, long pageNum, int idx, Value oldValue, long oldPointer) {
        super(BTree.LOG_REMOVE_VALUE, fileId, transaction);
        this.pageNum = pageNum;
        this.idx = idx;
        this.oldValue = oldValue;
        this.oldPointer = oldPointer;
    }

    /**
     * @param broker 
     * @param transactionId 
     */
    public RemoveValueLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_REMOVE_VALUE, broker, transactionId);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) pageNum);
        out.putShort((short) idx);
        out.putShort((short) oldValue.getLength());
        out.put(oldValue.data(), oldValue.start(), oldValue.getLength());
        out.putLong(oldPointer);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getInt();
        idx = in.getShort();
        int l = in.getShort();
        byte[] data = new byte[l];
        in.get(data);
        oldValue = new Value(data);
        oldPointer = in.getLong();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return super.getLogSize() + 16 + oldValue.getLength();
    }

    public void redo() throws LogException {
        getStorage().redoRemoveValue(this);
    }
    
    public void undo() throws LogException {
        getStorage().undoRemoveValue(this);
    }
    
    public String dump() {
        return super.dump() + " - removed btree key on page: " + pageNum;
    }
}
