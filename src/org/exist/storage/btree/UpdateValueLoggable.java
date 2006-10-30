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
public class UpdateValueLoggable extends BTAbstractLoggable {
    
    protected long pageNum;
    protected int idx;
    protected Value key;
    protected long pointer;
    protected long oldPointer;
    
    /**
     * @param fileId 
     * @param pageNum 
     * @param idx 
     * @param key 
     * @param pointer 
     * @param oldPointer 
     * @param transaction 
     */
    public UpdateValueLoggable(Txn transaction, byte fileId, long pageNum, int idx, Value key, long pointer, long oldPointer) {
        super(BTree.LOG_UPDATE_VALUE, fileId, transaction);
        this.pageNum = pageNum;
        this.idx = idx;
        this.key = key;
        this.pointer = pointer;
        this.oldPointer = oldPointer;
    }

    /**
     * @param broker 
     * @param transactionId 
     */
    public UpdateValueLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_UPDATE_VALUE, broker, transactionId);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) pageNum);
        out.putShort((short) idx);
        out.putLong(pointer);
        out.putLong(oldPointer);
        out.putShort((short) key.getLength());
        out.put(key.data(), key.start(), key.getLength());
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getInt();
        idx = in.getShort();
        pointer = in.getLong();
        oldPointer = in.getLong();
        byte[] data = new byte[in.getShort()];
        in.get(data);
        key = new Value(data);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return super.getLogSize() + 24 + key.getLength();
    }
    
    public void redo() throws LogException {
        getStorage().redoUpdateValue(this);
    }
    
    public void undo() throws LogException {
        getStorage().undoUpdateValue(this);
    }
    
    public String dump() {
        return super.dump() + " - update btree key on page: " + pageNum;
    }
}
