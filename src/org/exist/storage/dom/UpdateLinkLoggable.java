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
package org.exist.storage.dom;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

public class UpdateLinkLoggable extends AbstractLoggable {

    protected long pageNum;
    protected int offset;
    protected long link;
    protected long oldLink;
    private DOMFile domDb = null;
    
    public UpdateLinkLoggable(Txn transaction, long pageNum, int offset, long link, long oldLink) {
        super(DOMFile.LOG_UPDATE_LINK, transaction.getId());
        this.pageNum = pageNum;
        this.offset = offset;
        this.link = link;
        this.oldLink = oldLink;
    }

    public UpdateLinkLoggable(DBBroker broker, long transactId) {
        super(DOMFile.LOG_UPDATE_LINK, transactId);
        this.domDb = ((NativeBroker)broker).getDOMFile();
    }
    
    public void write(ByteBuffer out) {
        out.putInt((int) pageNum);
        out.putShort((short) offset);
        out.putLong(link);
        out.putLong(oldLink);
    }

    public void read(ByteBuffer in) {
        pageNum = in.getInt();
        offset = in.getShort();
        link = in.getLong();
        oldLink = in.getLong();
    }

    public int getLogSize() {
        return 22;
    }

    public void redo() throws LogException {
        domDb.redoUpdateLink(this);
    }
    
    public void undo() throws LogException {
        domDb.undoUpdateLink(this);
    }
    
    public String dump() {
        return super.dump() + " - updated link on page: " + pageNum + " at offset: " + offset;
    }
}
