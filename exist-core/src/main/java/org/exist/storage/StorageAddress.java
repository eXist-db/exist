/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.dom.persistent.NodeHandle;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import java.io.EOFException;
import java.io.IOException;

/**
 * Represents a (virtual) storage address in the paged file, consisting
 * of page number, tuple id and type indicator.
 * 
 * The format of a storage address is as follows:
 * 
 * <pre>
 * | page    | type | tid |
 * | 1 2 3 4 | 5 6   | 7 8 |
 * </pre>
 */
public class StorageAddress {

    /**
     *  Create virtual address from page number and offset (tid)
     *
     *@param  page    Page number
     *@param  tid     Tuple identifier
     *@return         Virtual address of the tuple
     */
    public static long createPointer(int page, short tid) {
        return tid | (((long)page) & 0xFFFFFFFFL) << 32;
    }

    public static long createPointer(int page, short tid, short flags) {
        return tid | (((long)flags) & 0xFFFFL) << 16 | (((long)page) & 0xFFFFFFFFL) << 32;
    }

    /**
     *  Get the tuple identifier from a virtual address.
     *
     *@param  pointer The address
     *@return The tuple ID at this address
     */
    public static short tidFromPointer(long pointer) {
        return (short) (pointer & 0xFFFFL);
    }

    /**
     *  Get the page number from a virtual address.
     *
     *@param  pointer The address
     *@return The page number
     */
    public static int pageFromPointer(long pointer) {
        return (int) ((pointer >>> 32) & 0xFFFFFFFFL);
    }

    /**
     * Get the type indicator from a virtual address.
     * 
     * Returns a short corresponding to the type constants defined
     * in {@link org.exist.xquery.value.Type}.
     * 
     * @param pointer The address
     * @return The type indicator
     */
    public static short indexTypeFromPointer(long pointer) {
        return (short) ((pointer >>> 16) & 0xFFFFL);
    }

    public static long setIndexType(long pointer, short type) {
        return pointer | ((long)(type << 16) & 0xFFFF0000L);
    }

    public static boolean hasAddress(long pointer) {
        return (pointer & 0xFFFFFFFF0000FFFFL) > 0;
    }

    /**
     * Returns <code>true</code> if the page number and the tuple ID of the two storage
     * addresses are equal. The type indicator is ignored.
     * 
     * @param p0 The first storage address
     * @param p1 The second storage address
     * @return <code>true</code> if the page number and the tuple ID
     * of the two storage addresses are equal
     */
    public static boolean equals(long p0, long p1) {
        return (p0 & 0xFFFFFFFF0000FFFFL) == (p1 & 0xFFFFFFFF0000FFFFL);
    }

    public static boolean equals(NodeHandle n0, NodeHandle n1) {
        return equals(n0.getInternalAddress(), n1.getInternalAddress());
    }

    public static void write(long pointer, VariableByteOutputStream os) {
        os.writeInt(pageFromPointer(pointer));
    	os.writeShort(tidFromPointer(pointer));
    	os.writeShort(indexTypeFromPointer(pointer));
    }

    public static long read(VariableByteInput is) throws IOException, EOFException {
        return createPointer(is.readInt(), is.readShort(), is.readShort());
    }

    public static String toString(long pointer) {
        return pageFromPointer(pointer) + ":" + tidFromPointer(pointer);
    }

    public static String toString(NodeHandle nodeHandle) {
        return toString(nodeHandle.getInternalAddress());
    }
}
