package org.exist.storage.store;

import java.io.EOFException;
import java.io.IOException;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

/**
 * Represents a (virtual) storage address in the paged file, consisting
 * of page number, offset (tuple id) and flags.
 */
public class StorageAddress {
	
	/**
	 *  Create virtual address from page number and offset (tid)
	 *
	 *@param  page    page number
	 *@param  offset  offset (tid)
	 *@return         new virtual address in a long
	 */
	public final static long createPointer(int page, short tid) {
		return tid | (((long)page) & 0xFFFFFFFFL) << 32;
	}

	public final static long createPointer(int page, short tid, short flags) {
		return tid | (((long)flags) & 0xFFFFL) << 16 | (((long)page) & 0xFFFFFFFFL) << 32;
	}

	/**
	 *  Get the tid (tuple id) from a virtual address
	 *
	 *@param  pointer  
	 *@return          the tid encoded in this address
	 */
	public final static short tidFromPointer(long pointer) {
		return (short) (pointer & 0xFFFFL);
	}

	/**
	 *  Get the page from a virtual address
	 *
	 *@param  pointer  
	 *@return          the page encoded in this address
	 */
	public final static int pageFromPointer(long pointer) {
		return (int) ((pointer >>> 32) & 0xFFFFFFFFL);
	}

	public final static short flagsFromPointer(long pointer) {
		return (short) ((pointer >>> 16) & 0xFFFFL);
	}

	public final static boolean equals(long p0, long p1) {
		return ((p0 & 0xFFFFFFFF0000FFFFL) == (p1 & 0xFFFFFFFF0000FFFFL));
	}
	
	public final static void write(long pointer, VariableByteOutputStream os) {
		os.writeInt(pageFromPointer(pointer));
		os.writeShort(tidFromPointer(pointer));
		os.writeShort(flagsFromPointer(pointer));
	}
	
	public final static long read(VariableByteInput is) throws IOException, EOFException {
	    return createPointer(is.readInt(), is.readShort(), is.readShort());
	}
	
	public final static String toString(long pointer) {
		return pageFromPointer(pointer) + ":" + tidFromPointer(pointer);
	}
}
