/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.lock;

import org.exist.Debuggable;
import org.exist.util.LockException;

public interface Lock extends Debuggable {

    /**
     * The modes of a {@link Lock}
     */
    enum LockMode {
        NO_LOCK((byte)0x0),
        READ_LOCK((byte)0x1),
        WRITE_LOCK((byte)0x2),

        INTENTION_READ((byte)0x3),
        INTENTION_WRITE((byte)0x4);

        private final byte val;

        LockMode(final byte val) {
            this.val = val;
        }

        public byte getVal() {
            return val;
        }
    }

    /**
     * The type of a {@link Lock}
     */
    enum LockType {

        /**
         * Should not be used outside of {@link EnsureLocked}!
         */
        @Deprecated UNKNOWN((byte) 0x5),

        @Deprecated LEGACY_COLLECTION((byte) 0x4),
        @Deprecated LEGACY_DOCUMENT((byte) 0x3),

        COLLECTION((byte) 0x2),
        DOCUMENT((byte) 0x1),

        BTREE((byte) 0x0);

        private final byte val;

        LockType(final byte val) {
            this.val = val;
        }

        public byte getVal() {
            return val;
        }
    }

    /**
     * Get the id of the lock
     * @return the id
     */
    String getId();

    /**
     * Create a LockInfo entry for the given lock.
     *
     * @return the lock info
     */
    LockInfo getLockInfo();

	/**
	 * Acquire a lock for read.
	 * 
	 * @throws LockException if a lock error occurs
     * @return true if lock could be acquired
     *
     * @deprecated Use {@link #acquire(LockMode)}
	 */
	@Deprecated
    boolean acquire() throws LockException;
    
    /**
     * Acquire a lock for read or write.
     * mode is one of {@link LockMode#READ_LOCK} or
     * {@link LockMode#WRITE_LOCK}.
     * 
     * @param mode The mode of the lock to acquire
     * @throws LockException if a lock error occurs
     * @return true if lock could be acquired
     */
	boolean acquire(LockMode mode) throws LockException;
	
	/**
	 * Attempt to acquire a lock for read or write. This method
	 * will fail immediately if the lock cannot be acquired.
	 *
     * @param mode The mode of the lock to attempt to acquire
     * @return true if attempt
	 */
	boolean attempt(LockMode mode);
	
    /**
     * Release a lock of the specified type.
     *
     * @param mode The mode of the lock to release
     */
    void release(LockMode mode);

    /**
     * Release a number of references of the specified lock mode
     *
     * @param mode The mode of the lock to release
     * @param count The number of references to release
     */
    void release(LockMode mode, int count);

    /**
     * Returns true if there are active or pending
     * write locks.
     *
     * @return true if the lock is locked for write
     */
    boolean isLockedForWrite();

    /**
     * Check if the specified thread does currently hold a read lock.
     *
     * @param owner the thread to search for
     * @return true if the thread holds a read lock
     */
    boolean isLockedForRead(Thread owner);

    /**
     * Check if the lock is currently locked by someone.
     *
     * @return true if there's an active read or write lock
     */
    boolean hasLock();

    /**
     * Check if the specified thread holds either a write or a read lock
     * on the resource.
     *
     * @param owner the thread
     * @return true if owner has a lock
     */
    boolean hasLock(Thread owner);

    /**
     * Wake up waiting threads and recompute dependencies.
     * Currently used to rerun deadlock detection.
     */
    void wakeUp();
}
