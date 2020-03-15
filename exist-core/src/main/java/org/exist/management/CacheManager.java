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
package org.exist.management;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class CacheManager implements CacheManagerMXBean {
    private final String instanceId;
    private final org.exist.storage.CacheManager manager;

    public CacheManager(final String instanceId, final org.exist.storage.CacheManager manager) {
        this.instanceId = instanceId;
        this.manager = manager;
    }

    public static String getAllInstancesQuery() {
        return "org.exist.management." + '*' + ":type=CacheManager";
    }

    private static ObjectName getName(final String instanceId) throws MalformedObjectNameException {
        return new ObjectName("org.exist.management." + instanceId + ":type=CacheManager");
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return getName(instanceId);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public long getMaxTotal() {
        return manager.getMaxTotal();
    }

    @Override
    public long getMaxSingle() {
        return manager.getMaxSingle();
    }

    @Override
    public long getCurrentSize() {
        return manager.getCurrentSize();
    }
}
