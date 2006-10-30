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
 * @author wolf
 *
 */
public class RemoveValueLoggable extends AbstractLoggable {

    private DOMFile domDb;
    protected long pageNum;
    protected short tid;
    protected int offset;
    protected byte[] oldData;
    protected boolean isOverflow;
    protected long backLink;
    
    /**
     * @param transaction 
     * @param pageNum 
     * @param tid 
     * @param offset 
     * @param oldData 
     * @param isOverflow 
     * @param backLink 
     */
    public RemoveValueLoggable(Txn transaction, long pageNum, short tid, int offset, byte[] oldData, 
            boolean isOverflow, long backLink) {
        super(DOMFile.LOG_REMOVE_VALUE, transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.offset = offset;
        this.oldData = oldData;
        this.isOverflow = isOverflow;
        this.backLink = backLink;
    }

    public RemoveValueLoggable(DBBroker broker, long transactionId) {
        super(DOMFile.LOG_REMOVE_VALUE, transactionId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.put((byte)(isOverflow ? 1 : 0));
        out.putInt((int)pageNum);
        out.putShort(tid);
        out.putShort((short) offset);
        out.putShort((short) oldData.length);
        out.put(oldData);
        if (ItemId.isRelocated(tid))
            out.putLong(backLink);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        isOverflow = in.get() == 1;
        pageNum = in.getInt();
        tid = in.getShort();
        offset = in.getShort();
        oldData = new byte[in.getShort()];
        in.get(oldData);
        if (ItemId.isRelocated(tid))
            backLink = in.getLong();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 11 + oldData.length + (ItemId.isRelocated(tid) ? 8 : 0);
    }

    public void redo() throws LogException {
        domDb.redoRemoveValue(this);
    }
    
    public void undo() throws LogException {
        domDb.undoRemoveValue(this);
    }
    
    public String dump() {
        return super.dump() + " - removed value; tid = " + ItemId.getId(tid) + " from page " + pageNum + " at " + offset +
            "; len = " + oldData.length;
    }
}
