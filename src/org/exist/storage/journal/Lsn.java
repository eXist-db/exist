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
 * Log Sequence Number: identifies a log record within the journal file.
 * A LSN is represented by a Java long and consists of the file number
 * of the journal file and an offset into the file.
 * 
 * @author wolf
 */
public class Lsn {

	public static final long LSN_INVALID = -1;
		
    private static final long INT_MASK = 0xFFFFFFFFL;
    
    public static long create(int fileNumber, int offset) {
        return offset & INT_MASK | ((fileNumber & INT_MASK) << 32);
    }
    
    /**
     * Returns the file number encoded in the passed LSN.
     * 
     * @param lsn
     * @return file number
     */
    public static long getFileNumber(long lsn) {
        return (lsn >> 32) & INT_MASK;
    }
    
    /**
     * Returns the file offset encoded in the passed LSN.
     * 
     * @param lsn
     * @return file offset
     */
    public static long getOffset(long lsn) {
        return (lsn & INT_MASK);
    }
	
	public static String dump(long lsn) {
		return getFileNumber(lsn) + ", " + getOffset(lsn);
	}
}
