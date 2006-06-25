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
import java.text.DateFormat;
import java.util.Date;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogEntryTypes;

/**
 * @author wolf
 */

public class Checkpoint extends AbstractLoggable {

	private long timestamp;
	private long storedLsn;
	
	private final static DateFormat df = 
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
	
    public Checkpoint(long transactionId) {
        this(null, transactionId);
    }
    
    public Checkpoint(DBBroker broker, long transactionId) {
        super(LogEntryTypes.CHECKPOINT, transactionId);
		timestamp = new Date().getTime();
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
    	out.putLong(lsn);
		out.putLong(timestamp);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
    	storedLsn = in.getLong();
		timestamp = in.getLong();
    }

    public long getStoredLsn() {
    	return storedLsn;
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 16;
    }
	
    public String getDateString() {
    	return df.format(new Date(timestamp));
    }
    
	public String dump() {
		return super.dump() + " - checkpoint at " + df.format(new Date(timestamp));
	}
}
