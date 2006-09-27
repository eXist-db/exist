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

import java.nio.ByteBuffer;

/**
 * Interface to be implemented by all objects that can be written or read
 * from the journalling log.
 * 
 * @author wolf
 */
public interface Loggable {
    
	/**
	 * Returns the type id of the log entry. This is the type registered
	 * with class {@link LogEntryTypes}. The returned id is used by
	 * {@link JournalReader} to find the correct Loggable instance
	 * that can handle the entry. 
	 * 
	 * @return Type id of the log entry
	 */
    public byte getLogType();
    
    /**
     * Returns the transaction id of the transaction to which the
     * logged operation belongs.
     * 
     * @return transaction id 
     */
    public long getTransactionId();
    
    /**
     * Returns the {@link Lsn} of the entry.
     * 
     * @return LSN
     */
    public long getLsn();
    
    /**
     * Set the {@link Lsn} of the entry.
     * 
     * @param lsn
     */
    public void setLsn(long lsn);
    
    /**
     * Write this entry to the specified ByteBuffer.
     * 
     * @param out
     */
    public void write(ByteBuffer out);
    
    /**
     * Read the entry.
     * 
     * @param in
     */
    public void read(ByteBuffer in);
    
    /**
     * Returns the size of the work load of this
     * entry.
     * 
     * @return size of the work load of this entry.
     */
    public int getLogSize();
	
    /**
     * Redo the underlying operation. This method is
     * called by {@link org.exist.storage.recovery.RecoveryManager}.
     * 
     * @throws LogException
     */
    public void redo() throws LogException;
    
    /**
     * Undo, i.e. roll back, the underlying operation. The method
     * is called by {@link org.exist.storage.recovery.RecoveryManager}.
     * 
     * @throws LogException
     */
    public void undo() throws LogException;
    
    /**
     * Returns a description of the entry for debugging purposes.
     * 
     * @return description
     */
	public String dump();
}
