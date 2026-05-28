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

import org.exist.xquery.XQueryContext;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional per-instance bridge for vector workload metrics. Defaults to no-op until the vector extension registers recorders.
 */
public final class VectorOperationMetrics {

    private static final ConcurrentHashMap<String, Recorder> RECORDERS = new ConcurrentHashMap<>();

    private VectorOperationMetrics() {
    }

    /**
     * Register the metrics recorder supplied by the vector extension for a database instance.
     *
     * @param instanceId broker pool instance id
     * @param newRecorder recorder implementation, or {@code null} to remove the recorder
     */
    public static void register(final String instanceId, @Nullable final Recorder newRecorder) {
        if (newRecorder == null) {
            unregister(instanceId);
        } else {
            RECORDERS.put(instanceId, newRecorder);
        }
    }

    /**
     * Remove the metrics recorder for a database instance.
     *
     * @param instanceId broker pool instance id
     */
    public static void unregister(final String instanceId) {
        RECORDERS.remove(instanceId);
    }

    /**
     * Record an embedding call duration for a database instance.
     *
     * @param instanceId broker pool instance id
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordEmbed(final String instanceId, final long durationNanos) {
        recorderFor(instanceId).record(Operation.EMBED, durationNanos);
    }

    /**
     * Record a KNN query duration for a database instance.
     *
     * @param instanceId broker pool instance id
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordKnn(final String instanceId, final long durationNanos) {
        recorderFor(instanceId).record(Operation.KNN, durationNanos);
    }

    /**
     * Record an embedding call duration for the database instance associated with the query context.
     *
     * @param context active XQuery context
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordEmbed(final XQueryContext context, final long durationNanos) {
        recordEmbed(instanceId(context), durationNanos);
    }

    /**
     * Record a KNN query duration for the database instance associated with the query context.
     *
     * @param context active XQuery context
     * @param durationNanos wall time in nanoseconds
     */
    public static void recordKnn(final XQueryContext context, final long durationNanos) {
        recordKnn(instanceId(context), durationNanos);
    }

    private static String instanceId(final XQueryContext context) {
        return context.getBroker().getBrokerPool().getId();
    }

    private static Recorder recorderFor(final String instanceId) {
        return RECORDERS.getOrDefault(instanceId, Recorder.NOOP);
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
