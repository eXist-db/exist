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
public class SetParentLoggable extends BTAbstractLoggable {

    protected long pageNum;
    protected long parentNum;
    
    /**
     * @param transaction the database transaction
     * @param fileId the file id
     * @param pageNum the page number
     * @param parentNum the parent numbe
     */
    public SetParentLoggable(Txn transaction, byte fileId, long pageNum, long parentNum) {
        super(BTree.LOG_SET_PARENT, fileId, transaction);
        this.pageNum = pageNum;
        this.parentNum = parentNum;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public SetParentLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_SET_PARENT, broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putLong(pageNum);
        out.putLong(parentNum);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getLong();
        parentNum = in.getLong();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 16;
    }

    @Override
    public void redo() throws LogException {
        getStorage().redoSetParent(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - set parent for page: " + pageNum + ": " + parentNum;
    }
}
