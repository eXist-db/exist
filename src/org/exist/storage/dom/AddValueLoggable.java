/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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

public class AddValueLoggable extends AbstractLoggable {

	protected DOMFile domDb;
	protected long pageNum;
	protected short tid;
	protected byte[] value;

    public AddValueLoggable() {
        super(DOMFile.LOG_ADD_VALUE, 0);
    }
    
    public AddValueLoggable(Txn transaction, long pageNum, short tid, byte[] value) {
        this(DOMFile.LOG_ADD_VALUE, transaction, pageNum, tid, value);
    }
    
	protected AddValueLoggable(byte id, Txn transaction, long pageNum, short tid, byte[] value) {
		super(id, transaction.getId());
		this.pageNum = pageNum;
		this.tid = tid;
		this.value = value;
	}
	
    public AddValueLoggable(DBBroker broker, long transactionId) {
        this(DOMFile.LOG_ADD_VALUE, broker, transactionId);
    }
    
	protected AddValueLoggable(byte id, DBBroker broker, long transactionId) {
		super(id, transactionId);
		this.domDb = ((NativeBroker)broker).getDOMFile();
	}
 
    public void clear(Txn transaction, long pageNum, short tid, byte[] value) {
        super.clear(transaction.getId());
        this.pageNum = pageNum;
        this.tid = tid;
        this.value = value;
    }
    
	public void write(ByteBuffer out) {
		out.putInt((int)pageNum);
		out.putShort(tid);
		out.putShort((short)value.length);
		out.put(value);
	}

	public void read(ByteBuffer in) {
		pageNum = in.getInt();
		tid = in.getShort();
		value = new byte[in.getShort()];
		in.get(value);
	}

	public int getLogSize() {
		return 8 + value.length;
	}

    public void redo() throws LogException {
        domDb.redoAddValue(this);
    }
    
    public void undo() throws LogException {
        domDb.undoAddValue(this);
    }
    
	public String dump() {
		return super.dump() + " - added value; tid = " + tid + " to page " + pageNum;
	}

}
