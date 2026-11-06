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
package org.exist.indexing.lucene;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Counts, by vector field, the nodes whose vectors a reindex of the whole database embeds again (embedding="local" or
 * "http"), so that its log shows which fields cost the time, how far it has got and how long it took.
 */
final class RecomputedVectors {

    private final LongSupplier nanoTime;
    private final long started;
    private final long progressInterval;
    private final AtomicLong lastProgress;
    private final Map<String, LongAdder> byField = new ConcurrentHashMap<>();

    /**
     * @param nanoTime the clock, {@link System#nanoTime()} but in tests
     * @param progressInterval the nanoseconds between two reports of progress
     */
    RecomputedVectors(final LongSupplier nanoTime, final long progressInterval) {
        this.nanoTime = nanoTime;
        this.started = nanoTime.getAsLong();
        this.progressInterval = progressInterval;
        this.lastProgress = new AtomicLong(started);
    }

    /**
     * Counts a node whose vector was embedded again.
     *
     * @param field the vector field
     * @return true if it is the first node of this field
     */
    boolean add(final String field) {
        final boolean[] first = {false};
        byField.computeIfAbsent(field, f -> {
            first[0] = true;
            return new LongAdder();
        }).increment();
        return first[0];
    }

    /**
     * Whether progress is to be reported now: once per interval, to only one of the threads that ask.
     *
     * @return true if the caller is to report progress
     */
    boolean isProgressDue() {
        final long now = nanoTime.getAsLong();
        final long last = lastProgress.get();
        return now - last >= progressInterval && lastProgress.compareAndSet(last, now);
    }

    long total() {
        return byField.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /**
     * @return the count of each field, in the order of their names, such as {@code abstract 280000, title 32410}
     */
    String byField() {
        return new TreeMap<>(byField).entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue().sum())
                .collect(Collectors.joining(", "));
    }

    /**
     * @return the time since counting started, to the second, such as {@code 2:14:05}
     */
    String elapsed() {
        final Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - started);
        return String.format("%d:%02d:%02d", elapsed.toHours(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
    }
}
