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
package org.exist.storage.journal;

/**
 * Abstract implementation of the Loggable interface.
 * 
 * @author wolf
 *
 */
public abstract class AbstractLoggable implements Loggable {

    protected long transactId;
    protected byte type;
    protected long lsn;
    
    /**
     * Default constructor: initialize entry type and transaction id.
     * 
     * @param type
     * @param transactionId
     */
    public AbstractLoggable(byte type, long transactionId) {
        this.type = type;
        this.transactId = transactionId;
    }
    
    public void clear(long transactionId) {
        this.transactId = transactionId;
    }
    
    public byte getLogType() {
        return type;
    }
    
    public long getTransactionId() {
        return transactId;
    }
    
    public void setLsn(long lsn) {
        this.lsn = lsn;
    }
    
    public long getLsn() {
        return lsn;
    }
	
    public void redo() throws LogException {
        // do nothing
    }
    
    public void undo() throws LogException {
        // do nothing
    }
    
    /**
     * Default implementation returns the current LSN plus the
     * class name of the Loggable instance. 
     */
	public String dump() {
		return '[' + Lsn.dump(getLsn()) + "] " + getClass().getName() + ' ';
	}
}
