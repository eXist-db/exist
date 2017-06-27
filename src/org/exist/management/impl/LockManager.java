/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.management.impl;

import org.exist.storage.lock.DeadlockDetection;
import org.exist.storage.lock.LockInfo;

import javax.management.openmbean.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Returns information from the lock manager. Very useful to check for deadlocks.
 */
public class LockManager implements LockManagerMXBean {

    @Override
    public List<Lock> getWaitingThreads() {
        final List<Lock> lockList = new ArrayList<>();
        final Map<String, LockInfo> map = DeadlockDetection.getWaitingThreads();
        for (final Map.Entry<String, LockInfo> entry : map.entrySet()) {
            lockList.add(new Lock(entry.getKey(), entry.getValue()));
        }
        return lockList;
    }
}