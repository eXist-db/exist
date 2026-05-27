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
package org.exist.storage.vector;

import javax.annotation.Nullable;

/**
 * Optional bridge for vector workload metrics. Defaults to no-op until the vector extension registers a recorder.
 */
public final class VectorOperationMetrics {

    @Nullable
    private static volatile Recorder recorder;

    private VectorOperationMetrics() {
    }

    /**
     * Register the metrics recorder supplied by the vector extension.
     *
     * @param newRecorder recorder implementation, or {@code null} to restore the no-op recorder
     */
    public static void register(@Nullable final Recorder newRecorder) {
        recorder = newRecorder != null ? newRecorder : Recorder.NOOP;
    }

    /**
     * Record an embedding call duration.
     *
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordEmbed(final long durationNanos) {
        activeRecorder().record(Operation.EMBED, durationNanos);
    }

    /**
     * Record a KNN query duration.
     *
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordKnn(final long durationNanos) {
        activeRecorder().record(Operation.KNN, durationNanos);
    }

    private static Recorder activeRecorder() {
        final Recorder current = recorder;
        return current != null ? current : Recorder.NOOP;
    }

    /**
     * Vector operation kinds tracked by JMX metrics.
     */
    public enum Operation {
        EMBED,
        KNN
    }

    /**
     * Receives vector operation timing events.
     */
    @FunctionalInterface
    public interface Recorder {
        Recorder NOOP = (operation, durationNanos) -> {
        };

        void record(Operation operation, long durationNanos);
    }
}
