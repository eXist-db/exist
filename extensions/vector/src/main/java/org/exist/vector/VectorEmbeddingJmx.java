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
package org.exist.vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.management.AgentFactory;
import org.exist.management.impl.VectorEmbedding;
import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the {@link VectorEmbedding} JMX MBean once per database instance.
 */
public final class VectorEmbeddingJmx {

    private static final Logger LOG = LogManager.getLogger(VectorEmbeddingJmx.class);
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private VectorEmbeddingJmx() {
    }

    /**
     * Register the VectorEmbedding MBean for the given broker pool if not already registered.
     *
     * @param pool the broker pool
     */
    public static void registerIfAbsent(final BrokerPool pool) {
        if (pool == null) {
            return;
        }
        final String instanceId = pool.getId();
        if (!REGISTERED.add(instanceId)) {
            return;
        }
        try {
            AgentFactory.getInstance().addMBean(new VectorEmbedding(pool));
        } catch (final DatabaseConfigurationException e) {
            REGISTERED.remove(instanceId);
            LOG.warn("Failed to register VectorEmbedding JMX MBean for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }

    /**
     * Clear registration state for the given broker pool so a subsequent startup can register again.
     * JMX deregistration is handled by {@link org.exist.management.AgentFactory#closeDBInstance}.
     *
     * @param pool the broker pool
     */
    public static void unregister(final BrokerPool pool) {
        if (pool == null) {
            return;
        }
        REGISTERED.remove(pool.getId());
    }
}
