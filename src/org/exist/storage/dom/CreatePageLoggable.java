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
 */
public class CreatePageLoggable extends AbstractLoggable {
 
	protected long prevPage;
	protected long newPage;
    protected long nextPage;
    protected short nextTID;
	private DOMFile domDb = null;

    public CreatePageLoggable(Txn transaction, long prevPage, long newPage, long nextPage) {
        this(transaction, prevPage, newPage, nextPage, (short)-1);
    }
    
	public CreatePageLoggable(Txn transaction, long prevPage, long newPage, long nextPage, short nextTID) {
		super(DOMFile.LOG_CREATE_PAGE, transaction.getId());
		this.prevPage = prevPage;
		this.newPage = newPage;
        this.nextPage = nextPage;
        this.nextTID = nextTID;
	}
	
	public CreatePageLoggable(DBBroker broker, long transactId) {
		super(DOMFile.LOG_CREATE_PAGE, transactId);
		this.domDb = ((NativeBroker)broker).getDOMFile();
	}
	
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
		out.putInt((int)prevPage);
		out.putInt((int)newPage);
        out.putInt((int)nextPage);
        out.putShort(nextTID);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
		prevPage = in.getInt();
		newPage = in.getInt();
        nextPage = in.getInt();
        nextTID = in.getShort();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 14;
    }
	
    public void redo() throws LogException {
        domDb.redoCreatePage(this);
    }
    
    public void undo() throws LogException {
        domDb.undoCreatePage(this);
    }
    
	public String dump() {
		return super.dump() + " - new page created: " + newPage + "; prev. page: " + prevPage + "; next page: " +
            nextPage;
	}
}