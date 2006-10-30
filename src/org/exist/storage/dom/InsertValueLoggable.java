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

    protected DOMFile domDb;
    protected byte isOverflow;
    protected long pageNum;
    protected short tid;
    protected byte[] value;
    protected int offset;
    
    /**
     * @param transaction 
     * @param pageNum 
     * @param isOverflow 
     * @param tid 
     * @param value 
     * @param offset 
     */
    public InsertValueLoggable(Txn transaction, long pageNum, boolean isOverflow, short tid, byte[] value, int offset) {
        super(DOMFile.LOG_INSERT_RECORD, transaction.getId());
        this.pageNum = pageNum;
        this.isOverflow = (isOverflow ? (byte)1 : 0);
        this.tid = tid;
        this.value = value;
        this.offset = offset;
    }

    public InsertValueLoggable(DBBroker broker, long transactionId) {
        super(DOMFile.LOG_INSERT_RECORD, transactionId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    protected boolean isOverflow() {
        return isOverflow == 1;
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.putInt((int)pageNum);
        out.put(isOverflow);
        out.putInt(offset);
        out.putShort(tid);
        out.putShort((short)value.length);
        out.put(value);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        pageNum = in.getInt();
        isOverflow = in.get();
        offset = in.getInt();
        tid = in.getShort();
        value = new byte[in.getShort()];
        in.get(value);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 13 + value.length;
    }

    public void redo() throws LogException {
        domDb.redoInsertValue(this);
    }
    
    public void undo() throws LogException {
        domDb.undoInsertValue(this);
    }
    
    public String dump() {
        return super.dump() + " - inserted value; tid = " + tid + " in page " + pageNum + 
            "; bytes: " + value.length + "; offset: " + offset;
    }
}
