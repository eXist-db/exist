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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.collections;

import java.beans.ConstructorProperties;
import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.*;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;

import javax.annotation.Nullable;

/**
 * Global cache for {@link org.exist.collections.Collection} objects.
 *
 * The CollectionCache safely permits concurrent access
 * however appropriate Collection locks should be held
 * on the actual collections when manipulating the
 * CollectionCache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class CollectionCache implements BrokerPoolService {
    private final static Logger LOG = LogManager.getLogger(CollectionCache.class);

    public static final int DEFAULT_CACHE_SIZE_BYTES = 64 * 1024 * 1024;   // 64 MB
    public static final String CACHE_SIZE_ATTRIBUTE = "collectionCache";
    public static final String PROPERTY_CACHE_SIZE_BYTES = "db-connection.collection-cache-mem";

    private int maxCacheSize = -1;
    private Cache<String, Collection> cache;
    private StatsCounter statsCounter = new ConcurrentStatsCounter();

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.maxCacheSize = Optional.of(configuration.getInteger(PROPERTY_CACHE_SIZE_BYTES))
                .filter(size -> size > 0)
                .orElse(DEFAULT_CACHE_SIZE_BYTES);

        if(LOG.isDebugEnabled()){
            LOG.debug("CollectionsCache will use {} bytes max.", this.maxCacheSize);
        }
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        final Weigher<String, Collection> collectionWeigher = (uri, collection) -> collection.getMemorySizeNoLock();
        this.statsCounter = new ConcurrentStatsCounter();
        this.cache = Caffeine.<XmldbURI, Collection>newBuilder()
                .maximumWeight(maxCacheSize)
                .weigher(collectionWeigher)
                .recordStats(() -> statsCounter)
                .build();
    }

    /**
     * Returns the maximum size of the cache in bytes
     *
     * @return maximum size of the cache in bytes
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Get a Snapshot of the Cache Statistics
     *
     * @return The cache statistics
     */
    public Statistics getStatistics() {
        final CacheStats cacheStats = statsCounter.snapshot();
        return new Statistics(
                cacheStats.hitCount(),
                cacheStats.missCount(),
                cacheStats.loadSuccessCount(),
                cacheStats.loadFailureCount(),
                cacheStats.totalLoadTime(),
                cacheStats.evictionCount(),
                cacheStats.evictionWeight()
        );
    }

    /**
     * Returns the Collection from the cache or creates the entry if it is not present
     *
     * @param collectionUri The URI of the Collection
     * @param creator A function that creates (or supplies) the Collection for the URI
     *
     * @return The collection indicated by the URI
     */
    public Collection getOrCreate(final XmldbURI collectionUri, final Function<XmldbURI, Collection> creator) {
        //NOTE: We must not store LockedCollections in the CollectionCache! So we call LockedCollection#unwrapLocked
        return cache.get(key(collectionUri), uri -> LockedCollection.unwrapLocked(creator.apply(XmldbURI.create(uri))));
    }

    /**
     * Returns the Collection from the cache or null if the Collection
     * is not in the cache
     *
     * @param collectionUri The URI of the Collection
     * @return The collection indicated by the URI or null otherwise
     */
    @Nullable public Collection getIfPresent(final XmldbURI collectionUri) {
        return cache.getIfPresent(key(collectionUri));
    }

    /**
     * Put's the Collection into the cache
     *
     * If an existing Collection object for the same URI exists
     * in the Cache it will be overwritten
     *
     * @param collection to put into the cache
     */
    public void put(final Collection collection) {
        //NOTE: We must not store LockedCollections in the CollectionCache! So we call LockedCollection#unwrapLocked
        cache.put(key(collection.getURI()), LockedCollection.unwrapLocked(collection));
    }

    /**
     * Removes an entry from the cache
     *
     * @param collectionUri The URI of the Collection to remove from the Cache
     */
    public void invalidate(final XmldbURI collectionUri) {
        cache.invalidate(collectionUri);
    }

    /**
     * Removes all entries from the Cache
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Calculates the key for the Cache
     *
     * @param collectionUri The URI of the Collection
     * @return the key for the Collection in the Cache
     */
    private String key(final XmldbURI collectionUri) {
        return collectionUri.getRawCollectionPath();
    }

    /**
     * Basically an eXist abstraction
     * for {@link CacheStats}
     *  Apache License Version 2.0
     */
    public static class Statistics {
        private final long hitCount;
        private final long missCount;
        private final long loadSuccessCount;
        private final long loadFailureCount;
        private final long totalLoadTime;
        private final long evictionCount;
        private final long evictionWeight;

        /**
         * @param hitCount the number of cache hits
         * @param missCount the number of cache misses
         * @param loadSuccessCount the number of successful cache loads
         * @param loadFailureCount the number of failed cache loads
         * @param totalLoadTime the total load time (success and failure)
         * @param evictionCount the number of entries evicted from the cache
         * @param evictionWeight the sum of weights of entries evicted from the cache
         */
        @ConstructorProperties({"hitCount", "missCount", "loadSuccessCount", "loadFailureCount", "totalLoadTime", "evictionCount", "evictionWeight"})
        public Statistics(final long hitCount, final long missCount, final long loadSuccessCount, final long loadFailureCount, final long totalLoadTime, final long evictionCount, final long evictionWeight) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.loadSuccessCount = loadSuccessCount;
            this.loadFailureCount = loadFailureCount;
            this.totalLoadTime = totalLoadTime;
            this.evictionCount = evictionCount;
            this.evictionWeight = evictionWeight;
        }

        /**
         * Returns the number of times {@link Cache} lookup methods have returned a cached value.
         *
         * @return the number of times {@link Cache} lookup methods have returned a cached value
         */
        public long getHitCount() {
            return hitCount;
        }

        /**
         * Returns the number of times {@link Cache} lookup methods have returned either a cached or
         * uncached value. This is defined as {@code hitCount + missCount}.
         *
         * @return the {@code hitCount + missCount}
         */
        public long getRequestCount() {
            return hitCount + missCount;
        }

        /**
         * Returns the ratio of cache requests which were hits. This is defined as
         * {@code hitCount / requestCount}, or {@code 1.0} when {@code requestCount == 0}. Note that
         * {@code hitRate + missRate =~ 1.0}.
         *
         * @return the ratio of cache requests which were hits
         */
        public double getHitRate() {
            final long requestCount = getRequestCount();
            return requestCount == 0 ? 1.0 : (double) hitCount / requestCount;
        }

        /**
         * Returns the number of times {@link Cache} lookup methods have returned an uncached (newly
         * loaded) value, or null. Multiple concurrent calls to {@link Cache} lookup methods on an absent
         * value can result in multiple misses, all returning the results of a single cache load
         * operation.
         *
         * @return the number of times {@link Cache} lookup methods have returned an uncached (newly
         *         loaded) value, or null
         */
        public long getMissCount() {
            return missCount;
        }

        /**
         * Returns the ratio of cache requests which were misses. This is defined as
         * {@code missCount / requestCount}, or {@code 0.0} when {@code requestCount == 0}.
         * Note that {@code hitRate + missRate =~ 1.0}. Cache misses include all requests which
         * weren't cache hits, including requests which resulted in either successful or failed loading
         * attempts, and requests which waited for other threads to finish loading. It is thus the case
         * that {@code missCount &gt;= loadSuccessCount + loadFailureCount}. Multiple
         * concurrent misses for the same key will result in a single load operation.
         *
         * @return the ratio of cache requests which were misses
         */
        public double getMissRate() {
            final long requestCount = getRequestCount();
            return requestCount == 0 ? 0.0 : (double) missCount / requestCount;
        }

        /**
         * Returns the total number of times that {@link Cache} lookup methods attempted to load new
         * values. This includes both successful load operations, as well as those that threw exceptions.
         * This is defined as {@code loadSuccessCount + loadFailureCount}.
         *
         * @return the {@code loadSuccessCount + loadFailureCount}
         */
        public long getLoadCount() {
            return loadSuccessCount + loadFailureCount;
        }

        /**
         * Returns the number of times {@link Cache} lookup methods have successfully loaded a new value.
         * This is always incremented in conjunction with {@link #missCount}, though {@code missCount}
         * is also incremented when an exception is encountered during cache loading (see
         * {@link #loadFailureCount}). Multiple concurrent misses for the same key will result in a
         * single load operation.
         *
         * @return the number of times {@link Cache} lookup methods have successfully loaded a new value
         */
        public long getLoadSuccessCount() {
            return loadSuccessCount;
        }

        /**
         * Returns the number of times {@link Cache} lookup methods failed to load a new value, either
         * because no value was found or an exception was thrown while loading. This is always incremented
         * in conjunction with {@code missCount}, though {@code missCount} is also incremented when cache
         * loading completes successfully (see {@link #loadSuccessCount}). Multiple concurrent misses for
         * the same key will result in a single load operation.
         *
         * @return the number of times {@link Cache} lookup methods failed to load a new value
         */
        public long getLoadFailureCount() {
            return loadFailureCount;
        }

        /**
         * Returns the ratio of cache loading attempts which threw exceptions. This is defined as
         * {@code loadFailureCount / (loadSuccessCount + loadFailureCount)}, or {@code 0.0} when
         * {@code loadSuccessCount + loadFailureCount == 0}.
         *
         * @return the ratio of cache loading attempts which threw exceptions
         */
        public double getLoadFailureRate() {
            final long totalLoadCount = loadSuccessCount + loadFailureCount;
            return totalLoadCount == 0
                    ? 0.0
                    : (double) loadFailureCount / totalLoadCount;
        }

        /**
         * Returns the total number of nanoseconds the cache has spent loading new values. This can be
         * used to calculate the miss penalty. This value is increased every time {@code loadSuccessCount}
         * or {@code loadFailureCount} is incremented.
         *
         * @return the total number of nanoseconds the cache has spent loading new values
         */
        public long getTotalLoadTime() {
            return totalLoadTime;
        }

        /**
         * Returns the average time spent loading new values. This is defined as
         * {@code totalLoadTime / (loadSuccessCount + loadFailureCount)}.
         *
         * @return the average time spent loading new values
         */
        public double getAverageLoadPenalty() {
            final long totalLoadCount = loadSuccessCount + loadFailureCount;
            return totalLoadCount == 0
                    ? 0.0
                    : (double) totalLoadTime / totalLoadCount;
        }

        /**
         * Returns the number of times an entry has been evicted. This count does not include manual
         * {@linkplain Cache#invalidate invalidations}.
         *
         * @return the number of times an entry has been evicted
         */

        public long getEvictionCount() {
            return evictionCount;
        }

        /**
         * Returns the sum of weights of evicted entries. This total does not include manual
         * {@linkplain Cache#invalidate invalidations}.
         *
         * @return the sum of weights of evicted entities
         */
        public long getEvictionWeight() {
            return evictionWeight;
        }
    }
}
