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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight counters for vector embedding and KNN query operations.
 */
public final class VectorMetrics {

    private static final VectorMetrics INSTANCE = new VectorMetrics();

    private final LongAdder embedCallCount = new LongAdder();
    private final LongAdder embedTotalTimeNanos = new LongAdder();
    private final AtomicLong embedLastTimeNanos = new AtomicLong();

    private final LongAdder knnQueryCount = new LongAdder();
    private final LongAdder knnTotalTimeNanos = new LongAdder();
    private final AtomicLong knnLastTimeNanos = new AtomicLong();

    private VectorMetrics() {
    }

    public static VectorMetrics getInstance() {
        return INSTANCE;
    }

    public void recordEmbed(final long durationNanos) {
        embedCallCount.increment();
        embedTotalTimeNanos.add(durationNanos);
        embedLastTimeNanos.set(durationNanos);
    }

    public void recordKnnQuery(final long durationNanos) {
        knnQueryCount.increment();
        knnTotalTimeNanos.add(durationNanos);
        knnLastTimeNanos.set(durationNanos);
    }

    public long getEmbedCallCount() {
        return embedCallCount.sum();
    }

    public long getEmbedTotalTimeNanos() {
        return embedTotalTimeNanos.sum();
    }

    public long getEmbedLastTimeNanos() {
        return embedLastTimeNanos.get();
    }

    public long getKnnQueryCount() {
        return knnQueryCount.sum();
    }

    public long getKnnTotalTimeNanos() {
        return knnTotalTimeNanos.sum();
    }

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
