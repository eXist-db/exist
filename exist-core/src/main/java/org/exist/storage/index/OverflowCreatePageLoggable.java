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
public class OverflowCreatePageLoggable extends AbstractBFileLoggable {

    protected long newPage;
    protected long prevPage;

    /**
     * @param transaction the database transaction
     * @param fileId the file id
     * @param newPage the new page number
     * @param prevPage the pevious page number
     */
    public OverflowCreatePageLoggable(Txn transaction, byte fileId, long newPage, long prevPage) {
        super(BFile.LOG_OVERFLOW_CREATE_PAGE, fileId, transaction);
        this.newPage = newPage;
        this.prevPage = prevPage;
    }

    public OverflowCreatePageLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) newPage);
        out.putInt((int) prevPage);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        newPage = in.getInt();
        prevPage = in.getInt();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 8;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoCreateOverflowPage(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoCreateOverflowPage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - create new overflow page " + newPage;
    }
}
