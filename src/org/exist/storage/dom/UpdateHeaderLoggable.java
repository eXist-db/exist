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
public class UpdateHeaderLoggable extends AbstractLoggable {

    protected long pageNum;
    protected long nextPage;
    protected long prevPage;
    protected long oldNext;
    protected long oldPrev;
    private DOMFile domDb = null;
    
    /**
     * 
     * 
     * @param transaction 
     * @param prevPage 
     * @param pageNum 
     * @param nextPage 
     * @param oldPrev 
     * @param oldNext 
     */
    public UpdateHeaderLoggable(Txn transaction, long prevPage, long pageNum, long nextPage,
            long oldPrev, long oldNext) {
        super(DOMFile.LOG_UPDATE_HEADER, transaction.getId());
        this.prevPage = prevPage;
        this.pageNum = pageNum;
        this.nextPage = nextPage;
        this.oldPrev = oldPrev;
        this.oldNext = oldNext;
    }

    public UpdateHeaderLoggable(DBBroker broker, long transactId) {
        super(DOMFile.LOG_UPDATE_HEADER, transactId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.putInt((int) prevPage);
        out.putInt((int) pageNum);
        out.putInt((int) nextPage);
        out.putInt((int) oldPrev);
        out.putInt((int) oldNext);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        prevPage = in.getInt();
        pageNum = in.getInt();
        nextPage = in.getInt();
        oldPrev = in.getInt();
        oldNext = in.getInt();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 20;
    }

    public void redo() throws LogException {
        domDb.redoUpdateHeader(this);
    }
    
    public void undo() throws LogException {
        domDb.undoUpdateHeader(this);
    }
    
    public String dump() {
        return super.dump() + " - update header of page " + pageNum + ": prev = " + prevPage +
            "; next = " + nextPage + "; oldPrev = " + oldPrev + "; oldNext = " + oldNext;
    }
}
