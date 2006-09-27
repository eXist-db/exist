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
public class AddLinkLoggable extends AbstractLoggable {

    protected long pageNum;
    protected short tid;
    protected long link;
    private DOMFile domDb = null;
    
    /**
     * @param transaction 
     * @param pageNum 
     * @param tid 
     * @param link 
     */
    public AddLinkLoggable(Txn transaction, long pageNum, short tid, long link) {
        super(DOMFile.LOG_ADD_LINK, transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.link = link;
    }

    public AddLinkLoggable(DBBroker broker, long transactId) {
        super(DOMFile.LOG_ADD_LINK, transactId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putShort(tid);
        out.putLong(link);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        pageNum = in.getInt();
        tid = in.getShort();
        link = in.getLong();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 14;
    }
    
    public void redo() throws LogException {
        domDb.redoAddLink(this);
    }
    
    public void undo() throws LogException {
        domDb.undoAddLink(this);
    }
    
    public String dump() {
        return super.dump() + " - created link on page: " + pageNum + " for tid: " + tid;
    }
}
