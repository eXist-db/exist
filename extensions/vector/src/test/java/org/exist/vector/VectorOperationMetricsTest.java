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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VectorOperationMetricsTest {

    @Before
    @After
    public void resetMetrics() {
        VectorMetrics.getInstance().reset();
        VectorOperationMetrics.register(null);
    }

    @Test
    public void bridgeRecordsKnnViaLifecycleHook() {
        VectorExtensionLifecycle.onBrokerPoolStartSystem(null);
        VectorOperationMetrics.recordKnn(42);
        assertEquals(1, VectorMetrics.getInstance().getKnnQueryCount());
        assertEquals(42, VectorMetrics.getInstance().getKnnTotalTimeNanos());
    }

    @Test
    public void bridgeRecordsEmbedViaLifecycleHook() {
        VectorExtensionLifecycle.onBrokerPoolStartSystem(null);
        VectorOperationMetrics.recordEmbed(99);
        assertEquals(1, VectorMetrics.getInstance().getEmbedCallCount());
        assertEquals(99, VectorMetrics.getInstance().getEmbedTotalTimeNanos());
    }
}
