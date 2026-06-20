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
package org.exist.xquery.modules.httpclient.jmx;

/**
 * JMX MXBean interface for monitoring the {@link HttpClientFactory} client cache.
 *
 * <p>Exposes cache-level statistics (size, hit/miss counts, eviction count) as well as
 * a human-readable summary of the currently cached {@link java.net.http.HttpClient}
 * instances and their configuration.</p>
 */
public interface HttpClientCacheMXBean {

    /**
     * Returns the number of {@link java.net.http.HttpClient} instances currently held in the cache.
     *
     * @return current cache size
     */
    long getCacheSize();

    /**
     * Returns the total number of cache hits since the last JVM start (or cache reset).
     *
     * @return cumulative hit count
     */
    long getHitCount();

    /**
     * Returns the total number of cache misses (i.e. new client creations) since the last JVM start.
     *
     * @return cumulative miss count
     */
    long getMissCount();

    /**
     * Returns the total number of cache entries that have been evicted.
     *
     * @return cumulative eviction count
     */
    long getEvictionCount();

    /**
     * Returns the cache hit rate as a value between 0.0 and 1.0.
     * Returns {@code 0.0} when no requests have been made yet.
     *
     * @return hit rate
     */
    double getHitRate();

    /**
     * Returns a human-readable summary of all currently cached client configurations.
     *
     * <p>Each entry is formatted as {@code followRedirect=<bool>, timeout=<seconds>s}
     * and entries are separated by {@code "; "}. Returns an empty string when the cache
     * is empty.</p>
     *
     * @return summary of cached client configurations
     */
    String getCachedClientsSummary();

    /**
     * Closes all cached {@link java.net.http.HttpClient} instances and removes them from the cache,
     * forcing new clients to be created on the next request.
     */
    void reset();
}
