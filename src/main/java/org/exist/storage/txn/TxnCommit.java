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
package org.exist.storage.txn;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogEntryTypes;

/**
 * @author wolf
 */
public class TxnCommit extends AbstractLoggable {

    public TxnCommit(final long transactionId) {
        this(null, transactionId);
    }

    public TxnCommit(final DBBroker broker, final long transactionId) {
        super(LogEntryTypes.TXN_COMMIT, transactionId);
    }

    @Override
    public void write(final ByteBuffer out) {
    }

    @Override
    public void read(final ByteBuffer in) {
    }

    @Override
    public int getLogSize() {
        return 0;
    }

    @Override
    public String dump() {
        return super.dump() + " - transaction " + transactionId + " committed.";
    }
}
