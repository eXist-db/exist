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
package org.exist.storage.index;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public abstract class AbstractBFileLoggable extends AbstractLoggable {

    protected NativeBroker broker;
    protected byte fileId;
    
    /**
     * 
     * 
     * @param fileId 
     * @param transaction 
     * @param type 
     */
    public AbstractBFileLoggable(byte type, byte fileId, Txn transaction) {
        super(type, transaction.getId());
        this.fileId = fileId;
    }

    public AbstractBFileLoggable(DBBroker broker, long transactionId) {
        super(BFile.LOG_CREATE_PAGE, transactionId);
        this.broker = (NativeBroker) broker;
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
     */
    public void write(ByteBuffer out) {
        out.put(fileId);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
        fileId = in.get();
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 1;
    }
    
    protected BFile getIndexFile() {
        return (BFile) broker.getStorage(fileId);
    }

    public String dump() {
        return super.dump() + " [BFile]";
    }
}
