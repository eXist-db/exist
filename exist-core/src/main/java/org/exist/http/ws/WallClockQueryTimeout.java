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
package org.exist.http.ws;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wall-clock backstop for WebSocket {@code max-execution-time}.
 *
 * <p>The XQuery watchdog only checks elapsed time inside {@code proceed()}. Under CPU
 * pressure the eval thread may not run for long stretches, so a scheduled task guarantees
 * the client receives a terminal response when the limit expires.</p>
 */
final class WallClockQueryTimeout {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "exist-ws-eval-wall-clock-timeout");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> future;

    void schedule(final long delayMs, final Runnable onTimeout) {
        future = EXECUTOR.schedule(() -> {
            if (triggered.compareAndSet(false, true)) {
                onTimeout.run();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    void cancel() {
        if (future != null) {
            future.cancel(false);
        }
    }

    boolean wasTriggered() {
        return triggered.get();
    }
}
