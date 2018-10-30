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
public class CreatePageLoggable extends AbstractLoggable {
    protected long prevPage;
    protected long newPage;
    protected long nextPage;
    protected short nextTID;
    private DOMFile domDb = null;

    public CreatePageLoggable(final Txn transaction, final long prevPage, final long newPage, final long nextPage) {
        this(transaction, prevPage, newPage, nextPage, (short) -1);
    }

    public CreatePageLoggable(final Txn transaction, final long prevPage, final long newPage, final long nextPage, final short nextTID) {
        super(DOMFile.LOG_CREATE_PAGE, transaction.getId());
        this.prevPage = prevPage;
        this.newPage = newPage;
        this.nextPage = nextPage;
        this.nextTID = nextTID;
    }

    public CreatePageLoggable(final DBBroker broker, final long transactId) {
        super(DOMFile.LOG_CREATE_PAGE, transactId);
        this.domDb = broker == null ? null : ((NativeBroker) broker).getDOMFile();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) prevPage);
        out.putInt((int) newPage);
        out.putInt((int) nextPage);
        out.putShort(nextTID);
    }

    @Override
    public void read(final ByteBuffer in) {
        prevPage = in.getInt();
        newPage = in.getInt();
        nextPage = in.getInt();
        nextTID = in.getShort();
    }

    @Override
    public int getLogSize() {
        return 14;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoCreatePage(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoCreatePage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - new page created: " + newPage + "; prev. page: " + prevPage + "; next page: " + nextPage;
    }
}
