/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
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
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.CollectionCache;
import org.exist.storage.cache.Cache;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.management.AgentFactory;
import org.exist.management.Agent;

import java.util.Optional;

public class CollectionCacheManager implements CacheManager, BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(CollectionCacheManager.class);
    private static final int DEFAULT_CACHE_SIZE_BYTES = 64 * 1024 * 1024;   // 64 MB
    public static final String CACHE_SIZE_ATTRIBUTE = "collectionCache";
    public static final String PROPERTY_CACHE_SIZE_BYTES = "db-connection.collection-cache-mem";

    private final String brokerPoolId;
    private CollectionCache collectionCache;
    private int maxCacheSize;

    public CollectionCacheManager(final BrokerPool pool, final CollectionCache cache) {
        this.brokerPoolId = pool.getId();
        this.collectionCache = cache;
        this.collectionCache.setCacheManager(this);
    }

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final int cacheSize = Optional.of(configuration.getInteger(PROPERTY_CACHE_SIZE_BYTES)).filter(size -> size > 0)
                .orElse(DEFAULT_CACHE_SIZE_BYTES);
        this.maxCacheSize = cacheSize;

        if(LOG.isDebugEnabled()){
            LOG.debug("collection collectionCache will be using " + this.maxCacheSize + " bytes max.");
        }

        //TODO(AR) move to some start method...
        registerMBean(brokerPoolId);
    }

    @Override
    public void shutdown() {
        deregisterCache(collectionCache);
    }

    @Override
    public void registerCache(final Cache cache) {
    }

    @Override
    public void deregisterCache(final Cache cache) {
        this.collectionCache = null;
    }

    @Override
    public int requestMem(final Cache cache) {
        final int realSize = collectionCache.getRealSize();
        if (realSize < maxCacheSize) {
            synchronized (this) {
                final int newCacheSize = (int)(collectionCache.getBuffers() * collectionCache.getGrowthFactor());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Growing cache {} (a {}) from {} to {} bytes. Current memory usage = {}",
                            collectionCache.getName(), collectionCache.getClass().getName(),
                            collectionCache.getBuffers(), newCacheSize, realSize);
                }
                collectionCache.resize(newCacheSize);
                return newCacheSize;
            }
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("Cache has reached max. size: " + realSize);
        }
        return -1;
    }

    @Override
    public void checkCaches() {
    }

    @Override
    public void checkDistribution() {
    }

    /**
     * @return Maximum size of all Caches in bytes
     */
    @Override
    public long getMaxTotal() {
        return maxCacheSize;
    }

    /**
     * @return Maximum size of a single Cache in bytes
     */
    @Override
    public long getMaxSingle() {
        return maxCacheSize;
    }

    /**
     * @return Current size of all Caches in bytes
     */
    @Override
    public long getCurrentSize() {
        return collectionCache.getRealSize();
    }

    @Override
    public int getDefaultInitialSize() {
        return DEFAULT_CACHE_SIZE_BYTES;
    }

    private void registerMBean(final String instanceName) {
        final Agent agent = AgentFactory.getInstance();
        try {
            agent.addMBean(instanceName, "org.exist.management." + instanceName +
                ":type=CollectionCacheManager", new org.exist.management.CacheManager(this));
        } catch (final DatabaseConfigurationException e) {
            LOG.warn("Exception while registering cache mbean.", e);
        }
    }
}
