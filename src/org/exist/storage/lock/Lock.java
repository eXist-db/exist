/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 * $Id$
 */
package org.exist.storage.lock;

import org.exist.util.LockException;

public interface Lock {

	public final static int READ_LOCK = 0;
	public final static int WRITE_LOCK = 1;
	public final static int NO_LOCK = -1;
	
	/**
	 * Acquire a lock for read.
	 * 
	 * @throws LockException
	 */
    public boolean acquire( ) throws LockException;
    
    /**
     * Acquire a lock for read or write.
     * mode is one of {@link #READ_LOCK} or
     * {@link #WRITE_LOCK}.
     * 
     * @param mode
     * @throws LockException
     */
	public boolean acquire( int mode ) throws LockException;
	
	/**
	 * Attempt to acquire a lock for read or write. This method
	 * will fail immediately if the lock cannot be acquired.
	 *  
	 * @param mode
	 * @throws LockException
	 */
	public boolean attempt( int mode );
	
	/**
	 * Release a lock. This method assumes that the
	 * lock is a read lock.
	 */
    public void release( );
    
    /**
     * Release a lock of the specified type.
     * 
     * @param mode
     */
    public void release( int mode );
    
    /**
     * Returns true if there are active or pending
     * write locks.
     */
    public boolean isLockedForWrite();
}
