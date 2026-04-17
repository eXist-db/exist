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

import org.exist.security.Subject;
import org.exist.xquery.XQueryWatchDog;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-connection state for the /ws/eval WebSocket endpoint.
 *
 * Tracks the authenticated user and all running queries for cancellation.
 */
public final class EvalSession {

    private final Subject subject;
    private final Map<String, XQueryWatchDog> runningQueries = new ConcurrentHashMap<>();

    public EvalSession(final Subject subject) {
        this.subject = subject;
    }

    public Subject getSubject() {
        return subject;
    }

    /**
     * Register a running query's watchdog for cancellation.
     */
    public void registerQuery(final String queryId, final XQueryWatchDog watchDog) {
        runningQueries.put(queryId, watchDog);
    }

    /**
     * Unregister a completed or cancelled query.
     */
    public void unregisterQuery(final String queryId) {
        runningQueries.remove(queryId);
    }

    /**
     * Cancel a running query by its ID.
     *
     * @return true if the query was found and cancelled
     */
    public boolean cancelQuery(final String queryId) {
        final XQueryWatchDog watchDog = runningQueries.get(queryId);
        if (watchDog != null) {
            watchDog.kill(0);
            return true;
        }
        return false;
    }

    /**
     * Cancel all running queries (called on session close).
     */
    public void cancelAll() {
        for (final XQueryWatchDog watchDog : runningQueries.values()) {
            watchDog.kill(0);
        }
        runningQueries.clear();
    }
}
