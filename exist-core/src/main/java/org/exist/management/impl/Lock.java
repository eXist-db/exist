/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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

    public Lock(String waitingThread, LockInfo info) {

        this.waitingThread = waitingThread;
        this.lockType = info.getLockType();
        this.lockMode = info.getLockMode();
        this.id = info.getId();
        this.owner = info.getOwners();
        this.waitingForRead = info.getWaitingForRead();
        this.waitingForWrite = info.getWaitingForWrite();
    }

    public String getWaitingThread() {
        return waitingThread;
    }

    public String getLockType() {
        return lockType;
    }

    public String getLockMode() {
        return lockMode;
    }

    public String getId() {
        return id;
    }

    public String[] getOwner() {
        return owner;
    }

    public String[] getWaitingForRead() {
        return waitingForRead;
    }

    public String[] getWaitingForWrite() {
        return waitingForWrite;
    }
}
