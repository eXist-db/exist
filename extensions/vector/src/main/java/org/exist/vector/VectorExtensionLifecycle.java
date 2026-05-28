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

import org.exist.storage.BrokerPool;
import org.exist.storage.vector.VectorOperationMetrics;

/**
 * Startup hooks for the vector extension invoked reflectively from exist-core.
 */
public final class VectorExtensionLifecycle {

    private VectorExtensionLifecycle() {
    }

    /**
     * Registers JMX beans and the metrics bridge when the vector extension is on the classpath.
     *
     * @param pool the broker pool
     */
    public static void onBrokerPoolStartSystem(final BrokerPool pool) {
        if (pool == null) {
            return;
        }
        final String instanceId = pool.getId();
        VectorOperationMetrics.register(instanceId, (operation, durationNanos) -> {
            switch (operation) {
                case EMBED -> VectorMetrics.forInstance(instanceId).recordEmbed(durationNanos);
                case KNN -> VectorMetrics.forInstance(instanceId).recordKnnQuery(durationNanos);
                default -> throw new IllegalStateException("Unsupported vector operation: " + operation);
            }
        });
        VectorEmbeddingJmx.registerIfAbsent(pool);
    }

    /**
     * Clears vector extension JMX registration state when the broker pool shuts down.
     *
     * @param pool the broker pool
     */
    public static void onBrokerPoolShutdown(final BrokerPool pool) {
        if (pool == null) {
            return;
        }
        final String instanceId = pool.getId();
        VectorOperationMetrics.unregister(instanceId);
        VectorMetrics.removeInstance(instanceId);
        VectorEmbeddingJmx.unregister(pool);
    }
}
