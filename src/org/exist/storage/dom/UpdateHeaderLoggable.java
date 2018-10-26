/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 *
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.dom;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 */
public class UpdateHeaderLoggable extends AbstractLoggable {
    protected long pageNum;
    protected long nextPage;
    protected long prevPage;
    protected long oldNext;
    protected long oldPrev;
    private DOMFile domDb = null;

    public UpdateHeaderLoggable(final Txn transaction, final long prevPage, final long pageNum, final long nextPage,
                                final long oldPrev, final long oldNext) {
        super(DOMFile.LOG_UPDATE_HEADER, transaction.getId());
        this.prevPage = prevPage;
        this.pageNum = pageNum;
        this.nextPage = nextPage;
        this.oldPrev = oldPrev;
        this.oldNext = oldNext;
    }

    public UpdateHeaderLoggable(final DBBroker broker, final long transactId) {
        super(DOMFile.LOG_UPDATE_HEADER, transactId);
        this.domDb = broker == null ? null : ((NativeBroker) broker).getDOMFile();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) prevPage);
        out.putInt((int) pageNum);
        out.putInt((int) nextPage);
        out.putInt((int) oldPrev);
        out.putInt((int) oldNext);
    }

    @Override
    public void read(final ByteBuffer in) {
        prevPage = in.getInt();
        pageNum = in.getInt();
        nextPage = in.getInt();
        oldPrev = in.getInt();
        oldNext = in.getInt();
    }

    @Override
    public int getLogSize() {
        return 20;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoUpdateHeader(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoUpdateHeader(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - update header of page " + pageNum + ": prev = " + prevPage +
                "; next = " + nextPage + "; oldPrev = " + oldPrev + "; oldNext = " + oldNext;
    }
}
