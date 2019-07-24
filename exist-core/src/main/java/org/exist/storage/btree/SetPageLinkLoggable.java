/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

public class SetPageLinkLoggable extends BTAbstractLoggable {

    protected long nextPage;
    protected long pageNum;

    public SetPageLinkLoggable(Txn transaction, byte fileId, long pageNum, long nextPage) {
        super(BTree.LOG_SET_LINK, fileId, transaction);
        this.pageNum = pageNum;
        this.nextPage = nextPage;
    }

    public SetPageLinkLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_SET_LINK, broker, transactionId);
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getLong();
        nextPage = in.getLong();
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putLong(pageNum);
        out.putLong(nextPage);
    }

    @Override
    public int getLogSize() {
        return super.getLogSize() + 16;
    }

    @Override
    public void redo() throws LogException {
        getStorage().redoSetPageLink(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - set next page link for page: " + pageNum + ": " + nextPage;
    }
}
