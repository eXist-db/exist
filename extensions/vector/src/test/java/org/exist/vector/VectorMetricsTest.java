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

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class VectorMetricsTest {

    @After
    public void tearDown() {
        VectorMetrics.removeInstance("metrics-a");
        VectorMetrics.removeInstance("metrics-b");
        VectorMetrics.removeInstance("metrics-reset");
    }

    @Test
    public void recordsEmbedAndKnnCounters() {
        final VectorMetrics metrics = VectorMetrics.forInstance("metrics-reset");
        metrics.reset();
        metrics.recordEmbed(100);
        metrics.recordEmbed(200);
        metrics.recordKnnQuery(50);

        assertEquals(2, metrics.getEmbedCallCount());
        assertEquals(300, metrics.getEmbedTotalTimeNanos());
        assertEquals(200, metrics.getEmbedLastTimeNanos());
        assertEquals(1, metrics.getKnnQueryCount());
        assertEquals(50, metrics.getKnnTotalTimeNanos());
        assertEquals(50, metrics.getKnnLastTimeNanos());
    }

    @Test
    public void resetClearsCounters() {
        final VectorMetrics metrics = VectorMetrics.forInstance("metrics-reset");
        metrics.recordEmbed(100);
        metrics.recordKnnQuery(50);
        metrics.reset();
        assertEquals(0, metrics.getEmbedCallCount());
        assertEquals(0, metrics.getKnnQueryCount());
    }

    @Test
    public void instancesAreIsolatedById() {
        final VectorMetrics first = VectorMetrics.forInstance("metrics-a");
        final VectorMetrics second = VectorMetrics.forInstance("metrics-b");
        assertNotSame(first, second);

        first.reset();
        second.reset();
        first.recordEmbed(100);
        second.recordKnnQuery(50);

        assertEquals(1, first.getEmbedCallCount());
        assertEquals(0, first.getKnnQueryCount());
        assertEquals(0, second.getEmbedCallCount());
        assertEquals(1, second.getKnnQueryCount());
    }

    @Test
    public void collectModelsIncludesBuiltins() {
        final var models = VectorModelDiagnostics.collectModels();
        assertTrue(models.size() >= 1);
        assertTrue(models.stream().anyMatch(model -> model.getId() != null && !model.getId().isEmpty()));
    }
}
