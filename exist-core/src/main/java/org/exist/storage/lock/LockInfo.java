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
package org.exist.storage.lock;

import org.exist.Debuggable;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Encapsulates debug information about a lock. This information can be exported
 * via the JMX management interface, if enabled.
 */
public class LockInfo implements Debuggable {

    public static final String COLLECTION_LOCK = "COLLECTION";
    public static final String RESOURCE_LOCK = "RESOURCE";

    public static final String READ_LOCK = "READ";
    public static final String WRITE_LOCK = "WRITE";

    private final String lockType;

    private final String lockMode;

    private final String id;

    private final String[] owners;

    private String[] waitingForWrite = new String[0];

    private String[] waitingForRead = new String[0];

    private String[] readLocks = new String[0];

    public LockInfo(String lockType, String lockMode, String id, String[] owners) {
        this.lockType = lockType;
        this.lockMode = lockMode;
        this.id = id;
        this.owners = owners;
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
    
    public String[] getOwners() {
        return owners;
    }

    public String[] getWaitingForWrite() {
        return waitingForWrite;
    }

    public void setWaitingForWrite(String[] waitingForWrite) {
        this.waitingForWrite = waitingForWrite;
    }

    public String[] getWaitingForRead() {
        return waitingForRead;
    }

    public void setWaitingForRead(String[] waitingForRead) {
        this.waitingForRead = waitingForRead;
    }

    public String[] getReadLocks() {
        return readLocks;
    }

    public void setReadLocks(String[] readLocks) {
        this.readLocks = readLocks;
    }

    @Override
    public void debug(PrintStream out) {
        out.println("Lock type: " + getLockType());
        out.println("Lock mode: " + getLockMode());
        out.println("Lock id: " + getId());
        out.println("Held by: " + Arrays.toString(getOwners()));
        out.println("Read locks: " + Arrays.toString(getReadLocks()));
        out.println("Wait for read: " + Arrays.toString(getWaitingForRead()));
        out.println("Wait for write: " + Arrays.toString(getWaitingForWrite()));
    }
}
