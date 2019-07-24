/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.storage.index;

import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ByteConversion;

/**
 * Manages a list of pages containing unused sections.
 * 
 * Class {@link org.exist.storage.index.BFile} stores all data in variable
 * length records. As records may grow or shrink, the database has to keep
 * track of the amount of free space currently available in pages. Class 
 * {@link org.exist.storage.index.BFile} will always check if FreeList has a page
 * that can be filled before creating a new page.
 * 
 * FreeList implements a linked list of {@link FreeSpace} objects. Each object
 * in the list describes a page and the unused space in this page.
 * 
 * @see FreeList
 * @author wolf
 */
public class FreeList {

    //private final static Logger LOG = LogManager.getLogger(FreeList.class);

    public final static int MAX_FREE_LIST_LEN = 128;

    protected FreeSpace header = null;
    protected FreeSpace last = null;
    protected int size = 0;

    /**
     * Append a new {@link FreeSpace} object to the list,
     * describing the amount of free space available on a page.
     *  
     * @param free the free space
     */
    public void add( FreeSpace free ) {
        if(header == null) {
            header = free;
            last = free;
        } else {
            last.next = free;
            free.previous = last;
            last = free;
        }
        ++size;
    }

    /**
     * Remove a record from the list.
     * 
     * @param node the free space
     */
    public void remove(FreeSpace node) {
        --size;
        if (node.previous == null) {
            if (node.next != null) {
                node.next.previous = null;
                header = node.next;
            } else
                {header = null;}
        } else {
            node.previous.next = node.next;
            if (node.next != null)
                {node.next.previous = node.previous;}
            else
                {last = node.previous;}
        }
    }

    /**
     * Retrieve the record stored for the given page number.
     * 
     * @param pageNum the page number
     * @return the free space
     */
    public FreeSpace retrieve(long pageNum) {
        FreeSpace next = header;
        while(next != null) {
            if(next.page == pageNum)
                {return next;}
            next = next.next;
        }
        return null;
    }

    /**
     * Try to find a page that has at least requiredSize bytes
     * available. This method selects the page with the smallest
     * possible space. This guarantees that all pages will be filled before
     * creating a new page. 
     * 
     * @param requiredSize the required size
     *
     * @return the free space
     */
    public FreeSpace find(int requiredSize) {
        FreeSpace next = header;
        FreeSpace found = null;
        while(next != null) {
            if(next.free >= requiredSize) {
                if(found == null || next.free < found.free)
                    {found = next;}
            }
            next = next.next;
        }
        return found;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        FreeSpace next = header;
        while(next != null) {
            buf.append("[").append(next.page).append(", ");
            buf.append(next.free).append("] ");
            next = next.next;
        }
        return buf.toString();
    }

    /**
     * Read the list from a {@link RandomAccessFile}.
     * 
     * @param buf the buffer to read from
     * @param offset the position in the buffer to read from
     * @return the offset after reading
     * @throws IOException if an error occurs whilst reading
     */
    public int read(byte[] buf, int offset) throws IOException {
        final int fsize = ByteConversion.byteToInt(buf, offset);
        offset += 4;
        long page;
        int space;
        for (int i = 0; i < fsize; i++) {
            page = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            space = ByteConversion.byteToInt(buf, offset);
            offset += 4;
            add(new FreeSpace(page, space));
        }
        return offset;
    }

    /**
     * Write the list to a {@link RandomAccessFile}.
     * 
     * As the list is written to the file header, its maximum length
     * has to be restricted. The method will thus only store
     * {@link #MAX_FREE_LIST_LEN} entries and throw away the 
     * rest. Usually, this should not happen very often, so it is ok to
     * waste some space.
     *
     * @param buf the buffer to write to
     * @param offset the position in the buffer to write to
     * @return the offset after writing
     * @throws IOException if an error occurs whilst writing
     */
    public int write(byte[] buf, int offset) throws IOException {
        //does the free-space list fit into the file header?
        int skip = 0;
        if (size > MAX_FREE_LIST_LEN) {
            //LOG.warn("removing " + (size - MAX_FREE_LIST_LEN) + " free pages.");
            // no: remove some smaller entries to make it fit
            skip = size - MAX_FREE_LIST_LEN;
        }
        ByteConversion.intToByte(size - skip, buf, offset);
        offset += 4;
        FreeSpace next = header;
        while(next != null) {
            if(skip == 0) {
                ByteConversion.longToByte(next.page, buf, offset);
                offset += 8;
                ByteConversion.intToByte(next.free, buf, offset);
                offset += 4;
            } else
                {--skip;}
            next = next.next;
        }
        return offset;
    }
}
