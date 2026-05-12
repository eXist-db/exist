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

/**
 * JMX MBean exposing statistics for a single internal page cache
 * ({@link org.exist.storage.cache.Cache}).
 */
public class Cache implements CacheMXBean {
    private final String instanceId;
    private final org.exist.storage.cache.Cache cache;

    /**
     * Create a new Cache MBean wrapper.
     *
     * @param instanceId the identifier of the database instance
     * @param cache      the underlying cache to expose
     */
    public Cache(final String instanceId, final org.exist.storage.cache.Cache cache) {
        this.instanceId = instanceId;
        this.cache = cache;
    }

    /**
     * Return a JMX object-name query that matches all Cache MBeans across all instances.
     *
     * @return the wildcard query string
     */
    public static String getAllInstancesQuery() {
        return "org.exist.management." + '*' + ":type=CacheManager.Cache," + '*';
    }

    private static ObjectName getName(final String instanceId, final String cacheName, final String cacheType) throws MalformedObjectNameException {
        return new ObjectName("org.exist.management." + instanceId + ":type=CacheManager.Cache,name=" + cacheName + ",cache-type=" + cacheType);
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return getName(instanceId, cache.getName(), cache.getType().toString());
    }

    @Override
    public String instanceId() {
        return instanceId;
    }

    @Override
    public org.exist.storage.cache.Cache.CacheType getType() {
        return cache.getType();
    }

    @Override
    public int getSize() {
        return cache.getBuffers();
    }

    @Override
    public int getUsed() {
        return cache.getUsedBuffers();
    }

    @Override
    public int getHits() {
        return cache.getHits();
    }

    @Override
    public int getFails() {
        return cache.getFails();
    }

    @Override
    public String getCacheName() {
        return cache.getName();
    }
}
