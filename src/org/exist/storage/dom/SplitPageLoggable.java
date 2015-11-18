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
import org.exist.storage.journal.Loggable;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 */
public class SplitPageLoggable extends AbstractLoggable implements Loggable {

    protected long pageNum;
    protected int splitOffset;
    protected byte[] oldData;
    protected int oldLen;
    private DOMFile domDb = null;

    public SplitPageLoggable(final Txn transaction, final long pageNum, final int splitOffset, final byte[] oldData,
                             final int oldLen) {
        super(DOMFile.LOG_SPLIT_PAGE, transaction.getId());
        this.pageNum = pageNum;
        this.splitOffset = splitOffset;
        this.oldData = oldData;
        this.oldLen = oldLen;
    }

    public SplitPageLoggable(final DBBroker broker, final long transactId) {
        super(DOMFile.LOG_SPLIT_PAGE, transactId);
        this.domDb = ((NativeBroker) broker).getDOMFile();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putInt(splitOffset);
        out.putShort((short) oldLen);
        out.put(oldData, 0, oldLen);
    }

    @Override
    public void read(final ByteBuffer in) {
        pageNum = in.getInt();
        splitOffset = in.getInt();
        oldLen = in.getShort();
        oldData = new byte[domDb.getFileHeader().getWorkSize()];
        in.get(oldData, 0, oldLen);
    }

    @Override
    public int getLogSize() {
        return 10 + oldLen;
    }

    @Override
    public void redo() throws LogException {
        domDb.redoSplitPage(this);
    }

    @Override
    public void undo() throws LogException {
        domDb.undoSplitPage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - page split: " + pageNum + " at offset: " + splitOffset;
    }
}
