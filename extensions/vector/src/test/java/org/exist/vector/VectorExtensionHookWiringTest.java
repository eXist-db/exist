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

import org.exist.storage.vector.VectorOperationMetrics;
import org.exist.test.ExistEmbeddedServer;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Regression coverage for the {@code VectorExtensionHook} SPI wiring itself - i.e. that
 * {@code VectorStoreServiceImpl} in exist-core actually discovers {@link VectorExtensionLifecycle}
 * via {@link java.util.ServiceLoader} (through
 * {@code META-INF/services/org.exist.storage.vector.VectorExtensionHook}) and invokes its
 * {@code startSystem}/{@code shutdown} hooks at the right points in the real broker pool
 * lifecycle. This is distinct from testing the hooks' own behavior, already covered directly by
 * {@link VectorOperationMetricsTest} and {@link VectorEmbeddingJmxTest}.
 *
 * <p>No test in this class ever calls {@link VectorExtensionLifecycle}'s methods directly - every
 * assertion here relies on the hooks firing automatically via a real {@code BrokerPool} boot and
 * shutdown. If the {@code META-INF/services} registration ever goes missing, is misspelled, or
 * {@link VectorExtensionLifecycle} loses its (implicit) public no-arg constructor, this is the
 * test that fails - {@code VectorStoreServiceImpl}'s SPI discovery would otherwise have no
 * dedicated coverage.
 */
public class VectorExtensionHookWiringTest {

    @Rule
    public final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    public void startupHookRegistersMetricsBridgeWithoutManualWiring() {
        final String instanceId = server.getBrokerPool().getId();
        VectorMetrics.forInstance(instanceId).reset();

        VectorOperationMetrics.recordEmbed(instanceId, 77);

        assertEquals(1, VectorMetrics.forInstance(instanceId).getEmbedCallCount());
        assertEquals(77, VectorMetrics.forInstance(instanceId).getEmbedTotalTimeNanos());
    }

    @Test
    public void shutdownHookUnregistersMetricsBridgeOnRealBrokerPoolShutdown() throws Exception {
        final String instanceId = server.getBrokerPool().getId();

        // Sanity check: the bridge is live before shutdown (same proof as the test above).
        VectorOperationMetrics.recordEmbed(instanceId, 1);
        assertEquals(1, VectorMetrics.forInstance(instanceId).getEmbedCallCount());
        VectorMetrics.forInstance(instanceId).reset();

        server.stopDb();

        // recordEmbed against an instanceId with no live bridge is a silent no-op
        // (VectorOperationMetrics.recorderFor falls back to Recorder.NOOP), so the count
        // staying at 0 proves the shutdown hook actually unregistered the bridge during the
        // real BrokerPool.shutdown() call above - not just that nothing crashed.
        VectorOperationMetrics.recordEmbed(instanceId, 999);
        assertEquals(0, VectorMetrics.forInstance(instanceId).getEmbedCallCount());

        // Leave the server running: the @Rule's own teardown calls stopDb() unconditionally
        // and throws IllegalStateException if the pool is already stopped.
        server.startDb();
    }
}
