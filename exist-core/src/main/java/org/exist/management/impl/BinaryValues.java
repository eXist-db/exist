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

import org.exist.management.impl.BinaryInputStreamCacheInfo.CacheType;
import org.exist.storage.BrokerPool;
import org.exist.util.io.FileFilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheMonitor;
import org.exist.util.io.FilterInputStreamCacheMonitor.FilterInputStreamCacheInfo;
import org.exist.util.io.MemoryMappedFileFilterInputStreamCache;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JMX MBean exposing information about active binary value input-stream caches
 * for a specific database instance.
 */
public record BinaryValues(String instanceId) implements BinaryValuesMXBean {
    /**
     * Create a new BinaryValues MBean for the given broker pool.
     *
     * @param instanceId the broker pool representing the database instance
     */
    public BinaryValues(final BrokerPool instanceId) {
        this(instanceId.getId());
    }

    /**
     * Explicit JavaBean-style accessor required by the MBean attribute discovery,
     * which relies on the {@code getXxx()} naming convention. The record's
     * auto-generated {@link #instanceId()} accessor does not satisfy that convention,
     * so we delegate to it here. See <a href="https://github.com/eXist-db/exist/issues/6379">#6379</a>.
     */
    @Override
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Return a JMX object-name query that matches all BinaryValues MBeans across all instances.
     *
     * @return the wildcard query string
     */
    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=BinaryValues";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instanceId));
    }

    @Override
    public List<BinaryInputStreamCacheInfo> getCacheInstances() {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        final Collection<FilterInputStreamCacheInfo> cacheInstances = monitor.getActive();

        final List<BinaryInputStreamCacheInfo> results = new ArrayList<>();
        for (final FilterInputStreamCacheInfo cacheInstance : cacheInstances) {

            final BinaryInputStreamCacheInfo result;
            final FilterInputStreamCache cache = cacheInstance.getCache();
            switch (cache) {
                case final FileFilterInputStreamCache streamCache1 ->
                        result = new BinaryInputStreamCacheInfo(CacheType.FILE, cacheInstance.getRegistered(),
                                Optional.of(streamCache1.getFilePath()), cache.getLength());
                case final MemoryMappedFileFilterInputStreamCache streamCache ->
                        result = new BinaryInputStreamCacheInfo(CacheType.MEMORY_MAPPED_FILE, cacheInstance.getRegistered(),
                                Optional.of(streamCache.getFilePath()), cache.getLength());
                case null, default ->
                        result = new BinaryInputStreamCacheInfo(CacheType.MEMORY, cacheInstance.getRegistered(),
                                Optional.empty(), cache.getLength()); // DW: This might be a null cache
            }

            results.add(result);
        }

        return results;
    }
}
