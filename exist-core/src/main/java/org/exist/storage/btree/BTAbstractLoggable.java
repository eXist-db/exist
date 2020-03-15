/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 */
public abstract class BTAbstractLoggable extends AbstractLoggable {
    private byte fileId;
    private NativeBroker broker;

    public BTAbstractLoggable(final byte type, final byte fileId, final Txn transaction) {
        super(type, transaction.getId());
        this.fileId = fileId;
    }

    public BTAbstractLoggable(final byte type, final DBBroker broker, final long transactionId) {
        super(type, transactionId);
        this.broker = (NativeBroker) broker;
    }

    protected BTree getStorage() {
        return broker.getStorage(fileId);
    }

    @Override
    public void read(final ByteBuffer in) {
        fileId = in.get();
    }

    @Override
    public void write(final ByteBuffer out) {
        out.put(fileId);
    }

    @Override
    public int getLogSize() {
        return 1;
    }
}
