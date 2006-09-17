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
import org.exist.storage.btree.Value;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class WriteOverflowPageLoggable extends AbstractLoggable {

	protected long pageNum;
	protected long nextPage;
	protected Value value;
	private DOMFile domDb = null;
	
	/**
     * 
     * 
     * @param transaction 
     * @param pageNum 
     * @param nextPage 
     * @param value 
     */
	public WriteOverflowPageLoggable(Txn transaction, long pageNum, long nextPage, Value value) {
		super(DOMFile.LOG_WRITE_OVERFLOW, transaction.getId());
		this.pageNum = pageNum;
		this.nextPage = nextPage;
		this.value = value;
	}

	public WriteOverflowPageLoggable(DBBroker broker, long transactId) {
		super(DOMFile.LOG_WRITE_OVERFLOW, transactId);
		this.domDb = ((NativeBroker)broker).getDOMFile();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
	 */
	public void write(ByteBuffer out) {
		out.putInt((int) pageNum);
		out.putInt((int) nextPage);
		out.putShort((short) value.getLength());
		out.put(value.data(), value.start(), value.getLength());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
	 */
	public void read(ByteBuffer in) {
		pageNum = in.getInt();
		nextPage = in.getInt();
		int len = in.getShort();
		byte[] data = new byte[len];
		in.get(data);
		value = new Value(data);
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.log.Loggable#getLogSize()
	 */
	public int getLogSize() {
		return 10 + value.getLength();
	}

	public void redo() throws LogException {
		domDb.redoWriteOverflow(this);
	}
	
	public void undo() throws LogException {
		domDb.undoWriteOverflow(this);
	}
	
	public String dump() {
		return super.dump() + " - writing overflow page " + pageNum + "; next: " + nextPage;
	}
}