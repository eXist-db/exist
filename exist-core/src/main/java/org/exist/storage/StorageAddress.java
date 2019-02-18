package org.exist.storage;

import java.io.EOFException;
import java.io.IOException;

import org.exist.dom.persistent.NodeHandle;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

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
    public final static long createPointer(int page, short tid) {
        return tid | (((long)page) & 0xFFFFFFFFL) << 32;
    }

    public final static long createPointer(int page, short tid, short flags) {
        return tid | (((long)flags) & 0xFFFFL) << 16 | (((long)page) & 0xFFFFFFFFL) << 32;
    }

    /**
     *  Get the tuple identifier from a virtual address.
     *
     *@param  pointer The address
     *@return The tuple ID at this address
     */
    public final static short tidFromPointer(long pointer) {
        return (short) (pointer & 0xFFFFL);
    }

    /**
     *  Get the page number from a virtual address.
     *
     *@param  pointer The address
     *@return The page number
     */
    public final static int pageFromPointer(long pointer) {
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
    public final static short indexTypeFromPointer(long pointer) {
        return (short) ((pointer >>> 16) & 0xFFFFL);
    }

    public final static long setIndexType(long pointer, short type) {
        return pointer | ((long)(type << 16) & 0xFFFF0000L);
    }

    public final static boolean hasAddress(long pointer) {
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
    public final static boolean equals(long p0, long p1) {
        return ((p0 & 0xFFFFFFFF0000FFFFL) == (p1 & 0xFFFFFFFF0000FFFFL));
    }

    public final static boolean equals(NodeHandle n0, NodeHandle n1) {
        return equals(n0.getInternalAddress(), n1.getInternalAddress());
    }

    public final static void write(long pointer, VariableByteOutputStream os) {
        os.writeInt(pageFromPointer(pointer));
    	os.writeShort(tidFromPointer(pointer));
    	os.writeShort(indexTypeFromPointer(pointer));
    }

    public final static long read(VariableByteInput is) throws IOException, EOFException {
        return createPointer(is.readInt(), is.readShort(), is.readShort());
    }

    public final static String toString(long pointer) {
        return pageFromPointer(pointer) + ":" + tidFromPointer(pointer);
    }

    public final static String toString(NodeHandle nodeHandle) {
        return toString(nodeHandle.getInternalAddress());
    }
}
