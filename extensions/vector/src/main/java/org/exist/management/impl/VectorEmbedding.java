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
package org.exist.management.impl;

import org.exist.storage.BrokerPool;
import org.exist.vector.VectorEmbeddingService;
import org.exist.vector.VectorMetrics;
import org.exist.vector.VectorModelDiagnostics;
import org.exist.vector.VectorModelInfo;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.List;

/**
 * JMX MBean for vector embedding workload and model diagnostics.
 */
public class VectorEmbedding implements VectorEmbeddingMXBean {

    private static final String KNN_BACKEND = "lucene";

    private final String instanceId;
    private final VectorMetrics metrics;
    private final boolean available;

    /**
     * Creates a new VectorEmbedding MBean for the given broker pool.
     *
     * @param pool the broker pool instance
     */
    public VectorEmbedding(final BrokerPool pool) {
        this.instanceId = pool.getId();
        this.metrics = VectorMetrics.forInstance(instanceId);
        this.available = true;
    }

    /**
     * Returns the JMX ObjectName query string matching all vector embedding MBeans.
     *
     * @return query string for all instances
     */
    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=VectorEmbedding";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instanceId));
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getKnnBackend() {
        return KNN_BACKEND;
    }

    @Override
    public int getModelCount() {
        return VectorModelDiagnostics.getModelCount();
    }

    @Override
    public int getReadyModelCount() {
        return VectorModelDiagnostics.getReadyModelCount();
    }

    @Override
    public int getLoadedProviderCount() {
        return VectorEmbeddingService.getInstance().getLoadedProviderCount();
    }

    @Override
    public long getEmbedCallCount() {
        return metrics.getEmbedCallCount();
    }

    @Override
    public long getEmbedTotalTimeNanos() {
        return metrics.getEmbedTotalTimeNanos();
    }

    @Override
    public long getEmbedLastTimeNanos() {
        return metrics.getEmbedLastTimeNanos();
    }

    @Override
    public long getKnnQueryCount() {
        return metrics.getKnnQueryCount();
    }

    @Override
    public long getKnnTotalTimeNanos() {
        return metrics.getKnnTotalTimeNanos();
    }

    @Override
    public long getKnnLastTimeNanos() {
        return metrics.getKnnLastTimeNanos();
    }

    @Override
    public void resetMetrics() {
        metrics.reset();
    }

    @Override
    public void refreshModels() {
        VectorModelDiagnostics.refreshModels();
    }

    @Override
    public List<VectorModelInfo> getModels() {
        return VectorModelDiagnostics.collectModels();
    }
}
