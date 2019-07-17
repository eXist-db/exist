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
package org.exist.storage.index;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class RemoveEmptyPageLoggable extends AbstractBFileLoggable {

    protected long page;
    
    /**
     * @param fileId the file id
     * @param transaction the database transaction
     * @param pageNum the page number
     */
    public RemoveEmptyPageLoggable(Txn transaction, byte fileId, long pageNum) {
        super(BFile.LOG_REMOVE_PAGE, fileId, transaction);
        this.page = pageNum;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public RemoveEmptyPageLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) page);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        page = in.getInt();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 4;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoRemovePage(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoRemovePage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - remove page " + page;
    }
}
