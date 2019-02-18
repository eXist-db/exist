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

import org.exist.storage.BrokerPool;
import org.exist.util.io.FileFilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheMonitor;
import org.exist.util.io.FilterInputStreamCacheMonitor.FilterInputStreamCacheInfo;
import org.exist.util.io.MemoryMappedFileFilterInputStreamCache;
import org.exist.management.impl.BinaryInputStreamCacheInfo.CacheType;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class BinaryValues implements BinaryValuesMXBean {
    private final String instanceId;

    public BinaryValues(final BrokerPool pool) {
        this.instanceId = pool.getId();
    }

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
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public List<BinaryInputStreamCacheInfo> getCacheInstances() {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        final Collection<FilterInputStreamCacheInfo> cacheInstances = monitor.getActive();

        final List<BinaryInputStreamCacheInfo> results = new ArrayList<>();
        for (final FilterInputStreamCacheInfo cacheInstance : cacheInstances) {

            final BinaryInputStreamCacheInfo result;
            final FilterInputStreamCache cache = cacheInstance.getCache();
            if (cache instanceof FileFilterInputStreamCache) {
                result = new BinaryInputStreamCacheInfo(CacheType.FILE, cacheInstance.getRegistered(),
                        Optional.of(((FileFilterInputStreamCache) cache).getFilePath()), cache.getLength());
            } else if (cache instanceof MemoryMappedFileFilterInputStreamCache) {
                result = new BinaryInputStreamCacheInfo(CacheType.MEMORY_MAPPED_FILE, cacheInstance.getRegistered(),
                        Optional.of(((MemoryMappedFileFilterInputStreamCache) cache).getFilePath()), cache.getLength());
            } else {
                result = new BinaryInputStreamCacheInfo(CacheType.MEMORY, cacheInstance.getRegistered(),
                        Optional.empty(), cache.getLength());
            }

            results.add(result);
        }

        return results;
    }
}
