/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.value.Sequence;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class SessionManager {

    private static final Logger LOG = LogManager.getLogger(SessionManager.class);
    private static final long TIMEOUT = 120_000;  // ms (e.g. 2 minutes)

    private final AtomicInteger sessionIdCounter = new AtomicInteger();
    private final Cache<Integer, QueryResult> cache;

    private static class QueryResult {
        final String query;
        final Sequence sequence;

        private QueryResult(final String query, final Sequence sequence) {
            this.query = query;
            this.sequence = sequence;
        }
    }

    public SessionManager() {
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder()
                .expireAfterAccess(TIMEOUT, TimeUnit.MILLISECONDS);
        if(LOG.isDebugEnabled()) {
            cacheBuilder.removalListener((key, value, cause) -> LOG.debug("Removing cached query result for session: " + key));
        }
        cache = cacheBuilder.build();
    }

    public int add(final String query, final Sequence sequence) {
        final int sessionId = sessionIdCounter.getAndIncrement();
        cache.put(sessionId, new QueryResult(query, sequence));
        return sessionId;
    }

    public Sequence get(final String query, final int sessionId) {
        if (sessionId < 0 || sessionId >= sessionIdCounter.get()) {
            return null; // out of scope
        }

        final QueryResult cached = cache.getIfPresent(sessionId);
        if (cached == null) {
            return null;
        }

        if (cached.query.equals(query)) {
            return cached.sequence;
        } else {
            // wrong query
            return null;
        }
    }

    public void release(final int sessionId) {
        if (sessionId < 0 || sessionId >= sessionIdCounter.get()) {
            return; // out of scope
        }
        cache.invalidate(sessionId);
    }
}
