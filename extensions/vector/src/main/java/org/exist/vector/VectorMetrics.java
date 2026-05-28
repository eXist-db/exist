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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight per-database-instance counters for vector embedding and KNN query operations.
 */
public final class VectorMetrics {

    private static final ConcurrentHashMap<String, VectorMetrics> INSTANCES = new ConcurrentHashMap<>();

    private final LongAdder embedCallCount = new LongAdder();
    private final LongAdder embedTotalTimeNanos = new LongAdder();
    private final AtomicLong embedLastTimeNanos = new AtomicLong();

    private final LongAdder knnQueryCount = new LongAdder();
    private final LongAdder knnTotalTimeNanos = new LongAdder();
    private final AtomicLong knnLastTimeNanos = new AtomicLong();

    private VectorMetrics() {
    }

    /**
     * Returns metrics for the given database instance id.
     *
     * @param instanceId broker pool instance id
     * @return metrics counters for the instance
     */
    public static VectorMetrics forInstance(final String instanceId) {
        return INSTANCES.computeIfAbsent(instanceId, id -> new VectorMetrics());
    }

    /**
     * Removes metrics state for a shut-down database instance.
     *
     * @param instanceId broker pool instance id
     */
    public static void removeInstance(final String instanceId) {
        INSTANCES.remove(instanceId);
    }

    /**
     * Records an embedding call duration.
     *
     * @param durationNanos wall time in nanoseconds
     */
    public void recordEmbed(final long durationNanos) {
        embedCallCount.increment();
        embedTotalTimeNanos.add(durationNanos);
        embedLastTimeNanos.set(durationNanos);
    }

    /**
     * Records a KNN query duration.
     *
     * @param durationNanos wall time in nanoseconds
     */
    public void recordKnnQuery(final long durationNanos) {
        knnQueryCount.increment();
        knnTotalTimeNanos.add(durationNanos);
        knnLastTimeNanos.set(durationNanos);
    }

    /**
     * Returns the total embedding call count.
     *
     * @return embed call count
     */
    public long getEmbedCallCount() {
        return embedCallCount.sum();
    }

    /**
     * Returns the total accumulated embedding time in nanoseconds.
     *
     * @return total time in nanoseconds
     */
    public long getEmbedTotalTimeNanos() {
        return embedTotalTimeNanos.sum();
    }

    /**
     * Returns the last embedding call duration in nanoseconds.
     *
     * @return last duration in nanoseconds
     */
    public long getEmbedLastTimeNanos() {
        return embedLastTimeNanos.get();
    }

    /**
     * Returns the total KNN query count.
     *
     * @return KNN query count
     */
    public long getKnnQueryCount() {
        return knnQueryCount.sum();
    }

    /**
     * Returns the total accumulated KNN query time in nanoseconds.
     *
     * @return total time in nanoseconds
     */
    public long getKnnTotalTimeNanos() {
        return knnTotalTimeNanos.sum();
    }

    /**
     * Returns the last KNN query duration in nanoseconds.
     *
     * @return last duration in nanoseconds
     */
    public long getKnnLastTimeNanos() {
        return knnLastTimeNanos.get();
    }

    /**
     * Resets all embed and KNN counters to zero.
     */
    public void reset() {
        embedCallCount.reset();
        embedTotalTimeNanos.reset();
        embedLastTimeNanos.set(0);
        knnQueryCount.reset();
        knnTotalTimeNanos.reset();
        knnLastTimeNanos.set(0);
    }
}
