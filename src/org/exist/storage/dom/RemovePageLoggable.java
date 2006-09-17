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
public class RemovePageLoggable extends AbstractLoggable {

    private DOMFile domDb;
    protected long pageNum;
    protected long prevPage;
    protected long nextPage;
    protected byte[] oldData;
    protected int oldLen;
    protected short oldTid;
    protected short oldRecCnt;
    
    /**
     * @param transaction 
     * @param pageNum 
     * @param prevPage 
     * @param nextPage 
     * @param oldData 
     * @param oldLen 
     * @param oldTid 
     * @param oldRecCnt 
     */
    public RemovePageLoggable(Txn transaction, long pageNum, long prevPage, long nextPage,
            byte[] oldData, int oldLen, short oldTid, short oldRecCnt) {
        super(DOMFile.LOG_REMOVE_PAGE, transaction.getId());
        this.pageNum = pageNum;
        this.prevPage = prevPage;
        this.nextPage = nextPage;
        this.oldData = oldData;
        this.oldLen = oldLen;
        this.oldTid = oldTid;
        this.oldRecCnt = oldRecCnt;
    }

    public RemovePageLoggable(DBBroker broker, long transactionId) {
        super(DOMFile.LOG_REMOVE_PAGE, transactionId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putInt((int) prevPage);
        out.putInt((int) nextPage);
        out.putShort(oldTid);
        out.putShort(oldRecCnt);
        out.putShort((short) oldLen);
        out.put(oldData, 0, oldLen);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        pageNum = in.getInt();
        prevPage = in.getInt();
        nextPage = in.getInt();
        oldTid = in.getShort();
        oldRecCnt = in.getShort();
        oldLen = in.getShort();
        oldData = new byte[domDb.getFileHeader().getWorkSize()];
        in.get(oldData, 0, oldLen);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 18 + oldLen;
    }

    public void redo() throws LogException {
        domDb.redoRemovePage(this);
    }
    
    public void undo() throws LogException {
        domDb.undoRemovePage(this);
    }
    
    public String dump() {
        return super.dump() + " - removed page " + pageNum;
    }
}
