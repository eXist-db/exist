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
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class AddMovedValueLoggable extends AddValueLoggable {

    protected long backLink;
    
    /**
     * @param transaction
     * @param pageNum
     * @param tid
     * @param value
     */
    public AddMovedValueLoggable(Txn transaction, long pageNum, short tid,
            byte[] value, long backLink) {
        super(DOMFile.LOG_ADD_MOVED_REC, transaction, pageNum, tid, value);
        this.backLink = backLink;
    }

    /**
     * @param broker
     * @param transactionId
     */
    public AddMovedValueLoggable(DBBroker broker, long transactionId) {
        super(DOMFile.LOG_ADD_MOVED_REC, broker, transactionId);
    }

    public void write(ByteBuffer out) {
        super.write(out);
        out.putLong(backLink);
    }
    
    public void read(ByteBuffer in) {
        super.read(in);
        backLink = in.getLong();
    }
    
    public int getLogSize() {
        return super.getLogSize() + 8;
    }
    
    public void redo() throws LogException {
        domDb.redoAddMovedValue(this);
    }
    
    public void undo() throws LogException {
        domDb.undoAddMovedValue(this);
    }
    
    public String dump() {
        return super.dump() + " - moved value; tid = " + tid + " to page " + pageNum + "; len = " + value.length;
    }
}
