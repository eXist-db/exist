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
public class OverflowModifiedLoggable extends AbstractBFileLoggable {

    protected long pageNum;
    protected long lastInChain;
    protected int length;
    protected int oldLength;

    /**
     *
     * @param fileId the file id
     * @param transaction the database transaction
     * @param pageNum the page number
     * @param length the length
     * @param oldLength the old length
     * @param lastInChain the last in chain
     */
    public OverflowModifiedLoggable(byte fileId, Txn transaction, long pageNum, int length, 
            int oldLength, long lastInChain) {
        super(BFile.LOG_OVERFLOW_MODIFIED, fileId, transaction);
        this.pageNum = pageNum;
        this.length = length;
        this.oldLength = oldLength;
        this.lastInChain = lastInChain;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public OverflowModifiedLoggable(DBBroker broker, long transactionId) {
        super(broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putInt((int) pageNum);
        out.putInt(length);
        out.putInt(oldLength);
        out.putInt((int) lastInChain);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getInt();
        length = in.getInt();
        oldLength = in.getInt();
        lastInChain = in.getInt();
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 16;
    }

    @Override
    public void redo() throws LogException {
        getIndexFile().redoModifiedOverflow(this);
    }

    @Override
    public void undo() throws LogException {
        getIndexFile().undoModifiedOverflow(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - update overflow page " + pageNum;
    }
}
