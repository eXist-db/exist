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
import org.exist.test.ExistEmbeddedServer;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VectorOperationMetricsTest {

    @ClassRule
    public static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    @After
    public void resetMetrics() {
        final String instanceId = SERVER.getBrokerPool().getId();
        VectorMetrics.forInstance(instanceId).reset();
    }

    @Test
    public void bridgeRecordsKnnViaLifecycleHook() {
        final String instanceId = SERVER.getBrokerPool().getId();
        VectorOperationMetrics.recordKnn(instanceId, 42);
        assertEquals(1, VectorMetrics.forInstance(instanceId).getKnnQueryCount());
        assertEquals(42, VectorMetrics.forInstance(instanceId).getKnnTotalTimeNanos());
    }

    @Test
    public void bridgeRecordsEmbedViaLifecycleHook() {
        final String instanceId = SERVER.getBrokerPool().getId();
        VectorOperationMetrics.recordEmbed(instanceId, 99);
        assertEquals(1, VectorMetrics.forInstance(instanceId).getEmbedCallCount());
        assertEquals(99, VectorMetrics.forInstance(instanceId).getEmbedTotalTimeNanos());
    }

    @Test
    public void bridgeRoutesMetricsByInstanceId() {
        VectorOperationMetrics.register("bridge-a", (operation, durationNanos) -> {
            switch (operation) {
                case EMBED -> VectorMetrics.forInstance("bridge-a").recordEmbed(durationNanos);
                case KNN -> VectorMetrics.forInstance("bridge-a").recordKnnQuery(durationNanos);
                default -> throw new IllegalStateException("Unsupported vector operation: " + operation);
            }
        });
        VectorOperationMetrics.register("bridge-b", (operation, durationNanos) -> {
            switch (operation) {
                case EMBED -> VectorMetrics.forInstance("bridge-b").recordEmbed(durationNanos);
                case KNN -> VectorMetrics.forInstance("bridge-b").recordKnnQuery(durationNanos);
                default -> throw new IllegalStateException("Unsupported vector operation: " + operation);
            }
        });

        VectorOperationMetrics.recordEmbed("bridge-a", 10);
        VectorOperationMetrics.recordEmbed("bridge-b", 20);

        assertEquals(10, VectorMetrics.forInstance("bridge-a").getEmbedTotalTimeNanos());
        assertEquals(20, VectorMetrics.forInstance("bridge-b").getEmbedTotalTimeNanos());

        VectorOperationMetrics.unregister("bridge-a");
        VectorOperationMetrics.unregister("bridge-b");
        VectorMetrics.removeInstance("bridge-a");
        VectorMetrics.removeInstance("bridge-b");
    }

    @Test
    public void shutdownHookClearsMetricsBridge() {
        final BrokerPool pool = SERVER.getBrokerPool();
        final String instanceId = pool.getId();
        VectorExtensionLifecycle.onBrokerPoolShutdown(pool);
        VectorOperationMetrics.recordEmbed(instanceId, 50);
        assertEquals(0, VectorMetrics.forInstance(instanceId).getEmbedCallCount());

        VectorExtensionLifecycle.onBrokerPoolStartSystem(pool);
    }
}
