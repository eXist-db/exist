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

package org.exist.util.io;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Monitors active {@link FilterInputStreamCacheMonitor} instances.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class FilterInputStreamCacheMonitor {

    private static final FilterInputStreamCacheMonitor INSTANCE = new FilterInputStreamCacheMonitor();

    private final ConcurrentMap<FilterInputStreamCache, FilterInputStreamCacheInfo> activeCaches = new ConcurrentHashMap<>();

    private FilterInputStreamCacheMonitor() {
    }

    public static FilterInputStreamCacheMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * Intentionally package private!
     * 
     * Only for use by org.exist.util.io.FilterInputStreamCacheMonitorTest
     */
    void clear() {
        activeCaches.clear();
    }

    public void register(final FilterInputStreamCache cache) {
        final long now = System.currentTimeMillis();
        final FilterInputStreamCacheInfo info = new FilterInputStreamCacheInfo(now, cache);
        activeCaches.put(cache, info);
    }

    public Collection<FilterInputStreamCacheInfo> getActive() {
        final List<FilterInputStreamCacheInfo> list = new ArrayList(activeCaches.values());
        list.sort(Comparator.comparingLong(FilterInputStreamCacheInfo::getRegistered));
        return list;
    }

    public void deregister(final FilterInputStreamCache cache) {
        activeCaches.remove(cache);
    }

    public String dump() {
        final StringBuilder builder = new StringBuilder();
        for (final FilterInputStreamCacheInfo info : getActive()) {
            final FilterInputStreamCache cache = info.getCache();
            final String id = switch (cache) {
                case FileFilterInputStreamCache fileFilterInputStreamCache ->
                        fileFilterInputStreamCache.getFilePath().normalize().toAbsolutePath().toString();
                case MemoryMappedFileFilterInputStreamCache memoryMappedFileFilterInputStreamCache ->
                        memoryMappedFileFilterInputStreamCache.getFilePath().normalize().toAbsolutePath().toString();
                case MemoryFilterInputStreamCache memoryFilterInputStreamCache -> "mem";
                case null, default -> "unknown";
            };
            builder.append(info.getRegistered()).append(": ").append(id);
        }
        return builder.toString();
    }

    public static class FilterInputStreamCacheInfo {
        private final long registered;
        private final FilterInputStreamCache cache;

        public FilterInputStreamCacheInfo(final long registered, final FilterInputStreamCache cache) {
            this.registered = registered;
            this.cache = cache;
        }

        public long getRegistered() {
            return registered;
        }

        public FilterInputStreamCache getCache() {
            return cache;
        }
    }
}
