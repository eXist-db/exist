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
 * @author wolf
 *
 */
public class UpdatePageLoggable extends BTAbstractLoggable {

    protected Value values[];
    protected long pointers[];
    protected long pageNum;
    
    /**
     * @param type
     * @param transaction
     */
    public UpdatePageLoggable(Txn transaction, byte fileId, long pageNum, Value values[], long pointers[]) {
        super(BTree.LOG_UPDATE_PAGE, fileId, transaction);
        this.pageNum = pageNum;
        this.values = values;
        this.pointers = pointers;
    }

    /**
     * @param type
     * @param broker
     * @param transactionId
     */
    public UpdatePageLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_UPDATE_PAGE, broker, transactionId);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        super.write(out);
        out.putLong(pageNum);
        out.putShort((short) values.length);
        for (int i = 0; i < values.length; i++) {
            out.putShort((short) values[i].getLength());
            out.put(values[i].data(), values[i].start(), values[i].getLength());
        }
        
        out.putShort((short) pointers.length);
        for (int i = 0; i < pointers.length; i++) {
            out.putLong(pointers[i]);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getLong();
        int len = in.getShort();
        values = new Value[len];
        int dataLen;
        byte[] data;
        for (int i = 0; i < len; i++) {
            dataLen = in.getShort();
            data = new byte[dataLen];
            in.get(data);
            values[i] = new Value(data);
        }
        
        len = in.getShort();
        pointers = new long[len];
        for (int i = 0; i < len; i++) {
            pointers[i] = in.getLong();
        }
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        int len = super.getLogSize() + 12 + (pointers.length * 8);
        for (int i = 0; i < values.length; i++)
            len += values[i].getLength() + 2;
        return len;
    }

    public void redo() throws LogException {
        getStorage().redoUpdatePage(this);
    }
    
    public String dump() {
        return super.dump() + " - updated page " + pageNum;
    }
}
