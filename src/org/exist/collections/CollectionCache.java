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

import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
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
 * @author Adam Retter <adam@evolvedbinary.com>
 */
@ThreadSafe
public class CollectionCache implements BrokerPoolService {
    private final static Logger LOG = LogManager.getLogger(CollectionCache.class);

    public static final int DEFAULT_CACHE_SIZE_BYTES = 64 * 1024 * 1024;   // 64 MB
    public static final String CACHE_SIZE_ATTRIBUTE = "collectionCache";
    public static final String PROPERTY_CACHE_SIZE_BYTES = "db-connection.collection-cache-mem";

    private int maxCacheSize = -1;
    private Cache<String, Collection> cache;

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
        this.cache = Caffeine.<XmldbURI, Collection>newBuilder()
                .maximumWeight(maxCacheSize)
                .weigher(collectionWeigher)
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
     * @param collection
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
}
