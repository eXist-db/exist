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
import org.exist.storage.btree.Value;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 */
public class WriteOverflowPageLoggable extends AbstractLoggable {
    protected long pageNum;
    protected long nextPage;
    protected Value value;
    private DOMFile domDb = null;

    public WriteOverflowPageLoggable(final Txn transaction, final long pageNum, final long nextPage, final Value value) {
        super(DOMFile.LOG_WRITE_OVERFLOW, transaction.getId());
        this.pageNum = pageNum;
        this.nextPage = nextPage;
        this.value = value;
    }

    public WriteOverflowPageLoggable(final DBBroker broker, final long transactId) {
        super(DOMFile.LOG_WRITE_OVERFLOW, transactId);
        this.domDb = broker == null ? null : ((NativeBroker) broker).getDOMFile();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putInt((int) nextPage);
        out.putShort((short) value.getLength());
        out.put(value.data(), value.start(), value.getLength());
    }

    @Override
    public void read(final ByteBuffer in) {
        pageNum = in.getInt();
        nextPage = in.getInt();
        final int len = in.getShort();
        final byte[] data = new byte[len];
        in.get(data);
        value = new Value(data);
    }

    @Override
    public int getLogSize() {
        return 10 + value.getLength();
    }

    @Override
    public void redo() throws LogException {
        domDb.redoWriteOverflow(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoWriteOverflow(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - writing overflow page " + pageNum + "; next: " + nextPage;
    }

    public long getPageNum() {
        return pageNum;
    }

    public Value getValue() {
        return value;
    }
}