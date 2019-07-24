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
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * Log object representing the insertion of a new btree key.
 * 
 * @author wolf
 */
public class InsertValueLoggable extends BTAbstractLoggable {

    protected long pageNum;
    protected int idx;
    protected long pointer;
    protected int pointerIdx;
    protected Value key;
    
    public InsertValueLoggable(Txn transaction, byte fileId, long pageNum, int idx, Value key, int pointerIdx, long pointer) {
        super(BTree.LOG_INSERT_VALUE, fileId, transaction);
        this.pageNum = pageNum;
        this.idx = idx;
        this.key = key;
        this.pointerIdx = pointerIdx;
        this.pointer = pointer;
    }
    
    public InsertValueLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_INSERT_VALUE, broker, transactionId);
    }
    
    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) pageNum);
        out.putShort((short) idx);
        out.putShort((short) pointerIdx);
        out.putLong(pointer);
        out.putShort((short) key.getLength());
        out.put(key.data(), key.start(), key.getLength());
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getInt();
        idx = in.getShort();
        pointerIdx = in.getShort();
        pointer = in.getLong();
        byte[] data = new byte[in.getShort()];
        in.get(data);
        key = new Value(data);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 18 + key.getLength();
    }

    @Override
    public void redo() throws LogException {
        getStorage().redoInsertValue(this);
    }

    @Override
    public void undo() throws LogException {
        getStorage().undoInsertValue(this);
    }

    @Override
	public String dump() {
		return super.dump() + " - insert btree key on page: " + pageNum + ": " + Paged.hexDump(key.data());
	}
}
