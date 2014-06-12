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
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Jul 6, 2007
 * Time: 10:48:35 AM
 * To change this template use File | Settings | File Templates.
 */
public class LockManager implements LockManagerMBean {

    @Override
    public TabularData getWaitingThreads() {
        final Map<String, LockInfo> map = DeadlockDetection.getWaitingThreads();
        try {
            return lockMapToComposite(map);
        } catch (final OpenDataException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final String[] itemNames = {"waitingThread", "lockType", "lockMode", "id", "owner", "waitingForRead", "waitingForWrite"};
    private static final String[] itemDescriptions = {            "Name of the thread waiting for the lock",
            "Type of the lock (COLLECTION or RESOURCE)",
            "Mode of the lock (READ or WRITE)",
            "Id of the lock (resource or collection path)",
            "The names of the threads currently holding the lock",
            "Names of threads currently waiting for a read lock",
            "Names of threads currently waiting for a write lock"
    };
    private static final String[] indexNames = {"waitingThread"};

    private TabularData lockMapToComposite(Map<String, LockInfo> map) throws OpenDataException {

        final OpenType<?>[] itemTypes = {
            SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
            new ArrayType<>(1, SimpleType.STRING), new ArrayType<>(1, SimpleType.STRING), new ArrayType<>(1, SimpleType.STRING)
        };

        final CompositeType lockType = new CompositeType("lockInfo", "Provides information on a thread waiting for a lock",
                itemNames, itemDescriptions, itemTypes);

        final TabularType tabularType = new TabularType("waitingThreads", "Lists all threads waiting for a lock", lockType, indexNames);
        final TabularDataSupport data = new TabularDataSupport(tabularType);

        for (final Map.Entry<String, LockInfo> entry : map.entrySet()) {
            final LockInfo info = entry.getValue();
            final Object[] itemValues = {
                entry.getKey(), info.getLockType(), info.getLockMode(), info.getId(),
                info.getOwners(), info.getWaitingForRead(), info.getWaitingForWrite()
            };
            data.put(new CompositeDataSupport(lockType, itemNames, itemValues));
        }
        return data;
    }
}