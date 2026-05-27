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

import org.exist.vector.VectorModelInfo;

import java.util.List;

/**
 * JMX MXBean interface for vector embedding operations and model diagnostics.
 */
public interface VectorEmbeddingMXBean extends PerInstanceMBean {

    /**
     * Whether the vector embedding extension registered this MBean at database startup.
     *
     * @return {@code true} when the vector extension is loaded
     */
    boolean isAvailable();

    /**
     * Persistence backend monitored by this MBean ({@code lucene} KNN index vs {@code vector.dbx}).
     * Vector embeddings may also be stored in Lucene when {@code vector-store="lucene"}.
     *
     * @return {@code lucene} for KNN workload metrics
     */
    String getPersistenceBackend();

    /**
     * Total configured models (registry + built-ins).
     *
     * @return model count
     */
    int getModelCount();

    /**
     * Models with {@code status == available}.
     *
     * @return ready model count
     */
    int getReadyModelCount();

    /**
     * Number of cached embedding providers.
     *
     * @return loaded provider count
     */
    int getLoadedProviderCount();

    long getEmbedCallCount();

    long getEmbedTotalTimeNanos();

    long getEmbedLastTimeNanos();

    long getKnnQueryCount();

    long getKnnTotalTimeNanos();

    long getKnnLastTimeNanos();

    /**
     * Resets embed and KNN workload counters to zero.
     */
    void resetMetrics();

    /**
     * Refreshes the cached model diagnostics snapshot.
     */
    void refreshModels();

    /**
     * Diagnostic rows for configured and built-in models.
     *
     * @return model information rows
     */
    List<VectorModelInfo> getModels();
}
