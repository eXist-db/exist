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
public class UpdateValueLoggable extends AbstractLoggable {

    protected DOMFile domDb;
    protected long pageNum;
    protected short tid;
    protected byte[] value;
    protected byte[] oldValue;
    protected int oldOffset;
    
    public UpdateValueLoggable(Txn transaction, long pageNum, short tid,
            byte[] value, byte[] oldValue, int oldOffset) {
        super(DOMFile.LOG_UPDATE_VALUE, transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.value = value;
        this.oldValue = oldValue;
        this.oldOffset = oldOffset;
    }

    public UpdateValueLoggable(DBBroker broker, long transactionId) {
        super(DOMFile.LOG_UPDATE_VALUE, transactionId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }

    public void write(ByteBuffer out) {
        out.putInt((int)pageNum);
        out.putShort(tid);
        out.putShort((short)value.length);
        out.put(value);
        out.putShort((short) oldOffset);
        out.put(oldValue, oldOffset, value.length);        
    }

    public void read(ByteBuffer in) {
        pageNum = in.getInt();
        tid = in.getShort();
        value = new byte[in.getShort()];
        in.get(value);
        oldOffset = in.getShort();
        oldValue = new byte[value.length];
        in.get(oldValue);
    }

    public int getLogSize() {
        return 10 + (value.length * 2);
    }
    
    public void redo() throws LogException {
        domDb.redoUpdateValue(this);
    }
    
    public void undo() throws LogException {
        domDb.undoUpdateValue(this);
    }
    
    public String dump() {
        return super.dump() + " - updated value; tid = " + ItemId.getId(tid) + " to page " + pageNum;
    }
}
