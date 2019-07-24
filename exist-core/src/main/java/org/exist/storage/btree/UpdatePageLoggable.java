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
package org.exist.storage.btree;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author wolf
 *
 */
public class UpdatePageLoggable extends BTAbstractLoggable {

    protected Value prefix = Value.EMPTY_VALUE;
    protected Value values[];
    protected long pointers[];
    protected long pageNum;
    protected int nValues;
    protected int nPointers;
    
    /**
     * @param transaction the database transaction
     * @param fileId the file id
     * @param pageNum the page number
     * @param prefix the prefix
     * @param values the values
     * @param nValues the number of values
     * @param pointers the pointers
     * @param nPointers the number of pointers
     */
    public UpdatePageLoggable(Txn transaction, byte fileId, long pageNum, Value prefix, Value values[], int nValues,
            long pointers[], int nPointers) {
        super(BTree.LOG_UPDATE_PAGE, fileId, transaction);
        this.pageNum = pageNum;
        this.prefix = prefix;
        this.values = values;
        this.nValues = nValues;
        this.pointers = pointers;
        this.nPointers = nPointers;
    }

    /**
     * @param broker the database broker
     * @param transactionId the transaction id
     */
    public UpdatePageLoggable(DBBroker broker, long transactionId) {
        super(BTree.LOG_UPDATE_PAGE, broker, transactionId);
    }

    @Override
    public void write(ByteBuffer out) {
        super.write(out);
        out.putLong(pageNum);
        final short pfxLen = (short) prefix.getLength();
        out.putShort(pfxLen);
        if (pfxLen > 0)
            {out.put(prefix.data(), prefix.start(), pfxLen);}
        out.putShort((short) nValues);
        short len;
        for (int i = 0; i < nValues; i++) {
            len = (short) values[i].getLength();
            out.putShort(len);
            if (len > 0)
                {out.put(values[i].data(), values[i].start(), len);}
        }
        
        out.putShort((short) nPointers);
        for (int i = 0; i < nPointers; i++) {
            out.putLong(pointers[i]);
        }
    }

    @Override
    public void read(ByteBuffer in) {
        super.read(in);
        pageNum = in.getLong();
        final short pfxLen = in.getShort();
        if (pfxLen > 0) {
            final byte[] pdata = new byte[pfxLen];
            in.get(pdata);
            prefix = new Value(pdata);
        }
        nValues = in.getShort();
        values = new Value[nValues];
        int dataLen;
        byte[] data;
        for (int i = 0; i < nValues; i++) {
            dataLen = in.getShort();
            data = new byte[dataLen];
            if (dataLen > 0)
                {in.get(data);}
            values[i] = new Value(data);
        }
        
        nPointers = in.getShort();
        pointers = new long[nPointers];
        for (int i = 0; i < nPointers; i++) {
            pointers[i] = in.getLong();
        }
    }

    @Override
    public int getLogSize() {
        int len = super.getLogSize() + 14 + (nPointers * 8) + prefix.getLength();
        for (int i = 0; i < nValues; i++)
            len += values[i].getLength() + 2;
        return len;
    }

    @Override
    public void redo() throws LogException {
        getStorage().redoUpdatePage(this);
    }

    @Override
    public String dump() {
        return super.dump() + " - updated page " + pageNum;
    }
}
