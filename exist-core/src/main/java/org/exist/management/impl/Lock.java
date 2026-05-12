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

package org.exist.management.impl;

import org.exist.storage.lock.LockInfo;

/**
 * Detail information about lock being held.
 */
public class Lock {

    private final String waitingThread;
    private final String lockType;
    private final String lockMode;
    private final String id;
    private final String[] owner;
    private final String[] waitingForRead;
    private final String[] waitingForWrite;

    /**
     * Create a Lock snapshot from the given waiting thread name and lock info.
     *
     * @param waitingThread the name of the thread waiting to acquire the lock
     * @param info          the lock information
     */
    public Lock(final String waitingThread, final LockInfo info) {

        this.waitingThread = waitingThread;
        this.lockType = info.getLockType();
        this.lockMode = info.getLockMode();
        this.id = info.getId();
        this.owner = info.getOwners();
        this.waitingForRead = info.getWaitingForRead();
        this.waitingForWrite = info.getWaitingForWrite();
    }

    /**
     * Get the name of the thread waiting to acquire the lock.
     *
     * @return the waiting thread name
     */
    public String getWaitingThread() {
        return waitingThread;
    }

    /**
     * Get the type of the lock (e.g. collection or document).
     *
     * @return the lock type string
     */
    public String getLockType() {
        return lockType;
    }

    /**
     * Get the mode of the lock (e.g. read or write).
     *
     * @return the lock mode string
     */
    public String getLockMode() {
        return lockMode;
    }

    /**
     * Get the identifier of the resource being locked.
     *
     * @return the lock target identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Get the names of the threads currently holding the lock.
     *
     * @return array of owner thread names
     */
    public String[] getOwner() {
        return owner;
    }

    /**
     * Get the names of the threads waiting to acquire a read lock.
     *
     * @return array of thread names waiting for a read lock
     */
    public String[] getWaitingForRead() {
        return waitingForRead;
    }

    /**
     * Get the names of the threads waiting to acquire a write lock.
     *
     * @return array of thread names waiting for a write lock
     */
    public String[] getWaitingForWrite() {
        return waitingForWrite;
    }
}
