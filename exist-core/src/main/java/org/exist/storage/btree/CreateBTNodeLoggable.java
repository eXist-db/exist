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
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

public class CreateBTNodeLoggable extends BTAbstractLoggable {
	
	protected byte status;
	protected long pageNum;
	protected long parentNum;
	
	public CreateBTNodeLoggable(Txn transaction, byte fileId, byte status, long pageNum, long parentNum) {
		super(BTree.LOG_CREATE_BNODE, fileId, transaction);
		this.pageNum = pageNum;
		this.parentNum = parentNum;
		this.status = status;
	}
	
	public CreateBTNodeLoggable(DBBroker broker, long transactionId) {
		super(BTree.LOG_CREATE_BNODE, broker, transactionId);
	}

	@Override
	public void redo() throws LogException {
		getStorage().redoCreateBTNode(this);
	}

	@Override
	public void write(ByteBuffer out) {
        super.write(out);
		out.put(status);
		out.putLong(pageNum);
		out.putLong(parentNum);
	}

	@Override
	public void read(ByteBuffer in) {
        super.read(in);
		status = in.get();
		pageNum = in.getLong();
		parentNum = in.getLong();
	}

	@Override
	public int getLogSize() {
		return super.getLogSize() + 17;
	}

	@Override
	public String dump() {
		return super.dump() + " - create btree node: " + pageNum;
	}
}
