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
package org.exist.storage.journal;

/**
 * Abstract implementation of the Loggable interface.
 *
 * @author wolf
 */
public abstract class AbstractLoggable implements Loggable {

    protected final byte type;
    protected long transactionId;
    protected Lsn lsn;

    public AbstractLoggable(final byte type, final long transactionId) {
        this.type = type;
        this.transactionId = transactionId;
    }

    public void clear(final long transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public byte getLogType() {
        return type;
    }

    @Override
    public long getTransactionId() {
        return transactionId;
    }

    @Override
    public void setLsn(final Lsn lsn) {
        this.lsn = lsn;
    }

    @Override
    public Lsn getLsn() {
        return lsn;
    }

    @Override
    public void redo() throws LogException {
        // do nothing
    }

    @Override
    public void undo() throws LogException {
        // do nothing
    }

    /**
     * Default implementation returns the current LSN plus the
     * class name of the Loggable instance.
     */
    @Override
    public String dump() {
        return '[' + getLsn().toString() + "] " + getClass().getName() + ' ';
    }
}
