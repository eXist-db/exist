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
package org.exist.storage.txn;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogEntryTypes;

/**
 * @author wolf
 *
 */
public class TxnCommit extends AbstractLoggable {

    public TxnCommit(long transactionId) {
        this(null, transactionId);
    }
    
    public TxnCommit(DBBroker broker, long transactionId) {
        super(LogEntryTypes.TXN_COMMIT, transactionId);
    }

    public void write(ByteBuffer out) {
    }

    public void read(ByteBuffer in) {
    }

    public int getLogSize() {
        return 0;
    }
	
	public String dump() {
		return super.dump() + " - transaction " + transactId + " committed.";
	}
}
